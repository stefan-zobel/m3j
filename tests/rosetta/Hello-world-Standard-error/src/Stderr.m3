(* Rosetta Code, task Hello-world-Standard-error: https://rosettacode.org/wiki/Hello_world_Standard_error (GNU Free Documentation License 1.3) *)
MODULE Stderr EXPORTS Main;

IMPORT Wr, Stdio;

BEGIN
  Wr.PutText(Stdio.stderr, "Goodbye, World!\n");
END Stderr.
