(* The package xml through m3build. *)

MODULE Main;

IMPORT IO, XmlReader;

<*FATAL ANY*>

VAR r := XmlReader.FromText ("<a><b>linked</b></a>");

BEGIN
  r.root ("a");
  EVAL r.child ();
  IO.Put (r.content () & "\n");
END Main.
