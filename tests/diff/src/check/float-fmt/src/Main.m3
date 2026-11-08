(* Formatting and scanning of floating-point and integer values: Fmt in
   all styles and precisions, Scan, and Convert, on values at the edges
   (zeros, ties, powers of ten, the smallest and largest numbers,
   denormals, infinities, NaN). *)

MODULE Main;

IMPORT IO, Fmt, Scan, Lex, FloatMode, Convert, LongReal, Real, IEEESpecial, Word, Text;

CONST
  longs = ARRAY OF LONGREAL {
             0.0D0, -0.0D0, 1.0D0, -1.0D0, 0.1D0, 0.5D0, 1.5D0, 2.5D0, -2.5D0,
             1.0D0 / 3.0D0, 2.0D0 / 3.0D0, 123456789.125D0, 1.0D22, 1.0D23,
             9007199254740993.0D0, 1.0D-5, 1.0D-300, 999999.5D0, 0.000123456D0,
             3.141592653589793D0, 0.30000000000000004D0, 9.995D0, 0.0095D0,
             1234567.0D0, 12345678901234567890.0D0, 5.0D-324,
             LongReal.MinPos, LongReal.MinPosNormal, LongReal.MaxFinite};
  reals = ARRAY OF REAL {
             0.0E0, -0.0E0, 1.0E0, -1.0E0, 0.1E0, 0.5E0, 2.5E0, 1.0E0 / 3.0E0,
             123456.7E0, 1.0E10, 1.0E-10, 16777217.0E0, 3.4028235E38,
             Real.MinPos, Real.MinPosNormal, Real.MaxFinite, 9.995E0};
  ints = ARRAY OF INTEGER {
            0, 1, -1, 7, -7, 255, 256, 65535, -65536, 2147483647, -2147483648,
            LAST(INTEGER), FIRST(INTEGER), 123456789, -987654321};
  scans = ARRAY OF TEXT {
             "0", "1", "-1", "+1", "1.5", "-1.5", "1e10", "1E-10", "1d5", "1D5",
             "1x5", "1X-5", "  2.5  ", "0.1", "4.9e-324", "2.4703282292062328e-324",
             "2.2250738585072014e-308", "1.7976931348623157e308", "1.7976931348623159e308",
             "1e309", "1e-400", "-1e-400", "123456789012345678901234567890", ".5",
             "5.", "1e", "abc", "1.5 2", "", "0.000000000000000000000000000001",
             "3.4028235e38", "3.4028236e38", "1.4e-45", "7e-46", "16777217",
             "9007199254740993", "-0", "-0.0", "1_000", "0x10"};
  intscans = ARRAY OF TEXT {
                "0", "-0", "123", "-123", "+5", "9223372036854775807",
                "-9223372036854775808", "9223372036854775808", "16_FF", "2_1011",
                "8_777", "16_FFFFFFFFFFFFFFFF", "16_10000000000000000", "12a",
                " 42 ", "", "-", "1 2"};

PROCEDURE Line (t: TEXT) =
  BEGIN
    IO.Put(t & "\n");
  END Line;

PROCEDURE FmtLong (x: LONGREAL) =
  CONST Precs = ARRAY OF CARDINAL {0, 1, 3, 6, 10, 16};
  VAR t := Fmt.LongReal(x) & " | " & Fmt.LongReal(x, literal := TRUE);
  BEGIN
    FOR s := FIRST(Fmt.Style) TO LAST(Fmt.Style) DO
      FOR i := FIRST(Precs) TO LAST(Precs) DO
        t := t & " | " & Fmt.LongReal(x, s, Precs[i]);
      END;
    END;
    Line(t);
  END FmtLong;

PROCEDURE FmtReal (x: REAL) =
  CONST Precs = ARRAY OF CARDINAL {0, 1, 3, 6, 8};
  VAR t := Fmt.Real(x) & " | " & Fmt.Real(x, literal := TRUE);
  BEGIN
    FOR s := FIRST(Fmt.Style) TO LAST(Fmt.Style) DO
      FOR i := FIRST(Precs) TO LAST(Precs) DO
        t := t & " | " & Fmt.Real(x, s, Precs[i]);
      END;
    END;
    Line(t);
  END FmtReal;

PROCEDURE ConvertLong (x: LONGREAL) =
  VAR buf: ARRAY [0 .. 399] OF CHAR; n, used: INTEGER; t := "";
  BEGIN
    FOR s := FIRST(Convert.Style) TO LAST(Convert.Style) DO
      FOR p := 0 TO 17 BY 4 DO
        TRY
          n := Convert.FromLongFloat(buf, x, p, s);
          t := t & " | " & Text.FromChars(SUBARRAY(buf, 0, n));
          EVAL Convert.ToLongFloat(SUBARRAY(buf, 0, n), used);
          t := t & " (" & Fmt.Int(used) & ")";
        EXCEPT
        | Convert.Failed => t := t & " | failed";
        END;
      END;
    END;
    Line(t);
  END ConvertLong;

PROCEDURE ScanText (s: TEXT) =
  VAR t := "[" & s & "]";
  BEGIN
    TRY
      t := t & " " & Fmt.LongReal(Scan.LongReal(s), Fmt.Style.Sci, 16);
    EXCEPT
    | Lex.Error => t := t & " Lex.Error";
    | FloatMode.Trap (f) => t := t & " Trap " & Fmt.Int(ORD(f));
    END;
    TRY
      t := t & " " & Fmt.Real(Scan.Real(s), Fmt.Style.Sci, 8);
    EXCEPT
    | Lex.Error => t := t & " Lex.Error";
    | FloatMode.Trap (f) => t := t & " Trap " & Fmt.Int(ORD(f));
    END;
    TRY
      t := t & " " & Fmt.Extended(Scan.Extended(s), Fmt.Style.Sci, 16);
    EXCEPT
    | Lex.Error => t := t & " Lex.Error";
    | FloatMode.Trap (f) => t := t & " Trap " & Fmt.Int(ORD(f));
    END;
    Line(t);
  END ScanText;

PROCEDURE ScanInt (s: TEXT) =
  VAR t := "[" & s & "]";
  BEGIN
    TRY
      t := t & " " & Fmt.Int(Scan.Int(s));
    EXCEPT
    | Lex.Error => t := t & " Lex.Error";
    | FloatMode.Trap (f) => t := t & " Trap " & Fmt.Int(ORD(f));
    END;
    TRY
      t := t & " " & Fmt.Unsigned(Scan.Unsigned(s));
    EXCEPT
    | Lex.Error => t := t & " Lex.Error";
    | FloatMode.Trap (f) => t := t & " Trap " & Fmt.Int(ORD(f));
    END;
    TRY
      t := t & " " & Fmt.LongInt(Scan.LongInt(s, 10));
    EXCEPT
    | Lex.Error => t := t & " Lex.Error";
    | FloatMode.Trap (f) => t := t & " Trap " & Fmt.Int(ORD(f));
    END;
    TRY
      t := t & " " & Fmt.Bool(Scan.Bool(s));
    EXCEPT
    | Lex.Error => t := t & " Lex.Error";
    END;
    Line(t);
  END ScanInt;

PROCEDURE FmtInt (i: INTEGER) =
  VAR t := Fmt.Int(i);
  BEGIN
    FOR b := 2 TO 16 DO
      t := t & " " & Fmt.Int(i, b);
    END;
    t := t & " | " & Fmt.Unsigned(i) & " " & Fmt.Unsigned(i, 10) & " " & Fmt.Unsigned(i, 2);
    t := t & " | " & Fmt.LongInt(VAL(i, LONGINT), 7) & " " & Fmt.LongUnsigned(VAL(i, LONGINT));
    t := t & " | " & Fmt.Pad(Fmt.Int(i), 12) & "|" & Fmt.Pad(Fmt.Int(i), 12, '*', Fmt.Align.Left);
    Line(t);
  END FmtInt;

PROCEDURE ConvertInt (i: INTEGER) =
  VAR buf: ARRAY [0 .. 99] OF CHAR; n, used: INTEGER; t := "";
  BEGIN
    TRY
      FOR b := 2 TO 16 BY 7 DO
        n := Convert.FromInt(buf, i, b, prefix := TRUE);
        t := t & " " & Text.FromChars(SUBARRAY(buf, 0, n));
        t := t & "=" & Fmt.Int(Convert.ToInt(SUBARRAY(buf, 0, n), used, b));
        t := t & "(" & Fmt.Int(used) & ")";
        n := Convert.FromUnsigned(buf, i, b);
        t := t & " " & Text.FromChars(SUBARRAY(buf, 0, n));
        t := t & "=" & Fmt.Int(Convert.ToUnsigned(SUBARRAY(buf, 0, n), used, b));
      END;
    EXCEPT
    | Convert.Failed => t := t & " failed";
    END;
    Line(t);
  END ConvertInt;

BEGIN
  Line("-- Fmt.LongReal");
  FOR i := FIRST(longs) TO LAST(longs) DO FmtLong(longs[i]) END;
  FmtLong(IEEESpecial.LongPosInf);
  FmtLong(IEEESpecial.LongNegInf);
  FmtLong(IEEESpecial.LongNan);
  Line("-- Fmt.Real");
  FOR i := FIRST(reals) TO LAST(reals) DO FmtReal(reals[i]) END;
  FmtReal(IEEESpecial.RealPosInf);
  FmtReal(IEEESpecial.RealNegInf);
  FmtReal(IEEESpecial.RealNan);
  Line("-- Fmt.Extended");
  FOR i := FIRST(longs) TO LAST(longs) DO
    Line(Fmt.Extended(FLOAT(longs[i], EXTENDED)) & " | "
         & Fmt.Extended(FLOAT(longs[i], EXTENDED), Fmt.Style.Fix, 4) & " | "
         & Fmt.Extended(FLOAT(longs[i], EXTENDED), literal := TRUE));
  END;
  Line("-- Convert (floating point)");
  FOR i := FIRST(longs) TO LAST(longs) DO ConvertLong(longs[i]) END;
  Line("-- Scan (floating point)");
  FOR i := FIRST(scans) TO LAST(scans) DO ScanText(scans[i]) END;
  Line("-- Scan (integers)");
  FOR i := FIRST(intscans) TO LAST(intscans) DO ScanInt(intscans[i]) END;
  Line("-- Fmt (integers)");
  FOR i := FIRST(ints) TO LAST(ints) DO FmtInt(ints[i]) END;
  Line("-- Convert (integers)");
  FOR i := FIRST(ints) TO LAST(ints) DO ConvertInt(ints[i]) END;
  Line(Fmt.Bool(TRUE) & " " & Fmt.Bool(FALSE) & " " & Fmt.Char('x') & " "
       & Fmt.Unsigned(Word.Not(0)));
END Main.
