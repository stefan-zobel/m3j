(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE RFC3339;

IMPORT Date, Fmt, Text, Time;

CONST
  Micro = 1000000;
  First = -62167219200.0D0;     (* 0000-01-01T00:00:00Z *)
  Last = 253402300800.0D0;      (* 10000-01-01T00:00:00Z *)
  FractionDigits = 15;          (* of what a Time.T can hold at all *)

PROCEDURE Pad (n, width: INTEGER): TEXT =
  BEGIN
    RETURN Fmt.Pad (Fmt.Int (n), width, '0');
  END Pad;

PROCEDURE Format (t: Time.T; offset := 0; digits := 0): TEXT =
  VAR micros, seconds, abs: INTEGER;  d: Date.T;  r: TEXT;
  BEGIN
    <*ASSERT digits >= 0 AND digits <= 6*>
    <*ASSERT offset MOD 60 = 0 AND ABS (offset) < 86400*>
    <*ASSERT t >= First AND t < Last*>
    micros := ROUND (t * FLOAT (Micro, LONGREAL));
    seconds := micros DIV Micro;
    d := Date.FromTime (FLOAT (seconds + offset, LONGREAL), Date.UTC);
    <*ASSERT d.year <= 9999*>
    r := Pad (d.year, 4) & "-" & Pad (ORD (d.month) + 1, 2) & "-" & Pad (d.day, 2) & "T"
           & Pad (d.hour, 2) & ":" & Pad (d.minute, 2) & ":" & Pad (d.second, 2);
    IF digits > 0 THEN
      r := r & "." & Text.Sub (Pad (micros MOD Micro, 6), 0, digits);
    END;
    IF offset = 0 THEN RETURN r & "Z" END;
    abs := ABS (offset);
    IF offset < 0 THEN r := r & "-" ELSE r := r & "+" END;
    RETURN r & Pad (abs DIV 3600, 2) & ":" & Pad (abs DIV 60 MOD 60, 2);
  END Format;

(* Sets "v" to the "count" digits of "t" from "start" on, if they are that. *)
PROCEDURE Number (t: TEXT; start, count: INTEGER; VAR v: INTEGER): BOOLEAN =
  VAR c: CHAR;
  BEGIN
    v := 0;
    FOR i := start TO start + count - 1 DO
      c := Text.GetChar (t, i);
      IF c < '0' OR c > '9' THEN RETURN FALSE END;
      v := v * 10 + ORD (c) - ORD ('0');
    END;
    RETURN TRUE;
  END Number;

PROCEDURE Parse (t: TEXT; VAR r: Time.T; VAR offset: INTEGER): BOOLEAN =
  VAR
    n := Text.Length (t);
    i := 19;
    year, month, day, hour, minute, second, zh, zm: INTEGER;
    east := 0;
    scale := 1.0D0;
    fraction := 0.0D0;
    digits := 0;
    c: CHAR;
    d: Date.T;
    time: Time.T;
  BEGIN
    IF n < 20 OR Text.HasWideChars (t) THEN RETURN FALSE END;
    IF NOT (Number (t, 0, 4, year)
              AND Text.GetChar (t, 4) = '-'
              AND Number (t, 5, 2, month)
              AND Text.GetChar (t, 7) = '-'
              AND Number (t, 8, 2, day)
              AND Text.GetChar (t, 10) IN SET OF CHAR {'T', 't'}
              AND Number (t, 11, 2, hour)
              AND Text.GetChar (t, 13) = ':'
              AND Number (t, 14, 2, minute)
              AND Text.GetChar (t, 16) = ':'
              AND Number (t, 17, 2, second)) THEN
      RETURN FALSE;
    END;
    IF month < 1 OR month > 12 OR day < 1 OR day > 31 OR hour > 23 OR minute > 59
         OR second > 60 THEN
      RETURN FALSE;
    END;

    IF Text.GetChar (t, i) = '.' THEN
      INC (i);
      WHILE i < n AND Text.GetChar (t, i) >= '0' AND Text.GetChar (t, i) <= '9' DO
        IF digits < FractionDigits THEN
          fraction := fraction * 10.0D0 + FLOAT (ORD (Text.GetChar (t, i)) - ORD ('0'), LONGREAL);
          scale := scale * 10.0D0;
          INC (digits);
        END;
        INC (i);
      END;
      IF digits = 0 OR i = n THEN RETURN FALSE END;
    END;

    c := Text.GetChar (t, i);
    IF c = 'Z' OR c = 'z' THEN
      IF i + 1 # n THEN RETURN FALSE END;
    ELSIF c = '+' OR c = '-' THEN
      IF i + 6 # n OR NOT Number (t, i + 1, 2, zh) OR Text.GetChar (t, i + 3) # ':'
           OR NOT Number (t, i + 4, 2, zm) OR zh > 23 OR zm > 59 THEN
        RETURN FALSE;
      END;
      east := (zh * 60 + zm) * 60;
      IF c = '-' THEN east := -east END;
    ELSE
      RETURN FALSE;
    END;

    d.year := year;
    d.month := VAL (month - 1, Date.Month);
    d.day := day;
    d.hour := hour;
    d.minute := minute;
    d.second := MIN (second, 59);
    d.offset := -east;
    TRY
      time := Date.ToTime (d);
    EXCEPT
    | Date.Error => RETURN FALSE;
    END;
    IF second = 60 THEN
      d := Date.FromTime (time, Date.UTC);
      IF d.hour # 23 OR d.minute # 59 THEN RETURN FALSE END;
      time := time + 1.0D0;
      d := Date.FromTime (time, Date.UTC);
      IF d.day # 1 THEN RETURN FALSE END;
    END;
    r := time + fraction / scale;
    offset := east;
    RETURN TRUE;
  END Parse;

BEGIN
END RFC3339.
