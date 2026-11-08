MODULE Main;

IMPORT IO, IsTypeVal, ArrayAssign, RaiseArgs, Misc;

BEGIN
  IO.PutInt (IsTypeVal.Run () + ArrayAssign.Run () + RaiseArgs.Run () + Misc.Run ());
  IO.Put ("\n");
END Main.
