(* Network objects between a server and a client, for tests between
   programs built by cm3 and by m3j (tools/net_test.py): both must speak
   the same bytes and know each other's types.

     netobj server       prints "port N", the port of its agent (it tries
                         ports from 9601 on until it can listen), exports
                         a Test.T there and serves until the client calls
                         its method quit
     netobj client N     imports that object from the agent on port N of
                         the local host and calls its methods

   The calls pass integers, texts (with wide characters and NIL), records
   (with real numbers, subranges, enumerations, sets and WIDECHAR), open
   arrays, lists (pickled, by Pickle for the stubs of version 1 and by
   Pickle2 for those of version 2), exceptions of the method and
   NetObj.Error, network objects both ways (a callback of the server to an
   object of the client, an object that comes back to its owner) and
   readers and writers of the client that the server reads and writes.
   Both print what they did; the output does not depend on the port. *)

MODULE Main;

IMPORT Test, NetObj, TCPNetObj, IP, Thread, Params, Scan, IO, Fmt, Rd, Wr,
       TextRd, TextWr, NetStream, AtomList, Atom, Lex, FloatMode, Text;

TYPE
  Impl = Test.T OBJECT
    u: Test.U;
  OVERRIDES
    add := Add;
    echo := Echo;
    rec := Rec;
    list := List;
    sum := Sum;
    arr := Arr;
    chars := Chars;
    texts := Texts;
    fail := Fail;
    callback := Callback;
    other := Other;
    reader := Reader;
    writer := Writer;
    quit := Quit;
  END;

  UImpl = Test.U OBJECT
    owner: Test.T;
  OVERRIDES
    list := UList;
    sum := USum;
    rec := URec;
    same := Same;
  END;

  Counter = Test.T OBJECT
    n := 0;
  OVERRIDES
    add := CountAdd;
  END;

VAR
  done := NEW(Thread.Condition);
  mu := NEW(MUTEX);
  finished := FALSE;

PROCEDURE Add (<*UNUSED*> self: Impl; a, b: INTEGER): INTEGER =
  BEGIN
    RETURN a + b
  END Add;

PROCEDURE Echo (<*UNUSED*> self: Impl; t: TEXT): TEXT =
  BEGIN
    IF t = NIL THEN RETURN NIL END;
    RETURN "echo:" & t
  END Echo;

PROCEDURE Double (READONLY r: Test.Rec; VAR out: Test.Rec) =
  BEGIN
    out.i := r.i * 2;
    out.r := r.r * 2.0;
    out.l := r.l * 2.0d0;
    out.b := NOT r.b;
    out.c := VAL(ORD(r.c) + 1, CHAR);
    out.w := VAL(ORD(r.w) + 1, WIDECHAR);
    out.s := r.s + 1;
    out.e := VAL((ORD(r.e) + 1) MOD 3, Test.Color);
    out.set := r.set + SET OF [0..40] {0, 40};
  END Double;

PROCEDURE Rec (<*UNUSED*> self: Impl; READONLY r: Test.Rec; VAR out: Test.Rec) =
  BEGIN
    Double(r, out);
  END Rec;

PROCEDURE MakeList (n: INTEGER): Test.List =
  VAR l: Test.List := NIL;
  BEGIN
    FOR i := 1 TO n DO
      l := NEW(Test.List, head := i, name := "n" & Fmt.Int(i), tail := l)
    END;
    RETURN l
  END MakeList;

PROCEDURE SumList (l: Test.List): INTEGER =
  VAR s := 0;
  BEGIN
    WHILE l # NIL DO INC(s, l.head); l := l.tail END;
    RETURN s
  END SumList;

PROCEDURE List (<*UNUSED*> self: Impl; n: INTEGER): Test.List =
  BEGIN
    RETURN MakeList(n)
  END List;

PROCEDURE Sum (<*UNUSED*> self: Impl; l: Test.List): INTEGER =
  BEGIN
    RETURN SumList(l)
  END Sum;

PROCEDURE Arr (<*UNUSED*> self: Impl; VAR a: ARRAY OF INTEGER) =
  BEGIN
    FOR i := 0 TO LAST(a) DO a[i] := a[i] * a[i] END
  END Arr;

PROCEDURE Chars (<*UNUSED*> self: Impl; READONLY a: ARRAY OF CHAR;
                 VAR w: ARRAY OF WIDECHAR): TEXT =
  BEGIN
    FOR i := 0 TO MIN(LAST(a), LAST(w)) DO
      w[i] := VAL(ORD(a[i]) + 16_100, WIDECHAR)
    END;
    RETURN Text.FromChars(a)
  END Chars;

PROCEDURE Texts (<*UNUSED*> self: Impl; READONLY a: ARRAY OF TEXT): TEXT =
  VAR t := "";
  BEGIN
    FOR i := 0 TO LAST(a) DO
      IF a[i] = NIL THEN t := t & "<nil>" ELSE t := t & a[i] END;
      t := t & "|"
    END;
    RETURN t
  END Texts;

PROCEDURE Fail (<*UNUSED*> self: Impl; code: INTEGER)
  RAISES {NetObj.Error, Test.Oops} =
  BEGIN
    IF code = 1 THEN RAISE Test.Oops("code one") END;
    IF code = 2 THEN
      RAISE NetObj.Error(AtomList.List2(Atom.FromText("my.error"),
                                        Atom.FromText("detail")))
    END;
  END Fail;

PROCEDURE Callback (<*UNUSED*> self: Impl; cb: Test.T; n: INTEGER): INTEGER
  RAISES {NetObj.Error, Thread.Alerted} =
  VAR s := 0;
  BEGIN
    FOR i := 1 TO n DO s := cb.add(s, i) END;
    RETURN s
  END Callback;

PROCEDURE Other (self: Impl): Test.U =
  BEGIN
    IF self.u = NIL THEN self.u := NEW(UImpl, owner := self) END;
    RETURN self.u
  END Other;

PROCEDURE Reader (<*UNUSED*> self: Impl; rd: Rd.T): TEXT =
  <*FATAL Rd.Failure, Thread.Alerted*>
  VAR t := Rd.GetText(rd, LAST(INTEGER));
  BEGIN
    Rd.Close(rd);
    RETURN "read " & Fmt.Int(Text.Length(t)) & ": " & t
  END Reader;

PROCEDURE Writer (<*UNUSED*> self: Impl; wr: Wr.T; t: TEXT) =
  <*FATAL Wr.Failure, Thread.Alerted*>
  BEGIN
    Wr.PutText(wr, "from server: " & t);
    Wr.Flush(wr);
    NetStream.ReleaseWr(wr);
  END Writer;

PROCEDURE Quit (<*UNUSED*> self: Impl) =
  BEGIN
    LOCK mu DO finished := TRUE END;
    Thread.Signal(done);
  END Quit;

PROCEDURE UList (<*UNUSED*> self: UImpl; n: INTEGER): Test.List =
  BEGIN
    RETURN MakeList(n)
  END UList;

PROCEDURE USum (<*UNUSED*> self: UImpl; l: Test.List): INTEGER =
  BEGIN
    RETURN SumList(l)
  END USum;

PROCEDURE URec (<*UNUSED*> self: UImpl; READONLY r: Test.Rec): Test.Rec =
  VAR out: Test.Rec;
  BEGIN
    Double(r, out);
    RETURN out
  END URec;

PROCEDURE Same (self: UImpl; t: Test.T): BOOLEAN =
  BEGIN
    RETURN t = self.owner
  END Same;

PROCEDURE CountAdd (self: Counter; a, b: INTEGER): INTEGER =
  BEGIN
    INC(self.n);
    RETURN a + b
  END CountAdd;

PROCEDURE Server () =
  <*FATAL NetObj.Error, Thread.Alerted*>
  VAR port := 9601;  agent: NetObj.Address := NIL;
  BEGIN
    WHILE agent = NIL DO
      TRY
        agent := TCPNetObj.Listen(port)
      EXCEPT
        TCPNetObj.Failed => INC(port)
      END
    END;
    NetObj.Export("test", NEW(Impl), agent);
    IO.Put("port " & Fmt.Int(port) & "\n");
    LOCK mu DO
      WHILE NOT finished DO Thread.Wait(mu, done) END
    END;
    (* time for the reply to quit *)
    Thread.Pause(1.0d0);
    IO.Put("server done\n");
  END Server;

PROCEDURE ShowRec (READONLY r: Test.Rec) =
  BEGIN
    IO.Put(Fmt.Int(r.i) & " " & Fmt.Real(r.r) & " " & Fmt.LongReal(r.l) & " "
           & Fmt.Bool(r.b) & " " & Fmt.Char(r.c) & " " & Fmt.Int(ORD(r.w)) & " "
           & Fmt.Int(r.s) & " " & Fmt.Int(ORD(r.e)) & " {");
    FOR i := 0 TO 40 DO
      IF i IN r.set THEN IO.Put(" " & Fmt.Int(i)) END
    END;
    IO.Put(" }\n");
  END ShowRec;

PROCEDURE ShowError (a: AtomList.T) =
  BEGIN
    IO.Put("NetObj.Error:");
    WHILE a # NIL DO IO.Put(" " & Atom.ToText(a.head)); a := a.tail END;
    IO.Put("\n");
  END ShowError;

PROCEDURE Client (port: INTEGER) =
  <*FATAL Thread.Alerted, IP.Error*>
  VAR
    t: Test.T;
    u, u2: Test.U;
    addr: IP.Address;
    agent: NetObj.Address;
    r, out: Test.Rec;
    a := ARRAY [0..4] OF INTEGER {1, 2, 3, 4, 5};
    cs := ARRAY [0..3] OF CHAR {'a', 'b', '\000', '\377'};
    ws: ARRAY [0..3] OF WIDECHAR;
    ts := ARRAY [0..2] OF TEXT {"x", NIL, "z\200"};
    cb := NEW(Counter);
    wr := TextWr.New();
    wide := Text.FromWideChars(ARRAY OF WIDECHAR {'<', W'\x263A', W'\xFFFF', '>'});
  BEGIN
    TRY
      EVAL IP.GetHostByName("localhost", addr);
      agent := TCPNetObj.Locate(IP.Endpoint{addr, port});
      IO.Put("missing: " & Fmt.Bool(NetObj.Import("nothing", agent) = NIL) & "\n");
      t := NetObj.Import("test", agent);
      IO.Put("imported: " & Fmt.Bool(t # NIL) & "\n");
      IO.Put("add: " & Fmt.Int(t.add(40, 2)) & "\n");
      IO.Put("add big: " & Fmt.Int(t.add(LAST(INTEGER) - 5, 3)) & "\n");
      IO.Put("add negative: " & Fmt.Int(t.add(FIRST(INTEGER), 7)) & "\n");
      IO.Put("echo: " & t.echo("hello") & "\n");
      IO.Put("echo empty: " & t.echo("") & "\n");
      IO.Put("echo nil: " & Fmt.Bool(t.echo(NIL) = NIL) & "\n");
      WITH e = t.echo(wide) DO
        IO.Put("echo wide:");
        FOR i := 0 TO Text.Length(e) - 1 DO
          IO.Put(" " & Fmt.Int(ORD(Text.GetWideChar(e, i))))
        END;
        IO.Put("\n");
      END;
      WITH long = Fmt.Pad("", 3000, 'y') DO
        IO.Put("echo long: " & Fmt.Int(Text.Length(t.echo(long))) & "\n");
      END;
      r := Test.Rec{i := -7, r := 1.5, l := 2.25d0, b := TRUE, c := 'a',
                    w := W'\x1234', s := -3, e := Test.Color.Blue,
                    set := SET OF [0..40] {3, 17, 39}};
      t.rec(r, out);
      ShowRec(out);
      IO.Put("sum list: " & Fmt.Int(t.sum(t.list(10))) & "\n");
      IO.Put("sum nil: " & Fmt.Int(t.sum(NIL)) & "\n");
      IO.Put("names: " & t.list(3).name & " " & t.list(3).tail.tail.name & "\n");
      t.arr(a);
      FOR i := 0 TO LAST(a) DO IO.Put(Fmt.Int(a[i]) & " ") END;
      IO.Put("\n");
      IO.Put("chars: " & Fmt.Int(Text.Length(t.chars(cs, ws))));
      FOR i := 0 TO LAST(ws) DO IO.Put(" " & Fmt.Int(ORD(ws[i]))) END;
      IO.Put("\n");
      IO.Put("texts: " & t.texts(ts) & "\n");
      TRY
        t.fail(0);
        IO.Put("fail 0: ok\n")
      EXCEPT
        Test.Oops(x) => IO.Put("oops " & x & "\n")
      END;
      TRY
        t.fail(1)
      EXCEPT
        Test.Oops(x) => IO.Put("oops " & x & "\n")
      END;
      TRY
        t.fail(2)
      EXCEPT
      | Test.Oops(x) => IO.Put("oops " & x & "\n")
      | NetObj.Error(x) => ShowError(x)
      END;
      IO.Put("callback: " & Fmt.Int(t.callback(cb, 10)) & " calls "
             & Fmt.Int(cb.n) & "\n");
      u := t.other();
      u2 := t.other();
      IO.Put("other: " & Fmt.Bool(u # NIL) & " same surrogate: " & Fmt.Bool(u = u2) & "\n");
      IO.Put("other sum list: " & Fmt.Int(u.sum(u.list(20))) & "\n");
      ShowRec(u.rec(r));
      IO.Put("owner comes home: " & Fmt.Bool(u.same(t)) & "\n");
      IO.Put("other object: " & Fmt.Bool(u.same(cb)) & "\n");
      IO.Put("reader: " & t.reader(TextRd.New("some text for the reader")) & "\n");
      t.writer(wr, "abc");
      IO.Put("writer: " & TextWr.ToText(wr) & "\n");
      t.quit();
      IO.Put("client done\n");
    EXCEPT
    | NetObj.Error(x) => ShowError(x)
    END;
  END Client;

<*FATAL Lex.Error, FloatMode.Trap*>
BEGIN
  IF Text.Equal(Params.Get(1), "server") THEN
    Server();
  ELSE
    Client(Scan.Int(Params.Get(2)));
  END;
END Main.
