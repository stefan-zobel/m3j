(* MD5.FromFile and SHA256.FromFile read to the end of a reader from where it
   stands, for inputs around the sizes of the blocks that the algorithms and
   the readers use, with characters of all values. *)

MODULE Main;

IMPORT FileRd, FileWr, Fmt, FS, IO, MD5, Rd, SHA256, Text, TextRd, Wr;

<*FATAL ANY*>

CONST
  File = "hash-streams.tmp";
  Sizes = ARRAY OF CARDINAL {0, 1, 55, 56, 63, 64, 65, 4095, 4096, 4097,
                             65535, 65536, 65537, 131072, 131073};

VAR data := NEW (REF ARRAY OF CHAR, Sizes[LAST (Sizes)]);

PROCEDURE Show (what: TEXT; n: CARDINAL; md5, sha: TEXT) =
  BEGIN
    IO.Put (what & " " & Fmt.Int (n) & " " & md5 & " " & sha & "\n");
  END Show;

PROCEDURE Sized (n: CARDINAL) =
  VAR
    t := Text.FromChars (SUBARRAY (data^, 0, n));
    md5, sha: TEXT;
    wr: Wr.T;
    rd: Rd.T;
  BEGIN
    md5 := MD5.FromFile (TextRd.New (t));
    sha := SHA256.FromFile (TextRd.New (t));
    Show ("text", n, md5, sha);

    wr := FileWr.Open (File);
    Wr.PutString (wr, SUBARRAY (data^, 0, n));
    Wr.Close (wr);
    rd := FileRd.Open (File);
    md5 := MD5.FromFile (rd);
    Rd.Close (rd);
    rd := FileRd.Open (File);
    sha := SHA256.FromFile (rd);
    Rd.Close (rd);
    FS.DeleteFile (File);
    Show ("file", n, md5, sha);

    IF n > 0 THEN
      rd := TextRd.New (t);
      EVAL Rd.GetChar (rd);
      md5 := MD5.FromFile (rd);
      rd := TextRd.New (t);
      EVAL Rd.GetChar (rd);
      sha := SHA256.FromFile (rd);
      Show ("rest", n, md5, sha);
    END;
  END Sized;

BEGIN
  FOR i := 0 TO LAST (data^) DO
    data[i] := VAL ((i * 7 + i DIV 256) MOD 256, CHAR);
  END;
  FOR i := FIRST (Sizes) TO LAST (Sizes) DO
    Sized (Sizes[i]);
  END;
END Main.
