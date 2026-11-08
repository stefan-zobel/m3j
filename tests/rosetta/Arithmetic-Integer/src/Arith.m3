(* Rosetta Code, task Arithmetic-Integer: https://rosettacode.org/wiki/Arithmetic_Integer (GNU Free Documentation License 1.3) *)
MODULE Arith EXPORTS Main;

IMPORT IO, Fmt;

VAR a, b: INTEGER;

BEGIN
  a := IO.GetInt();
  b := IO.GetInt();
  IO.Put("a+b = " & Fmt.Int(a + b) & "\n");
  IO.Put("a-b = " & Fmt.Int(a - b) & "\n");
  IO.Put("a*b = " & Fmt.Int(a * b) & "\n");
  IO.Put("a DIV b = " & Fmt.Int(a DIV b) & "\n");
  IO.Put("a MOD b = " & Fmt.Int(a MOD b) & "\n");
END Arith.
