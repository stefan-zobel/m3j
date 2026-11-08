(* Rosetta Code, task Input-loop: https://rosettacode.org/wiki/Input_loop (GNU Free Documentation License 1.3) *)
MODULE Output EXPORTS Main;

IMPORT Rd, Wr, Stdio;

VAR buf: TEXT;

<*FATAL ANY*>

BEGIN
  WHILE NOT Rd.EOF(Stdio.stdin) DO
    buf := Rd.GetLine(Stdio.stdin);
    Wr.PutText(Stdio.stdout, buf);
  END;
END Output.
