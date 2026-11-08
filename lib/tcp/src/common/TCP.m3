(* The module TCP of m3j: in cm3 it is UNSAFE and calls Windows Sockets
   (WIN32/TCP.m3) or the sockets of POSIX; here the sockets of the Java
   platform do the work (M3jNet), with the exceptions and lists of atoms
   of cm3's module for Windows. The module also implements TCPMisc,
   TCPSpecial, TCPPeer and TCPExtras (cm3 has modules of their own for the
   last two, which reach the socket of a "TCP.T" through TCPWin32). A
   thread that waits for a socket returns at once when it is alerted (cm3
   tests for an alert once a second). *)

(* Copyright (C) 1995, Digital Equipment Corporation.       *)
(* All rights reserved.                                     *)
(* See the file COPYRIGHT for a full description.           *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

MODULE TCP EXPORTS TCP, TCPMisc, TCPSpecial, TCPPeer, TCPExtras;

IMPORT Atom, AtomList, ConnFD, Ctypes, Fmt, IP, IPError, M3jNet, Rd, Thread,
       Word, Wr;

REVEAL
  Connector = MUTEX BRANDED "TCP.Connector" OBJECT
    h: INTEGER;  (*CONST*)
    ep: IP.Endpoint;
    closed: BOOLEAN := FALSE;
  END;

REVEAL
  T = ConnFD.T BRANDED "TCP.T" OBJECT
    h: INTEGER;  (*CONST*)
    ep: IP.Endpoint;
    closed: BOOLEAN := FALSE;
    error: AtomList.T := NIL;
  OVERRIDES
    get := GetBytesFD;
    put := PutBytesFD;
    shutdownIn := ShutdownIn;
    shutdownOut := ShutdownOut;
    close := Close;
  END;

TYPE WaitResult = {Ready, Timeout};

CONST
  NetReset = 10052;  (* WSAENETRESET *)
  HostDown = 10064;  (* WSAEHOSTDOWN *)

(* addresses as integers, for M3jNet *)

PROCEDURE ToInt (READONLY a: IP.Address): INTEGER =
  BEGIN
    RETURN ((a.a[0] * 256 + a.a[1]) * 256 + a.a[2]) * 256 + a.a[3];
  END ToInt;

PROCEDURE FromInt (i: INTEGER): IP.Address =
  VAR r: IP.Address;
  BEGIN
    FOR k := 3 TO 0 BY -1 DO
      r.a[k] := i MOD 256;
      i := i DIV 256;
    END;
    RETURN r;
  END FromInt;

PROCEDURE NewConnector (ep: IP.Endpoint): Connector RAISES {IP.Error} =
  VAR h := M3jNet.Listen(ToInt(ep.addr), ep.port);
  BEGIN
    IF h < 0 THEN
      IF -h = M3jNet.TooMany THEN
        IPError.Raise(IP.NoResources, -h);
      ELSIF -h = M3jNet.AddrInUse THEN
        IPError.Raise(IP.PortBusy, -h);
      ELSE
        Ouch(-h, "TCP.NewConnector(bind)");
      END;
    END;
    RETURN NEW(Connector, h := h, ep := ep);
  END NewConnector;

PROCEDURE GetEndPoint(c: Connector): IP.Endpoint =
  VAR addr, port: INTEGER;
  BEGIN
    IF c.ep.addr = IP.NullAddress THEN
      c.ep.addr := IP.GetHostAddr();
    END;
    IF c.ep.port = IP.NullPort THEN
      IF M3jNet.LocalEndpoint(c.h, addr, port) < 0 THEN
        IPError.Die()
      END;
      c.ep.port := port;
    END;
    RETURN c.ep
  END GetEndPoint;

PROCEDURE Connect (ep: IP.Endpoint): T
    RAISES {IP.Error, Thread.Alerted} =
  VAR
    t := StartConnect(ep);
    ok := FALSE;
  BEGIN
    TRY
      EVAL FinishConnect(t);
      ok := TRUE;
    FINALLY
     IF NOT ok THEN Close(t); END;
    END;
    RETURN t;
  END Connect;

PROCEDURE StartConnect(to: IP.Endpoint;
                       from: IP.Endpoint := IP.NullEndPoint): T
    RAISES {IP.Error} =
  VAR h, err: INTEGER;
  BEGIN
    h := M3jNet.Connect(ToInt(to.addr), to.port, ToInt(from.addr), from.port);
    IF h < 0 THEN
      err := -h;
      CASE err OF
      | M3jNet.TooMany =>
          IPError.Raise(IP.NoResources, err);
      | M3jNet.AddrInUse =>
          RaiseWSA(IP.PortBusy, err);
      | M3jNet.AddrNotAvail,
        M3jNet.ConnRefused,
        M3jNet.ConnReset =>
          IPError.Raise(Refused, err);
      | M3jNet.TimedOut =>
          IPError.Raise(Timeout, err);
      | M3jNet.NetUnreach,
        M3jNet.HostUnreach,
        HostDown,
        M3jNet.NetDown =>
          IPError.Raise(IP.Unreachable, err);
      ELSE
          Ouch(err, "TCP.CheckConnect");
      END;
    END;
    RETURN NEW(T, h := h, ep := to);
  END StartConnect;

PROCEDURE FinishConnect(t: T; timeout: LONGREAL := -1.0D0): BOOLEAN
    RAISES {IP.Error, Thread.Alerted} =
  VAR r, w: INTEGER;
  BEGIN
    LOCK t DO
      IF t.error # NIL THEN RAISE IP.Error(t.error); END;

      (* Wait for the connection to finish *)
      LOOP
        r := M3jNet.FinishConnect(t.h);
        w := 1;
        IF r = 0 THEN
          w := M3jNet.Wait(t.h, FALSE, timeout, TRUE);
          r := M3jNet.FinishConnect(t.h);
        END;
        IF Thread.TestAlert() THEN RAISE Thread.Alerted; END;
        IF r = 1 THEN
          (* connect succeeded *)
          RETURN TRUE;
        ELSIF r < 0 THEN
          (* connect failed *)
          IPError.Raise(Refused, 0);
        END;
        IF w = 0 THEN RETURN FALSE; END;
      END;
    END;
  END FinishConnect;

PROCEDURE Accept (c: Connector): T
    RAISES {IP.Error, Thread.Alerted} =
  VAR
    h, addr, port, err: INTEGER;
  BEGIN
    LOOP
      LOCK c DO
        IF c.closed THEN IPError.Raise(Closed, 0); END;
        h := M3jNet.Accept(c.h, addr, port);
        IF h > 0 THEN EXIT; END;
        err := -h;
      END;
      IF    err = M3jNet.TooMany    THEN  IPError.Raise(IP.NoResources, err);
      ELSIF err = M3jNet.WouldBlock THEN  EVAL IOWait(c.h, TRUE, TRUE);
      ELSE                                Ouch(err, "TCP.Accept");
      END;
    END;
    RETURN NEW(T, h := h, ep := IP.NullEndPoint);
  END Accept;

PROCEDURE CloseConnector(c: Connector) =
  BEGIN
    LOCK c DO
      IF NOT c.closed THEN
        M3jNet.Close(c.h);
        c.closed := TRUE;
      END;
    END;
  END CloseConnector;

PROCEDURE EOF(t: T) : BOOLEAN =
  BEGIN
    LOCK t DO
      IF NOT t.closed THEN
        RETURN M3jNet.EOF(t.h) # 0;
      END;
    END;
    RETURN FALSE;
  END EOF;


(* methods of TCP.T *)

PROCEDURE Close(t: T) =
  BEGIN
    LOCK t DO
      IF NOT t.closed THEN
        M3jNet.Close(t.h);
        t.closed := TRUE;
        t.error := IPError.ClosedErr;
      END;
    END;
  END Close;

PROCEDURE GetBytesFD(
    t: T; VAR arr: ARRAY OF CHAR; timeout: LONGREAL) : CARDINAL
    RAISES {Rd.Failure, ConnFD.TimedOut, Thread.Alerted} =
  VAR len, err: INTEGER;
  BEGIN
    LOOP
      LOCK t DO
        IF t.error # NIL THEN RAISE Rd.Failure(t.error); END;
        len := M3jNet.Recv(t.h, arr);
        IF len >= 0 THEN RETURN len; END;
        err := -len;
      END;
      CASE err OF
      | M3jNet.ConnReset =>
          RETURN 0;
      | NetReset =>
          SetError(t, ConnLost, err);
      | M3jNet.NetUnreach,
        M3jNet.HostUnreach,
        HostDown,
        M3jNet.NetDown =>
          SetError(t, IP.Unreachable, err);
      | M3jNet.WouldBlock =>
          IF timeout = 0.0D0 OR
                IOWait(t.h, TRUE, TRUE, timeout) = WaitResult.Timeout THEN
            RAISE ConnFD.TimedOut;
          END;
      ELSE
          SetError(t, IPError.Unexpected, err, "TCP.GetBytesFD");
      END;
      (* loop to raise error *)
    END;
  END GetBytesFD;

PROCEDURE PutBytesFD(t: T; READONLY arr: ARRAY OF CHAR)
    RAISES {Wr.Failure, Thread.Alerted} =
  VAR pos := 0;  len, err: INTEGER;
  BEGIN
    WHILE pos # NUMBER(arr) DO
      LOCK t DO
        IF t.error # NIL THEN RAISE Wr.Failure(t.error); END;
        len := M3jNet.Send(t.h, SUBARRAY(arr, pos, NUMBER(arr)-pos));
        IF len < 0 THEN  err := -len;  END;
      END;
      IF len < 0 THEN
        CASE err OF
        | M3jNet.ConnReset,
          M3jNet.ConnAborted,
          NetReset =>
            SetError(t, ConnLost, err);
        | M3jNet.NetUnreach,
          M3jNet.HostUnreach,
          HostDown,
          M3jNet.NetDown =>
            SetError(t, IP.Unreachable, err);
        | M3jNet.WouldBlock =>
            EVAL IOWait(t.h, FALSE, TRUE);
        ELSE
            SetError(t, IPError.Unexpected, err, "TCP.PutBytesFD");
        END;
      ELSE
        INC(pos, len)
      END;
    END;
  END PutBytesFD;

PROCEDURE SetError(t: T; atom: Atom.T;  err: INTEGER;  msg: TEXT := NIL) =
  VAR xx: AtomList.T := NIL;
  BEGIN
    IF (msg # NIL) THEN xx := AtomList.Cons (Atom.FromText(msg), NIL); END;
    xx := AtomList.Cons(Atom.FromText(Fmt.Int(err)), xx);
    xx := AtomList.Cons(atom, xx);
    LOCK t DO
      t.error := xx;
    END;
  END SetError;

PROCEDURE Ouch(err: INTEGER;  msg: TEXT) RAISES {IP.Error} =
  BEGIN
    RAISE IP.Error(AtomList.List3(IPError.Unexpected,
                                  Atom.FromText(Fmt.Int(err)),
                                  Atom.FromText(msg)));
  END Ouch;

PROCEDURE ShutdownIn(t: T) RAISES {Rd.Failure} =
  BEGIN
    LOCK t DO
      IF t.error # NIL THEN RAISE Rd.Failure(t.error); END;
      EVAL M3jNet.Shutdown(t.h, 0);
    END;
  END ShutdownIn;

PROCEDURE ShutdownOut(t: T) RAISES {Wr.Failure} =
  BEGIN
    LOCK t DO
      IF t.error # NIL THEN RAISE Wr.Failure(t.error); END;
      EVAL M3jNet.Shutdown(t.h, 1);
    END;
  END ShutdownOut;

PROCEDURE IOWait(h: INTEGER; read: BOOLEAN; alert: BOOLEAN;
                  timeoutInterval: LONGREAL := -1.0D0): WaitResult
                  RAISES {Thread.Alerted} =
  VAR r := M3jNet.Wait(h, NOT read, timeoutInterval, alert);
  BEGIN
    IF alert AND Thread.TestAlert() THEN RAISE Thread.Alerted; END;
    IF r = 0 THEN RETURN WaitResult.Timeout; END;
    RETURN WaitResult.Ready;
  END IOWait;

PROCEDURE RaiseWSA(a: Atom.T; err: INTEGER) RAISES {IP.Error} =
  BEGIN
    RAISE IP.Error(AtomList.List2(a, Atom.FromText(Fmt.Int(err))));
  END RaiseWSA;

PROCEDURE RaiseUnexpected(err: INTEGER) RAISES {IP.Error} =
  BEGIN
    RaiseWSA(Unexpected, err);
  END RaiseUnexpected;

PROCEDURE RaiseNoEC(a: Atom.T) RAISES {IP.Error} =
  BEGIN
    RAISE IP.Error(AtomList.List1(a));
  END RaiseNoEC;

(*****************************************************************************)
(* TCPMisc procedures. *)
(*****************************************************************************)

PROCEDURE AcceptFrom(c: Connector; VAR (*OUT*) peer: IP.Endpoint): T
    RAISES {IP.Error, Thread.Alerted} =
  VAR h, addr, port, err: INTEGER;
  BEGIN
    LOOP
      LOCK c DO
        IF c.closed THEN RaiseNoEC(Closed); END;
        h := M3jNet.Accept(c.h, addr, port);
      END;
      IF h > 0 THEN EXIT; END;
      err := -h;
      IF err = M3jNet.TooMany THEN
        RaiseWSA(IP.NoResources, err);
      ELSIF err = M3jNet.WouldBlock THEN
        EVAL IOWait(c.h, TRUE, TRUE);
      ELSE
        RaiseUnexpected(err);
      END
    END;
    peer.addr := FromInt(addr);
    peer.port := port;
    RETURN NEW(T, h := h, ep := IP.NullEndPoint);
  END AcceptFrom;

PROCEDURE SetOption(tcp: T; option: INTEGER; on: BOOLEAN) RAISES {IP.Error} =
  VAR r: INTEGER;
  BEGIN
    LOCK tcp DO
      IF tcp.closed THEN
        RAISE IP.Error(AtomList.List1(Closed));
      END;
      r := M3jNet.SetOption(tcp.h, option, on);
    END;
    IF r < 0 THEN RaiseUnexpected(-r); END;
  END SetOption;

PROCEDURE CoalesceWrites(tcp: T; allow: BOOLEAN)
  RAISES {IP.Error} =
  BEGIN
    SetOption(tcp, M3jNet.NoDelay, NOT allow);
  END CoalesceWrites;

PROCEDURE ConnectFrom(to, from: IP.Endpoint): T
  RAISES {IP.Error, Thread.Alerted} =
  VAR
    t := StartConnect(to, from);
    ok := FALSE;
  BEGIN
    TRY
      EVAL FinishConnect(t);
      ok := TRUE;
    FINALLY
     IF NOT ok THEN Close(t); END;
    END;
    RETURN t;
  END ConnectFrom;

PROCEDURE GetPeerName(tcp: T): IP.Endpoint
  RAISES {IP.Error} =
  VAR r, addr, port: INTEGER;  ep: IP.Endpoint;
  BEGIN
    LOCK tcp DO
      IF tcp.closed THEN
        RAISE IP.Error(AtomList.List1(Closed));
      END;
      r := M3jNet.PeerEndpoint(tcp.h, addr, port);
    END;
    IF r < 0 THEN RaiseUnexpected(-r); END;
    ep.addr := FromInt(addr);
    ep.port := port;
    RETURN ep;
  END GetPeerName;

PROCEDURE GetSockName(tcp: T): IP.Endpoint
  RAISES {IP.Error} =
  VAR r, addr, port: INTEGER;  ep: IP.Endpoint;
  BEGIN
    LOCK tcp DO
      IF tcp.closed THEN
        RAISE IP.Error(AtomList.List1(Closed));
      END;
      r := M3jNet.LocalEndpoint(tcp.h, addr, port);
    END;
    IF r < 0 THEN RaiseUnexpected(-r); END;
    ep.addr := FromInt(addr);
    ep.port := port;
    RETURN ep;
  END GetSockName;

PROCEDURE KeepAlive(tcp: T; allow: BOOLEAN)
  RAISES {IP.Error} =
  BEGIN
    SetOption(tcp, M3jNet.KeepAlive, allow);
  END KeepAlive;

PROCEDURE LingerOnClose(tcp: T; allow: BOOLEAN)
  RAISES {IP.Error} =
  BEGIN
    SetOption(tcp, M3jNet.Linger, allow);
  END LingerOnClose;

(*****************************************************************************)
(* TCPPeer procedures. *)
(*****************************************************************************)

PROCEDURE Get (channel: T): IP.Endpoint RAISES {IP.Error} =
  BEGIN
    RETURN PeerOf(channel);
  END Get;

PROCEDURE GetName (channel: T): TEXT RAISES {IP.Error} =
  BEGIN
    RETURN IP.GetCanonicalByAddr(PeerOf(channel).addr);
  END GetName;

PROCEDURE Match (channel: T; address: IP.Address; maskBits: [0 .. 32]):
  BOOLEAN RAISES {IP.Error} =
  VAR peer, mask: INTEGER;
  BEGIN
    peer := Word.Extract(ToInt(PeerOf(channel).addr), 32 - maskBits, maskBits);
    mask := Word.Extract(ToInt(address), 32 - maskBits, maskBits);
    RETURN (peer = mask);
  END Match;

PROCEDURE PeerOf (channel: T): IP.Endpoint RAISES {IP.Error} =
  VAR r, addr, port: INTEGER;  ep: IP.Endpoint;
  BEGIN
    LOCK channel DO
      IF (channel.closed) THEN IPError.Raise (Closed); END;
      r := M3jNet.PeerEndpoint(channel.h, addr, port);
    END;
    IF r < 0 THEN IPError.Raise(IPError.Unexpected, -r); END;
    ep.addr := FromInt(addr);
    ep.port := port;
    RETURN ep;
  END PeerOf;

(*****************************************************************************)
(* TCPExtras procedures. *)
(*****************************************************************************)

PROCEDURE LocalEndpoint (conn: T): IP.Endpoint RAISES {IP.Error} =
  VAR r, addr, port: INTEGER;  ep: IP.Endpoint;
  BEGIN
    LOCK conn DO
      IF conn.closed THEN IPError.Raise (Closed); END;
      r := M3jNet.LocalEndpoint(conn.h, addr, port);
    END;
    IF r < 0 THEN IPError.Raise(IPError.Unexpected, -r); END;
    ep.addr := FromInt(addr);
    ep.port := port;
    RETURN ep;
  END LocalEndpoint;

PROCEDURE htons (s: Ctypes.unsigned_short_int): Ctypes.unsigned_short_int =
  BEGIN
    RETURN s MOD 256 * 256 + s DIV 256;
  END htons;

PROCEDURE ntohs (s: Ctypes.unsigned_short_int): Ctypes.unsigned_short_int =
  BEGIN
    RETURN s MOD 256 * 256 + s DIV 256;
  END ntohs;

BEGIN
END TCP.
