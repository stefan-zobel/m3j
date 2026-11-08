(* Rosetta Code, task 99-bottles-of-beer: https://rosettacode.org/wiki/99_bottles_of_beer (GNU Free Documentation License 1.3) *)
MODULE Bottles EXPORTS Main;

IMPORT IO, Fmt;

BEGIN
  FOR i := 99 TO 1 BY -1 DO
    IO.Put(Fmt.Int(i) & " bottles of beer on the wall\n");
    IO.Put(Fmt.Int(i) & " bottles of beer\n");
    IO.Put("Take one down, pass it around\n");
    IO.Put(Fmt.Int(i - 1) & " bottles of beer on the wall\n");
    IO.Put("\n");
  END;
END Bottles.
