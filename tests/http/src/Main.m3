(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The program that tools/http_test.py runs. The first argument names the
   test:

     codec    answers requests on the standard input, one per line, so that
              the driver can compare the results with those of Python
     headers  prints the behavior of "HTTP.Headers"
     serve [KEYSTORE]
              runs a server with tight limits, for the tests that talk to
              it over a socket; it prints the port first
     client BASE TLSBASE CA OTHERCA WRONGBASE FREEPORT PROXY
              the client against a server of Python
     together KEYSTORE CA
              server and client in this program, over TCP and TLS *)

MODULE Main;

IMPORT Fmt, HTTP, HTTPClient, HTTPServer, IO, IP, Params, Process, Rd, RdClass, Scan, Stdio, TCP,
       Text, TextRd, Thread, Time, TLS, Wr;
IMPORT Atom, AtomList, FloatMode, Lex;

<*FATAL Rd.Failure, Wr.Failure, Thread.Alerted*>

CONST HexDigits = "0123456789abcdef";

PROCEDURE FromHex (t: TEXT): TEXT =
  VAR n := Text.Length (t) DIV 2;  r := NEW (REF ARRAY OF CHAR, n);

  PROCEDURE Digit (c: CHAR): INTEGER =
    BEGIN
      IF c <= '9' THEN RETURN ORD (c) - ORD ('0') ELSE RETURN ORD (c) - ORD ('a') + 10 END;
    END Digit;

  BEGIN
    FOR i := 0 TO n - 1 DO
      r[i] := VAL (Digit (Text.GetChar (t, 2 * i)) * 16 + Digit (Text.GetChar (t, 2 * i + 1)), CHAR);
    END;
    RETURN Text.FromChars (r^);
  END FromHex;

PROCEDURE ToHex (t: TEXT): TEXT =
  VAR n := Text.Length (t);  r := NEW (REF ARRAY OF CHAR, 2 * n);  c: INTEGER;
  BEGIN
    FOR i := 0 TO n - 1 DO
      c := ORD (Text.GetChar (t, i));
      r[2 * i] := Text.GetChar (HexDigits, c DIV 16);
      r[2 * i + 1] := Text.GetChar (HexDigits, c MOD 16);
    END;
    RETURN Text.FromChars (r^);
  END ToHex;

PROCEDURE Flag (b: BOOLEAN): TEXT =
  BEGIN
    IF b THEN RETURN "1" ELSE RETURN "0" END;
  END Flag;

PROCEDURE TryUnescape (t: TEXT; form: BOOLEAN): TEXT =
  BEGIN
    TRY
      RETURN "ok " & ToHex (HTTP.Unescape (t, form));
    EXCEPT
    | HTTP.Error => RETURN "bad";
    END;
  END TryUnescape;

PROCEDURE TryParseDate (t: TEXT): TEXT =
  VAR r: Time.T;
  BEGIN
    IF HTTP.ParseDate (t, r) THEN
      RETURN "ok " & Fmt.LongReal (r, Fmt.Style.Fix, 0);
    ELSE
      RETURN "bad";
    END;
  END TryParseDate;

PROCEDURE TryBasic (user, password: TEXT): TEXT =
  BEGIN
    TRY
      RETURN ToHex (HTTP.BasicAuth (user, password));
    EXCEPT
    | HTTP.Error => RETURN "bad";
    END;
  END TryBasic;

PROCEDURE TryParseBasic (t: TEXT): TEXT =
  VAR user, password: TEXT;
  BEGIN
    IF HTTP.ParseBasicAuth (t, user, password) THEN
      RETURN "ok " & ToHex (user) & " " & ToHex (password);
    ELSE
      RETURN "bad";
    END;
  END TryParseBasic;

(* One request: a letter, a space and then arguments in hexadecimal, an
   empty argument being a single dash. *)
PROCEDURE Answer (line: TEXT): TEXT
  RAISES {FloatMode.Trap, Lex.Error} =
  VAR op := Text.GetChar (line, 0);  rest := Text.Sub (line, 2);  sp := Text.FindChar (rest, ' ');
      a, b: TEXT;

  PROCEDURE Arg (t: TEXT): TEXT =
    BEGIN
      IF Text.Equal (t, "-") THEN RETURN "" ELSE RETURN FromHex (t) END;
    END Arg;

  BEGIN
    IF sp >= 0 THEN
      a := Arg (Text.Sub (rest, 0, sp));
      b := Arg (Text.Sub (rest, sp + 1));
    ELSIF op # 'D' AND op # 'R' THEN
      a := Arg (rest);
    END;
    CASE op OF
    | 'E' => RETURN ToHex (HTTP.Escape (a));
    | 'F' => RETURN ToHex (HTTP.Escape (a, form := TRUE));
    | 'U' => RETURN TryUnescape (a, FALSE);
    | 'V' => RETURN TryUnescape (a, TRUE);
    | 'D' => RETURN HTTP.FormatDate (FLOAT (Scan.Int (rest), LONGREAL));
    | 'P' => RETURN TryParseDate (a);
    | 'B' => RETURN TryBasic (a, b);
    | 'A' => RETURN TryParseBasic (a);
    | 'R' => RETURN HTTP.Reason (Scan.Int (rest));
    | 'N' => RETURN Flag (HTTP.ValidName (a));
    | 'W' => RETURN Flag (HTTP.ValidValue (a));
    ELSE RETURN "unknown";
    END;
  END Answer;

PROCEDURE Codec () =
  <*FATAL FloatMode.Trap, Lex.Error*>
  BEGIN
    TRY
      LOOP
        Wr.PutText (Stdio.stdout, Answer (Rd.GetLine (Stdio.stdin)) & "\n");
      END;
    EXCEPT
    | Rd.EndOfFile =>
    END;
    Wr.Flush (Stdio.stdout);
  END Codec;

PROCEDURE Dump (h: HTTP.Headers) =
  VAR it := h.iterate ();  name, value: TEXT;
  BEGIN
    WHILE it.next (name, value) DO
      IO.Put ("  " & name & ": " & value & "\n");
    END;
  END Dump;

PROCEDURE Show (h: HTTP.Headers; name: TEXT) =
  VAR v := h.get (name);
  BEGIN
    IF v = NIL THEN v := "<NIL>" END;
    IO.Put ("get " & name & " = " & v & "\n");
  END Show;

PROCEDURE Headers () =
  <*FATAL HTTP.Error*>
  VAR h := HTTP.NewHeaders ();
  BEGIN
    IO.Put ("empty\n");
    Show (h, "Host");
    Dump (h);

    IO.Put ("add\n");
    h.add ("Host", "example.org");
    h.add ("Set-Cookie", "a=1");
    h.add ("Accept", "*/*");
    h.add ("set-cookie", "b=2");
    h.add ("SET-COOKIE", "c=3");
    Dump (h);
    Show (h, "HOST");
    Show (h, "Set-Cookie");
    Show (h, "Missing");

    IO.Put ("set keeps the position of the first and removes the others\n");
    h.set ("Set-cookie", "d=4");
    Dump (h);

    IO.Put ("set of a new name appends\n");
    h.set ("Date", "today");
    Dump (h);

    IO.Put ("remove\n");
    h.add ("ACCEPT", "text/plain");
    h.remove ("accept");
    Dump (h);
    h.remove ("Missing");

    IO.Put ("growth\n");
    FOR i := 1 TO 100 DO
      h.add ("X-" & Fmt.Int (i), Fmt.Int (i * i));
    END;
    Show (h, "x-100");
    Show (h, "X-1");
    h.remove ("Host");
    h.remove ("Set-Cookie");
    h.remove ("Date");
    Show (h, "x-50");
    VAR it := h.iterate ();  name, value: TEXT;  count := 0;
    BEGIN
      WHILE it.next (name, value) DO INC (count) END;
      IO.Put ("count " & Fmt.Int (count) & "\n");
      IO.Put ("next at the end: " & Flag (it.next (name, value)) & "\n");
    END;

    IO.Put ("characters above 255\n");
    VAR w := ARRAY [0 .. 2] OF WIDECHAR {'a', VAL (16_E9, WIDECHAR), VAL (16_20AC, WIDECHAR)};
    BEGIN
      IO.Put (HTTP.Escape (Text.FromWideChars (w)) & "\n");
      IO.Put (Flag (HTTP.ValidName (Text.FromWideChars (w))) & "\n");
      IO.Put (Flag (HTTP.ValidValue (Text.FromWideChars (w))) & "\n");
      IO.Put (TryUnescape (Text.FromWideChars (w), FALSE) & "\n");
      IO.Put (TryParseDate (Text.FromWideChars (w)) & "\n");
      IO.Put (TryParseBasic ("Basic " & Text.FromWideChars (w) & "a") & "\n");
      IO.Put (ToHex (HTTP.BasicAuth ("u", Text.FromWideChars (w))) & "\n");
    END;
  END Headers;

(* the test server *)

TYPE
  Test = HTTPServer.Handler OBJECT
  OVERRIDES
    accept := TestAccept;
    request := TestRequest;
  END;

  Log = HTTPServer.Log OBJECT
  OVERRIDES
    log := LogLine;
  END;

  Stopper = Thread.Closure OBJECT
  OVERRIDES
    apply := StopServer;
  END;

  Unlisted = OBJECT METHODS m () END;

EXCEPTION NotListed;

VAR server: HTTPServer.T;

PROCEDURE LogLine (<*UNUSED*> l: Log; msg: TEXT) =
  BEGIN
    IO.Put ("LOG " & msg & "\n");
    Wr.Flush (Stdio.stdout);
  END LogLine;

PROCEDURE StopServer (<*UNUSED*> s: Stopper): REFANY =
  BEGIN
    Thread.Pause (0.2D0);
    server.shutdown (3.0D0);
    RETURN NIL;
  END StopServer;

(* The value of "key=" in the query, or "default". *)
PROCEDURE QueryInt (req: HTTPServer.Request; key: TEXT; default: INTEGER): INTEGER =
  <*FATAL FloatMode.Trap, Lex.Error*>
  VAR q := req.query;  prefix := key & "=";  start := 0;  amp: INTEGER;  part: TEXT;
  BEGIN
    IF q = NIL THEN RETURN default END;
    WHILE start <= Text.Length (q) DO
      amp := Text.FindChar (q, '&', start);
      IF amp < 0 THEN amp := Text.Length (q) END;
      part := Text.Sub (q, start, amp - start);
      IF Text.Length (part) > Text.Length (prefix)
           AND Text.Equal (Text.Sub (part, 0, Text.Length (prefix)), prefix) THEN
        RETURN Scan.Int (Text.Sub (part, Text.Length (prefix)));
      END;
      start := amp + 1;
    END;
    RETURN default;
  END QueryInt;

PROCEDURE ReadAll (rd: Rd.T): TEXT RAISES {Rd.Failure, Thread.Alerted} =
  VAR buf: ARRAY [0 .. 8191] OF CHAR;  n: CARDINAL;  all := "";
  BEGIN
    LOOP
      n := Rd.GetSub (rd, buf);
      IF n = 0 THEN RETURN all END;
      all := all & Text.FromChars (SUBARRAY (buf, 0, n));
    END;
  END ReadAll;

PROCEDURE PutPattern (wr: Wr.T; n: INTEGER) RAISES {Wr.Failure, Thread.Alerted} =
  VAR buf: ARRAY [0 .. 4095] OF CHAR;  k, sent := 0;
  BEGIN
    WHILE sent < n DO
      k := MIN (n - sent, NUMBER (buf));
      FOR i := 0 TO k - 1 DO
        buf[i] := VAL ((sent + i) MOD 251, CHAR);
      END;
      Wr.PutString (wr, SUBARRAY (buf, 0, k));
      INC (sent, k);
    END;
  END PutPattern;

PROCEDURE TestAccept (<*UNUSED*> h: Test; req: HTTPServer.Request): BOOLEAN =
  BEGIN
    RETURN NOT Text.Equal (req.path, "/missing") AND NOT Text.Equal (req.path, "*");
  END TestAccept;

PROCEDURE Fail (atom: Atom.T) RAISES {Wr.Failure} =
  BEGIN
    RAISE Wr.Failure (AtomList.List1 (atom));
  END Fail;

PROCEDURE TestRequest (<*UNUSED*> h: Test; req: HTTPServer.Request; resp: HTTPServer.Response)
  RAISES {Rd.Failure, Wr.Failure, Thread.Alerted} =
  VAR path := req.path;  wr: Wr.T;
  BEGIN
    resp.headers.set ("Content-Type", "text/plain");
    IF Text.Equal (path, "/hello") THEN
      Wr.PutText (resp.body (), "Hello\n");
    ELSIF Text.Equal (path, "/echo") THEN
      Wr.PutText (resp.body (), ReadAll (req.body));
    ELSIF Text.Equal (path, "/echo-len") THEN
      VAR body := ReadAll (req.body);
      BEGIN
        Wr.PutText (resp.body (), Fmt.Int (Text.Length (body)) & " " & Fmt.Int (Rd.Length (req.body)));
      END;
    ELSIF Text.Equal (path, "/info") THEN
      wr := resp.body ();
      Wr.PutText (wr, "method=" & req.method & "\n");
      Wr.PutText (wr, "target=" & req.target & "\n");
      Wr.PutText (wr, "path=" & req.path & "\n");
      IF req.query # NIL THEN Wr.PutText (wr, "query=" & req.query & "\n") END;
      Wr.PutText (wr, "version=" & req.version & "\n");
      Wr.PutText (wr, "secure=" & Flag (req.secure) & "\n");
      VAR it := req.headers.iterate ();  name, value: TEXT;
      BEGIN
        WHILE it.next (name, value) DO
          Wr.PutText (wr, "h " & name & "=" & value & "\n");
        END;
      END;
    ELSIF Text.Equal (path, "/noread") THEN
      Wr.PutText (resp.body (), "unread\n");
    ELSIF Text.Equal (path, "/big") THEN
      PutPattern (resp.body (), QueryInt (req, "n", 100000));
    ELSIF Text.Equal (path, "/len") THEN
      resp.headers.set ("Content-Length", Fmt.Int (QueryInt (req, "claim", 0)));
      PutPattern (resp.body (), QueryInt (req, "n", 0));
    ELSIF Text.Equal (path, "/status") THEN
      resp.status := QueryInt (req, "c", 200);
      IF QueryInt (req, "body", 0) # 0 THEN Wr.PutText (resp.body (), "should not appear") END;
    ELSIF Text.Equal (path, "/nobody") THEN
      (* the handler returns without calling "body" *)
    ELSIF Text.Equal (path, "/flush") THEN
      wr := resp.body ();
      Wr.PutText (wr, "one\n");
      Wr.Flush (wr);
      Thread.Pause (0.2D0);
      Wr.PutText (wr, "two\n");
    ELSIF Text.Equal (path, "/close") THEN
      wr := resp.body ();
      Wr.PutText (wr, "closed\n");
      Wr.Close (wr);
    ELSIF Text.Equal (path, "/conn-close") THEN
      resp.headers.set ("Connection", "close");
      Wr.PutText (resp.body (), "bye\n");
    ELSIF Text.Equal (path, "/date") THEN
      resp.headers.set ("Date", "Sun, 06 Nov 1994 08:49:37 GMT");
    ELSIF Text.Equal (path, "/badvalue") THEN
      resp.headers.set ("X-Bad", "a\r\nX-Evil: 1");
      Wr.PutText (resp.body (), "x");
    ELSIF Text.Equal (path, "/badname") THEN
      resp.headers.set ("X Bad", "a");
    ELSIF Text.Equal (path, "/badte") THEN
      resp.headers.set ("Transfer-Encoding", "chunked");
    ELSIF Text.Equal (path, "/fail") THEN
      Fail (HTTP.BadMessage);
    ELSIF Text.Equal (path, "/fail-other") THEN
      Fail (Atom.FromText ("Test.Other"));
    ELSIF Text.Equal (path, "/fail-late") THEN
      wr := resp.body ();
      Wr.PutText (wr, "partial\n");
      Wr.Flush (wr);
      Fail (HTTP.BadMessage);
    ELSIF Text.Equal (path, "/crash") THEN
      VAR u: Unlisted := NIL;
      BEGIN
        u.m ();
      END;
    ELSIF Text.Equal (path, "/unlisted") THEN
      <*FATAL NotListed*>
      BEGIN
        RAISE NotListed;
      END;
    ELSIF Text.Equal (path, "/sleep") THEN
      Thread.AlertPause (FLOAT (QueryInt (req, "ms", 100), LONGREAL) / 1000.0D0);
      Wr.PutText (resp.body (), "slept\n");
    ELSIF Text.Equal (path, "/shutdown") THEN
      EVAL Thread.Fork (NEW (Stopper));
      Wr.PutText (resp.body (), "stopping\n");
    ELSE
      resp.status := 418;
      Wr.PutText (resp.body (), "no such test\n");
    END;
  END TestRequest;

PROCEDURE Serve () =
  <*FATAL ANY*>
  VAR o := HTTPServer.Defaults;  ctx: TLS.Context := NIL;  c: TCP.Connector;  a: IP.Address;
  BEGIN
    o.maxConnections := 6;
    o.maxLineBytes := 1024;
    o.maxHeaderBytes := 4096;
    o.maxHeaders := 20;
    o.maxBodyBytes := 1048576;
    o.handshakeTimeout := 2.0D0;
    o.headerTimeout := 1.0D0;
    o.readTimeout := 1.0D0;
    o.writeTimeout := 2.0D0;
    o.idleTimeout := 1.5D0;
    o.log := NEW (Log);
    IF Params.Count > 2 THEN
      ctx := TLS.NewContext (keyStore := Params.Get (2), keyPassword := "changeit");
    END;
    EVAL IP.GetHostByName ("localhost", a);
    c := TCP.NewConnector (IP.Endpoint {a, IP.NullPort});
    server := HTTPServer.New (o);
    server.register (NEW (Test));
    IO.Put (Fmt.Int (TCP.GetEndPoint (c).port) & "\n");
    Wr.Flush (Stdio.stdout);
    server.serve (c, ctx);
    IO.Put ("serve returned\n");
    Wr.Flush (Stdio.stdout);
  END Serve;

(* the tests of the client *)

PROCEDURE Out (t: TEXT) =
  BEGIN
    IO.Put (t & "\n");
    Wr.Flush (Stdio.stdout);
  END Out;

PROCEDURE ErrorText (e: AtomList.T): TEXT =
  VAR t := "";
  BEGIN
    IF e # NIL THEN t := Atom.ToText (e.head) END;
    IF e # NIL AND e.tail # NIL THEN t := t & " (" & Atom.ToText (e.tail.head) & ")" END;
    RETURN t;
  END ErrorText;

PROCEDURE TryGetText (c: HTTPClient.T; url: TEXT): TEXT =
  <*FATAL Thread.Alerted*>
  BEGIN
    TRY
      RETURN "ok " & HTTPClient.GetText (c, url);
    EXCEPT
    | HTTP.Error (e) => RETURN ErrorText (e);
    END;
  END TryGetText;

PROCEDURE TrySend (c: HTTPClient.T; r: HTTPClient.Request): TEXT =
  <*FATAL Thread.Alerted*>
  VAR resp: HTTPClient.Response;
  BEGIN
    TRY
      resp := HTTPClient.Send (c, r);
    EXCEPT
    | HTTP.Error (e) => RETURN ErrorText (e);
    END;
    RETURN Fmt.Int (resp.status) & " " & ReadAllText (resp);
  END TrySend;

PROCEDURE ReadAllText (resp: HTTPClient.Response): TEXT =
  <*FATAL Thread.Alerted*>
  BEGIN
    TRY
      RETURN ReadAll (resp.body);
    EXCEPT
    | Rd.Failure (e) => RETURN "Rd.Failure " & ErrorText (e);
    END;
  END ReadAllText;

TYPE
  BrokenRd = Rd.T OBJECT
  OVERRIDES
    seek := BrokenSeek;
    length := BrokenLength;
  END;

PROCEDURE BrokenSeek (<*UNUSED*> rd: BrokenRd; <*UNUSED*> pos: CARDINAL;
                      <*UNUSED*> dontBlock: BOOLEAN): RdClass.SeekResult RAISES {Rd.Failure} =
  BEGIN
    RAISE Rd.Failure (AtomList.List1 (Atom.FromText ("Test.Broken")));
  END BrokenSeek;

PROCEDURE BrokenLength (<*UNUSED*> rd: BrokenRd): INTEGER =
  BEGIN
    RETURN -1;
  END BrokenLength;

PROCEDURE Long (n: INTEGER): TEXT =
  VAR a := NEW (REF ARRAY OF CHAR, n);
  BEGIN
    FOR i := 0 TO n - 1 DO a[i] := VAL (ORD ('a') + i MOD 26, CHAR) END;
    RETURN Text.FromChars (a^);
  END Long;

TYPE
  Blocked = Thread.Closure OBJECT
    c: HTTPClient.T;
    url: TEXT;
    timeout := -1.0D0;
    read := FALSE;     (* block in the body, not in the head *)
    result: TEXT := "nothing";
  OVERRIDES
    apply := RunBlocked;
  END;

PROCEDURE RunBlocked (b: Blocked): REFANY =
  VAR resp: HTTPClient.Response;
  BEGIN
    TRY
      resp := HTTPClient.Send (b.c, NEW (HTTPClient.Request, url := b.url, timeout := b.timeout));
      IF b.read THEN
        Out ("  head arrived, reading");
        EVAL ReadAll (resp.body);
      END;
      b.result := "returned";
    EXCEPT
    | Thread.Alerted => b.result := "alerted";
    | HTTP.Error (e) => b.result := "HTTP.Error " & ErrorText (e);
    | Rd.Failure (e) => b.result := "Rd.Failure " & ErrorText (e);
    END;
    RETURN NIL;
  END RunBlocked;

PROCEDURE AlertTest (what: TEXT; b: Blocked) =
  VAR th := Thread.Fork (b);  t0: Time.T;
  BEGIN
    Thread.Pause (0.5D0);
    t0 := Time.Now ();
    Thread.Alert (th);
    EVAL Thread.Join (th);
    Out (what & ": " & b.result & ", quick: " & Flag (Time.Now () - t0 < 5.0D0));
  END AlertTest;

PROCEDURE Client (base, tlsBase, ca, otherCa, wrongBase: TEXT) =
  <*FATAL ANY*>
  VAR
    c := HTTPClient.New ();
    r: HTTPClient.Response;
    h: HTTP.Headers;
    it: HTTP.Iterator;
    name, value: TEXT;
    o := HTTPClient.Defaults;
  BEGIN
    Out ("text: " & HTTPClient.GetText (c, base & "/text"));

    r := HTTPClient.Get (c, base & "/status/404");
    Out ("404: " & Fmt.Int (r.status) & " " & r.version & " " & ReadAllText (r));
    Out ("GetText of 404: " & TryGetText (c, base & "/status/404"));

    r := HTTPClient.Get (c, base & "/multi");
    Out ("multi: " & Fmt.Int (r.status));
    Out ("  get set-cookie: " & r.headers.get ("Set-Cookie"));
    it := r.headers.iterate ();
    WHILE it.next (name, value) DO
      IF Text.Equal (name, "set-cookie") OR Text.Equal (name, "x-a") OR Text.Equal (name, "x-empty") THEN
        Out ("  " & name & ": [" & value & "]");
      END;
    END;
    EVAL ReadAllText (r);

    Out ("redirect: " & TryGetText (c, base & "/redirect"));
    o.followRedirects := FALSE;
    VAR nf := HTTPClient.New (o);
    BEGIN
      r := HTTPClient.Get (nf, base & "/redirect");
      Out ("no redirect: " & Fmt.Int (r.status) & " " & r.headers.get ("location"));
      EVAL ReadAllText (r);
      HTTPClient.Close (nf);
    END;
    o := HTTPClient.Defaults;

    r := HTTPClient.Post (c, base & "/echo", "text/x-test", "body of a post");
    Out ("post: " & Fmt.Int (r.status) & " " & ReadAllText (r));
    r := HTTPClient.Post (c, base & "/echo", "text/x-test", "");
    Out ("empty post: " & Fmt.Int (r.status) & " " & ReadAllText (r));
    VAR w := ARRAY [0 .. 0] OF WIDECHAR {VAL (16_20AC, WIDECHAR)};
    BEGIN
      r := HTTPClient.Post (c, base & "/echo", "text/x-test", Text.FromWideChars (w));
      Out ("wide post: " & Fmt.Int (r.status) & " " & ReadAllText (r));
    END;

    h := HTTP.NewHeaders ();
    h.add ("X-Multi", "one");
    h.add ("X-Multi", "two");
    Out ("method: " & TrySend (c, NEW (HTTPClient.Request, method := "PATCH", url := base & "/echo", headers := h)));
    Out ("delete: " & TrySend (c, NEW (HTTPClient.Request, method := "DELETE", url := base & "/echo")));

    VAR long := Long (300000);
    BEGIN
      Out ("known length: " & TrySend (c, NEW (HTTPClient.Request, method := "POST", url := base & "/echo",
                                               body := TextRd.New (long), length := Text.Length (long))));
      Out ("unknown length: " & TrySend (c, NEW (HTTPClient.Request, method := "POST", url := base & "/echo",
                                                 body := TextRd.New (long))));
      Out ("wrong length: " & TrySend (c, NEW (HTTPClient.Request, method := "POST", url := base & "/echo",
                                               body := TextRd.New (long), length := 5)));
    END;
    Out ("failing body: " & TrySend (c, NEW (HTTPClient.Request, method := "POST", url := base & "/echo",
                                             body := NEW (BrokenRd, buff := NIL, st := 0, lo := 0, hi := 0, cur := 0,
                                                          intermittent := TRUE, seekable := FALSE, closed := FALSE))));

    h := HTTP.NewHeaders ();
    h.add ("Host", "x");
    Out ("restricted field: " & TrySend (c, NEW (HTTPClient.Request, url := base & "/text", headers := h)));
    h := HTTP.NewHeaders ();
    h.add ("transfer-encoding", "chunked");
    Out ("transfer-encoding: " & TrySend (c, NEW (HTTPClient.Request, url := base & "/text", headers := h)));
    h := HTTP.NewHeaders ();
    h.add ("X-A", "caf\351");
    Out ("not ascii: " & TrySend (c, NEW (HTTPClient.Request, url := base & "/text", headers := h)));
    h := HTTP.NewHeaders ();
    h.add ("X-A", "a\r\nX-Evil: 1");
    Out ("line break: " & TrySend (c, NEW (HTTPClient.Request, url := base & "/text", headers := h)));
    h := HTTP.NewHeaders ();
    h.add ("bad name", "a");
    Out ("bad name: " & TrySend (c, NEW (HTTPClient.Request, url := base & "/text", headers := h)));
    Out ("bad method: " & TrySend (c, NEW (HTTPClient.Request, method := "A B", url := base & "/text")));
    Out ("no url: " & TrySend (c, NEW (HTTPClient.Request)));
    Out ("scheme: " & TryGetText (c, "ftp://127.0.0.1/"));
    Out ("relative: " & TryGetText (c, "/text"));
    Out ("syntax: " & TryGetText (c, "http://a b/"));

    Out ("unreachable: " & TryGetText (c, "http://127.0.0.1:" & Params.Get (7) & "/"));
    Out ("unknown host: " & TryGetText (c, "http://no-such-host.invalid/"));
    Out ("timeout in head: " & TrySend (c, NEW (HTTPClient.Request, url := base & "/slow-head", timeout := 0.3D0)));
    Out ("timeout in body: " & TrySend (c, NEW (HTTPClient.Request, url := base & "/slow-body", timeout := 0.3D0)));
    Out ("truncated: " & TrySend (c, NEW (HTTPClient.Request, url := base & "/truncated")));
    Out ("garbage: " & TrySend (c, NEW (HTTPClient.Request, url := base & "/garbage")));
    Out ("closes early: " & TrySend (c, NEW (HTTPClient.Request, url := base & "/hangup")));

    AlertTest ("alert in the head", NEW (Blocked, c := c, url := base & "/slow-head"));
    AlertTest ("alert in the body", NEW (Blocked, c := c, url := base & "/slow-body", read := TRUE));

    r := HTTPClient.Get (c, base & "/download?n=1000000");
    VAR buf: ARRAY [0 .. 99] OF CHAR;  n := Rd.GetSub (r.body, buf);
    BEGIN
      Out ("close early: read " & Fmt.Int (n));
      r.close ();
      r.close ();
    END;

    r := HTTPClient.Get (c, base & "/text");
    Out ("reader closed: " & Rd.GetLine (r.body));
    Rd.Close (r.body);

    Out ("still works: " & TryGetText (c, base & "/text"));

    VAR buf := NEW (REF ARRAY OF CHAR, 65536);  n: CARDINAL;  total := 0;  sum := 0;  size := 100 * 1024 * 1024;
    BEGIN
      r := HTTPClient.Get (c, base & "/download?n=" & Fmt.Int (size));
      LOOP
        n := Rd.GetSub (r.body, buf^);
        IF n = 0 THEN EXIT END;
        FOR i := 0 TO n - 1 DO
          sum := (sum * 31 + ORD (buf[i]) + total + i) MOD 1000000007;
        END;
        INC (total, n);
      END;
      Out ("download: " & Fmt.Int (total) & " bytes, checksum " & Fmt.Int (sum));
    END;

    o := HTTPClient.Defaults;
    o.proxy := Params.Get (8);
    VAR p := HTTPClient.New (o);
    BEGIN
      Out ("through a proxy: " & TryGetText (p, "http://proxied.invalid/text"));
      HTTPClient.Close (p);
    END;
    o.proxy := "no port";
    TRY
      EVAL HTTPClient.New (o);
      Out ("bad proxy: accepted");
    EXCEPT
    | HTTP.Error (e) => Out ("bad proxy: " & ErrorText (e));
    END;

    Out ("default trust against the test ca: " & TryGetText (c, tlsBase & "/text"));
    o := HTTPClient.Defaults;
    o.ctx := TLS.NewContext (trustStore := ca);
    VAR t := HTTPClient.New (o);
    BEGIN
      Out ("https: " & TryGetText (t, tlsBase & "/text"));
      r := HTTPClient.Post (t, tlsBase & "/echo", "text/x-test", "over tls");
      Out ("https post: " & Fmt.Int (r.status) & " " & r.version & " " & ReadAllText (r));
      Out ("wrong host: " & TryGetText (t, wrongBase & "/text"));
      HTTPClient.Close (t);
    END;
    o.ctx := TLS.NewContext (trustStore := otherCa);
    VAR t := HTTPClient.New (o);
    BEGIN
      Out ("other ca: " & TryGetText (t, tlsBase & "/text"));
      HTTPClient.Close (t);
    END;

    HTTPClient.Close (c);
    Out ("end");
  END Client;

TYPE
  ServeThread = Thread.Closure OBJECT
    s: HTTPServer.T;
    c: TCP.Connector;
    ctx: TLS.Context;
  OVERRIDES
    apply := RunServe;
  END;

PROCEDURE RunServe (st: ServeThread): REFANY =
  <*FATAL ANY*>
  BEGIN
    st.s.serve (st.c, st.ctx);
    RETURN NIL;
  END RunServe;

(* Server and client in this program. *)
PROCEDURE Loop (scheme: TEXT; ctx, clientCtx: TLS.Context) =
  <*FATAL ANY*>
  VAR
    a: IP.Address;  conn: TCP.Connector;  th: Thread.T;  s := HTTPServer.New ();  o := HTTPClient.Defaults;
    c: HTTPClient.T;  base: TEXT;  r: HTTPClient.Response;  long := Long (500000);
  BEGIN
    EVAL IP.GetHostByName ("localhost", a);
    conn := TCP.NewConnector (IP.Endpoint {a, IP.NullPort});
    server := s;
    s.register (NEW (Test));
    th := Thread.Fork (NEW (ServeThread, s := s, c := conn, ctx := ctx));
    base := scheme & "://localhost:" & Fmt.Int (TCP.GetEndPoint (conn).port);
    o.ctx := clientCtx;
    c := HTTPClient.New (o);

    Out (scheme);
    r := HTTPClient.Get (c, base & "/hello");
    Out ("  " & Fmt.Int (r.status) & " " & r.version & " " & ReadAllText (r));
    r := HTTPClient.Post (c, base & "/echo", "text/plain", "ping");
    Out ("  " & ReadAllText (r));
    r := HTTPClient.Get (c, base & "/missing");
    Out ("  " & Fmt.Int (r.status) & " " & ReadAllText (r));
    r := HTTPClient.Send (c, NEW (HTTPClient.Request, method := "POST", url := base & "/echo-len", body := TextRd.New (long)));
    Out ("  chunked upload: " & ReadAllText (r));
    r := HTTPClient.Send (c, NEW (HTTPClient.Request, method := "POST", url := base & "/echo-len", body := TextRd.New (long),
                                  length := Text.Length (long)));
    Out ("  known upload: " & ReadAllText (r));
    r := HTTPClient.Get (c, base & "/big?n=2000000");
    VAR body := ReadAll (r.body);  good := Text.Length (body) = 2000000;
    BEGIN
      FOR i := 0 TO Text.Length (body) - 1 DO
        IF ORD (Text.GetChar (body, i)) # i MOD 251 THEN good := FALSE END;
      END;
      Out ("  download: " & Flag (good));
    END;
    Out ("  " & TrySend (c, NEW (HTTPClient.Request, url := base & "/fail-other")));
    Out ("  " & TrySend (c, NEW (HTTPClient.Request, url := base & "/fail-late")));
    HTTPClient.Close (c);
    s.shutdown (2.0D0);
    EVAL Thread.Join (th);
    Out ("  end");
  END Loop;

PROCEDURE Together (keyStore, ca: TEXT) =
  <*FATAL ANY*>
  VAR clientCtx := TLS.NewContext (trustStore := ca);
  BEGIN
    Loop ("http", NIL, NIL);
    Loop ("https", TLS.NewContext (keyStore := keyStore, keyPassword := "changeit"), clientCtx);
  END Together;

BEGIN
  IF Params.Count < 2 THEN
    IO.Put ("usage: test\n");
    Process.Exit (2);
  END;
  WITH test = Params.Get (1) DO
    IF Text.Equal (test, "codec") THEN
      Codec ();
    ELSIF Text.Equal (test, "headers") THEN
      Headers ();
    ELSIF Text.Equal (test, "serve") THEN
      Serve ();
    ELSIF Text.Equal (test, "client") THEN
      Client (Params.Get (2), Params.Get (3), Params.Get (4), Params.Get (5), Params.Get (6));
    ELSIF Text.Equal (test, "together") THEN
      Together (Params.Get (2), Params.Get (3));
    ELSE
      IO.Put ("unknown test\n");
      Process.Exit (2);
    END;
  END;
END Main.
