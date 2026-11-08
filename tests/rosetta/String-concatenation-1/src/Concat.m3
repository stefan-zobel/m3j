(* Rosetta Code, task String-concatenation: https://rosettacode.org/wiki/String_concatenation (GNU Free Documentation License 1.3) *)
MODULE Concat EXPORTS Main;

IMPORT IO;

VAR string: TEXT := "String";
    string1: TEXT;

BEGIN
  IO.Put(string & " literal.\n");
  string1 := string & " literal.\n";
  IO.Put(string1);
END Concat.
