(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE UUID;

IMPORT Crypto, Text, Time;

CONST
  Digits = "0123456789abcdef";
  Length = 36;

(* Sets the version and the variant "10" of RFC 9562. *)
PROCEDURE Mark (VAR u: T; version: [0 .. 15]) =
  BEGIN
    u[6] := VAL (version * 16 + ORD (u[6]) MOD 16, CHAR);
    u[8] := VAL (16_80 + ORD (u[8]) MOD 64, CHAR);
  END Mark;

PROCEDURE NewV4 (): T =
  VAR u: T;
  BEGIN
    Crypto.RandomBytes (u);
    Mark (u, 4);
    RETURN u;
  END NewV4;

PROCEDURE NewV7 (): T =
  VAR u: T;  ms := FLOOR (Time.Now () * 1000.0D0);
  BEGIN
    Crypto.RandomBytes (u);
    FOR i := 5 TO 0 BY -1 DO
      u[i] := VAL (ms MOD 256, CHAR);
      ms := ms DIV 256;
    END;
    Mark (u, 7);
    RETURN u;
  END NewV7;

PROCEDURE Dash (i: INTEGER): BOOLEAN =
  BEGIN
    RETURN i = 8 OR i = 13 OR i = 18 OR i = 23;
  END Dash;

PROCEDURE Format (READONLY u: T): TEXT =
  VAR r: ARRAY [0 .. Length - 1] OF CHAR;  j := 0;
  BEGIN
    FOR i := 0 TO LAST (u) DO
      IF Dash (j) THEN
        r[j] := '-';
        INC (j);
      END;
      r[j] := Text.GetChar (Digits, ORD (u[i]) DIV 16);
      r[j + 1] := Text.GetChar (Digits, ORD (u[i]) MOD 16);
      INC (j, 2);
    END;
    RETURN Text.FromChars (r);
  END Format;

PROCEDURE Value (c: CHAR): INTEGER =
  BEGIN
    CASE c OF
    | '0' .. '9' => RETURN ORD (c) - ORD ('0');
    | 'a' .. 'f' => RETURN ORD (c) - ORD ('a') + 10;
    | 'A' .. 'F' => RETURN ORD (c) - ORD ('A') + 10;
    ELSE RETURN -1;
    END;
  END Value;

PROCEDURE Parse (t: TEXT; VAR u: T): BOOLEAN =
  VAR r: T;  j := 0;  hi, lo: INTEGER;
  BEGIN
    IF Text.Length (t) # Length OR Text.HasWideChars (t) THEN RETURN FALSE END;
    FOR i := 0 TO LAST (r) DO
      IF Dash (j) THEN
        IF Text.GetChar (t, j) # '-' THEN RETURN FALSE END;
        INC (j);
      END;
      hi := Value (Text.GetChar (t, j));
      lo := Value (Text.GetChar (t, j + 1));
      IF hi < 0 OR lo < 0 THEN RETURN FALSE END;
      r[i] := VAL (hi * 16 + lo, CHAR);
      INC (j, 2);
    END;
    u := r;
    RETURN TRUE;
  END Parse;

PROCEDURE Version (READONLY u: T): [0 .. 15] =
  BEGIN
    RETURN ORD (u[6]) DIV 16;
  END Version;

BEGIN
END UUID.
