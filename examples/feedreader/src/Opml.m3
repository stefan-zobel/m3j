(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Opml;

IMPORT Links, Rd, Text, Thread, Wr, XmlReader, XmlWriter;

TYPE List = RECORD a: Outlines := NIL; n := 0 END;

PROCEDURE Add (VAR l: List; READONLY o: Outline) =
  VAR b: Outlines;
  BEGIN
    IF l.a = NIL THEN
      l.a := NEW (Outlines, 16);
    ELSIF l.n = NUMBER (l.a^) THEN
      b := NEW (Outlines, 2 * l.n);
      SUBARRAY (b^, 0, l.n) := l.a^;
      l.a := b;
    END;
    l.a[l.n] := o;
    INC (l.n);
  END Add;

PROCEDURE Seen (READONLY l: List; url: TEXT): BOOLEAN =
  BEGIN
    FOR i := 0 TO l.n - 1 DO
      IF Text.Equal (l.a[i].xmlUrl, url) THEN RETURN TRUE END;
    END;
    RETURN FALSE;
  END Seen;

(* At an "outline": adds it if it names a feed, and the feeds in it. *)
PROCEDURE Outline1 (r: XmlReader.T; VAR l: List) RAISES {XmlReader.Error, Rd.Failure, Thread.Alerted} =
  VAR o: Outline;  url := Links.Resolve (NIL, r.attr ("xmlUrl"));
  BEGIN
    IF url # NIL AND NOT Seen (l, url) THEN
      o.xmlUrl := url;
      o.title := r.attr ("title");
      IF o.title = NIL OR Text.Empty (o.title) THEN o.title := r.attr ("text") END;
      IF o.title = NIL THEN o.title := "" END;
      o.htmlUrl := Links.Resolve (NIL, r.attr ("htmlUrl"));
      Add (l, o);
    END;
    WHILE r.child () DO
      IF r.is ("outline") THEN Outline1 (r, l) ELSE r.skip () END;
    END;
  END Outline1;

PROCEDURE Read (rd: Rd.T): Outlines RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR r := XmlReader.FromRd (rd);  l: List;  result: Outlines;
  BEGIN
    TRY
      r.root ("opml");
      WHILE r.child () DO
        IF r.is ("body") THEN
          WHILE r.child () DO
            IF r.is ("outline") THEN Outline1 (r, l) ELSE r.skip () END;
          END;
        ELSE
          r.skip ();
        END;
      END;
    EXCEPT
    | XmlReader.Error (f) =>
        IF f.problem = XmlReader.Problem.Unexpected THEN RAISE Error ("not a list of subscriptions in OPML") END;
        RAISE Error (XmlReader.Format (f));
    END;
    result := NEW (Outlines, l.n);
    IF l.n > 0 THEN result^ := SUBARRAY (l.a^, 0, l.n) END;
    RETURN result;
  END Read;

PROCEDURE Write (wr: Wr.T; title: TEXT; READONLY feeds: ARRAY OF Outline) RAISES {Wr.Failure, Thread.Alerted} =
  <*FATAL XmlWriter.Error*>  (* names and the order of the calls are fixed; texts are
                                checked with "replaceInvalid" *)
  VAR w := XmlWriter.ToWr (wr, XmlWriter.Options {indent := "  ", replaceInvalid := TRUE});
  BEGIN
    w.start ("opml");
    w.attr ("version", "2.0");
    w.start ("head");
    w.element ("title", title);
    w.end ();
    w.start ("body");
    FOR i := FIRST (feeds) TO LAST (feeds) DO
      w.start ("outline");
      w.attr ("type", "rss");
      w.attr ("text", feeds[i].title);
      w.attr ("title", feeds[i].title);
      w.attr ("xmlUrl", feeds[i].xmlUrl);
      IF feeds[i].htmlUrl # NIL THEN w.attr ("htmlUrl", feeds[i].htmlUrl) END;
      w.end ();
    END;
    w.end ();
    w.end ();
    w.finish ();
  END Write;

BEGIN
END Opml.
