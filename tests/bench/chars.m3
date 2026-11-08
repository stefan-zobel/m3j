(* Arrays of CHAR: words of a generated text are counted in a hash table of fixed arrays. *)
MODULE Main;
IMPORT IO, Fmt, Word;

CONST N = 4000000;  Size = 65536;

TYPE Entry = RECORD word: ARRAY [0 .. 15] OF CHAR; len, count: INTEGER END;

VAR
  buf := NEW (REF ARRAY OF CHAR, N);
  tbl := NEW (REF ARRAY OF Entry, Size);
  seed: INTEGER := 7;

PROCEDURE Random (n: INTEGER): INTEGER =
  BEGIN
    seed := (seed * 1103515245 + 12345) MOD 2147483648;
    RETURN seed DIV 65536 MOD n;
  END Random;

PROCEDURE Fill () =
  VAR i := 0;  len: INTEGER;
  BEGIN
    WHILE i < N DO
      len := Random (5) + 1;
      FOR k := 1 TO len DO
        IF i < N THEN buf[i] := VAL (ORD ('a') + Random (4), CHAR); INC (i) END;
      END;
      IF i < N THEN buf[i] := ' '; INC (i) END;
    END;
  END Fill;

PROCEDURE Add (READONLY w: ARRAY OF CHAR): INTEGER =
  VAR h: Word.T := 0;  k: INTEGER;
  BEGIN
    FOR i := 0 TO LAST (w) DO h := Word.Plus (Word.Times (h, 31), ORD (w[i])) END;
    k := Word.Mod (h, Size);
    LOOP
      WITH e = tbl[k] DO
        IF e.len = 0 THEN
          SUBARRAY (e.word, 0, NUMBER (w)) := w;  e.len := NUMBER (w);  e.count := 1;
          RETURN 1;
        ELSIF e.len = NUMBER (w) AND SUBARRAY (e.word, 0, e.len) = w THEN
          INC (e.count);
          RETURN 0;
        END;
      END;
      k := (k + 1) MOD Size;
    END;
  END Add;

VAR start, distinct, total, most := 0;
BEGIN
  Fill ();
  FOR r := 1 TO 5 DO
    FOR i := 0 TO Size - 1 DO tbl[i].len := 0 END;
    distinct := 0;
    start := 0;
    FOR i := 0 TO N - 1 DO
      IF buf[i] = ' ' THEN
        IF i > start THEN INC (distinct, Add (SUBARRAY (buf^, start, i - start))) END;
        start := i + 1;
      END;
    END;
    INC (total, distinct);
  END;
  FOR i := 0 TO Size - 1 DO most := MAX (most, tbl[i].count) END;
  IO.Put (Fmt.Int (total) & " " & Fmt.Int (most) & "\n");
END Main.
