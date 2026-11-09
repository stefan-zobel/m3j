(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The engine (M3jTls) does no input or output, so all of that is here, on
   the connection below. No lock is held while a thread is blocked on the
   connection, so a thread that waits for data does not stop one that
   writes. The flag "reading" lets one thread at a time read the connection;
   "sending" lets one thread at a time send, so the records of the engine
   stay in order. *)

MODULE TLS;

IMPORT Atom, AtomList, ConnFD, IP, IPError, M3jTls, Rd, TCP, Thread, Time, Wr;

REVEAL
  Context = ROOT BRANDED "TLS.Context" OBJECT
    ctx: REFANY;  (*CONST*)
  END;

  T = ConnFD.T BRANDED "TLS.T" OBJECT
    conn: ConnFD.T;  (*CONST*)
    e: REFANY;  (*CONST*)
    netRd, netWr: REF ARRAY OF CHAR;  (* of the thread with the flag *)
    c: Thread.Condition;  (* "reading" or "sending" has become FALSE *)
    reading, sending := FALSE;
    inClosed, closed := FALSE;
    outError: AtomList.T := NIL;  (* the output is unusable *)
  OVERRIDES
    get := Get;
    put := Put;
    shutdownIn := ShutdownIn;
    shutdownOut := ShutdownOut;
    close := Close;
  END;

CONST
  NetSize = 128 * 1024;  (* several records, for one call on the connection *)
  SendSize = 48 * 1024;  (* a "put" sends when it has encrypted this much *)

PROCEDURE NewContext (keyStore, keyPassword, trustStore, trustPassword: TEXT := NIL): Context
  RAISES {IP.Error} =
  VAR
    err: TEXT := NIL;
    ctx := M3jTls.NewContext (keyStore, keyPassword, trustStore, trustPassword, err);
  BEGIN
    IF ctx = NIL THEN
      RAISE IP.Error (AtomList.List2 (BadKeyStore, Atom.FromText (err)));
    END;
    RETURN NEW (Context, ctx := ctx);
  END NewContext;

PROCEDURE Platform (ctx: Context): REFANY =
  BEGIN
    RETURN ctx.ctx;
  END Platform;

PROCEDURE Client (ctx: Context; conn: ConnFD.T; host: TEXT; alpn: TEXT := NIL;
                  waitFor: LONGREAL := -1.0D0): T
  RAISES {IP.Error, Thread.Alerted} =
  BEGIN
    <* ASSERT host # NIL *>
    RETURN Handshake (M3jTls.NewEngine (ctx.ctx, host, TRUE, FALSE, alpn), conn, waitFor);
  END Client;

PROCEDURE Server (ctx: Context; conn: ConnFD.T; needClientCert := FALSE; alpn: TEXT := NIL;
                  waitFor: LONGREAL := -1.0D0): T
  RAISES {IP.Error, Thread.Alerted} =
  BEGIN
    RETURN Handshake (M3jTls.NewEngine (ctx.ctx, NIL, FALSE, needClientCert, alpn), conn, waitFor);
  END Server;

PROCEDURE Handshake (e: REFANY; conn: ConnFD.T; waitFor: LONGREAL): T
  RAISES {IP.Error, Thread.Alerted} =
  VAR
    t := NEW (T, conn := conn, e := e, c := NEW (Thread.Condition),
              netRd := NEW (REF ARRAY OF CHAR, NetSize),
              netWr := NEW (REF ARRAY OF CHAR, NetSize));
    deadline := Deadline (waitFor);
    ok := FALSE;
    s: INTEGER;
  BEGIN
    TRY
      TRY
        LOOP
          s := M3jTls.Handshaking (e);
          IF s < 0 THEN
            TellPeer (t);
            RAISE IP.Error (Why (t, -s));
          END;
          Flush (t, wait := FALSE);
          IF s = 0 THEN EXIT END;
          Pump (t, deadline);
        END;
        ok := TRUE;
      EXCEPT
      | Rd.Failure (l) => RAISE IP.Error (l);
      | Wr.Failure (l) => RAISE IP.Error (l);
      | ConnFD.TimedOut => IPError.Raise (TCP.Timeout);
      END;
    FINALLY
      IF NOT ok THEN conn.close () END;
    END;
    RETURN t;
  END Handshake;

PROCEDURE Deadline (waitFor: LONGREAL): LONGREAL =
  BEGIN
    IF waitFor < 0.0D0 THEN RETURN -1.0D0 END;
    RETURN Time.Now () + waitFor;
  END Deadline;

PROCEDURE Remaining (deadline: LONGREAL): LONGREAL =
  BEGIN
    IF deadline < 0.0D0 THEN RETURN -1.0D0 END;
    RETURN MAX (deadline - Time.Now (), 0.0D0);
  END Remaining;

PROCEDURE Why (t: T; code: INTEGER): AtomList.T =
  VAR a: Atom.T;  msg := M3jTls.Message (t.e);
  BEGIN
    CASE code OF
    | M3jTls.BadCertificate => a := BadCertificate;
    | M3jTls.ProtocolError => a := ProtocolError;
    | M3jTls.Truncated => a := Truncated;
    | M3jTls.Closed => RETURN IPError.ClosedErr;
    ELSE a := HandshakeFailed;
    END;
    IF msg = NIL THEN RETURN AtomList.List1 (a) END;
    RETURN AtomList.List2 (a, Atom.FromText (msg));
  END Why;

(* Sends what the engine has for the network. A thread that "wait"s sends
   after any other thread that is sending, which holds back a writer that
   is faster than the network. Otherwise it leaves the bytes to that
   thread, which drains the engine until it is empty: a reader must not
   wait for a writer that is blocked. *)
PROCEDURE Flush (t: T; wait: BOOLEAN) RAISES {Wr.Failure, Thread.Alerted} =
  VAR n: INTEGER;
  BEGIN
    LOCK t DO
      IF wait THEN
        WHILE t.sending DO Thread.AlertWait (t, t.c) END;
      ELSIF t.sending THEN
        RETURN;
      END;
      t.sending := TRUE;
    END;
    TRY
      LOOP
        LOCK t DO
          n := M3jTls.Drain (t.e, t.netWr^);
          IF n = 0 THEN
            t.sending := FALSE;
            Thread.Broadcast (t.c);
            RETURN;
          END;
        END;
        t.conn.put (SUBARRAY (t.netWr^, 0, n));
      END;
    EXCEPT
    | Wr.Failure (l) =>
        StopSending (t, l);
        RAISE Wr.Failure (l);
    | Thread.Alerted =>
        StopSending (t, AtomList.List1 (TCP.ConnLost));
        RAISE Thread.Alerted;
    END;
  END Flush;

PROCEDURE StopSending (t: T; l: AtomList.T) =
  BEGIN
    LOCK t DO
      t.outError := l;
      t.sending := FALSE;
      Thread.Broadcast (t.c);
    END;
  END StopSending;

(* Sends an alert or the notice of closing, if that is possible. An alert
   for this thread is kept for its next wait. *)
PROCEDURE TellPeer (t: T) =
  BEGIN
    TRY
      Flush (t, wait := FALSE);
    EXCEPT
    | Wr.Failure =>
    | Thread.Alerted => Thread.Alert (Thread.Self ());
    END;
  END TellPeer;

(* Gives the engine what comes from the connection, once. A thread that
   finds another one reading returns when that one is done, so that the
   caller can look at what it has read. *)
PROCEDURE Pump (t: T; deadline: LONGREAL)
  RAISES {Rd.Failure, Wr.Failure, ConnFD.TimedOut, Thread.Alerted} =
  VAR n: CARDINAL;
  BEGIN
    LOCK t DO
      IF t.closed THEN RAISE Rd.Failure (IPError.ClosedErr) END;
      IF t.reading THEN
        REPEAT Thread.AlertWait (t, t.c) UNTIL NOT t.reading;
        RETURN;
      END;
      t.reading := TRUE;
    END;
    TRY
      n := t.conn.get (t.netRd^, Remaining (deadline));
      M3jTls.Feed (t.e, SUBARRAY (t.netRd^, 0, n));
    FINALLY
      LOCK t DO
        t.reading := FALSE;
        Thread.Broadcast (t.c);
      END;
    END;
    Flush (t, wait := FALSE);
  END Pump;

(* methods of T *)

PROCEDURE Get (t: T; VAR arr: ARRAY OF CHAR; waitFor: LONGREAL): CARDINAL
  RAISES {Rd.Failure, ConnFD.TimedOut, Thread.Alerted} =
  VAR deadline := Deadline (waitFor);  n: INTEGER;
  BEGIN
    LOOP
      LOCK t DO
        IF t.closed THEN RAISE Rd.Failure (IPError.ClosedErr) END;
        IF t.inClosed THEN RETURN 0 END;
      END;
      n := M3jTls.Take (t.e, arr);
      IF n >= 0 THEN RETURN n END;
      IF n # -M3jTls.NeedData THEN
        TellPeer (t);
        RAISE Rd.Failure (Why (t, -n));
      END;
      TRY
        Pump (t, deadline);
      EXCEPT
      | Wr.Failure (l) => RAISE Rd.Failure (l);
      END;
    END;
  END Get;

PROCEDURE Put (t: T; READONLY arr: ARRAY OF CHAR)
  RAISES {Wr.Failure, Thread.Alerted} =
  VAR pos := 0;  pending := 0;  n: INTEGER;
  BEGIN
    WHILE pos < NUMBER (arr) DO
      LOCK t DO
        IF t.closed THEN RAISE Wr.Failure (IPError.ClosedErr) END;
        IF t.outError # NIL THEN RAISE Wr.Failure (t.outError) END;
      END;
      n := M3jTls.Give (t.e, SUBARRAY (arr, pos, NUMBER (arr) - pos));
      IF n < 0 THEN
        TellPeer (t);
        RAISE Wr.Failure (Why (t, -n));
      END;
      INC (pos, n);
      INC (pending, n);
      (* several records in one call cost far less than one call for each *)
      IF pending >= SendSize OR pos = NUMBER (arr) THEN
        Flush (t, wait := TRUE);
        pending := 0;
      END;
    END;
  END Put;

PROCEDURE ShutdownIn (t: T) RAISES {Rd.Failure} =
  BEGIN
    LOCK t DO
      IF t.closed THEN RAISE Rd.Failure (IPError.ClosedErr) END;
      t.inClosed := TRUE;
    END;
    t.conn.shutdownIn ();
  END ShutdownIn;

PROCEDURE ShutdownOut (t: T) RAISES {Wr.Failure} =
  BEGIN
    LOCK t DO
      IF t.closed THEN RAISE Wr.Failure (IPError.ClosedErr) END;
    END;
    M3jTls.CloseOutput (t.e);
    TRY
      Flush (t, wait := TRUE);
    EXCEPT
    | Thread.Alerted =>
        Thread.Alert (Thread.Self ());
        RAISE Wr.Failure (AtomList.List1 (TCP.ConnLost));
    END;
    t.conn.shutdownOut ();
  END ShutdownOut;

PROCEDURE Close (t: T) =
  VAR first: BOOLEAN;
  BEGIN
    LOCK t DO
      first := NOT t.closed;
      t.closed := TRUE;
    END;
    IF first THEN
      M3jTls.CloseOutput (t.e);
      TellPeer (t);
      t.conn.close ();
    END;
  END Close;

(* what the handshake has found *)

PROCEDURE Protocol (t: T): TEXT =
  BEGIN
    RETURN M3jTls.Info (t.e, M3jTls.InfoProtocol);
  END Protocol;

PROCEDURE Version (t: T): TEXT =
  BEGIN
    RETURN M3jTls.Info (t.e, M3jTls.InfoVersion);
  END Version;

PROCEDURE Cipher (t: T): TEXT =
  BEGIN
    RETURN M3jTls.Info (t.e, M3jTls.InfoCipher);
  END Cipher;

PROCEDURE PeerName (t: T): TEXT =
  BEGIN
    RETURN M3jTls.Info (t.e, M3jTls.InfoPeer);
  END PeerName;

BEGIN
  HandshakeFailed := Atom.FromText ("TLS.HandshakeFailed");
  BadCertificate := Atom.FromText ("TLS.BadCertificate");
  BadKeyStore := Atom.FromText ("TLS.BadKeyStore");
  ProtocolError := Atom.FromText ("TLS.ProtocolError");
  Truncated := Atom.FromText ("TLS.Truncated");
END TLS.
