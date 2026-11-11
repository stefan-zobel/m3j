(* The package json through m3build. *)

MODULE Main;

IMPORT IO, JsonReader, JsonWriter, TextWr;

<*FATAL ANY*>

VAR
  wr := TextWr.New ();
  w := JsonWriter.ToWr (wr);
  doc: TEXT;
  r: JsonReader.T;

BEGIN
  w.object ();
  w.text ("linked", "b");
  w.end ();
  w.finish ();
  doc := TextWr.ToText (wr);
  r := JsonReader.FromText (doc);
  r.object ();
  EVAL r.member ();
  IO.Put (r.text () & "\n");
END Main.
