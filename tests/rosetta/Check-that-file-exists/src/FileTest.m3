(* Rosetta Code, task Check-that-file-exists: https://rosettacode.org/wiki/Check_that_file_exists (GNU Free Documentation License 1.3) *)
MODULE FileTest EXPORTS Main;

IMPORT IO, Fmt, FS, File, OSError, Pathname;

PROCEDURE FileExists(file: Pathname.T): BOOLEAN =
  VAR status: File.Status;
  BEGIN
    TRY
      status := FS.Status(file);
      RETURN TRUE;
    EXCEPT
    | OSError.E => RETURN FALSE;
    END;
  END FileExists;

BEGIN
  IO.Put(Fmt.Bool(FileExists("input.txt")) & "\n");
  IO.Put(Fmt.Bool(FileExists("/input.txt")) & "\n");
  IO.Put(Fmt.Bool(FileExists("docs/")) & "\n");
  IO.Put(Fmt.Bool(FileExists("/docs")) & "\n");
END FileTest.
