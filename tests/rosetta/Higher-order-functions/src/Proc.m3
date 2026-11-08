(* Rosetta Code, task Higher-order-functions: https://rosettacode.org/wiki/Higher_order_functions (GNU Free Documentation License 1.3) *)
MODULE Proc EXPORTS Main;

IMPORT IO;

TYPE Proc = PROCEDURE();

PROCEDURE Second() =
  BEGIN
    IO.Put("Second procedure.\n");
  END Second;

PROCEDURE First(proc: Proc) =
  BEGIN
    proc();
  END First;

BEGIN
  First(Second);
END Proc.
