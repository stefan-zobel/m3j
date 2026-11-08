(* Pickles: every kind of field (ordinals, subranges with and without a
   sign, enumerations, floating-point numbers, small and large sets,
   packed fields, padding), records and fixed arrays in referents, open
   arrays of one and two dimensions, references to scalars and texts,
   objects of three levels with methods and procedure fields, sharing,
   cycles and NIL, texts, Atom.T, RefList.T, a special of the program, a
   subtype of Pickle.Writer, several pickles in one stream, and errors.
   Each pickle is written in hex and read back. The pickles of texts that
   a program computes are read from bytes that CM3 wrote (its texts of
   the classes Text8, Text8Short, Text16, Text16Short, TextCat, TextSub). *)

MODULE Main;

IMPORT IO, Fmt, Pickle, TextWr, TextRd, Wr, Rd, Text, Thread, Atom, RefList;
IMPORT Word;

<*FATAL Wr.Failure, Rd.Failure, Thread.Alerted*>

TYPE
  Color = {Red, Green, Blue, Cyan};

  Scalars = REF RECORD
    i: INTEGER;
    card: CARDINAL;
    neg: [-5..5];
    small: [0..200];
    big: [0..100000];
    huge: [-3000000000..3000000000];
    ch: CHAR;
    wc: WIDECHAR;
    b: BOOLEAN;
    col: Color;
    r: REAL;
    lr: LONGREAL;
    x: EXTENDED;
    s10: SET OF [0..9];
    scol: SET OF Color;
    s100: SET OF [0..99];
    li: LONGINT;
  END;

  Packed = REF RECORD
    a: BITS 3 FOR [0..7];
    b: BITS 5 FOR [-16..15];
    c: BITS 1 FOR BOOLEAN;
    d: BITS 7 FOR [0..100];
    e: BITS 16 FOR [0..65535];
    f: BITS 8 FOR CHAR;
    g: BITS 2 FOR Color;
  END;

  Point = RECORD x, y: INTEGER END;
  Inner = RECORD c: CHAR; p: Point; flags: ARRAY [0..2] OF BOOLEAN END;
  Nested = REF RECORD
    in: Inner;
    pts: ARRAY [0..2] OF Point;
    grid: ARRAY [0..1] OF ARRAY [0..2] OF [0..255];
    name: TEXT;
    sets: ARRAY [0..1] OF SET OF [0..99];
    w: ARRAY [0..2] OF WIDECHAR;
  END;

  Ints = REF ARRAY OF INTEGER;
  Chars = REF ARRAY OF CHAR;
  Bools = REF ARRAY OF BOOLEAN;
  Matrix = REF ARRAY OF ARRAY OF REAL;
  Points = REF ARRAY OF Point;
  Texts = REF ARRAY OF TEXT;
  Shorts = REF ARRAY OF [-300..300];

  RefInt = REF INTEGER;
  RefText = REF TEXT;
  RefColor = REF Color;
  RefLong = REF LONGREAL;
  RefChars4 = REF ARRAY [0..3] OF CHAR;

  A = OBJECT
    a: INTEGER;
    t: TEXT;
  METHODS
    m (): TEXT := MA;
  END;
  B = A OBJECT
    b: CHAR;
  OVERRIDES
    m := MB;
  END;
  C = B BRANDED "check-pickle C" OBJECT
    c: REAL;
    next: A;
    p: PROCEDURE (): INTEGER;
    u: UNTRACED REF INTEGER;
  END;

  Node = REF RECORD v: INTEGER; next, other: Node END;

  SOObj = OBJECT name: TEXT END;

  Counter = REF RECORD n: INTEGER END;

  CmTexts = REF RECORD a, b, c: TEXT; n: INTEGER END;

PROCEDURE MA (self: A): TEXT =
  BEGIN
    RETURN "MA " & Fmt.Int (self.a);
  END MA;

PROCEDURE MB (self: A): TEXT =
  BEGIN
    RETURN "MB " & Fmt.Int (self.a);
  END MB;

PROCEDURE MX (<*UNUSED*> self: A): TEXT =
  BEGIN
    RETURN "MX";
  END MX;

PROCEDURE Seven (): INTEGER =
  BEGIN
    RETURN 7;
  END Seven;

(*------------------------------------------------------------- output ---*)

PROCEDURE Hex (t: TEXT) =
  VAR n := Text.Length (t);
  BEGIN
    FOR i := 0 TO n - 1 DO
      IF i MOD 32 = 0 THEN IO.Put ("  ") END;
      IO.Put (Fmt.Pad (Fmt.Int (ORD (Text.GetChar (t, i)), 16), 2, '0'));
      IF i MOD 32 = 31 OR i = n - 1 THEN IO.Put ("\n") END;
    END;
  END Hex;

PROCEDURE Pkl (r: REFANY): TEXT =
  VAR wr := TextWr.New ();
  BEGIN
    TRY
      Pickle.Write (wr, r);
    EXCEPT
    | Pickle.Error (e) => IO.Put ("  Pickle.Error: " & e & "\n");
    END;
    RETURN TextWr.ToText (wr);
  END Pkl;

PROCEDURE Unpkl (t: TEXT): REFANY =
  BEGIN
    TRY
      RETURN Pickle.Read (TextRd.New (t));
    EXCEPT
    | Pickle.Error (e) => IO.Put ("  Pickle.Error: " & e & "\n");
    | Rd.EndOfFile => IO.Put ("  Rd.EndOfFile\n");
    END;
    RETURN NIL;
  END Unpkl;

(* Writes r as a pickle in hex, and returns the value read back. *)
PROCEDURE Round (name: TEXT; r: REFANY): REFANY =
  VAR t := Pkl (r);
  BEGIN
    IO.Put (name & " (" & Fmt.Int (Text.Length (t)) & " bytes)\n");
    Hex (t);
    RETURN Unpkl (t);
  END Round;

PROCEDURE B2T (b: BOOLEAN): TEXT =
  BEGIN
    IF b THEN RETURN "TRUE" ELSE RETURN "FALSE" END;
  END B2T;

PROCEDURE SetText (s: SET OF [0..99]): TEXT =
  VAR t := "{";
  BEGIN
    FOR i := 0 TO 99 DO
      IF i IN s THEN t := t & " " & Fmt.Int (i) END;
    END;
    RETURN t & " }";
  END SetText;

PROCEDURE Wide (t: TEXT): TEXT =
  VAR r := "";
  BEGIN
    FOR i := 0 TO Text.Length (t) - 1 DO
      WITH c = Text.GetWideChar (t, i) DO
        IF ORD (c) < 128 THEN
          r := r & Text.FromChar (VAL (ORD (c), CHAR));
        ELSE
          r := r & "<" & Fmt.Int (ORD (c), 16) & ">";
        END;
      END;
    END;
    RETURN r;
  END Wide;

(*-------------------------------------------------------------- cases ---*)

PROCEDURE TestScalars () =
  VAR s := NEW (Scalars);
  BEGIN
    s.i := -1234567890123; s.card := 98765; s.neg := -3; s.small := 200;
    s.big := 99999; s.huge := -2999999999; s.ch := 'Z'; s.wc := W'\x263A';
    s.b := TRUE; s.col := Color.Cyan; s.r := 1.5E0; s.lr := -2.25D-10;
    s.x := 3.0X100; s.s10 := SET OF [0..9] {0, 3, 9};
    s.scol := SET OF Color {Color.Red, Color.Blue};
    s.s100 := SET OF [0..99] {1, 50, 63, 64, 99}; s.li := -5L;
    TYPECASE Round ("scalars", s) OF
    | Scalars (t) =>
        IO.Put ("  " & Fmt.Int (t.i) & " " & Fmt.Int (t.card) & " " & Fmt.Int (t.neg)
          & " " & Fmt.Int (t.small) & " " & Fmt.Int (t.big) & " " & Fmt.Int (t.huge)
          & " " & Text.FromChar (t.ch) & " " & Fmt.Int (ORD (t.wc)) & " " & B2T (t.b)
          & " " & Fmt.Int (ORD (t.col)) & " " & Fmt.Real (t.r) & " " & Fmt.LongReal (t.lr)
          & " " & Fmt.Extended (t.x) & " " & Fmt.LongInt (t.li) & "\n");
        FOR i := 0 TO 9 DO IF i IN t.s10 THEN IO.Put (" " & Fmt.Int (i)) END END;
        FOR c := FIRST (Color) TO LAST (Color) DO
          IF c IN t.scol THEN IO.Put (" c" & Fmt.Int (ORD (c))) END;
        END;
        IO.Put ("  " & SetText (t.s100) & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
  END TestScalars;

PROCEDURE TestPacked () =
  VAR p := NEW (Packed);
  BEGIN
    p.a := 5; p.b := -9; p.c := TRUE; p.d := 77; p.e := 54321; p.f := 'q';
    p.g := Color.Blue;
    TYPECASE Round ("packed", p) OF
    | Packed (q) =>
        IO.Put ("  " & Fmt.Int (q.a) & " " & Fmt.Int (q.b) & " " & B2T (q.c) & " "
          & Fmt.Int (q.d) & " " & Fmt.Int (q.e) & " " & Text.FromChar (q.f) & " "
          & Fmt.Int (ORD (q.g)) & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
  END TestPacked;

PROCEDURE TestNested () =
  VAR n := NEW (Nested);
  BEGIN
    n.in.c := 'k'; n.in.p.x := 11; n.in.p.y := -12;
    n.in.flags[0] := TRUE; n.in.flags[2] := TRUE;
    FOR i := 0 TO 2 DO n.pts[i].x := i * 10; n.pts[i].y := -i END;
    FOR i := 0 TO 1 DO FOR j := 0 TO 2 DO n.grid[i][j] := 100 * i + j + 50 END END;
    n.name := "nested";
    n.sets[0] := SET OF [0..99] {7, 77};
    n.sets[1] := SET OF [0..99] {0, 98};
    n.w[0] := W'a'; n.w[1] := W'\x00E9'; n.w[2] := W'\xFFFF';
    TYPECASE Round ("nested", n) OF
    | Nested (m) =>
        IO.Put ("  " & Text.FromChar (m.in.c) & " " & Fmt.Int (m.in.p.x) & " "
          & Fmt.Int (m.in.p.y) & " " & B2T (m.in.flags[0]) & B2T (m.in.flags[1])
          & B2T (m.in.flags[2]) & " " & m.name & "\n ");
        FOR i := 0 TO 2 DO
          IO.Put (" (" & Fmt.Int (m.pts[i].x) & "," & Fmt.Int (m.pts[i].y) & ")");
        END;
        FOR i := 0 TO 1 DO FOR j := 0 TO 2 DO IO.Put (" " & Fmt.Int (m.grid[i][j])) END END;
        IO.Put (" " & SetText (m.sets[0]) & " " & SetText (m.sets[1]));
        FOR i := 0 TO 2 DO IO.Put (" " & Fmt.Int (ORD (m.w[i]))) END;
        IO.Put ("\n");
    ELSE IO.Put ("  wrong type\n");
    END;
  END TestNested;

PROCEDURE TestArrays () =
  VAR
    ints := NEW (Ints, 5);
    chars := NEW (Chars, 7);
    bools := NEW (Bools, 0);
    mat := NEW (Matrix, 2, 3);
    pts := NEW (Points, 2);
    txts := NEW (Texts, 3);
    shorts := NEW (Shorts, 4);
  BEGIN
    FOR i := 0 TO 4 DO ints[i] := i * i - 3 END;
    FOR i := 0 TO 6 DO chars[i] := VAL (ORD ('a') + i, CHAR) END;
    FOR i := 0 TO 1 DO FOR j := 0 TO 2 DO mat[i, j] := FLOAT (i) + FLOAT (j) / 4.0 END END;
    pts[0].x := 1; pts[0].y := 2; pts[1].x := -3; pts[1].y := 4;
    txts[0] := "zero"; txts[1] := NIL; txts[2] := "zero";
    shorts[0] := -300; shorts[1] := 300; shorts[2] := -1; shorts[3] := 0;
    TYPECASE Round ("ints", ints) OF
    | Ints (a) => FOR i := 0 TO LAST (a^) DO IO.Put (" " & Fmt.Int (a[i])) END; IO.Put ("\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    TYPECASE Round ("chars", chars) OF
    | Chars (a) => IO.Put ("  " & Text.FromChars (a^) & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    TYPECASE Round ("bools", bools) OF
    | Bools (a) => IO.Put ("  " & Fmt.Int (NUMBER (a^)) & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    TYPECASE Round ("matrix", mat) OF
    | Matrix (a) =>
        IO.Put ("  " & Fmt.Int (NUMBER (a^)) & "x" & Fmt.Int (NUMBER (a[0])));
        FOR i := 0 TO 1 DO FOR j := 0 TO 2 DO IO.Put (" " & Fmt.Real (a[i, j])) END END;
        IO.Put ("\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    TYPECASE Round ("points", pts) OF
    | Points (a) =>
        FOR i := 0 TO LAST (a^) DO
          IO.Put (" (" & Fmt.Int (a[i].x) & "," & Fmt.Int (a[i].y) & ")");
        END;
        IO.Put ("\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    TYPECASE Round ("texts", txts) OF
    | Texts (a) =>
        IO.Put ("  " & a[0] & " " & B2T (a[1] = NIL) & " " & a[2] & " shared "
          & B2T (a[0] = a[2]) & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    TYPECASE Round ("shorts", shorts) OF
    | Shorts (a) => FOR i := 0 TO LAST (a^) DO IO.Put (" " & Fmt.Int (a[i])) END; IO.Put ("\n");
    ELSE IO.Put ("  wrong type\n");
    END;
  END TestArrays;

PROCEDURE TestRefs () =
  VAR
    ri := NEW (RefInt);
    rt := NEW (RefText);
    rc := NEW (RefColor);
    rl := NEW (RefLong);
    r4 := NEW (RefChars4);
  BEGIN
    ri^ := 424242; rt^ := "in a ref"; rc^ := Color.Green; rl^ := 1.0D300;
    r4^ := ARRAY [0..3] OF CHAR {'w', 'x', 'y', 'z'};
    TYPECASE Round ("ref integer", ri) OF
    | RefInt (r) => IO.Put ("  " & Fmt.Int (r^) & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    TYPECASE Round ("ref text", rt) OF
    | RefText (r) => IO.Put ("  " & r^ & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    TYPECASE Round ("ref color", rc) OF
    | RefColor (r) => IO.Put ("  " & Fmt.Int (ORD (r^)) & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    TYPECASE Round ("ref longreal", rl) OF
    | RefLong (r) => IO.Put ("  " & Fmt.LongReal (r^) & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    TYPECASE Round ("ref array", r4) OF
    | RefChars4 (r) => IO.Put ("  " & Text.FromChars (r^) & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
  END TestRefs;

PROCEDURE TestObjects () =
  VAR
    a := NEW (A, a := 1, t := "first");
    b := NEW (B, a := 2, t := "second", b := '!');
    c := NEW (C, a := 3, t := "third", b := '?', c := 2.5E0, next := b,
              p := Seven, u := NIL);
    x := NEW (A, a := 4, m := MX);
  BEGIN
    TYPECASE Round ("object A", a) OF
    | A (o) => IO.Put ("  " & o.m () & " " & o.t & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    TYPECASE Round ("object C", c) OF
    | C (o) =>
        IO.Put ("  " & o.m () & " " & o.t & " " & Text.FromChar (o.b) & " "
          & Fmt.Real (o.c) & " p=NIL " & B2T (o.p = NIL) & " next " & o.next.m ()
          & " " & o.next.t & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    TYPECASE Round ("object with a method", x) OF
    | A (o) => IO.Put ("  " & o.m () & " " & Fmt.Int (o.a) & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    TYPECASE Round ("p197", NEW (SOObj, name := "aaaa")) OF
    | SOObj (o) => IO.Put ("  " & o.name & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
  END TestObjects;

PROCEDURE TestSharing () =
  VAR
    a := NEW (Node, v := 1);
    b := NEW (Node, v := 2);
    c := NEW (Node, v := 3);
  BEGIN
    a.next := b; b.next := c; c.next := a;     (* a cycle *)
    a.other := c; b.other := b; c.other := NIL;
    TYPECASE Round ("cycle", a) OF
    | Node (n) =>
        IO.Put ("  " & Fmt.Int (n.v) & Fmt.Int (n.next.v) & Fmt.Int (n.next.next.v)
          & " cycle " & B2T (n.next.next.next = n)
          & " other " & B2T (n.other = n.next.next)
          & " self " & B2T (n.next.other = n.next)
          & " nil " & B2T (n.next.next.other = NIL) & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    EVAL Round ("NIL", NIL);
  END TestSharing;

PROCEDURE TestTexts () =
  VAR
    t1 := "a literal";
    t2 := "";
    t3 := W"wide \x263A text";
    t4: TEXT;
  BEGIN
    TYPECASE Round ("text", t1) OF
    | TEXT (t) => IO.Put ("  " & t & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    TYPECASE Round ("empty text", t2) OF
    | TEXT (t) => IO.Put ("  [" & t & "] " & Fmt.Int (Text.Length (t)) & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    TYPECASE Round ("wide text", t3) OF
    | TEXT (t) => IO.Put ("  " & Wide (t) & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    (* a computed text: CM3 and m3j write it differently, so no hex *)
    t4 := Text.Cat (t1, Fmt.Int (12345));
    TYPECASE Unpkl (Pkl (t4)) OF
    | TEXT (t) => IO.Put ("computed text\n  " & t & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
  END TestTexts;

CONST
  CmTextPickles = ARRAY OF TEXT {
  (* Text8 *)
  "\046\045\062\111\114\066\116\065\000\156\165\154\154\000\000\200\044\000\364\357\016\231\057\012"
  & "\176\004\065\001\000\027\162\117\236\237\233\304\031\033\000\000\000\141\142\143\144\145\146\147"
  & "\150\151\152\153\154\155\156\157\160\161\162\163\164\165\166\167\170\171\172\000\064\012",
  (* Text8Short *)
  "\046\045\062\111\114\066\116\065\000\156\165\154\154\000\000\200\044\000\030\364\134\342\264\147"
  & "\376\036\005\000\000\000\000\000\000\000\163\150\157\162\164\000\000\000\000\000\000\000\000\000"
  & "\000\000\064\012",
  (* Text16 *)
  "\046\045\062\111\114\066\116\065\000\156\165\154\154\000\000\200\044\000\047\017\033\247\310\355"
  & "\003\001\065\001\000\174\171\227\062\223\365\003\003\024\000\000\000\141\000\072\046\142\000\143"
  & "\000\144\000\145\000\146\000\147\000\150\000\151\000\152\000\153\000\154\000\155\000\156\000\157"
  & "\000\160\000\161\000\162\000\000\000\064\012",
  (* Text16Short *)
  "\046\045\062\111\114\066\116\065\000\156\165\154\154\000\000\200\044\000\277\053\061\231\231\117"
  & "\075\020\003\000\000\000\000\000\000\000\170\000\351\000\055\116\000\000\000\000\000\000\000\000"
  & "\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\064\012",
  (* TextCat *)
  "\046\045\062\111\114\066\116\065\000\156\165\154\154\000\000\200\044\000\106\315\017\352\335\312"
  & "\251\025\065\001\000\364\357\016\231\057\012\176\004\065\001\000\027\162\117\236\237\233\304\031"
  & "\033\000\000\000\141\142\143\144\145\146\147\150\151\152\153\154\155\156\157\160\161\162\163\164"
  & "\165\166\167\170\171\172\000\065\001\000\030\364\134\342\264\147\376\036\005\000\000\000\000\000"
  & "\000\000\163\150\157\162\164\000\000\000\000\000\000\000\000\000\000\000\032\000\000\000\000\000"
  & "\000\000\005\000\000\000\000\000\000\000\000\000\000\000\000\000\000\000\064\012",
  (* TextSub *)
  "\046\045\062\111\114\066\116\065\000\156\165\154\154\000\000\200\044\000\355\307\204\075\067\351"
  & "\104\007\065\001\000\364\357\016\231\057\012\176\004\065\001\000\027\162\117\236\237\233\304\031"
  & "\033\000\000\000\141\142\143\144\145\146\147\150\151\152\153\154\155\156\157\160\161\162\163\164"
  & "\165\166\167\170\171\172\000\003\000\000\000\000\000\000\000\024\000\000\000\000\000\000\000\064"
  & "\012",
  (* Texts *)
  "\046\045\062\111\114\066\116\065\000\156\165\154\154\000\000\200\044\000\031\172\217\150\043\074"
  & "\166\027\065\001\000\106\315\017\352\335\312\251\025\065\001\000\364\357\016\231\057\012\176\004"
  & "\065\001\000\027\162\117\236\237\233\304\031\033\000\000\000\141\142\143\144\145\146\147\150\151"
  & "\152\153\154\155\156\157\160\161\162\163\164\165\166\167\170\171\172\000\065\001\000\030\364\134"
  & "\342\264\147\376\036\005\000\000\000\000\000\000\000\163\150\157\162\164\000\000\000\000\000\000"
  & "\000\000\000\000\000\032\000\000\000\000\000\000\000\005\000\000\000\000\000\000\000\000\000\000"
  & "\000\000\000\000\000\065\001\000\355\307\204\075\067\351\104\007\061\002\000\000\000\003\000\000"
  & "\000\000\000\000\000\024\000\000\000\000\000\000\000\061\001\000\000\000\052\000\000\000\000\000"
  & "\000\000\064\012"
  };

PROCEDURE TestCm3Texts () =
  BEGIN
    IO.Put ("texts written by CM3\n");
    FOR i := 0 TO LAST (CmTextPickles) DO
      TYPECASE Unpkl (CmTextPickles[i]) OF
      | NULL => IO.Put ("  NIL\n");
      | TEXT (t) => IO.Put ("  " & Wide (t) & "\n");
      | CmTexts (r) =>
          IO.Put ("  " & r.a & " " & r.b & " " & Fmt.Int (r.n) & " shared "
            & B2T (r.a = r.c) & "\n");
      ELSE IO.Put ("  other\n");
      END;
    END;
  END TestCm3Texts;

PROCEDURE TestAtomsLists () =
  VAR
    at := Atom.FromText ("an atom");
    l := RefList.List3 ("one", Atom.FromText ("two"), RefList.List1 ("three"));
  BEGIN
    TYPECASE Round ("atom", at) OF
    | Atom.T (a) => IO.Put ("  " & Atom.ToText (a) & " same " & B2T (a = at) & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    TYPECASE Round ("list", l) OF
    | RefList.T (m) =>
        IO.Put ("  " & Fmt.Int (RefList.Length (m)) & " " & NARROW (m.head, TEXT) & " "
          & Atom.ToText (m.tail.head) & " "
          & NARROW (NARROW (m.tail.tail.head, RefList.T).head, TEXT) & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
  END TestAtomsLists;

(* A special of the program: a Counter is written as 2*n + 1. *)
TYPE
  CounterSpecial = Pickle.Special OBJECT
  OVERRIDES
    write := WriteCounter;
    read := ReadCounter;
  END;

PROCEDURE WriteCounter (<*UNUSED*> sp: CounterSpecial; r: REFANY; w: Pickle.Writer)
  RAISES {Pickle.Error, Wr.Failure, Thread.Alerted} =
  VAR c: Counter := r;
  BEGIN
    w.writeInt (2 * c.n + 1);
  END WriteCounter;

PROCEDURE ReadCounter (<*UNUSED*> sp: CounterSpecial; rd: Pickle.Reader;
                       <*UNUSED*> id: Pickle.RefID): REFANY
  RAISES {Pickle.Error, Rd.EndOfFile, Rd.Failure, Thread.Alerted} =
  BEGIN
    RETURN NEW (Counter, n := (rd.readInt () - 1) DIV 2);
  END ReadCounter;

(* A writer that counts the references it writes. *)
TYPE
  CountingWriter = Pickle.Writer OBJECT
    count := 0;
  OVERRIDES
    write := CountingWrite;
  END;

PROCEDURE CountingWrite (self: CountingWriter; r: REFANY)
  RAISES {Pickle.Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    INC (self.count);
    Pickle.Writer.write (self, r);
  END CountingWrite;

PROCEDURE TestCustom () =
  VAR
    ctr := NEW (Counter, n := 21);
    wr := TextWr.New ();
    cw := NEW (CountingWriter, wr := wr);
    a := NEW (Node, v := 5);
    t: TEXT;
  BEGIN
    Pickle.RegisterSpecial (NEW (CounterSpecial, sc := TYPECODE (Counter)));
    TYPECASE Round ("special", ctr) OF
    | Counter (c) => IO.Put ("  " & Fmt.Int (c.n) & "\n");
    ELSE IO.Put ("  wrong type\n");
    END;
    a.next := NEW (Node, v := 6, next := a);
    TRY
      cw.write (a);
      cw.write (ctr);       (* the writer is used again *)
    EXCEPT
    | Pickle.Error (e) => IO.Put ("  Pickle.Error: " & e & "\n");
    END;
    t := TextWr.ToText (wr);
    IO.Put ("two pickles, written by a subtype of Writer (" & Fmt.Int (cw.count)
      & " calls of write)\n");
    Hex (t);
    VAR rd := TextRd.New (t); rr := NEW (Pickle.Reader, rd := rd); BEGIN
      TRY
        TYPECASE rr.read () OF
        | Node (n) => IO.Put ("  " & Fmt.Int (n.v) & " " & Fmt.Int (n.next.v) & " "
                        & B2T (n.next.next = n) & "\n");
        ELSE IO.Put ("  wrong type\n");
        END;
        TYPECASE rr.read () OF
        | Counter (c) => IO.Put ("  " & Fmt.Int (c.n) & "\n");
        ELSE IO.Put ("  wrong type\n");
        END;
        IO.Put ("  at end " & B2T (Rd.EOF (rd)) & "\n");
      EXCEPT
      | Pickle.Error (e) => IO.Put ("  Pickle.Error: " & e & "\n");
      | Rd.EndOfFile => IO.Put ("  Rd.EndOfFile\n");
      END;
    END;
  END TestCustom;

PROCEDURE Patch (t: TEXT; i: INTEGER; c: CHAR): TEXT =
  BEGIN
    RETURN Text.Sub (t, 0, i) & Text.FromChar (c) & Text.Sub (t, i + 1);
  END Patch;

PROCEDURE TestErrors () =
  VAR t := Pkl (NEW (RefInt));
  BEGIN
    IO.Put ("errors\n");
    EVAL Unpkl ("not a pickle");
    EVAL Unpkl (Patch (t, 2, '9'));                   (* version *)
    EVAL Unpkl (Patch (t, 4, 'B'));                   (* endian *)
    EVAL Unpkl (Text.Sub (t, 0, Text.Length (t) - 3)); (* too short *)
    EVAL Unpkl (Patch (t, 7, '7'));                   (* switch *)
    EVAL Unpkl (Patch (t, Text.Length (t) - 1, 'x')); (* trailer *)
    (* a fingerprint that no type of the program has *)
    EVAL Unpkl (Patch (t, 19, VAL (Word.Xor (ORD (Text.GetChar (t, 19)), 1), CHAR)));
    EVAL Unpkl ("");
  END TestErrors;

BEGIN
  TestScalars ();
  TestPacked ();
  TestNested ();
  TestArrays ();
  TestRefs ();
  TestObjects ();
  TestSharing ();
  TestTexts ();
  TestCm3Texts ();
  TestAtomsLists ();
  TestCustom ();
  TestErrors ();
END Main.
