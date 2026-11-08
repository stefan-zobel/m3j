(* Set constructors whose elements and ranges are known only at run time
   (small sets, and large sets of more than 64 elements whose ranges lie
   within a word or span several words, are empty or reach the ends of
   the domain), the changes S := S + {..}, S := {..} + S and S := S - {..}
   of large sets (also of sets in arrays and records, and into another
   variable), and = and # on arrays and records of every kind of element
   (BOOLEAN, CHAR, WIDECHAR, subranges of every size, INTEGER, LONGINT,
   REAL, LONGREAL, references, sets), fixed, open and SUBARRAY.

   As cm3 compares structures: a fixed array or a record without holes
   compares as its bits (0.0 and -0.0 differ, a NaN equals itself); the
   fields of REAL types of a record with holes, and the elements of REAL
   types of an open array, compare as numbers.

   With an argument, the program runs only what cm3 gets wrong (see
   HandChecked). *)

MODULE Main;

IMPORT IO, Fmt, Params;

TYPE
  Small = SET OF [0 .. 63];
  Shifted = SET OF [10 .. 40];
  Letters = SET OF ['a' .. 'z'];
  Color = {Red, Orange, Yellow, Green, Blue, Indigo, Violet};
  Colors = SET OF Color;
  Big = SET OF [0 .. 199];
  BigShifted = SET OF [100 .. 299];
  BigArr = ARRAY [0 .. 2] OF Big;
  BigRec = RECORD n: INTEGER;  s: Big;  t: BigShifted END;

VAR
  small: Small;
  shifted: Shifted;
  letters: Letters;
  colors: Colors;
  big, big2: Big;
  bigS: BigShifted;
  bigArr: BigArr;
  bigRec: BigRec;

PROCEDURE Id (n: INTEGER): INTEGER =
  BEGIN
    IF Params.Count > 1000 THEN RETURN 0 END;
    RETURN n;
  END Id;

PROCEDURE C (c: CHAR): CHAR =
  BEGIN
    IF Params.Count > 1000 THEN RETURN 'a' END;
    RETURN c;
  END C;

PROCEDURE Col (c: Color): Color =
  BEGIN
    IF Params.Count > 1000 THEN RETURN Color.Red END;
    RETURN c;
  END Col;

PROCEDURE Line (t: TEXT) =
  BEGIN
    IO.Put(t & "\n");
  END Line;

PROCEDURE I (n: INTEGER): TEXT =
  BEGIN
    RETURN Fmt.Int(n);
  END I;

PROCEDURE B (b: BOOLEAN): TEXT =
  BEGIN
    IF b THEN RETURN "T" ELSE RETURN "F" END;
  END B;

PROCEDURE SmallText (s: Small): TEXT =
  VAR t := "{";
  BEGIN
    FOR i := 0 TO 63 DO
      IF i IN s THEN t := t & " " & I(i) END;
    END;
    RETURN t & " }";
  END SmallText;

PROCEDURE ShiftedText (s: Shifted): TEXT =
  VAR t := "{";
  BEGIN
    FOR i := 10 TO 40 DO
      IF i IN s THEN t := t & " " & I(i) END;
    END;
    RETURN t & " }";
  END ShiftedText;

PROCEDURE LettersText (s: Letters): TEXT =
  VAR t := "{";
  BEGIN
    FOR c := 'a' TO 'z' DO
      IF c IN s THEN t := t & " " & Fmt.Char(c) END;
    END;
    RETURN t & " }";
  END LettersText;

PROCEDURE ColorsText (s: Colors): TEXT =
  VAR t := "{";
  BEGIN
    FOR c := FIRST(Color) TO LAST(Color) DO
      IF c IN s THEN t := t & " " & I(ORD(c)) END;
    END;
    RETURN t & " }";
  END ColorsText;

(* The elements of a large set, with runs written as a..b. *)
PROCEDURE BigText (READONLY s: Big): TEXT =
  VAR t := "{";  i := 0;  j: INTEGER;
  BEGIN
    WHILE i <= 199 DO
      IF i IN s THEN
        j := i;
        WHILE j < 199 AND j + 1 IN s DO INC(j) END;
        IF j = i THEN t := t & " " & I(i) ELSE t := t & " " & I(i) & ".." & I(j) END;
        i := j + 1;
      ELSE
        INC(i);
      END;
    END;
    RETURN t & " }";
  END BigText;

PROCEDURE BigShiftedText (READONLY s: BigShifted): TEXT =
  VAR t := "{";  i := 100;  j: INTEGER;
  BEGIN
    WHILE i <= 299 DO
      IF i IN s THEN
        j := i;
        WHILE j < 299 AND j + 1 IN s DO INC(j) END;
        IF j = i THEN t := t & " " & I(i) ELSE t := t & " " & I(i) & ".." & I(j) END;
        i := j + 1;
      ELSE
        INC(i);
      END;
    END;
    RETURN t & " }";
  END BigShiftedText;

(* A set that the compiler cannot know: a call in a constructor. *)
PROCEDURE Some (n: INTEGER): Big =
  BEGIN
    RETURN Big {n, n + 1};
  END Some;

PROCEDURE SmallSets () =
  BEGIN
    Line("-- small sets");
    FOR k := 0 TO 4 DO
      CASE k OF
      | 0 => small := Small {Id(0) .. Id(63)};
      | 1 => small := Small {Id(31) .. Id(63)};
      | 2 => small := Small {Id(62) .. Id(63), Id(0), Id(5)};
      | 3 => small := Small {1 .. 3, Id(7), 9 .. 10};
      | 4 => small := Small {Id(63) .. Id(63), 40 .. 41};
      END;
      Line(SmallText(small));
    END;
    shifted := Shifted {Id(20), 11 .. 13, Id(40)};
    Line(ShiftedText(shifted));
    letters := Letters {C('z'), 'a' .. 'b', C('m')};
    Line(LettersText(letters));
    colors := Colors {Col(Color.Orange), Color.Yellow .. Color.Green};
    Line(ColorsText(colors));
    Line(B(Id(40) IN Small {Id(3) .. Id(63)}) & B(Id(2) IN Small {Id(3) .. Id(63)})
           & B(Small {Id(60) .. Id(63)} = Small {Id(60), Id(61), 62, Id(63)})
           & B(Small {Id(61) .. Id(63)} <= Small {Id(59) .. Id(63)}));
  END SmallSets;

(* What cm3 gets wrong, run only with an argument (its output is checked
   by hand): ranges of small sets whose upper bound is not the last
   element of a word ({a..b} is {a..63} for cm3's C back end, which shifts
   -1 right arithmetically), and <, <=, >, >= on large sets (whose
   operands cm3's C back end swaps). *)
PROCEDURE HandChecked () =
  VAR a, b: Big;
  BEGIN
    Line("-- ranges of small sets");
    FOR k := 0 TO 4 DO
      CASE k OF
      | 0 => small := Small {Id(0) .. Id(62)};
      | 1 => small := Small {Id(5) .. Id(4)};
      | 2 => small := Small {Id(62) .. Id(63), Id(0), Id(31) .. Id(33)};
      | 3 => small := Small {Id(1) .. Id(3), Id(7), 9 .. 10};
      | 4 => small := Small {Id(17) .. Id(17), 40 .. 41};
      END;
      Line(SmallText(small));
    END;
    shifted := Shifted {Id(10) .. Id(12), Id(39) .. Id(40)};
    Line(ShiftedText(shifted));
    shifted := Shifted {Id(10) .. Id(40)};
    Line(ShiftedText(shifted));
    shifted := Shifted {Id(20), 11 .. 13} + Shifted {Id(30) .. Id(29)};
    Line(ShiftedText(shifted));
    letters := Letters {C('c') .. C('f'), C('z'), 'a' .. 'b'};
    Line(LettersText(letters));
    colors := Colors {Col(Color.Orange) .. Col(Color.Green), Color.Violet};
    Line(ColorsText(colors));
    colors := Colors {Col(Color.Red) .. Col(Color.Violet)} - Colors {Col(Color.Blue)};
    Line(ColorsText(colors));
    Line(B(Id(5) IN Small {Id(3) .. Id(6)}) & B(Id(7) IN Small {Id(3) .. Id(6)})
           & B(Small {Id(1) .. Id(2)} = Small {1, 2})
           & B(Small {Id(1) .. Id(2)} <= Small {Id(0) .. Id(5)}));
    Line("-- order of large sets");
    a := Big {Id(0), Id(3), Id(150)};
    b := Big {Id(0) .. Id(3), Id(150) .. Id(199)};
    Line(B(a <= b) & B(a < b) & B(a >= b) & B(a > b) & " "
           & B(b <= a) & B(b < a) & B(b >= a) & B(b > a) & " "
           & B(a <= a) & B(a < a) & B(a >= a) & B(a > a) & " "
           & B(a <= Big {Id(0) .. Id(3)}) & B(Big {Id(0) .. Id(199)} > a)
           & B(Big {} < a) & B(a <= Big {}));
  END HandChecked;

PROCEDURE BigSets () =
  VAR
    x: Big;
    (* elements in variables: the constructors call nothing, so the set
       can be changed in place *)
    v0 := Id(0);  v7 := Id(7);  v60 := Id(60);  v130 := Id(130);
    v62 := Id(62);  v66 := Id(66);  v199 := Id(199);  v5 := Id(5);
    v4 := Id(4);
  BEGIN
    Line("-- large sets");
    FOR k := 0 TO 8 DO
      CASE k OF
      | 0 => big := Big {Id(0) .. Id(199)};
      | 1 => big := Big {Id(10) .. Id(9)};
      | 2 => big := Big {Id(3) .. Id(60)};
      | 3 => big := Big {Id(60) .. Id(70)};
      | 4 => big := Big {Id(63) .. Id(64), Id(127) .. Id(128), Id(191) .. Id(199)};
      | 5 => big := Big {Id(5) .. Id(150), Id(0)};
      | 6 => big := Big {1 .. 3, Id(100), 197 .. 199};
      | 7 => big := Big {Id(64) .. Id(127)};
      | 8 => big := Some(Id(41)) + Big {Id(50) .. Id(52)};
      END;
      Line(I(k) & " " & BigText(big));
    END;

    Line("-- S := S + {..} and S := S - {..}");
    big := Big {};
    big := big + Big {Id(7)};
    big := big + Big {Id(60) .. Id(130)};
    Line(BigText(big));
    big := Big {Id(199)} + big;
    big := Big {Id(0) .. Id(2), Id(190)} + big;
    Line(BigText(big));
    big := big - Big {Id(64)};
    big := big - Big {Id(100) .. Id(110)};
    big := big - Big {Id(62) .. Id(66), Id(127) .. Id(129)};
    Line(BigText(big));
    big := big - Big {Id(5) .. Id(4)};
    big := big + Big {Id(5) .. Id(4)};
    Line(BigText(big));
    big := big - Big {Id(0) .. Id(199)};
    Line(BigText(big));

    Line("-- into another set");
    big := Big {Id(1), Id(100) .. Id(102)};
    big2 := big + Big {Id(50)};
    Line(BigText(big) & " " & BigText(big2));
    big2 := Big {Id(51) .. Id(53)} + big;
    Line(BigText(big) & " " & BigText(big2));
    big2 := big - Big {Id(101)};
    Line(BigText(big) & " " & BigText(big2));
    x := big + Big {Id(150)};
    Line(BigText(x));
    big2 := big2 + Some(Id(9));
    Line(BigText(big2));
    big2 := big2 - Big {Id(100), Id(1)} + Big {Id(2)};
    Line(BigText(big2));

    Line("-- elements in variables");
    big := Big {};
    big := big + Big {v7};
    big := big + Big {v60 .. v130};
    big := Big {v199} + big;
    big := Big {v0 .. v4, v66} + big;
    Line(BigText(big));
    big := big - Big {v62 .. v66};
    big := big - Big {v7, v199};
    big := big - Big {v5 .. v4};
    Line(BigText(big));
    big := big - Big {v60 .. v130, v0};
    big := big + Big {v5 .. v4};
    Line(BigText(big));
    big2 := big + Big {v130};
    big2 := Big {v62} + big2;
    Line(BigText(big) & " " & BigText(big2));
    big2 := big2 - Big {v62 .. v130};
    big := Big {v60 .. v130};
    Line(BigText(big) & " " & BigText(big2));
    bigArr[v0] := bigArr[2] + Big {v7};
    bigArr[2] := bigArr[2] - Big {v0 .. v199};
    FOR i := 0 TO 2 DO Line(I(i) & " " & BigText(bigArr[i])) END;
    bigRec.s := bigRec.s + Big {v60 .. v66};
    bigRec.s := Big {v199} + bigRec.s;
    bigRec.s := bigRec.s - Big {v0 .. v62};
    Line(BigText(bigRec.s));

    Line("-- a domain that does not start at 0");
    bigS := BigShifted {Id(100) .. Id(163), Id(299)};
    bigS := bigS + BigShifted {Id(164)};
    bigS := bigS - BigShifted {Id(101) .. Id(162)};
    Line(BigShiftedText(bigS));
    bigS := BigShifted {Id(200) .. Id(299)} - BigShifted {Id(228) .. Id(291)};
    Line(BigShiftedText(bigS));

    Line("-- sets in arrays and records");
    FOR i := 0 TO 2 DO
      bigArr[i] := Big {Id(i * 70) .. Id(i * 70 + 3)};
    END;
    bigArr[1] := bigArr[1] + Big {Id(199)};
    bigArr[2] := Big {Id(0)} + bigArr[2];
    bigArr[0] := bigArr[0] - Big {Id(1) .. Id(2)};
    FOR i := 0 TO 2 DO Line(I(i) & " " & BigText(bigArr[i])) END;
    bigArr[1] := bigArr[2] + Big {Id(5)};
    Line(BigText(bigArr[1]) & " " & BigText(bigArr[2]));
    bigRec.n := 3;
    bigRec.s := Big {Id(10) .. Id(140)};
    bigRec.s := bigRec.s - Big {Id(20) .. Id(130)};
    bigRec.t := BigShifted {Id(250)} + bigRec.t;
    bigRec.t := bigRec.t + BigShifted {Id(100) .. Id(101)};
    Line(I(bigRec.n) & " " & BigText(bigRec.s) & " " & BigShiftedText(bigRec.t));
    Line(B(Id(140) IN bigRec.s) & B(Id(139) IN bigRec.s)
           & B(bigRec.s = Big {10 .. 19, 131 .. 140})
           & B(bigArr[0] = Big {Id(0), Id(3)}));
  END BigSets;

(* = and # on arrays of every kind of element. *)

TYPE
  Short = [-30000 .. 30000];
  Mid = [0 .. 100000];
  Byte = [0 .. 255];
  Bools = ARRAY [0 .. 3] OF BOOLEAN;
  Chars = ARRAY [0 .. 3] OF CHAR;
  Wides = ARRAY [0 .. 3] OF WIDECHAR;
  Bytes = ARRAY [0 .. 3] OF Byte;
  Shorts = ARRAY [0 .. 3] OF Short;
  Mids = ARRAY [0 .. 3] OF Mid;
  Ints = ARRAY [0 .. 3] OF INTEGER;
  Longs = ARRAY [0 .. 3] OF LONGINT;
  Reals = ARRAY [0 .. 3] OF REAL;
  LReals = ARRAY [0 .. 3] OF LONGREAL;
  Refs = ARRAY [0 .. 3] OF REFANY;
  Texts = ARRAY [0 .. 3] OF TEXT;
  Colorss = ARRAY [0 .. 3] OF Colors;
  Bigs = ARRAY [0 .. 1] OF Big;
  Grid = ARRAY [0 .. 1], [0 .. 2] OF Mid;
  RGrid = ARRAY [0 .. 1], [0 .. 1] OF REAL;
  Pair = RECORD a: REAL;  b: Mid;  c: CHAR END;
  Pairs = ARRAY [0 .. 2] OF Pair;
  WithArr = RECORD x: LReals;  y: Bools END;
  Tight = RECORD a: REAL;  b: Mid END;
  LTight = RECORD d: LONGREAL;  n: INTEGER END;
  Tights = ARRAY [0 .. 1] OF Tight;
  Obj = OBJECT n: INTEGER END;
  Objs = ARRAY [0 .. 1] OF Obj;

VAR
  zeroR: REAL := 0.0;
  zeroL: LONGREAL := 0.0D0;

PROCEDURE NegZeroR (): REAL =
  BEGIN
    RETURN -zeroR;
  END NegZeroR;

PROCEDURE NegZeroL (): LONGREAL =
  BEGIN
    RETURN -zeroL;
  END NegZeroL;

PROCEDURE NaNR (): REAL =
  BEGIN
    RETURN zeroR / zeroR;
  END NaNR;

PROCEDURE NaNL (): LONGREAL =
  BEGIN
    RETURN zeroL / zeroL;
  END NaNL;

(* The two results of = and # for one pair. *)
PROCEDURE EQ (eq, ne: BOOLEAN): TEXT =
  BEGIN
    RETURN B(eq) & B(ne) & " ";
  END EQ;

PROCEDURE Arrays () =
  VAR
    b1, b2: Bools;
    c1, c2: Chars;
    w1, w2: Wides;
    y1, y2: Bytes;
    s1, s2: Shorts;
    m1, m2: Mids;
    i1, i2: Ints;
    l1, l2: Longs;
    r1, r2: Reals;
    d1, d2: LReals;
    f1, f2: Refs;
    t1, t2: Texts;
    k1, k2: Colorss;
    g1, g2: Bigs;
    x1, x2: Grid;
    q1, q2: RGrid;
    p1, p2: Pairs;
    a1, a2: WithArr;
    o1, o2: Objs;
    h1, h2: Tight;
    j1, j2: LTight;
    z1, z2: Tights;
    t: TEXT;
    shared := NEW(REF INTEGER);
    obj := NEW(Obj, n := 1);
  BEGIN
    Line("-- = and # on fixed arrays");
    FOR i := 0 TO 3 DO
      b1[i] := i MOD 2 = 1;  c1[i] := VAL(ORD('a') + i, CHAR);
      w1[i] := VAL(16_400 + i, WIDECHAR);  y1[i] := 250 + i;
      s1[i] := -29990 + i;  m1[i] := 99990 + i;  i1[i] := -i;
      l1[i] := VAL(i, LONGINT) * 16_100000000L;  r1[i] := FLOAT(i) / 4.0;
      d1[i] := FLOAT(i, LONGREAL) / 3.0D0;  f1[i] := shared;
      t1[i] := "t";  k1[i] := Colors {VAL(i, Color)};
    END;
    g1[0] := Big {Id(7), Id(150)};  g1[1] := Big {};
    b2 := b1;  c2 := c1;  w2 := w1;  y2 := y1;  s2 := s1;  m2 := m1;  i2 := i1;
    l2 := l1;  r2 := r1;  d2 := d1;  f2 := f1;  t2 := t1;  k2 := k1;  g2 := g1;
    t := EQ(b1 = b2, b1 # b2) & EQ(c1 = c2, c1 # c2) & EQ(w1 = w2, w1 # w2)
           & EQ(y1 = y2, y1 # y2) & EQ(s1 = s2, s1 # s2) & EQ(m1 = m2, m1 # m2)
           & EQ(i1 = i2, i1 # i2) & EQ(l1 = l2, l1 # l2) & EQ(r1 = r2, r1 # r2)
           & EQ(d1 = d2, d1 # d2) & EQ(f1 = f2, f1 # f2) & EQ(t1 = t2, t1 # t2)
           & EQ(k1 = k2, k1 # k2) & EQ(g1 = g2, g1 # g2);
    Line("equal     " & t);
    b2[Id(3)] := FALSE;  c2[Id(3)] := 'z';  w2[Id(3)] := W'\x0400';
    y2[Id(3)] := 0;  s2[Id(3)] := 30000;  m2[Id(3)] := 0;  i2[Id(3)] := 1;
    l2[Id(3)] := 1L;  r2[Id(3)] := 9.5;  d2[Id(3)] := 0.5D0;
    f2[Id(3)] := NEW(REF INTEGER);  t2[Id(3)] := NIL;
    k2[Id(3)] := Colors {};  g2[Id(1)] := Big {Id(199)};
    t := EQ(b1 = b2, b1 # b2) & EQ(c1 = c2, c1 # c2) & EQ(w1 = w2, w1 # w2)
           & EQ(y1 = y2, y1 # y2) & EQ(s1 = s2, s1 # s2) & EQ(m1 = m2, m1 # m2)
           & EQ(i1 = i2, i1 # i2) & EQ(l1 = l2, l1 # l2) & EQ(r1 = r2, r1 # r2)
           & EQ(d1 = d2, d1 # d2) & EQ(f1 = f2, f1 # f2) & EQ(t1 = t2, t1 # t2)
           & EQ(k1 = k2, k1 # k2) & EQ(g1 = g2, g1 # g2);
    Line("last      " & t);
    b2 := b1;  c2 := c1;  w2 := w1;  y2 := y1;  s2 := s1;  m2 := m1;  i2 := i1;
    l2 := l1;  r2 := r1;  d2 := d1;  f2 := f1;  k2 := k1;  g2 := g1;
    b2[Id(0)] := TRUE;  c2[Id(0)] := 'A';  w2[Id(0)] := W'a';
    y2[Id(0)] := 1;  s2[Id(0)] := 0;  m2[Id(0)] := 1;  i2[Id(0)] := 5;
    l2[Id(0)] := -1L;  r2[Id(0)] := -1.0;  d2[Id(0)] := 2.0D0;
    f2[Id(0)] := NIL;  k2[Id(0)] := Colors {Color.Violet};
    g2[Id(0)] := Big {Id(7)};
    t := EQ(b1 = b2, b1 # b2) & EQ(c1 = c2, c1 # c2) & EQ(w1 = w2, w1 # w2)
           & EQ(y1 = y2, y1 # y2) & EQ(s1 = s2, s1 # s2) & EQ(m1 = m2, m1 # m2)
           & EQ(i1 = i2, i1 # i2) & EQ(l1 = l2, l1 # l2) & EQ(r1 = r2, r1 # r2)
           & EQ(d1 = d2, d1 # d2) & EQ(f1 = f2, f1 # f2)
           & EQ(k1 = k2, k1 # k2) & EQ(g1 = g2, g1 # g2);
    Line("first     " & t);

    Line("-- REAL and LONGREAL compare as their bits");
    r2 := r1;  d2 := d1;
    r1[0] := zeroR;  r2[0] := NegZeroR();
    d1[0] := zeroL;  d2[0] := NegZeroL();
    Line("0 and -0  " & EQ(r1 = r2, r1 # r2) & EQ(d1 = d2, d1 # d2)
           & EQ(r1[0] = r2[0], r1[0] # r2[0]) & EQ(d1[0] = d2[0], d1[0] # d2[0])
           & EQ(SUBARRAY(r1, 0, 2) = SUBARRAY(r2, 0, 2), FALSE)
           & EQ(SUBARRAY(d1, Id(0), 3) = SUBARRAY(d2, Id(0), 3), FALSE)
           & EQ(r1 = SUBARRAY(r2, 0, 4), d1 = SUBARRAY(d2, 0, 4)));
    r1[0] := NaNR();  r2[0] := r1[0];
    d1[0] := NaNL();  d2[0] := d1[0];
    Line("NaN       " & EQ(r1 = r2, r1 # r2) & EQ(d1 = d2, d1 # d2)
           & EQ(r1[0] = r2[0], r1[0] # r2[0]) & EQ(d1[0] = d2[0], d1[0] # d2[0])
           & EQ(SUBARRAY(r1, 0, 2) = SUBARRAY(r2, 0, 2), FALSE)
           & EQ(SUBARRAY(d1, Id(0), 3) = SUBARRAY(d2, Id(0), 3), FALSE)
           & EQ(r1 = SUBARRAY(r2, 0, 4), d1 = SUBARRAY(d2, 0, 4)));

    Line("-- two dimensions, records, objects");
    FOR i := 0 TO 1 DO
      FOR j := 0 TO 2 DO x1[i, j] := 100000 - i * 3 - j END;
      FOR j := 0 TO 1 DO q1[i, j] := FLOAT(i + j) END;
    END;
    x2 := x1;  q2 := q1;
    t := EQ(x1 = x2, x1 # x2) & EQ(q1 = q2, q1 # q2);
    x2[Id(1), Id(2)] := 0;  q2[Id(1), Id(0)] := NegZeroR();
    q1[Id(1), Id(0)] := zeroR;
    t := t & EQ(x1 = x2, x1 # x2) & EQ(q1 = q2, q1 # q2)
           & EQ(x1[0] = x2[0], x1[0] # x2[0]) & EQ(x1[1] = x2[1], x1[1] # x2[1]);
    Line("grids     " & t);
    FOR i := 0 TO 2 DO
      p1[i] := Pair {FLOAT(i), 99999 + i MOD 2, VAL(ORD('x') + i, CHAR)};
    END;
    p2 := p1;
    t := EQ(p1 = p2, p1 # p2);
    p2[Id(2)].c := 'q';
    t := t & EQ(p1 = p2, p1 # p2) & EQ(p1[1] = p2[1], p1[1] # p2[1]);
    p2[Id(2)] := p1[2];  p2[Id(0)].a := NegZeroR();
    t := t & EQ(p1 = p2, p1 # p2) & EQ(p1[0] = p2[0], p1[0] # p2[0]);
    Line("records   " & t);
    h1 := Tight {zeroR, 5};  h2 := Tight {NegZeroR(), 5};
    j1 := LTight {zeroL, 6};  j2 := LTight {NegZeroL(), 6};
    z1[0] := h1;  z1[1] := h1;  z2 := z1;  z2[1] := h2;
    t := EQ(h1 = h2, h1 # h2) & EQ(j1 = j2, j1 # j2) & EQ(z1 = z2, z1 # z2);
    h1.a := NaNR();  h2 := h1;  j1.d := NaNL();  j2 := j1;
    z1[1] := h1;  z2 := z1;
    t := t & EQ(h1 = h2, h1 # h2) & EQ(j1 = j2, j1 # j2) & EQ(z1 = z2, z1 # z2);
    Line("tight     " & t);
    a1.x := d1;  a1.y := b1;  a2 := a1;
    t := EQ(a1 = a2, a1 # a2);
    a2.y[Id(2)] := NOT a2.y[2];
    t := t & EQ(a1 = a2, a1 # a2);
    a2.y := a1.y;  a2.x[Id(3)] := 7.0D0;
    t := t & EQ(a1 = a2, a1 # a2);
    Line("nested    " & t);
    o1[0] := obj;  o1[1] := NIL;  o2 := o1;
    t := EQ(o1 = o2, o1 # o2);
    o2[Id(1)] := NEW(Obj, n := 1);
    t := t & EQ(o1 = o2, o1 # o2);
    o2[Id(1)] := NIL;  o2[Id(0)] := NEW(Obj, n := 1);
    t := t & EQ(o1 = o2, o1 # o2);
    Line("objects   " & t);
  END Arrays;

PROCEDURE Open () =
  TYPE
    OB = REF ARRAY OF BOOLEAN;
    OC = REF ARRAY OF CHAR;
    OW = REF ARRAY OF WIDECHAR;
    OM = REF ARRAY OF Mid;
    OF4 = REF ARRAY OF REAL;
    OL = REF ARRAY OF LONGREAL;
    OX = REF ARRAY OF REFANY;
    OP = REF ARRAY OF Pair;
    O2 = REF ARRAY OF ARRAY OF Mid;
  VAR
    b1 := NEW(OB, Id(5));  b2 := NEW(OB, Id(5));  b3 := NEW(OB, Id(4));
    c1 := NEW(OC, Id(5));  c2 := NEW(OC, Id(5));
    w1 := NEW(OW, Id(5));  w2 := NEW(OW, Id(5));
    m1 := NEW(OM, Id(5));  m2 := NEW(OM, Id(5));
    r1 := NEW(OF4, Id(5));  r2 := NEW(OF4, Id(5));
    l1 := NEW(OL, Id(5));  l2 := NEW(OL, Id(5));
    x1 := NEW(OX, Id(5));  x2 := NEW(OX, Id(5));
    p1 := NEW(OP, Id(3));  p2 := NEW(OP, Id(3));
    g1 := NEW(O2, Id(2), Id(3));  g2 := NEW(O2, Id(2), Id(3));
    g3 := NEW(O2, Id(3), Id(2));
    shared := NEW(REF INTEGER);
    t: TEXT;
    fixed: ARRAY [0 .. 4] OF Mid;
  BEGIN
    Line("-- open arrays and SUBARRAY");
    FOR i := 0 TO 4 DO
      b1[i] := i = 2;  c1[i] := VAL(ORD('k') + i, CHAR);
      w1[i] := VAL(16_3B1 + i, WIDECHAR);  m1[i] := 100000 - i;
      r1[i] := FLOAT(i) * 0.5;  l1[i] := FLOAT(i, LONGREAL) * 0.25D0;
      x1[i] := shared;  fixed[i] := m1[i];
    END;
    FOR i := 0 TO 2 DO p1[i] := Pair {1.5, i, 'p'} END;
    b2^ := b1^;  c2^ := c1^;  w2^ := w1^;  m2^ := m1^;  r2^ := r1^;
    l2^ := l1^;  x2^ := x1^;  p2^ := p1^;
    t := EQ(b1^ = b2^, b1^ # b2^) & EQ(c1^ = c2^, c1^ # c2^)
           & EQ(w1^ = w2^, w1^ # w2^) & EQ(m1^ = m2^, m1^ # m2^)
           & EQ(r1^ = r2^, r1^ # r2^) & EQ(l1^ = l2^, l1^ # l2^)
           & EQ(x1^ = x2^, x1^ # x2^) & EQ(p1^ = p2^, p1^ # p2^)
           & EQ(m1^ = fixed, m1^ # fixed);
    Line("equal     " & t);
    b2[Id(4)] := TRUE;  c2[Id(4)] := 'K';  w2[Id(4)] := W'k';
    m2[Id(4)] := 3;  r2[Id(4)] := NegZeroR();  l2[Id(4)] := -1.0D0;
    x2[Id(4)] := NIL;  p2[Id(2)].b := 7;  fixed[Id(0)] := 0;
    t := EQ(b1^ = b2^, b1^ # b2^) & EQ(c1^ = c2^, c1^ # c2^)
           & EQ(w1^ = w2^, w1^ # w2^) & EQ(m1^ = m2^, m1^ # m2^)
           & EQ(r1^ = r2^, r1^ # r2^) & EQ(l1^ = l2^, l1^ # l2^)
           & EQ(x1^ = x2^, x1^ # x2^) & EQ(p1^ = p2^, p1^ # p2^)
           & EQ(m1^ = fixed, m1^ # fixed);
    Line("last      " & t);
    t := EQ(b1^ = b3^, b1^ # b3^)
           & EQ(SUBARRAY(b1^, 0, 4) = b3^, SUBARRAY(b1^, 0, 4) # b3^)
           & EQ(SUBARRAY(b1^, 1, 4) = SUBARRAY(b2^, 0, 4), FALSE)
           & EQ(SUBARRAY(c1^, Id(0), 4) = SUBARRAY(c2^, Id(0), 4), FALSE)
           & EQ(SUBARRAY(w1^, Id(1), 3) = SUBARRAY(w2^, Id(1), 3), FALSE)
           & EQ(SUBARRAY(m1^, Id(0), 4) = SUBARRAY(m2^, Id(0), 4), FALSE)
           & EQ(SUBARRAY(r1^, Id(2), 2) = SUBARRAY(r2^, Id(2), 2),
                SUBARRAY(r1^, Id(2), 3) = SUBARRAY(r2^, Id(2), 3))
           & EQ(SUBARRAY(l1^, Id(1), 3) = SUBARRAY(l2^, Id(1), 3), FALSE)
           & EQ(SUBARRAY(x1^, Id(0), 4) = SUBARRAY(x2^, Id(1), 4), FALSE)
           & EQ(SUBARRAY(p1^, Id(0), 2) = SUBARRAY(p2^, Id(0), 2), FALSE)
           & EQ(SUBARRAY(m1^, Id(1), 4) = SUBARRAY(fixed, Id(1), 4), FALSE);
    Line("subarrays " & t);
    r2^ := r1^;  l2^ := l1^;
    r1[Id(0)] := zeroR;  r2[Id(0)] := NegZeroR();
    l1[Id(0)] := zeroL;  l2[Id(0)] := NegZeroL();
    t := EQ(r1^ = r2^, r1^ # r2^) & EQ(l1^ = l2^, l1^ # l2^);
    r1[Id(0)] := NaNR();  r2[Id(0)] := r1[0];
    l1[Id(0)] := NaNL();  l2[Id(0)] := l1[0];
    t := t & EQ(r1^ = r2^, r1^ # r2^) & EQ(l1^ = l2^, l1^ # l2^)
           & EQ(SUBARRAY(r1^, 1, 4) = SUBARRAY(r2^, 1, 4), FALSE);
    p2^ := p1^;  p2[Id(1)].a := NegZeroR();  p1[Id(1)].a := zeroR;
    t := t & EQ(p1^ = p2^, p1^ # p2^);
    Line("numbers   " & t);
    FOR i := 0 TO 1 DO
      FOR j := 0 TO 2 DO g1[i, j] := i * 10 + j;  g3[j, i] := i * 10 + j END;
    END;
    g2^ := g1^;
    t := EQ(g1^ = g2^, g1^ # g2^) & EQ(g1^ = g3^, g1^ # g3^)
           & EQ(g1[1] = g2[1], g1[1] # g2[1]);
    g2[Id(1), Id(1)] := 0;
    t := t & EQ(g1^ = g2^, g1^ # g2^) & EQ(g1[0] = g2[0], g1[0] # g2[0])
           & EQ(g1[1] = g2[1], g1[1] # g2[1]);
    Line("grids     " & t);
  END Open;

BEGIN
  IF Params.Count > 1 THEN
    HandChecked();
  ELSE
    SmallSets();
    BigSets();
    Arrays();
    Open();
  END;
END Main.
