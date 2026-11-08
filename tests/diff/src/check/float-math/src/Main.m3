(* The functions of Math on values across their domains and at their
   edges (zeros, poles, large and small arguments, infinities, NaN). The
   C runtime and Java may differ in the last bit of some results, and in
   the sign of a NaN, so a NaN is printed without its sign. *)

MODULE Main;

IMPORT IO, Fmt, Math, IEEESpecial;

CONST
  args = ARRAY OF LONGREAL {
           0.0D0, -0.0D0, 1.0D-300, 1.0D-8, 0.1D0, 0.5D0, -0.5D0, 0.7853981633974483D0,
           1.0D0, -1.0D0, 1.5D0, 2.0D0, 2.5D0, -2.5D0, 3.141592653589793D0, 10.0D0,
           -10.0D0, 100.5D0, 710.0D0, -745.5D0, 1.0D15, 1.0D300};

VAR inf, nan: LONGREAL;

PROCEDURE L (x: LONGREAL): TEXT =
  BEGIN
    IF x # x THEN RETURN "NaN" END;
    RETURN Fmt.LongReal(x, Fmt.Style.Sci, 16);
  END L;

PROCEDURE Line (t: TEXT) =
  BEGIN
    IO.Put(t & "\n");
  END Line;

PROCEDURE One (x: LONGREAL) =
  VAR t: TEXT;
  BEGIN
    Line(L(x) & ": exp " & L(Math.exp(x)) & " expm1 " & L(Math.expm1(x)) & " log " & L(Math.log(x))
         & " log10 " & L(Math.log10(x)) & " log1p " & L(Math.log1p(x)));
    Line("  sqrt " & L(Math.sqrt(x)) & " sin " & L(Math.sin(x)) & " cos " & L(Math.cos(x))
         & " tan " & L(Math.tan(x)));
    Line("  asin " & L(Math.asin(x)) & " acos " & L(Math.acos(x)) & " atan " & L(Math.atan(x))
         & " atan2 " & L(Math.atan2(x, -1.0D0)) & " " & L(Math.atan2(-1.0D0, x)));
    Line("  sinh " & L(Math.sinh(x)) & " cosh " & L(Math.cosh(x)) & " tanh " & L(Math.tanh(x))
         & " asinh " & L(Math.asinh(x)) & " acosh " & L(Math.acosh(x)) & " atanh " & L(Math.atanh(x)));
    t := "  ceil " & L(Math.ceil(x)) & " floor " & L(Math.floor(x)) & " rint " & L(Math.rint(x))
           & " fabs " & L(Math.fabs(x)) & " hypot " & L(Math.hypot(x, 3.0D0));
    (* not frexp and modf: cm3 cannot link them; ldexp only to normal
       numbers: the C runtime does not round to denormals correctly *)
    t := t & " ldexp " & L(Math.ldexp(x, 5));
    Line(t);
    Line("  pow " & L(Math.pow(x, 2.0D0)) & " " & L(Math.pow(x, 0.5D0)) & " " & L(Math.pow(2.0D0, x))
         & " " & L(Math.pow(x, -3.0D0)) & " fmod " & L(Math.fmod(x, 0.75D0)) & " "
         & L(Math.fmod(7.25D0, x)));
  END One;

BEGIN
  inf := IEEESpecial.LongPosInf;
  nan := IEEESpecial.LongNan;
  FOR i := FIRST(args) TO LAST(args) DO One(args[i]) END;
  One(inf);
  One(-inf);
  One(nan);
  Line("pow " & L(Math.pow(0.0D0, 0.0D0)) & " " & L(Math.pow(-8.0D0, 1.0D0 / 3.0D0)) & " "
       & L(Math.pow(-2.0D0, 3.0D0)) & " " & L(Math.pow(inf, 0.0D0)) & " " & L(Math.pow(1.0D0, nan)));
  Line("hypot " & L(Math.hypot(inf, nan)) & " " & L(Math.hypot(3.0D0, 4.0D0)));
END Main.
