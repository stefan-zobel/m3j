MODULE Main;
IMPORT IO, Fmt, Wr, Rd, TextWr, TextRd, Text;
<*FATAL ANY*>
VAR sum := 0; tw: TextWr.T; trd: TextRd.T; line: TEXT;
BEGIN
  FOR r := 1 TO 40 DO
    tw := TextWr.New ();
    FOR i := 1 TO 100000 DO
      Wr.PutChar (tw, VAL (ORD ('a') + i MOD 26, CHAR));
      IF i MOD 80 = 0 THEN Wr.PutChar (tw, '\n') END;
    END;
    FOR i := 1 TO 8000 DO
      Wr.PutText (tw, Fmt.Int (i));
      Wr.PutText (tw, " line of text\n");
    END;
    trd := TextRd.New (TextWr.ToText (tw));
    FOR i := 1 TO 100000 + 100000 DIV 80 DO
      INC (sum, ORD (Rd.GetChar (trd)));
    END;
    WHILE NOT Rd.EOF (trd) DO
      line := Rd.GetLine (trd);
      INC (sum, Text.Length (line) + r);
    END;
  END;
  IO.Put (Fmt.Int (sum) & "\n");
END Main.
