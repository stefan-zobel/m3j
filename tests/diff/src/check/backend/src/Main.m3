(* Paths of the code generation that the other tests do not reach:
   arrays and references of scalars of every size (16-bit subranges,
   BOOLEAN, REAL, LONGREAL), array elements of those types passed VAR,
   variables of a procedure that a nested procedure changes (also value
   parameters passed VAR), open arrays of records passed by value, default values
   of fields that are not literals, BITSIZE and BYTESIZE of open arrays,
   ROOT in type tests, NEW with field values, SUBARRAY of open arrays of
   two dimensions and of records, comparisons of procedure values, sets
   of more than 64 elements, and exceptions with arguments of many kinds
   (not REAL and LONGREAL: cm3's C back end cannot compile them). *)

MODULE Main;

IMPORT IO, Fmt;

TYPE
  Small = [-100 .. 100];
  Byte = [0 .. 200];
  Short = [-30000 .. 30000];
  UShort = [0 .. 60000];
  Mid = [0 .. 100000];
  Arr3 = ARRAY [0 .. 2] OF INTEGER;
  Digits = SET OF [0 .. 9];
  R = RECORD a, b: INTEGER END;
  Big = SET OF [0 .. 199];
  WithDefaults = RECORD
    arr := Arr3 {1, 2, 3};
    set := Digits {1, 3, 5};
    rec := R {7, 8};
    txt := "con" & "cat";
    sh: Short := -12345;
  END;
  ObjDefaults = OBJECT
    arr := Arr3 {4, 5, 6};
    set := Digits {0, 9};
    rec := R {9, 10};
    big := Big {3, 150, 199};
    us: UShort := 59999;
  END;
  RA = RECORD n: INTEGER;  arr: Arr3;  s: Digits END;

EXCEPTION
  EI (INTEGER);  EL (LONGINT);  EB (BOOLEAN);
  EC (CHAR);  ES (Short);  ET (TEXT);  EA (Arr3);  ERec (R);

VAR
  shorts: ARRAY [0 .. 5] OF Short;
  ushorts: ARRAY [0 .. 5] OF UShort;
  bools: ARRAY [0 .. 3] OF BOOLEAN;
  reals: ARRAY [0 .. 3] OF REAL;
  longs: ARRAY [0 .. 3] OF LONGREAL;
  smalls: ARRAY [0 .. 3] OF Small;
  bytes: ARRAY [0 .. 3] OF Byte;
  mids: ARRAY [0 .. 3] OF Mid;
  ints: ARRAY [0 .. 3] OF INTEGER;
  chars: ARRAY [0 .. 3] OF CHAR;
  wides: ARRAY [0 .. 3] OF WIDECHAR;
  texts: ARRAY [0 .. 3] OF TEXT;

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
    RETURN Fmt.Bool(b);
  END B;

PROCEDURE SetText (READONLY s: Big): TEXT =
  VAR t := "{";
  BEGIN
    FOR i := 0 TO 199 DO
      IF i IN s THEN t := t & " " & I(i) END;
    END;
    RETURN t & " }";
  END SetText;

PROCEDURE DigitsText (s: Digits): TEXT =
  VAR t := "{";
  BEGIN
    FOR i := 0 TO 9 DO
      IF i IN s THEN t := t & " " & I(i) END;
    END;
    RETURN t & " }";
  END DigitsText;

(* VAR parameters of every size *)

PROCEDURE IncShort (VAR x: Short; d: INTEGER) = BEGIN INC(x, d) END IncShort;
PROCEDURE IncUShort (VAR x: UShort; d: INTEGER) = BEGIN INC(x, d) END IncUShort;
PROCEDURE IncSmall (VAR x: Small) = BEGIN INC(x) END IncSmall;
PROCEDURE IncByte (VAR x: Byte) = BEGIN INC(x, 2) END IncByte;
PROCEDURE IncMid (VAR x: Mid) = BEGIN INC(x, 50000) END IncMid;
PROCEDURE IncInt (VAR x: INTEGER) = BEGIN DEC(x, 3) END IncInt;
PROCEDURE Flip (VAR x: BOOLEAN) = BEGIN x := NOT x END Flip;
PROCEDURE Half (VAR x: REAL) = BEGIN x := x / 2.0E0 END Half;
PROCEDURE Third (VAR x: LONGREAL) = BEGIN x := x / 3.0D0 END Third;
PROCEDURE NextChar (VAR x: CHAR) = BEGIN x := VAL(ORD(x) + 1, CHAR) END NextChar;
PROCEDURE NextWide (VAR x: WIDECHAR) = BEGIN x := VAL(ORD(x) + 256, WIDECHAR) END NextWide;
PROCEDURE Append (VAR x: TEXT) = BEGIN x := x & "!" END Append;

PROCEDURE Arrays () =
  VAR
    os := NEW(REF ARRAY OF Short, 4);
    ou := NEW(REF ARRAY OF UShort, 4);
    copy: ARRAY [0 .. 5] OF Short;
  BEGIN
    FOR k := 0 TO 5 DO
      shorts[k] := k * 9000 - 25000;
      ushorts[k] := k * 11000 + 3;
    END;
    copy := shorts;
    Line("shorts equal " & B(copy = shorts));
    copy[2] := -1;
    Line("shorts equal " & B(copy = shorts));
    FOR k := 0 TO 3 DO
      os[k] := shorts[k + 1];
      ou[k] := ushorts[k + 2];
    END;
    SUBARRAY(shorts, 0, 2) := SUBARRAY(os^, 2, 2);
    ushorts[0] := ou[3];
    FOR k := 0 TO 5 DO
      IncShort(shorts[k], 7);
      IncUShort(ushorts[k], 5);
      Line(I(k) & " " & I(shorts[k]) & " " & I(ushorts[k]) & " " & I(copy[k]));
    END;
    FOR k := 0 TO 3 DO
      IncShort(os[k], -1);
      IncUShort(ou[k], 1);
      Line("open " & I(os[k]) & " " & I(ou[k]));
    END;
  END Arrays;

PROCEDURE VarElements () =
  BEGIN
    FOR k := 0 TO 3 DO
      bools[k] := k MOD 2 = 1;
      reals[k] := FLOAT(k, REAL) + 0.5E0;
      longs[k] := FLOAT(k, LONGREAL) + 1.0D0;
      smalls[k] := k * 20 - 30;
      bytes[k] := k * 50;
      mids[k] := k * 1000;
      ints[k] := k * k;
      chars[k] := VAL(ORD('a') + k, CHAR);
      wides[k] := VAL(1000 + k, WIDECHAR);
      texts[k] := "t" & I(k);
    END;
    FOR k := 0 TO 3 DO
      Flip(bools[k]);
      Half(reals[k]);
      Third(longs[k]);
      IncSmall(smalls[k]);
      IncByte(bytes[k]);
      IncMid(mids[k]);
      IncInt(ints[k]);
      NextChar(chars[k]);
      NextWide(wides[k]);
      Append(texts[k]);
      Line(I(k) & " " & B(bools[k]) & " " & Fmt.Real(reals[k]) & " " & Fmt.LongReal(longs[k]) & " "
           & I(smalls[k]) & " " & I(bytes[k]) & " " & I(mids[k]) & " " & I(ints[k]) & " "
           & Fmt.Char(chars[k]) & " " & I(ORD(wides[k])) & " " & texts[k]);
    END;
  END VarElements;

PROCEDURE Refs () =
  VAR
    rb := NEW(REF BOOLEAN);
    rr := NEW(REF REAL);
    rl := NEW(REF LONGREAL);
    rs := NEW(REF Short);
    ru := NEW(REF UShort);
    rc := NEW(REF CHAR);
    rw := NEW(REF WIDECHAR);
    rm := NEW(REF Mid);
    any: REFANY;
  BEGIN
    rb^ := TRUE;
    rr^ := 1.25E0;
    rl^ := 2.5D0;
    rs^ := -29999;
    ru^ := 59999;
    rc^ := 'q';
    rw^ := VAL(40000, WIDECHAR);
    rm^ := 9999;
    Flip(rb^);
    Half(rr^);
    Third(rl^);
    IncShort(rs^, 1);
    IncUShort(ru^, 1);
    NextChar(rc^);
    NextWide(rw^);
    IncMid(rm^);
    Line(B(rb^) & " " & Fmt.Real(rr^) & " " & Fmt.LongReal(rl^) & " " & I(rs^) & " " & I(ru^) & " "
         & Fmt.Char(rc^) & " " & I(ORD(rw^)) & " " & I(rm^));
    any := rr;
    TYPECASE any OF
    | REF REAL (x) => Line("REF REAL " & Fmt.Real(x^));
    ELSE Line("other");
    END;
    any := rs;
    TYPECASE any OF
    | REF UShort => Line("REF UShort");
    | REF Short (x) => Line("REF Short " & I(x^));
    ELSE Line("other");
    END;
  END Refs;

PROCEDURE Captured () =
  VAR b := FALSE; r := 1.0E0; l := 1.0D0; s: Short := 5; u: UShort := 6; c := 'a'; m: Mid := 7;
  PROCEDURE Change () =
    BEGIN
      b := NOT b;
      r := r * 3.0E0;
      l := l - 0.25D0;
      INC(s, 1000);
      INC(u, 2000);
      INC(c);
      INC(m, 3000);
    END Change;
  PROCEDURE Pass () =
    BEGIN
      Flip(b);
      Half(r);
      Third(l);
      IncShort(s, -1);
      IncUShort(u, 1);
      NextChar(c);
      IncMid(m);
    END Pass;
  BEGIN
    Change();
    Pass();
    Change();
    Line(B(b) & " " & Fmt.Real(r) & " " & Fmt.LongReal(l) & " " & I(s) & " " & I(u) & " "
         & Fmt.Char(c) & " " & I(m));
  END Captured;

PROCEDURE CapturedVar (VAR s: Short; VAR r: REAL; VAR b: BOOLEAN) =
  PROCEDURE Change () =
    BEGIN
      INC(s, 3);
      r := r + 1.0E0;
      b := NOT b;
    END Change;
  BEGIN
    Change();
    Change();
  END CapturedVar;

PROCEDURE IncLong (VAR x: LONGINT) = BEGIN INC(x, 5L) END IncLong;

(* value parameters that a nested procedure uses and that are passed VAR:
   they move from the parameters into cells in the frame *)
PROCEDURE CapturedValue (b: BOOLEAN; r: REAL; l: LONGREAL; s: Short; c: CHAR; w: WIDECHAR;
                         n: INTEGER; x: LONGINT) =
  PROCEDURE Change () =
    BEGIN
      b := NOT b;
      r := r + 1.0E0;
      l := l * 2.0D0;
      INC(s, 100);
      INC(c);
      w := VAL(ORD(w) + 1, WIDECHAR);
      INC(n, 10);
      INC(x, 1000L);
    END Change;
  BEGIN
    Change();
    Flip(b);
    Half(r);
    Third(l);
    IncShort(s, -7);
    NextChar(c);
    NextWide(w);
    IncInt(n);
    IncLong(x);
    Change();
    Line(B(b) & " " & Fmt.Real(r) & " " & Fmt.LongReal(l) & " " & I(s) & " " & Fmt.Char(c) & " "
         & I(ORD(w)) & " " & I(n) & " " & Fmt.LongInt(x));
  END CapturedValue;

(* open arrays of records passed by value: the callee changes its copy *)
PROCEDURE ValueRecords (a: ARRAY OF R; b: ARRAY OF ARRAY OF R): INTEGER =
  VAR sum := 0;
  BEGIN
    FOR k := 0 TO LAST(a) DO
      INC(a[k].a, 100);
      a[k].b := -a[k].b;
      INC(sum, a[k].a + a[k].b);
    END;
    FOR i := 0 TO LAST(b) DO
      FOR j := 0 TO LAST(b[0]) DO
        b[i, j] := R {i, j};
        INC(sum, b[i, j].a * 10 + b[i, j].b);
      END;
    END;
    RETURN sum;
  END ValueRecords;

PROCEDURE OpenRecords () =
  VAR
    a := NEW(REF ARRAY OF R, 4);
    g := NEW(REF ARRAY OF ARRAY OF R, 2, 3);
    t := "";
  BEGIN
    FOR k := 0 TO 3 DO a[k] := R {k, k * 2} END;
    FOR i := 0 TO 1 DO
      FOR j := 0 TO 2 DO g[i, j] := R {7, 7} END;
    END;
    Line("sum " & I(ValueRecords(a^, g^)) & " " & I(ValueRecords(SUBARRAY(a^, 1, 2), g^)));
    FOR k := 0 TO 3 DO t := t & " " & I(a[k].a) & "," & I(a[k].b) END;
    FOR i := 0 TO 1 DO
      FOR j := 0 TO 2 DO t := t & " " & I(g[i, j].a) END;
    END;
    Line("unchanged" & t);
  END OpenRecords;

PROCEDURE Defaults () =
  VAR w: WithDefaults; o := NEW(ObjDefaults); ow := NEW(ObjDefaults, us := 1);
  BEGIN
    Line(I(w.arr[0]) & I(w.arr[1]) & I(w.arr[2]) & " " & DigitsText(w.set) & " " & I(w.rec.a) & " "
         & I(w.rec.b) & " " & w.txt & " " & I(w.sh));
    Line(I(o.arr[0]) & I(o.arr[1]) & I(o.arr[2]) & " " & DigitsText(o.set) & " " & I(o.rec.a) & " "
         & I(o.rec.b) & " " & SetText(o.big) & " " & I(o.us) & " " & I(ow.us));
    o.arr[1] := 0;
    Line(I(o.arr[1]) & " " & I(NEW(ObjDefaults).arr[1]));
  END Defaults;

PROCEDURE Sizes (READONLY a: ARRAY OF CHAR; READONLY b: ARRAY OF INTEGER;
                 READONLY c: ARRAY OF ARRAY OF BOOLEAN; READONLY d: ARRAY OF Short) =
  BEGIN
    Line(I(BITSIZE(a)) & " " & I(BYTESIZE(a)) & " " & I(ADRSIZE(a)) & " " & I(BITSIZE(b)) & " "
         & I(BYTESIZE(b)) & " " & I(BITSIZE(c)) & " " & I(BYTESIZE(c)) & " " & I(BITSIZE(d)) & " "
         & I(BYTESIZE(d)));
  END Sizes;

PROCEDURE Roots () =
  TYPE O = OBJECT n := 1 END;
  VAR xs: ARRAY [0 .. 4] OF REFANY;
  BEGIN
    xs[0] := NEW(O);
    xs[1] := "text";
    xs[2] := NEW(REF INTEGER);
    xs[3] := NIL;
    xs[4] := NEW(REF ARRAY OF CHAR, 2);
    FOR k := FIRST(xs) TO LAST(xs) DO
      VAR t := I(k);
      BEGIN
        TYPECASE xs[k] OF
        | NULL => t := t & " NULL";
        | ROOT => t := t & " ROOT";
        ELSE t := t & " ELSE";
        END;
        t := t & " " & B(ISTYPE(xs[k], ROOT)) & " " & B(ISTYPE(xs[k], TEXT)) & " "
               & B(ISTYPE(xs[k], REF INTEGER));
        Line(t);
      END;
    END;
  END Roots;

PROCEDURE NewWithValues () =
  (* an array constructor as the value of a field is rejected by the front
     end (cm3's as well), so these are variables *)
  CONST c789 = Arr3 {7, 8, 9};
  VAR
    a111 := Arr3 {1, 1, 1};
    r := NEW(REF RA, n := 3, arr := c789, s := Digits {2, 4});
    q := NEW(REF RA, arr := a111);
  BEGIN
    Line(I(r.n) & " " & I(r.arr[0]) & I(r.arr[1]) & I(r.arr[2]) & " " & DigitsText(r.s) & " "
         & I(q.n) & " " & I(q.arr[2]) & " " & DigitsText(q.s));
  END NewWithValues;

PROCEDURE SubArrays () =
  VAR
    m := NEW(REF ARRAY OF ARRAY OF INTEGER, 4, 3);
    n := NEW(REF ARRAY OF ARRAY OF INTEGER, 2, 3);
    recs: ARRAY [0 .. 4] OF R;
    some := NEW(REF ARRAY OF R, 3);
  BEGIN
    FOR i := 0 TO 3 DO
      FOR j := 0 TO 2 DO m[i, j] := i * 10 + j END;
    END;
    n^ := SUBARRAY(m^, 1, 2);
    SUBARRAY(m^, 2, 2) := SUBARRAY(m^, 0, 2);
    FOR i := 0 TO 1 DO Line(I(n[i, 0]) & " " & I(n[i, 1]) & " " & I(n[i, 2])) END;
    FOR i := 0 TO 3 DO Line(I(m[i, 0]) & " " & I(m[i, 2])) END;
    Line(I(NUMBER(SUBARRAY(m^, 1, 3))) & " " & I(NUMBER(SUBARRAY(m^, 1, 3)[0])));
    FOR i := 0 TO 4 DO recs[i] := R {i, -i} END;
    some^ := SUBARRAY(recs, 1, 3);
    recs[2].a := 100;
    FOR i := 0 TO 2 DO Line(I(some[i].a) & " " & I(some[i].b)) END;
  END SubArrays;

(* P1 and P2 differ: the linker of cm3's C compiler may fold equal
   procedures into one *)
VAR calls := 0;
PROCEDURE P1 () = BEGIN INC(calls) END P1;
PROCEDURE P2 () = BEGIN DEC(calls, 2) END P2;

PROCEDURE ProcValues (k: INTEGER) =
  VAR p, q: PROCEDURE ();
  PROCEDURE N1 () = BEGIN INC(k) END N1;
  BEGIN
    p := P1;
    q := P1;
    Line(B(p = q) & " " & B(p # q) & " " & B(p = P2) & " " & B(p = NIL));
    q := NIL;
    Line(B(p = q) & " " & B(q = NIL));
    p := P2;
    Line(B(p = P2) & " " & B(P1 = P2) & " " & B(P1 = P1));
    WITH n1 = N1, n2 = N1 DO
      Line(B(n1 = n2));
    END;
  END ProcValues;

PROCEDURE BigSets (x: INTEGER) =
  VAR s := Big {1, 70, 130};
  BEGIN
    s := s + Big {x, 199};
    Line(SetText(s));
    s := Big {0, 64 .. 66} + s;
    Line(SetText(s));
    s := s - Big {1, x};
    Line(SetText(s));
    s := Big {x, 10 .. 12, x + 100};
    Line(SetText(s));
    s := s * Big {11 .. 150};
    (* no comparisons of such sets: cm3's C back end cannot compile them *)
    Line(SetText(s) & " " & B(11 IN s) & " " & B(13 IN s));
  END BigSets;

PROCEDURE Raise (k: INTEGER)
  RAISES {EI, EL, EB, EC, ES, ET, EA, ERec} =
  BEGIN
    CASE k OF
    | 0 => RAISE EI(-5);
    | 1 => RAISE EL(123456789012L);
    | 2 => RAISE EB(TRUE);
    | 3 => RAISE EC('z');
    | 4 => RAISE ES(-29000);
    | 5 => RAISE ET("message");
    | 6 => RAISE EA(Arr3 {3, 2, 1});
    ELSE RAISE ERec(R {5, 6});
    END;
  END Raise;

PROCEDURE Exceptions () =
  BEGIN
    FOR k := 0 TO 7 DO
      TRY
        Raise(k);
      EXCEPT
      | EI (n) => Line("EI " & I(n));
      | EL (n) => Line("EL " & Fmt.LongInt(n));
      | EB (b) => Line("EB " & B(b));
      | EC (c) => Line("EC " & Fmt.Char(c));
      | ES (s) => Line("ES " & I(s));
      | ET (t) => Line("ET " & t);
      | EA (a) => Line("EA " & I(a[0]) & I(a[1]) & I(a[2]));
      | ERec (r) => Line("ERec " & I(r.a) & " " & I(r.b));
      END;
    END;
  END Exceptions;

VAR s: Short := 10; r := 0.5E0; b := FALSE;

BEGIN
  Line("-- arrays of 16-bit elements");
  Arrays();
  Line("-- array elements passed VAR");
  VarElements();
  Line("-- references");
  Refs();
  Line("-- variables of a nested procedure");
  Captured();
  CapturedVar(s, r, b);
  CapturedVar(shorts[3], reals[1], bools[2]);
  Line(I(s) & " " & Fmt.Real(r) & " " & B(b) & " " & I(shorts[3]) & " " & Fmt.Real(reals[1]) & " "
       & B(bools[2]));
  Line("-- value parameters of a nested procedure, passed VAR");
  CapturedValue(TRUE, 1.5E0, 0.75D0, -300, 'x', VAL(2000, WIDECHAR), 40, 123456789012L);
  Line("-- open arrays of records passed by value");
  OpenRecords();
  Line("-- default values");
  Defaults();
  Line("-- sizes of open arrays");
  Sizes(ARRAY OF CHAR {'a', 'b', 'c'}, ints, ARRAY OF ARRAY OF BOOLEAN {ARRAY OF BOOLEAN {TRUE, FALSE}},
        SUBARRAY(shorts, 1, 3));
  Line("-- ROOT");
  Roots();
  Line("-- NEW with values");
  NewWithValues();
  Line("-- SUBARRAY");
  SubArrays();
  Line("-- procedure values");
  ProcValues(1);
  Line("-- sets of 200 elements");
  BigSets(42);
  Line("-- exception arguments");
  Exceptions();
END Main.
