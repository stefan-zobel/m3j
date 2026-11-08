(* Exceptions raised and handled in a loop, and TRY FINALLY. *)
MODULE Main;
IMPORT IO, Fmt;

EXCEPTION Odd (INTEGER);

VAR finals := 0;

PROCEDURE Check (i: INTEGER) RAISES {Odd} =
  BEGIN
    TRY
      IF i MOD 2 = 1 THEN RAISE Odd (i) END;
    FINALLY
      INC (finals);
    END;
  END Check;

VAR sum := 0;
BEGIN
  FOR i := 1 TO 3000000 DO
    TRY
      Check (i);
      INC (sum);
    EXCEPT
    | Odd (v) => INC (sum, v MOD 3);
    END;
  END;
  IO.Put (Fmt.Int (sum) & " " & Fmt.Int (finals) & "\n");
END Main.
