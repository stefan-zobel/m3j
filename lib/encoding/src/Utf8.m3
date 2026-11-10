(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Utf8;

IMPORT Text;

CONST
  HighFirst = 16_D800;
  LowFirst = 16_DC00;
  LowLast = 16_DFFF;
  Replacement = 16_FFFD;

PROCEDURE Encode (t: TEXT): TEXT =
  VAR
    n := Text.Length (t);
    w := NEW (REF ARRAY OF WIDECHAR, n);
    r := NEW (REF ARRAY OF CHAR, 3 * n);
    j, i := 0;
    c: INTEGER;

  PROCEDURE Put (b: INTEGER) =
    BEGIN
      r[j] := VAL (b, CHAR);
      INC (j);
    END Put;

  BEGIN
    Text.SetWideChars (w^, t);
    WHILE i < n DO
      c := ORD (w[i]);
      INC (i);
      IF c >= HighFirst AND c <= LowLast THEN
        IF c < LowFirst AND i < n AND ORD (w[i]) >= LowFirst AND ORD (w[i]) <= LowLast THEN
          c := 16_10000 + (c - HighFirst) * 1024 + ORD (w[i]) - LowFirst;
          INC (i);
        ELSE
          c := Replacement;
        END;
      END;
      IF c < 16_80 THEN
        Put (c);
      ELSIF c < 16_800 THEN
        Put (16_C0 + c DIV 64);
        Put (16_80 + c MOD 64);
      ELSIF c < 16_10000 THEN
        Put (16_E0 + c DIV 4096);
        Put (16_80 + c DIV 64 MOD 64);
        Put (16_80 + c MOD 64);
      ELSE
        Put (16_F0 + c DIV 262144);
        Put (16_80 + c DIV 4096 MOD 64);
        Put (16_80 + c DIV 64 MOD 64);
        Put (16_80 + c MOD 64);
      END;
    END;
    RETURN Text.FromChars (SUBARRAY (r^, 0, j));
  END Encode;

PROCEDURE Decode (t: TEXT; VAR r: TEXT): BOOLEAN =
  VAR
    n := Text.Length (t);
    b := NEW (REF ARRAY OF CHAR, n);
    o := NEW (REF ARRAY OF WIDECHAR, n);
    i, j := 0;
    c, more, low, high: INTEGER;
  BEGIN
    IF Text.HasWideChars (t) THEN RETURN FALSE END;
    Text.SetChars (b^, t);
    WHILE i < n DO
      c := ORD (b[i]);
      INC (i);
      low := 16_80;
      high := 16_BF;
      IF c < 16_80 THEN
        more := 0;
      ELSIF c >= 16_C2 AND c <= 16_DF THEN
        more := 1;
        c := c - 16_C0;
      ELSIF c >= 16_E0 AND c <= 16_EF THEN
        more := 2;
        IF c = 16_E0 THEN low := 16_A0 ELSIF c = 16_ED THEN high := 16_9F END;
        c := c - 16_E0;
      ELSIF c >= 16_F0 AND c <= 16_F4 THEN
        more := 3;
        IF c = 16_F0 THEN low := 16_90 ELSIF c = 16_F4 THEN high := 16_8F END;
        c := c - 16_F0;
      ELSE
        RETURN FALSE;
      END;
      IF i + more > n THEN RETURN FALSE END;
      FOR k := 1 TO more DO
        IF ORD (b[i]) < low OR ORD (b[i]) > high THEN RETURN FALSE END;
        c := c * 64 + ORD (b[i]) - 16_80;
        INC (i);
        low := 16_80;
        high := 16_BF;
      END;
      IF c < 16_10000 THEN
        o[j] := VAL (c, WIDECHAR);
        INC (j);
      ELSE
        c := c - 16_10000;
        o[j] := VAL (HighFirst + c DIV 1024, WIDECHAR);
        o[j + 1] := VAL (LowFirst + c MOD 1024, WIDECHAR);
        INC (j, 2);
      END;
    END;
    r := Text.FromWideChars (SUBARRAY (o^, 0, j));
    RETURN TRUE;
  END Decode;

BEGIN
END Utf8.
