(* Rosetta Code, task Reverse-a-string: https://rosettacode.org/wiki/Reverse_a_string (GNU Free Documentation License 1.3) *)
MODULE Reverse EXPORTS Main;

IMPORT IO, Text;

PROCEDURE String(item: TEXT): TEXT =
  VAR result: TEXT := "";
  BEGIN
    FOR i := Text.Length(item) - 1 TO 0 BY - 1 DO
      result := Text.Cat(result, Text.FromChar(Text.GetChar(item, i)));
    END;
    RETURN result;
  END String;

BEGIN
  IO.Put(String("Foobarbaz") & "\n");
END Reverse.
