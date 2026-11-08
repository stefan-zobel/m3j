(* Rosetta Code, task Tokenize-a-string: https://rosettacode.org/wiki/Tokenize_a_string (GNU Free Documentation License 1.3) *)
MODULE Tokenize EXPORTS Main;

IMPORT IO, TextConv;

TYPE Texts = REF ARRAY OF TEXT;

VAR tokens: Texts;
    string := "Hello,How,Are,You,Today";
    sep := SET OF CHAR {','};

BEGIN
  tokens := NEW(Texts, TextConv.ExplodedSize(string, sep));
  TextConv.Explode(string, tokens^, sep);
  FOR i := FIRST(tokens^) TO LAST(tokens^) DO
    IO.Put(tokens[i] & ".");
  END;
  IO.Put("\n");
END Tokenize.
