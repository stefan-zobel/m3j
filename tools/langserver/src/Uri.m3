(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Uri;

IMPORT M3jOS, Text;

CONST
  Hex = ARRAY [0 .. 15] OF CHAR{'0', '1', '2', '3', '4', '5', '6', '7',
                                '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
  (* the characters that stand for themselves in the path of a URI *)
  Plain = SET OF CHAR{'a' .. 'z', 'A' .. 'Z', '0' .. '9', '-', '.', '_',
                      '~', '/', '!', '$', '&', '\'', '(', ')', '*', '+',
                      ',', ';', '=', '@'};

PROCEDURE HexValue (c: CHAR): INTEGER =
  BEGIN
    CASE c OF
    | '0' .. '9' => RETURN ORD(c) - ORD('0');
    | 'a' .. 'f' => RETURN ORD(c) - ORD('a') + 10;
    | 'A' .. 'F' => RETURN ORD(c) - ORD('A') + 10;
    ELSE
      RETURN -1;
    END;
  END HexValue;

PROCEDURE IsLetter (c: CHAR): BOOLEAN =
  BEGIN
    RETURN c IN SET OF CHAR{'a' .. 'z', 'A' .. 'Z'};
  END IsLetter;

PROCEDURE ToPath (uri: TEXT): TEXT =
  VAR
    len           := Text.Length(uri);
    start         := 5;          (* after "file:" *)
    out           := NEW(REF ARRAY OF CHAR, len);
    n             := 0;
    i   : INTEGER;
    c   : CHAR;
    hi, lo: INTEGER;
  BEGIN
    IF len < 5 OR NOT Text.Equal(Text.Sub(uri, 0, 5), "file:") THEN
      RETURN uri;
    END;
    (* "file://HOST/PATH": an empty host or "localhost" *)
    IF len >= 7 AND Text.Equal(Text.Sub(uri, 5, 2), "//") THEN
      start := 7;
      WHILE start < len AND Text.GetChar(uri, start) # '/' DO
        INC(start);
      END;
    END;
    i := start;
    WHILE i < len DO
      c := Text.GetChar(uri, i);
      IF c = '%' AND i + 2 < len THEN
        hi := HexValue(Text.GetChar(uri, i + 1));
        lo := HexValue(Text.GetChar(uri, i + 2));
        IF hi >= 0 AND lo >= 0 THEN
          c := VAL(hi * 16 + lo, CHAR);
          INC(i, 2);
        END;
      END;
      out[n] := c;
      INC(n);
      INC(i);
    END;
    IF M3jOS.Posix() THEN RETURN Text.FromChars(SUBARRAY(out^, 0, n)); END;
    (* Windows: "/d:/x/y" is "d:\x\y" *)
    VAR from := 0;
    BEGIN
      IF n >= 3 AND out[0] = '/' AND IsLetter(out[1]) AND out[2] = ':' THEN
        from := 1;
      END;
      FOR k := from TO n - 1 DO
        IF out[k] = '/' THEN out[k] := '\\'; END;
      END;
      RETURN Text.FromChars(SUBARRAY(out^, from, n - from));
    END;
  END ToPath;

PROCEDURE FromPath (path: TEXT): TEXT =
  VAR
    len          := Text.Length(path);
    out          := NEW(REF ARRAY OF CHAR, 3 * len + 1);
    n            := 0;
    c  : CHAR;
  PROCEDURE Put (ch: CHAR) =
    BEGIN
      out[n] := ch;
      INC(n);
    END Put;
  BEGIN
    IF len >= 2 AND IsLetter(Text.GetChar(path, 0))
         AND Text.GetChar(path, 1) = ':' THEN
      Put('/');
    END;
    FOR i := 0 TO len - 1 DO
      c := Text.GetChar(path, i);
      IF c = '\\' AND NOT M3jOS.Posix() THEN c := '/'; END;
      IF c IN Plain OR (c = ':' AND i = 1) THEN
        Put(c);
      ELSE
        Put('%');
        Put(Hex[ORD(c) DIV 16]);
        Put(Hex[ORD(c) MOD 16]);
      END;
    END;
    RETURN "file://" & Text.FromChars(SUBARRAY(out^, 0, n));
  END FromPath;

BEGIN
END Uri.
