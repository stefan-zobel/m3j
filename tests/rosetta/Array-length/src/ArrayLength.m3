(* Rosetta Code, task Array-length: https://rosettacode.org/wiki/Array_length (GNU Free Documentation License 1.3) *)
MODULE ArrayLength EXPORTS Main;

IMPORT IO;

VAR
     Arr:ARRAY[1..2] OF TEXT :=
			     ARRAY[1..2] OF TEXT{"apples", "oranges"};
BEGIN
     IO.PutInt(NUMBER(Arr));
END ArrayLength.
