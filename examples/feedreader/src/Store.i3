(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The database of the feed reader, in SQLite: users and their sessions,
   feeds and who subscribed to them, the entries of the feeds, and what
   each user has read or starred. All SQL of the program is here.

   Times are kept as whole seconds since the epoch. Entries are searched
   with the full-text index of SQLite (FTS5), which triggers keep in step
   with the table of entries.

   A "T" may be used by several threads: its calls run one after the other. *)

INTERFACE Store;

IMPORT Feed, Thread, Time;

EXCEPTION Error (TEXT);

CONST SchemaVersion = 2;       (* 2: the setting "show_images" of a user *)

TYPE
  FeedRow = RECORD
    id: INTEGER;
    url: TEXT;
    title: TEXT;                (* the user's name for it, else the feed's, else "" *)
    site: TEXT;                 (* NIL if not known *)
    etag, lastModified: TEXT;   (* the validators of the last fetch, NIL if none *)
    lastFetch: Time.T;          (* 0 if never *)
    nextFetch: Time.T;
    errors: INTEGER;            (* failures since the last fetch that worked *)
    lastError: TEXT;            (* NIL after a fetch that worked *)
    entries, unread: INTEGER;   (* for the user, from "feeds" only *)
  END;
  FeedRows = REF ARRAY OF FeedRow;

  EntryRow = RECORD
    id, feed: INTEGER;
    feedTitle: TEXT;
    title: TEXT;
    link: TEXT;                 (* NIL if none *)
    author: TEXT;
    published: Time.T;          (* when it was first seen, if "dated" is FALSE *)
    dated: BOOLEAN;
    excerpt: TEXT;              (* plain text; in a search, a snippet in which
                                   '\001' and '\002' enclose what was found *)
    read, starred: BOOLEAN;
  END;
  EntryRows = REF ARRAY OF EntryRow;

  Filter = RECORD
    feed := 0;                  (* only of this feed; 0 for all *)
    unread := FALSE;            (* only those not read *)
    starred := FALSE;           (* only those starred *)
    query: TEXT := NIL;         (* only those with all these words, at the
                                   beginning of words *)
    limit: CARDINAL := 50;
    offset: CARDINAL := 0;
  END;

  Content = RECORD
    row: EntryRow;
    summary, content: TEXT;     (* HTML *)
    base: TEXT;                 (* for relative references in it, NIL if none *)
  END;

  T <: Public;
  Public = OBJECT
  METHODS
    close ();

    (* Users. A password is kept as the salt and the hash of PBKDF2 with
       its number of iterations; an API token as its hash. *)
    userCount (): INTEGER RAISES {Error, Thread.Alerted};
    addUser (name, salt, hash: TEXT; iterations: INTEGER): INTEGER RAISES {Error, Thread.Alerted};
    (* The id of the new user; "Error" if there is one of that name. *)
    user (name: TEXT; VAR id: INTEGER; VAR salt, hash: TEXT; VAR iterations: INTEGER): BOOLEAN
      RAISES {Error, Thread.Alerted};
    userName (id: INTEGER): TEXT RAISES {Error, Thread.Alerted};  (* NIL if none *)
    firstUser (): INTEGER RAISES {Error, Thread.Alerted};        (* -1 if none *)
    setPassword (id: INTEGER; salt, hash: TEXT; iterations: INTEGER) RAISES {Error, Thread.Alerted};
    setApiToken (id: INTEGER; hash: TEXT) RAISES {Error, Thread.Alerted};
    showImages (id: INTEGER): BOOLEAN RAISES {Error, Thread.Alerted};
    setShowImages (id: INTEGER; on: BOOLEAN) RAISES {Error, Thread.Alerted};
    (* Whether the user sees the images of other servers in entries; at
       first TRUE. *)
    userByApiToken (hash: TEXT): INTEGER RAISES {Error, Thread.Alerted};  (* -1 if none *)

    (* Sessions, by the hash of their token. *)
    addSession (hash: TEXT; user: INTEGER; csrf: TEXT; expires: Time.T) RAISES {Error, Thread.Alerted};
    session (hash: TEXT; now: Time.T; VAR user: INTEGER; VAR csrf: TEXT; VAR expired: BOOLEAN): BOOLEAN
      RAISES {Error, Thread.Alerted};
    (* FALSE if there is no session of "hash" or it has expired, which
       "expired" tells. *)
    removeSession (hash: TEXT) RAISES {Error, Thread.Alerted};
    removeExpired (now: Time.T) RAISES {Error, Thread.Alerted};

    (* Feeds. A feed is fetched while someone subscribes to it. *)
    subscribe (user: INTEGER; url: TEXT; title: TEXT := NIL): INTEGER RAISES {Error, Thread.Alerted};
    (* The id of the feed of "url", which is new if there was none. *)
    unsubscribe (user, feed: INTEGER) RAISES {Error, Thread.Alerted};
    (* A feed without subscribers is removed, with its entries. *)
    rename (user, feed: INTEGER; title: TEXT) RAISES {Error, Thread.Alerted};
    feeds (user: INTEGER): FeedRows RAISES {Error, Thread.Alerted};
    (* The feeds of "user", by title. *)
    feed (id: INTEGER; VAR r: FeedRow): BOOLEAN RAISES {Error, Thread.Alerted};
    due (now: Time.T; all := FALSE): FeedRows RAISES {Error, Thread.Alerted};
    (* The feeds with subscribers that are to be fetched by "now", or all
       of them, earliest first. *)

    (* The results of fetching a feed. *)
    fetched (feed: INTEGER; f: Feed.T; etag, lastModified: TEXT; now, next: Time.T): INTEGER
      RAISES {Error, Thread.Alerted};
    (* Saves the entries of "f" that are new, and the changes of those that
       are not; returns the number of new entries. *)
    unchanged (feed: INTEGER; now, next: Time.T) RAISES {Error, Thread.Alerted};
    failed (feed: INTEGER; message: TEXT; now, next: Time.T) RAISES {Error, Thread.Alerted};

    (* Entries, of the feeds that "user" subscribes to. *)
    entries (user: INTEGER; READONLY f: Filter): EntryRows RAISES {Error, Thread.Alerted};
    (* Newest first. *)
    entry (user, id: INTEGER; VAR c: Content): BOOLEAN RAISES {Error, Thread.Alerted};
    setRead (user, entry: INTEGER; on: BOOLEAN) RAISES {Error, Thread.Alerted};
    setStarred (user, entry: INTEGER; on: BOOLEAN) RAISES {Error, Thread.Alerted};
    markAllRead (user, feed: INTEGER) RAISES {Error, Thread.Alerted};
    (* Of the feed, or of all feeds if "feed" is 0. *)
  END;

PROCEDURE Open (path: TEXT): T RAISES {Error, Thread.Alerted};
(* The database in the file "path", made if there is none, and brought up
   to "SchemaVersion" if it was made by an earlier version. *)

PROCEDURE MatchQuery (words: TEXT): TEXT;
(* The query of FTS5 for the words, which match at the beginning of words
   of an entry; NIL if there are none. *)

END Store.
