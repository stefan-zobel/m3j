(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The feeds that a web page names, so that a user may give the address of
   a site rather than that of its feed. *)

INTERFACE Discover;

TYPE URLs = REF ARRAY OF TEXT;

PROCEDURE Feeds (html, base: TEXT): URLs;
(* The absolute URLs of the "link" elements of "html" with the "rel"
   "alternate" and the "type" of RSS or Atom, in their order, each once.
   "base" is the URL of the page. *)

END Discover.
