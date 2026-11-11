(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Discover;

IMPORT Html, Links, Regexp, Text;

CONST Types = ARRAY OF TEXT {"application/rss+xml", "application/atom+xml", "application/rdf+xml"};

VAR link, rel, type, href, alternate: Regexp.T;  (* compiled once, by the main body *)

(* The value of the attribute that "re" finds in "tag", NIL if none. *)
PROCEDURE Attr (re: Regexp.T; tag: TEXT): TEXT RAISES {Regexp.TooComplex} =
  VAR m := Regexp.Find (re, tag);  v: TEXT;
  BEGIN
    IF m = NIL THEN RETURN NIL END;
    v := m.group (1);
    IF v = NIL THEN v := m.group (2) END;
    IF v = NIL THEN v := m.group (3) END;
    RETURN Html.Unescape (v);
  END Attr;

PROCEDURE Lower (t: TEXT): TEXT =
  VAR a := NEW (REF ARRAY OF CHAR, Text.Length (t));
  BEGIN
    IF Text.HasWideChars (t) THEN RETURN t END;
    Text.SetChars (a^, t);
    FOR i := 0 TO LAST (a^) DO
      IF a[i] >= 'A' AND a[i] <= 'Z' THEN a[i] := VAL (ORD (a[i]) + 32, CHAR) END;
    END;
    RETURN Text.FromChars (a^);
  END Lower;

PROCEDURE IsFeedType (t: TEXT): BOOLEAN =
  BEGIN
    IF t = NIL THEN RETURN FALSE END;
    t := Lower (t);
    FOR i := FIRST (Types) TO LAST (Types) DO
      IF Text.Equal (t, Types[i]) THEN RETURN TRUE END;
    END;
    RETURN FALSE;
  END IsFeedType;

PROCEDURE Feeds (html, base: TEXT): URLs =
  VAR m: Regexp.Match;  tag, r, url: TEXT;  a := NEW (URLs, 8);  n := 0;  b: URLs;  seen: BOOLEAN;
  BEGIN
    TRY
      m := Regexp.Find (link, html);
      WHILE m # NIL AND n < NUMBER (a^) DO
        tag := m.group (0);
        r := Attr (rel, tag);
        IF r # NIL AND Regexp.Find (alternate, r) # NIL AND IsFeedType (Attr (type, tag)) THEN
          url := Links.Resolve (base, Attr (href, tag));
          seen := FALSE;
          FOR i := 0 TO n - 1 DO
            IF url # NIL AND Text.Equal (a[i], url) THEN seen := TRUE END;
          END;
          IF url # NIL AND NOT seen THEN
            a[n] := url;
            INC (n);
          END;
        END;
        m := Regexp.Next (m);
      END;
    EXCEPT
    | Regexp.TooComplex => (* what was found so far *)
    END;
    b := NEW (URLs, n);
    b^ := SUBARRAY (a^, 0, n);
    RETURN b;
  END Feeds;

(* A pattern for the value of the attribute "name": in double quotes, in
   single quotes or without quotes, as groups 1, 2 and 3. *)
PROCEDURE AttrPattern (name: TEXT): Regexp.T RAISES {Regexp.Error} =
  BEGIN
    RETURN Regexp.Compile ("\\s" & name & "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))",
                           Regexp.Options {Regexp.Option.IgnoreCase});
  END AttrPattern;

<*FATAL Regexp.Error*>
BEGIN
  link := Regexp.Compile ("<link\\b[^>]*>", Regexp.Options {Regexp.Option.IgnoreCase});
  rel := AttrPattern ("rel");
  type := AttrPattern ("type");
  href := AttrPattern ("href");
  alternate := Regexp.Compile ("(^|\\s)alternate(\\s|$)", Regexp.Options {Regexp.Option.IgnoreCase});
END Discover.
