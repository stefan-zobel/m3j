(* FOR loops near the ends of INTEGER and of the range of a 32-bit int,
   whose last increment does not overflow. The back end counts a loop in
   an int where all values of the counter fit; at the ends of INTEGER,
   the computation of that range must not overflow itself. *)

MODULE Main;

IMPORT IO, Fmt;

TYPE
  High = [LAST(INTEGER) - 5 .. LAST(INTEGER)];
  Low = [FIRST(INTEGER) .. FIRST(INTEGER) + 5];

VAR
  h: High := LAST(INTEGER) - 1;
  l: Low := FIRST(INTEGER) + 1;
  n: INTEGER;

PROCEDURE Put (tag: TEXT; i: INTEGER) =
  BEGIN
    IO.Put(tag & " " & Fmt.Int(i) & "\n");
    INC(n);
  END Put;

PROCEDURE Count (tag: TEXT) =
  BEGIN
    IO.Put(tag & " count " & Fmt.Int(n) & "\n");
    n := 0;
  END Count;

BEGIN
  FOR i := LAST(INTEGER) TO LAST(INTEGER) - 2 BY -1 DO Put("a", i) END;
  Count("a");
  FOR i := LAST(INTEGER) - 10 TO LAST(INTEGER) - 5 BY 2 DO Put("b", i) END;
  Count("b");
  FOR i := FIRST(INTEGER) TO FIRST(INTEGER) + 2 DO Put("c", i) END;
  Count("c");
  FOR i := FIRST(INTEGER) + 9 TO FIRST(INTEGER) + 4 BY -2 DO Put("d", i) END;
  Count("d");
  FOR i := LAST(INTEGER) TO FIRST(INTEGER) DO Put("e", i) END;
  Count("e");
  FOR i := FIRST(INTEGER) TO LAST(INTEGER) BY -1 DO Put("f", i) END;
  Count("f");
  FOR i := -2147483648 TO -2147483646 DO Put("g", i) END;
  Count("g");
  FOR i := 2147483647 TO 2147483645 BY -1 DO Put("h", i) END;
  Count("h");
  FOR i := 2147483645 TO 2147483647 BY 2 DO Put("i", i) END;
  Count("i");
  FOR i := -2147483647 TO -2147483649 BY -1 DO Put("j", i) END;
  Count("j");
  FOR i := h TO h - 3 BY -1 DO Put("k", i) END;
  Count("k");
  FOR i := l TO l + 2 DO Put("l", i) END;
  Count("l");
  FOR i := LAST(LONGINT) TO LAST(LONGINT) - 2L BY -1L DO Put("m", VAL(i, INTEGER)) END;
  Count("m");
  FOR c := VAL(253, CHAR) TO LAST(CHAR) DO Put("n", ORD(c)) END;
  Count("n");
  FOR c := VAL(2, CHAR) TO FIRST(CHAR) BY -1 DO Put("o", ORD(c)) END;
  Count("o");
END Main.
