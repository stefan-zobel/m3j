(* Rosetta Code, task String-length: https://rosettacode.org/wiki/String_length (GNU Free Documentation License 1.3) *)
MODULE StringLength EXPORTS Main;

IMPORT IO, Fmt, Text;

VAR s: TEXT := "Foo bar baz";

BEGIN
  IO.Put("String length of s: " & Fmt.Int(Text.Length(s)) & "\n");
END StringLength.
