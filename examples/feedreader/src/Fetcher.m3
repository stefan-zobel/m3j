(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Fetcher;

IMPORT Discover, Events, Feed, Fetch, Fmt, HTTP, HTTPClient, Log, Rd, Store, TextRd, Thread, Time;

CONST
  Look = 20;                    (* half seconds between two looks for due feeds *)

TYPE
  Node = REF RECORD
    row: Store.FeedRow;
    next: Node := NIL;
  END;

REVEAL
  T = MUTEX BRANDED OBJECT
    store: Store.T;
    client: HTTPClient.T;
    reporter: Reporter;
    head, tail: Node := NIL;            (* the queue *)
    pending: REF ARRAY OF INTEGER;      (* the feeds queued or being fetched *)
    npending := 0;
    work: Thread.Condition;             (* something was queued *)
    kicked := FALSE;
    drain := FALSE;                     (* the workers end when the queue is empty *)
    threads: REF ARRAY OF Thread.T;
    planner: Thread.T := NIL;
  END;

TYPE
  Worker = Thread.Closure OBJECT t: T OVERRIDES apply := Work END;
  Planner = Thread.Closure OBJECT t: T OVERRIDES apply := Plan END;

(* The queue; all with "t" locked *)

PROCEDURE Pending (t: T; id: INTEGER): BOOLEAN =
  BEGIN
    FOR i := 0 TO t.npending - 1 DO
      IF t.pending[i] = id THEN RETURN TRUE END;
    END;
    RETURN FALSE;
  END Pending;

PROCEDURE Queue (t: T; READONLY row: Store.FeedRow) =
  VAR n := NEW (Node, row := row);  b: REF ARRAY OF INTEGER;
  BEGIN
    IF Pending (t, row.id) THEN RETURN END;
    IF t.npending = NUMBER (t.pending^) THEN
      b := NEW (REF ARRAY OF INTEGER, 2 * t.npending);
      SUBARRAY (b^, 0, t.npending) := t.pending^;
      t.pending := b;
    END;
    t.pending[t.npending] := row.id;
    INC (t.npending);
    IF t.tail = NIL THEN t.head := n ELSE t.tail.next := n END;
    t.tail := n;
    Thread.Signal (t.work);
  END Queue;

PROCEDURE Done (t: T; id: INTEGER) =
  BEGIN
    FOR i := 0 TO t.npending - 1 DO
      IF t.pending[i] = id THEN
        t.pending[i] := t.pending[t.npending - 1];
        DEC (t.npending);
        RETURN;
      END;
    END;
  END Done;

(* The next feed to fetch, waiting for one; NIL when the queue is empty and
   the workers are to end then. *)
PROCEDURE Take (t: T): Node RAISES {Thread.Alerted} =
  VAR n: Node;
  BEGIN
    LOCK t DO
      WHILE t.head = NIL DO
        IF t.drain THEN RETURN NIL END;
        Thread.AlertWait (t, t.work);
      END;
      n := t.head;
      t.head := n.next;
      IF t.head = NIL THEN t.tail := NIL END;
      RETURN n;
    END;
  END Take;

(* The threads *)

PROCEDURE Work (w: Worker): REFANY =
  VAR t := w.t;  n: Node;  r: Report;
  BEGIN
    TRY
      LOOP
        n := Take (t);
        IF n = NIL THEN EXIT END;
        TRY
          r := One (t.store, t.client, n.row);
          IF t.reporter # NIL THEN t.reporter.report (r) END;
        FINALLY
          LOCK t DO Done (t, n.row.id) END;
        END;
      END;
    EXCEPT
    | Thread.Alerted => (* stopped *)
    END;
    RETURN NIL;
  END Work;

PROCEDURE Plan (p: Planner): REFANY =
  VAR t := p.t;  rows: Store.FeedRows;
  BEGIN
    TRY
      LOOP
        TRY
          rows := t.store.due (Time.Now ());
          LOCK t DO
            FOR i := 0 TO LAST (rows^) DO Queue (t, rows[i]) END;
          END;
        EXCEPT
        | Store.Error (m) => Log.Put ("cannot find the feeds that are due: " & m);
        END;
        FOR i := 1 TO Look DO
          LOCK t DO
            IF t.kicked THEN t.kicked := FALSE; EXIT END;
          END;
          Thread.AlertPause (0.5D0);
        END;
      END;
    EXCEPT
    | Thread.Alerted => (* stopped *)
    END;
    RETURN NIL;
  END Plan;

PROCEDURE New (s: Store.T; workers: CARDINAL; r: Reporter; drain: BOOLEAN): T =
  VAR t := NEW (T, store := s, client := Fetch.NewClient (), reporter := r, drain := drain,
                work := NEW (Thread.Condition), pending := NEW (REF ARRAY OF INTEGER, 16));
  BEGIN
    t.threads := NEW (REF ARRAY OF Thread.T, MAX (workers, 1));
    RETURN t;
  END New;

PROCEDURE StartWorkers (t: T) =
  BEGIN
    FOR i := 0 TO LAST (t.threads^) DO t.threads[i] := Thread.Fork (NEW (Worker, t := t)) END;
  END StartWorkers;

PROCEDURE Start (s: Store.T; workers: CARDINAL := 4; r: Reporter := NIL): T =
  VAR t := New (s, workers, r, drain := FALSE);
  BEGIN
    StartWorkers (t);
    t.planner := Thread.Fork (NEW (Planner, t := t));
    RETURN t;
  END Start;

PROCEDURE Kick (t: T) =
  BEGIN
    LOCK t DO t.kicked := TRUE END;
  END Kick;

PROCEDURE Stop (t: T) =
  BEGIN
    IF t.planner # NIL THEN Thread.Alert (t.planner); EVAL Thread.Join (t.planner) END;
    FOR i := 0 TO LAST (t.threads^) DO Thread.Alert (t.threads[i]) END;
    FOR i := 0 TO LAST (t.threads^) DO EVAL Thread.Join (t.threads[i]) END;
    HTTPClient.Close (t.client);
  END Stop;

PROCEDURE Sync (s: Store.T; all: BOOLEAN; workers: CARDINAL := 4; r: Reporter := NIL)
  RAISES {Store.Error, Thread.Alerted} =
  VAR rows := s.due (Time.Now (), all);  t := New (s, MIN (workers, MAX (NUMBER (rows^), 1)), r, drain := TRUE);
  BEGIN
    LOCK t DO
      FOR i := 0 TO LAST (rows^) DO Queue (t, rows[i]) END;
    END;
    StartWorkers (t);
    FOR i := 0 TO LAST (t.threads^) DO EVAL Thread.Join (t.threads[i]) END;
    HTTPClient.Close (t.client);
  END Sync;

(* Fetching one feed *)

PROCEDURE Backoff (failures: INTEGER): Time.T =
  VAR r := MinRetry;
  BEGIN
    FOR i := 2 TO failures DO
      r := r * 2.0D0;
      IF r >= MaxRetry THEN RETURN MaxRetry END;
    END;
    RETURN r;
  END Backoff;

PROCEDURE Status (status: INTEGER): TEXT =
  BEGIN
    RETURN "HTTP status " & Fmt.Int (status) & " " & HTTP.Reason (status);
  END Status;

PROCEDURE One (s: Store.T; c: HTTPClient.T; READONLY f: Store.FeedRow): Report RAISES {Thread.Alerted} =
  VAR rep: Report;  start := Time.Now ();  r: Fetch.Result;  feed: Feed.T;
  BEGIN
    rep.feed := f.id;
    rep.url := f.url;
    rep.new := 0;
    rep.message := NIL;
    TRY
      TRY
        r := Fetch.Get (c, f.url, f.etag, f.lastModified, timeout);
        IF r.status = 304 THEN
          s.unchanged (f.id, start, start + Interval);
          rep.outcome := Outcome.Unchanged;
        ELSIF r.body = NIL THEN
          rep.message := Status (r.status);
        ELSE
          feed := Feed.Parse (TextRd.New (r.body), f.url);
          rep.new := s.fetched (f.id, feed, r.etag, r.lastModified, start, start + Interval);
          rep.outcome := Outcome.Fetched;
          IF rep.new > 0 THEN Events.Signal () END;
        END;
      EXCEPT
      | Fetch.Error (m) => rep.message := m;
      | Feed.Error (m) => rep.message := m;
      | Rd.Failure => rep.message := "cannot read the feed";
      END;
      IF rep.message # NIL THEN
        rep.outcome := Outcome.Failed;
        s.failed (f.id, rep.message, start, start + Backoff (f.errors + 1));
      END;
    EXCEPT
    | Store.Error (m) =>
        rep.outcome := Outcome.Failed;
        rep.message := "database: " & m;
        Log.Put (f.url & ": " & rep.message);
    END;
    rep.seconds := Time.Now () - start;
    RETURN rep;
  END One;

(* The feed of the response "r" to a request of "url". *)
PROCEDURE Parse (READONLY r: Fetch.Result; url: TEXT): Feed.T RAISES {Feed.Error, Error, Thread.Alerted} =
  BEGIN
    IF r.body = NIL THEN RAISE Error (url & ": " & Status (r.status)) END;
    TRY
      RETURN Feed.Parse (TextRd.New (r.body), url);
    EXCEPT
    | Rd.Failure => RAISE Error (url & ": cannot read it");
    END;
  END Parse;

PROCEDURE Find (c: HTTPClient.T; url: TEXT; VAR feedUrl: TEXT): Feed.T RAISES {Error, Thread.Alerted} =
  VAR r: Fetch.Result;  urls: Discover.URLs;
  BEGIN
    TRY
      r := Fetch.Get (c, url, timeout := timeout);
      TRY
        feedUrl := url;
        RETURN Parse (r, url);
      EXCEPT
      | Feed.Error =>
          urls := Discover.Feeds (r.body, url);
          IF NUMBER (urls^) = 0 THEN RAISE Error (url & ": neither a feed nor a page that names one") END;
          feedUrl := urls[0];
          r := Fetch.Get (c, feedUrl, timeout := timeout);
          TRY
            RETURN Parse (r, feedUrl);
          EXCEPT
          | Feed.Error (m2) => RAISE Error (feedUrl & ": " & m2);
          END;
      END;
    EXCEPT
    | Fetch.Error (m) => RAISE Error (url & ": " & m);
    END;
  END Find;

BEGIN
END Fetcher.
