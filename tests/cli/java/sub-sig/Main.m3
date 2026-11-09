MODULE Main;

IMPORT R;

TYPE S = R.T OBJECT METHODS
  run (n: INTEGER) := Run;
END;

PROCEDURE Run (<*UNUSED*> self: S; <*UNUSED*> n: INTEGER) =
  BEGIN
  END Run;

VAR s := NEW (S);

BEGIN
  R.run (s);
END Main.
