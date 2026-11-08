(* Rosetta Code, task String-length: https://rosettacode.org/wiki/String_length (GNU Free Documentation License 1.3) *)
MODULE ByteLength EXPORTS Main;

IMPORT IO, Fmt, Text;

VAR s: TEXT := "Foo bar baz";

BEGIN
  IO.Put("Byte length of s: " & Fmt.Int((Text.Length(s) * BYTESIZE(s))) & "\n");
END ByteLength.
