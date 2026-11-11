(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE HtmlText;

IMPORT Html, Regexp, Text;

VAR
  hidden, inline, tag, space: Regexp.T;  (* compiled once, by the main body *)

PROCEDURE ToText (html: TEXT): TEXT =
  VAR t := html;  n: INTEGER;
  BEGIN
    IF Text.FindChar (t, '<') >= 0 THEN
      TRY
        t := Regexp.Replace (hidden, t, " ");
        t := Regexp.Replace (inline, t, "");
        t := Regexp.Replace (tag, t, " ");
      EXCEPT
      | Regexp.TooComplex, Regexp.Error => t := html;
      END;
    END;
    t := Html.Unescape (t);
    TRY
      t := Regexp.Replace (space, t, " ");
    EXCEPT
    | Regexp.TooComplex, Regexp.Error => (* keep it *)
    END;
    n := Text.Length (t);
    IF n > 0 AND Text.GetWideChar (t, 0) = W' ' THEN t := Text.Sub (t, 1); DEC (n) END;
    IF n > 0 AND Text.GetWideChar (t, n - 1) = W' ' THEN t := Text.Sub (t, 0, n - 1) END;
    RETURN t;
  END ToText;

PROCEDURE Excerpt (text: TEXT; max: CARDINAL): TEXT =
  VAR cut: INTEGER;
  BEGIN
    IF Text.Length (text) <= max THEN RETURN text END;
    cut := Text.FindCharR (text, ' ', max);
    IF cut < max DIV 2 THEN cut := max END;
    RETURN Text.Sub (text, 0, cut) & "...";
  END Excerpt;

<*FATAL Regexp.Error*>
BEGIN
  (* scripts, styles and comments, with what is in them *)
  hidden := Regexp.Compile ("<(script|style)\\b.*?</\\1\\s*>|<!--.*?(-->|$)",
                            Regexp.Options {Regexp.Option.IgnoreCase, Regexp.Option.DotAll});
  (* the tags of elements inside a line, which leave no space *)
  inline := Regexp.Compile ("</?(a|abbr|b|bdi|bdo|cite|code|data|dfn|em|font|i|kbd|mark|q|s|samp|small|span"
                              & "|strong|sub|sup|time|tt|u|var)\\b[^>]*>",
                            Regexp.Options {Regexp.Option.IgnoreCase});
  (* the other tags, and declarations *)
  tag := Regexp.Compile ("</?[A-Za-z][^>]*>|<![^>]*>|<\\?[^>]*>");
  space := Regexp.Compile ("[\\s\\u00A0\\u2028\\u2029]+");
END HtmlText.
