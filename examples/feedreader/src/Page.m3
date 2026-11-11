(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Page;

IMPORT FeedDate, Html, Text, TextBuf, Time;

PROCEDURE E (t: TEXT): TEXT =
  BEGIN
    IF t = NIL THEN RETURN "" END;
    RETURN Html.Escape (t);
  END E;

PROCEDURE Marked (t: TEXT): TEXT =
  VAR e := E (t);  b: TextBuf.T;  start := 0;  c: WIDECHAR;
  BEGIN
    IF Text.FindChar (e, '\001') < 0 AND Text.FindChar (e, '\002') < 0 THEN RETURN e END;
    b := TextBuf.New ();
    FOR i := 0 TO Text.Length (e) - 1 DO
      c := Text.GetWideChar (e, i);
      IF c = W'\000001' OR c = W'\000002' THEN
        b.put (Text.Sub (e, start, i - start));
        IF c = W'\000001' THEN b.put ("<mark>") ELSE b.put ("</mark>") END;
        start := i + 1;
      END;
    END;
    b.put (Text.Sub (e, start));
    RETURN b.text ();
  END Marked;

PROCEDURE When (t: Time.T): TEXT =
  VAR iso := FeedDate.Format (t);
  BEGIN
    RETURN "<time datetime=\"" & iso & "\">" & Text.Sub (iso, 0, 10) & " " & Text.Sub (iso, 11, 5)
             & " UTC</time>";
  END When;

PROCEDURE Hidden (b: TextBuf.T; name, value: TEXT) =
  BEGIN
    b.put ("<input type=\"hidden\" name=\"" & E (name) & "\" value=\"" & E (value) & "\">");
  END Hidden;

PROCEDURE Begin (title: TEXT; user, csrf, query: TEXT := NIL): TextBuf.T =
  VAR b := TextBuf.New ();
  BEGIN
    b.put ("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
             & "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
             & "<title>" & E (title) & " &#8211; feedreader</title>\n");
    b.put ("<link rel=\"stylesheet\" href=\"/static/style.css\">\n"
             & "<script src=\"/static/app.js\" defer></script>\n</head>\n<body>\n");
    b.put ("<header class=\"top\">\n<a class=\"brand\" href=\"/\">feedreader</a>\n");
    IF user # NIL THEN
      b.put ("<form class=\"search\" action=\"/\" method=\"get\" role=\"search\">"
               & "<input type=\"search\" name=\"q\" placeholder=\"Search\" aria-label=\"Search\" value=\""
               & E (query) & "\"></form>\n");
      b.put ("<nav class=\"links\"><a href=\"/feeds\">Feeds</a> <span class=\"who\">" & E (user) & "</span> "
               & "<form method=\"post\" action=\"/logout\">");
      Hidden (b, "csrf", csrf);
      b.put ("<button class=\"link\">Log out</button></form></nav>\n");
    END;
    b.put ("</header>\n");
    IF user # NIL THEN
      b.put ("<p id=\"news\" class=\"news\" hidden><a href=\"\">New entries have come in: show them</a></p>\n");
    END;
    RETURN b;
  END Begin;

PROCEDURE End (b: TextBuf.T): TEXT =
  BEGIN
    b.put ("\n</body>\n</html>\n");
    RETURN b.text ();
  END End;

BEGIN
END Page.
