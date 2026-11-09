MODULE Main;

IMPORT R;

TYPE S = R.T OBJECT END;

VAR s: S := NIL;

BEGIN
  s := NEW (S);
END Main.
