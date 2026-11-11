(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE FeedDate;

IMPORT Regexp, RFC3339, RFC5322, Text, Time;

CONST
  (* Zones by name that RFC 5322 does not have, and their offsets. *)
  ZoneNames = ARRAY OF TEXT {"UTC", "CET", "CEST", "MEZ", "MESZ", "BST", "EET", "EEST", "WET", "WEST", "MSK",
                             "IST", "HKT", "SGT", "AWST", "JST", "KST", "ACST", "AEST", "AEDT", "NZST",
                             "NZDT", "AKST", "AKDT", "HST"};
  ZoneOffsets = ARRAY OF TEXT {"+0000", "+0100", "+0200", "+0100", "+0200", "+0100", "+0200", "+0300", "+0000",
                               "+0100", "+0300", "+0530", "+0800", "+0800", "+0800", "+0900", "+0900", "+0930",
                               "+1000", "+1100", "+1200", "+1300", "-0900", "-0800", "-1000"};

VAR
  iso, mail: Regexp.T;          (* compiled once, by the main body *)

PROCEDURE Trim (t: TEXT): TEXT =
  VAR start := 0;  stop := Text.Length (t);
  BEGIN
    WHILE start < stop AND Text.GetWideChar (t, start) <= W' ' DO INC (start) END;
    WHILE stop > start AND Text.GetWideChar (t, stop - 1) <= W' ' DO DEC (stop) END;
    RETURN Text.Sub (t, start, stop - start);
  END Trim;

(* The zone "z" as "+hhmm", NIL if it is not known. *)
PROCEDURE Zone (z: TEXT): TEXT =
  VAR u: TEXT;
  BEGIN
    IF z = NIL THEN RETURN "+0000" END;
    IF Text.GetChar (z, 0) = '+' OR Text.GetChar (z, 0) = '-' THEN
      IF Text.Length (z) = 6 THEN RETURN Text.Sub (z, 0, 3) & Text.Sub (z, 4) END;
      RETURN z;
    END;
    u := Upper (z);
    FOR i := FIRST (ZoneNames) TO LAST (ZoneNames) DO
      IF Text.Equal (u, ZoneNames[i]) THEN RETURN ZoneOffsets[i] END;
    END;
    RETURN NIL;
  END Zone;

PROCEDURE Upper (t: TEXT): TEXT =
  VAR a := NEW (REF ARRAY OF CHAR, Text.Length (t));
  BEGIN
    Text.SetChars (a^, t);
    FOR i := 0 TO LAST (a^) DO
      IF a[i] >= 'a' AND a[i] <= 'z' THEN a[i] := VAL (ORD (a[i]) - 32, CHAR) END;
    END;
    RETURN Text.FromChars (a^);
  END Upper;

PROCEDURE Or (t, default: TEXT): TEXT =
  BEGIN
    IF t = NIL THEN RETURN default END;
    RETURN t;
  END Or;

(* A date of ISO 8601 as feeds have it, in the form of RFC 3339. *)
PROCEDURE FromIso (m: Regexp.Match): TEXT =
  VAR z := m.group (8);  zone: TEXT;
  BEGIN
    IF z = NIL OR Text.Equal (Upper (z), "Z") OR Text.Equal (Upper (z), "UTC") OR Text.Equal (Upper (z), "GMT") THEN
      zone := "Z";
    ELSE
      z := Zone (z);
      zone := Text.Sub (z, 0, 3) & ":" & Text.Sub (z, 3);
    END;
    RETURN m.group (1) & "-" & m.group (2) & "-" & m.group (3) & "T" & Or (m.group (4), "00") & ":"
             & Or (m.group (5), "00") & ":" & Or (m.group (6), "00") & Or (m.group (7), "") & zone;
  END FromIso;

PROCEDURE Parse (t: TEXT; VAR r: Time.T): BOOLEAN =
  VAR offset: INTEGER;  m: Regexp.Match;  zone: TEXT;
  BEGIN
    IF t = NIL THEN RETURN FALSE END;
    t := Trim (t);
    IF RFC3339.Parse (t, r, offset) OR RFC5322.Parse (t, r, offset) THEN RETURN TRUE END;
    TRY
      m := Regexp.FullMatch (iso, t);
      IF m # NIL THEN RETURN RFC3339.Parse (FromIso (m), r, offset) END;
      m := Regexp.FullMatch (mail, t);
      IF m # NIL THEN
        zone := Zone (m.group (2));
        RETURN zone # NIL AND RFC5322.Parse (m.group (1) & " " & zone, r, offset);
      END;
    EXCEPT
    | Regexp.TooComplex => (* not a date *)
    END;
    RETURN FALSE;
  END Parse;

PROCEDURE Format (t: Time.T): TEXT =
  BEGIN
    RETURN RFC3339.Format (FLOAT (FLOOR (t), LONGREAL));
  END Format;

<*FATAL Regexp.Error*>
BEGIN
  iso := Regexp.Compile ("(\\d{4})-(\\d{2})-(\\d{2})"
                           & "(?:[Tt ](\\d{2}):(\\d{2})(?::(\\d{2})(\\.\\d+)?)?"
                           & " ?([Zz]|[+-]\\d{2}:?\\d{2}|UTC|GMT)?)?");
  mail := Regexp.Compile ("(.*?\\d:\\d\\d(?::\\d\\d)?)(?:\\s+([A-Za-z]{2,5}|[+-]\\d\\d:?\\d\\d))?");
END FeedDate.
