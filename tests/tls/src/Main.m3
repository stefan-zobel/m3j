(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Tests of the library tls, run by tools/tls_test.py in a directory with
   the keys and certificates that it makes:

     Main local            server and client in this program over TCP
     Main client PORT      a client for a server of another TLS implementation
     Main server           the same for a client; prints the port first
     Main speed            the rate of a transfer, with and without TLS *)

MODULE Main;

IMPORT Atom, AtomList, ConnFD, ConnMsgRW, ConnRW, Fmt, IO, IP, MsgRd, MsgWr, Params,
       Process, Rd, Scan, Stdio, TCP, Text, Thread, Time, TLS, Wr;

<*FATAL ANY*>

CONST Password = "changeit";

PROCEDURE Out (t: TEXT) =
  BEGIN
    IO.Put (t & "\n");
    Wr.Flush (Stdio.stdout);
  END Out;

PROCEDURE First (l: AtomList.T): TEXT =
  BEGIN
    RETURN Atom.ToText (l.head);
  END First;

PROCEDURE Opt (t: TEXT): TEXT =
  BEGIN
    IF t = NIL THEN RETURN "NIL" END;
    RETURN t;
  END Opt;

PROCEDURE At (i: INTEGER): CHAR =
  BEGIN
    RETURN VAL ((i * 7 + i DIV 251) MOD 256, CHAR);
  END At;

PROCEDURE Loopback (port: IP.Port): IP.Endpoint =
  VAR a: IP.Address;
  BEGIN
    EVAL IP.GetHostByName ("localhost", a);
    RETURN IP.Endpoint {a, port};
  END Loopback;

(* a server thread *)

TYPE
  Body = PROCEDURE (s: Srv; t: TLS.T);
  Srv = Thread.Closure OBJECT
    c: TCP.Connector;
    ep: IP.Endpoint;
    th: Thread.T;
    conn: TCP.T;
    ctx: TLS.Context;
    need := FALSE;
    alpn: TEXT := NIL;
    plain := FALSE;
    body: Body := NIL;
    result := "";
  OVERRIDES
    apply := RunServer;
  END;

PROCEDURE RunServer (s: Srv): REFANY =
  VAR t: TLS.T;  b: ARRAY [0 .. 99] OF CHAR;
  BEGIN
    s.conn := TCP.Accept (s.c);
    TRY
      IF s.plain THEN
        s.conn.put (ARRAY OF CHAR {'n', 'o', 't', ' ', 'T', 'L', 'S', ' ', 'a', 't', ' ', 'a', 'l', 'l', '\n'});
        WHILE s.conn.get (b) > 0 DO END;
        s.conn.close ();
        RETURN NIL;
      END;
      t := TLS.Server (s.ctx, s.conn, s.need, s.alpn);
      IF s.body # NIL THEN
        s.body (s, t);
      ELSE
        WHILE t.get (b) > 0 DO END;
      END;
      t.close ();
    EXCEPT
    | IP.Error (l) => s.result := s.result & "IP.Error " & First (l);
    | Rd.Failure (l) => s.result := s.result & "Rd.Failure " & First (l);
    | Wr.Failure (l) => s.result := s.result & "Wr.Failure " & First (l);
    END;
    RETURN NIL;
  END RunServer;

PROCEDURE Start (ctx: TLS.Context; body: Body := NIL; need := FALSE; alpn: TEXT := NIL;
                 plain := FALSE): Srv =
  VAR s := NEW (Srv, ctx := ctx, body := body, need := need, alpn := alpn, plain := plain);
  BEGIN
    s.c := TCP.NewConnector (IP.Endpoint {IP.NullAddress, IP.NullPort});
    s.ep := Loopback (TCP.GetEndPoint (s.c).port);
    s.th := Thread.Fork (s);
    RETURN s;
  END Start;

PROCEDURE Finish (name: TEXT; s: Srv) =
  BEGIN
    EVAL Thread.Join (s.th);
    TCP.CloseConnector (s.c);
    IF NOT Text.Equal (s.result, "") THEN Out (name & ": server: " & s.result) END;
  END Finish;

PROCEDURE TryClient (name: TEXT; ctx: TLS.Context; s: Srv; host: TEXT; alpn: TEXT := NIL) =
  VAR t: TLS.T;  b: ARRAY [0 .. 99] OF CHAR;
  BEGIN
    TRY
      t := TLS.Client (ctx, TCP.Connect (s.ep), host, alpn);
      Out (name & ": " & TLS.Version (t) & " protocol " & Opt (TLS.Protocol (t))
           & " peer " & Opt (TLS.PeerName (t)));
      (* reads to the end: closing with unread bytes resets the connection *)
      t.shutdownOut ();
      WHILE t.get (b) > 0 DO END;
      t.close ();
    EXCEPT
    | IP.Error (l) => Out (name & ": IP.Error " & First (l));
    END;
    Finish (name, s);
  END TryClient;

VAR
  server, wrongName, expired, foreign: TLS.Context;
  client, withCert: TLS.Context;

(* data *)

PROCEDURE EchoLines (<*UNUSED*> s: Srv; t: TLS.T) =
  VAR rd := ConnRW.NewRd (t);  wr := ConnRW.NewWr (t);
  BEGIN
    WHILE NOT Rd.EOF (rd) DO
      Wr.PutText (wr, "echo " & Rd.GetLine (rd) & "\n");
      Wr.Flush (wr);
    END;
  END EchoLines;

PROCEDURE TestLines () =
  VAR s := Start (server, EchoLines);
      t := TLS.Client (client, TCP.Connect (s.ep), "localhost");
      rd := ConnRW.NewRd (t);  wr := ConnRW.NewWr (t);
  BEGIN
    Out ("lines: " & TLS.Version (t) & " peer " & Opt (TLS.PeerName (t)));
    FOR i := 1 TO 3 DO
      Wr.PutText (wr, "line " & Fmt.Int (i) & "\n");
      Wr.Flush (wr);
      Out ("lines: " & Rd.GetLine (rd));
    END;
    Wr.Close (wr);
    Out ("lines: EOF " & Fmt.Bool (Rd.EOF (rd)));
    t.close ();
    Finish ("lines", s);
  END TestLines;

PROCEDURE EchoRaw (<*UNUSED*> s: Srv; t: TLS.T) =
  VAR b := NEW (REF ARRAY OF CHAR, 5000);  n: CARDINAL;
  BEGIN
    LOOP
      n := t.get (b^);
      IF n = 0 THEN EXIT END;
      t.put (SUBARRAY (b^, 0, n));
    END;
  END EchoRaw;

TYPE
  Writer = Thread.Closure OBJECT
    wr: Wr.T;
    size: CARDINAL;
  OVERRIDES
    apply := WriteBlock;
  END;

PROCEDURE WriteBlock (w: Writer): REFANY =
  VAR b := NEW (REF ARRAY OF CHAR, w.size);
  BEGIN
    FOR i := 0 TO w.size - 1 DO b[i] := At (i) END;
    Wr.PutString (w.wr, b^);
    Wr.Flush (w.wr);
    RETURN NIL;
  END WriteBlock;

(* Sends a block of the given size and reads it back at the same time. *)
PROCEDURE Exchange (name: TEXT; rd: Rd.T; wr: Wr.T; size: CARDINAL) =
  VAR
    th := Thread.Fork (NEW (Writer, wr := wr, size := size));
    b := NEW (REF ARRAY OF CHAR, size);
    got := 0;
    bad := 0;
    n: CARDINAL;
  BEGIN
    WHILE got < size DO
      n := Rd.GetSub (rd, SUBARRAY (b^, got, size - got));
      IF n = 0 THEN EXIT END;
      INC (got, n);
    END;
    EVAL Thread.Join (th);
    FOR i := 0 TO got - 1 DO
      IF b[i] # At (i) THEN INC (bad) END;
    END;
    Out (name & " " & Fmt.Int (size) & ": read " & Fmt.Int (got) & " wrong " & Fmt.Int (bad));
  END Exchange;

CONST Sizes = ARRAY OF CARDINAL {1, 100, 16383, 16384, 16385, 70000, 1048576};

PROCEDURE ExchangeAll (name: TEXT; rd: Rd.T; wr: Wr.T) =
  BEGIN
    FOR i := FIRST (Sizes) TO LAST (Sizes) DO
      Exchange (name, rd, wr, Sizes[i]);
    END;
  END ExchangeAll;

PROCEDURE TestBlocks () =
  VAR s := Start (server, EchoRaw);
      t := TLS.Client (client, TCP.Connect (s.ep), "localhost");
  BEGIN
    ExchangeAll ("blocks", ConnRW.NewRd (t), ConnRW.NewWr (t));
    t.close ();
    Finish ("blocks", s);
  END TestBlocks;

PROCEDURE MsgEcho (<*UNUSED*> s: Srv; t: TLS.T) =
  VAR rd := ConnMsgRW.NewRd (t);  wr := ConnMsgRW.NewWr (t);  m: TEXT;
  BEGIN
    LOOP
      EVAL rd.nextMsg ();
      m := Rd.GetText (rd, LAST (CARDINAL));
      Wr.PutText (wr, Fmt.Int (Text.Length (m)) & ":" & m);
      wr.nextMsg ();
      IF Text.Length (m) = 0 THEN EXIT END;
    END;
  END MsgEcho;

PROCEDURE TestMessages () =
  VAR
    s := Start (server, MsgEcho);
    t := TLS.Client (client, TCP.Connect (s.ep), "localhost");
    mrd: MsgRd.T := ConnMsgRW.NewRd (t);
    mwr: MsgWr.T := ConnMsgRW.NewWr (t);
  PROCEDURE Send (m: TEXT) =
    BEGIN
      Wr.PutText (mwr, m);
      mwr.nextMsg ();
      EVAL mrd.nextMsg ();
      Out ("messages: " & Rd.GetText (mrd, LAST (CARDINAL)));
    END Send;
  BEGIN
    Send ("short");
    Send ("hello, world");
    Send ("");
    t.close ();
    Finish ("messages", s);
  END TestMessages;

(* checks of certificates *)

PROCEDURE TestChecks () =
  BEGIN
    TryClient ("name", client, Start (server), "localhost");
    TryClient ("address", client, Start (server), "127.0.0.1");
    TryClient ("other host", client, Start (server), "www.example.org");
    TryClient ("wrong name", client, Start (wrongName), "localhost");
    TryClient ("expired", client, Start (expired), "localhost");
    TryClient ("unknown CA", client, Start (foreign), "localhost");
    TryClient ("not TLS", client, Start (NIL, plain := TRUE), "localhost");
  END TestChecks;

PROCEDURE TestPlainClient () =
  VAR
    s := Start (server);
    conn := TCP.Connect (s.ep);
    b: ARRAY [0 .. 99] OF CHAR;
  BEGIN
    conn.put (ARRAY OF CHAR {'G', 'E', 'T', ' ', '/', ' ', 'H', 'T', 'T', 'P', '\n'});
    WHILE conn.get (b) > 0 DO END;
    conn.close ();
    Finish ("plain client", s);
  END TestPlainClient;

PROCEDURE TestStores () =
  PROCEDURE Bad (name, keyStore, password: TEXT) =
    BEGIN
      TRY
        EVAL TLS.NewContext (keyStore, password);
        Out (name & ": no error");
      EXCEPT
      | IP.Error (l) => Out (name & ": " & First (l));
      END;
    END Bad;
  BEGIN
    Bad ("missing file", "nothing.p12", Password);
    Bad ("wrong password", "server.p12", "wrong");
    TryClient ("trust PKCS12", TLS.NewContext (NIL, NIL, "ca.p12", Password),
               Start (server), "localhost");
  END TestStores;

(* ALPN *)

PROCEDURE TestAlpn () =
  BEGIN
    TryClient ("alpn agreed", client, Start (server, alpn := "b,a"), "localhost", "a,b");
    TryClient ("alpn only client", client, Start (server), "localhost", "a,b");
    TryClient ("alpn only server", client, Start (server, alpn := "a"), "localhost");
    TryClient ("alpn mismatch", client, Start (server, alpn := "c"), "localhost", "a,b");
  END TestAlpn;

(* certificates of clients *)

PROCEDURE Peer (s: Srv; t: TLS.T) =
  VAR b: ARRAY [0 .. 99] OF CHAR;
  BEGIN
    s.result := "peer " & Opt (TLS.PeerName (t));
    WHILE t.get (b) > 0 DO END;
  END Peer;

PROCEDURE TestClientCert () =
  VAR s: Srv;  t: TLS.T;  b: ARRAY [0 .. 99] OF CHAR;
  BEGIN
    TryClient ("client cert", withCert, Start (server, Peer, need := TRUE), "localhost");
    s := Start (server, need := TRUE);
    t := TLS.Client (client, TCP.Connect (s.ep), "localhost");
    TRY
      EVAL t.get (b);
      Out ("no client cert: no error");
    EXCEPT
    | Rd.Failure (l) => Out ("no client cert: Rd.Failure " & First (l));
    END;
    t.close ();
    Finish ("no client cert", s);
  END TestClientCert;

(* timeouts *)

PROCEDURE AnswerGo (<*UNUSED*> s: Srv; t: TLS.T) =
  VAR rd := ConnRW.NewRd (t);  wr := ConnRW.NewWr (t);
  BEGIN
    WHILE NOT Rd.EOF (rd) DO
      EVAL Rd.GetLine (rd);
      Wr.PutText (wr, "answer\n");
      Wr.Flush (wr);
    END;
  END AnswerGo;

PROCEDURE TestTimeouts () =
  VAR
    s := Start (server, AnswerGo);
    t := TLS.Client (client, TCP.Connect (s.ep), "localhost");
    rd := ConnRW.NewRd (t);
    wr := ConnRW.NewWr (t);
    b: ARRAY [0 .. 99] OF CHAR;
    t0: Time.T;
    c: TCP.Connector;
    n: CARDINAL;
  BEGIN
    t0 := Time.Now ();
    TRY
      EVAL t.get (b, 0.2D0);
      Out ("timeout: no timeout");
    EXCEPT
    | ConnFD.TimedOut =>
        Out ("timeout: TimedOut, in time " & Fmt.Bool (Time.Now () - t0 > 0.15D0));
    END;
    TRY
      EVAL t.get (b, 0.0D0);
      Out ("timeout 0: no timeout");
    EXCEPT
    | ConnFD.TimedOut => Out ("timeout 0: TimedOut");
    END;
    Out ("CharsReady: " & Fmt.Int (Rd.CharsReady (rd)));
    Wr.PutText (wr, "go\n");
    Wr.Flush (wr);
    n := t.get (b, 10.0D0);
    Out ("after timeouts: " & Text.FromChars (SUBARRAY (b, 0, n - 1)));
    t.close ();
    Finish ("timeout", s);

    c := TCP.NewConnector (IP.Endpoint {IP.NullAddress, IP.NullPort});
    TRY
      EVAL TLS.Client (client, TCP.Connect (Loopback (TCP.GetEndPoint (c).port)), "localhost",
                       waitFor := 0.2D0);
      Out ("handshake timeout: none");
    EXCEPT
    | IP.Error (l) => Out ("handshake timeout: IP.Error " & First (l));
    END;
    TCP.CloseConnector (c);
  END TestTimeouts;

(* alerts *)

TYPE
  Getter = Thread.Closure OBJECT
    t: TLS.T;
    result: TEXT := "none";
  OVERRIDES
    apply := BlockedGet;
  END;

PROCEDURE BlockedGet (g: Getter): REFANY =
  VAR b: ARRAY [0 .. 99] OF CHAR;
  BEGIN
    TRY
      EVAL g.t.get (b);
      g.result := "data";
    EXCEPT
    | Thread.Alerted => g.result := "Thread.Alerted";
    END;
    RETURN NIL;
  END BlockedGet;

TYPE
  Shaker = Thread.Closure OBJECT
    ep: IP.Endpoint;
    result: TEXT := "none";
  OVERRIDES
    apply := BlockedHandshake;
  END;

PROCEDURE BlockedHandshake (h: Shaker): REFANY =
  BEGIN
    TRY
      EVAL TLS.Client (client, TCP.Connect (h.ep), "localhost");
      h.result := "connected";
    EXCEPT
    | Thread.Alerted => h.result := "Thread.Alerted";
    END;
    RETURN NIL;
  END BlockedHandshake;

PROCEDURE TestAlerts () =
  VAR
    s := Start (server, AnswerGo);
    t := TLS.Client (client, TCP.Connect (s.ep), "localhost");
    g := NEW (Getter, t := t);
    th := Thread.Fork (g);
    rd: Rd.T;
    wr: Wr.T;
    c: TCP.Connector;
    h: Shaker;
  BEGIN
    Thread.Pause (0.2D0);
    Thread.Alert (th);
    EVAL Thread.Join (th);
    Out ("alert in get: " & g.result);
    rd := ConnRW.NewRd (t);
    wr := ConnRW.NewWr (t);
    Wr.PutText (wr, "go\n");
    Wr.Flush (wr);
    Out ("after the alert: " & Rd.GetLine (rd));
    t.close ();
    Finish ("alert", s);

    c := TCP.NewConnector (IP.Endpoint {IP.NullAddress, IP.NullPort});
    h := NEW (Shaker, ep := Loopback (TCP.GetEndPoint (c).port));
    th := Thread.Fork (h);
    Thread.Pause (0.2D0);
    Thread.Alert (th);
    EVAL Thread.Join (th);
    Out ("alert in handshake: " & h.result);
    TCP.CloseConnector (c);
  END TestAlerts;

(* the end of a connection *)

PROCEDURE SendAndClose (<*UNUSED*> s: Srv; t: TLS.T) =
  VAR wr := ConnRW.NewWr (t);
  BEGIN
    Wr.PutText (wr, "last line\n");
    Wr.Flush (wr);
  END SendAndClose;

PROCEDURE SendAndCut (s: Srv; t: TLS.T) =
  VAR wr := ConnRW.NewWr (t);
  BEGIN
    Wr.PutText (wr, "last line\n");
    Wr.Flush (wr);
    s.conn.close ();
  END SendAndCut;

PROCEDURE CountThenAnswer (<*UNUSED*> s: Srv; t: TLS.T) =
  VAR rd := ConnRW.NewRd (t);  wr := ConnRW.NewWr (t);  n := 0;
  BEGIN
    WHILE NOT Rd.EOF (rd) DO
      EVAL Rd.GetLine (rd);
      INC (n);
    END;
    Wr.PutText (wr, "lines read: " & Fmt.Int (n) & "\n");
    Wr.Flush (wr);
  END CountThenAnswer;

PROCEDURE TestEnds () =
  VAR s: Srv;  t: TLS.T;  rd: Rd.T;  wr: Wr.T;  b: ARRAY [0 .. 99] OF CHAR;
  BEGIN
    s := Start (server, SendAndClose);
    t := TLS.Client (client, TCP.Connect (s.ep), "localhost");
    rd := ConnRW.NewRd (t);
    Out ("notice: " & Rd.GetLine (rd));
    Out ("notice: EOF " & Fmt.Bool (Rd.EOF (rd)));
    t.close ();
    Finish ("notice", s);

    s := Start (server, SendAndCut);
    t := TLS.Client (client, TCP.Connect (s.ep), "localhost");
    rd := ConnRW.NewRd (t);
    Out ("cut: " & Rd.GetLine (rd));
    TRY
      EVAL Rd.EOF (rd);
      Out ("cut: no error");
    EXCEPT
    | Rd.Failure (l) => Out ("cut: Rd.Failure " & First (l));
    END;
    t.close ();
    Finish ("cut", s);

    s := Start (server, CountThenAnswer);
    t := TLS.Client (client, TCP.Connect (s.ep), "localhost");
    rd := ConnRW.NewRd (t);
    wr := ConnRW.NewWr (t);
    Wr.PutText (wr, "one\ntwo\n");
    Wr.Close (wr);
    Out ("half close: " & Rd.GetLine (rd));
    t.close ();
    Finish ("half close", s);

    s := Start (server, SendAndClose);
    t := TLS.Client (client, TCP.Connect (s.ep), "localhost");
    EVAL Rd.GetLine (ConnRW.NewRd (t));
    t.shutdownIn ();
    Out ("shutdownIn: get " & Fmt.Int (t.get (b)));
    t.close ();
    t.close ();
    TRY
      EVAL t.get (b);
      Out ("closed get: no error");
    EXCEPT
    | Rd.Failure (l) => Out ("closed get: Rd.Failure " & First (l));
    END;
    TRY
      t.put (ARRAY OF CHAR {'x'});
      Out ("closed put: no error");
    EXCEPT
    | Wr.Failure (l) => Out ("closed put: Wr.Failure " & First (l));
    END;
    Finish ("closed", s);
  END TestEnds;

(* a writer while a reader is blocked *)

CONST Lines = 200;

PROCEDURE AllThenEcho (<*UNUSED*> s: Srv; t: TLS.T) =
  VAR rd := ConnRW.NewRd (t);  wr := ConnRW.NewWr (t);
  BEGIN
    FOR i := 1 TO Lines DO EVAL Rd.GetLine (rd) END;
    FOR i := 1 TO Lines DO Wr.PutText (wr, "back\n") END;
    Wr.Flush (wr);
  END AllThenEcho;

TYPE
  LineReader = Thread.Closure OBJECT
    rd: Rd.T;
    n := 0;
  OVERRIDES
    apply := ReadLines;
  END;

PROCEDURE ReadLines (r: LineReader): REFANY =
  BEGIN
    WHILE NOT Rd.EOF (r.rd) DO
      EVAL Rd.GetLine (r.rd);
      INC (r.n);
    END;
    RETURN NIL;
  END ReadLines;

PROCEDURE TestConcurrent () =
  VAR
    s := Start (server, AllThenEcho);
    t := TLS.Client (client, TCP.Connect (s.ep), "localhost");
    r := NEW (LineReader, rd := ConnRW.NewRd (t));
    th := Thread.Fork (r);
    wr := ConnRW.NewWr (t);
  BEGIN
    Thread.Pause (0.1D0);
    FOR i := 1 TO Lines DO
      Wr.PutText (wr, "line\n");
      Wr.Flush (wr);
    END;
    EVAL Thread.Join (th);
    Out ("reader and writer: " & Fmt.Int (r.n) & " lines");
    t.close ();
    Finish ("concurrent", s);
  END TestConcurrent;

PROCEDURE Local () =
  BEGIN
    server := TLS.NewContext ("server.p12", Password, "ca.pem");
    wrongName := TLS.NewContext ("wrong.p12", Password);
    expired := TLS.NewContext ("expired.p12", Password);
    foreign := TLS.NewContext ("foreign.p12", Password);
    client := TLS.NewContext (NIL, NIL, "ca.pem");
    withCert := TLS.NewContext ("client.p12", Password, "ca.pem");
    TestLines ();
    TestBlocks ();
    TestMessages ();
    TestChecks ();
    TestPlainClient ();
    TestStores ();
    TestAlpn ();
    TestClientCert ();
    TestTimeouts ();
    TestAlerts ();
    TestEnds ();
    TestConcurrent ();
    Out ("end");
  END Local;

(* with another implementation of TLS *)

PROCEDURE AsClient (port: IP.Port) =
  VAR
    ctx := TLS.NewContext (NIL, NIL, "ca.pem");
    t := TLS.Client (ctx, TCP.Connect (Loopback (port)), "localhost", "echo");
    rd := ConnRW.NewRd (t);
    wr := ConnRW.NewWr (t);
  BEGIN
    Out (TLS.Version (t) & " protocol " & Opt (TLS.Protocol (t)));
    ExchangeAll ("client", rd, wr);
    Wr.Close (wr);
    Out ("client: EOF " & Fmt.Bool (Rd.EOF (rd)));
    t.close ();
  END AsClient;

PROCEDURE AsServer () =
  VAR
    ctx := TLS.NewContext ("server.p12", Password);
    c := TCP.NewConnector (IP.Endpoint {IP.NullAddress, IP.NullPort});
    t: TLS.T;
  BEGIN
    Out ("port " & Fmt.Int (TCP.GetEndPoint (c).port));
    t := TLS.Server (ctx, TCP.Accept (c), alpn := "echo");
    Out (TLS.Version (t) & " protocol " & Opt (TLS.Protocol (t)));
    EchoRaw (NIL, t);
    t.close ();
    Out ("server: done");
    TCP.CloseConnector (c);
  END AsServer;

(* the rate of a transfer *)

TYPE
  Sink = Thread.Closure OBJECT
    c: TCP.Connector;
    ctx: TLS.Context;
  OVERRIDES
    apply := RunSink;
  END;

PROCEDURE RunSink (s: Sink): REFANY =
  VAR conn: ConnFD.T := TCP.Accept (s.c);  b := NEW (REF ARRAY OF CHAR, 65536);
  BEGIN
    IF s.ctx # NIL THEN conn := TLS.Server (s.ctx, conn) END;
    WHILE conn.get (b^) > 0 DO END;
    conn.close ();
    RETURN NIL;
  END RunSink;

PROCEDURE Measure (name: TEXT; serverCtx, clientCtx: TLS.Context) =
  CONST Blocks = 4096;  BlockSize = 65536;
  VAR
    c := TCP.NewConnector (IP.Endpoint {IP.NullAddress, IP.NullPort});
    th := Thread.Fork (NEW (Sink, c := c, ctx := serverCtx));
    conn: ConnFD.T := TCP.Connect (Loopback (TCP.GetEndPoint (c).port));
    b := NEW (REF ARRAY OF CHAR, BlockSize);
    t0: Time.T;
  BEGIN
    IF clientCtx # NIL THEN conn := TLS.Client (clientCtx, conn, "localhost") END;
    t0 := Time.Now ();
    FOR i := 1 TO Blocks DO conn.put (b^) END;
    conn.shutdownOut ();
    EVAL Thread.Join (th);
    Out (name & ": " & Fmt.LongReal (FLOAT (Blocks * BlockSize DIV 1048576, LONGREAL)
                                     / (Time.Now () - t0), Fmt.Style.Fix, 0) & " MB/s");
    conn.close ();
    TCP.CloseConnector (c);
  END Measure;

PROCEDURE Speed () =
  BEGIN
    server := TLS.NewContext ("server.p12", Password);
    client := TLS.NewContext (NIL, NIL, "ca.pem");
    FOR i := 1 TO 3 DO
      Measure ("TCP", NIL, NIL);
      Measure ("TLS", server, client);
    END;
  END Speed;

VAR mode := Params.Get (1);

BEGIN
  Process.SetWorkingDirectory ("certs");
  IF Text.Equal (mode, "local") THEN
    Local ();
  ELSIF Text.Equal (mode, "client") THEN
    AsClient (Scan.Int (Params.Get (2)));
  ELSIF Text.Equal (mode, "speed") THEN
    Speed ();
  ELSE
    AsServer ();
  END;
END Main.
