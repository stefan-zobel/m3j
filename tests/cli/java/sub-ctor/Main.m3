MODULE Main;

IMPORT B;

TYPE S = B.T OBJECT END;

VAR s: S := NIL;

BEGIN
  s := NEW (S);
END Main.
