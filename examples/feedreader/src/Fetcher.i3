(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Fetching the feeds that are due, with several threads: a planner looks
   for due feeds in the store and puts them in a queue, from which the
   workers take them. A feed that works is fetched again after "Interval";
   one that fails is tried again after a time that doubles with each
   failure, from "MinRetry" to "MaxRetry". *)

INTERFACE Fetcher;

IMPORT Feed, HTTPClient, Store, Thread, Time;

CONST
  Interval = 30.0D0 * 60.0D0;
  MinRetry = 15.0D0 * 60.0D0;
  MaxRetry = 24.0D0 * 60.0D0 * 60.0D0;

VAR timeout := 30.0D0;          (* seconds for the answer of a server, and for each read *)

TYPE
  Outcome = {Fetched, Unchanged, Failed};  (* "Fetched" with new entries or without *)

  Report = RECORD
    feed: INTEGER;
    url: TEXT;
    outcome: Outcome;
    new: INTEGER;               (* the number of new entries *)
    message: TEXT;              (* why it failed *)
    seconds: Time.T;            (* how long it took *)
  END;

  Reporter = OBJECT METHODS report (READONLY r: Report) END;
  (* "report" is called by the worker that fetched the feed. *)

  T <: ROOT;

EXCEPTION Error (TEXT);

PROCEDURE Start (s: Store.T; workers: CARDINAL := 4; r: Reporter := NIL): T;
(* Starts fetching feeds in the background. *)

PROCEDURE Kick (t: T);
(* Has the planner look for due feeds at once, as after a subscription. *)

PROCEDURE Stop (t: T);
(* Ends the threads of "t", and waits for them. *)

PROCEDURE Sync (s: Store.T; all: BOOLEAN; workers: CARDINAL := 4; r: Reporter := NIL) RAISES {Store.Error, Thread.Alerted};
(* Fetches the feeds that are due, or all of them, and returns when that is
   done. *)

PROCEDURE One (s: Store.T; c: HTTPClient.T; READONLY f: Store.FeedRow): Report RAISES {Thread.Alerted};
(* Fetches the feed "f" and saves what it brought, or how it failed. *)

PROCEDURE Find (c: HTTPClient.T; url: TEXT; VAR feedUrl: TEXT): Feed.T RAISES {Error, Thread.Alerted};
(* The feed at "url" or, if "url" is a web page, the first feed that it
   names; sets "feedUrl" to the URL of the feed. *)

END Fetcher.
