(* Rosetta Code, task Non-decimal-radices-Output: https://rosettacode.org/wiki/Non_decimal_radices_Output (GNU Free Documentation License 1.3) *)
MODULE Conv EXPORTS Main;

IMPORT IO, Fmt;

BEGIN
  FOR i := 1 TO 33 DO
    IO.Put(Fmt.Int(i, base := 10) & " ");
    IO.Put(Fmt.Int(i, base := 16) & " ");
    IO.Put(Fmt.Int(i, base := 8) & " ");
    IO.Put("\n");
  END;
END Conv.
