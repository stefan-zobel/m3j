(* Constant floating-point expressions inside computed ones. The front end
   folds the arithmetic of REAL in double precision (FLOAT (x, REAL) does
   not round to REAL), but the code computes such an expression unless
   the front end has put the folded value in its place (as for an actual
   parameter); a negation of a constant is folded, so -0.0 is 0.0 there.
   ABS of -0.0 is -0.0, and the NaN of IEEESpecial is a signaling NaN. *)

MODULE Main;

IMPORT IO, Fmt, IEEESpecial, LongFloat, RealFloat;

VAR d, z: LONGREAL; r: REAL;

PROCEDURE P (t: TEXT; x: LONGREAL) =
  BEGIN
    IO.Put(t & " " & Fmt.LongReal(x, Fmt.Style.Sci, 16) & "\n");
  END P;

PROCEDURE Q (t: TEXT; x: REAL) =
  BEGIN
    IO.Put(t & " " & Fmt.Real(x, Fmt.Style.Sci, 8) & "\n");
  END Q;

BEGIN
  d := 1.0D0;
  r := 1.0E0;
  z := 0.0D0;
  P("1", FLOAT(FLOAT(0.1D0, REAL), LONGREAL));
  P("2", FLOAT(FLOAT(0.1D0, REAL), LONGREAL) * d);
  P("3", FLOAT(FLOAT(0.1D0, REAL) * r, LONGREAL));
  P("4", FLOAT(0.1E0, LONGREAL) * d);
  P("5", FLOAT(FLOAT(0.1D0, REAL) + 0.0E0, LONGREAL) * d);
  P("6", FLOAT(FLOAT(613.6123D0, REAL), LONGREAL) * d);
  P("7", FLOAT(2.5E0 / 973.6699829101562E0, LONGREAL) * d);
  Q("8", (FLOAT(16_7 - 349, REAL) / FLOAT(0.3D0 / 10000000000.0D0, REAL)) * r);
  P("9", (-0.0D0) * d);
  P("10", (-0.0D0) + z);
  P("11", ABS(-z));
  Q("12", ABS(-0.0E0 * r));
  P("13", ABS(z - 1.0D0));
  IO.Put("14 " & Fmt.Int(ORD(LongFloat.Class(IEEESpecial.LongNan))) & " "
         & Fmt.Int(ORD(RealFloat.Class(IEEESpecial.RealNan))) & "\n");
END Main.
