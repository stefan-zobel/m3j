(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* A reader of news feeds.

     feedreader [-db FILE] [-user NAME] [-timeout SECONDS] COMMAND ...

   The commands:

     fetch SOURCE [-base URL] [-html]   shows a feed, without the database
     check SOURCE...                    one line for each feed: what was understood
     adduser NAME                       a new user, with the password from the standard input
     add URL [-title TITLE]             subscribes to the feed at URL, or the one its page names
     import FILE                        subscribes to the feeds of a list in OPML
     export [FILE]                      the subscriptions as a list in OPML
     feeds                              the subscriptions, and how their fetching goes
     sync [-all] [-workers N]           fetches the feeds that are due, or all of them, once
     list [-unread] [-starred] [-feed ID] [-q WORDS] [-n COUNT]
                                        the newest entries
     serve [-listen HOST:PORT] [-workers N]
                                        the web interface, while the feeds are fetched in the
                                        background; at 127.0.0.1:8080 unless "-listen" says otherwise

   "-workers" is the number of threads that fetch feeds, 4 by default.
   Ctrl+C ends "serve" in order: the server stops, the fetching threads
   end, and the database is closed; a second Ctrl+C ends it at once.
   "-timeout SECONDS" before the command limits the wait for a server that
   is asked for a feed, 30 seconds by default.

   A SOURCE is an "http" or "https" URL, or a file. "-base" gives the URL
   that relative links in a file are resolved against; "-html" shows the
   HTML of each entry as well. The database is "feedreader.db" unless
   "-db" names another; the commands act for the first user unless "-user"
   names another. *)

MODULE Main;

IMPORT Auth, Feed, FeedDate, Fetch, Fetcher, FileRd, FileWr, Fmt, FS, HTTPClient, HTTPServer, HtmlText, IP, Log,
       Opml, OSError, Params, Process, Rd, RTProcess, Scan, Stdio, Store, TCP, Text, TextRd, Thread, Time, Utf8, Web,
       Wr;

IMPORT Lex, FloatMode;

<*FATAL Thread.Alerted*>

EXCEPTION
  Failed (TEXT);
  Usage;

VAR
  args: REF ARRAY OF TEXT;      (* the arguments after the command *)
  dbPath := "feedreader.db";
  userName: TEXT := NIL;
  client: HTTPClient.T := NIL;
  store: Store.T := NIL;
  server: HTTPServer.T := NIL;   (* that of "serve", for Ctrl+C *)
  interrupts := 0;

(* Output *)

PROCEDURE Out (t: TEXT) =
  <*FATAL Wr.Failure*>
  BEGIN
    Wr.PutText (Stdio.stdout, Utf8.Encode (t & "\n"));
  END Out;

PROCEDURE Err (t: TEXT) =
  <*FATAL Wr.Failure*>
  BEGIN
    Wr.PutText (Stdio.stderr, Utf8.Encode ("feedreader: " & t & "\n"));
    Wr.Flush (Stdio.stderr);
  END Err;

PROCEDURE ShowUsage () =
  <*FATAL Wr.Failure*>
  BEGIN
    Wr.PutText (Stdio.stderr,
                "usage: feedreader [-db FILE] [-user NAME] [-timeout SECONDS] COMMAND ...\n"
                  & "  fetch SOURCE [-base URL] [-html]\n"
                  & "  check SOURCE...\n"
                  & "  adduser NAME\n"
                  & "  add URL [-title TITLE]\n"
                  & "  import FILE\n"
                  & "  export [FILE]\n"
                  & "  feeds\n"
                  & "  sync [-all] [-workers N]\n"
                  & "  list [-unread] [-starred] [-feed ID] [-q WORDS] [-n COUNT]\n"
                  & "  serve [-listen HOST:PORT] [-workers N]\n");
    Wr.Flush (Stdio.stderr);
  END ShowUsage;

PROCEDURE Or (t, default: TEXT): TEXT =
  BEGIN
    IF t = NIL OR Text.Empty (t) THEN RETURN default END;
    RETURN t;
  END Or;

(* The time "t" in UTC, to the minute. *)
PROCEDURE When (t: Time.T): TEXT =
  VAR r := FeedDate.Format (t);
  BEGIN
    RETURN Text.Sub (r, 0, 10) & " " & Text.Sub (r, 11, 5);
  END When;

(* Arguments *)

(* Whether the flag "name" is among the arguments; removes it. *)
PROCEDURE Flag (name: TEXT): BOOLEAN =
  BEGIN
    FOR i := 0 TO LAST (args^) DO
      IF args[i] # NIL AND Text.Equal (args[i], name) THEN
        args[i] := NIL;
        RETURN TRUE;
      END;
    END;
    RETURN FALSE;
  END Flag;

(* The value after the option "name", or NIL; removes both. *)
PROCEDURE Option (name: TEXT): TEXT RAISES {Usage} =
  VAR v: TEXT;
  BEGIN
    FOR i := 0 TO LAST (args^) DO
      IF args[i] # NIL AND Text.Equal (args[i], name) THEN
        IF i = LAST (args^) OR args[i + 1] = NIL THEN RAISE Usage END;
        v := args[i + 1];
        args[i] := NIL;
        args[i + 1] := NIL;
        RETURN v;
      END;
    END;
    RETURN NIL;
  END Option;

PROCEDURE Number (t: TEXT): INTEGER RAISES {Usage} =
  BEGIN
    TRY
      RETURN Scan.Int (t);
    EXCEPT
    | Lex.Error, FloatMode.Trap => RAISE Usage;
    END;
  END Number;

(* The arguments that are left, after the options are taken; an argument
   that looks like an option is an error. *)
PROCEDURE Rest (): REF ARRAY OF TEXT RAISES {Usage} =
  VAR n := 0;  r: REF ARRAY OF TEXT;
  BEGIN
    FOR i := 0 TO LAST (args^) DO
      IF args[i] # NIL THEN
        IF Text.Length (args[i]) > 1 AND Text.GetChar (args[i], 0) = '-' THEN RAISE Usage END;
        INC (n);
      END;
    END;
    r := NEW (REF ARRAY OF TEXT, n);
    n := 0;
    FOR i := 0 TO LAST (args^) DO
      IF args[i] # NIL THEN r[n] := args[i]; INC (n) END;
    END;
    RETURN r;
  END Rest;

(* The store and the user *)

PROCEDURE Db (): Store.T RAISES {Failed} =
  BEGIN
    IF store = NIL THEN
      TRY
        store := Store.Open (dbPath);
      EXCEPT
      | Store.Error (m) => RAISE Failed (m);
      END;
    END;
    RETURN store;
  END Db;

(* The user that the command acts for. *)
PROCEDURE User (): INTEGER RAISES {Failed, Store.Error} =
  VAR s := Db ();  id, iterations: INTEGER;  salt, hash: TEXT;
  BEGIN
    IF userName # NIL THEN
      IF NOT s.user (userName, id, salt, hash, iterations) THEN RAISE Failed ("no user " & userName) END;
      RETURN id;
    END;
    id := s.firstUser ();
    IF id < 0 THEN RAISE Failed ("there is no user yet: feedreader adduser NAME") END;
    RETURN id;
  END User;

PROCEDURE Client (): HTTPClient.T =
  BEGIN
    IF client = NIL THEN client := Fetch.NewClient () END;
    RETURN client;
  END Client;

(* fetch and check *)

PROCEDURE IsUrl (t: TEXT): BOOLEAN =
  BEGIN
    RETURN Text.Equal (Text.Sub (t, 0, 7), "http://") OR Text.Equal (Text.Sub (t, 0, 8), "https://");
  END IsUrl;

(* The feed of "source", with "base" for relative links in a file. *)
PROCEDURE Load (source, base: TEXT): Feed.T RAISES {Failed} =
  VAR rd: Rd.T;  r: Fetch.Result;
  BEGIN
    TRY
      IF IsUrl (source) THEN
        r := Fetch.Get (Client (), source);
        IF r.body = NIL THEN RAISE Failed ("HTTP status " & Fmt.Int (r.status)) END;
        RETURN Feed.Parse (TextRd.New (r.body), source);
      ELSE
        rd := FileRd.Open (source);
        TRY
          RETURN Feed.Parse (rd, base);
        FINALLY
          Rd.Close (rd);
        END;
      END;
    EXCEPT
    | Fetch.Error (t) => RAISE Failed (t);
    | Feed.Error (t) => RAISE Failed (t);
    | OSError.E => RAISE Failed ("cannot open " & source);
    | Rd.Failure => RAISE Failed ("cannot read " & source);
    END;
  END Load;

PROCEDURE DateOf (e: Feed.Entry): TEXT =
  BEGIN
    IF e.dated THEN RETURN FeedDate.Format (e.date) END;
    IF e.dateText = NIL THEN RETURN "no date" END;
    RETURN "bad date \"" & e.dateText & "\"";
  END DateOf;

PROCEDURE Show (f: Feed.T; html: BOOLEAN) =
  VAR e: Feed.Entry;  body: TEXT;
  BEGIN
    Out (Feed.FormatNames[f.format] & ": " & f.title);
    Out ("link: " & Or (f.link, "-"));
    IF NOT Text.Empty (f.description) THEN Out ("description: " & HtmlText.Excerpt (f.description, 200)) END;
    IF NUMBER (f.entries^) = 1 THEN Out ("1 entry") ELSE Out (Fmt.Int (NUMBER (f.entries^)) & " entries") END;
    FOR i := 0 TO LAST (f.entries^) DO
      e := f.entries[i];
      Out ("* " & DateOf (e) & "  " & e.title);
      Out ("  link: " & Or (e.link, "-"));
      Out ("  id: " & Or (e.id, "-"));
      IF NOT Text.Empty (e.author) THEN Out ("  author: " & e.author) END;
      body := e.content;
      IF Text.Empty (body) THEN body := e.summary END;
      body := HtmlText.ToText (body);
      IF NOT Text.Empty (body) THEN Out ("  text: " & HtmlText.Excerpt (body, 160)) END;
      IF html THEN
        IF NOT Text.Empty (e.summary) THEN Out ("  summary: " & e.summary) END;
        IF NOT Text.Empty (e.content) THEN Out ("  content: " & e.content) END;
        Out ("  base: " & Or (e.base, "-"));
      END;
    END;
  END Show;

PROCEDURE DoFetch () RAISES {Failed, Usage} =
  VAR base := Option ("-base");  html := Flag ("-html");  rest := Rest ();
  BEGIN
    IF NUMBER (rest^) # 1 THEN RAISE Usage END;
    TRY
      Show (Load (rest[0], base), html);
    EXCEPT
    | Failed (t) => RAISE Failed (rest[0] & ": " & t);
    END;
  END DoFetch;

(* One line about the feed. *)
PROCEDURE Check (source: TEXT) =
  VAR f: Feed.T;  undated := 0;  bad := 0;  firstBad: TEXT := NIL;  line: TEXT;
  BEGIN
    TRY
      f := Load (source, NIL);
      FOR i := 0 TO LAST (f.entries^) DO
        WITH e = f.entries[i] DO
          IF e.dateText = NIL THEN
            INC (undated);
          ELSIF NOT e.dated THEN
            INC (bad);
            IF firstBad = NIL THEN firstBad := e.dateText END;
          END;
        END;
      END;
      line := source & ": ok " & Feed.FormatNames[f.format] & " " & Fmt.Int (NUMBER (f.entries^)) & " entries";
      IF undated > 0 THEN line := line & ", " & Fmt.Int (undated) & " without a date" END;
      IF bad > 0 THEN line := line & ", " & Fmt.Int (bad) & " bad dates such as \"" & firstBad & "\"" END;
      Out (line);
    EXCEPT
    | Failed (t) => Out (source & ": error: " & t);
    END;
  END Check;

PROCEDURE DoCheck () RAISES {Usage} =
  VAR rest := Rest ();
  BEGIN
    IF NUMBER (rest^) = 0 THEN RAISE Usage END;
    FOR i := 0 TO LAST (rest^) DO Check (rest[i]) END;
  END DoCheck;

(* Users and subscriptions *)

PROCEDURE DoAddUser () RAISES {Failed, Usage, Store.Error} =
  VAR rest := Rest ();  password: TEXT;  n: INTEGER;
  BEGIN
    IF NUMBER (rest^) # 1 THEN RAISE Usage END;
    TRY
      password := Rd.GetLine (Stdio.stdin);
    EXCEPT
    | Rd.EndOfFile, Rd.Failure => RAISE Failed ("no password on the standard input");
    END;
    n := Text.Length (password);
    IF n > 0 AND Text.GetChar (password, n - 1) = '\r' THEN password := Text.Sub (password, 0, n - 1) END;
    TRY
      EVAL Auth.AddUser (Db (), rest[0], password);
    EXCEPT
    | Auth.Error (m) => RAISE Failed (m);
    END;
    Out ("added the user " & rest[0]);
  END DoAddUser;

PROCEDURE DoAdd () RAISES {Failed, Usage, Store.Error} =
  VAR title := Option ("-title");  rest := Rest ();  url: TEXT;  f: Feed.T;  id, user: INTEGER;  now: Time.T;
  BEGIN
    IF NUMBER (rest^) # 1 THEN RAISE Usage END;
    user := User ();
    TRY
      f := Fetcher.Find (Client (), rest[0], url);
    EXCEPT
    | Fetcher.Error (m) => RAISE Failed (m);
    END;
    id := Db ().subscribe (user, url, title);
    now := Time.Now ();
    EVAL store.fetched (id, f, NIL, NIL, now, now + Fetcher.Interval);
    Out ("subscribed to " & Or (title, Or (f.title, url)) & " (" & url & "), "
           & Fmt.Int (NUMBER (f.entries^)) & " entries");
  END DoAdd;

PROCEDURE DoImport () RAISES {Failed, Usage, Store.Error} =
  VAR rest := Rest ();  rd: Rd.T;  list: Opml.Outlines;  user: INTEGER;
  BEGIN
    IF NUMBER (rest^) # 1 THEN RAISE Usage END;
    user := User ();
    TRY
      rd := FileRd.Open (rest[0]);
      TRY
        list := Opml.Read (rd);
      FINALLY
        Rd.Close (rd);
      END;
    EXCEPT
    | OSError.E => RAISE Failed ("cannot open " & rest[0]);
    | Rd.Failure => RAISE Failed ("cannot read " & rest[0]);
    | Opml.Error (m) => RAISE Failed (rest[0] & ": " & m);
    END;
    FOR i := 0 TO LAST (list^) DO
      EVAL store.subscribe (user, list[i].xmlUrl, Or (list[i].title, NIL));
    END;
    Out ("subscribed to " & Fmt.Int (NUMBER (list^)) & " feeds; sync or serve fetches them");
  END DoImport;

PROCEDURE DoExport () RAISES {Failed, Usage, Store.Error} =
  VAR rest := Rest ();  feeds: Store.FeedRows;  list: Opml.Outlines;  wr: Wr.T;
  BEGIN
    IF NUMBER (rest^) > 1 THEN RAISE Usage END;
    feeds := Db ().feeds (User ());
    list := NEW (Opml.Outlines, NUMBER (feeds^));
    FOR i := 0 TO LAST (feeds^) DO
      list[i] := Opml.Outline {title := Or (feeds[i].title, feeds[i].url), xmlUrl := feeds[i].url,
                               htmlUrl := feeds[i].site};
    END;
    TRY
      IF NUMBER (rest^) = 0 THEN
        Opml.Write (Stdio.stdout, "Subscriptions", list^);
      ELSE
        wr := FileWr.Open (rest[0]);
        TRY
          Opml.Write (wr, "Subscriptions", list^);
        FINALLY
          Wr.Close (wr);
        END;
      END;
    EXCEPT
    | OSError.E, Wr.Failure => RAISE Failed ("cannot write " & rest[0]);
    END;
  END DoExport;

PROCEDURE DoFeeds () RAISES {Failed, Usage, Store.Error} =
  VAR rest := Rest ();  feeds: Store.FeedRows;  state: TEXT;
  BEGIN
    IF NUMBER (rest^) # 0 THEN RAISE Usage END;
    feeds := Db ().feeds (User ());
    FOR i := 0 TO LAST (feeds^) DO
      WITH f = feeds[i] DO
        IF f.lastFetch = 0.0D0 THEN
          state := "not fetched yet";
        ELSIF f.lastError # NIL THEN
          state := "failed " & Fmt.Int (f.errors) & " times, last at " & When (f.lastFetch) & ": " & f.lastError;
        ELSE
          state := "fetched " & When (f.lastFetch);
        END;
        Out (Fmt.Pad (Fmt.Int (f.id), 4) & " " & Fmt.Pad (Fmt.Int (f.unread), 5) & "/"
               & Fmt.Pad (Fmt.Int (f.entries), 5, align := Fmt.Align.Left) & " " & Or (f.title, f.url));
        Out ("            " & f.url);
        Out ("            " & state);
      END;
    END;
  END DoFeeds;

TYPE
  Printer = Fetcher.Reporter OBJECT
    mu: MUTEX;
    fetched, new, unchanged, failed := 0;
  OVERRIDES
    report := Report;
  END;

PROCEDURE Report (p: Printer; READONLY r: Fetcher.Report) =
  VAR line := r.url & ": ";
  BEGIN
    CASE r.outcome OF
    | Fetcher.Outcome.Fetched => line := line & Fmt.Int (r.new) & " new";
    | Fetcher.Outcome.Unchanged => line := line & "not modified";
    | Fetcher.Outcome.Failed => line := line & "failed: " & r.message;
    END;
    LOCK p.mu DO
      CASE r.outcome OF
      | Fetcher.Outcome.Fetched => INC (p.fetched); INC (p.new, r.new);
      | Fetcher.Outcome.Unchanged => INC (p.unchanged);
      | Fetcher.Outcome.Failed => INC (p.failed);
      END;
      Out (line & " (" & Fmt.LongReal (r.seconds, Fmt.Style.Fix, 1) & " s)");
    END;
  END Report;

PROCEDURE DoSync () RAISES {Failed, Usage, Store.Error} =
  VAR
    all := Flag ("-all");
    w := Option ("-workers");
    rest := Rest ();
    p := NEW (Printer, mu := NEW (MUTEX));
    start := Time.Now ();
    workers := 4;
  BEGIN
    IF NUMBER (rest^) # 0 THEN RAISE Usage END;
    IF w # NIL THEN workers := MAX (Number (w), 1) END;
    Fetcher.Sync (Db (), all, workers, p);
    Out (Fmt.Int (p.fetched) & " fetched with " & Fmt.Int (p.new) & " new entries, " & Fmt.Int (p.unchanged)
           & " not modified, " & Fmt.Int (p.failed) & " failed, in "
           & Fmt.LongReal (Time.Now () - start, Fmt.Style.Fix, 1) & " s");
  END DoSync;

(* "t" with the marks of a search shown as [ and ]. *)
PROCEDURE Marked (t: TEXT): TEXT =
  VAR a := NEW (REF ARRAY OF WIDECHAR, Text.Length (t));
  BEGIN
    Text.SetWideChars (a^, t);
    FOR i := 0 TO LAST (a^) DO
      IF a[i] = W'\000001' THEN a[i] := W'[' ELSIF a[i] = W'\000002' THEN a[i] := W']' END;
    END;
    RETURN Text.FromWideChars (a^);
  END Marked;

PROCEDURE DoList () RAISES {Failed, Usage, Store.Error} =
  VAR f: Store.Filter;  feed := Option ("-feed");  n := Option ("-n");  rows: Store.EntryRows;  flags: TEXT;
  BEGIN
    f.unread := Flag ("-unread");
    f.starred := Flag ("-starred");
    f.query := Option ("-q");
    IF feed # NIL THEN f.feed := Number (feed) END;
    IF n # NIL THEN f.limit := MAX (Number (n), 0) ELSE f.limit := 20 END;
    IF NUMBER (Rest ()^) # 0 THEN RAISE Usage END;
    rows := Db ().entries (User (), f);
    FOR i := 0 TO LAST (rows^) DO
      WITH e = rows[i] DO
        flags := "  ";
        IF NOT e.read THEN flags := "* " END;
        IF e.starred THEN flags := Text.Sub (flags, 0, 1) & "+" END;
        Out (flags & Fmt.Pad (Fmt.Int (e.id), 6) & " " & When (e.published) & "  " & e.feedTitle & ": " & e.title);
        IF e.link # NIL THEN Out ("                           " & e.link) END;
        IF f.query # NIL THEN Out ("                           " & Marked (e.excerpt)) END;
      END;
    END;
  END DoList;

(* The server *)

TYPE
  (* What the fetching in the background has to say: new entries and
     failures. *)
  Logger = Fetcher.Reporter OBJECT OVERRIDES report := LogReport END;
  ServerLog = HTTPServer.Log OBJECT OVERRIDES log := LogServer END;

PROCEDURE LogReport (<*UNUSED*> l: Logger; READONLY r: Fetcher.Report) =
  BEGIN
    IF r.outcome = Fetcher.Outcome.Failed THEN
      Log.Put (r.url & ": failed: " & r.message);
    ELSIF r.new > 0 THEN
      Log.Put (r.url & ": " & Fmt.Int (r.new) & " new");
    END;
  END LogReport;

PROCEDURE LogServer (<*UNUSED*> l: ServerLog; msg: TEXT) =
  BEGIN
    Log.Put ("server: " & msg);
  END LogServer;

(* Ctrl+C while "serve" runs: the first shuts the server down, in a thread
   of its own, so that "serve" returns and ends in order; a second, if that
   hangs, ends the program at once. *)
PROCEDURE Interrupted () =
  BEGIN
    INC (interrupts);
    IF interrupts = 1 AND server # NIL THEN
      Log.Put ("interrupted: shutting down (Ctrl+C again ends at once)");
      EVAL Thread.Fork (NEW (Thread.Closure, apply := ShutDown));
    ELSE
      Log.Put ("interrupted again: ending now");
      Process.Exit (130);
    END;
  END Interrupted;

PROCEDURE ShutDown (<*UNUSED*> self: Thread.Closure): REFANY =
  BEGIN
    server.shutdown (2.0D0);
    RETURN NIL;
  END ShutDown;

PROCEDURE DoServe () RAISES {Failed, Usage, Store.Error} =
  VAR
    path := dbPath;
    listen := Or (Option ("-listen"), "127.0.0.1:8080");
    w := Option ("-workers");
    workers := 4;
    colon := Text.FindCharR (listen, ':');
    host: TEXT;
    port: INTEGER;
    addr: IP.Address;
    s: Store.T;
    f: Fetcher.T;
    c: TCP.Connector;
  BEGIN
    IF w # NIL THEN workers := MAX (Number (w), 1) END;
    IF NUMBER (Rest ()^) # 0 OR colon < 0 THEN RAISE Usage END;
    host := Text.Sub (listen, 0, colon);
    port := Number (Text.Sub (listen, colon + 1));
    IF port < 0 OR port > LAST (IP.Port) THEN RAISE Usage END;
    TRY
      IF Text.Empty (host) OR Text.Equal (host, "*") THEN
        addr := IP.NullAddress;
        host := "localhost";
      ELSIF NOT IP.GetHostByName (host, addr) THEN
        RAISE Failed ("no host " & host);
      END;
      c := TCP.NewConnector (IP.Endpoint {addr, port});
    EXCEPT
    | IP.Error => RAISE Failed ("cannot listen at " & listen);
    END;
    s := Db ();
    f := Fetcher.Start (s, workers, NEW (Logger));
    server := HTTPServer.New (HTTPServer.Options {log := NEW (ServerLog)});
    server.register (Web.New (s, f));
    TRY path := FS.GetAbsolutePathname (dbPath) EXCEPT OSError.E => END;
    Log.Put ("database " & path & ", " & Plural (s.userCount (), "user"));
    Out ("feedreader: open http://" & host & ":" & Fmt.Int (port) & "/ in a browser");
    IF s.userCount () = 0 THEN Out ("feedreader: there is no user yet; the page makes the first one") END;
    TRY Wr.Flush (Stdio.stdout) EXCEPT Wr.Failure => END;
    EVAL RTProcess.OnInterrupt (Interrupted);
    TRY
      server.serve (c);
    EXCEPT
    | IP.Error => RAISE Failed ("the server failed");
    END;
    Fetcher.Stop (f);
    store := NIL;
    s.close ();
    Log.Put ("stopped; the database is closed");
  END DoServe;

PROCEDURE Plural (n: INTEGER; what: TEXT): TEXT =
  BEGIN
    IF n = 1 THEN RETURN "1 " & what END;
    RETURN Fmt.Int (n) & " " & what & "s";
  END Plural;

(* The command line *)

PROCEDURE Run (): INTEGER =
  VAR n := Params.Count;  i := 1;  cmd: TEXT;
  BEGIN
    TRY
      WHILE i < n AND Text.GetChar (Params.Get (i), 0) = '-' DO
        IF i + 1 >= n THEN RAISE Usage END;
        IF Text.Equal (Params.Get (i), "-db") THEN
          dbPath := Params.Get (i + 1);
        ELSIF Text.Equal (Params.Get (i), "-user") THEN
          userName := Params.Get (i + 1);
        ELSIF Text.Equal (Params.Get (i), "-timeout") THEN
          TRY
            Fetcher.timeout := Scan.LongReal (Params.Get (i + 1));
          EXCEPT
          | Lex.Error, FloatMode.Trap => RAISE Usage;
          END;
          IF Fetcher.timeout <= 0.0D0 THEN RAISE Usage END;
        ELSE
          RAISE Usage;
        END;
        INC (i, 2);
      END;
      IF i >= n THEN RAISE Usage END;
      cmd := Params.Get (i);
      args := NEW (REF ARRAY OF TEXT, n - i - 1);
      FOR k := 0 TO LAST (args^) DO args[k] := Params.Get (i + 1 + k) END;
      TRY
        IF Text.Equal (cmd, "fetch") THEN DoFetch ()
        ELSIF Text.Equal (cmd, "check") THEN DoCheck ()
        ELSIF Text.Equal (cmd, "adduser") THEN DoAddUser ()
        ELSIF Text.Equal (cmd, "add") THEN DoAdd ()
        ELSIF Text.Equal (cmd, "import") THEN DoImport ()
        ELSIF Text.Equal (cmd, "export") THEN DoExport ()
        ELSIF Text.Equal (cmd, "feeds") THEN DoFeeds ()
        ELSIF Text.Equal (cmd, "sync") THEN DoSync ()
        ELSIF Text.Equal (cmd, "list") THEN DoList ()
        ELSIF Text.Equal (cmd, "serve") THEN DoServe ()
        ELSE RAISE Usage
        END;
      EXCEPT
      | Store.Error (m) => RAISE Failed (m);
      END;
    EXCEPT
    | Usage => ShowUsage (); RETURN 2;
    | Failed (m) => Err (m); RETURN 1;
    END;
    RETURN 0;
  END Run;

VAR code: INTEGER;

<*FATAL Wr.Failure*>
BEGIN
  code := Run ();
  Wr.Flush (Stdio.stdout);
  IF client # NIL THEN HTTPClient.Close (client) END;
  IF store # NIL THEN store.close () END;
  IF code # 0 THEN Process.Exit (code) END;
END Main.
