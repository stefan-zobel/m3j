MODULE Main;

IMPORT B, Java;

TYPE O = OBJECT METHODS m (): Java.Int := B.Hash; END;

VAR o := NEW (O);

BEGIN
  EVAL o.m ();
END Main.
