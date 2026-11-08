(* Rosetta Code, task Binary-digits: https://rosettacode.org/wiki/Binary_digits (GNU Free Documentation License 1.3) *)
MODULE Binary EXPORTS Main;

IMPORT IO, Fmt;

VAR num := 10;

BEGIN
  IO.Put(Fmt.Int(num, 2) & "\n");
  num := 150;
  IO.Put(Fmt.Int(num, 2) & "\n");
END Binary.
