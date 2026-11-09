(* Errors of CM3's front end that m3j's front end had too, fixed in m3j
   (cm3 cannot compile this program; its output is checked by hand):

   - WITH binds a name to a packed field or element that need not start
     at a byte: fields of packed records (also through a REF, nested and
     spanning bytes, a packed record itself), packed array elements at a
     constant index; also from a nested procedure and a nested WITH.
   - RETURN of an array constructor that a TRY EXCEPT ELSE absorbs.
   - Operations on constant small sets, folded by the front end, passed to
     a READONLY formal and, with negative elements, to a value formal.
   - Word.Divide and Long.Divide folded to a value beyond LAST(INTEGER).
   - BITSIZE, BYTESIZE and ADRSIZE of a type of size zero under EVAL. *)

MODULE Main;

IMPORT IO, Fmt, Word, Long;

(* ---------------------------------------------------- WITH and packing *)

TYPE
  Color = {Red, Green, Blue};
  In = BITS 9 FOR RECORD a: BITS 4 FOR [0..15]; b: BITS 5 FOR [0..31] END;
  P = BITS 64 FOR RECORD
    x: BITS 3 FOR [0..7];
    y: BITS 5 FOR [0..31];
    s: BITS 6 FOR [0..63];
    c: BITS 2 FOR Color;
    f: BITS 1 FOR BOOLEAN;
    n: BITS 9 FOR In;        (* bits 17..25 *)
    z: BITS 38 FOR [0..16_3FFFFFFFFF];
  END;
  A = ARRAY [0..9] OF BITS 3 FOR [0..7];
  RP = REF P;

VAR
  gp: P;
  pa: A;

PROCEDURE Show (READONLY p: P) =
  BEGIN
    IO.Put(Fmt.Int(p.x) & " " & Fmt.Int(p.y) & " " & Fmt.Int(p.s) & " "
           & Fmt.Int(ORD(p.c)) & " " & Fmt.Bool(p.f) & " " & Fmt.Int(p.n.a)
           & " " & Fmt.Int(p.n.b) & " " & Fmt.Int(p.z) & "\n");
  END Show;

PROCEDURE Value (n: [0..31]) =
  BEGIN
    IO.Put("value " & Fmt.Int(n) & "\n");
  END Value;

PROCEDURE ReadOnly (READONLY n: [0..31]) =
  BEGIN
    IO.Put("readonly " & Fmt.Int(n) & "\n");
  END ReadOnly;

PROCEDURE Local () =
  VAR lp: P;
  BEGIN
    lp := gp;
    WITH w = lp.s DO w := 63 - w END;
    WITH c = lp.c, f = lp.f DO c := Color.Blue; f := NOT f END;
    Show(lp);
  END Local;

PROCEDURE Nested () =
  PROCEDURE Inc () = BEGIN INC(gp.y) END Inc;
  BEGIN
    WITH w = gp.y DO
      PROCEDURE Bump () = BEGIN INC(w, 2) END Bump;
      BEGIN
        Bump();
        Inc();
        IO.Put("nested " & Fmt.Int(w) & "\n");
        WITH u = w DO u := u + 10 END;
        Value(w);
        ReadOnly(w);
      END;
    END;
  END Nested;

PROCEDURE Ref () =
  VAR r := NEW(RP);
  BEGIN
    r^ := gp;
    WITH w = r.n, v = r^.y DO
      w.a := 9;
      w.b := 30;
      v := 1;
      IO.Put("in " & Fmt.Int(w.a) & " " & Fmt.Int(w.b) & "\n");
    END;
    Show(r^);
  END Ref;

PROCEDURE Packing () =
  BEGIN
    gp.x := 5; gp.y := 17; gp.s := 45; gp.c := Color.Green; gp.f := TRUE;
    gp.n.a := 3; gp.n.b := 21; gp.z := 16_123456789;
    Show(gp);
    WITH w = gp.y DO
      IO.Put(Fmt.Int(w) & "\n");
      w := 30;
      INC(w);
      IO.Put(Fmt.Int(w * 2) & "\n");
    END;
    Show(gp);
    WITH w = gp.s, x = gp.x DO w := w + x; x := 0 END;
    Show(gp);
    WITH w = gp.n DO
      WITH b = w.b DO b := b - 1 END;
      w.a := w.a + 1;
    END;
    Show(gp);
    WITH w = gp.z DO w := w + 1 END;
    Show(gp);
    Local();
    gp.y := 3;
    Nested();
    Show(gp);
    Ref();
    FOR i := 0 TO 9 DO pa[i] := i MOD 8 END;
    WITH e = pa[2], d = pa[7] DO e := 7; d := e - 1 END;
    FOR i := 0 TO 9 DO IO.Put(Fmt.Int(pa[i])) END;
    IO.Put("\n");
  END Packing;

(* ------------------------------------------------ RETURN in TRY ELSE *)

TYPE
  Arr2 = ARRAY [0..1] OF INTEGER;
  Big = ARRAY [0..9] OF INTEGER;
  Rec = RECORD a: Arr2; n: INTEGER END;

VAR g := 7;

PROCEDURE Side (x: INTEGER): INTEGER =
  BEGIN
    IO.Put("side " & Fmt.Int(x) & "\n");
    RETURN x;
  END Side;

PROCEDURE F (): Arr2 =
  BEGIN
    TRY RETURN Arr2{1, Side(g)} EXCEPT ELSE IO.Put("caught\n") END;
    RETURN Arr2{3, 4};
  END F;

PROCEDURE G (): Big =
  BEGIN
    TRY RETURN Big{Side(1), ..} EXCEPT ELSE IO.Put("caught\n") END;
    RETURN Big{5, ..};
  END G;

PROCEDURE H (): Rec =
  BEGIN
    TRY RETURN Rec{Arr2{Side(2), g}, 3} EXCEPT ELSE IO.Put("caught\n") END;
    RETURN Rec{Arr2{8, 9}, 10};
  END H;

PROCEDURE K (): Arr2 =
  BEGIN
    TRY
      TRY RETURN Arr2{Side(3), g} FINALLY IO.Put("finally\n") END;
    EXCEPT ELSE
      IO.Put("caught\n");
    END;
    RETURN Arr2{11, 12};
  END K;

PROCEDURE L (b: BOOLEAN): Arr2 =
  BEGIN
    TRY
      IF b THEN RETURN Arr2{Side(4), g} END;
    EXCEPT ELSE
      IO.Put("caught\n");
    END;
    RETURN Arr2{13, 14};
  END L;

PROCEDURE Returns () =
  VAR a: Arr2; b: Big; r: Rec;
  BEGIN
    a := F(); IO.Put(Fmt.Int(a[0]) & " " & Fmt.Int(a[1]) & "\n");
    b := G(); IO.Put(Fmt.Int(b[0]) & " " & Fmt.Int(b[9]) & "\n");
    r := H();
    IO.Put(Fmt.Int(r.a[0]) & " " & Fmt.Int(r.a[1]) & " " & Fmt.Int(r.n) & "\n");
    a := K(); IO.Put(Fmt.Int(a[0]) & " " & Fmt.Int(a[1]) & "\n");
    a := L(TRUE); IO.Put(Fmt.Int(a[0]) & " " & Fmt.Int(a[1]) & "\n");
    a := L(FALSE); IO.Put(Fmt.Int(a[0]) & " " & Fmt.Int(a[1]) & "\n");
  END Returns;

(* ------------------------------------------------ folded small sets *)

TYPE
  SS = SET OF [-5..20];
  E = {a, b, c, d, e};
  ES = SET OF [E.b..E.e];

PROCEDURE PutSet (s: SS) =
  BEGIN
    FOR i := -5 TO 20 DO
      IF i IN s THEN IO.Put(Fmt.Int(i) & " ") END;
    END;
    IO.Put("\n");
  END PutSet;

PROCEDURE PutSetRO (READONLY s: SS) =
  BEGIN
    IO.Put("ro ");
    PutSet(s);
  END PutSetRO;

PROCEDURE PutES (s: ES) =
  BEGIN
    FOR i := E.b TO E.e DO
      IF i IN s THEN IO.Put(Fmt.Int(ORD(i)) & " ") END;
    END;
    IO.Put("\n");
  END PutES;

PROCEDURE PutESRO (READONLY s: ES) =
  BEGIN
    IO.Put("ro ");
    PutES(s);
  END PutESRO;

PROCEDURE R (): SS =
  BEGIN
    RETURN SS{-3, 4} + SS{-1, 20};
  END R;

PROCEDURE Sets () =
  VAR v: SS;
  BEGIN
    PutSet(SS{-3, 4} + SS{-1});
    PutSet(SS{-5..-2} - SS{-4});
    PutSet(SS{-5, 20} * SS{-5, 3, 20});
    PutSet(SS{-5, 1} / SS{1, 2});
    PutSet((SS{-3} + SS{-2}) + SS{-1});
    v := SS{-3, 4} + SS{-1};
    PutSet(v);
    PutSet(R());
    WITH w = SS{-3} + SS{-4} DO PutSet(w) END;
    IO.Put(Fmt.Bool(-3 IN SS{-3} + SS{-4}) & "\n");
    PutSetRO(SS{-3, 4} + SS{-1});
    PutSetRO(SS{1} + SS{2});
    PutSetRO(SS{1} / SS{});
    PutES(ES{E.c} + ES{E.e});
    PutES(ES{E.b..E.e} - ES{E.c});
    PutESRO(ES{E.c} + ES{E.d});
  END Sets;

(* ------------------------------------------------ folded Word.Divide *)

CONST
  WD1 = Word.Divide(-1, 3);
  WD2 = Word.Divide(-1, 1);
  WD3 = Word.Divide(-2, 2);
  WM = Word.Mod(-1, 7);
  LD1 = Long.Divide(-1L, 3L);
  LD2 = Long.Divide(-1L, 1L);

PROCEDURE Divides () =
  BEGIN
    IO.Put(Fmt.Unsigned(WD1, 10) & " " & Fmt.Unsigned(WD2, 10) & " "
           & Fmt.Unsigned(WD3, 10) & " " & Fmt.Int(WM) & "\n");
    IO.Put(Fmt.LongUnsigned(LD1, 10) & " " & Fmt.LongUnsigned(LD2, 10) & "\n");
  END Divides;

(* ------------------------------------------------ types of size zero *)

TYPE
  Empty = RECORD END;
  NoElements = ARRAY [1 .. 0] OF INTEGER;

PROCEDURE Sizes () =
  VAR n: INTEGER;
  BEGIN
    EVAL BITSIZE(Empty);
    EVAL BYTESIZE(Empty);
    EVAL ADRSIZE(Empty);
    EVAL BYTESIZE(NoElements);
    n := BYTESIZE(Empty);
    IO.Put(Fmt.Int(n) & " " & Fmt.Int(BITSIZE(NoElements)) & " "
           & Fmt.Int(BYTESIZE(INTEGER)) & "\n");
  END Sizes;

BEGIN
  Packing();
  Returns();
  Sets();
  Divides();
  Sizes();
END Main.
