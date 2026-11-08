(* Rosetta Code, task Loops-N-plus-one-half: https://rosettacode.org/wiki/Loops_N_plus_one_half (GNU Free Documentation License 1.3) *)
MODULE Loop EXPORTS Main;

IMPORT IO, Fmt;

VAR i := 1;

BEGIN
  LOOP
    IO.Put(Fmt.Int(i));
    IF i = 10 THEN EXIT; END;
    IO.Put(", ");
    i := i + 1;
  END;
  IO.Put("\n");
END Loop.
