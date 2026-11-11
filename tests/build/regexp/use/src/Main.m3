(* The packages regexp and patternmatching through m3build. *)

MODULE Main;

IMPORT Glob, IO, RegEx, Regexp;

<*FATAL ANY*>

VAR re := Regexp.Compile ("([a-z]+)ed");

BEGIN
  IO.Put (Regexp.Replace (re, "linked", "$1") & "ed\n");
  IF Glob.Match ("*.m3", "Main.m3") AND RegEx.Execute (RegEx.Compile ("a*b"), "aab") = 0 THEN
    IO.Put ("matched\n");
  END;
END Main.
