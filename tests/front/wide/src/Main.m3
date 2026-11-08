MODULE Main;

IMPORT IO, Text, WideLit;

BEGIN
  IO.PutInt (Text.Length (WideLit.Run ()));
  IO.Put ("\n");
END Main.
