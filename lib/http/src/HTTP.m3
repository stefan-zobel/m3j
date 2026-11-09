(* Copyright (C) 1995, Digital Equipment Corporation. *)
(* All rights reserved. *)
(* Parts Copyright (C) 1997, Columbia University *)
(* All rights reserved. *)
(* See the file COPYRIGHT for a full description. *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed under
   the MIT license in the file LICENSE. *)

(* The tables and the outline of the escapes and of FormatDate come from
   the package m3-www/http of CM3; the rest is new. *)

MODULE HTTP;

IMPORT Atom, AtomList, Date, Fmt, Text, TextExtras, Time;

CONST
  Unreserved = SET OF CHAR {'A' .. 'Z', 'a' .. 'z', '0' .. '9', '-', '.', '_', '~'};
  TokenChars = SET OF CHAR {'A' .. 'Z', 'a' .. 'z', '0' .. '9', '!', '#', '$', '%',
                            '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~'};
  Hex = ARRAY [0 .. 15] OF CHAR {'0', '1', '2', '3', '4', '5', '6', '7',
                                 '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

  WeekDay3 = ARRAY Date.WeekDay OF TEXT {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
  WeekDayFull = ARRAY Date.WeekDay OF TEXT {"Sunday", "Monday", "Tuesday", "Wednesday",
                                            "Thursday", "Friday", "Saturday"};
  Month3 = ARRAY Date.Month OF TEXT {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                                     "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

  Base64Decode = ARRAY CHAR OF INTEGER {
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 62, -1, -1, -1, 63, (* +, / *)
    52, 53, 54, 55, 56, 57, 58, 59, 60, 61, -1, -1, -1, -1, -1, -1, (* 0..9 *)
    -1, 00, 01, 02, 03, 04, 05, 06, 07, 08, 09, 10, 11, 12, 13, 14, (* A..O *)
    15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, -1, -1, -1, -1, -1, (* P..Z *)
    -1, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, (* a..o *)
    41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, -1, ..};            (* p..z *)

  Base64Encode = ARRAY [0 .. 63] OF CHAR {
    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
    'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
    'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
    'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
    'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
    'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
    'w', 'x', 'y', 'z', '0', '1', '2', '3',
    '4', '5', '6', '7', '8', '9', '+', '/'};

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
   in UTF-8. *)
PROCEDURE Bytes (t: TEXT): REF ARRAY OF CHAR =
  VAR n := Text.Length (t);  size := 0;  c: INTEGER;  r: REF ARRAY OF CHAR;  j := 0;

  PROCEDURE Put (b: INTEGER) =
    BEGIN
      r[j] := VAL (b, CHAR);
      INC (j);
    END Put;

  BEGIN
    FOR i := 0 TO n - 1 DO
      c := ORD (Text.GetWideChar (t, i));
      IF c <= 16_FF THEN
        INC (size);
      ELSIF c <= 16_7FF THEN
        INC (size, 2);
      ELSIF c <= 16_FFFF THEN
        INC (size, 3);
      ELSE
        INC (size, 4);
      END;
    END;
    r := NEW (REF ARRAY OF CHAR, size);
    FOR i := 0 TO n - 1 DO
      c := ORD (Text.GetWideChar (t, i));
      IF c <= 16_FF THEN
        Put (c);
      ELSIF c <= 16_7FF THEN
        Put (16_C0 + c DIV 64);
        Put (16_80 + c MOD 64);
      ELSIF c <= 16_FFFF THEN
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
    RETURN r;
  END Bytes;

PROCEDURE Escape (t: TEXT; form := FALSE): TEXT =
  VAR b := Bytes (t);  size := 0;  r: REF ARRAY OF CHAR;  j := 0;  c: CHAR;
  BEGIN
    FOR i := 0 TO LAST (b^) DO
      IF b[i] IN Unreserved OR form AND b[i] = ' ' THEN
        INC (size);
      ELSE
        INC (size, 3);
      END;
    END;
    r := NEW (REF ARRAY OF CHAR, size);
    FOR i := 0 TO LAST (b^) DO
      c := b[i];
      IF c IN Unreserved THEN
        r[j] := c;
        INC (j);
      ELSIF form AND c = ' ' THEN
        r[j] := '+';
        INC (j);
      ELSE
        r[j] := '%';
        r[j + 1] := Hex[ORD (c) DIV 16];
        r[j + 2] := Hex[ORD (c) MOD 16];
        INC (j, 3);
      END;
    END;
    RETURN Text.FromChars (r^);
  END Escape;

PROCEDURE HexValue (c: CHAR): INTEGER =
  BEGIN
    CASE c OF
    | '0' .. '9' => RETURN ORD (c) - ORD ('0');
    | 'A' .. 'F' => RETURN ORD (c) - ORD ('A') + 10;
    | 'a' .. 'f' => RETURN ORD (c) - ORD ('a') + 10;
    ELSE RETURN -1;
    END;
  END HexValue;

PROCEDURE Unescape (t: TEXT; form := FALSE): TEXT RAISES {Error} =
  VAR n := Text.Length (t);  r := NEW (REF ARRAY OF CHAR, n);  i := 0;  j := 0;
      c: CHAR;  high, low: INTEGER;
  BEGIN
    IF Text.HasWideChars (t) THEN RAISE Error (BadEscape ()) END;
    WHILE i < n DO
      c := Text.GetChar (t, i);
      INC (i);
      IF c = '%' THEN
        IF i + 2 > n THEN RAISE Error (BadEscape ()) END;
        high := HexValue (Text.GetChar (t, i));
        low := HexValue (Text.GetChar (t, i + 1));
        IF high < 0 OR low < 0 THEN RAISE Error (BadEscape ()) END;
        c := VAL (high * 16 + low, CHAR);
        INC (i, 2);
      ELSIF form AND c = '+' THEN
        c := ' ';
      END;
      r[j] := c;
      INC (j);
    END;
    RETURN Text.FromChars (SUBARRAY (r^, 0, j));
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

PROCEDURE Base64 (READONLY b: ARRAY OF CHAR): TEXT =
  VAR n := NUMBER (b);  r := NEW (REF ARRAY OF CHAR, (n + 2) DIV 3 * 4);
      i := 0;  j := 0;  v: INTEGER;
  BEGIN
    WHILE n - i >= 3 DO
      v := ORD (b[i]) * 65536 + ORD (b[i + 1]) * 256 + ORD (b[i + 2]);
      r[j] := Base64Encode[v DIV 262144];
      r[j + 1] := Base64Encode[v DIV 4096 MOD 64];
      r[j + 2] := Base64Encode[v DIV 64 MOD 64];
      r[j + 3] := Base64Encode[v MOD 64];
      INC (i, 3);
      INC (j, 4);
    END;
    IF n - i = 1 THEN
      v := ORD (b[i]);
      r[j] := Base64Encode[v DIV 4];
      r[j + 1] := Base64Encode[v MOD 4 * 16];
      r[j + 2] := '=';
      r[j + 3] := '=';
    ELSIF n - i = 2 THEN
      v := ORD (b[i]) * 256 + ORD (b[i + 1]);
      r[j] := Base64Encode[v DIV 1024];
      r[j + 1] := Base64Encode[v DIV 16 MOD 64];
      r[j + 2] := Base64Encode[v MOD 16 * 4];
      r[j + 3] := '=';
    END;
    RETURN Text.FromChars (r^);
  END Base64;

(* The bytes of the Base64 in "t" from the index "start" on into "r", and
   their number; -1 if it is malformed. *)
PROCEDURE FromBase64 (t: TEXT; start: INTEGER; VAR r: ARRAY OF CHAR): INTEGER =
  VAR n := Text.Length (t) - start;  j := 0;  i := start;  last := start + n - 4;
      a, b, c, d: INTEGER;  c2, c3: CHAR;
  BEGIN
    IF n MOD 4 # 0 THEN RETURN -1 END;
    WHILE i <= last DO
      a := Base64Decode[Text.GetChar (t, i)];
      b := Base64Decode[Text.GetChar (t, i + 1)];
      c2 := Text.GetChar (t, i + 2);
      c3 := Text.GetChar (t, i + 3);
      IF a < 0 OR b < 0 THEN RETURN -1 END;
      r[j] := VAL (a * 4 + b DIV 16, CHAR);
      INC (j);
      IF i = last AND c2 = '=' THEN
        IF c3 # '=' THEN RETURN -1 END;
      ELSIF i = last AND c3 = '=' THEN
        c := Base64Decode[c2];
        IF c < 0 THEN RETURN -1 END;
        r[j] := VAL (b MOD 16 * 16 + c DIV 4, CHAR);
        INC (j);
      ELSE
        c := Base64Decode[c2];
        d := Base64Decode[c3];
        IF c < 0 OR d < 0 THEN RETURN -1 END;
        r[j] := VAL (b MOD 16 * 16 + c DIV 4, CHAR);
        r[j + 1] := VAL (c MOD 4 * 64 + d, CHAR);
        INC (j, 2);
      END;
      INC (i, 4);
    END;
    RETURN j;
  END FromBase64;

PROCEDURE BasicAuth (user, password: TEXT): TEXT RAISES {Error} =
  VAR i: CARDINAL := 0;
  BEGIN
    IF TextExtras.FindChar (user, ':', i) THEN
      RAISE Error (AtomList.List2 (BadMessage, Atom.FromText ("colon in a user name")));
    END;
    RETURN "Basic " & Base64 (Bytes (user & ":" & password)^);
  END BasicAuth;

PROCEDURE ParseBasicAuth (value: TEXT; VAR user, password: TEXT): BOOLEAN =
  VAR n := Text.Length (value);  i := 5;  m: INTEGER;  r: REF ARRAY OF CHAR;
  BEGIN
    IF n < 7 OR Text.HasWideChars (value)
         OR NOT TextExtras.CIEqual (Text.Sub (value, 0, 5), "Basic")
         OR Text.GetChar (value, 5) # ' ' THEN
      RETURN FALSE;
    END;
    WHILE i < n AND Text.GetChar (value, i) = ' ' DO INC (i) END;
    r := NEW (REF ARRAY OF CHAR, (n - i) DIV 4 * 3 + 3);
    m := FromBase64 (value, i, r^);
    IF m < 0 THEN RETURN FALSE END;
    FOR k := 0 TO m - 1 DO
      IF r[k] = ':' THEN
        user := Text.FromChars (SUBARRAY (r^, 0, k));
        password := Text.FromChars (SUBARRAY (r^, k + 1, m - k - 1));
        RETURN TRUE;
      END;
    END;
    RETURN FALSE;
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
