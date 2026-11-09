MODULE Refused;

(* Errors that the checker has to find and survive. *)

IMPORT Long, Word;

CONST
  A = Long.Plus (1L, 2);
  B = Word.Plus (1, 2L);
  C = Long.Shift (1L, 2L);
  D = Long.Insert (1L, 2L, 3L, 4);
  E = Long.Not (1);
  F = Word.Divide (1, 0);
  G = Long.Mod (1L, 0L);
  H = Long.Extract (1L, -1, 4);
  I = Word.Shift (1, 1000);
  J = Long.Rotate (1L, -1000);

PROCEDURE P () =
  <*FATAL Not_Declared, OR, Word.OR*>
  BEGIN
  END P;

VAR
  t := 1 & "x";
  u := "x" & 1.0;

BEGIN
  IF t = u OR A = 0L OR B = 0 OR C = 0L OR D = 0L OR E = 0L OR F = 0
     OR G = 0L OR H = 0L OR I = 0 OR J = 0L THEN
    P ();
  END;
END Refused.
