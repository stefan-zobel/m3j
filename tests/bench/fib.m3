(* Recursive calls. *)
MODULE Main;
IMPORT IO, Fmt;

PROCEDURE Fib (n: INTEGER): INTEGER =
  BEGIN
    IF n < 2 THEN RETURN n ELSE RETURN Fib (n - 1) + Fib (n - 2) END;
  END Fib;

BEGIN
  IO.Put (Fmt.Int (Fib (40)) & "\n");
END Main.
