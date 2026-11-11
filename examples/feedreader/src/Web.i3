(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The web interface: pages made on the server, which work without
   scripts, and a small script that shows times in local time and new
   entries as they come.

   A user logs in with a name and a password and gets a session cookie;
   each form that changes something carries the token of the session
   against forged requests. The first visit, when there is no user yet,
   makes one.

   The HTML of an entry comes from a stranger, so it is never part of a
   page: it is shown in an "iframe" from a path of its own, in a sandbox
   in which no script runs, by the attribute of the frame and by the
   "Content-Security-Policy" of its response, which also keeps images from
   other servers out if the reader does not want them (an image can tell
   its server who read an entry when): a setting of each user, which a
   link of each entry overrides for it. *)

INTERFACE Web;

IMPORT Fetcher, HTTPServer, Store;

TYPE T <: HTTPServer.Handler;

PROCEDURE New (s: Store.T; f: Fetcher.T): T;
(* The handler of all the paths of the interface and of the API. *)

END Web.
