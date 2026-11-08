(* Checked runtime errors that the front-end option -NoChecks turns off: a
   value out of the range of a subrange, and a function that ends without
   RETURN (it then returns the default value of its result type). *)

MODULE Checks EXPORTS Main;

IMPORT IO, Fmt, Params;

VAR
  n := 7 + Params.Count;
  s: [0 .. 5];

PROCEDURE NoReturn (k: INTEGER): INTEGER =
  BEGIN
    IF k < 0 THEN RETURN k END;
  END NoReturn;

BEGIN
  s := n;
  IO.Put("s " & Fmt.Int(s) & "\n");
  IO.Put("result " & Fmt.Int(NoReturn(n)) & "\n");
END Checks.
