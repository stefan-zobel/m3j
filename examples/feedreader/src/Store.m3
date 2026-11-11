(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Store;

IMPORT Crypto, Digest, Feed, Fmt, Hex, HtmlText, SQL, Text, Thread, Time, Utf8;

CONST
  Schema = ARRAY OF TEXT {
    "CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
    "CREATE TABLE user (id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE, salt BLOB NOT NULL,"
      & " hash BLOB NOT NULL, iterations INTEGER NOT NULL, api_token BLOB UNIQUE, created INTEGER NOT NULL)",
    "CREATE TABLE session (hash BLOB PRIMARY KEY,"
      & " user_id INTEGER NOT NULL REFERENCES user (id) ON DELETE CASCADE,"
      & " csrf TEXT NOT NULL, expires INTEGER NOT NULL)",
    "CREATE TABLE feed (id INTEGER PRIMARY KEY, url TEXT NOT NULL UNIQUE, title TEXT NOT NULL DEFAULT '',"
      & " site TEXT, etag TEXT, last_modified TEXT, last_fetch INTEGER NOT NULL DEFAULT 0,"
      & " next_fetch INTEGER NOT NULL DEFAULT 0, errors INTEGER NOT NULL DEFAULT 0, last_error TEXT)",
    "CREATE TABLE subscription (user_id INTEGER NOT NULL REFERENCES user (id) ON DELETE CASCADE,"
      & " feed_id INTEGER NOT NULL REFERENCES feed (id) ON DELETE CASCADE,"
      & " title TEXT, added INTEGER NOT NULL, PRIMARY KEY (user_id, feed_id))",
    "CREATE INDEX subscription_feed ON subscription (feed_id)",
    "CREATE TABLE entry (id INTEGER PRIMARY KEY,"
      & " feed_id INTEGER NOT NULL REFERENCES feed (id) ON DELETE CASCADE,"
      & " key TEXT NOT NULL, title TEXT NOT NULL, link TEXT, author TEXT NOT NULL,"
      & " published INTEGER NOT NULL, dated INTEGER NOT NULL, summary TEXT NOT NULL, content TEXT NOT NULL,"
      & " base TEXT, text TEXT NOT NULL, fetched INTEGER NOT NULL, UNIQUE (feed_id, key))",
    "CREATE INDEX entry_published ON entry (published DESC, id DESC)",
    "CREATE TABLE entry_state (user_id INTEGER NOT NULL REFERENCES user (id) ON DELETE CASCADE,"
      & " entry_id INTEGER NOT NULL REFERENCES entry (id) ON DELETE CASCADE,"
      & " read INTEGER NOT NULL DEFAULT 0, starred INTEGER NOT NULL DEFAULT 0,"
      & " PRIMARY KEY (user_id, entry_id))",
    "CREATE INDEX entry_state_entry ON entry_state (entry_id)",
    "CREATE VIRTUAL TABLE entry_fts USING fts5 (title, text, content = 'entry', content_rowid = 'id')",
    "CREATE TRIGGER entry_insert AFTER INSERT ON entry BEGIN"
      & " INSERT INTO entry_fts (rowid, title, text) VALUES (new.id, new.title, new.text); END",
    "CREATE TRIGGER entry_delete AFTER DELETE ON entry BEGIN"
      & " INSERT INTO entry_fts (entry_fts, rowid, title, text) VALUES ('delete', old.id, old.title, old.text);"
      & " END",
    "CREATE TRIGGER entry_update AFTER UPDATE OF title, text ON entry BEGIN"
      & " INSERT INTO entry_fts (entry_fts, rowid, title, text) VALUES ('delete', old.id, old.title, old.text);"
      & " INSERT INTO entry_fts (rowid, title, text) VALUES (new.id, new.title, new.text); END"};

  (* What brings a database of the version before up to each version from
     2 on; "Schema" makes version 1. *)
  Migrations = ARRAY [2 .. SchemaVersion] OF TEXT {
    "ALTER TABLE user ADD COLUMN show_images INTEGER NOT NULL DEFAULT 1"};

  (* The columns of a "FeedRow", with "title" for the title. *)
  FeedColumns = "f.id, f.url, %title%, f.site, f.etag, f.last_modified, f.last_fetch, f.next_fetch, f.errors,"
                  & " f.last_error";
  UserTitle = "COALESCE (NULLIF (s.title, ''), f.title)";

  (* The columns of an "EntryRow", with "excerpt" for the excerpt. *)
  EntryColumns = "e.id, e.feed_id, " & UserTitle & ", e.title, e.link, e.author, e.published, e.dated,"
                   & " %excerpt%, COALESCE (st.read, 0), COALESCE (st.starred, 0)";
  EntryJoins = " FROM entry e JOIN subscription s ON s.feed_id = e.feed_id AND s.user_id = ?1"
                 & " JOIN feed f ON f.id = e.feed_id"
                 & " LEFT JOIN entry_state st ON st.entry_id = e.id AND st.user_id = ?1";

  ExcerptChars = 400;           (* of the text of an entry in a list *)

REVEAL
  T = Public BRANDED OBJECT
    mu: MUTEX;
    db: SQL.T;
  OVERRIDES
    close := Close;
    userCount := UserCount;
    addUser := AddUser;
    user := User;
    userName := UserName;
    firstUser := FirstUser;
    setPassword := SetPassword;
    setApiToken := SetApiToken;
    showImages := ShowImages;
    setShowImages := SetShowImages;
    userByApiToken := UserByApiToken;
    addSession := AddSession;
    session := Session;
    removeSession := RemoveSession;
    removeExpired := RemoveExpired;
    subscribe := Subscribe;
    unsubscribe := Unsubscribe;
    rename := Rename;
    feeds := Feeds;
    feed := GetFeed;
    due := Due;
    fetched := Fetched;
    unchanged := Unchanged;
    failed := Failed;
    entries := Entries;
    entry := GetEntry;
    setRead := SetRead;
    setStarred := SetStarred;
    markAllRead := MarkAllRead;
  END;

(* Helpers *)

PROCEDURE Secs (t: Time.T): INTEGER =
  BEGIN
    RETURN ROUND (t);
  END Secs;

PROCEDURE Moment (s: INTEGER): Time.T =
  BEGIN
    RETURN FLOAT (s, LONGREAL);
  END Moment;

(* "t" with "name", in "%" signs, replaced by "by". *)
PROCEDURE Fill (t, name, by: TEXT): TEXT =
  VAR key := "%" & name & "%";  i := Text.FindChar (t, '%');
  BEGIN
    WHILE i >= 0 DO
      IF Text.Equal (Text.Sub (t, i, Text.Length (key)), key) THEN
        RETURN Text.Sub (t, 0, i) & by & Text.Sub (t, i + Text.Length (key));
      END;
      i := Text.FindChar (t, '%', i + 1);
    END;
    RETURN t;
  END Fill;

PROCEDURE Fail (f: SQL.Failure) RAISES {Error} =
  BEGIN
    RAISE Error (f.text);
  END Fail;

(* Executes "sql" with the integer parameters "a", "b", "c" (as far as it
   has them): the number of rows changed. *)
PROCEDURE ExecInts (db: SQL.T; sql: TEXT; a, b, c := 0; n := 0): INTEGER
  RAISES {SQL.Error, Thread.Alerted} =
  VAR s := db.prepare (sql);
  BEGIN
    TRY
      IF n >= 1 THEN s.bindInt (1, a) END;
      IF n >= 2 THEN s.bindInt (2, b) END;
      IF n >= 3 THEN s.bindInt (3, c) END;
      RETURN s.exec ();
    FINALLY
      s.close ();
    END;
  END ExecInts;

(* The integer in the first column of the first row of "sql" with the
   integer parameters, or "default" if there is no row. *)
PROCEDURE QueryInt (db: SQL.T; sql: TEXT; default: INTEGER; a, b := 0; n := 0): INTEGER
  RAISES {SQL.Error, Thread.Alerted} =
  VAR s := db.prepare (sql);  r: SQL.Rows;
  BEGIN
    TRY
      IF n >= 1 THEN s.bindInt (1, a) END;
      IF n >= 2 THEN s.bindInt (2, b) END;
      r := s.query ();
      IF r.next () AND NOT r.isNull (1) THEN RETURN r.int (1) END;
      RETURN default;
    FINALLY
      s.close ();
    END;
  END QueryInt;

PROCEDURE ReadFeed (r: SQL.Rows; VAR f: FeedRow) RAISES {SQL.Error} =
  BEGIN
    f.id := r.int (1);
    f.url := r.text (2);
    f.title := r.text (3);
    f.site := r.text (4);
    f.etag := r.text (5);
    f.lastModified := r.text (6);
    f.lastFetch := Moment (r.int (7));
    f.nextFetch := Moment (r.int (8));
    f.errors := r.int (9);
    f.lastError := r.text (10);
    f.entries := 0;
    f.unread := 0;
  END ReadFeed;

PROCEDURE ReadEntry (r: SQL.Rows; VAR e: EntryRow) RAISES {SQL.Error} =
  BEGIN
    e.id := r.int (1);
    e.feed := r.int (2);
    e.feedTitle := r.text (3);
    e.title := r.text (4);
    e.link := r.text (5);
    e.author := r.text (6);
    e.published := Moment (r.int (7));
    e.dated := r.bool (8);
    e.excerpt := r.text (9);
    e.read := r.bool (10);
    e.starred := r.bool (11);
  END ReadEntry;

(* Opening *)

PROCEDURE Open (path: TEXT): T RAISES {Error, Thread.Alerted} =
  VAR t := NEW (T, mu := NEW (MUTEX));  r: SQL.Rows;  version: INTEGER;  done := FALSE;
  BEGIN
    TRY
      t.db := SQL.Connect ("jdbc:sqlite:" & path);
      EVAL t.db.exec ("PRAGMA foreign_keys = ON");
      EVAL t.db.exec ("PRAGMA busy_timeout = 5000");
      r := t.db.query ("PRAGMA journal_mode = WAL");
      r.close ();
      version := 0;             (* a new database, without the table meta *)
      IF QueryInt (t.db, "SELECT count (*) FROM sqlite_master WHERE type = 'table' AND name = 'meta'", 0) = 1 THEN
        version := QueryInt (t.db, "SELECT CAST (value AS INTEGER) FROM meta WHERE key = 'schema'", 0);
      END;
      IF version > SchemaVersion THEN
        t.db.close ();
        RAISE Error (path & " was made by a newer version of the feed reader");
      ELSIF version < SchemaVersion THEN
        (* one transaction, so that a failure leaves the database as it was *)
        t.db.setAutoCommit (FALSE);
        TRY
          IF version = 0 THEN
            FOR i := FIRST (Schema) TO LAST (Schema) DO EVAL t.db.exec (Schema[i]) END;
            EVAL t.db.exec ("INSERT INTO meta (key, value) VALUES ('schema', '1')");
            version := 1;
          END;
          FOR v := version + 1 TO SchemaVersion DO EVAL t.db.exec (Migrations[v]) END;
          EVAL t.db.exec ("UPDATE meta SET value = '" & Fmt.Int (SchemaVersion) & "' WHERE key = 'schema'");
          t.db.commit ();
          done := TRUE;
        FINALLY
          EndTransaction (t.db, done);
        END;
      END;
    EXCEPT
    | SQL.Error (f) =>
        IF t.db # NIL THEN t.db.close () END;
        IF f.kind = SQL.Kind.NoDriver THEN
          RAISE Error ("the driver of SQLite is missing: run fetch-driver.sh, then build again");
        END;
        RAISE Error (path & ": " & f.text);
    END;
    RETURN t;
  END Open;

PROCEDURE Close (t: T) =
  BEGIN
    LOCK t.mu DO t.db.close () END;
  END Close;

(* Users *)

PROCEDURE UserCount (t: T): INTEGER RAISES {Error, Thread.Alerted} =
  BEGIN
    LOCK t.mu DO
      TRY
        RETURN QueryInt (t.db, "SELECT count (*) FROM user", 0);
      EXCEPT
      | SQL.Error (f) => Fail (f); RETURN 0;
      END;
    END;
  END UserCount;

PROCEDURE AddUser (t: T; name, salt, hash: TEXT; iterations: INTEGER): INTEGER RAISES {Error, Thread.Alerted} =
  VAR s: SQL.Stmt;  r: SQL.Rows;
  BEGIN
    LOCK t.mu DO
      TRY
        s := t.db.prepare ("INSERT INTO user (name, salt, hash, iterations, created) VALUES (?, ?, ?, ?, ?)",
                           keys := TRUE);
        TRY
          s.bindText (1, name);
          s.bindBytes (2, salt);
          s.bindBytes (3, hash);
          s.bindInt (4, iterations);
          s.bindInt (5, Secs (Time.Now ()));
          EVAL s.exec ();
          r := s.keys ();
          EVAL r.next ();
          RETURN r.int (1);
        FINALLY
          s.close ();
        END;
      EXCEPT
      | SQL.Error (f) =>
          IF f.kind = SQL.Kind.Constraint THEN RAISE Error ("there is a user " & name) END;
          Fail (f);
          RETURN -1;
      END;
    END;
  END AddUser;

PROCEDURE User (t: T; name: TEXT; VAR id: INTEGER; VAR salt, hash: TEXT; VAR iterations: INTEGER): BOOLEAN
  RAISES {Error, Thread.Alerted} =
  VAR s: SQL.Stmt;  r: SQL.Rows;
  BEGIN
    LOCK t.mu DO
      TRY
        s := t.db.prepare ("SELECT id, salt, hash, iterations FROM user WHERE name = ?");
        TRY
          s.bindText (1, name);
          r := s.query ();
          IF NOT r.next () THEN RETURN FALSE END;
          id := r.int (1);
          salt := r.bytes (2);
          hash := r.bytes (3);
          iterations := r.int (4);
          RETURN TRUE;
        FINALLY
          s.close ();
        END;
      EXCEPT
      | SQL.Error (f) => Fail (f); RETURN FALSE;
      END;
    END;
  END User;

PROCEDURE UserName (t: T; id: INTEGER): TEXT RAISES {Error, Thread.Alerted} =
  VAR s: SQL.Stmt;  r: SQL.Rows;
  BEGIN
    LOCK t.mu DO
      TRY
        s := t.db.prepare ("SELECT name FROM user WHERE id = ?");
        TRY
          s.bindInt (1, id);
          r := s.query ();
          IF r.next () THEN RETURN r.text (1) END;
          RETURN NIL;
        FINALLY
          s.close ();
        END;
      EXCEPT
      | SQL.Error (f) => Fail (f); RETURN NIL;
      END;
    END;
  END UserName;

PROCEDURE FirstUser (t: T): INTEGER RAISES {Error, Thread.Alerted} =
  BEGIN
    LOCK t.mu DO
      TRY
        RETURN QueryInt (t.db, "SELECT min (id) FROM user", -1);
      EXCEPT
      | SQL.Error (f) => Fail (f); RETURN -1;
      END;
    END;
  END FirstUser;

PROCEDURE SetPassword (t: T; id: INTEGER; salt, hash: TEXT; iterations: INTEGER) RAISES {Error, Thread.Alerted} =
  VAR s: SQL.Stmt;
  BEGIN
    LOCK t.mu DO
      TRY
        s := t.db.prepare ("UPDATE user SET salt = ?, hash = ?, iterations = ? WHERE id = ?");
        TRY
          s.bindBytes (1, salt);
          s.bindBytes (2, hash);
          s.bindInt (3, iterations);
          s.bindInt (4, id);
          EVAL s.exec ();
        FINALLY
          s.close ();
        END;
        EVAL ExecInts (t.db, "DELETE FROM session WHERE user_id = ?", id, n := 1);
      EXCEPT
      | SQL.Error (f) => Fail (f);
      END;
    END;
  END SetPassword;

PROCEDURE SetApiToken (t: T; id: INTEGER; hash: TEXT) RAISES {Error, Thread.Alerted} =
  VAR s: SQL.Stmt;
  BEGIN
    LOCK t.mu DO
      TRY
        s := t.db.prepare ("UPDATE user SET api_token = ? WHERE id = ?");
        TRY
          s.bindBytes (1, hash);
          s.bindInt (2, id);
          EVAL s.exec ();
        FINALLY
          s.close ();
        END;
      EXCEPT
      | SQL.Error (f) => Fail (f);
      END;
    END;
  END SetApiToken;

PROCEDURE ShowImages (t: T; id: INTEGER): BOOLEAN RAISES {Error, Thread.Alerted} =
  BEGIN
    LOCK t.mu DO
      TRY
        RETURN QueryInt (t.db, "SELECT show_images FROM user WHERE id = ?", 1, id, n := 1) # 0;
      EXCEPT
      | SQL.Error (f) => Fail (f); RETURN TRUE;
      END;
    END;
  END ShowImages;

PROCEDURE SetShowImages (t: T; id: INTEGER; on: BOOLEAN) RAISES {Error, Thread.Alerted} =
  BEGIN
    LOCK t.mu DO
      TRY
        EVAL ExecInts (t.db, "UPDATE user SET show_images = ? WHERE id = ?", ORD (on), id, n := 2);
      EXCEPT
      | SQL.Error (f) => Fail (f);
      END;
    END;
  END SetShowImages;

PROCEDURE UserByApiToken (t: T; hash: TEXT): INTEGER RAISES {Error, Thread.Alerted} =
  VAR s: SQL.Stmt;  r: SQL.Rows;
  BEGIN
    LOCK t.mu DO
      TRY
        s := t.db.prepare ("SELECT id FROM user WHERE api_token = ?");
        TRY
          s.bindBytes (1, hash);
          r := s.query ();
          IF r.next () THEN RETURN r.int (1) END;
          RETURN -1;
        FINALLY
          s.close ();
        END;
      EXCEPT
      | SQL.Error (f) => Fail (f); RETURN -1;
      END;
    END;
  END UserByApiToken;

(* Sessions *)

PROCEDURE AddSession (t: T; hash: TEXT; user: INTEGER; csrf: TEXT; expires: Time.T) RAISES {Error, Thread.Alerted} =
  VAR s: SQL.Stmt;
  BEGIN
    LOCK t.mu DO
      TRY
        s := t.db.prepare ("INSERT INTO session (hash, user_id, csrf, expires) VALUES (?, ?, ?, ?)");
        TRY
          s.bindBytes (1, hash);
          s.bindInt (2, user);
          s.bindText (3, csrf);
          s.bindInt (4, Secs (expires));
          EVAL s.exec ();
        FINALLY
          s.close ();
        END;
      EXCEPT
      | SQL.Error (f) => Fail (f);
      END;
    END;
  END AddSession;

PROCEDURE Session (t: T; hash: TEXT; now: Time.T; VAR user: INTEGER; VAR csrf: TEXT): BOOLEAN
  RAISES {Error, Thread.Alerted} =
  VAR s: SQL.Stmt;  r: SQL.Rows;
  BEGIN
    LOCK t.mu DO
      TRY
        s := t.db.prepare ("SELECT user_id, csrf FROM session WHERE hash = ? AND expires > ?");
        TRY
          s.bindBytes (1, hash);
          s.bindInt (2, Secs (now));
          r := s.query ();
          IF NOT r.next () THEN RETURN FALSE END;
          user := r.int (1);
          csrf := r.text (2);
          RETURN TRUE;
        FINALLY
          s.close ();
        END;
      EXCEPT
      | SQL.Error (f) => Fail (f); RETURN FALSE;
      END;
    END;
  END Session;

PROCEDURE RemoveSession (t: T; hash: TEXT) RAISES {Error, Thread.Alerted} =
  VAR s: SQL.Stmt;
  BEGIN
    LOCK t.mu DO
      TRY
        s := t.db.prepare ("DELETE FROM session WHERE hash = ?");
        TRY
          s.bindBytes (1, hash);
          EVAL s.exec ();
        FINALLY
          s.close ();
        END;
      EXCEPT
      | SQL.Error (f) => Fail (f);
      END;
    END;
  END RemoveSession;

PROCEDURE RemoveExpired (t: T; now: Time.T) RAISES {Error, Thread.Alerted} =
  BEGIN
    LOCK t.mu DO
      TRY
        EVAL ExecInts (t.db, "DELETE FROM session WHERE expires <= ?", Secs (now), n := 1);
      EXCEPT
      | SQL.Error (f) => Fail (f);
      END;
    END;
  END RemoveExpired;

(* Feeds *)

PROCEDURE Subscribe (t: T; user: INTEGER; url: TEXT; title: TEXT := NIL): INTEGER RAISES {Error, Thread.Alerted} =
  VAR s: SQL.Stmt;  r: SQL.Rows;  id: INTEGER;
  BEGIN
    LOCK t.mu DO
      TRY
        s := t.db.prepare ("INSERT INTO feed (url) VALUES (?) ON CONFLICT (url) DO NOTHING");
        TRY
          s.bindText (1, url);
          EVAL s.exec ();
        FINALLY
          s.close ();
        END;
        s := t.db.prepare ("SELECT id FROM feed WHERE url = ?");
        TRY
          s.bindText (1, url);
          r := s.query ();
          EVAL r.next ();
          id := r.int (1);
        FINALLY
          s.close ();
        END;
        s := t.db.prepare ("INSERT INTO subscription (user_id, feed_id, title, added) VALUES (?, ?, ?, ?)"
                             & " ON CONFLICT (user_id, feed_id) DO NOTHING");
        TRY
          s.bindInt (1, user);
          s.bindInt (2, id);
          s.bindText (3, title);
          s.bindInt (4, Secs (Time.Now ()));
          EVAL s.exec ();
        FINALLY
          s.close ();
        END;
        RETURN id;
      EXCEPT
      | SQL.Error (f) => Fail (f); RETURN -1;
      END;
    END;
  END Subscribe;

PROCEDURE Unsubscribe (t: T; user, feed: INTEGER) RAISES {Error, Thread.Alerted} =
  VAR done := FALSE;
  BEGIN
    LOCK t.mu DO
      TRY
        t.db.setAutoCommit (FALSE);
        TRY
          EVAL ExecInts (t.db, "DELETE FROM subscription WHERE user_id = ? AND feed_id = ?", user, feed, n := 2);
          EVAL ExecInts (t.db, "DELETE FROM feed WHERE id = ?1"
                                 & " AND NOT EXISTS (SELECT 1 FROM subscription WHERE feed_id = ?1)", feed, n := 1);
          t.db.commit ();
          done := TRUE;
        FINALLY
          EndTransaction (t.db, done);
        END;
      EXCEPT
      | SQL.Error (f) => Fail (f);
      END;
    END;
  END Unsubscribe;

PROCEDURE Rename (t: T; user, feed: INTEGER; title: TEXT) RAISES {Error, Thread.Alerted} =
  VAR s: SQL.Stmt;
  BEGIN
    LOCK t.mu DO
      TRY
        s := t.db.prepare ("UPDATE subscription SET title = ? WHERE user_id = ? AND feed_id = ?");
        TRY
          s.bindText (1, title);
          s.bindInt (2, user);
          s.bindInt (3, feed);
          EVAL s.exec ();
        FINALLY
          s.close ();
        END;
      EXCEPT
      | SQL.Error (f) => Fail (f);
      END;
    END;
  END Rename;

PROCEDURE Feeds (t: T; user: INTEGER): FeedRows RAISES {Error, Thread.Alerted} =
  VAR s: SQL.Stmt;  r: SQL.Rows;  n := 0;  a := NEW (FeedRows, 16);  b: FeedRows;
  BEGIN
    LOCK t.mu DO
      TRY
        s := t.db.prepare (
               "SELECT " & Fill (FeedColumns, "title", UserTitle) & ","
                 & " (SELECT count (*) FROM entry e WHERE e.feed_id = f.id),"
                 & " (SELECT count (*) FROM entry e LEFT JOIN entry_state st"
                 & " ON st.entry_id = e.id AND st.user_id = ?1"
                 & " WHERE e.feed_id = f.id AND COALESCE (st.read, 0) = 0)"
                 & " FROM feed f JOIN subscription s ON s.feed_id = f.id AND s.user_id = ?1"
                 & " ORDER BY lower (" & UserTitle & "), f.url");
        TRY
          s.bindInt (1, user);
          r := s.query ();
          WHILE r.next () DO
            IF n = NUMBER (a^) THEN
              b := NEW (FeedRows, 2 * n);
              SUBARRAY (b^, 0, n) := a^;
              a := b;
            END;
            ReadFeed (r, a[n]);
            a[n].entries := r.int (11);
            a[n].unread := r.int (12);
            INC (n);
          END;
        FINALLY
          s.close ();
        END;
      EXCEPT
      | SQL.Error (f) => Fail (f);
      END;
    END;
    b := NEW (FeedRows, n);
    b^ := SUBARRAY (a^, 0, n);
    RETURN b;
  END Feeds;

PROCEDURE GetFeed (t: T; id: INTEGER; VAR row: FeedRow): BOOLEAN RAISES {Error, Thread.Alerted} =
  VAR s: SQL.Stmt;  r: SQL.Rows;
  BEGIN
    LOCK t.mu DO
      TRY
        s := t.db.prepare ("SELECT " & Fill (FeedColumns, "title", "f.title") & " FROM feed f WHERE f.id = ?");
        TRY
          s.bindInt (1, id);
          r := s.query ();
          IF NOT r.next () THEN RETURN FALSE END;
          ReadFeed (r, row);
          RETURN TRUE;
        FINALLY
          s.close ();
        END;
      EXCEPT
      | SQL.Error (f) => Fail (f); RETURN FALSE;
      END;
    END;
  END GetFeed;

PROCEDURE Due (t: T; now: Time.T; all := FALSE): FeedRows RAISES {Error, Thread.Alerted} =
  VAR s: SQL.Stmt;  r: SQL.Rows;  n := 0;  a := NEW (FeedRows, 16);  b: FeedRows;  limit := Secs (now);
  BEGIN
    IF all THEN limit := LAST (INTEGER) END;
    LOCK t.mu DO
      TRY
        s := t.db.prepare ("SELECT " & Fill (FeedColumns, "title", "f.title") & " FROM feed f"
                             & " WHERE f.next_fetch <= ?"
                             & " AND EXISTS (SELECT 1 FROM subscription s WHERE s.feed_id = f.id)"
                             & " ORDER BY f.next_fetch, f.id");
        TRY
          s.bindInt (1, limit);
          r := s.query ();
          WHILE r.next () DO
            IF n = NUMBER (a^) THEN
              b := NEW (FeedRows, 2 * n);
              SUBARRAY (b^, 0, n) := a^;
              a := b;
            END;
            ReadFeed (r, a[n]);
            INC (n);
          END;
        FINALLY
          s.close ();
        END;
      EXCEPT
      | SQL.Error (f) => Fail (f);
      END;
    END;
    b := NEW (FeedRows, n);
    b^ := SUBARRAY (a^, 0, n);
    RETURN b;
  END Due;

(* What identifies an entry within its feed: its id, else a hash of its
   link and title. *)
PROCEDURE Key (e: Feed.Entry): TEXT =
  <*FATAL Crypto.Error*>
  VAR link := e.link;
  BEGIN
    IF e.id # NIL AND NOT Text.Empty (e.id) THEN RETURN e.id END;
    IF link = NIL THEN link := "" END;
    RETURN "sha256:" & Hex.Encode (Digest.Of (Digest.Algorithm.SHA256, Utf8.Encode (link & "\n" & e.title)));
  END Key;

PROCEDURE Fetched (t: T; feed: INTEGER; f: Feed.T; etag, lastModified: TEXT; now, next: Time.T): INTEGER
  RAISES {Error, Thread.Alerted} =
  VAR
    s, insert, update: SQL.Stmt := NIL;
    new := 0;
    e: Feed.Entry;
    key, body: TEXT;
    published: Time.T;
    done := FALSE;
  BEGIN
    LOCK t.mu DO
      TRY
        t.db.setAutoCommit (FALSE);
        TRY
          s := t.db.prepare ("UPDATE feed SET title = CASE WHEN ?1 <> '' THEN ?1 ELSE title END,"
                               & " site = COALESCE (?2, site), etag = ?3, last_modified = ?4, last_fetch = ?5,"
                               & " next_fetch = ?6, errors = 0, last_error = NULL WHERE id = ?7");
          s.bindText (1, f.title);
          s.bindText (2, f.link);
          s.bindText (3, etag);
          s.bindText (4, lastModified);
          s.bindInt (5, Secs (now));
          s.bindInt (6, Secs (next));
          s.bindInt (7, feed);
          EVAL s.exec ();
          insert := t.db.prepare (
                      "INSERT INTO entry (feed_id, key, title, link, author, published, dated, summary, content,"
                        & " base, text, fetched) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                        & " ON CONFLICT (feed_id, key) DO NOTHING");
          update := t.db.prepare (
                      "UPDATE entry SET title = ?3, link = ?4, author = ?5, summary = ?6, content = ?7, base = ?8,"
                        & " text = ?9 WHERE feed_id = ?1 AND key = ?2"
                        & " AND (title IS NOT ?3 OR link IS NOT ?4 OR summary IS NOT ?6 OR content IS NOT ?7)");
          (* the last first, so that the entries without a date keep the order of the feed *)
          FOR i := LAST (f.entries^) TO 0 BY -1 DO
            e := f.entries[i];
            key := Key (e);
            body := e.content;
            IF Text.Empty (body) THEN body := e.summary END;
            body := HtmlText.ToText (body);
            published := now;
            IF e.dated AND e.date < now THEN published := e.date END;
            insert.bindInt (1, feed);
            insert.bindText (2, key);
            insert.bindText (3, e.title);
            insert.bindText (4, e.link);
            insert.bindText (5, e.author);
            insert.bindInt (6, Secs (published));
            insert.bindBool (7, e.dated);
            insert.bindText (8, e.summary);
            insert.bindText (9, e.content);
            insert.bindText (10, e.base);
            insert.bindText (11, body);
            insert.bindInt (12, Secs (now));
            IF insert.exec () = 1 THEN
              INC (new);
            ELSE
              update.bindInt (1, feed);
              update.bindText (2, key);
              update.bindText (3, e.title);
              update.bindText (4, e.link);
              update.bindText (5, e.author);
              update.bindText (6, e.summary);
              update.bindText (7, e.content);
              update.bindText (8, e.base);
              update.bindText (9, body);
              EVAL update.exec ();
            END;
          END;
          t.db.commit ();
          done := TRUE;
        FINALLY
          IF s # NIL THEN s.close () END;
          IF insert # NIL THEN insert.close () END;
          IF update # NIL THEN update.close () END;
          EndTransaction (t.db, done);
        END;
      EXCEPT
      | SQL.Error (x) => Fail (x);
      END;
    END;
    RETURN new;
  END Fetched;

(* After a transaction: rolls it back unless it was "done", and goes back
   to a transaction for each statement. *)
PROCEDURE EndTransaction (db: SQL.T; done: BOOLEAN) RAISES {SQL.Error, Thread.Alerted} =
  BEGIN
    IF NOT done THEN
      TRY db.rollback () EXCEPT SQL.Error => (* the connection is gone *) END;
    END;
    db.setAutoCommit (TRUE);
  END EndTransaction;

PROCEDURE Unchanged (t: T; feed: INTEGER; now, next: Time.T) RAISES {Error, Thread.Alerted} =
  BEGIN
    LOCK t.mu DO
      TRY
        EVAL ExecInts (t.db, "UPDATE feed SET last_fetch = ?, next_fetch = ?, errors = 0, last_error = NULL"
                               & " WHERE id = ?", Secs (now), Secs (next), feed, n := 3);
      EXCEPT
      | SQL.Error (f) => Fail (f);
      END;
    END;
  END Unchanged;

PROCEDURE Failed (t: T; feed: INTEGER; message: TEXT; now, next: Time.T) RAISES {Error, Thread.Alerted} =
  VAR s: SQL.Stmt;
  BEGIN
    LOCK t.mu DO
      TRY
        s := t.db.prepare ("UPDATE feed SET last_fetch = ?, next_fetch = ?, errors = errors + 1, last_error = ?"
                             & " WHERE id = ?");
        TRY
          s.bindInt (1, Secs (now));
          s.bindInt (2, Secs (next));
          s.bindText (3, message);
          s.bindInt (4, feed);
          EVAL s.exec ();
        FINALLY
          s.close ();
        END;
      EXCEPT
      | SQL.Error (f) => Fail (f);
      END;
    END;
  END Failed;

(* Entries *)

PROCEDURE MatchQuery (words: TEXT): TEXT =
  VAR n, start: INTEGER;  i := 0;  r: TEXT := NIL;  w: TEXT;  c: WIDECHAR;
  BEGIN
    IF words = NIL THEN RETURN NIL END;
    n := Text.Length (words);
    WHILE i < n DO
      WHILE i < n AND Text.GetWideChar (words, i) <= W' ' DO INC (i) END;
      start := i;
      WHILE i < n AND Text.GetWideChar (words, i) > W' ' DO INC (i) END;
      IF i > start THEN
        w := "";
        FOR k := start TO i - 1 DO
          c := Text.GetWideChar (words, k);
          IF c # W'"' THEN w := w & Text.FromWideChar (c) END;
        END;
        IF NOT Text.Empty (w) THEN
          w := "\"" & w & "\"*";
          IF r = NIL THEN r := w ELSE r := r & " " & w END;
        END;
      END;
    END;
    RETURN r;
  END MatchQuery;

PROCEDURE Entries (t: T; user: INTEGER; READONLY f: Filter): EntryRows RAISES {Error, Thread.Alerted} =
  VAR
    s: SQL.Stmt;
    r: SQL.Rows;
    n := 0;
    a := NEW (EntryRows, MAX (f.limit, 1));
    b: EntryRows;
    match := MatchQuery (f.query);
    sql: TEXT;
  BEGIN
    IF match = NIL THEN
      sql := "SELECT " & Fill (EntryColumns, "excerpt", "substr (e.text, 1, " & Fmt.Int (ExcerptChars) & ")")
               & EntryJoins;
    ELSE
      sql := "SELECT " & Fill (EntryColumns, "excerpt", "snippet (entry_fts, -1, char (1), char (2), '...', 40)")
               & EntryJoins & " JOIN entry_fts ON entry_fts.rowid = e.id AND entry_fts MATCH ?5";
    END;
    sql := sql & " WHERE (?2 = 0 OR e.feed_id = ?2)";
    IF f.unread THEN sql := sql & " AND COALESCE (st.read, 0) = 0" END;
    IF f.starred THEN sql := sql & " AND COALESCE (st.starred, 0) = 1" END;
    sql := sql & " ORDER BY e.published DESC, e.id DESC LIMIT ?3 OFFSET ?4";
    LOCK t.mu DO
      TRY
        s := t.db.prepare (sql);
        TRY
          s.bindInt (1, user);
          s.bindInt (2, f.feed);
          s.bindInt (3, f.limit);
          s.bindInt (4, f.offset);
          IF match # NIL THEN s.bindText (5, match) END;
          r := s.query ();
          WHILE n < f.limit AND r.next () DO
            ReadEntry (r, a[n]);
            INC (n);
          END;
        FINALLY
          s.close ();
        END;
      EXCEPT
      | SQL.Error (x) => Fail (x);
      END;
    END;
    b := NEW (EntryRows, n);
    b^ := SUBARRAY (a^, 0, n);
    RETURN b;
  END Entries;

PROCEDURE GetEntry (t: T; user, id: INTEGER; VAR c: Content): BOOLEAN RAISES {Error, Thread.Alerted} =
  VAR s: SQL.Stmt;  r: SQL.Rows;
  BEGIN
    LOCK t.mu DO
      TRY
        s := t.db.prepare ("SELECT " & Fill (EntryColumns, "excerpt", "e.text")
                             & ", e.summary, e.content, e.base" & EntryJoins & " WHERE e.id = ?2");
        TRY
          s.bindInt (1, user);
          s.bindInt (2, id);
          r := s.query ();
          IF NOT r.next () THEN RETURN FALSE END;
          ReadEntry (r, c.row);
          c.summary := r.text (12);
          c.content := r.text (13);
          c.base := r.text (14);
          RETURN TRUE;
        FINALLY
          s.close ();
        END;
      EXCEPT
      | SQL.Error (x) => Fail (x); RETURN FALSE;
      END;
    END;
  END GetEntry;

(* Sets the column "read" or "starred" of the state of an entry that
   "user" can see. *)
PROCEDURE SetState (t: T; column: TEXT; user, entry: INTEGER; on: BOOLEAN) RAISES {Error, Thread.Alerted} =
  BEGIN
    LOCK t.mu DO
      TRY
        EVAL ExecInts (t.db, "INSERT INTO entry_state (user_id, entry_id, " & column & ")"
                               & " SELECT ?1, e.id, ?3 FROM entry e"
                               & " JOIN subscription s ON s.feed_id = e.feed_id AND s.user_id = ?1"
                               & " WHERE e.id = ?2"
                               & " ON CONFLICT (user_id, entry_id) DO UPDATE SET " & column & " = excluded."
                               & column, user, entry, ORD (on), n := 3);
      EXCEPT
      | SQL.Error (f) => Fail (f);
      END;
    END;
  END SetState;

PROCEDURE SetRead (t: T; user, entry: INTEGER; on: BOOLEAN) RAISES {Error, Thread.Alerted} =
  BEGIN
    SetState (t, "read", user, entry, on);
  END SetRead;

PROCEDURE SetStarred (t: T; user, entry: INTEGER; on: BOOLEAN) RAISES {Error, Thread.Alerted} =
  BEGIN
    SetState (t, "starred", user, entry, on);
  END SetStarred;

PROCEDURE MarkAllRead (t: T; user, feed: INTEGER) RAISES {Error, Thread.Alerted} =
  BEGIN
    LOCK t.mu DO
      TRY
        EVAL ExecInts (t.db, "INSERT INTO entry_state (user_id, entry_id, read)"
                               & " SELECT ?1, e.id, 1 FROM entry e"
                               & " JOIN subscription s ON s.feed_id = e.feed_id AND s.user_id = ?1"
                               & " WHERE ?2 = 0 OR e.feed_id = ?2"
                               & " ON CONFLICT (user_id, entry_id) DO UPDATE SET read = 1", user, feed, n := 2);
      EXCEPT
      | SQL.Error (f) => Fail (f);
      END;
    END;
  END MarkAllRead;

BEGIN
END Store.
