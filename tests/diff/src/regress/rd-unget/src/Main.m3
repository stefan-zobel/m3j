(* UnGetChar, UnGetCharMulti, GetSub, Seek, GetLine and GetText on a file
   reader, around positions that are the end of a buffer of 4 KB or of 64 KB.
   The size of the buffer of the reader must not be visible. *)

MODULE Main;

IMPORT FileRd, FileWr, Fmt, FS, IO, Rd, Text, Wr;

<*FATAL ANY*>

CONST
  File = "rd-unget.tmp";
  Size = 200000;
  Lines = 2500;
  Boundaries = ARRAY OF CARDINAL {4096, 8192, 65536, 131072};

VAR
  small: ARRAY [0 .. 9] OF CHAR;
  big := NEW (REF ARRAY OF CHAR, 20000);

PROCEDURE At (i: INTEGER): CHAR =
  BEGIN
    RETURN VAL ((i * 7 + i DIV 256) MOD 256, CHAR);
  END At;

PROCEDURE Check (name: TEXT; b: CARDINAL; READONLY a: ARRAY OF CHAR;
                 from: INTEGER) =
  VAR bad := -1;
  BEGIN
    FOR i := LAST (a) TO 0 BY -1 DO
      IF a[i] # At (from + i) THEN bad := i END;
    END;
    IO.Put (name & " " & Fmt.Int (b) & " " & Fmt.Int (NUMBER (a)) & ": ");
    IF bad < 0 THEN
      IO.Put ("ok\n");
    ELSE
      IO.Put ("wrong at " & Fmt.Int (bad) & "\n");
    END;
  END Check;

PROCEDURE Skip (rd: Rd.T; n: INTEGER) =
  BEGIN
    FOR i := 1 TO n DO EVAL Rd.GetChar (rd) END;
  END Skip;

PROCEDURE Show (name: TEXT; b: CARDINAL; value: INTEGER) =
  BEGIN
    IO.Put (name & " " & Fmt.Int (b) & ": " & Fmt.Int (value) & "\n");
  END Show;

PROCEDURE ShowChar (name: TEXT; b: CARDINAL; c: CHAR; i: INTEGER) =
  BEGIN
    IO.Put (name & " " & Fmt.Int (b) & ": ");
    IF c = At (i) THEN IO.Put ("ok\n") ELSE IO.Put ("wrong\n") END;
  END ShowChar;

PROCEDURE Boundary (b: CARDINAL) =
  VAR rd: Rd.T;  n: CARDINAL;
  BEGIN
    (* the reader stands at the end of a buffer, and EOF has filled the next *)
    rd := FileRd.Open (File);
    Skip (rd, b);
    EVAL Rd.EOF (rd);
    Rd.UnGetChar (rd);
    n := Rd.GetSub (rd, small);
    Check ("unget, small GetSub", b, SUBARRAY (small, 0, n), b - 1);
    Rd.Close (rd);

    rd := FileRd.Open (File);
    Skip (rd, b);
    EVAL Rd.EOF (rd);
    Rd.UnGetChar (rd);
    n := Rd.GetSub (rd, big^);
    Check ("unget, big GetSub", b, SUBARRAY (big^, 0, n), b - 1);
    Rd.Close (rd);

    rd := FileRd.Open (File);
    Skip (rd, b);
    EVAL Rd.EOF (rd);
    Rd.UnGetChar (rd);
    ShowChar ("unget, GetChar", b, Rd.GetChar (rd), b - 1);
    ShowChar ("next GetChar", b, Rd.GetChar (rd), b);
    Rd.Close (rd);

    rd := FileRd.Open (File);
    Skip (rd, b);
    EVAL Rd.EOF (rd);
    Show ("UnGetCharMulti 8", b, Rd.UnGetCharMulti (rd, 8));
    n := Rd.GetSub (rd, small);
    Check ("after multi", b, SUBARRAY (small, 0, n), b - 8);
    Rd.Close (rd);

    (* an unget buffer that GetChar has filled, then a large GetSub *)
    rd := FileRd.Open (File);
    Skip (rd, b + 4);
    n := Rd.GetSub (rd, big^);
    Check ("big GetSub after GetChar", b, SUBARRAY (big^, 0, n), b + 4);
    Show ("UnGetCharMulti 8 after it", b, Rd.UnGetCharMulti (rd, 8));
    n := Rd.GetSub (rd, small);
    Check ("small GetSub after it", b, SUBARRAY (small, 0, n), b + 4 + 20000 - 8);
    Rd.Close (rd);

    (* a GetSub that ends at the end of a buffer *)
    rd := FileRd.Open (File);
    Skip (rd, b - 20000 MOD b);
    n := Rd.GetSub (rd, big^);
    Show ("Index at the boundary", b, Rd.Index (rd));
    Show ("UnGetCharMulti 3 there", b, Rd.UnGetCharMulti (rd, 3));
    ShowChar ("GetChar after it", b, Rd.GetChar (rd), Rd.Index (rd) - 1);
    Rd.Close (rd);

    (* Seek on both sides *)
    rd := FileRd.Open (File);
    Rd.Seek (rd, b - 2);
    n := Rd.GetSub (rd, small);
    Check ("Seek before", b, SUBARRAY (small, 0, n), b - 2);
    Show ("Index", b, Rd.Index (rd));
    Rd.Seek (rd, b + 5);
    ShowChar ("Seek after", b, Rd.GetChar (rd), b + 5);
    Rd.Seek (rd, 1);
    ShowChar ("Seek back", b, Rd.GetChar (rd), 1);
    Rd.Close (rd);
  END Boundary;

PROCEDURE Ends () =
  VAR rd := FileRd.Open (File);  t: TEXT;  n := 0;
  BEGIN
    Show ("UnGetCharMulti at the start", 0, Rd.UnGetCharMulti (rd, 3));
    Show ("Length", 0, Rd.Length (rd));
    Rd.Seek (rd, Size - 1);
    ShowChar ("GetChar at the end", 0, Rd.GetChar (rd), Size - 1);
    Show ("EOF", 0, ORD (Rd.EOF (rd)));
    Rd.UnGetChar (rd);
    ShowChar ("last character", 0, Rd.GetChar (rd), Size - 1);
    Show ("EOF again", 0, ORD (Rd.EOF (rd)));
    Rd.Seek (rd, 3);
    t := Rd.GetText (rd, 70000);
    FOR i := 0 TO Text.Length (t) - 1 DO
      IF Text.GetChar (t, i) = At (3 + i) THEN INC (n) END;
    END;
    Show ("GetText", 0, n);
    Rd.Close (rd);
  END Ends;

PROCEDURE LineReads () =
  VAR wr := FileWr.Open (File);  rd: Rd.T;  n := 0;
  BEGIN
    FOR i := 1 TO Lines DO
      Wr.PutText (wr, Fmt.Pad (Fmt.Int (i), 60, '.') & "\n");
    END;
    Wr.Close (wr);
    rd := FileRd.Open (File);
    WHILE NOT Rd.EOF (rd) DO
      INC (n);
      IF NOT Text.Equal (Rd.GetLine (rd), Fmt.Pad (Fmt.Int (n), 60, '.')) THEN
        IO.Put ("line " & Fmt.Int (n) & " differs\n");
      END;
    END;
    Rd.Close (rd);
    Show ("lines", 0, n);
  END LineReads;

VAR wr := FileWr.Open (File);

BEGIN
  FOR i := 0 TO Size - 1 DO Wr.PutChar (wr, At (i)) END;
  Wr.Close (wr);
  FOR i := FIRST (Boundaries) TO LAST (Boundaries) DO
    Boundary (Boundaries[i]);
  END;
  Ends ();
  LineReads ();
  FS.DeleteFile (File);
END Main.
