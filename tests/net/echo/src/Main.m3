(* A server and a client of TCP, for tests between programs built by cm3
   and by m3j (tools/net_test.py): both must speak the same bytes.

     echo server         prints "port N", the port that it accepts on
                         (on the loopback address), then serves the
                         connections of one client and ends
     echo client N       is that client, of the server on port N

   The client makes these connections, one after the other:

   - lines, through readers and writers of ConnRW, which the server
     sends back in upper case;
   - messages of ConnMsgRW, of many sizes and flushed in parts, whose
     bytes the server reads as they come over the connection and
     describes: their number and a checksum, and the headers of the
     fragments it finds in them;
   - for each of the same messages, a connection on which the client
     sends it and the server sends it back with its length in front
     (one message each way: the reader of messages of cm3 fails on the
     second message of a connection when it reads it to its end).

   Both print what they did; the output does not depend on the port. *)

MODULE Main;

IMPORT ConnFD, ConnMsgRW, ConnRW, Fmt, IO, IP, Params, Rd, Scan, Stdio, TCP, Text,
       Thread, Wr, Lex, FloatMode;

<* FATAL Wr.Failure, Thread.Alerted, Rd.Failure, Rd.EndOfFile, IP.Error,
        ConnFD.TimedOut *>

PROCEDURE Out (t: TEXT) =
  BEGIN
    IO.Put (t & "\n");
    Wr.Flush (Stdio.stdout);
  END Out;

CONST
  Sizes = ARRAY OF CARDINAL {0, 1, 7, 8, 9, 100, 8183, 8184, 8185, 8192,
                             20000, 70000};
  Parts = ARRAY OF CARDINAL {1, 1, 2, 1, 3, 4, 1, 1, 2, 5, 7, 3};

PROCEDURE Message (n: CARDINAL): TEXT =
  (* the message of size n *)
  VAR a := NEW (REF ARRAY OF CHAR, n);
  BEGIN
    FOR i := 0 TO n - 1 DO a[i] := VAL ((i * 7 + n) MOD 256, CHAR) END;
    RETURN Text.FromChars (a^);
  END Message;

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

(* the server *)

PROCEDURE Server () =
  VAR
    c := TCP.NewConnector (IP.Endpoint {IP.NullAddress, IP.NullPort});
  BEGIN
    Out ("port " & Fmt.Int (TCP.GetEndPoint (c).port));
    ServeLines (TCP.Accept (c));
    ServeBytes (TCP.Accept (c));
    FOR i := FIRST (Sizes) TO LAST (Sizes) DO
      ServeMessage (TCP.Accept (c));
    END;
    TCP.CloseConnector (c);
    Out ("server: end");
  END Server;

PROCEDURE ServeLines (t: TCP.T) =
  VAR
    rd := ConnRW.NewRd (t);
    wr := ConnRW.NewWr (t);
    line: TEXT;
    n := 0;
  BEGIN
    LOOP
      line := Rd.GetLine (rd);
      IF Text.Equal (line, "bye") THEN EXIT END;
      Wr.PutText (wr, Upper (line) & "\n");
      Wr.Flush (wr);
      INC (n);
    END;
    Wr.PutText (wr, "done\n");
    Wr.Flush (wr);
    TCP.Close (t);
    Out ("server: " & Fmt.Int (n) & " lines");
  END ServeLines;

PROCEDURE ServeBytes (t: TCP.T) =
  (* reads the bytes of the connection to its end; sends back their number,
     a checksum, and the fragment headers found by walking them *)
  VAR
    buf := NEW (REF ARRAY OF CHAR, 1000000);
    len := 0;
    n: CARDINAL;
    sum := 0;
    pos := 0;
    eom, nb: INTEGER;
    frags := 0;
    msgs := 0;
    off := 0;
    wr := ConnRW.NewWr (t);
  BEGIN
    REPEAT
      n := t.get (SUBARRAY (buf^, len, NUMBER (buf^) - len));
      INC (len, n);
    UNTIL n = 0;
    FOR i := 0 TO len - 1 DO
      sum := (sum * 31 + ORD (buf[i])) MOD 1000000007;
    END;
    (* the fragments: a header of 8 bytes (eom and the count of data
       bytes, little-endian), padding that gives each data byte the
       alignment (index MOD 8) it has in its message, the data, and
       padding to a multiple of 8; a fragment without data is a header *)
    WHILE pos + 8 <= len DO
      eom := Int32 (buf^, pos);
      nb := Int32 (buf^, pos + 4);
      INC (frags);
      IF nb # 0 THEN
        INC (pos, Align (8 + off MOD 8 + nb));
        INC (off, nb);
      ELSE
        INC (pos, 8);
      END;
      IF eom # 0 THEN INC (msgs); off := 0 END;
    END;
    Wr.PutText (wr, Fmt.Int (len) & " " & Fmt.Int (sum) & " "
                      & Fmt.Int (frags) & " fragments " & Fmt.Int (msgs)
                      & " messages, " & Fmt.Int (len - pos) & " left\n");
    Wr.Flush (wr);
    TCP.Close (t);
    Out ("server: " & Fmt.Int (len) & " bytes");
  END ServeBytes;

PROCEDURE Align (n: CARDINAL): CARDINAL =
  BEGIN
    RETURN n + (-n) MOD 8;
  END Align;

PROCEDURE Int32 (READONLY b: ARRAY OF CHAR; i: CARDINAL): INTEGER =
  VAR w := ORD (b[i]) + ORD (b[i+1]) * 16_100 + ORD (b[i+2]) * 16_10000
             + ORD (b[i+3]) * 16_1000000;
  BEGIN
    IF w > 16_7FFFFFFF THEN DEC (w, 16_100000000) END;
    RETURN w;
  END Int32;

PROCEDURE ServeMessage (t: TCP.T) =
  VAR
    rd := ConnMsgRW.NewRd (t);
    wr := ConnMsgRW.NewWr (t);
    m: TEXT;
  BEGIN
    EVAL rd.nextMsg ();
    m := Rd.GetText (rd, LAST (CARDINAL));
    Wr.PutText (wr, Fmt.Int (Text.Length (m)) & ":" & m);
    wr.nextMsg ();
    TCP.Close (t);
    Out ("server: message of " & Fmt.Int (Text.Length (m)));
  END ServeMessage;

(* the client *)

PROCEDURE Client (port: IP.Port) =
  VAR ep := IP.Endpoint {IP.Address {a := ARRAY OF BITS 8 FOR [0..255] {127, 0, 0, 1}}, port};
  BEGIN
    Lines (TCP.Connect (ep));
    Bytes (TCP.Connect (ep));
    FOR i := FIRST (Sizes) TO LAST (Sizes) DO
      OneMessage (TCP.Connect (ep), Sizes[i], Parts[i]);
    END;
    Out ("client: end");
  END Client;

PROCEDURE Lines (t: TCP.T) =
  VAR
    rd := ConnRW.NewRd (t);
    wr := ConnRW.NewWr (t);
    long := "";
  BEGIN
    Wr.PutText (wr, "hello, world\n");
    Wr.Flush (wr);
    Out ("client: " & Rd.GetLine (rd));
    FOR i := 1 TO 20000 DO long := long & Text.FromChar (VAL (ORD ('a') + i MOD 26, CHAR)) END;
    Wr.PutText (wr, long & "\nabc\n\nbye\n");
    Wr.Flush (wr);
    IF Text.Equal (Rd.GetLine (rd), Upper (long)) THEN
      Out ("client: long line ok");
    ELSE
      Out ("client: long line FAILED");
    END;
    Out ("client: " & Rd.GetLine (rd) & " [" & Rd.GetLine (rd) & "] " & Rd.GetLine (rd));
    IF Rd.EOF (rd) THEN Out ("client: end of lines") END;
    TCP.Close (t);
  END Lines;

PROCEDURE Send (wr: Wr.T; m: TEXT; parts: CARDINAL) =
  VAR n := Text.Length (m);  k := 0;
  BEGIN
    FOR p := 1 TO parts DO
      WITH e = n * p DIV parts DO
        Wr.PutText (wr, Text.Sub (m, k, e - k));
        Wr.Flush (wr);
        k := e;
      END;
    END;
  END Send;

PROCEDURE Bytes (t: TCP.T) =
  VAR
    wr := ConnMsgRW.NewWr (t);
    rd := ConnRW.NewRd (t);
  BEGIN
    FOR i := FIRST (Sizes) TO LAST (Sizes) DO
      Send (wr, Message (Sizes[i]), Parts[i]);
      wr.nextMsg ();
    END;
    t.shutdownOut ();
    Out ("client: bytes " & Rd.GetLine (rd));
    TCP.Close (t);
  END Bytes;

PROCEDURE OneMessage (t: TCP.T; size, parts: CARDINAL) =
  VAR
    wr := ConnMsgRW.NewWr (t);
    rd := ConnMsgRW.NewRd (t);
    m := Message (size);
    r: TEXT;
  BEGIN
    Send (wr, m, parts);
    wr.nextMsg ();
    EVAL rd.nextMsg ();
    r := Rd.GetText (rd, LAST (CARDINAL));
    IF Text.Equal (r, Fmt.Int (size) & ":" & m) THEN
      Out ("client: message of " & Fmt.Int (size) & " in " & Fmt.Int (parts) & " ok");
    ELSE
      Out ("client: message of " & Fmt.Int (size) & " in " & Fmt.Int (parts)
             & " FAILED (" & Fmt.Int (Text.Length (r)) & ")");
    END;
    TCP.Close (t);
  END OneMessage;

<* FATAL Lex.Error, FloatMode.Trap *>
BEGIN
  IF Params.Count = 2 AND Text.Equal (Params.Get (1), "server") THEN
    Server ();
  ELSIF Params.Count = 3 AND Text.Equal (Params.Get (1), "client") THEN
    Client (Scan.Int (Params.Get (2)));
  ELSE
    Out ("usage: echo server | echo client PORT");
  END;
END Main.
