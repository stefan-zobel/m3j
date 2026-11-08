(* Text.ForAllDo: the characters that the callbacks get, for texts of
   CHAR and of WIDECHAR values, literals, empty texts and NIL, texts made
   by Cat, Sub, FromChars and FromWideChars, nested procedures as
   callbacks, and exceptions raised by a callback. How a text is split
   into calls depends on how the implementation represents it, so the
   kinds and numbers of calls are printed only for single literals and
   for empty texts and NIL; for the others, the characters in sequence. *)

MODULE Main;

IMPORT Fmt, IO, Text;

EXCEPTION Stop (INTEGER);

VAR
  buf: ARRAY [0 .. 999] OF INTEGER;
  len := 0;
  narrowCalls := 0;
  wideCalls := 0;
  emptyCalls := 0;

PROCEDURE Narrow (READONLY a: ARRAY OF CHAR) =
  BEGIN
    INC (narrowCalls);
    IF NUMBER (a) = 0 THEN INC (emptyCalls) END;
    FOR i := FIRST (a) TO LAST (a) DO
      buf[len] := ORD (a[i]);
      INC (len);
    END;
  END Narrow;

PROCEDURE Wide (READONLY a: ARRAY OF WIDECHAR) =
  BEGIN
    INC (wideCalls);
    IF NUMBER (a) = 0 THEN INC (emptyCalls) END;
    FOR i := FIRST (a) TO LAST (a) DO
      buf[len] := ORD (a[i]);
      INC (len);
    END;
  END Wide;

PROCEDURE Reset () =
  BEGIN
    len := 0;
    narrowCalls := 0;
    wideCalls := 0;
    emptyCalls := 0;
  END Reset;

PROCEDURE Chars (): TEXT =
  VAR r := "";
  BEGIN
    FOR i := 0 TO len - 1 DO
      IF buf[i] >= 32 AND buf[i] < 127 THEN
        r := r & Text.FromChar (VAL (buf[i], CHAR));
      ELSE
        r := r & "<" & Fmt.Int (buf[i], 16) & ">";
      END;
    END;
    RETURN r;
  END Chars;

PROCEDURE Same (t: TEXT): BOOLEAN =
  (* Do the characters seen equal those of t? *)
  BEGIN
    IF len # Text.Length (t) THEN RETURN FALSE END;
    FOR i := 0 TO len - 1 DO
      IF buf[i] # ORD (Text.GetWideChar (t, i)) THEN RETURN FALSE END;
    END;
    RETURN TRUE;
  END Same;

PROCEDURE Calls (name: TEXT; t: TEXT) =
  <*FATAL ANY*>
  BEGIN
    Reset ();
    Text.ForAllDo (t, Narrow, Wide);
    IO.Put (name & ": narrow " & Fmt.Int (narrowCalls) & " wide "
              & Fmt.Int (wideCalls) & " empty " & Fmt.Int (emptyCalls)
              & " [" & Chars () & "]\n");
  END Calls;

PROCEDURE Seq (name: TEXT; t: TEXT) =
  <*FATAL ANY*>
  BEGIN
    Reset ();
    Text.ForAllDo (t, Narrow, Wide);
    IO.Put (name & ": " & Fmt.Int (len) & " [" & Chars () & "] "
              & Fmt.Bool (Same (t)) & "\n");
  END Seq;

PROCEDURE Long (): TEXT =
  VAR t := "";
  BEGIN
    FOR i := 0 TO 39 DO
      t := t & Text.FromChar (VAL (ORD ('a') + i MOD 26, CHAR));
      IF i MOD 7 = 3 THEN
        t := t & Text.FromWideChar (VAL (16_100 + i, WIDECHAR));
      END;
    END;
    RETURN t;
  END Long;

PROCEDURE Nested (t: TEXT; skip: INTEGER): INTEGER =
  VAR sum := 0; seen := 0;
  PROCEDURE N (READONLY a: ARRAY OF CHAR) =
    BEGIN
      FOR i := FIRST (a) TO LAST (a) DO
        IF seen >= skip THEN INC (sum, ORD (a[i])) END;
        INC (seen);
      END;
    END N;
  PROCEDURE W (READONLY a: ARRAY OF WIDECHAR) =
    BEGIN
      FOR i := FIRST (a) TO LAST (a) DO
        IF seen >= skip THEN INC (sum, ORD (a[i])) END;
        INC (seen);
      END;
    END W;
  <*FATAL ANY*>
  BEGIN
    Text.ForAllDo (t, N, W);
    RETURN sum * 1000 + seen;
  END Nested;

PROCEDURE StopAt (READONLY a: ARRAY OF CHAR) RAISES {Stop} =
  BEGIN
    FOR i := FIRST (a) TO LAST (a) DO
      IF a[i] = 'x' THEN RAISE Stop (len) END;
      buf[len] := ORD (a[i]);
      INC (len);
    END;
  END StopAt;

PROCEDURE StopAtWide (READONLY a: ARRAY OF WIDECHAR) RAISES {Stop} =
  BEGIN
    FOR i := FIRST (a) TO LAST (a) DO
      IF a[i] = W'x' THEN RAISE Stop (len) END;
      buf[len] := ORD (a[i]);
      INC (len);
    END;
  END StopAtWide;

PROCEDURE Raise (name: TEXT; t: TEXT) =
  BEGIN
    Reset ();
    TRY
      Text.ForAllDo (t, StopAt, StopAtWide);
      IO.Put (name & ": no exception, " & Fmt.Int (len) & "\n");
    EXCEPT
    | Stop (n) => IO.Put (name & ": Stop " & Fmt.Int (n) & " [" & Chars () & "]\n");
    ELSE
      IO.Put (name & ": other exception\n");
    END;
  END Raise;

VAR
  chars := ARRAY [0 .. 4] OF CHAR {'h', 'e', 'l', 'l', 'o'};
  wchars := ARRAY [0 .. 3] OF WIDECHAR {W'w', W'\x2603', W'i', W'\xFFFF'};
  long := Long ();

BEGIN
  Calls ("literal", "abc");
  Calls ("one char", "z");
  Calls ("empty", "");
  Calls ("nil", NIL);
  Calls ("wide literal", W"a\x1234b");
  Calls ("high chars", "\000\177\200\377");

  Seq ("cat", "abc" & "def");
  Seq ("cat empty", "" & "abc" & "");
  Seq ("cat wide", "ab" & W"\x0100" & "cd");
  Seq ("sub", Text.Sub ("0123456789", 3, 4));
  Seq ("sub of cat", Text.Sub ("01234" & "56789", 3, 4));
  Seq ("sub to end", Text.Sub ("01234" & "56789", 7, 100));
  Seq ("sub empty", Text.Sub ("01234", 5, 3));
  Seq ("from chars", Text.FromChars (chars));
  Seq ("from sub chars", Text.FromChars (SUBARRAY (chars, 1, 3)));
  Seq ("from wide chars", Text.FromWideChars (wchars));
  Seq ("from char", Text.FromChar ('\377'));
  Seq ("from wide char", Text.FromWideChar (W'\x00FF'));
  Seq ("long", long);
  Seq ("sub of long", Text.Sub (long, 9, 20));

  IO.Put ("nested: " & Fmt.Int (Nested ("hello", 0)) & " "
            & Fmt.Int (Nested ("hello" & W"\x0101", 2)) & " "
            & Fmt.Int (Nested (long, 30)) & "\n");

  Raise ("raise narrow", "abxcd");
  Raise ("raise wide", W"ab\x0400xcd");
  Raise ("raise cat", "abc" & "dexf");
  Raise ("raise none", "abc");
END Main.
