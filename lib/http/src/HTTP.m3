(* Copyright (C) 1995, Digital Equipment Corporation. *)
(* All rights reserved. *)
(* Parts Copyright (C) 1997, Columbia University *)
(* All rights reserved. *)
(* See the file COPYRIGHT for a full description. *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed under
   the MIT license in the file LICENSE. *)

(* The tables and the outline of FormatDate come from the package
   m3-www/http of CM3; the rest is new. *)

MODULE HTTP;

IMPORT Atom, AtomList, Base64, Date, Fmt, Text, TextExtras, Time, URI;

CONST
  TokenChars = SET OF CHAR {'A' .. 'Z', 'a' .. 'z', '0' .. '9', '!', '#', '$', '%',
                            '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~'};

  HighFirst = 16_D800;
  LowFirst = 16_DC00;
  LowLast = 16_DFFF;
  Replacement = 16_FFFD;

  WeekDay3 = ARRAY Date.WeekDay OF TEXT {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
  WeekDayFull = ARRAY Date.WeekDay OF TEXT {"Sunday", "Monday", "Tuesday", "Wednesday",
                                            "Thursday", "Friday", "Saturday"};
  Month3 = ARRAY Date.Month OF TEXT {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                     "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

REVEAL
  Headers = HeadersPublic BRANDED "HTTP.Headers" OBJECT
    n := 0;
    names, values: REF ARRAY OF TEXT := NIL;
  OVERRIDES
    get := Get;
    add := Add;
    set := Set;
    remove := Remove;
    iterate := Iterate;
  END;

  Iterator = IteratorPublic BRANDED "HTTP.Iterator" OBJECT
    h: Headers;
    i := 0;
  OVERRIDES
    next := Next;
  END;

(* Headers are few, so a search is linear and keeps their order. *)

PROCEDURE NewHeaders (): Headers =
  BEGIN
    RETURN NEW (Headers);
  END NewHeaders;

PROCEDURE Get (h: Headers; name: TEXT): TEXT =
  BEGIN
    FOR i := 0 TO h.n - 1 DO
      IF TextExtras.CIEqual (h.names[i], name) THEN RETURN h.values[i] END;
    END;
    RETURN NIL;
  END Get;

PROCEDURE Add (h: Headers; name, value: TEXT) =
  BEGIN
    IF h.names = NIL THEN
      h.names := NEW (REF ARRAY OF TEXT, 16);
      h.values := NEW (REF ARRAY OF TEXT, 16);
    ELSIF h.n = NUMBER (h.names^) THEN
      VAR
        names := NEW (REF ARRAY OF TEXT, 2 * h.n);
        values := NEW (REF ARRAY OF TEXT, 2 * h.n);
      BEGIN
        SUBARRAY (names^, 0, h.n) := h.names^;
        SUBARRAY (values^, 0, h.n) := h.values^;
        h.names := names;
        h.values := values;
      END;
    END;
    h.names[h.n] := name;
    h.values[h.n] := value;
    INC (h.n);
  END Add;

PROCEDURE Set (h: Headers; name, value: TEXT) =
  VAR j := 0;  found := FALSE;
  BEGIN
    FOR i := 0 TO h.n - 1 DO
      IF NOT TextExtras.CIEqual (h.names[i], name) THEN
        h.names[j] := h.names[i];
        h.values[j] := h.values[i];
        INC (j);
      ELSIF NOT found THEN
        found := TRUE;
        h.names[j] := h.names[i];
        h.values[j] := value;
        INC (j);
      END;
    END;
    h.n := j;
    IF NOT found THEN Add (h, name, value) END;
  END Set;

PROCEDURE Remove (h: Headers; name: TEXT) =
  VAR j := 0;
  BEGIN
    FOR i := 0 TO h.n - 1 DO
      IF NOT TextExtras.CIEqual (h.names[i], name) THEN
        h.names[j] := h.names[i];
        h.values[j] := h.values[i];
        INC (j);
      END;
    END;
    h.n := j;
  END Remove;

PROCEDURE Iterate (h: Headers): Iterator =
  BEGIN
    RETURN NEW (Iterator, h := h);
  END Iterate;

PROCEDURE Next (it: Iterator; VAR name, value: TEXT): BOOLEAN =
  BEGIN
    IF it.i >= it.h.n THEN RETURN FALSE END;
    name := it.h.names[it.i];
    value := it.h.values[it.i];
    INC (it.i);
    RETURN TRUE;
  END Next;

PROCEDURE ValidName (name: TEXT): BOOLEAN =
  VAR n := Text.Length (name);
  BEGIN
    IF n = 0 OR Text.HasWideChars (name) THEN RETURN FALSE END;
    FOR i := 0 TO n - 1 DO
      IF NOT Text.GetChar (name, i) IN TokenChars THEN RETURN FALSE END;
    END;
    RETURN TRUE;
  END ValidName;

PROCEDURE ValidValue (value: TEXT): BOOLEAN =
  VAR c: CHAR;
  BEGIN
    IF Text.HasWideChars (value) THEN RETURN FALSE END;
    FOR i := 0 TO Text.Length (value) - 1 DO
      c := Text.GetChar (value, i);
      IF c < ' ' AND c # '\t' OR c = '\177' THEN RETURN FALSE END;
    END;
    RETURN TRUE;
  END ValidValue;

(* The bytes of "t": a character up to 255 is a byte, a larger one goes out
   in UTF-8. A pair of surrogates goes out as the one character it stands
   for, and a surrogate without a partner as U+FFFD, as in "Utf8.Encode". *)
PROCEDURE Bytes (t: TEXT): TEXT =
  VAR
    n := Text.Length (t);
    w: REF ARRAY OF WIDECHAR;
    r: REF ARRAY OF CHAR;
    i, j := 0;
    c: INTEGER;

  PROCEDURE Put (b: INTEGER) =
    BEGIN
      r[j] := VAL (b, CHAR);
      INC (j);
    END Put;

  BEGIN
    IF NOT Text.HasWideChars (t) THEN RETURN t END;
    w := NEW (REF ARRAY OF WIDECHAR, n);
    r := NEW (REF ARRAY OF CHAR, 3 * n);
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
      IF c <= 16_FF THEN
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
  END Bytes;

PROCEDURE Escape (t: TEXT; form := FALSE): TEXT =
  BEGIN
    RETURN URI.Escape (Bytes (t), form := form);
  END Escape;

PROCEDURE Unescape (t: TEXT; form := FALSE): TEXT RAISES {Error} =
  VAR r: TEXT;
  BEGIN
    IF NOT URI.Unescape (t, r, form) THEN RAISE Error (BadEscape ()) END;
    RETURN r;
  END Unescape;

(* The text of the error does not come from the message, because atoms are
   never freed and a peer must not be able to make more of them. *)
PROCEDURE BadEscape (): AtomList.T =
  BEGIN
    RETURN AtomList.List2 (BadUrl, Atom.FromText ("malformed escape"));
  END BadEscape;

PROCEDURE Pad (n, width: INTEGER): TEXT =
  BEGIN
    RETURN Fmt.Pad (Fmt.Int (n), width, '0');
  END Pad;

PROCEDURE FormatDate (t: Time.T): TEXT =
  VAR d := Date.FromTime (t, Date.UTC);
  BEGIN
    RETURN WeekDay3[d.weekDay] & ", " & Pad (d.day, 2) & " " & Month3[d.month] & " "
             & Pad (d.year, 4) & " " & Pad (d.hour, 2) & ":" & Pad (d.minute, 2) & ":"
             & Pad (d.second, 2) & " GMT";
  END FormatDate;

TYPE
  Cursor = RECORD
    t: TEXT;
    n, i: INTEGER;
  END;

PROCEDURE Take (VAR c: Cursor; lit: TEXT): BOOLEAN =
  VAR m := Text.Length (lit);
  BEGIN
    IF c.i + m > c.n THEN RETURN FALSE END;
    FOR k := 0 TO m - 1 DO
      IF Text.GetChar (c.t, c.i + k) # Text.GetChar (lit, k) THEN RETURN FALSE END;
    END;
    INC (c.i, m);
    RETURN TRUE;
  END Take;

PROCEDURE Number (VAR c: Cursor; digits: INTEGER; VAR r: INTEGER): BOOLEAN =
  VAR ch: CHAR;
  BEGIN
    IF c.i + digits > c.n THEN RETURN FALSE END;
    r := 0;
    FOR k := 1 TO digits DO
      ch := Text.GetChar (c.t, c.i);
      IF ch < '0' OR ch > '9' THEN RETURN FALSE END;
      r := r * 10 + ORD (ch) - ORD ('0');
      INC (c.i);
    END;
    RETURN TRUE;
  END Number;

PROCEDURE Month (VAR c: Cursor; VAR d: Date.T): BOOLEAN =
  BEGIN
    FOR m := FIRST (Date.Month) TO LAST (Date.Month) DO
      IF Take (c, Month3[m]) THEN
        d.month := m;
        RETURN TRUE;
      END;
    END;
    RETURN FALSE;
  END Month;

PROCEDURE Day (VAR c: Cursor; VAR d: Date.T; digits: INTEGER): BOOLEAN =
  VAR n: INTEGER;
  BEGIN
    IF NOT Number (c, digits, n) OR n < 1 OR n > 31 THEN RETURN FALSE END;
    d.day := n;
    RETURN TRUE;
  END Day;

PROCEDURE Year (VAR c: Cursor; VAR d: Date.T): BOOLEAN =
  VAR n: INTEGER;
  BEGIN
    IF NOT Number (c, 4, n) THEN RETURN FALSE END;
    d.year := n;
    RETURN TRUE;
  END Year;

PROCEDURE ShortYear (VAR c: Cursor; VAR d: Date.T): BOOLEAN =
  VAR n, y: INTEGER;  now := Date.FromTime (Time.Now (), Date.UTC).year;
  BEGIN
    IF NOT Number (c, 2, n) THEN RETURN FALSE END;
    y := now - now MOD 100 + n;
    IF y > now + 50 THEN
      DEC (y, 100);
    ELSIF y <= now - 50 THEN
      INC (y, 100);
    END;
    d.year := y;
    RETURN TRUE;
  END ShortYear;

PROCEDURE Clock (VAR c: Cursor; VAR d: Date.T): BOOLEAN =
  VAR hour, minute, second: INTEGER;
  BEGIN
    IF NOT (Number (c, 2, hour) AND Take (c, ":") AND Number (c, 2, minute)
              AND Take (c, ":") AND Number (c, 2, second)) THEN
      RETURN FALSE;
    END;
    IF hour > 23 OR minute > 59 OR second > 60 THEN RETURN FALSE END;
    d.hour := hour;
    d.minute := minute;
    d.second := second;
    RETURN TRUE;
  END Clock;

PROCEDURE Preferred (VAR c: Cursor; VAR d: Date.T): BOOLEAN =
  BEGIN
    RETURN Day (c, d, 2) AND Take (c, " ") AND Month (c, d) AND Take (c, " ")
             AND Year (c, d) AND Take (c, " ") AND Clock (c, d) AND Take (c, " GMT");
  END Preferred;

PROCEDURE Rfc850 (VAR c: Cursor; VAR d: Date.T): BOOLEAN =
  BEGIN
    RETURN Day (c, d, 2) AND Take (c, "-") AND Month (c, d) AND Take (c, "-")
             AND ShortYear (c, d) AND Take (c, " ") AND Clock (c, d) AND Take (c, " GMT");
  END Rfc850;

PROCEDURE Asctime (VAR c: Cursor; VAR d: Date.T): BOOLEAN =
  VAR ok: BOOLEAN;
  BEGIN
    IF NOT (Month (c, d) AND Take (c, " ")) THEN RETURN FALSE END;
    IF Take (c, " ") THEN
      ok := Day (c, d, 1);
    ELSE
      ok := Day (c, d, 2);
    END;
    RETURN ok AND Take (c, " ") AND Clock (c, d) AND Take (c, " ") AND Year (c, d);
  END Asctime;

PROCEDURE ParseDate (t: TEXT; VAR r: Time.T): BOOLEAN =
  VAR c := Cursor {t, Text.Length (t), 0};  d: Date.T;  ok := FALSE;
  BEGIN
    IF Text.HasWideChars (t) THEN RETURN FALSE END;
    FOR w := FIRST (Date.WeekDay) TO LAST (Date.WeekDay) DO
      IF Take (c, WeekDayFull[w]) THEN
        ok := Take (c, ", ") AND Rfc850 (c, d);
        EXIT;
      ELSIF Take (c, WeekDay3[w]) THEN
        IF Take (c, ", ") THEN
          ok := Preferred (c, d);
        ELSIF Take (c, " ") THEN
          ok := Asctime (c, d);
        END;
        EXIT;
      END;
    END;
    IF NOT ok OR c.i # c.n THEN RETURN FALSE END;
    d.offset := 0;
    d.zone := "GMT";
    TRY
      r := Date.ToTime (d);
    EXCEPT
    | Date.Error => RETURN FALSE;
    END;
    RETURN TRUE;
  END ParseDate;

PROCEDURE BasicAuth (user, password: TEXT): TEXT RAISES {Error} =
  VAR u := Bytes (user);  i: CARDINAL := 0;
  BEGIN
    IF TextExtras.FindChar (u, ':', i) THEN
      RAISE Error (AtomList.List2 (BadMessage, Atom.FromText ("colon in a user name")));
    END;
    RETURN "Basic " & Base64.Encode (u & ":" & Bytes (password));
  END BasicAuth;

PROCEDURE ParseBasicAuth (value: TEXT; VAR user, password: TEXT): BOOLEAN =
  VAR n := Text.Length (value);  i := 5;  k: CARDINAL := 0;  r: TEXT;
  BEGIN
    IF n < 7 OR Text.HasWideChars (value)
         OR NOT TextExtras.CIEqual (Text.Sub (value, 0, 5), "Basic")
         OR Text.GetChar (value, 5) # ' ' THEN
      RETURN FALSE;
    END;
    WHILE i < n AND Text.GetChar (value, i) = ' ' DO INC (i) END;
    IF NOT Base64.Decode (Text.Sub (value, i), r)
         OR NOT TextExtras.FindChar (r, ':', k) THEN
      RETURN FALSE;
    END;
    user := Text.Sub (r, 0, k);
    password := Text.Sub (r, k + 1);
    RETURN TRUE;
  END ParseBasicAuth;

PROCEDURE Reason (status: INTEGER): TEXT =
  BEGIN
    CASE status OF
    | 100 => RETURN "Continue";
    | 101 => RETURN "Switching Protocols";
    | 200 => RETURN "OK";
    | 201 => RETURN "Created";
    | 202 => RETURN "Accepted";
    | 203 => RETURN "Non-Authoritative Information";
    | 204 => RETURN "No Content";
    | 205 => RETURN "Reset Content";
    | 206 => RETURN "Partial Content";
    | 300 => RETURN "Multiple Choices";
    | 301 => RETURN "Moved Permanently";
    | 302 => RETURN "Found";
    | 303 => RETURN "See Other";
    | 304 => RETURN "Not Modified";
    | 305 => RETURN "Use Proxy";
    | 307 => RETURN "Temporary Redirect";
    | 308 => RETURN "Permanent Redirect";
    | 400 => RETURN "Bad Request";
    | 401 => RETURN "Unauthorized";
    | 402 => RETURN "Payment Required";
    | 403 => RETURN "Forbidden";
    | 404 => RETURN "Not Found";
    | 405 => RETURN "Method Not Allowed";
    | 406 => RETURN "Not Acceptable";
    | 407 => RETURN "Proxy Authentication Required";
    | 408 => RETURN "Request Timeout";
    | 409 => RETURN "Conflict";
    | 410 => RETURN "Gone";
    | 411 => RETURN "Length Required";
    | 412 => RETURN "Precondition Failed";
    | 413 => RETURN "Content Too Large";
    | 414 => RETURN "URI Too Long";
    | 415 => RETURN "Unsupported Media Type";
    | 416 => RETURN "Range Not Satisfiable";
    | 417 => RETURN "Expectation Failed";
    | 421 => RETURN "Misdirected Request";
    | 422 => RETURN "Unprocessable Content";
    | 426 => RETURN "Upgrade Required";
    | 428 => RETURN "Precondition Required";
    | 429 => RETURN "Too Many Requests";
    | 431 => RETURN "Request Header Fields Too Large";
    | 451 => RETURN "Unavailable For Legal Reasons";
    | 500 => RETURN "Internal Server Error";
    | 501 => RETURN "Not Implemented";
    | 502 => RETURN "Bad Gateway";
    | 503 => RETURN "Service Unavailable";
    | 504 => RETURN "Gateway Timeout";
    | 505 => RETURN "HTTP Version Not Supported";
    | 506 => RETURN "Variant Also Negotiates";
    | 507 => RETURN "Insufficient Storage";
    | 508 => RETURN "Loop Detected";
    | 510 => RETURN "Not Extended";
    | 511 => RETURN "Network Authentication Required";
    ELSE RETURN "";
    END;
  END Reason;

BEGIN
  Timeout := Atom.FromText ("HTTP.Timeout");
  TooLarge := Atom.FromText ("HTTP.TooLarge");
  BadMessage := Atom.FromText ("HTTP.BadMessage");
  Unreachable := Atom.FromText ("HTTP.Unreachable");
  BadUrl := Atom.FromText ("HTTP.BadUrl");
END HTTP.
