(* The package xml through m3build. *)

MODULE Main;

IMPORT IO, TextRd, TextWr, XmlReader, XmlWriter;

<*FATAL ANY*>

VAR
  wr := TextWr.New ();
  w := XmlWriter.ToWr (wr);
  r: XmlReader.T;

BEGIN
  w.start ("a");
  w.element ("b", "linked");
  w.end ();
  w.finish ();
  r := XmlReader.FromRd (TextRd.New (TextWr.ToText (wr)));
  r.root ("a");
  EVAL r.child ();
  IO.Put (r.content () & "\n");
END Main.
