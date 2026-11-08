(* Floating-point operations at the edges: the conversions FLOAT, TRUNC,
   ROUND, FLOOR and CEILING (ties, signed zeros, the limits of INTEGER),
   arithmetic with signed zeros, denormals, infinities and NaN, the
   comparisons, ABS, MIN and MAX, and the procedures of LongFloat and
   RealFloat. *)

MODULE Main;

IMPORT IO, Fmt, LongFloat, RealFloat, FloatMode, IEEESpecial, LongReal, Real;

CONST
  longs = ARRAY OF LONGREAL {
            0.0D0, -0.0D0, 0.5D0, -0.5D0, 1.5D0, -1.5D0, 2.5D0, -2.5D0,
            0.49999999999999994D0, -0.49999999999999994D0, 1.0D0, -1.0D0,
            4503599627370495.5D0, -4503599627370495.5D0, 4503599627370497.0D0,
            1.0D15, 123.456D0, -123.456D0, 9.2233720368547748D18,
            -9.2233720368547758D18, 0.1D0, 1.0D-310, -1.0D-310};
  reals = ARRAY OF REAL {
            0.0E0, -0.0E0, 0.5E0, -0.5E0, 1.5E0, -1.5E0, 2.5E0, -2.5E0,
            0.49999997E0, 8388607.5E0, -8388607.5E0, 16777215.0E0, 1.0E-40,
            123.456E0, 9.2233715E18, -9.223372E18};
  ints = ARRAY OF INTEGER {
           0, 1, -1, 16777217, -16777217, 9007199254740993, -9007199254740993,
           LAST(INTEGER), FIRST(INTEGER), 4611686018427387905, 123456789};

VAR
  pinf, ninf, nan, zero, nzero, one, tiny, huge: LONGREAL;
  rpinf, rnan, rzero, rnzero, rone: REAL;

PROCEDURE L (x: LONGREAL): TEXT =
  BEGIN
    RETURN Fmt.LongReal(x, Fmt.Style.Sci, 16);
  END L;

PROCEDURE R (x: REAL): TEXT =
  BEGIN
    RETURN Fmt.Real(x, Fmt.Style.Sci, 8);
  END R;

PROCEDURE B (b: BOOLEAN): TEXT =
  BEGIN
    RETURN Fmt.Bool(b);
  END B;

PROCEDURE Line (t: TEXT) =
  BEGIN
    IO.Put(t & "\n");
  END Line;

PROCEDURE Conversions (x: LONGREAL) =
  VAR t := L(x);
  BEGIN
    IF ABS(x) < 9.2D18 THEN
      t := t & " trunc " & Fmt.Int(TRUNC(x)) & " round " & Fmt.Int(ROUND(x)) & " floor "
             & Fmt.Int(FLOOR(x)) & " ceiling " & Fmt.Int(CEILING(x));
    END;
    t := t & " real " & R(FLOAT(x, REAL)) & " ext " & Fmt.Extended(FLOAT(x, EXTENDED), Fmt.Style.Sci, 16);
    t := t & " abs " & L(ABS(x)) & " neg " & L(-x);
    Line(t);
  END Conversions;

PROCEDURE RealConversions (x: REAL) =
  VAR t := R(x);
  BEGIN
    IF ABS(x) < 9.2E18 THEN
      t := t & " trunc " & Fmt.Int(TRUNC(x)) & " round " & Fmt.Int(ROUND(x)) & " floor "
             & Fmt.Int(FLOOR(x)) & " ceiling " & Fmt.Int(CEILING(x));
    END;
    t := t & " long " & L(FLOAT(x, LONGREAL)) & " abs " & R(ABS(x)) & " neg " & R(-x);
    Line(t);
  END RealConversions;

PROCEDURE IntConversions (i: INTEGER) =
  BEGIN
    Line(Fmt.Int(i) & " real " & R(FLOAT(i, REAL)) & " real1 " & R(FLOAT(i)) & " longreal "
         & L(FLOAT(i, LONGREAL)) & " ext " & Fmt.Extended(FLOAT(i, EXTENDED), Fmt.Style.Sci, 16)
         & " fromlong " & L(FLOAT(VAL(i, LONGINT), LONGREAL)));
  END IntConversions;

PROCEDURE Compare (a, b: LONGREAL) =
  BEGIN
    Line(L(a) & " " & L(b) & ": = " & B(a = b) & " # " & B(a # b) & " < " & B(a < b) & " <= "
         & B(a <= b) & " > " & B(a > b) & " >= " & B(a >= b) & " min " & L(MIN(a, b))
         & " max " & L(MAX(a, b)) & " differs " & B(LongFloat.Differs(a, b)) & " unordered "
         & B(LongFloat.Unordered(a, b)));
  END Compare;

PROCEDURE Arith (a, b: LONGREAL) =
  BEGIN
    Line(L(a) & " " & L(b) & ": + " & L(a + b) & " - " & L(a - b) & " * " & L(a * b)
         & " / " & L(a / b));
  END Arith;

PROCEDURE RealArith (a, b: REAL) =
  BEGIN
    Line(R(a) & " " & R(b) & ": + " & R(a + b) & " - " & R(a - b) & " * " & R(a * b)
         & " / " & R(a / b) & " = " & B(a = b) & " < " & B(a < b));
  END RealArith;

PROCEDURE Functions (x: LONGREAL) =
  VAR t := L(x);
  BEGIN
    TRY
      t := t & " scalb " & L(LongFloat.Scalb(x, 3)) & " " & L(LongFloat.Scalb(x, -1074));
      t := t & " logb " & L(LongFloat.Logb(x));
      IF NOT LongFloat.IsNaN(x) THEN
        (* ILogb fails an assertion for NaN *)
        t := t & " ilogb " & Fmt.Int(LongFloat.ILogb(x));
      END;
      t := t & " next " & L(LongFloat.NextAfter(x, pinf)) & " " & L(LongFloat.NextAfter(x, ninf));
      t := t & " copysign " & L(LongFloat.CopySign(one, x));
      t := t & " finite " & B(LongFloat.Finite(x)) & " isnan " & B(LongFloat.IsNaN(x));
      t := t & " sign " & Fmt.Int(LongFloat.Sign(x)) & " class " & Fmt.Int(ORD(LongFloat.Class(x)));
      t := t & " sqrt " & L(LongFloat.Sqrt(ABS(x)));
    EXCEPT
    | FloatMode.Trap (f) => t := t & " Trap " & Fmt.Int(ORD(f));
    END;
    Line(t);
  END Functions;

PROCEDURE RealFunctions (x: REAL) =
  VAR t := R(x);
  BEGIN
    TRY
      t := t & " scalb " & R(RealFloat.Scalb(x, 3)) & " logb " & R(RealFloat.Logb(x));
      IF NOT RealFloat.IsNaN(x) THEN
        t := t & " ilogb " & Fmt.Int(RealFloat.ILogb(x));
      END;
      t := t & " next " & R(RealFloat.NextAfter(x, rpinf));
      t := t & " copysign " & R(RealFloat.CopySign(rone, x));
      t := t & " finite " & B(RealFloat.Finite(x)) & " isnan " & B(RealFloat.IsNaN(x));
      t := t & " sign " & Fmt.Int(RealFloat.Sign(x)) & " class " & Fmt.Int(ORD(RealFloat.Class(x)));
      t := t & " sqrt " & R(RealFloat.Sqrt(ABS(x)));
    EXCEPT
    | FloatMode.Trap (f) => t := t & " Trap " & Fmt.Int(ORD(f));
    END;
    Line(t);
  END RealFunctions;

BEGIN
  pinf := IEEESpecial.LongPosInf;
  ninf := IEEESpecial.LongNegInf;
  nan := IEEESpecial.LongNan;
  zero := 0.0D0;
  nzero := -zero;
  one := 1.0D0;
  tiny := LongReal.MinPos;
  huge := LongReal.MaxFinite;
  rpinf := IEEESpecial.RealPosInf;
  rnan := IEEESpecial.RealNan;
  rzero := 0.0E0;
  rnzero := -rzero;
  rone := 1.0E0;

  Line("-- conversions");
  FOR i := FIRST(longs) TO LAST(longs) DO Conversions(longs[i]) END;
  Conversions(nzero);
  Conversions(tiny);
  Conversions(huge);
  FOR i := FIRST(reals) TO LAST(reals) DO RealConversions(reals[i]) END;
  RealConversions(rnzero);
  RealConversions(Real.MinPos);
  FOR i := FIRST(ints) TO LAST(ints) DO IntConversions(ints[i]) END;

  Line("-- comparisons");
  Compare(zero, nzero);
  Compare(one, nan);
  Compare(nan, nan);
  Compare(pinf, huge);
  Compare(ninf, -huge);
  Compare(tiny, zero);
  Compare(-tiny, nzero);
  Compare(one, one + tiny);

  Line("-- arithmetic");
  Arith(one, zero);
  Arith(one, nzero);
  Arith(zero, zero);
  Arith(nzero, nzero);
  Arith(pinf, pinf);
  Arith(pinf, ninf);
  Arith(nan, one);
  Arith(huge, huge);
  Arith(tiny, 0.5D0);
  Arith(tiny, 2.0D0);
  Arith(LongReal.MinPosNormal, 3.0D0);
  Arith(1.0D0 / 3.0D0, 3.0D0);
  Arith(0.1D0, 0.2D0);
  Arith(1.0D16, 1.0D0);
  RealArith(rone, rzero);
  RealArith(rone, rnzero);
  RealArith(rnan, rone);
  RealArith(Real.MaxFinite, 2.0E0);
  RealArith(Real.MinPos, 0.5E0);
  RealArith(0.1E0, 0.2E0);
  RealArith(16777216.0E0, 1.0E0);

  Line("-- LongFloat");
  FOR i := FIRST(longs) TO LAST(longs) DO Functions(longs[i]) END;
  Functions(pinf);
  Functions(ninf);
  Functions(nan);
  Functions(tiny);
  Functions(huge);
  Line("-- RealFloat");
  FOR i := FIRST(reals) TO LAST(reals) DO RealFunctions(reals[i]) END;
  RealFunctions(rpinf);
  RealFunctions(rnan);
END Main.
