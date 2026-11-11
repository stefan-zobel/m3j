(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE RFC5322;

IMPORT ASCII, Date, Fmt, Text, Time;

CONST
  Days = ARRAY Date.WeekDay OF TEXT {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
  LongDays = ARRAY Date.WeekDay OF TEXT {"sunday", "monday", "tuesday", "wednesday", "thursday", "friday",
                                         "saturday"};
  Months = ARRAY Date.Month OF TEXT {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct",
                                     "Nov", "Dec"};
  First = -2208988800.0D0;      (* 1900-01-01T00:00:00Z *)
  Last = 253402300800.0D0;      (* 10000-01-01T00:00:00Z *)

  (* The zones by name and their offsets in hours. *)
  ZoneNames = ARRAY OF TEXT {"ut", "gmt", "z", "est", "edt", "cst", "cdt", "mst", "mdt", "pst", "pdt"};
  ZoneHours = ARRAY OF INTEGER {0, 0, 0, -5, -4, -6, -5, -7, -6, -8, -7};

  Space = SET OF CHAR {' ', '\t', '\r', '\n'};  (* the folding white space of mail *)

PROCEDURE Pad (n, width: INTEGER): TEXT =
  BEGIN
    RETURN Fmt.Pad (Fmt.Int (n), width, '0');
  END Pad;

PROCEDURE Format (t: Time.T; offset := 0): TEXT =
  VAR seconds: INTEGER;  d: Date.T;  r: TEXT;  abs := ABS (offset);
  BEGIN
    <*ASSERT offset MOD 60 = 0 AND abs < 86400*>
    <*ASSERT t >= First AND t < Last*>
    seconds := FLOOR (t);
    d := Date.FromTime (FLOAT (seconds + offset, LONGREAL), Date.UTC);
    <*ASSERT d.year >= 1900 AND d.year <= 9999*>
    r := Days[d.weekDay] & ", " & Pad (d.day, 2) & " " & Months[d.month] & " " & Pad (d.year, 4) & " "
           & Pad (d.hour, 2) & ":" & Pad (d.minute, 2) & ":" & Pad (d.second, 2) & " ";
    IF offset < 0 THEN r := r & "-" ELSE r := r & "+" END;
    RETURN r & Pad (abs DIV 3600, 2) & Pad (abs DIV 60 MOD 60, 2);
  END Format;

TYPE
  Scanner = RECORD
    t: TEXT;
    n, i: INTEGER;
  END;

PROCEDURE Peek (READONLY s: Scanner): CHAR =
  BEGIN
    IF s.i < s.n THEN RETURN Text.GetChar (s.t, s.i) END;
    RETURN '\000';
  END Peek;

(* Skips white space; whether there was some. *)
PROCEDURE Blank (VAR s: Scanner): BOOLEAN =
  VAR start := s.i;
  BEGIN
    WHILE s.i < s.n AND Text.GetChar (s.t, s.i) IN Space DO INC (s.i) END;
    RETURN s.i > start;
  END Blank;

(* "t" in lower case. *)
PROCEDURE Lower (t: TEXT): TEXT =
  VAR a := NEW (REF ARRAY OF CHAR, Text.Length (t));
  BEGIN
    Text.SetChars (a^, t);
    FOR i := 0 TO LAST (a^) DO a[i] := ASCII.Lower[a[i]] END;
    RETURN Text.FromChars (a^);
  END Lower;

(* The letters from here on, in lower case. *)
PROCEDURE Word (VAR s: Scanner): TEXT =
  VAR start := s.i;
  BEGIN
    WHILE s.i < s.n AND Text.GetChar (s.t, s.i) IN ASCII.Letters DO INC (s.i) END;
    RETURN Lower (Text.Sub (s.t, start, s.i - start));
  END Word;

(* Sets "v" to the digits from here on, if there are "min" to "max" of
   them. *)
PROCEDURE Digits (VAR s: Scanner; min, max: INTEGER; VAR v: INTEGER): BOOLEAN =
  VAR start := s.i;  c: CHAR;
  BEGIN
    v := 0;
    WHILE s.i < s.n DO
      c := Text.GetChar (s.t, s.i);
      IF c < '0' OR c > '9' THEN EXIT END;
      IF s.i - start = max THEN RETURN FALSE END;
      v := v * 10 + ORD (c) - ORD ('0');
      INC (s.i);
    END;
    RETURN s.i - start >= min;
  END Digits;

PROCEDURE DaysIn (month: Date.Month; year: INTEGER): INTEGER =
  BEGIN
    CASE month OF
    | Date.Month.Feb =>
        IF year MOD 4 = 0 AND (year MOD 100 # 0 OR year MOD 400 = 0) THEN RETURN 29 END;
        RETURN 28;
    | Date.Month.Apr, Date.Month.Jun, Date.Month.Sep, Date.Month.Nov => RETURN 30;
    ELSE
      RETURN 31;
    END;
  END DaysIn;

PROCEDURE Parse (t: TEXT; VAR r: Time.T; VAR offset: INTEGER): BOOLEAN =
  VAR
    s := Scanner {t, Text.Length (t), 0};
    w: TEXT;
    found := FALSE;
    day, year, hour, minute, zone, start: INTEGER;
    second := 0;
    month := Date.Month.Jan;
    east := 0;
    sign: CHAR;
    depth: INTEGER;
    d: Date.T;
    time: Time.T;
  BEGIN
    IF Text.HasWideChars (t) THEN RETURN FALSE END;
    EVAL Blank (s);

    (* the day of the week *)
    IF Peek (s) IN ASCII.Letters THEN
      w := Word (s);
      FOR k := FIRST (Date.WeekDay) TO LAST (Date.WeekDay) DO
        IF Text.Equal (w, Lower (Days[k])) OR Text.Equal (w, LongDays[k]) THEN found := TRUE END;
      END;
      IF NOT found THEN RETURN FALSE END;
      EVAL Blank (s);
      IF Peek (s) # ',' THEN RETURN FALSE END;
      INC (s.i);
      EVAL Blank (s);
    END;

    (* the date *)
    IF NOT Digits (s, 1, 2, day) OR NOT Blank (s) THEN RETURN FALSE END;
    w := Word (s);
    found := FALSE;
    FOR k := FIRST (Date.Month) TO LAST (Date.Month) DO
      IF Text.Equal (w, Lower (Months[k])) THEN
        month := k;
        found := TRUE;
      END;
    END;
    IF NOT found OR NOT Blank (s) THEN RETURN FALSE END;
    start := s.i;
    IF NOT Digits (s, 2, 4, year) THEN RETURN FALSE END;
    IF s.i - start = 2 AND year < 50 THEN
      INC (year, 2000);
    ELSIF s.i - start < 4 THEN
      INC (year, 1900);
    END;
    IF NOT Blank (s) OR year < 1900 OR day < 1 OR day > DaysIn (month, year) THEN RETURN FALSE END;

    (* the time *)
    IF NOT Digits (s, 1, 2, hour) OR Peek (s) # ':' THEN RETURN FALSE END;
    INC (s.i);
    IF NOT Digits (s, 2, 2, minute) THEN RETURN FALSE END;
    IF Peek (s) = ':' THEN
      INC (s.i);
      IF NOT Digits (s, 2, 2, second) THEN RETURN FALSE END;
    END;
    IF hour > 23 OR minute > 59 OR second > 60 THEN RETURN FALSE END;

    (* the zone *)
    IF NOT Blank (s) THEN RETURN FALSE END;
    sign := Peek (s);
    IF sign = '+' OR sign = '-' THEN
      INC (s.i);
      IF NOT Digits (s, 4, 4, zone) OR zone DIV 100 > 23 OR zone MOD 100 > 59 THEN RETURN FALSE END;
      east := (zone DIV 100 * 60 + zone MOD 100) * 60;
      IF sign = '-' THEN east := -east END;
    ELSE
      w := Word (s);
      found := FALSE;
      FOR k := FIRST (ZoneNames) TO LAST (ZoneNames) DO
        IF Text.Equal (w, ZoneNames[k]) THEN
          east := ZoneHours[k] * 3600;
          found := TRUE;
        END;
      END;
      IF NOT found AND NOT (Text.Length (w) = 1 AND Text.GetChar (w, 0) # 'j') THEN RETURN FALSE END;
    END;

    (* a comment, and the end *)
    EVAL Blank (s);
    IF Peek (s) = '(' THEN
      depth := 0;
      REPEAT
        IF s.i = s.n THEN RETURN FALSE END;
        IF Text.GetChar (t, s.i) = '(' THEN
          INC (depth);
        ELSIF Text.GetChar (t, s.i) = ')' THEN
          DEC (depth);
        END;
        INC (s.i);
      UNTIL depth = 0;
      EVAL Blank (s);
    END;
    IF s.i # s.n THEN RETURN FALSE END;

    d.year := year;
    d.month := month;
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
    r := time;
    offset := east;
    RETURN TRUE;
  END Parse;

BEGIN
END RFC5322.
