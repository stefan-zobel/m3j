(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Hex;

IMPORT Text;

CONST
  Digits = ARRAY [0 .. 15] OF CHAR {'0', '1', '2', '3', '4', '5', '6', '7',
                                    '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

PROCEDURE Encode (t: TEXT): TEXT =
  VAR
    n := Text.Length (t);
    b := NEW (REF ARRAY OF CHAR, n);
    r := NEW (REF ARRAY OF CHAR, 2 * n);
  BEGIN
    <*ASSERT NOT Text.HasWideChars (t)*>
    Text.SetChars (b^, t);
    FOR i := 0 TO n - 1 DO
      r[2 * i] := Digits[ORD (b[i]) DIV 16];
      r[2 * i + 1] := Digits[ORD (b[i]) MOD 16];
    END;
    RETURN Text.FromChars (r^);
  END Encode;

PROCEDURE Value (c: CHAR): INTEGER =
  BEGIN
    CASE c OF
    | '0' .. '9' => RETURN ORD (c) - ORD ('0');
    | 'a' .. 'f' => RETURN ORD (c) - ORD ('a') + 10;
    | 'A' .. 'F' => RETURN ORD (c) - ORD ('A') + 10;
    ELSE RETURN -1;
    END;
  END Value;

PROCEDURE Decode (t: TEXT; VAR r: TEXT): BOOLEAN =
  VAR
    n := Text.Length (t);
    b := NEW (REF ARRAY OF CHAR, n);
    o := NEW (REF ARRAY OF CHAR, n DIV 2);
    high, low: INTEGER;
  BEGIN
    IF n MOD 2 # 0 OR Text.HasWideChars (t) THEN RETURN FALSE END;
    Text.SetChars (b^, t);
    FOR i := 0 TO n DIV 2 - 1 DO
      high := Value (b[2 * i]);
      low := Value (b[2 * i + 1]);
      IF high < 0 OR low < 0 THEN RETURN FALSE END;
      o[i] := VAL (high * 16 + low, CHAR);
    END;
    r := Text.FromChars (o^);
    RETURN TRUE;
  END Decode;

BEGIN
END Hex.
