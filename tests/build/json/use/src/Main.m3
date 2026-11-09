(* The package json through m3build. *)

MODULE Main;

IMPORT IO, Json, JsonReader, JsonWriter, Text, TextWr;

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
  <*ASSERT Text.Equal (Json.ParseBuf (doc).find ("b").value (), "linked")*>
  IO.Put (r.text () & "\n");
END Main.
