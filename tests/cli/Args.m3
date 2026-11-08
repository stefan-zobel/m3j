(* Prints its arguments: for the option -run of the compiler. *)

MODULE Args EXPORTS Main;

IMPORT IO, Fmt, Params;

BEGIN
  IO.Put(Fmt.Int(Params.Count - 1));
  FOR i := 1 TO Params.Count - 1 DO
    IO.Put(" [" & Params.Get(i) & "]");
  END;
  IO.Put("\n");
END Args.
