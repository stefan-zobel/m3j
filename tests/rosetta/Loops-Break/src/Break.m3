(* Rosetta Code, task Loops-Break: https://rosettacode.org/wiki/Loops_Break (GNU Free Documentation License 1.3) *)
MODULE Break EXPORTS Main;

IMPORT IO, Fmt, Random;

VAR a,b: INTEGER;

BEGIN
  WITH rand = NEW(Random.Default).init() DO
    LOOP
      a := rand.integer(min := 0, max := 19);
      IO.Put(Fmt.Int(a) & "\n");
      IF a = 10 THEN EXIT END;
      b := rand.integer(min := 0, max := 19);
      IO.Put(Fmt.Int(b) & "\n");
    END;
  END;
END Break.
