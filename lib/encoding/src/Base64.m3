(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Base64;

IMPORT Text;

CONST
  Common = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  Spaces = SET OF CHAR {' ', '\t', '\r', '\n'};

VAR
  symbols: ARRAY Alphabet OF ARRAY [0 .. 63] OF CHAR;
  values: ARRAY Alphabet OF ARRAY CHAR OF INTEGER;

PROCEDURE Encode (t: TEXT; a := Alphabet.Standard; pad := TRUE): TEXT =
  VAR
    n := Text.Length (t);
    b := NEW (REF ARRAY OF CHAR, n);
    r := NEW (REF ARRAY OF CHAR, (n + 2) DIV 3 * 4);
    i, j := 0;
    v: INTEGER;

  PROCEDURE Put (x: INTEGER) =
    BEGIN
      r[j] := symbols[a][x];
      INC (j);
    END Put;

  PROCEDURE Pad () =
    BEGIN
      IF pad THEN
        r[j] := '=';
        INC (j);
      END;
    END Pad;

  BEGIN
    <*ASSERT NOT Text.HasWideChars (t)*>
    Text.SetChars (b^, t);
    WHILE n - i >= 3 DO
      v := ORD (b[i]) * 65536 + ORD (b[i + 1]) * 256 + ORD (b[i + 2]);
      Put (v DIV 262144);
      Put (v DIV 4096 MOD 64);
      Put (v DIV 64 MOD 64);
      Put (v MOD 64);
      INC (i, 3);
    END;
    IF n - i = 1 THEN
      v := ORD (b[i]);
      Put (v DIV 4);
      Put (v MOD 4 * 16);
      Pad ();
      Pad ();
    ELSIF n - i = 2 THEN
      v := ORD (b[i]) * 256 + ORD (b[i + 1]);
      Put (v DIV 1024);
      Put (v DIV 16 MOD 64);
      Put (v MOD 16 * 4);
      Pad ();
    END;
    RETURN Text.FromChars (SUBARRAY (r^, 0, j));
  END Encode;

PROCEDURE Decode (t: TEXT; VAR r: TEXT; a := Alphabet.Standard;
                  p := Padding.Required; ignoreSpace := FALSE): BOOLEAN =
  VAR
    n := Text.Length (t);
    s := NEW (REF ARRAY OF CHAR, n);
    m := n;                     (* the characters that count *)
    pads, len, rest: INTEGER;
    o: REF ARRAY OF CHAR;
    i, j := 0;
    v: INTEGER;

  (* The value of the next character, or -1. *)
  PROCEDURE Next (): INTEGER =
    BEGIN
      INC (i);
      RETURN values[a][s[i - 1]];
    END Next;

  PROCEDURE Put (x: INTEGER) =
    BEGIN
      o[j] := VAL (x, CHAR);
      INC (j);
    END Put;

  VAR c1, c2, c3, c4: INTEGER;
  BEGIN
    IF Text.HasWideChars (t) THEN RETURN FALSE END;
    Text.SetChars (s^, t);
    IF ignoreSpace THEN
      m := 0;
      FOR k := 0 TO n - 1 DO
        IF NOT s[k] IN Spaces THEN
          s[m] := s[k];
          INC (m);
        END;
      END;
    END;

    pads := 0;
    WHILE pads < m AND s[m - 1 - pads] = '=' DO INC (pads) END;
    len := m - pads;
    rest := len MOD 4;
    IF rest = 1 THEN RETURN FALSE END;
    CASE p OF
    | Padding.Required =>
        IF pads # (4 - rest) MOD 4 THEN RETURN FALSE END;
    | Padding.Forbidden =>
        IF pads # 0 THEN RETURN FALSE END;
    | Padding.Optional =>
        IF pads # 0 AND pads # (4 - rest) MOD 4 THEN RETURN FALSE END;
    END;

    o := NEW (REF ARRAY OF CHAR, len DIV 4 * 3 + MAX (rest - 1, 0));
    FOR k := 1 TO len DIV 4 DO
      c1 := Next ();
      c2 := Next ();
      c3 := Next ();
      c4 := Next ();
      IF c1 < 0 OR c2 < 0 OR c3 < 0 OR c4 < 0 THEN RETURN FALSE END;
      v := c1 * 262144 + c2 * 4096 + c3 * 64 + c4;
      Put (v DIV 65536);
      Put (v DIV 256 MOD 256);
      Put (v MOD 256);
    END;
    IF rest = 2 THEN
      c1 := Next ();
      c2 := Next ();
      IF c1 < 0 OR c2 < 0 OR c2 MOD 16 # 0 THEN RETURN FALSE END;
      Put (c1 * 4 + c2 DIV 16);
    ELSIF rest = 3 THEN
      c1 := Next ();
      c2 := Next ();
      c3 := Next ();
      IF c1 < 0 OR c2 < 0 OR c3 < 0 OR c3 MOD 4 # 0 THEN RETURN FALSE END;
      Put (c1 * 4 + c2 DIV 16);
      Put (c2 MOD 16 * 16 + c3 DIV 4);
    END;
    r := Text.FromChars (o^);
    RETURN TRUE;
  END Decode;

PROCEDURE Fill (a: Alphabet; c62, c63: CHAR) =
  BEGIN
    FOR c := FIRST (CHAR) TO LAST (CHAR) DO values[a][c] := -1 END;
    FOR i := 0 TO 61 DO symbols[a][i] := Text.GetChar (Common, i) END;
    symbols[a][62] := c62;
    symbols[a][63] := c63;
    FOR i := 0 TO 63 DO values[a][symbols[a][i]] := i END;
  END Fill;

BEGIN
  Fill (Alphabet.Standard, '+', '/');
  Fill (Alphabet.Url, '-', '_');
END Base64.
