(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Web;

IMPORT Api, Auth, Bundle, Events, Fetch, Fetcher, Fmt, FloatMode, HTTP, HTTPClient, HTTPCookie, HTTPServer,
       HtmlText, Lex, Links, Log, Multipart, Opml, Page, Rd, Scan, Static, Store, Text, TextBuf, TextRd, Thread,
       Time, URI, Utf8, Wr;

FROM Page IMPORT E;

CONST
  PagePolicy = "default-src 'self'; img-src 'self' data:; frame-src 'self'; object-src 'none'; base-uri 'none';"
                 & " form-action 'self'; frame-ancestors 'none'";
  (* The HTML of an entry: no scripts, no plugins, no requests but for
     images if the reader wants them; links open outside of the sandbox. *)
  ContentPolicy = "sandbox allow-popups allow-popups-to-escape-sandbox; default-src 'none';"
                    & " style-src 'unsafe-inline'; frame-ancestors 'self'; img-src data:";
  RemoteImages = " https: http:";
  Sandbox = "allow-popups allow-popups-to-escape-sandbox";
  ContentStyle = "body{font:17px/1.6 Georgia,serif;max-width:40em;margin:1em auto;padding:0 1em;color:#222;"
                   & "background:#fff}img,video{max-width:100%;height:auto}pre{overflow:auto}"
                   & "a{color:#0645ad}@media (prefers-color-scheme:dark){body{color:#ddd;background:#1b1b1b}"
                   & "a{color:#8ab4f8}}";

  PageSize = 50;
  MaxForm = 1024 * 1024;        (* bytes of a form *)
  MaxOpml = 4 * 1024 * 1024;    (* bytes of a list of subscriptions *)
  Heartbeat = 15.0D0;           (* seconds between two messages to a page that waits for news *)

REVEAL
  T = HTTPServer.Handler BRANDED OBJECT
    store: Store.T;
    fetcher: Fetcher.T;
    client: HTTPClient.T;       (* for subscriptions *)
  OVERRIDES
    request := Request;
  END;

TYPE
  (* A request, and who makes it. *)
  Ctx = RECORD
    t: T;
    req: HTTPServer.Request;
    resp: HTTPServer.Response;
    user := -1;                 (* -1 if not logged in *)
    name: TEXT := NIL;
    csrf: TEXT := NIL;
    token: TEXT := NIL;         (* of the session *)
    form: URI.Pairs := NIL;     (* the fields of a form that was posted *)
  END;

EXCEPTION Bad (INTEGER);        (* answer with a page of this status *)

PROCEDURE New (s: Store.T; f: Fetcher.T): T =
  BEGIN
    RETURN NEW (T, store := s, fetcher := f, client := Fetch.NewClient ());
  END New;

(* Small things *)

PROCEDURE Is (a, b: TEXT): BOOLEAN =
  BEGIN
    RETURN a # NIL AND Text.Equal (a, b);
  END Is;

PROCEDURE Prefix (t, p: TEXT): BOOLEAN =
  BEGIN
    RETURN Text.Length (t) >= Text.Length (p) AND Text.Equal (Text.Sub (t, 0, Text.Length (p)), p);
  END Prefix;

PROCEDURE Decode (bytes: TEXT): TEXT =
  VAR r: TEXT;
  BEGIN
    IF bytes = NIL THEN RETURN NIL END;
    IF Utf8.Decode (bytes, r) THEN RETURN r END;
    RETURN bytes;
  END Decode;

(* The value of the field "name", decoded from UTF-8; NIL if there is none. *)
PROCEDURE Lookup (p: URI.Pairs; name: TEXT): TEXT =
  BEGIN
    IF p = NIL THEN RETURN NIL END;
    FOR i := 0 TO LAST (p^) DO
      IF Text.Equal (p[i].name, name) THEN RETURN Decode (p[i].value) END;
    END;
    RETURN NIL;
  END Lookup;

PROCEDURE Param (READONLY c: Ctx; name: TEXT): TEXT =
  VAR p: URI.Pairs;
  BEGIN
    IF c.req.query = NIL OR NOT URI.ParseQuery (c.req.query, p) THEN RETURN NIL END;
    RETURN Lookup (p, name);
  END Param;

PROCEDURE Field (READONLY c: Ctx; name: TEXT): TEXT =
  BEGIN
    RETURN Lookup (c.form, name);
  END Field;

PROCEDURE Int (t: TEXT; default: INTEGER): INTEGER =
  BEGIN
    IF t = NIL THEN RETURN default END;
    TRY
      RETURN Scan.Int (t);
    EXCEPT
    | Lex.Error, FloatMode.Trap => RETURN default;
    END;
  END Int;

PROCEDURE Id (t: TEXT): INTEGER RAISES {Bad} =
  VAR i := Int (t, -1);
  BEGIN
    IF i <= 0 THEN RAISE Bad (404) END;
    RETURN i;
  END Id;

(* A query value for a URL. *)
PROCEDURE Q (t: TEXT): TEXT =
  BEGIN
    RETURN URI.Escape (Utf8.Encode (t), form := TRUE);
  END Q;

PROCEDURE Or (t, default: TEXT): TEXT =
  BEGIN
    IF t = NIL OR Text.Empty (t) THEN RETURN default END;
    RETURN t;
  END Or;

(* Answers *)

PROCEDURE Head (VAR c: Ctx; status: INTEGER; type: TEXT) =
  BEGIN
    c.resp.status := status;
    c.resp.headers.set ("Content-Type", type);
    c.resp.headers.set ("X-Content-Type-Options", "nosniff");
    (* Not "no-referrer": with it, browsers send "Origin: null" with a form,
       which "SameOrigin" must refuse. *)
    c.resp.headers.set ("Referrer-Policy", "same-origin");
    c.resp.headers.set ("Cache-Control", "no-store");
  END Head;

(* A page. It is not compressed, as it holds the token against forged
   requests next to texts that others choose, such as a search. *)
PROCEDURE Send (VAR c: Ctx; status: INTEGER; html: TEXT) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    Head (c, status, "text/html; charset=utf-8");
    c.resp.headers.set ("Content-Security-Policy", PagePolicy);
    Wr.PutText (c.resp.body (), Utf8.Encode (html));
  END Send;

PROCEDURE Redirect (VAR c: Ctx; location: TEXT) =
  BEGIN
    c.resp.status := 303;
    c.resp.headers.set ("Location", location);
    c.resp.headers.set ("Cache-Control", "no-store");
  END Redirect;

PROCEDURE Fail (VAR c: Ctx; status: INTEGER; message: TEXT) RAISES {Wr.Failure, Thread.Alerted} =
  VAR b := Page.Begin (HTTP.Reason (status), c.name, c.csrf);
  BEGIN
    b.put ("<main class=\"narrow\"><h1>" & E (HTTP.Reason (status)) & "</h1><p>" & E (message)
             & "</p><p><a href=\"/\">Back to the entries</a></p></main>");
    Send (c, status, Page.End (b));
  END Fail;

PROCEDURE SetCookie (VAR c: Ctx; value: TEXT; maxAge: INTEGER) =
  <*FATAL HTTP.Error*>  (* the value is a token in Base64 for URLs, or empty *)
  BEGIN
    c.resp.headers.add ("Set-Cookie", HTTPCookie.Format (HTTPCookie.T {
                                                           name := "session", value := value, path := "/",
                                                           maxAge := maxAge, httpOnly := TRUE,
                                                           secure := c.req.secure,
                                                           sameSite := HTTPCookie.SameSite.Lax}));
  END SetCookie;

(* Where a form sends the reader back to: a path of this server. *)
PROCEDURE Back (t, default: TEXT): TEXT =
  BEGIN
    IF t = NIL OR NOT Prefix (t, "/") OR Prefix (t, "//") OR Text.FindChar (t, '\\') >= 0 THEN RETURN default END;
    RETURN t;
  END Back;

(* Forms *)

PROCEDURE ReadForm (VAR c: Ctx) RAISES {Bad, Rd.Failure, Thread.Alerted} =
  VAR type := c.req.headers.get ("content-type");  body: TEXT;
  BEGIN
    IF type = NIL OR NOT Prefix (type, "application/x-www-form-urlencoded") THEN RETURN END;
    body := Rd.GetText (c.req.body, MaxForm + 1);
    IF Text.Length (body) > MaxForm THEN RAISE Bad (413) END;
    IF NOT URI.ParseQuery (body, c.form) THEN RAISE Bad (400) END;
  END ReadForm;

(* Whether a request that changes something comes from a page of this
   server, as far as the browser tells by "Origin". *)
PROCEDURE SameOrigin (READONLY c: Ctx): BOOLEAN =
  VAR origin := c.req.headers.get ("origin");  host := c.req.headers.get ("host");  i: INTEGER;
  BEGIN
    IF origin = NIL THEN RETURN TRUE END;
    IF host = NIL THEN RETURN FALSE END;
    i := Text.FindChar (origin, ':');
    RETURN i >= 0 AND Text.Equal (Text.Sub (origin, i + 3), host);
  END SameOrigin;

PROCEDURE CheckPost (READONLY c: Ctx; csrf: TEXT) RAISES {Bad} =
  BEGIN
    IF NOT SameOrigin (c) OR NOT Auth.Equal (csrf, c.csrf) THEN RAISE Bad (403) END;
  END CheckPost;

(* The pages *)

PROCEDURE Request (t: T; req: HTTPServer.Request; resp: HTTPServer.Response)
  RAISES {Rd.Failure, Wr.Failure, Thread.Alerted} =
  VAR c := Ctx {t := t, req := req, resp := resp};
  BEGIN
    TRY
      TRY
        Route (c);
      EXCEPT
      | Bad (status) => Fail (c, status, Message (status));
      END;
    EXCEPT
    | Store.Error (m) =>
        Log.Put ("database: " & m);
        Fail (c, 500, "The database failed: " & m);
    END;
  END Request;

PROCEDURE Message (status: INTEGER): TEXT =
  BEGIN
    CASE status OF
    | 403 => RETURN "This form is out of date, or comes from another site. Load the page again and retry.";
    | 404 => RETURN "There is no such page.";
    | 405 => RETURN "That cannot be done with this page.";
    | 413 => RETURN "That is too large.";
    ELSE RETURN "That did not work.";
    END;
  END Message;

PROCEDURE Route (VAR c: Ctx) RAISES {Bad, Store.Error, Rd.Failure, Wr.Failure, Thread.Alerted} =
  VAR
    path := c.req.path;
    get := Is (c.req.method, "GET") OR Is (c.req.method, "HEAD");
    post := Is (c.req.method, "POST");
    token, why: TEXT;
    s := c.t.store;
    parts: REF ARRAY OF TEXT;
  BEGIN
    IF Prefix (path, "/static/") THEN
      IF NOT get THEN RAISE Bad (405) END;
      StaticFile (c, Text.Sub (path, 8));
      RETURN;
    ELSIF Prefix (path, "/api/") THEN
      Api.Handle (s, c.t.client, c.req, c.resp);
      RETURN;
    END;

    token := HTTPCookie.Get (c.req.headers, "session");
    c.user := -1;
    IF token # NIL AND NOT Text.Empty (token) THEN
      IF Auth.Session (s, token, c.user, c.csrf, why) THEN
        c.token := token;
        c.name := s.userName (c.user);
      ELSE
        Log.Put ("a session cookie refused: " & why);
      END;
    END;

    IF Text.Equal (path, "/setup") THEN
      Setup (c, get);
      RETURN;
    ELSIF Text.Equal (path, "/login") THEN
      Login (c, get);
      RETURN;
    ELSIF c.user < 0 THEN
      IF s.userCount () = 0 THEN Redirect (c, "/setup") ELSE Redirect (c, "/login") END;
      RETURN;
    END;

    IF post AND NOT Text.Equal (path, "/import") THEN
      ReadForm (c);
      CheckPost (c, Field (c, "csrf"));
    END;

    parts := Split (path);
    IF Text.Equal (path, "/") THEN
      IF NOT get THEN RAISE Bad (405) END;
      Home (c);
    ELSIF Text.Equal (path, "/logout") AND post THEN
      Auth.EndSession (s, c.token);
      SetCookie (c, "", 0);
      Redirect (c, "/login");
    ELSIF Text.Equal (path, "/feeds") AND get THEN
      Feeds (c, NIL, FALSE);
    ELSIF Text.Equal (path, "/feeds/add") AND post THEN
      AddFeed (c);
    ELSIF Text.Equal (path, "/import") AND post THEN
      Import (c);
    ELSIF Text.Equal (path, "/demo") AND post THEN
      Subscribe (c, Bundle.Get (Static.Get (), "demo.opml"), "/");
    ELSIF Text.Equal (path, "/export.opml") AND get THEN
      Export (c);
    ELSIF Text.Equal (path, "/token") AND post THEN
      NewToken (c);
    ELSIF Text.Equal (path, "/settings") AND post THEN
      s.setShowImages (c.user, Is (Field (c, "images"), "1"));
      Redirect (c, "/feeds");
    ELSIF Text.Equal (path, "/events") AND get THEN
      Stream (c);
    ELSIF NUMBER (parts^) = 2 AND Text.Equal (parts[0], "entry") THEN
      IF get THEN Entry (c, Id (parts[1])) ELSIF post THEN Mark (c, Id (parts[1])) ELSE RAISE Bad (405) END;
    ELSIF NUMBER (parts^) = 2 AND Text.Equal (parts[0], "content") AND get THEN
      Content (c, Id (parts[1]));
    ELSIF NUMBER (parts^) = 3 AND Text.Equal (parts[0], "feeds") AND post THEN
      FeedAction (c, Int (parts[1], -1), parts[2]);
    ELSE
      RAISE Bad (404);
    END;
  END Route;

(* The parts of a path between its slashes. *)
PROCEDURE Split (path: TEXT): REF ARRAY OF TEXT =
  VAR n := 0;  r: REF ARRAY OF TEXT;  start := 1;  stop: INTEGER;
  BEGIN
    FOR i := 1 TO Text.Length (path) - 1 DO
      IF Text.GetChar (path, i) = '/' THEN INC (n) END;
    END;
    r := NEW (REF ARRAY OF TEXT, n + 1);
    FOR i := 0 TO n DO
      stop := Text.FindChar (path, '/', start);
      IF stop < 0 THEN stop := Text.Length (path) END;
      r[i] := Text.Sub (path, start, stop - start);
      start := stop + 1;
    END;
    RETURN r;
  END Split;

(* Users *)

PROCEDURE Setup (VAR c: Ctx; get: BOOLEAN) RAISES {Bad, Store.Error, Rd.Failure, Wr.Failure, Thread.Alerted} =
  VAR s := c.t.store;  name, password: TEXT;  id: INTEGER;
  BEGIN
    IF s.userCount () > 0 THEN
      IF get THEN Redirect (c, "/login"); RETURN END;
      RAISE Bad (403);
    END;
    IF get THEN SetupPage (c, NIL, NIL); RETURN END;
    ReadForm (c);
    IF NOT SameOrigin (c) THEN RAISE Bad (403) END;
    name := Or (Field (c, "name"), "");
    password := Or (Field (c, "password"), "");
    IF NOT Is (Field (c, "again"), password) THEN
      SetupPage (c, name, "The two passwords differ.");
      RETURN;
    END;
    TRY
      id := Auth.AddUser (s, name, password);
    EXCEPT
    | Auth.Error (m) => SetupPage (c, name, "That will not do: " & m & "."); RETURN;
    END;
    StartSession (c, id);
    Redirect (c, "/");
  END Setup;

PROCEDURE SetupPage (VAR c: Ctx; name, message: TEXT) RAISES {Wr.Failure, Thread.Alerted} =
  VAR b := Page.Begin ("Welcome");
  BEGIN
    b.put ("<main class=\"narrow\"><h1>Welcome</h1>"
             & "<p>This is a reader of news feeds, written in Modula-3. Make the first user, who can then "
             & "subscribe to feeds; the name and the password are kept in the database of the reader.</p>");
    IF message # NIL THEN b.put ("<p class=\"msg error\">" & E (message) & "</p>") END;
    b.put ("<form method=\"post\" action=\"/setup\" class=\"login\">"
             & "<label>Name <input name=\"name\" autocomplete=\"username\" required maxlength=\"64\" value=\""
             & E (name) & "\"></label>"
             & "<label>Password <input type=\"password\" name=\"password\" autocomplete=\"new-password\" "
             & "required minlength=\"8\"></label>"
             & "<label>Password again <input type=\"password\" name=\"again\" autocomplete=\"new-password\" "
             & "required minlength=\"8\"></label>"
             & "<button>Make the user</button></form></main>");
    Send (c, 200, Page.End (b));
  END SetupPage;

PROCEDURE StartSession (VAR c: Ctx; user: INTEGER) RAISES {Store.Error, Thread.Alerted} =
  VAR csrf: TEXT;
  BEGIN
    SetCookie (c, Auth.NewSession (c.t.store, user, csrf), ROUND (Auth.SessionTime));
  END StartSession;

PROCEDURE Login (VAR c: Ctx; get: BOOLEAN) RAISES {Bad, Store.Error, Rd.Failure, Wr.Failure, Thread.Alerted} =
  VAR s := c.t.store;  name, why: TEXT;  id: INTEGER;
  BEGIN
    IF get THEN
      IF c.user >= 0 THEN Redirect (c, "/"); RETURN END;
      IF s.userCount () = 0 THEN Redirect (c, "/setup"); RETURN END;
      LoginPage (c, NIL, 200, NIL);
      RETURN;
    END;
    ReadForm (c);
    IF NOT SameOrigin (c) THEN RAISE Bad (403) END;
    name := Or (Field (c, "name"), "");
    id := Auth.Login (s, name, Or (Field (c, "password"), ""), why);
    IF id < 0 THEN
      Log.Put ("a login as " & Shown (name) & " refused: " & why);
      LoginPage (c, name, 401, "The name or the password is wrong.");
      RETURN;
    END;
    IF c.token # NIL THEN Auth.EndSession (s, c.token) END;
    StartSession (c, id);
    Redirect (c, "/");
  END Login;

(* "t" for the log: in quotes, at most 64 characters, each one other than
   printable ASCII as "?". *)
PROCEDURE Shown (t: TEXT): TEXT =
  VAR n := MIN (Text.Length (t), 64);  a := NEW (REF ARRAY OF CHAR, n);  w: WIDECHAR;
  BEGIN
    FOR i := 0 TO n - 1 DO
      w := Text.GetWideChar (t, i);
      IF ORD (w) < ORD (' ') OR ORD (w) > ORD ('~') THEN a[i] := '?' ELSE a[i] := VAL (ORD (w), CHAR) END;
    END;
    RETURN "\"" & Text.FromChars (a^) & "\"";
  END Shown;

PROCEDURE LoginPage (VAR c: Ctx; name: TEXT; status: INTEGER; message: TEXT) RAISES {Wr.Failure, Thread.Alerted} =
  VAR b := Page.Begin ("Log in");
  BEGIN
    b.put ("<main class=\"narrow\"><h1>Log in</h1>");
    IF message # NIL THEN b.put ("<p class=\"msg error\">" & E (message) & "</p>") END;
    b.put ("<form method=\"post\" action=\"/login\" class=\"login\">"
             & "<label>Name <input name=\"name\" autocomplete=\"username\" required value=\"" & E (name)
             & "\"></label>"
             & "<label>Password <input type=\"password\" name=\"password\" autocomplete=\"current-password\" "
             & "required></label>"
             & "<button>Log in</button></form></main>");
    Send (c, status, Page.End (b));
  END LoginPage;

(* The entries *)

PROCEDURE Home (VAR c: Ctx) RAISES {Store.Error, Wr.Failure, Thread.Alerted} =
  VAR
    s := c.t.store;
    q := Param (c, "q");
    show := Or (Param (c, "show"), "unread");
    feed := Int (Param (c, "feed"), 0);
    page := MAX (Int (Param (c, "p"), 0), 0);
    f: Store.Filter;
    rows: Store.EntryRows;
    feeds := s.feeds (c.user);
    title: TEXT;
    b: TextBuf.T;
    base, here: TEXT;
  BEGIN
    IF q # NIL AND Text.Empty (q) THEN q := NIL END;
    IF q # NIL AND NOT Is (show, "starred") THEN show := "all" END;
    IF NOT Is (show, "all") AND NOT Is (show, "starred") THEN show := "unread" END;
    f.feed := feed;
    f.unread := Text.Equal (show, "unread");
    f.starred := Text.Equal (show, "starred");
    f.query := q;
    f.limit := PageSize + 1;
    f.offset := page * PageSize;
    rows := s.entries (c.user, f);

    IF q # NIL THEN
      title := "Search: " & q;
    ELSIF f.starred THEN
      title := "Starred";
    ELSE
      title := "All feeds";
      FOR i := 0 TO LAST (feeds^) DO
        IF feeds[i].id = feed THEN title := Or (feeds[i].title, feeds[i].url) END;
      END;
    END;

    (* the address of this view, without the page *)
    base := "/?show=" & show;
    IF feed # 0 THEN base := base & "&feed=" & Fmt.Int (feed) END;
    IF q # NIL THEN base := base & "&q=" & Q (q) END;
    here := base;
    IF page > 0 THEN here := here & "&p=" & Fmt.Int (page) END;

    b := Page.Begin (title, c.name, c.csrf, q);
    b.put ("<div class=\"layout\">\n");
    Sidebar (b, feeds, feed, show, q);
    b.put ("<main>\n<div class=\"bar\"><h1>" & E (title) & "</h1>");
    IF q = NIL AND NOT f.starred THEN
      b.put ("<nav class=\"views\">");
      ViewLink (b, "Unread", feed, "unread", show);
      ViewLink (b, "All", feed, "all", show);
      b.put ("</nav>");
      IF f.unread AND NUMBER (rows^) > 0 THEN
        b.put ("<form method=\"post\" action=\"/feeds/" & Fmt.Int (feed) & "/read\">");
        Page.Hidden (b, "csrf", c.csrf);
        Page.Hidden (b, "back", here);
        b.put ("<button>Mark all as read</button></form>");
      END;
    END;
    b.put ("</div>\n");

    IF NUMBER (feeds^) = 0 THEN
      b.put ("<section class=\"welcome\"><p>You have no feeds yet. <a href=\"/feeds\">Add some</a>, "
               & "or start with a few about programming languages and other things:</p>"
               & "<form method=\"post\" action=\"/demo\">");
      Page.Hidden (b, "csrf", c.csrf);
      b.put ("<button>Subscribe to the feeds to start with</button></form></section>\n");
    ELSIF NUMBER (rows^) = 0 THEN
      IF q # NIL THEN
        b.put ("<p class=\"empty\">Nothing found.</p>\n");
      ELSIF f.unread THEN
        b.put ("<p class=\"empty\">Nothing unread. New entries show up here as they come.</p>\n");
      ELSE
        b.put ("<p class=\"empty\">Nothing here yet.</p>\n");
      END;
    END;
    FOR i := 0 TO MIN (LAST (rows^), PageSize - 1) DO Item (c, b, rows[i], here) END;

    IF page > 0 OR NUMBER (rows^) > PageSize THEN
      b.put ("<nav class=\"pager\">");
      IF page > 0 THEN b.put ("<a href=\"" & E (base & "&p=" & Fmt.Int (page - 1)) & "\">Newer</a>") END;
      IF NUMBER (rows^) > PageSize THEN
        b.put ("<a href=\"" & E (base & "&p=" & Fmt.Int (page + 1)) & "\">Older</a>");
      END;
      b.put ("</nav>\n");
    END;
    b.put ("</main>\n</div>");
    Send (c, 200, Page.End (b));
  END Home;

PROCEDURE ViewLink (b: TextBuf.T; label: TEXT; feed: INTEGER; show, current: TEXT) =
  VAR url := "/?show=" & show;
  BEGIN
    IF feed # 0 THEN url := url & "&feed=" & Fmt.Int (feed) END;
    IF Text.Equal (show, current) THEN
      b.put ("<a class=\"on\" aria-current=\"page\" href=\"" & E (url) & "\">" & label & "</a>");
    ELSE
      b.put ("<a href=\"" & E (url) & "\">" & label & "</a>");
    END;
  END ViewLink;

PROCEDURE Sidebar (b: TextBuf.T; feeds: Store.FeedRows; feed: INTEGER; show, q: TEXT) =
  VAR unread := 0;  cls: TEXT;
  BEGIN
    FOR i := 0 TO LAST (feeds^) DO INC (unread, feeds[i].unread) END;
    b.put ("<nav class=\"side\" aria-label=\"Feeds\"><ul>");
    cls := "";
    IF feed = 0 AND q = NIL AND NOT Text.Equal (show, "starred") THEN cls := " class=\"on\"" END;
    b.put ("<li" & cls & "><a href=\"/\">All feeds</a> <span class=\"count\">" & Fmt.Int (unread) & "</span></li>");
    cls := "";
    IF Text.Equal (show, "starred") THEN cls := " class=\"on\"" END;
    b.put ("<li" & cls & "><a href=\"/?show=starred\">Starred</a></li></ul><ul>");
    FOR i := 0 TO LAST (feeds^) DO
      WITH f = feeds[i] DO
        cls := "";
        IF f.id = feed THEN cls := "on" END;
        IF f.lastError # NIL THEN cls := cls & " failing" END;
        b.put ("<li class=\"" & cls & "\"><a href=\"/?feed=" & Fmt.Int (f.id) & "\">" & E (Or (f.title, f.url))
                 & "</a>");
        IF f.unread > 0 THEN b.put (" <span class=\"count\">" & Fmt.Int (f.unread) & "</span>") END;
        b.put ("</li>");
      END;
    END;
    b.put ("</ul></nav>\n");
  END Sidebar;

(* The buttons that mark an entry; "back" is where they lead to. *)
PROCEDURE Actions (READONLY c: Ctx; b: TextBuf.T; READONLY e: Store.EntryRow; back: TEXT) =
  BEGIN
    b.put ("<form method=\"post\" action=\"/entry/" & Fmt.Int (e.id) & "\" class=\"actions\">");
    Page.Hidden (b, "csrf", c.csrf);
    Page.Hidden (b, "back", back);
    IF e.read THEN
      b.put ("<button name=\"read\" value=\"0\">Mark as unread</button>");
    ELSE
      b.put ("<button name=\"read\" value=\"1\">Mark as read</button>");
    END;
    IF e.starred THEN
      b.put ("<button name=\"starred\" value=\"0\" class=\"star on\" title=\"Unstar\">&#9733; Starred</button>");
    ELSE
      b.put ("<button name=\"starred\" value=\"1\" class=\"star\" title=\"Star\">&#9734; Star</button>");
    END;
    b.put ("</form>");
  END Actions;

PROCEDURE Meta (b: TextBuf.T; READONLY e: Store.EntryRow) =
  BEGIN
    b.put ("<div class=\"meta\"><a href=\"/?feed=" & Fmt.Int (e.feed) & "\">" & E (e.feedTitle) & "</a> &middot; "
             & Page.When (e.published));
    IF NOT Text.Empty (e.author) THEN b.put (" &middot; " & E (e.author)) END;
    b.put ("</div>");
  END Meta;

PROCEDURE Item (READONLY c: Ctx; b: TextBuf.T; READONLY e: Store.EntryRow; back: TEXT) =
  VAR cls := "entry";  excerpt: TEXT;
  BEGIN
    IF NOT e.read THEN cls := "entry unread" END;
    b.put ("<article class=\"" & cls & "\"><h2><a href=\"/entry/" & Fmt.Int (e.id) & "\">" & E (e.title)
             & "</a></h2>");
    Meta (b, e);
    excerpt := e.excerpt;
    IF Text.FindChar (excerpt, '\001') < 0 THEN excerpt := HtmlText.Excerpt (excerpt, 240) END;
    IF NOT Text.Empty (excerpt) THEN b.put ("<p class=\"excerpt\">" & Page.Marked (excerpt) & "</p>") END;
    Actions (c, b, e, back);
    b.put ("</article>\n");
  END Item;

(* Whether the entry is to be shown with the images of other servers: as
   the link of the entry says, else as the setting of the user says. *)
PROCEDURE Images (READONLY c: Ctx): BOOLEAN RAISES {Store.Error, Thread.Alerted} =
  VAR p := Param (c, "images");
  BEGIN
    IF Is (p, "1") THEN RETURN TRUE END;
    IF Is (p, "0") THEN RETURN FALSE END;
    RETURN c.t.store.showImages (c.user);
  END Images;

PROCEDURE Entry (VAR c: Ctx; id: INTEGER) RAISES {Bad, Store.Error, Wr.Failure, Thread.Alerted} =
  VAR s := c.t.store;  e: Store.Content;  b: TextBuf.T;  images := Images (c);  src: TEXT;
  BEGIN
    IF NOT s.entry (c.user, id, e) THEN RAISE Bad (404) END;
    IF NOT e.row.read THEN
      s.setRead (c.user, id, TRUE);
      e.row.read := TRUE;
    END;
    b := Page.Begin (e.row.title, c.name, c.csrf);
    b.put ("<main class=\"reading\"><p class=\"up\"><a href=\"/?feed=" & Fmt.Int (e.row.feed) & "\">&larr; "
             & E (e.row.feedTitle) & "</a></p><article class=\"full\"><h1>");
    IF e.row.link # NIL THEN
      b.put ("<a href=\"" & E (e.row.link) & "\" rel=\"noopener noreferrer\" target=\"_blank\">" & E (e.row.title)
               & "</a>");
    ELSE
      b.put (E (e.row.title));
    END;
    b.put ("</h1>");
    Meta (b, e.row);
    b.put ("<div class=\"bar\">");
    Actions (c, b, e.row, "/entry/" & Fmt.Int (id));
    IF images THEN
      src := "/content/" & Fmt.Int (id) & "?images=1";
      b.put ("<a href=\"/entry/" & Fmt.Int (id) & "?images=0\">Hide the images of other sites</a>");
    ELSE
      src := "/content/" & Fmt.Int (id) & "?images=0";
      b.put ("<a href=\"/entry/" & Fmt.Int (id) & "?images=1\">Show the images of other sites</a>");
    END;
    b.put ("</div><iframe class=\"content\" src=\"" & src & "\" sandbox=\"" & Sandbox
             & "\" referrerpolicy=\"no-referrer\" title=\"The text of the entry\"></iframe></article></main>");
    Send (c, 200, Page.End (b));
  END Entry;

(* The HTML of an entry, as a document of its own for the frame. *)
PROCEDURE Content (VAR c: Ctx; id: INTEGER) RAISES {Bad, Store.Error, Wr.Failure, Thread.Alerted} =
  VAR e: Store.Content;  b := TextBuf.New ();  policy := ContentPolicy;  body: TEXT;
  BEGIN
    IF NOT c.t.store.entry (c.user, id, e) THEN RAISE Bad (404) END;
    IF Images (c) THEN policy := policy & RemoteImages END;
    body := e.content;
    IF Text.Empty (body) THEN body := e.summary END;
    IF Text.Empty (body) THEN body := "<p><i>This entry has no text of its own: follow its title.</i></p>" END;
    b.put ("<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\">");
    IF Links.Web (e.base) THEN
      b.put ("<base href=\"" & E (e.base) & "\" target=\"_blank\">");
    ELSE
      b.put ("<base target=\"_blank\">");
    END;
    b.put ("<style>" & ContentStyle & "</style></head><body>\n");
    b.put (body);
    b.put ("\n</body></html>\n");
    Head (c, 200, "text/html; charset=utf-8");
    c.resp.headers.set ("Content-Security-Policy", policy);
    Wr.PutText (c.resp.body (), Utf8.Encode (b.text ()));
  END Content;

PROCEDURE Mark (VAR c: Ctx; id: INTEGER) RAISES {Store.Error, Thread.Alerted} =
  VAR s := c.t.store;  read := Field (c, "read");  starred := Field (c, "starred");
  BEGIN
    IF read # NIL THEN s.setRead (c.user, id, Is (read, "1")) END;
    IF starred # NIL THEN s.setStarred (c.user, id, Is (starred, "1")) END;
    Redirect (c, Back (Field (c, "back"), "/entry/" & Fmt.Int (id)));
  END Mark;

(* The feeds *)

PROCEDURE State (READONLY f: Store.FeedRow): TEXT =
  BEGIN
    IF f.lastFetch = 0.0D0 THEN RETURN "Not fetched yet." END;
    IF f.lastError # NIL THEN
      RETURN "Failed " & Fmt.Int (f.errors) & " times, last at " & Page.When (f.lastFetch) & ": " & E (f.lastError)
               & ". Next try at " & Page.When (f.nextFetch) & ".";
    END;
    RETURN "Fetched at " & Page.When (f.lastFetch) & ", next at " & Page.When (f.nextFetch) & ".";
  END State;

PROCEDURE Feeds (VAR c: Ctx; message: TEXT; error: BOOLEAN) RAISES {Store.Error, Wr.Failure, Thread.Alerted} =
  VAR feeds := c.t.store.feeds (c.user);  b := Page.Begin ("Feeds", c.name, c.csrf);  cls: TEXT;
  BEGIN
    b.put ("<main class=\"narrow\"><h1>Feeds</h1>");
    IF message # NIL THEN
      IF error THEN cls := "msg error" ELSE cls := "msg" END;
      b.put ("<p class=\"" & cls & "\">" & E (message) & "</p>");
    END;
    b.put ("<form method=\"post\" action=\"/feeds/add\" class=\"add\">");
    Page.Hidden (b, "csrf", c.csrf);
    b.put ("<input name=\"url\" required placeholder=\"The address of a feed, or of a web site\" "
             & "aria-label=\"Address\"> <input name=\"title\" placeholder=\"Name (if you like)\" "
             & "aria-label=\"Name\"> <button>Subscribe</button></form>\n");
    IF NUMBER (feeds^) > 0 THEN
      b.put ("<table class=\"feeds\"><thead><tr><th>Feed</th><th class=\"num\">Unread</th><th></th></tr></thead>"
               & "<tbody>");
      FOR i := 0 TO LAST (feeds^) DO
        WITH f = feeds[i] DO
          cls := "state";
          IF f.lastError # NIL THEN cls := "state failing" END;
          b.put ("<tr><td><a href=\"/?feed=" & Fmt.Int (f.id) & "\">" & E (Or (f.title, f.url)) & "</a>"
                   & "<div class=\"url\">" & E (f.url) & "</div><div class=\"" & cls & "\">" & State (f)
                   & "</div></td><td class=\"num\">" & Fmt.Int (f.unread) & " / " & Fmt.Int (f.entries)
                   & "</td><td class=\"do\">");
          b.put ("<form method=\"post\" action=\"/feeds/" & Fmt.Int (f.id) & "/read\">");
          Page.Hidden (b, "csrf", c.csrf);
          Page.Hidden (b, "back", "/feeds");
          b.put ("<button>Mark as read</button></form>");
          b.put ("<form method=\"post\" action=\"/feeds/" & Fmt.Int (f.id) & "/remove\">");
          Page.Hidden (b, "csrf", c.csrf);
          b.put ("<button data-confirm=\"Unsubscribe from this feed?\">Unsubscribe</button></form>");
          b.put ("</td></tr>\n");
        END;
      END;
      b.put ("</tbody></table>\n");
    END;
    b.put ("<h2>Lists of feeds</h2><p>Feed readers trade their subscriptions as lists in OPML.</p>"
             & "<form method=\"post\" action=\"/import\" enctype=\"multipart/form-data\" class=\"add\">");
    Page.Hidden (b, "csrf", c.csrf);
    b.put ("<input type=\"file\" name=\"file\" accept=\".opml,.xml,text/xml,application/xml\" required "
             & "aria-label=\"A list in OPML\"> <button>Subscribe to its feeds</button></form>"
             & "<p><a href=\"/export.opml\" download=\"feeds.opml\">Save the list of these feeds</a></p>"
             & "<form method=\"post\" action=\"/demo\">");
    Page.Hidden (b, "csrf", c.csrf);
    b.put ("<button>Subscribe to the feeds to start with</button></form>\n");
    b.put ("<h2>Images</h2><form method=\"post\" action=\"/settings\">");
    Page.Hidden (b, "csrf", c.csrf);
    IF c.t.store.showImages (c.user) THEN
      b.put ("<p>Entries show the images of other servers, which can tell those servers when you read "
               & "what. <button name=\"images\" value=\"0\">Do not show them</button></p>");
    ELSE
      b.put ("<p>Entries show no images of other servers, unless you ask for them in an entry. "
               & "<button name=\"images\" value=\"1\">Show them</button></p>");
    END;
    b.put ("</form>\n");
    b.put ("<h2>API</h2><p>Programs read the entries and subscribe to feeds through a JSON API, with a "
             & "token of their own. A new token makes the one before invalid.</p>"
             & "<form method=\"post\" action=\"/token\">");
    Page.Hidden (b, "csrf", c.csrf);
    b.put ("<button>Make a new token</button></form></main>");
    Send (c, 200, Page.End (b));
  END Feeds;

PROCEDURE AddFeed (VAR c: Ctx) RAISES {Store.Error, Wr.Failure, Thread.Alerted} =
  VAR url := Or (Field (c, "url"), "");  title := Field (c, "title");  feedUrl: TEXT;  id: INTEGER;  now: Time.T;
  BEGIN
    IF title # NIL AND Text.Empty (title) THEN title := NIL END;
    IF Text.FindChar (url, ':') < 0 THEN url := "https://" & url END;
    url := Links.Resolve (NIL, url);
    IF url = NIL THEN
      Feeds (c, "That is not the address of a web page.", TRUE);
      RETURN;
    END;
    TRY
      WITH f = Fetcher.Find (c.t.client, url, feedUrl) DO
        id := c.t.store.subscribe (c.user, feedUrl, title);
        now := Time.Now ();
        IF c.t.store.fetched (id, f, NIL, NIL, now, now + Fetcher.Interval) > 0 THEN Events.Signal () END;
      END;
    EXCEPT
    | Fetcher.Error (m) => Feeds (c, "No feed: " & m, TRUE); RETURN;
    END;
    Redirect (c, "/?show=all&feed=" & Fmt.Int (id));
  END AddFeed;

PROCEDURE FeedAction (VAR c: Ctx; id: INTEGER; action: TEXT) RAISES {Bad, Store.Error, Thread.Alerted} =
  VAR s := c.t.store;
  BEGIN
    IF id < 0 THEN RAISE Bad (404) END;
    IF Text.Equal (action, "read") THEN
      s.markAllRead (c.user, id);
      Redirect (c, Back (Field (c, "back"), "/"));
    ELSIF Text.Equal (action, "remove") AND id > 0 THEN
      s.unsubscribe (c.user, id);
      Redirect (c, "/feeds");
    ELSE
      RAISE Bad (404);
    END;
  END FeedAction;

PROCEDURE Import (VAR c: Ctx) RAISES {Bad, Store.Error, Wr.Failure, Thread.Alerted} =
  VAR
    boundary := Multipart.Boundary (c.req.headers.get ("content-type"));
    r: Multipart.Reader;
    p: Multipart.Part;
    csrf, data: TEXT := NIL;
  BEGIN
    IF boundary = NIL THEN RAISE Bad (400) END;
    TRY
      r := Multipart.NewReader (c.req.body, boundary,
                                Multipart.Limits {maxParts := 10, maxPartBytes := MaxOpml,
                                                  maxTotalBytes := MaxOpml + 65536});
      WHILE r.next (p) DO
        IF Is (p.name, "csrf") THEN
          csrf := Rd.GetText (p.body, 1000);
        ELSIF Is (p.name, "file") THEN
          data := Rd.GetText (p.body, MaxOpml);
        END;
      END;
    EXCEPT
    | Rd.Failure =>
        CheckPost (c, csrf);
        Feeds (c, "The file could not be read, or it is larger than 4 MB.", TRUE);
        RETURN;
    END;
    CheckPost (c, csrf);
    IF data = NIL THEN
      Feeds (c, "No file came.", TRUE);
      RETURN;
    END;
    Subscribe (c, data, NIL);
  END Import;

(* Subscribes to the feeds of the list in OPML "data", and goes to "next",
   or to the page of the feeds if it is NIL. *)
PROCEDURE Subscribe (VAR c: Ctx; data, next: TEXT) RAISES {Store.Error, Wr.Failure, Thread.Alerted} =
  VAR list: Opml.Outlines;
  BEGIN
    TRY
      list := Opml.Read (TextRd.New (data));
    EXCEPT
    | Opml.Error (m) => Feeds (c, "The list could not be read: " & m, TRUE); RETURN;
    | Rd.Failure => Feeds (c, "That is not a list of feeds.", TRUE); RETURN;
    END;
    FOR i := 0 TO LAST (list^) DO
      EVAL c.t.store.subscribe (c.user, list[i].xmlUrl, Or (list[i].title, NIL));
    END;
    IF c.t.fetcher # NIL THEN Fetcher.Kick (c.t.fetcher) END;
    IF next # NIL THEN
      Redirect (c, next);
    ELSE
      Feeds (c, "Subscribed to " & Fmt.Int (NUMBER (list^)) & " feeds; they are being fetched.", FALSE);
    END;
  END Subscribe;

PROCEDURE Export (VAR c: Ctx) RAISES {Store.Error, Wr.Failure, Thread.Alerted} =
  VAR feeds := c.t.store.feeds (c.user);  list := NEW (Opml.Outlines, NUMBER (feeds^));
  BEGIN
    FOR i := 0 TO LAST (feeds^) DO
      list[i] := Opml.Outline {title := Or (feeds[i].title, feeds[i].url), xmlUrl := feeds[i].url,
                               htmlUrl := feeds[i].site};
    END;
    Head (c, 200, "text/x-opml; charset=utf-8");
    c.resp.headers.set ("Content-Disposition", "attachment; filename=\"feeds.opml\"");
    Opml.Write (c.resp.body (), "Feeds of " & c.name, list^);
  END Export;

PROCEDURE NewToken (VAR c: Ctx) RAISES {Store.Error, Wr.Failure, Thread.Alerted} =
  VAR token := Auth.NewApiToken (c.t.store, c.user);  b := Page.Begin ("API", c.name, c.csrf);
      host := Or (c.req.headers.get ("host"), "localhost:8080");  scheme := "http";
  BEGIN
    IF c.req.secure THEN scheme := "https" END;
    b.put ("<main class=\"narrow\"><h1>A token for the API</h1><p>This is the new token. It is shown only "
             & "now: the reader keeps no more than its hash.</p><pre class=\"token\">" & E (token) & "</pre>"
             & "<p>For example:</p><pre>curl -H 'Authorization: Bearer " & E (token) & "' " & E (scheme)
             & "://" & E (host) & "/api/entries?q=modula\n"
             & "curl -H 'Authorization: Bearer " & E (token) & "' -H 'Content-Type: application/json' \\\n"
             & "     -d '{\"url\": \"https://xkcd.com/\"}' " & E (scheme) & "://" & E (host) & "/api/feeds</pre>"
             & "<p><a href=\"/feeds\">Back to the feeds</a></p></main>");
    Send (c, 200, Page.End (b));
  END NewToken;

(* News, as Server-Sent Events: an event "news" when new entries have come,
   and a comment now and then that keeps the connection alive. It ends
   when the page goes away, or when the server is shut down. *)
PROCEDURE Stream (VAR c: Ctx) RAISES {Thread.Alerted} =
  VAR wr: Wr.T;  seen := Events.Count ();  now: INTEGER;
  BEGIN
    Head (c, 200, "text/event-stream");
    TRY
      wr := c.resp.body ();
      Wr.PutText (wr, "retry: 10000\n\n");
      Wr.Flush (wr);
      LOOP
        now := Events.Wait (seen, Heartbeat);
        IF now > seen THEN
          Wr.PutText (wr, "event: news\ndata: " & Fmt.Int (now) & "\n\n");
          seen := now;
        ELSE
          Wr.PutText (wr, ": still here\n\n");
        END;
        Wr.Flush (wr);
      END;
    EXCEPT
    | Wr.Failure => (* the page is gone *)
    END;
  END Stream;

PROCEDURE StaticFile (VAR c: Ctx; name: TEXT) RAISES {Bad, Wr.Failure, Thread.Alerted} =
  VAR type, data: TEXT;
  BEGIN
    IF Text.Equal (name, "style.css") THEN
      type := "text/css; charset=utf-8";
    ELSIF Text.Equal (name, "app.js") THEN
      type := "text/javascript; charset=utf-8";
    ELSE
      RAISE Bad (404);
    END;
    data := Bundle.Get (Static.Get (), name);
    c.resp.status := 200;
    c.resp.headers.set ("Content-Type", type);
    c.resp.headers.set ("X-Content-Type-Options", "nosniff");
    c.resp.headers.set ("Cache-Control", "public, max-age=3600");
    c.resp.compress ();
    Wr.PutText (c.resp.body (), data);
  END StaticFile;

BEGIN
END Web.
