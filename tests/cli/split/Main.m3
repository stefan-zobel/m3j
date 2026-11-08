(* Uses the interface Two of the source package pkg (-pkg pkg), whose
   procedures two modules of one directory export: Two.m3 and TwoB.m3
   (as M3AST_SM_F.m3 and M3AST_SM_Walk.m3 of m3tk export M3AST_SM_F).
   The compiler takes both; not TwoOther.m3, which exports Two too, in
   another directory (as the modules of m3tk for POSIX and for Windows). *)

MODULE Main;

IMPORT IO, Two;

BEGIN
  IO.Put(Two.A() & " " & Two.B() & "\n");
END Main.
