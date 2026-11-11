(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The JSON API, for programs: each request carries a token of its user in
   "Authorization: Bearer TOKEN", which the page of the feeds makes.

     GET    /api/feeds           the feeds, with the number of their entries
     POST   /api/feeds           {"url": URL, "title": TITLE} subscribes
     DELETE /api/feeds/ID        unsubscribes
     GET    /api/entries         the newest entries; the query may have
                                 feed=ID, unread=1, starred=1, q=WORDS,
                                 n=COUNT (at most 200) and offset=N
     GET    /api/entries/ID      one entry, with its HTML
     POST   /api/entries/ID      {"read": BOOL, "starred": BOOL}, either or both

   Times are in RFC 3339. An error is an object {"error": TEXT} with the
   status of the error. *)

INTERFACE Api;

IMPORT HTTPClient, HTTPServer, Rd, Store, Thread, Wr;

PROCEDURE Handle (s: Store.T; c: HTTPClient.T; req: HTTPServer.Request; resp: HTTPServer.Response)
  RAISES {Rd.Failure, Wr.Failure, Thread.Alerted};
(* Answers a request whose path begins with "/api/"; "c" fetches feeds to
   subscribe to. *)

END Api.
