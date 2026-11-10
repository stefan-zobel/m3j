(* The library sql through m3build: a URL that no driver accepts, since
   the program names no jar of a driver. *)

MODULE Main;

IMPORT IO, SQL, Thread;

<*FATAL Thread.Alerted*>

CONST Kinds = ARRAY SQL.Kind OF TEXT {"NoDriver", "Connection", "Auth", "Syntax", "Constraint", "Rollback", "Data",
                             "Timeout", "NotSupported", "Null", "Type", "Index", "Unbound", "Closed", "Other"};

BEGIN
  TRY
    SQL.Connect ("jdbc:none:x").close ();
    IO.Put ("connected\n");
  EXCEPT
  | SQL.Error (f) => IO.Put ("SQL." & Kinds[f.kind] & "\n");
  END;
END Main.
