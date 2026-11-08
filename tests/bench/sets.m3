(* Small sets: N queens with bit sets. *)
MODULE Main;
IMPORT IO, Fmt;

TYPE Set = SET OF [0 .. 63];

PROCEDURE Count (n: INTEGER; cols, d1, d2: Set; row: INTEGER): INTEGER =
  VAR r := 0;
  BEGIN
    IF row = n THEN RETURN 1 END;
    FOR c := 0 TO n - 1 DO
      IF NOT (c IN cols) AND NOT (row + c IN d1) AND NOT (row - c + n IN d2) THEN
        INC (r, Count (n, cols + Set {c}, d1 + Set {row + c}, d2 + Set {row - c + n}, row + 1));
      END;
    END;
    RETURN r;
  END Count;

BEGIN
  IO.Put (Fmt.Int (Count (13, Set {}, Set {}, Set {}, 0)) & "\n");
END Main.
