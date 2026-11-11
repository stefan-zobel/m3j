(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Links;

IMPORT ASCII, Text, URI, Utf8;

CONST
  Digits = "0123456789ABCDEF";
  (* What RFC 3986 allows in a URI, "%" included. *)
  Allowed = SET OF CHAR {'a' .. 'z', 'A' .. 'Z', '0' .. '9', '-', '.', '_', '~', ':', '/', '?', '#', '[', ']',
                         '@', '!', '$', '&','\'', '(', ')', '*', '+', ',', ';', '=', '%'};

PROCEDURE Lower (t: TEXT): TEXT =
  VAR a := NEW (REF ARRAY OF CHAR, Text.Length (t));
  BEGIN
    Text.SetChars (a^, t);
    FOR i := 0 TO LAST (a^) DO a[i] := ASCII.Lower[a[i]] END;
    RETURN Text.FromChars (a^);
  END Lower;

(* "t" without white space around it, in UTF-8, with every byte escaped
   that a URI may not have. *)
PROCEDURE Clean (t: TEXT): TEXT =
  VAR start := 0;  stop := Text.Length (t);  b: TEXT;  a: REF ARRAY OF CHAR;  n := 0;  c: CHAR;  extra := 0;
  BEGIN
    WHILE start < stop AND Text.GetWideChar (t, start) <= W' ' DO INC (start) END;
    WHILE stop > start AND Text.GetWideChar (t, stop - 1) <= W' ' DO DEC (stop) END;
    b := Utf8.Encode (Text.Sub (t, start, stop - start));
    FOR i := 0 TO Text.Length (b) - 1 DO
      IF NOT Text.GetChar (b, i) IN Allowed THEN INC (extra, 2) END;
    END;
    IF extra = 0 THEN RETURN b END;
    a := NEW (REF ARRAY OF CHAR, Text.Length (b) + extra);
    FOR i := 0 TO Text.Length (b) - 1 DO
      c := Text.GetChar (b, i);
      IF c IN Allowed THEN
        a[n] := c;
        INC (n);
      ELSE
        a[n] := '%';
        a[n + 1] := Text.GetChar (Digits, ORD (c) DIV 16);
        a[n + 2] := Text.GetChar (Digits, ORD (c) MOD 16);
        INC (n, 3);
      END;
    END;
    RETURN Text.FromChars (a^);
  END Clean;

PROCEDURE IsWeb (READONLY u: URI.T): BOOLEAN =
  VAR s: TEXT;
  BEGIN
    IF u.scheme = NIL OR u.host = NIL OR Text.Empty (u.host) OR Text.FindChar (u.host, '%') >= 0 THEN
      RETURN FALSE;                (* a host with an escape is a typing error *)
    END;
    s := Lower (u.scheme);
    RETURN Text.Equal (s, "http") OR Text.Equal (s, "https");
  END IsWeb;

PROCEDURE Resolve (base, ref: TEXT): TEXT =
  VAR r, b, u: URI.T;
  BEGIN
    IF ref = NIL THEN RETURN NIL END;
    ref := Clean (ref);
    IF Text.Empty (ref) OR NOT URI.Parse (ref, r) THEN RETURN NIL END;
    IF r.scheme # NIL THEN
      u := r;
    ELSIF base = NIL OR NOT URI.Parse (Clean (base), b) OR b.scheme = NIL THEN
      RETURN NIL;
    ELSE
      u := URI.Resolve (b, r);
    END;
    IF NOT IsWeb (u) THEN RETURN NIL END;
    u.scheme := Lower (u.scheme);
    RETURN URI.Format (u);
  END Resolve;

PROCEDURE Web (url: TEXT): BOOLEAN =
  VAR u: URI.T;
  BEGIN
    RETURN url # NIL AND URI.Parse (url, u) AND IsWeb (u);
  END Web;

PROCEDURE Host (url: TEXT): TEXT =
  VAR u: URI.T;
  BEGIN
    IF url = NIL OR NOT URI.Parse (url, u) OR u.host = NIL THEN RETURN "" END;
    RETURN Lower (u.host);
  END Host;

BEGIN
END Links.
