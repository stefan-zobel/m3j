(* TCP connections on the loopback address, within one program: a
   connector on a port that the system chooses, connections, readers and
   writers on them (ConnRW) and messages (ConnMsgRW: short ones, ones
   larger than a buffer, ones flushed in parts, an empty one), many
   connections at once, the end of a connection, reads with a timeout,
   the options of TCPMisc, the endpoints of both sides, the errors of a
   refused connection, of a busy port, of a closed connection and of a
   closed connector, alerts to threads that wait in Accept and in a read,
   and addresses and ports of IP and TCPExtras.

   With the argument "hand-checked" the program tests what cm3 gets wrong,
   to be compared with a file checked by hand: messages (cm3 fails with
   a range error in Rd.m3 of libm3 at the second message that is read to
   its end), TCPPeer.Match (cm3 compares other bits of the address than
   the first ones on a little-endian machine) and a second connector on
   the port of an open one (cm3 makes it on Windows, which lets a socket
   with SO_REUSEADDR take a port in use, instead of raising IP.PortBusy).

   The output does not depend on the port or on the address of the
   machine: it has checks, sizes and the atoms of errors only. *)

MODULE Main;

IMPORT Atom, AtomList, ConnFD, ConnMsgRW, ConnRW, Fmt, IO, IP, MsgRd, MsgWr,
       Params, Rd, Stdio, TCP, TCPExtras, TCPMisc, TCPPeer, TCPSpecial, Text, Thread,
       Wr;

<* FATAL Wr.Failure, Thread.Alerted *>

PROCEDURE Out (t: TEXT) =
  BEGIN
    IO.Put (t & "\n");
    Wr.Flush (Stdio.stdout);
  END Out;

PROCEDURE Check (name: TEXT; ok: BOOLEAN) =
  BEGIN
    IF ok THEN Out (name & ": ok") ELSE Out (name & ": FAILED") END;
  END Check;

PROCEDURE Atoms (l: AtomList.T): TEXT =
  (* the atoms of an error list; numbers of errors of the system as "#" *)
  VAR t := "";
  BEGIN
    WHILE l # NIL DO
      IF l.head = NIL THEN
        t := t & " NIL";
      ELSE
        WITH n = Atom.ToText (l.head) DO
          IF Text.Length (n) > 0
               AND Text.GetChar (n, 0) IN SET OF CHAR {'0'..'9'} THEN
            t := t & " #";
          ELSE
            t := t & " " & n;
          END;
        END;
      END;
      l := l.tail;
    END;
    RETURN "[" & t & " ]";
  END Atoms;

PROCEDURE ToInt (t: TEXT): INTEGER =
  VAR n := 0;
  BEGIN
    FOR i := 0 TO Text.Length (t) - 1 DO
      n := n * 10 + ORD (Text.GetChar (t, i)) - ORD ('0');
    END;
    RETURN n;
  END ToInt;

VAR
  conn: TCP.Connector;
  ep: IP.Endpoint;          (* where conn is, on the loopback address *)
  loopback := IP.Address {a := ARRAY OF BITS 8 FOR [0..255] {127, 0, 0, 1}};

(* A server: accepts one connection on conn and runs serve on it. *)
TYPE
  Server = Thread.Closure OBJECT
    serve: PROCEDURE (t: TCP.T);
  OVERRIDES
    apply := ServerApply;
  END;

PROCEDURE ServerApply (s: Server): REFANY =
  <* FATAL IP.Error *>
  VAR t := TCP.Accept (conn);
  BEGIN
    s.serve (t);
    RETURN NIL;
  END ServerApply;

PROCEDURE Serve (serve: PROCEDURE (t: TCP.T)): Thread.T =
  BEGIN
    RETURN Thread.Fork (NEW (Server, serve := serve));
  END Serve;

(* Echo: lines back, in upper case, until "bye". *)
PROCEDURE Echo (t: TCP.T) =
  <* FATAL Rd.Failure, Rd.EndOfFile *>
  VAR
    rd := ConnRW.NewRd (t);
    wr := ConnRW.NewWr (t);
    line: TEXT;
  BEGIN
    LOOP
      line := Rd.GetLine (rd);
      IF Text.Equal (line, "bye") THEN EXIT END;
      Wr.PutText (wr, Upper (line) & "\n");
      Wr.Flush (wr);
    END;
    Wr.PutText (wr, "done\n");
    Wr.Flush (wr);
    TCP.Close (t);
  END Echo;

PROCEDURE Upper (t: TEXT): TEXT =
  VAR a := NEW (REF ARRAY OF CHAR, Text.Length (t));
  BEGIN
    Text.SetChars (a^, t);
    FOR i := 0 TO LAST (a^) DO
      IF a[i] IN SET OF CHAR {'a'..'z'} THEN
        a[i] := VAL (ORD (a[i]) - ORD ('a') + ORD ('A'), CHAR);
      END;
    END;
    RETURN Text.FromChars (a^);
  END Upper;

PROCEDURE TestStream () =
  <* FATAL IP.Error, Rd.Failure, Rd.EndOfFile *>
  VAR
    th := Serve (Echo);
    t := TCP.Connect (ep);
    rd := ConnRW.NewRd (t);
    wr := ConnRW.NewWr (t);
    long := "";
  BEGIN
    Wr.PutText (wr, "hello\n");
    Wr.Flush (wr);
    Out ("stream: " & Rd.GetLine (rd));
    FOR i := 1 TO 20000 DO long := long & Fmt.Int (i MOD 10) END;
    Wr.PutText (wr, long & "\n");
    Wr.Flush (wr);
    Check ("stream long line", Text.Equal (Rd.GetLine (rd), Upper (long)));
    Wr.PutText (wr, "a\nb\nc\nbye\n");
    Wr.Flush (wr);
    Out ("stream: " & Rd.GetLine (rd) & Rd.GetLine (rd) & Rd.GetLine (rd)
           & " " & Rd.GetLine (rd));
    Check ("stream eof", Rd.EOF (rd));
    Check ("stream TCPSpecial.EOF", TCPSpecial.EOF (t));
    EVAL Thread.Join (th);
    TCP.Close (t);
  END TestStream;

(* Messages: each message back with its length in front, until an empty
   one. A reader of messages starts at the end of an empty message:
   nextMsg goes to the next one. *)
PROCEDURE MsgEcho (t: TCP.T) =
  <* FATAL Rd.Failure *>
  VAR
    rd := ConnMsgRW.NewRd (t);
    wr := ConnMsgRW.NewWr (t);
    m: TEXT;
  BEGIN
    LOOP
      EVAL rd.nextMsg ();
      m := Rd.GetText (rd, LAST (CARDINAL));
      Wr.PutText (wr, Fmt.Int (Text.Length (m)) & ":" & m);
      wr.nextMsg ();
      IF Text.Length (m) = 0 THEN EXIT END;
    END;
    TCP.Close (t);
  END MsgEcho;

VAR
  mrd: MsgRd.T;
  mwr: MsgWr.T;

PROCEDURE SendMsg (m: TEXT; parts: CARDINAL): TEXT =
  <* FATAL Rd.Failure *>
  VAR n := Text.Length (m);  k := 0;  r: TEXT;
  BEGIN
    FOR p := 1 TO parts DO
      WITH e = n * p DIV parts DO
        Wr.PutText (mwr, Text.Sub (m, k, e - k));
        Wr.Flush (mwr);
        k := e;
      END;
    END;
    mwr.nextMsg ();
    EVAL mrd.nextMsg ();
    r := Rd.GetText (mrd, LAST (CARDINAL));
    RETURN r;
  END SendMsg;

PROCEDURE TestMsg () =
  <* FATAL IP.Error, Rd.Failure *>
  VAR
    th := Serve (MsgEcho);
    t := TCP.Connect (ep);
    big := "";
    r: TEXT;
  BEGIN
    mrd := ConnMsgRW.NewRd (t);
    mwr := ConnMsgRW.NewWr (t);
    Out ("msg: " & SendMsg ("x", 1));
    Out ("msg: " & SendMsg ("abc", 3));
    Out ("msg: " & SendMsg ("hello, world", 5));
    FOR i := 1 TO 30000 DO big := big & Text.FromChar (VAL (ORD ('a') + i MOD 26, CHAR)) END;
    r := SendMsg (big, 1);
    Check ("msg big", Text.Equal (r, "30000:" & big));
    r := SendMsg (big, 7);
    Check ("msg big in parts", Text.Equal (r, "30000:" & big));
    mwr.nextMsg ();
    EVAL mrd.nextMsg ();
    Out ("msg: " & Rd.GetText (mrd, LAST (CARDINAL)));
    EVAL Thread.Join (th);
    TCP.Close (t);
  END TestMsg;

(* Many connections at once: each server counts the characters of the
   lines it reads. *)
PROCEDURE Count (t: TCP.T) =
  <* FATAL Rd.Failure, Rd.EndOfFile *>
  VAR
    rd := ConnRW.NewRd (t);
    wr := ConnRW.NewWr (t);
    sum := 0;
    line: TEXT;
  BEGIN
    LOOP
      line := Rd.GetLine (rd);
      IF Text.Equal (line, "end") THEN EXIT END;
      INC (sum, Text.Length (line));
    END;
    Wr.PutText (wr, Fmt.Int (sum) & "\n");
    Wr.Flush (wr);
    TCP.Close (t);
  END Count;

PROCEDURE TestMany () =
  <* FATAL IP.Error, Rd.Failure, Rd.EndOfFile *>
  CONST N = 20;
  VAR
    th: ARRAY [1..N] OF Thread.T;
    t: ARRAY [1..N] OF TCP.T;
    wr: ARRAY [1..N] OF Wr.T;
    total := 0;
  BEGIN
    FOR i := 1 TO N DO th[i] := Serve (Count) END;
    FOR i := 1 TO N DO
      t[i] := TCP.Connect (ep);
      wr[i] := ConnRW.NewWr (t[i]);
    END;
    FOR round := 1 TO 50 DO
      FOR i := 1 TO N DO
        Wr.PutText (wr[i], Fmt.Pad ("", i * round MOD 13 + 1, '*') & "\n");
      END;
    END;
    FOR i := 1 TO N DO
      Wr.PutText (wr[i], "end\n");
      Wr.Flush (wr[i]);
    END;
    FOR i := 1 TO N DO
      total := total + ToInt (Rd.GetLine (ConnRW.NewRd (t[i])));
      EVAL Thread.Join (th[i]);
      TCP.Close (t[i]);
    END;
    Out ("many: total " & Fmt.Int (total));
  END TestMany;

(* Reads with a timeout, the end of a connection, options and endpoints. *)
VAR
  serverSide: TCP.T;
  gate := NEW (MUTEX);
  ready := NEW (Thread.Condition);
  phase := 0;

PROCEDURE Await (p: INTEGER) =
  BEGIN
    LOCK gate DO
      WHILE phase < p DO Thread.Wait (gate, ready) END;
    END;
  END Await;

PROCEDURE Advance () =
  BEGIN
    LOCK gate DO INC (phase) END;
    Thread.Broadcast (ready);
  END Advance;

PROCEDURE Hold (t: TCP.T) =
  (* keeps the connection, writes "x" in phase 1, closes it in phase 2 *)
  BEGIN
    serverSide := t;
    Advance ();                    (* phase 1 *)
    Await (2);
    t.put (ARRAY [0..0] OF CHAR {'x'});
    Await (3);
    TCP.Close (t);
    Advance ();                    (* phase 4 *)
  END Hold;

PROCEDURE TestTimeout () =
  <* FATAL IP.Error, Rd.Failure, ConnFD.TimedOut *>
  VAR
    th := Serve (Hold);
    t := TCP.Connect (ep);
    buf: ARRAY [0..9] OF CHAR;
    n: CARDINAL;
    local, peer: IP.Endpoint;
  BEGIN
    Await (1);
    TRY
      n := t.get (buf, 0.0D0);
      Out ("timeout 0: got " & Fmt.Int (n));
    EXCEPT ConnFD.TimedOut => Out ("timeout 0: TimedOut");
    END;
    TRY
      n := t.get (buf, 0.2D0);
      Out ("timeout 0.2: got " & Fmt.Int (n));
    EXCEPT ConnFD.TimedOut => Out ("timeout 0.2: TimedOut");
    END;
    Check ("not EOF while open", NOT TCPSpecial.EOF (t));
    Advance ();                    (* phase 2 *)
    TRY
      n := t.get (buf, 10.0D0);
      Out ("timeout 10: got " & Fmt.Int (n) & " " & Text.FromChars (SUBARRAY (buf, 0, n)));
    EXCEPT ConnFD.TimedOut => Out ("timeout 10: TimedOut");
    END;

    (* endpoints *)
    local := TCPMisc.GetSockName (t);
    peer := TCPMisc.GetPeerName (t);
    Check ("peer is the connector", peer.port = ep.port AND peer.addr = loopback);
    Check ("TCPPeer.Get", TCPPeer.Get (t) = peer);
    Check ("TCPExtras.LocalEndpoint", TCPExtras.LocalEndpoint (t) = local);
    Check ("server's peer is the client",
           TCPMisc.GetPeerName (serverSide) = local);
    Check ("server's local port", TCPMisc.GetSockName (serverSide).port = ep.port);
    Check ("TCPPeer.Match 32", NOT TCPPeer.Match (t, IP.Address {a := ARRAY OF BITS 8 FOR [0..255] {127, 0, 0, 99}}, 32));
    Check ("TCPPeer.Match 0", TCPPeer.Match (t, IP.NullAddress, 0));

    (* options *)
    TCPMisc.CoalesceWrites (t, TRUE);
    TCPMisc.CoalesceWrites (t, FALSE);
    TCPMisc.KeepAlive (t, TRUE);
    TCPMisc.LingerOnClose (t, TRUE);
    TCPMisc.LingerOnClose (t, FALSE);
    Out ("options: set");

    Advance ();                    (* phase 3: the server closes *)
    Await (4);
    n := t.get (buf, 10.0D0);
    Out ("after close by peer: got " & Fmt.Int (n));
    Check ("EOF after close by peer", TCPSpecial.EOF (t));
    EVAL Thread.Join (th);
    TCP.Close (t);
  END TestTimeout;

(* Errors. *)
PROCEDURE TestErrors () =
  <* FATAL IP.Error *>
  VAR
    c: TCP.Connector;
    cep: IP.Endpoint;
    t: TCP.T;
    buf: ARRAY [0..9] OF CHAR;
  BEGIN
    (* a refused connection: a connector closed before *)
    c := TCP.NewConnector (IP.Endpoint {IP.NullAddress, IP.NullPort});
    cep := TCP.GetEndPoint (c);
    cep.addr := loopback;
    TCP.CloseConnector (c);
    TRY
      t := TCP.Connect (cep);
      Out ("refused: connected");
    EXCEPT IP.Error (e) => Out ("refused: IP.Error " & Atoms (e));
    END;

    (* Accept on a closed connector *)
    TRY
      t := TCP.Accept (c);
      Out ("accept closed: accepted");
    EXCEPT IP.Error (e) => Out ("accept closed: IP.Error " & Atoms (e));
    END;
    TRY
      VAR peer: IP.Endpoint; BEGIN t := TCPMisc.AcceptFrom (c, peer) END;
      Out ("acceptfrom closed: accepted");
    EXCEPT IP.Error (e) => Out ("acceptfrom closed: IP.Error " & Atoms (e));
    END;

    (* a closed connection *)
    WITH th = Serve (Close) DO
      t := TCP.Connect (ep);
      EVAL Thread.Join (th);
    END;
    TCP.Close (t);
    TRY
      EVAL t.get (buf);
      Out ("get closed: got");
    EXCEPT
    | Rd.Failure (e) => Out ("get closed: Rd.Failure " & Atoms (e));
    | ConnFD.TimedOut => Out ("get closed: TimedOut");
    END;
    TRY
      t.put (buf);
      Out ("put closed: put");
    EXCEPT Wr.Failure (e) => Out ("put closed: Wr.Failure " & Atoms (e));
    END;
    TRY
      EVAL TCPSpecial.FinishConnect (t);
      Out ("finish closed: done");
    EXCEPT IP.Error (e) => Out ("finish closed: IP.Error " & Atoms (e));
    END;
    TRY
      EVAL TCPMisc.GetPeerName (t);
      Out ("peer closed: done");
    EXCEPT IP.Error (e) => Out ("peer closed: IP.Error " & Atoms (e));
    END;
    TRY
      EVAL TCPPeer.Get (t);
      Out ("TCPPeer closed: done");
    EXCEPT IP.Error (e) => Out ("TCPPeer closed: IP.Error " & Atoms (e));
    END;
    TRY
      t.shutdownIn ();
      Out ("shutdownIn closed: done");
    EXCEPT Rd.Failure (e) => Out ("shutdownIn closed: Rd.Failure " & Atoms (e));
    END;
    Check ("EOF of a closed connection", NOT TCPSpecial.EOF (t));
    TCP.Close (t);
    Out ("close twice: done");
  END TestErrors;

PROCEDURE Close (t: TCP.T) =
  BEGIN
    TCP.Close (t);
  END Close;

(* Alerts to threads that wait. *)
TYPE
  Waiter = Thread.Closure OBJECT
    what: INTEGER;
    t: TCP.T;
    c: TCP.Connector;
    result: TEXT;
  OVERRIDES
    apply := WaiterApply;
  END;

VAR
  wgate := NEW (MUTEX);
  wcond := NEW (Thread.Condition);
  wstarted := FALSE;

PROCEDURE StartWaiter (w: Waiter): Thread.T =
  (* forks w and returns when it is about to wait *)
  VAR th: Thread.T;
  BEGIN
    wstarted := FALSE;
    th := Thread.Fork (w);
    LOCK wgate DO
      WHILE NOT wstarted DO Thread.Wait (wgate, wcond) END;
    END;
    Thread.Pause (0.2D0);
    RETURN th;
  END StartWaiter;

PROCEDURE WaiterApply (w: Waiter): REFANY =
  VAR buf: ARRAY [0..9] OF CHAR;
  BEGIN
    TRY
      LOCK wgate DO wstarted := TRUE END;
      Thread.Broadcast (wcond);
      CASE w.what OF
      | 0 => EVAL TCP.Accept (w.c);
      | 1 => EVAL w.t.get (buf);
      | 2 => EVAL Rd.GetLine (ConnRW.NewRd (w.t));
      ELSE
      END;
      w.result := "returned";
    EXCEPT
    | Thread.Alerted => w.result := "Thread.Alerted";
    | IP.Error (e) => w.result := "IP.Error " & Atoms (e);
    | Rd.Failure (e) => w.result := "Rd.Failure " & Atoms (e);
    | Rd.EndOfFile => w.result := "Rd.EndOfFile";
    | ConnFD.TimedOut => w.result := "TimedOut";
    END;
    RETURN NIL;
  END WaiterApply;

PROCEDURE TestAlerts () =
  <* FATAL IP.Error *>
  VAR
    c := TCP.NewConnector (IP.Endpoint {IP.NullAddress, IP.NullPort});
    th: Thread.T;
    t: TCP.T;
    w: Waiter;
    wt: Thread.T;
    names := ARRAY [0..2] OF TEXT {"Accept", "get", "GetLine"};
  BEGIN
    phase := 0;
    th := Serve (Hold);
    t := TCP.Connect (ep);
    Await (1);
    FOR what := 0 TO 2 DO
      w := NEW (Waiter, what := what, t := t, c := c);
      wt := StartWaiter (w);
      Thread.Alert (wt);
      EVAL Thread.Join (wt);
      Out ("alert in " & names[what] & ": " & w.result);
    END;
    (* the connection works after the alerts *)
    Advance ();                    (* phase 2: the server writes "x" *)
    VAR buf: ARRAY [0..9] OF CHAR; n: CARDINAL; BEGIN
      TRY
        n := t.get (buf, 10.0D0);
        Out ("after alerts: got " & Text.FromChars (SUBARRAY (buf, 0, n)));
      EXCEPT
      | ConnFD.TimedOut => Out ("after alerts: TimedOut");
      | Rd.Failure (e) => Out ("after alerts: Rd.Failure " & Atoms (e));
      END;
    END;

    (* a thread in Accept when the connector is closed *)
    w := NEW (Waiter, what := 0, c := c);
    wt := StartWaiter (w);
    TCP.CloseConnector (c);
    EVAL Thread.Join (wt);
    Out ("close in Accept: " & w.result);

    Advance ();                    (* phase 3: the server closes *)
    EVAL Thread.Join (th);
    TCP.Close (t);
  END TestAlerts;

(* TCPPeer.Match of the first bits of the address. *)
PROCEDURE TestMatch () =
  <* FATAL IP.Error *>
  VAR
    th := Serve (Close);
    t := TCP.Connect (ep);
  PROCEDURE M (a, b, c, d: [0..255]; bits: [0..32]): TEXT =
    <* FATAL IP.Error *>
    BEGIN
      IF TCPPeer.Match (t, IP.Address {a := ARRAY OF BITS 8 FOR [0..255] {a, b, c, d}}, bits) THEN
        RETURN " T";
      ELSE
        RETURN " F";
      END;
    END M;
  BEGIN
    EVAL Thread.Join (th);
    Out ("match:" & M (127, 0, 0, 99, 24) & M (127, 9, 9, 9, 8) & M (127, 0, 0, 1, 32)
           & M (127, 0, 0, 99, 32) & M (126, 0, 0, 1, 8) & M (127, 0, 0, 1, 1)
           & M (255, 0, 0, 1, 1) & M (1, 2, 3, 4, 0));
    TCP.Close (t);
  END TestMatch;

(* The port of a connector that is open. *)
PROCEDURE TestBusy () =
  BEGIN
    TRY
      WITH c = TCP.NewConnector (IP.Endpoint {IP.NullAddress, ep.port}) DO
        Out ("busy: a second connector");
        TCP.CloseConnector (c);
      END;
    EXCEPT IP.Error (e) => Out ("busy: IP.Error " & Atoms (e));
    END;
  END TestBusy;

(* Addresses. *)
PROCEDURE A (READONLY a: IP.Address): TEXT =
  BEGIN
    RETURN Fmt.Int (a.a[0]) & "." & Fmt.Int (a.a[1]) & "." & Fmt.Int (a.a[2])
             & "." & Fmt.Int (a.a[3]);
  END A;

PROCEDURE TestIP () =
  <* FATAL IP.Error *>
  VAR
    a: IP.Address;
    e: IP.EP;
    host, service: TEXT;
  BEGIN
    Check ("GetHostByName 10.1.2.3",
           IP.GetHostByName ("10.1.2.3", a) AND Text.Equal (A (a), "10.1.2.3"));
    Check ("GetHostByName 127.0.0.1",
           IP.GetHostByName ("127.0.0.1", a) AND a = loopback);
    a := IP.GetHostAddr ();
    Check ("GetHostAddr", a # IP.NullAddress);
    e := IP.GetAddrInfo ("127.0.0.1", "80");
    TYPECASE e OF
    | IP.Endpoint4 (e4) => Out ("GetAddrInfo: " & A (e4.adr) & " port " & Fmt.Int (e4.port));
    ELSE Out ("GetAddrInfo: not IP version 4");
    END;
    e := IP.GetAddrInfo ("127.0.0.1", "8080");
    Out ("GetAddrInfo 8080: port " & Fmt.Int (e.port));
    e := IP.GetAddrInfo ("10.20.30.40", NIL);
    TYPECASE e OF
    | IP.Endpoint4 (e4) => Out ("GetAddrInfo NIL: " & A (e4.adr) & " port " & Fmt.Int (e4.port));
    ELSE Out ("GetAddrInfo NIL: not IP version 4");
    END;
    TRY
      EVAL IP.GetAddrInfo (NIL, NIL);
      Out ("GetAddrInfo NIL NIL: done");
    EXCEPT IP.Error (l) => Out ("GetAddrInfo NIL NIL: IP.Error " & Atoms (l));
    END;
    e := IP.GetAddrInfo ("127.0.0.1", "8080");
    IP.GetNameInfo (e, host, service);
    Out ("GetNameInfo service: " & service);
    Out ("htons 1: " & Fmt.Int (TCPExtras.htons (1)) & ", ntohs 16_1234: "
           & Fmt.Int (TCPExtras.ntohs (16_1234), 16));
    Check ("atoms", Text.Equal (Atom.ToText (TCP.Refused), "TCP.Refused")
                      AND Text.Equal (Atom.ToText (IP.PortBusy), "IP.PortBusy"));
  END TestIP;

<* FATAL IP.Error *>
BEGIN
  conn := TCP.NewConnector (IP.Endpoint {IP.NullAddress, IP.NullPort});
  ep := TCP.GetEndPoint (conn);
  Check ("port chosen", ep.port # IP.NullPort);
  ep.addr := loopback;
  IF Params.Count > 1 AND Text.Equal (Params.Get (1), "hand-checked") THEN
    TestMsg ();
    TestMatch ();
    TestBusy ();
  ELSE
    TestStream ();
    TestMany ();
    TestTimeout ();
    TestErrors ();
    TestAlerts ();
    TestIP ();
  END;
  TCP.CloseConnector (conn);
  Out ("end");
END Main.
