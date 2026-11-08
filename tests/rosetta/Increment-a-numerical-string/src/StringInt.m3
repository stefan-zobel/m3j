(* Rosetta Code, task Increment-a-numerical-string: https://rosettacode.org/wiki/Increment_a_numerical_string (GNU Free Documentation License 1.3) *)
MODULE StringInt EXPORTS Main;

IMPORT IO, Fmt, Scan;

VAR string: TEXT := "1234";
    num: INTEGER := 0;

BEGIN
  num := Scan.Int(string);
  IO.Put(string & " + 1 = " & Fmt.Int(num + 1) & "\n");
END StringInt.
