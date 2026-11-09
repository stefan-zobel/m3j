MODULE Main;

IMPORT B;

TYPE S = B.T OBJECT METHODS
  write (x: REFANY) := W;
END;

PROCEDURE W (<*UNUSED*> self: S; <*UNUSED*> x: REFANY) =
  BEGIN
  END W;

VAR s: S := NIL;

BEGIN
  EVAL s;
END Main.
