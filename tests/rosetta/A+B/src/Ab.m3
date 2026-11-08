(* Rosetta Code, task A+B: https://rosettacode.org/wiki/A+B (GNU Free Documentation License 1.3) *)
MODULE Ab EXPORTS Main;

IMPORT IO;

VAR
     A,B:INTEGER;
BEGIN
     TRY
	  A := IO.GetInt();
          B := IO.GetInt();
     EXCEPT
     | IO.Error => IO.Put("Invalid number!\n");
     END;
     IO.PutInt(A+B);
     IO.Put("\n");
END Ab.
