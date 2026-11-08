(* Rosetta Code, task Integer-comparison: https://rosettacode.org/wiki/Integer_comparison (GNU Free Documentation License 1.3) *)
MODULE Main;

FROM IO  IMPORT Put, GetInt;
FROM Fmt IMPORT Int;

VAR a,b: INTEGER;

BEGIN
  a := GetInt();
  b := GetInt();
  IF a < b THEN
    Put(Int(a) & " is less than " & Int(b) & "\n");
  ELSIF a = b THEN
    Put(Int(a) & " is equal to " & Int(b) & "\n");
  ELSIF a > b THEN
    Put(Int(a) & " is greater than " & Int(b) & "\n");
  END;
END Main.
