(* Records in an open array. *)
MODULE Main;
IMPORT IO, Fmt;

TYPE Pt = RECORD x, y, z: INTEGER END;

VAR pts := NEW (REF ARRAY OF Pt, 1000000);

PROCEDURE Points (): INTEGER =
  VAR sum := 0;
  BEGIN
    FOR r := 1 TO 100 DO
      FOR i := 0 TO LAST (pts^) DO
        WITH p = pts[i] DO
          p.x := i * r;  p.y := p.x MOD 7;  p.z := p.x DIV 3;
        END;
      END;
      FOR i := 0 TO LAST (pts^) DO INC (sum, pts[i].x + pts[i].y - pts[i].z) END;
    END;
    RETURN sum;
  END Points;

BEGIN
  IO.Put (Fmt.Int (Points ()) & "\n");
END Main.
