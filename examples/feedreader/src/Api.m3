(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Api;

IMPORT Auth, Events, FeedDate, Fetcher, FloatMode, HTTPClient, HTTPServer, JsonReader, JsonWriter, Lex, Links,
       Log, Rd, Scan, Store, Text, TextRd, Thread, Time, URI, Utf8, Wr;

CONST
  MaxBody = 64 * 1024;
  MaxEntries = 200;

TYPE Problem = REF RECORD status: INTEGER; message: TEXT END;

EXCEPTION Fail (Problem);       (* answer with this error *)

PROCEDURE Oops (status: INTEGER; message: TEXT) RAISES {Fail} =
  BEGIN
    RAISE Fail (NEW (Problem, status := status, message := message));
  END Oops;

PROCEDURE Prefix (t, p: TEXT): BOOLEAN =
  BEGIN
    RETURN Text.Length (t) >= Text.Length (p) AND Text.Equal (Text.Sub (t, 0, Text.Length (p)), p);
  END Prefix;

PROCEDURE Is (a, b: TEXT): BOOLEAN =
  BEGIN
    RETURN a # NIL AND Text.Equal (a, b);
  END Is;

PROCEDURE Int (t: TEXT; default: INTEGER): INTEGER =
  BEGIN
    IF t = NIL THEN RETURN default END;
    TRY
      RETURN Scan.Int (t);
    EXCEPT
    | Lex.Error, FloatMode.Trap => RETURN default;
    END;
  END Int;

(* The value of the query parameter "name", decoded, NIL if none. *)
PROCEDURE Param (req: HTTPServer.Request; name: TEXT): TEXT =
  VAR p: URI.Pairs;  r: TEXT;
  BEGIN
    IF req.query = NIL OR NOT URI.ParseQuery (req.query, p) THEN RETURN NIL END;
    FOR i := 0 TO LAST (p^) DO
      IF Text.Equal (p[i].name, name) THEN
        IF Utf8.Decode (p[i].value, r) THEN RETURN r END;
        RETURN p[i].value;
      END;
    END;
    RETURN NIL;
  END Param;

(* "t" without the marks of a search. *)
PROCEDURE Unmarked (t: TEXT): TEXT =
  VAR a := NEW (REF ARRAY OF WIDECHAR, Text.Length (t));  n := 0;  c: WIDECHAR;
  BEGIN
    IF Text.FindChar (t, '\001') < 0 AND Text.FindChar (t, '\002') < 0 THEN RETURN t END;
    FOR i := 0 TO Text.Length (t) - 1 DO
      c := Text.GetWideChar (t, i);
      IF c # W'\000001' AND c # W'\000002' THEN a[n] := c; INC (n) END;
    END;
    RETURN Text.FromWideChars (SUBARRAY (a^, 0, n));
  END Unmarked;

PROCEDURE Opt (w: JsonWriter.T; key, value: TEXT) RAISES {JsonWriter.Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    IF value = NIL THEN w.null (key) ELSE w.text (value, key) END;
  END Opt;

PROCEDURE Start (resp: HTTPServer.Response; status: INTEGER): JsonWriter.T =
  BEGIN
    resp.status := status;
    resp.headers.set ("Content-Type", "application/json");
    resp.headers.set ("Cache-Control", "no-store");
    resp.headers.set ("X-Content-Type-Options", "nosniff");
    resp.compress ();
    RETURN JsonWriter.ToWr (resp.body (), JsonWriter.Options {replaceInvalid := TRUE});
  END Start;

PROCEDURE Error (resp: HTTPServer.Response; status: INTEGER; message: TEXT) RAISES {Wr.Failure, Thread.Alerted} =
  <*FATAL JsonWriter.Error*>
  VAR w: JsonWriter.T;
  BEGIN
    IF status = 401 THEN resp.headers.set ("WWW-Authenticate", "Bearer") END;
    w := Start (resp, status);
    w.object ();
    w.text (message, "error");
    w.end ();
    w.finish ();
  END Error;

PROCEDURE Handle (s: Store.T; c: HTTPClient.T; req: HTTPServer.Request; resp: HTTPServer.Response)
  RAISES {Rd.Failure, Wr.Failure, Thread.Alerted} =
  VAR auth := req.headers.get ("authorization");  user := -1;  rest := Text.Sub (req.path, 5);  slash: INTEGER;
      id := 0;
  BEGIN
    TRY
      TRY
        IF auth # NIL AND Prefix (auth, "Bearer ") THEN user := Auth.ApiUser (s, Text.Sub (auth, 7)) END;
        IF user < 0 THEN Oops (401, "a valid token in \"Authorization: Bearer TOKEN\" is needed") END;
        slash := Text.FindChar (rest, '/');
        IF slash >= 0 THEN
          id := Int (Text.Sub (rest, slash + 1), -1);
          IF id <= 0 THEN Oops (404, "no such object") END;
          rest := Text.Sub (rest, 0, slash);
        END;
        IF Text.Equal (rest, "feeds") THEN
          IF id = 0 AND Text.Equal (req.method, "GET") THEN
            Feeds (s, user, resp);
          ELSIF id = 0 AND Text.Equal (req.method, "POST") THEN
            Subscribe (s, c, user, req, resp);
          ELSIF id > 0 AND Text.Equal (req.method, "DELETE") THEN
            s.unsubscribe (user, id);
            resp.status := 204;
          ELSE
            Oops (405, "not with this method");
          END;
        ELSIF Text.Equal (rest, "entries") THEN
          IF id = 0 AND Text.Equal (req.method, "GET") THEN
            Entries (s, user, req, resp);
          ELSIF id > 0 AND Text.Equal (req.method, "GET") THEN
            Entry (s, user, id, resp);
          ELSIF id > 0 AND Text.Equal (req.method, "POST") THEN
            Mark (s, user, id, req);
            resp.status := 204;
          ELSE
            Oops (405, "not with this method");
          END;
        ELSE
          Oops (404, "no such object");
        END;
      EXCEPT
      | Store.Error (m) =>
          Log.Put ("database: " & m);
          Oops (500, "the database failed: " & m);
      | JsonWriter.Error (f) => Oops (500, JsonWriter.Format (f));
      END;
    EXCEPT
    | Fail (p) => Error (resp, p.status, p.message);
    END;
  END Handle;

PROCEDURE Feeds (s: Store.T; user: INTEGER; resp: HTTPServer.Response)
  RAISES {Store.Error, JsonWriter.Error, Wr.Failure, Thread.Alerted} =
  VAR feeds := s.feeds (user);  w := Start (resp, 200);
  BEGIN
    w.array ();
    FOR i := 0 TO LAST (feeds^) DO
      WITH f = feeds[i] DO
        w.object ();
        w.int (f.id, "id");
        w.text (f.title, "title");
        w.text (f.url, "url");
        Opt (w, "site", f.site);
        w.int (f.entries, "entries");
        w.int (f.unread, "unread");
        IF f.lastFetch = 0.0D0 THEN w.null ("fetched") ELSE w.text (FeedDate.Format (f.lastFetch), "fetched") END;
        Opt (w, "error", f.lastError);
        w.end ();
      END;
    END;
    w.end ();
    w.finish ();
  END Feeds;

PROCEDURE Row (w: JsonWriter.T; READONLY e: Store.EntryRow) RAISES {JsonWriter.Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    w.int (e.id, "id");
    w.int (e.feed, "feed");
    w.text (e.feedTitle, "feedTitle");
    w.text (e.title, "title");
    Opt (w, "link", e.link);
    w.text (e.author, "author");
    w.text (FeedDate.Format (e.published), "published");
    w.bool (e.read, "read");
    w.bool (e.starred, "starred");
  END Row;

PROCEDURE Entries (s: Store.T; user: INTEGER; req: HTTPServer.Request; resp: HTTPServer.Response)
  RAISES {Store.Error, JsonWriter.Error, Wr.Failure, Thread.Alerted} =
  VAR f: Store.Filter;  rows: Store.EntryRows;  w: JsonWriter.T;
  BEGIN
    f.feed := MAX (Int (Param (req, "feed"), 0), 0);
    f.unread := Is (Param (req, "unread"), "1");
    f.starred := Is (Param (req, "starred"), "1");
    f.query := Param (req, "q");
    f.limit := MIN (MAX (Int (Param (req, "n"), 50), 1), MaxEntries);
    f.offset := MAX (Int (Param (req, "offset"), 0), 0);
    rows := s.entries (user, f);
    w := Start (resp, 200);
    w.array ();
    FOR i := 0 TO LAST (rows^) DO
      w.object ();
      Row (w, rows[i]);
      w.text (Unmarked (rows[i].excerpt), "excerpt");
      w.end ();
    END;
    w.end ();
    w.finish ();
  END Entries;

PROCEDURE Entry (s: Store.T; user, id: INTEGER; resp: HTTPServer.Response)
  RAISES {Fail, Store.Error, JsonWriter.Error, Wr.Failure, Thread.Alerted} =
  VAR c: Store.Content;  w: JsonWriter.T;
  BEGIN
    IF NOT s.entry (user, id, c) THEN Oops (404, "no such entry") END;
    w := Start (resp, 200);
    w.object ();
    Row (w, c.row);
    w.text (c.row.excerpt, "text");
    w.text (c.summary, "summary");
    w.text (c.content, "content");
    Opt (w, "base", c.base);
    w.end ();
    w.finish ();
  END Entry;

(* The object in the body of the request. *)
PROCEDURE Body (req: HTTPServer.Request): JsonReader.T RAISES {Fail, Rd.Failure, Thread.Alerted} =
  VAR t := Rd.GetText (req.body, MaxBody + 1);
  BEGIN
    IF Text.Length (t) > MaxBody THEN Oops (413, "the body is too large") END;
    RETURN JsonReader.FromRd (TextRd.New (t));
  END Body;

PROCEDURE Mark (s: Store.T; user, id: INTEGER; req: HTTPServer.Request)
  RAISES {Fail, Store.Error, Rd.Failure, Thread.Alerted} =
  VAR r := Body (req);  c: Store.Content;
  BEGIN
    IF NOT s.entry (user, id, c) THEN Oops (404, "no such entry") END;
    TRY
      r.object ();
      WHILE r.member () DO
        IF r.is ("read") THEN
          s.setRead (user, id, r.bool ());
        ELSIF r.is ("starred") THEN
          s.setStarred (user, id, r.bool ());
        ELSE
          r.skip ();
        END;
      END;
      r.finish ();
    EXCEPT
    | JsonReader.Error (f) => Oops (400, JsonReader.Format (f));
    END;
  END Mark;

PROCEDURE Subscribe (s: Store.T; c: HTTPClient.T; user: INTEGER; req: HTTPServer.Request;
                     resp: HTTPServer.Response)
  RAISES {Fail, Store.Error, JsonWriter.Error, Rd.Failure, Wr.Failure, Thread.Alerted} =
  VAR r := Body (req);  url, title, feedUrl: TEXT := NIL;  id, new: INTEGER;  w: JsonWriter.T;  now: Time.T;
  BEGIN
    TRY
      r.object ();
      WHILE r.member () DO
        IF r.is ("url") THEN
          url := r.text ();
        ELSIF r.is ("title") THEN
          title := r.text ();
        ELSE
          r.skip ();
        END;
      END;
      r.finish ();
    EXCEPT
    | JsonReader.Error (f) => Oops (400, JsonReader.Format (f));
    END;
    IF url # NIL AND Text.FindChar (url, ':') < 0 THEN url := "https://" & url END;
    url := Links.Resolve (NIL, url);
    IF url = NIL THEN Oops (400, "\"url\" must be the address of a web page") END;
    TRY
      WITH f = Fetcher.Find (c, url, feedUrl) DO
        id := s.subscribe (user, feedUrl, title);
        now := Time.Now ();
        new := s.fetched (id, f, NIL, NIL, now, now + Fetcher.Interval);
        IF new > 0 THEN Events.Signal () END;
        w := Start (resp, 201);
        w.object ();
        w.int (id, "id");
        w.text (feedUrl, "url");
        IF title # NIL THEN w.text (title, "title") ELSE w.text (f.title, "title") END;
        w.int (NUMBER (f.entries^), "entries");
        w.end ();
        w.finish ();
      END;
    EXCEPT
    | Fetcher.Error (m) => Oops (422, m);
    END;
  END Subscribe;

BEGIN
END Api.
