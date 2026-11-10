(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE HTTPCookie;

IMPORT Atom, AtomList, Date, Fmt, HTTP, Text, TextExtras, Time;

CONST
  Earliest = -11644473600.0D0;   (* 1601-01-01 00:00:00 UTC *)
  Latest = 253402300800.0D0;     (* 10000-01-01 00:00:00 UTC, excluded *)
  MaxPair = 4096;
  MaxAttribute = 1024;

  Space = SET OF CHAR {' ', '\t'};
  Digits = SET OF CHAR {'0' .. '9'};
  CookieOctets = SET OF CHAR {'!', '#' .. '+', '-' .. ':', '<' .. '[', ']' .. '~'};
  DomainChars = SET OF CHAR {'A' .. 'Z', 'a' .. 'z', '0' .. '9', '-', '.'};
  PathChars = SET OF CHAR {' ' .. ':', '<' .. '~'};

  (* the delimiters of the dates of section 5.1.1 of RFC 6265 *)
  Delimiters = SET OF CHAR {'\t', ' ' .. '/', ';' .. '@', '[' .. '`', '{' .. '~'};

  Months = ARRAY Date.Month OF TEXT {"jan", "feb", "mar", "apr", "may", "jun",
                                     "jul", "aug", "sep", "oct", "nov", "dec"};

(* Formatting *)

PROCEDURE Bad (what: TEXT) RAISES {HTTP.Error} =
  BEGIN
    RAISE HTTP.Error (AtomList.List2 (HTTP.BadMessage, Atom.FromText (what)));
  END Bad;

PROCEDURE Large (what: TEXT) RAISES {HTTP.Error} =
  BEGIN
    RAISE HTTP.Error (AtomList.List2 (HTTP.TooLarge, Atom.FromText (what)));
  END Large;

(* Do all the characters of "t" belong to "set"? *)
PROCEDURE AllIn (t: TEXT; set: SET OF CHAR): BOOLEAN =
  BEGIN
    IF Text.HasWideChars (t) THEN RETURN FALSE END;
    FOR i := 0 TO Text.Length (t) - 1 DO
      IF NOT Text.GetChar (t, i) IN set THEN RETURN FALSE END;
    END;
    RETURN TRUE;
  END AllIn;

PROCEDURE ValidValue (v: TEXT): BOOLEAN =
  VAR n := Text.Length (v);
  BEGIN
    IF n > 0 AND Text.GetChar (v, 0) = '"' THEN
      RETURN n >= 2 AND Text.GetChar (v, n - 1) = '"'
               AND AllIn (Text.Sub (v, 1, n - 2), CookieOctets);
    END;
    RETURN AllIn (v, CookieOctets);
  END ValidValue;

(* Does "t" start with "prefix", without regard to case? *)
PROCEDURE HasPrefix (t, prefix: TEXT): BOOLEAN =
  VAR m := Text.Length (prefix);
  BEGIN
    RETURN Text.Length (t) >= m AND TextExtras.CIEqual (Text.Sub (t, 0, m), prefix);
  END HasPrefix;

PROCEDURE Format (READONLY c: T): TEXT RAISES {HTTP.Error} =
  VAR r: TEXT;
  BEGIN
    IF NOT HTTP.ValidName (c.name) THEN Bad ("invalid name of a cookie") END;
    IF NOT ValidValue (c.value) THEN Bad ("invalid value of a cookie") END;
    IF Text.Length (c.name) + Text.Length (c.value) > MaxPair THEN
      Large ("cookie larger than 4096 bytes");
    END;
    r := c.name & "=" & c.value;
    IF c.expires # NoExpires THEN
      IF NOT (Earliest <= c.expires AND c.expires < Latest) THEN
        Bad ("expiry of a cookie out of range");
      END;
      r := r & "; Expires=" & HTTP.FormatDate (c.expires);
    END;
    IF c.maxAge # NoMaxAge THEN
      IF c.maxAge < 0 THEN Bad ("negative Max-Age of a cookie") END;
      r := r & "; Max-Age=" & Fmt.Int (c.maxAge);
    END;
    IF c.domain # NIL THEN
      IF Text.Length (c.domain) = 0 OR NOT AllIn (c.domain, DomainChars) THEN
        Bad ("invalid domain of a cookie");
      END;
      IF Text.Length (c.domain) > MaxAttribute THEN Large ("domain of a cookie too long") END;
      r := r & "; Domain=" & c.domain;
    END;
    IF c.path # NIL THEN
      IF Text.Length (c.path) = 0 OR Text.GetChar (c.path, 0) # '/'
           OR NOT AllIn (c.path, PathChars) THEN
        Bad ("invalid path of a cookie");
      END;
      IF Text.Length (c.path) > MaxAttribute THEN Large ("path of a cookie too long") END;
      r := r & "; Path=" & c.path;
    END;
    IF c.secure THEN r := r & "; Secure" END;
    IF c.httpOnly THEN r := r & "; HttpOnly" END;
    CASE c.sameSite OF
    | SameSite.Unset =>
    | SameSite.Strict => r := r & "; SameSite=Strict";
    | SameSite.Lax => r := r & "; SameSite=Lax";
    | SameSite.None =>
        IF NOT c.secure THEN Bad ("SameSite=None without Secure") END;
        r := r & "; SameSite=None";
    END;
    IF HasPrefix (c.name, "__Secure-") AND NOT c.secure THEN
      Bad ("cookie with the prefix __Secure- without Secure");
    END;
    IF HasPrefix (c.name, "__Host-")
         AND (NOT c.secure OR c.domain # NIL OR c.path = NIL OR NOT Text.Equal (c.path, "/")) THEN
      Bad ("cookie with the prefix __Host- without Secure, Path=/ or with Domain");
    END;
    RETURN r;
  END Format;

(* Parsing *)

(* "t[start .. end-1]" without spaces and tabs around it. *)
PROCEDURE Trim (t: TEXT; start, end: INTEGER): TEXT =
  BEGIN
    WHILE start < end AND Text.GetChar (t, start) IN Space DO INC (start) END;
    WHILE end > start AND Text.GetChar (t, end - 1) IN Space DO DEC (end) END;
    RETURN Text.Sub (t, start, end - start);
  END Trim;

PROCEDURE Lower (t: TEXT): TEXT =
  VAR a := NEW (REF ARRAY OF CHAR, Text.Length (t));
  BEGIN
    Text.SetChars (a^, t);
    FOR i := 0 TO LAST (a^) DO
      IF a[i] IN SET OF CHAR {'A' .. 'Z'} THEN
        a[i] := VAL (ORD (a[i]) - ORD ('A') + ORD ('a'), CHAR);
      END;
    END;
    RETURN Text.FromChars (a^);
  END Lower;

(* The number of digits at "t[i]" and on. *)
PROCEDURE CountDigits (t: TEXT; i: INTEGER): INTEGER =
  VAR n := Text.Length (t);  k := i;
  BEGIN
    WHILE k < n AND Text.GetChar (t, k) IN Digits DO INC (k) END;
    RETURN k - i;
  END CountDigits;

PROCEDURE Value (t: TEXT; i, digits: INTEGER): INTEGER =
  VAR r := 0;
  BEGIN
    FOR k := i TO i + digits - 1 DO
      r := r * 10 + ORD (Text.GetChar (t, k)) - ORD ('0');
    END;
    RETURN r;
  END Value;

(* Does the token "t" start with "min" to "max" digits that are not followed
   by another digit? Then "v" is their value. *)
PROCEDURE Number (t: TEXT; min, max: INTEGER; VAR v: INTEGER): BOOLEAN =
  VAR k := CountDigits (t, 0);
  BEGIN
    IF k < min OR k > max THEN RETURN FALSE END;
    v := Value (t, 0, k);
    RETURN TRUE;
  END Number;

(* hms-time = time-field ":" time-field ":" time-field, each of one or two
   digits, then the end of the token or a character that is not a digit. *)
PROCEDURE Clock (t: TEXT; VAR h, m, s: INTEGER): BOOLEAN =
  VAR n := Text.Length (t);  i := 0;  k: INTEGER;  v: ARRAY [0 .. 2] OF INTEGER;
  BEGIN
    FOR f := 0 TO 2 DO
      IF f > 0 THEN
        IF i >= n OR Text.GetChar (t, i) # ':' THEN RETURN FALSE END;
        INC (i);
      END;
      k := CountDigits (t, i);
      IF k < 1 OR k > 2 THEN RETURN FALSE END;
      v[f] := Value (t, i, k);
      INC (i, k);
    END;
    h := v[0];
    m := v[1];
    s := v[2];
    RETURN TRUE;
  END Clock;

PROCEDURE MonthOf (t: TEXT; VAR m: Date.Month): BOOLEAN =
  BEGIN
    IF Text.Length (t) < 3 THEN RETURN FALSE END;
    FOR x := FIRST (Date.Month) TO LAST (Date.Month) DO
      IF TextExtras.CIEqual (Text.Sub (t, 0, 3), Months[x]) THEN
        m := x;
        RETURN TRUE;
      END;
    END;
    RETURN FALSE;
  END MonthOf;

(* A date by the algorithm of section 5.1.1 of RFC 6265. *)
PROCEDURE ParseDate (t: TEXT; VAR r: Time.T): BOOLEAN =
  VAR n := Text.Length (t);  i := 0;  start: INTEGER;  tok: TEXT;
      foundTime, foundDay, foundMonth, foundYear := FALSE;
      hour, minute, second, day, year := 0;  month := Date.Month.Jan;  d: Date.T;
  BEGIN
    WHILE i < n DO
      WHILE i < n AND Text.GetChar (t, i) IN Delimiters DO INC (i) END;
      start := i;
      WHILE i < n AND NOT Text.GetChar (t, i) IN Delimiters DO INC (i) END;
      IF i > start THEN
        tok := Text.Sub (t, start, i - start);
        IF NOT foundTime AND Clock (tok, hour, minute, second) THEN
          foundTime := TRUE;
        ELSIF NOT foundDay AND Number (tok, 1, 2, day) THEN
          foundDay := TRUE;
        ELSIF NOT foundMonth AND MonthOf (tok, month) THEN
          foundMonth := TRUE;
        ELSIF NOT foundYear AND Number (tok, 2, 4, year) THEN
          foundYear := TRUE;
        END;
      END;
    END;
    IF NOT (foundTime AND foundDay AND foundMonth AND foundYear) THEN RETURN FALSE END;
    IF year >= 70 AND year <= 99 THEN
      INC (year, 1900);
    ELSIF year <= 69 THEN
      INC (year, 2000);
    END;
    IF day < 1 OR day > 31 OR year < 1601 OR hour > 23 OR minute > 59 OR second > 59 THEN
      RETURN FALSE;
    END;
    d.year := year;
    d.month := month;
    d.day := day;
    d.hour := hour;
    d.minute := minute;
    d.second := second;
    d.offset := 0;
    d.zone := "GMT";
    TRY
      r := Date.ToTime (d);
    EXCEPT
    | Date.Error => RETURN FALSE;
    END;
    RETURN TRUE;
  END ParseDate;

(* Max-Age: an optional minus and digits. *)
PROCEDURE ParseMaxAge (v: TEXT; VAR r: INTEGER): BOOLEAN =
  VAR n := Text.Length (v);  i := 0;  negative := FALSE;  x := 0;  d: INTEGER;
  BEGIN
    IF n > 0 AND Text.GetChar (v, 0) = '-' THEN
      negative := TRUE;
      i := 1;
    END;
    IF i >= n OR CountDigits (v, i) # n - i THEN RETURN FALSE END;
    FOR k := i TO n - 1 DO
      d := ORD (Text.GetChar (v, k)) - ORD ('0');
      IF x > (LAST (INTEGER) - d) DIV 10 THEN
        x := LAST (INTEGER);
        EXIT;
      END;
      x := x * 10 + d;
    END;
    IF negative THEN r := 0 ELSE r := x END;
    RETURN TRUE;
  END ParseMaxAge;

PROCEDURE Attribute (VAR c: T; av: TEXT) =
  VAR n := Text.Length (av);  eq := Text.FindChar (av, '=');  name, v: TEXT;  age: INTEGER;
      t: Time.T;
  BEGIN
    IF eq < 0 THEN
      name := Trim (av, 0, n);
      v := "";
    ELSE
      name := Trim (av, 0, eq);
      v := Trim (av, eq + 1, n);
    END;
    IF Text.Length (v) > MaxAttribute THEN RETURN END;
    IF TextExtras.CIEqual (name, "Expires") THEN
      IF ParseDate (v, t) THEN c.expires := t END;
    ELSIF TextExtras.CIEqual (name, "Max-Age") THEN
      IF ParseMaxAge (v, age) THEN c.maxAge := age END;
    ELSIF TextExtras.CIEqual (name, "Domain") THEN
      IF Text.Length (v) > 0 THEN
        IF Text.GetChar (v, 0) = '.' THEN v := Text.Sub (v, 1) END;
        c.domain := Lower (v);
      END;
    ELSIF TextExtras.CIEqual (name, "Path") THEN
      IF Text.Length (v) > 0 AND Text.GetChar (v, 0) = '/' THEN
        c.path := v;
      ELSE
        c.path := NIL;
      END;
    ELSIF TextExtras.CIEqual (name, "Secure") THEN
      c.secure := TRUE;
    ELSIF TextExtras.CIEqual (name, "HttpOnly") THEN
      c.httpOnly := TRUE;
    ELSIF TextExtras.CIEqual (name, "SameSite") THEN
      IF TextExtras.CIEqual (v, "Strict") THEN
        c.sameSite := SameSite.Strict;
      ELSIF TextExtras.CIEqual (v, "Lax") THEN
        c.sameSite := SameSite.Lax;
      ELSIF TextExtras.CIEqual (v, "None") THEN
        c.sameSite := SameSite.None;
      ELSE
        c.sameSite := SameSite.Unset;
      END;
    END;
  END Attribute;

PROCEDURE ParseSetCookie (value: TEXT; VAR c: T): BOOLEAN =
  VAR n, semi, eq, next: INTEGER;  ch: CHAR;  r: T;
  BEGIN
    IF value = NIL OR Text.HasWideChars (value) THEN RETURN FALSE END;
    n := Text.Length (value);
    FOR i := 0 TO n - 1 DO
      ch := Text.GetChar (value, i);
      IF ch < ' ' AND ch # '\t' OR ch = '\177' THEN RETURN FALSE END;
    END;
    semi := Text.FindChar (value, ';');
    IF semi < 0 THEN semi := n END;
    eq := Text.FindChar (value, '=');
    IF eq < 0 OR eq > semi THEN RETURN FALSE END;
    r.name := Trim (value, 0, eq);
    r.value := Trim (value, eq + 1, semi);
    IF Text.Length (r.name) = 0 OR Text.Length (r.name) + Text.Length (r.value) > MaxPair THEN
      RETURN FALSE;
    END;
    WHILE semi < n DO
      next := Text.FindChar (value, ';', semi + 1);
      IF next < 0 THEN next := n END;
      Attribute (r, Text.Sub (value, semi + 1, next - semi - 1));
      semi := next;
    END;
    c := r;
    RETURN TRUE;
  END ParseSetCookie;

(* The "Cookie" field *)

(* The value of the cookie "name" in the value "v" of a "Cookie" field, or
   "NIL". *)
PROCEDURE Find (v, name: TEXT): TEXT =
  VAR n := Text.Length (v);  start := 0;  end, eq: INTEGER;
  BEGIN
    WHILE start <= n DO
      end := Text.FindChar (v, ';', start);
      IF end < 0 THEN end := n END;
      eq := Text.FindChar (v, '=', start);
      IF eq >= 0 AND eq < end AND Text.Equal (Trim (v, start, eq), name) THEN
        RETURN Trim (v, eq + 1, end);
      END;
      start := end + 1;
    END;
    RETURN NIL;
  END Find;

PROCEDURE Get (h: HTTP.Headers; name: TEXT): TEXT =
  VAR it := h.iterate ();  field, v, r: TEXT;
  BEGIN
    WHILE it.next (field, v) DO
      IF TextExtras.CIEqual (field, "Cookie") THEN
        r := Find (v, name);
        IF r # NIL THEN RETURN r END;
      END;
    END;
    RETURN NIL;
  END Get;

BEGIN
END HTTPCookie.
