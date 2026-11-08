(* Rosetta Code, task Create-a-file: https://rosettacode.org/wiki/Create_a_file (GNU Free Documentation License 1.3) *)
MODULE FileCreation EXPORTS Main;

IMPORT FS, File, OSError, IO, Stdio;

VAR file: File.T;

BEGIN
  TRY
    file := FS.OpenFile("output.txt");
    file.close();
    FS.CreateDirectory("docs");
    file := FS.OpenFile("/output.txt");
    file.close();
    FS.CreateDirectory("/docs");
  EXCEPT
  | OSError.E => IO.Put("Error creating file or directory.\n", Stdio.stderr);
  END;
END FileCreation.
