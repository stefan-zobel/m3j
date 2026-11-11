(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE TextBuf;

IMPORT Text;

REVEAL
  T = Public BRANDED OBJECT
    a: REF ARRAY OF TEXT;
    n := 0;
  OVERRIDES
    put := Put;
    text := Join;
  END;

PROCEDURE New (): T =
  BEGIN
    RETURN NEW (T, a := NEW (REF ARRAY OF TEXT, 16));
  END New;

PROCEDURE Put (b: T; t: TEXT) =
  VAR c: REF ARRAY OF TEXT;
  BEGIN
    IF b.n = NUMBER (b.a^) THEN
      c := NEW (REF ARRAY OF TEXT, 2 * b.n);
      SUBARRAY (c^, 0, b.n) := b.a^;
      b.a := c;
    END;
    b.a[b.n] := t;
    INC (b.n);
  END Put;

PROCEDURE Join (b: T): TEXT =
  VAR len := 0;  pos := 0;  buf: REF ARRAY OF WIDECHAR;  k: INTEGER;
  BEGIN
    IF b.n = 0 THEN RETURN "" END;
    IF b.n = 1 THEN RETURN b.a[0] END;
    FOR i := 0 TO b.n - 1 DO INC (len, Text.Length (b.a[i])) END;
    buf := NEW (REF ARRAY OF WIDECHAR, len);
    FOR i := 0 TO b.n - 1 DO
      k := Text.Length (b.a[i]);
      Text.SetWideChars (SUBARRAY (buf^, pos, k), b.a[i]);
      INC (pos, k);
    END;
    RETURN Text.FromWideChars (buf^);
  END Join;

BEGIN
END TextBuf.
