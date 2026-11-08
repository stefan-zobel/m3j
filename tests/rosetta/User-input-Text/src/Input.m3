(* Rosetta Code, task User-input-Text: https://rosettacode.org/wiki/User_input_Text (GNU Free Documentation License 1.3) *)
MODULE Input EXPORTS Main;

IMPORT IO, Fmt;

VAR string: TEXT;
    number: INTEGER;

BEGIN
  IO.Put("Enter a string: ");
  string := IO.GetLine();
  IO.Put("Enter a number: ");
  number := IO.GetInt();
  IO.Put("You entered: " & string & " and " & Fmt.Int(number) & "\n");
END Input.
