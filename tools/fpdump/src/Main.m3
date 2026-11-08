(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Reference side of the fingerprint test: reads cases from stdin and
   writes the results of CM3's Fingerprint, M3FP and Poly to stdout.
   m3j.tools.FpDump writes the same lines for the Java port.

   Each input line is an operation code followed by arguments separated
   by single blanks. HEX is a sequence of bytes as pairs of hexadecimal
   digits, or "-" for no bytes; INT is a decimal integer.

     t HEX        Fingerprint.FromText
     c HEX HEX    Fingerprint.Combine of the texts' fingerprints
     m HEX HEX    M3FP.Combine of the texts' fingerprints
     f HEX HEX    Fingerprint.FromChars (chars2, FromText (text1))
     x HEX INT    M3FP.ExtendByInt (FromText (text), INT)
     n INT        M3FP.FromInt
     p INT        Poly.Power
     r HEX HEX    Poly.Product of Poly.FromBytes of two 8-byte values

   Every output line starts with the input line, followed by " => " and
   the result. A fingerprint is written as its 8 bytes in hexadecimal,
   byte[0] first. *)

MODULE Main;

IMPORT Fingerprint, M3FP, Poly, Rd, Wr, Stdio, Text, Fmt, Scan, Thread;
IMPORT Lex, FloatMode;

<*FATAL Rd.Failure, Wr.Failure, Thread.Alerted, Lex.Error, FloatMode.Trap*>

VAR out := Stdio.stdout;

PROCEDURE HexDigit (c: CHAR): INTEGER =
  BEGIN
    CASE c OF
    | '0'..'9' => RETURN ORD (c) - ORD ('0');
    | 'a'..'f' => RETURN ORD (c) - ORD ('a') + 10;
    | 'A'..'F' => RETURN ORD (c) - ORD ('A') + 10;
    ELSE <*ASSERT FALSE*>
    END;
  END HexDigit;

PROCEDURE Bytes (hex: TEXT): REF ARRAY OF CHAR =
  VAR n: INTEGER;  r: REF ARRAY OF CHAR;
  BEGIN
    IF Text.Equal (hex, "-") THEN RETURN NEW (REF ARRAY OF CHAR, 0) END;
    n := Text.Length (hex) DIV 2;
    <*ASSERT 2 * n = Text.Length (hex)*>
    r := NEW (REF ARRAY OF CHAR, n);
    FOR i := 0 TO n - 1 DO
      r[i] := VAL (16 * HexDigit (Text.GetChar (hex, 2 * i))
                     + HexDigit (Text.GetChar (hex, 2 * i + 1)), CHAR);
    END;
    RETURN r;
  END Bytes;

PROCEDURE ToText (READONLY a: ARRAY OF CHAR): TEXT =
  BEGIN
    RETURN Text.FromChars (a);
  END ToText;

PROCEDURE FP (hex: TEXT): Fingerprint.T =
  BEGIN
    RETURN Fingerprint.FromText (ToText (Bytes (hex)^));
  END FP;

PROCEDURE Byte (b: INTEGER): TEXT =
  BEGIN
    RETURN Fmt.Pad (Fmt.Int (b, 16), 2, '0');
  END Byte;

PROCEDURE FPText (READONLY fp: Fingerprint.T): TEXT =
  VAR t := "";
  BEGIN
    FOR i := FIRST (fp.byte) TO LAST (fp.byte) DO
      t := t & Byte (fp.byte[i]);
    END;
    RETURN t;
  END FPText;

PROCEDURE PolyText (READONLY p: Poly.T): TEXT =
  BEGIN
    RETURN Fmt.Int (p[0]) & " " & Fmt.Int (p[1]);
  END PolyText;

PROCEDURE PolyOf (hex: TEXT): Poly.T =
  VAR b := Bytes (hex);  bytes: Poly.Bytes;  p: Poly.T;
  BEGIN
    <*ASSERT NUMBER (b^) = 8*>
    FOR i := 0 TO 7 DO bytes[i] := ORD (b[i]) END;
    Poly.FromBytes (bytes, p);
    RETURN p;
  END PolyOf;

PROCEDURE Field (line: TEXT; VAR pos: INTEGER): TEXT =
  VAR start := pos;  len := Text.Length (line);
  BEGIN
    WHILE pos < len AND Text.GetChar (line, pos) # ' ' DO INC (pos) END;
    VAR f := Text.Sub (line, start, pos - start); BEGIN
      IF pos < len THEN INC (pos) END;
      RETURN f;
    END;
  END Field;

PROCEDURE DoLine (line: TEXT) =
  VAR
    pos := 0;
    op := Field (line, pos);
    a := Field (line, pos);
    b := Field (line, pos);
    res: TEXT;
    buf: M3FP.CharBuf;
    fp: Fingerprint.T;
  BEGIN
    IF Text.Equal (op, "t") THEN
      fp := FP (a);
      M3FP.ToChars (fp, buf);
      res := FPText (fp) & " " & Text.FromChars (buf)
               & " " & Fmt.Int (M3FP.ToInt (fp))
               & " " & Fmt.Int (Fingerprint.Hash (fp));
    ELSIF Text.Equal (op, "c") THEN
      res := FPText (Fingerprint.Combine (FP (a), FP (b)));
    ELSIF Text.Equal (op, "m") THEN
      res := FPText (M3FP.Combine (FP (a), FP (b)));
    ELSIF Text.Equal (op, "f") THEN
      res := FPText (Fingerprint.FromChars (Bytes (b)^, FP (a)));
    ELSIF Text.Equal (op, "x") THEN
      res := FPText (M3FP.ExtendByInt (FP (a), Scan.Int (b)));
    ELSIF Text.Equal (op, "n") THEN
      M3FP.FromInt (Scan.Int (a), fp);
      res := FPText (fp);
    ELSIF Text.Equal (op, "p") THEN
      res := PolyText (Poly.Power (Scan.Int (a)));
    ELSIF Text.Equal (op, "r") THEN
      res := PolyText (Poly.Product (PolyOf (a), PolyOf (b)));
    ELSE
      <*ASSERT FALSE*>
    END;
    Wr.PutText (out, line & " => " & res & "\n");
    Wr.Flush (out);
  END DoLine;

BEGIN
  TRY
    LOOP
      DoLine (Rd.GetLine (Stdio.stdin));
    END;
  EXCEPT
  | Rd.EndOfFile =>
  END;
END Main.
