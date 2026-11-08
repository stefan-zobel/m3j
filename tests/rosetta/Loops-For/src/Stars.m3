(* Rosetta Code, task Loops-For: https://rosettacode.org/wiki/Loops_For (GNU Free Documentation License 1.3) *)
MODULE Stars EXPORTS Main;

IMPORT IO;

BEGIN
  FOR i := 1 TO 5 DO
    FOR j := 1 TO i DO
      IO.Put("*");
    END;
    IO.Put("\n");
  END;
END Stars.
