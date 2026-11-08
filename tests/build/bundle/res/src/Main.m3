MODULE Main;

IMPORT IO, Bundle, Res;

VAR b := Res.Get ();
BEGIN
  IO.Put (Bundle.Get (b, "a.txt"));
  IO.Put (Bundle.Get (b, "b.txt"));
END Main.
