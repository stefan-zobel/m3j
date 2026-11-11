(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Feed;

IMPORT FeedDate, Html, HtmlText, Links, Rd, Text, TextBuf, Thread, XmlReader;

CONST
  AtomNs = "http://www.w3.org/2005/Atom";
  Atom03Ns = "http://purl.org/atom/ns#";
  RdfNs = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
  Rss1Ns = "http://purl.org/rss/1.0/";
  Rss090Ns = "http://my.netscape.com/rdf/simple/0.9/";
  ContentNs = "http://purl.org/rss/1.0/modules/content/";
  DcNs = "http://purl.org/dc/elements/1.1/";
  XmlNs = "http://www.w3.org/XML/1998/namespace";

  TitleChars = 100;             (* of a title made from the text *)

  (* The elements of HTML that have no end tag. *)
  Void = ARRAY OF TEXT {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source",
                        "track", "wbr"};

TYPE
  Reader = XmlReader.T;
  Kind = XmlReader.Kind;

PROCEDURE Trim (t: TEXT): TEXT =
  VAR start := 0;  stop := Text.Length (t);
  BEGIN
    WHILE start < stop AND Text.GetWideChar (t, start) <= W' ' DO INC (start) END;
    WHILE stop > start AND Text.GetWideChar (t, stop - 1) <= W' ' DO DEC (stop) END;
    RETURN Text.Sub (t, start, stop - start);
  END Trim;

PROCEDURE Same (a, b: TEXT): BOOLEAN =
  BEGIN
    RETURN a # NIL AND Text.Equal (a, b);
  END Same;

(* The reader at the elements *)

(* At a "Start": moves to its "End", and returns the text in the element,
   escaped if "escape", and the markup of the elements in it, as HTML. With
   "unwrap", a "div" just inside is left out, as Atom wraps XHTML in one. *)
PROCEDURE Inner (r: Reader; escape: BOOLEAN; unwrap := FALSE): TEXT
  RAISES {XmlReader.Error, Rd.Failure, Thread.Alerted} =
  VAR top := r.depth ();  p := TextBuf.New ();  a: XmlReader.Attribute;  name: TEXT;
  BEGIN
    LOOP
      CASE r.next () OF
      | Kind.Start =>
          IF NOT (unwrap AND r.depth () = top + 1 AND r.is ("div")) THEN
            p.put ("<" & r.local ());
            FOR i := 0 TO r.attrCount () - 1 DO
              a := r.attrAt (i);
              name := a.local;
              IF NOT Text.Empty (a.prefix) THEN name := a.prefix & ":" & name END;
              p.put (" " & name & "=\"" & Html.Escape (a.value) & "\"");
            END;
            p.put (">");
          END;
      | Kind.End =>
          IF r.depth () = top THEN EXIT END;
          IF NOT (unwrap AND r.depth () = top + 1 AND r.is ("div")) AND NOT IsVoid (r.local ()) THEN
            p.put ("</" & r.local () & ">");
          END;
      | Kind.Text =>
          IF escape THEN p.put (Html.Escape (r.value ())) ELSE p.put (r.value ()) END;
      | Kind.EOF => EXIT;
      ELSE
      END;
    END;
    RETURN p.text ();
  END Inner;

PROCEDURE IsVoid (name: TEXT): BOOLEAN =
  BEGIN
    FOR i := FIRST (Void) TO LAST (Void) DO
      IF Text.Equal (name, Void[i]) THEN RETURN TRUE END;
    END;
    RETURN FALSE;
  END IsVoid;

(* The text of the element, which may hold HTML, as plain text. *)
PROCEDURE PlainText (r: Reader): TEXT RAISES {XmlReader.Error, Rd.Failure, Thread.Alerted} =
  BEGIN
    RETURN HtmlText.ToText (Inner (r, escape := FALSE));
  END PlainText;

(* An Atom text construct, as HTML; "" for content of another type, or
   out of line. *)
PROCEDURE Construct (r: Reader): TEXT RAISES {XmlReader.Error, Rd.Failure, Thread.Alerted} =
  VAR type := r.attr ("type");  mode := r.attr ("mode");
  BEGIN
    IF r.attr ("src") # NIL THEN
      r.skip ();
      RETURN "";
    ELSIF type = NIL OR Same (type, "text") OR Same (type, "text/plain") THEN
      IF Same (mode, "escaped") THEN RETURN Inner (r, escape := FALSE) END;  (* Atom 0.3 *)
      RETURN Inner (r, escape := TRUE);
    ELSIF Same (type, "html") OR Same (type, "text/html") THEN
      RETURN Inner (r, escape := FALSE);
    ELSIF Same (type, "xhtml") OR Same (type, "application/xhtml+xml") THEN
      RETURN Inner (r, escape := TRUE, unwrap := TRUE);
    ELSE
      r.skip ();
      RETURN "";
    END;
  END Construct;

(* The base of the element: its "xml:base" against "base". *)
PROCEDURE Base (r: Reader; base: TEXT): TEXT =
  VAR b := Links.Resolve (base, r.attr ("base", XmlNs));
  BEGIN
    IF b = NIL THEN RETURN base END;
    RETURN b;
  END Base;

PROCEDURE SetDate (e: Entry; t: TEXT) =
  BEGIN
    t := Trim (t);
    IF NOT Text.Empty (t) THEN
      e.dateText := t;
      e.dated := FeedDate.Parse (t, e.date);
    END;
  END SetDate;

(* An element of RSS without a namespace, or in that of RSS 1.0 or 0.90. *)
PROCEDURE Rss (r: Reader; local: TEXT): BOOLEAN =
  VAR u := r.uri ();
  BEGIN
    RETURN r.is (local) AND (Text.Empty (u) OR Text.Equal (u, Rss1Ns) OR Text.Equal (u, Rss090Ns));
  END Rss;

PROCEDURE IsAtom (r: Reader; local: TEXT): BOOLEAN =
  BEGIN
    RETURN r.is (local, AtomNs) OR r.is (local, Atom03Ns);
  END IsAtom;

(* RSS 2.0 and 1.0 *)

PROCEDURE Item (r: Reader; base: TEXT): Entry RAISES {XmlReader.Error, Rd.Failure, Thread.Alerted} =
  VAR e := NEW (Entry, base := base);  g, permalink, pubDate, dcDate, enclosure: TEXT := NIL;
  BEGIN
    e.id := r.attr ("about", RdfNs);
    WHILE r.child () DO
      IF Rss (r, "title") THEN
        e.title := PlainText (r);
      ELSIF Rss (r, "link") THEN
        e.link := Links.Resolve (base, Inner (r, escape := FALSE));
      ELSIF Rss (r, "description") THEN
        e.summary := Inner (r, escape := FALSE);
      ELSIF Rss (r, "guid") THEN
        permalink := r.attr ("isPermaLink");
        g := Trim (Inner (r, escape := FALSE));
        IF NOT Text.Empty (g) THEN e.id := g END;
      ELSIF Rss (r, "pubDate") THEN
        pubDate := Inner (r, escape := FALSE);
      ELSIF Rss (r, "author") THEN
        e.author := PlainText (r);
      ELSIF Rss (r, "enclosure") THEN
        enclosure := r.attr ("url");
        r.skip ();
      ELSIF r.is ("encoded", ContentNs) THEN
        e.content := Inner (r, escape := FALSE);
      ELSIF r.is ("date", DcNs) THEN
        dcDate := Inner (r, escape := FALSE);
      ELSIF r.is ("creator", DcNs) THEN
        IF Text.Empty (e.author) THEN e.author := PlainText (r) ELSE r.skip () END;
      ELSE
        r.skip ();
      END;
    END;
    IF e.link = NIL AND g # NIL AND NOT Same (permalink, "false") AND Links.Web (g) THEN e.link := g END;
    IF e.link = NIL THEN e.link := Links.Resolve (base, enclosure) END;  (* an episode of a podcast *)
    IF pubDate # NIL THEN SetDate (e, pubDate) END;
    IF NOT e.dated AND dcDate # NIL THEN SetDate (e, dcDate) END;
    RETURN e;
  END Item;

PROCEDURE Channel (r: Reader; f: T; base: TEXT; VAR items: List)
  RAISES {XmlReader.Error, Rd.Failure, Thread.Alerted} =
  BEGIN
    WHILE r.child () DO
      IF Rss (r, "title") THEN
        f.title := PlainText (r);
      ELSIF Rss (r, "link") THEN
        f.link := Links.Resolve (base, Inner (r, escape := FALSE));
      ELSIF Rss (r, "description") THEN
        f.description := PlainText (r);
      ELSIF Rss (r, "item") THEN
        Add (items, Item (r, base));
      ELSE
        r.skip ();
      END;
    END;
  END Channel;

(* Atom 1.0, and 0.3 as far as it is the same *)

PROCEDURE AtomEntry (r: Reader; base: TEXT): Entry RAISES {XmlReader.Error, Rd.Failure, Thread.Alerted} =
  VAR e := NEW (Entry);  published, updated: TEXT := NIL;  rel: TEXT;
  BEGIN
    base := Base (r, base);
    e.base := base;
    WHILE r.child () DO
      IF IsAtom (r, "id") THEN
        e.id := Trim (Inner (r, escape := FALSE));
      ELSIF IsAtom (r, "title") THEN
        e.title := HtmlText.ToText (Construct (r));
      ELSIF IsAtom (r, "link") THEN
        rel := r.attr ("rel");
        IF e.link = NIL AND (rel = NIL OR Same (rel, "alternate")) THEN
          e.link := Links.Resolve (Base (r, base), r.attr ("href"));
        END;
        r.skip ();
      ELSIF IsAtom (r, "published") OR IsAtom (r, "issued") THEN
        published := Inner (r, escape := FALSE);
      ELSIF IsAtom (r, "updated") OR IsAtom (r, "modified") THEN
        updated := Inner (r, escape := FALSE);
      ELSIF IsAtom (r, "author") THEN
        WHILE r.child () DO
          IF IsAtom (r, "name") AND Text.Empty (e.author) THEN
            e.author := Trim (Inner (r, escape := FALSE));
          ELSE
            r.skip ();
          END;
        END;
      ELSIF IsAtom (r, "summary") THEN
        e.summary := Construct (r);
      ELSIF IsAtom (r, "content") THEN
        e.content := Construct (r);
      ELSE
        r.skip ();
      END;
    END;
    IF published # NIL THEN SetDate (e, published) END;
    IF NOT e.dated AND updated # NIL THEN SetDate (e, updated) END;
    RETURN e;
  END AtomEntry;

PROCEDURE AtomFeed (r: Reader; f: T; base: TEXT; VAR items: List)
  RAISES {XmlReader.Error, Rd.Failure, Thread.Alerted} =
  VAR rel: TEXT;
  BEGIN
    base := Base (r, base);
    WHILE r.child () DO
      IF IsAtom (r, "title") THEN
        f.title := HtmlText.ToText (Construct (r));
      ELSIF IsAtom (r, "subtitle") OR IsAtom (r, "tagline") THEN
        f.description := HtmlText.ToText (Construct (r));
      ELSIF IsAtom (r, "link") THEN
        rel := r.attr ("rel");
        IF f.link = NIL AND (rel = NIL OR Same (rel, "alternate")) THEN
          f.link := Links.Resolve (Base (r, base), r.attr ("href"));
        END;
        r.skip ();
      ELSIF IsAtom (r, "entry") THEN
        Add (items, AtomEntry (r, base));
      ELSE
        r.skip ();
      END;
    END;
  END AtomFeed;

(* The entries so far. *)

TYPE
  List = RECORD
    a: Entries := NIL;
    n := 0;
  END;

PROCEDURE Add (VAR l: List; e: Entry) =
  VAR b: Entries;
  BEGIN
    IF l.a = NIL THEN
      l.a := NEW (Entries, 16);
    ELSIF l.n = NUMBER (l.a^) THEN
      b := NEW (Entries, 2 * l.n);
      SUBARRAY (b^, 0, l.n) := l.a^;
      l.a := b;
    END;
    l.a[l.n] := e;
    INC (l.n);
  END Add;

PROCEDURE Parse (rd: Rd.T; url: TEXT): T RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR r := XmlReader.FromRd (rd);  f: T;  items: List;  e: Entry;
  BEGIN
    TRY
      r.root ();
      IF r.is ("rss") THEN
        f := NEW (T, format := Format.RSS);
        WHILE r.child () DO
          IF r.is ("channel") THEN Channel (r, f, url, items) ELSE r.skip () END;
        END;
      ELSIF r.is ("RDF", RdfNs) THEN
        f := NEW (T, format := Format.RDF);
        WHILE r.child () DO
          IF Rss (r, "channel") THEN
            Channel (r, f, url, items);
          ELSIF Rss (r, "item") THEN
            Add (items, Item (r, url));
          ELSE
            r.skip ();
          END;
        END;
      ELSIF IsAtom (r, "feed") THEN
        f := NEW (T, format := Format.Atom);
        AtomFeed (r, f, url, items);
      ELSE
        RAISE Error ("not a feed: the root element is <" & r.qname () & ">");
      END;
    EXCEPT
    | XmlReader.Error (x) => RAISE Error (XmlReader.Format (x));
    END;
    IF f.link = NIL THEN f.link := Links.Resolve (url, "/") END;  (* the site, at least *)
    f.entries := NEW (Entries, items.n);
    FOR i := 0 TO items.n - 1 DO
      e := items.a[i];
      e.summary := Trim (e.summary);
      e.content := Trim (e.content);
      IF Text.Empty (e.title) THEN
        e.title := HtmlText.Excerpt (HtmlText.ToText (e.summary & " " & e.content), TitleChars);
      END;
      f.entries[i] := e;
    END;
    RETURN f;
  END Parse;

BEGIN
END Feed.
