(* Readers and writers: characters and texts written to a file and to a
   text writer, and read back with GetChar and GetLine. *)
MODULE Main;
IMPORT IO, Fmt, Wr, Rd, FileWr, FileRd, TextWr, TextRd, Text, FS;
<*FATAL ANY*>

CONST File = "rdwr.tmp";

VAR
  sum := 0;
  wr: Wr.T;
  rd: Rd.T;
  tw: TextWr.T;
  line: TEXT;

BEGIN
  wr := FileWr.Open (File);
  FOR i := 1 TO 4000000 DO
    Wr.PutChar (wr, VAL (ORD ('a') + i MOD 26, CHAR));
    IF i MOD 80 = 0 THEN Wr.PutChar (wr, '\n') END;
  END;
  FOR i := 1 TO 300000 DO
    Wr.PutText (wr, Fmt.Int (i));
    Wr.PutText (wr, " line of text\n");
  END;
  Wr.Close (wr);

  rd := FileRd.Open (File);
  FOR i := 1 TO 4000000 + 4000000 DIV 80 DO
    INC (sum, ORD (Rd.GetChar (rd)));
  END;
  WHILE NOT Rd.EOF (rd) DO
    line := Rd.GetLine (rd);
    INC (sum, Text.Length (line));
  END;
  Rd.Close (rd);
  FS.DeleteFile (File);

  FOR r := 1 TO 20 DO
    tw := TextWr.New ();
    FOR i := 1 TO 20000 DO
      Wr.PutText (tw, "word ");
      Wr.PutChar (tw, VAL (ORD ('0') + i MOD 10, CHAR));
      IF i MOD 10 = 0 THEN Wr.PutChar (tw, '\n') END;
    END;
    rd := TextRd.New (TextWr.ToText (tw));
    WHILE NOT Rd.EOF (rd) DO
      line := Rd.GetLine (rd);
      INC (sum, Text.Length (line) + r);
    END;
  END;
  IO.Put (Fmt.Int (sum) & "\n");
END Main.
