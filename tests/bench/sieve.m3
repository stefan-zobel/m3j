(* Sieve of Eratosthenes on an open array of BOOLEAN. *)
MODULE Main;
IMPORT IO, Fmt;

CONST N = 20000000;

VAR flags := NEW (REF ARRAY OF BOOLEAN, N);

PROCEDURE Sieve (): INTEGER =
  VAR count := 0;  k: INTEGER;
  BEGIN
    FOR i := 0 TO N - 1 DO flags[i] := TRUE END;
    FOR i := 2 TO N - 1 DO
      IF flags[i] THEN
        INC (count);
        k := i + i;
        WHILE k < N DO flags[k] := FALSE; INC (k, i) END;
      END;
    END;
    RETURN count;
  END Sieve;

VAR r := 0;
BEGIN
  FOR i := 1 TO 10 DO r := Sieve () END;
  IO.Put (Fmt.Int (r) & "\n");
END Main.
