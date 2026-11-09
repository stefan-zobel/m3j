MODULE Values;

(* Pairs of labels with the same value: exactly these labels overlap if the
   value of a call of Long or Word is computed correctly. *)

IMPORT Long, Word;

VAR
  l := 0L;
  i := 0;

BEGIN
  CASE l OF
  | Long.Not (0L) => l := 1L;
  | -1L => l := 2L;
  | Long.Shift (1L, 40) => l := 3L;
  | 1099511627776L => l := 4L;
  | Long.Extract (16_ABCD00L, 8, 16) => l := 5L;
  | 16_ABCDL => l := 6L;
  | Long.Insert (0L, 16_FFL, 32, 8) => l := 7L;
  | 16_FF00000000L => l := 8L;
  ELSE
  END;
  CASE i OF
  | Word.Shift (1, 4) => i := 1;
  | 16 => i := 2;
  ELSE
  END;
END Values.
