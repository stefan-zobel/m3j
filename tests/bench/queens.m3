(* N queens: a nested recursive procedure that uses the variables of its parent. *)
MODULE Main;
IMPORT IO, Fmt;

PROCEDURE Queens (n: INTEGER): INTEGER =
  VAR
    count := 0;
    col := NEW (REF ARRAY OF BOOLEAN, n);
    up := NEW (REF ARRAY OF BOOLEAN, 2 * n);
    down := NEW (REF ARRAY OF BOOLEAN, 2 * n);

  PROCEDURE Place (row: INTEGER) =
    BEGIN
      IF row = n THEN INC (count); RETURN END;
      FOR c := 0 TO n - 1 DO
        IF NOT col[c] AND NOT up[row + c] AND NOT down[row - c + n] THEN
          col[c] := TRUE;  up[row + c] := TRUE;  down[row - c + n] := TRUE;
          Place (row + 1);
          col[c] := FALSE;  up[row + c] := FALSE;  down[row - c + n] := FALSE;
        END;
      END;
    END Place;

  BEGIN
    Place (0);
    RETURN count;
  END Queens;

BEGIN
  IO.Put (Fmt.Int (Queens (13)) & "\n");
END Main.
