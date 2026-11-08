(* Quicksort of an open array of INTEGER filled by a linear congruential generator. *)
MODULE Main;
IMPORT IO, Fmt, Word;

CONST N = 5000000;

VAR a := NEW (REF ARRAY OF INTEGER, N);
    seed: Word.T := 42;

PROCEDURE Random (): INTEGER =
  BEGIN
    seed := Word.Plus (Word.Times (seed, 6364136223846793005), 1442695040888963407);
    RETURN Word.RightShift (seed, 33);
  END Random;

PROCEDURE Sort (VAR a: ARRAY OF INTEGER; lo, hi: INTEGER) =
  VAR i, j, p, t: INTEGER;
  BEGIN
    WHILE lo < hi DO
      i := lo;  j := hi;  p := a[(lo + hi) DIV 2];
      REPEAT
        WHILE a[i] < p DO INC (i) END;
        WHILE p < a[j] DO DEC (j) END;
        IF i <= j THEN t := a[i]; a[i] := a[j]; a[j] := t; INC (i); DEC (j) END;
      UNTIL i > j;
      IF j - lo < hi - i THEN
        Sort (a, lo, j);  lo := i;
      ELSE
        Sort (a, i, hi);  hi := j;
      END;
    END;
  END Sort;

VAR sum := 0;
BEGIN
  FOR r := 1 TO 4 DO
    FOR i := 0 TO N - 1 DO a[i] := Random () END;
    Sort (a^, 0, N - 1);
    FOR i := 1 TO N - 1 DO <* ASSERT a[i - 1] <= a[i] *> END;
    INC (sum, a[N DIV 2]);
  END;
  IO.Put (Fmt.Int (sum) & "\n");
END Main.
