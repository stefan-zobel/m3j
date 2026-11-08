(* Rosetta Code, task Generic-swap: https://rosettacode.org/wiki/Generic_swap (GNU Free Documentation License 1.3) *)
MODULE Main;

IMPORT IntSwap, IO, Fmt;

VAR left := 10;
    right := 20;

BEGIN
  IO.Put("Left = " & Fmt.Int(left) & "\n");
  IntSwap.Swap(left, right);
  IO.Put("Left = " & Fmt.Int(left) & "\n");
END Main.
