(* Matrix multiplication of fixed LONGREAL matrices. *)
MODULE Main;
IMPORT IO, Fmt;

CONST N = 400;
TYPE Mat = ARRAY [0 .. N - 1] OF ARRAY [0 .. N - 1] OF LONGREAL;

VAR a := NEW (REF Mat);
    b := NEW (REF Mat);
    c := NEW (REF Mat);

PROCEDURE MatMul (READONLY a, b: Mat; VAR c: Mat) =
  VAR s: LONGREAL;
  BEGIN
    FOR i := 0 TO N - 1 DO
      FOR j := 0 TO N - 1 DO
        s := 0.0d0;
        FOR k := 0 TO N - 1 DO s := s + a[i, k] * b[k, j] END;
        c[i, j] := s;
      END;
    END;
  END MatMul;

VAR sum := 0.0d0;
BEGIN
  FOR i := 0 TO N - 1 DO
    FOR j := 0 TO N - 1 DO
      a[i, j] := FLOAT (i + j, LONGREAL) / FLOAT (N, LONGREAL);
      b[i, j] := FLOAT (i - j, LONGREAL) / FLOAT (N, LONGREAL);
    END;
  END;
  FOR r := 1 TO 5 DO MatMul (a^, b^, c^) END;
  FOR i := 0 TO N - 1 DO sum := sum + c[i, (i * 7) MOD N] END;
  IO.Put (Fmt.LongReal (sum, Fmt.Style.Fix, 4) & "\n");
END Main.
