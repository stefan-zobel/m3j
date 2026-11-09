(* The package json through m3build. *)

MODULE Main;

IMPORT IO, Json, JsonWriter, TextWr;

<*FATAL ANY*>

VAR
  wr := TextWr.New ();
  w := JsonWriter.ToWr (wr);

BEGIN
  w.object ();
  w.text ("linked", "b");
  w.end ();
  w.finish ();
  IO.Put (Json.ParseBuf (TextWr.ToText (wr)).find ("b").value () & "\n");
END Main.
