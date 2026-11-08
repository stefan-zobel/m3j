(* Calls of the built-in procedures of Word and Long through constants
   whose values are these procedures (as m3middle's TInt has them). *)

MODULE Main;

IMPORT IO, Fmt, Word, Long;

CONST
  And    = Word.And;
  Or     = Word.Or;
  Xor    = Word.Xor;
  Plus   = Word.Plus;
  Times  = Word.Times;
  LShift = Word.LeftShift;
  RShift = Word.RightShift;
  Extr   = Word.Extract;
  Ins    = Word.Insert;
  LT     = Word.LT;
  LAnd   = Long.And;
  LShl   = Long.Shift;
  Alias  = And;  (* a constant of a constant *)

VAR x := 16_12345678;  y := 16_0F0F;  l := 16_FF00L;

BEGIN
  IO.Put (Fmt.Unsigned (And (x, y)) & "\n");
  IO.Put (Fmt.Unsigned (Or (x, y)) & "\n");
  IO.Put (Fmt.Unsigned (Xor (x, y)) & "\n");
  IO.Put (Fmt.Unsigned (Plus (x, y)) & "\n");
  IO.Put (Fmt.Unsigned (Times (x, y)) & "\n");
  IO.Put (Fmt.Unsigned (LShift (x, 8)) & "\n");
  IO.Put (Fmt.Unsigned (RShift (x, 8)) & "\n");
  IO.Put (Fmt.Unsigned (Extr (x, 4, 8)) & "\n");
  IO.Put (Fmt.Unsigned (Ins (x, 16_AB, 0, 8)) & "\n");
  IO.Put (Fmt.Bool (LT (x, y)) & "\n");
  IO.Put (Fmt.LongUnsigned (LAnd (l, 16_0FF0L)) & "\n");
  IO.Put (Fmt.LongUnsigned (LShl (l, 4)) & "\n");
  IO.Put (Fmt.Unsigned (Alias (x, 16_FF)) & "\n");
END Main.
