(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Lists of subscriptions in OPML, the format in which feed readers trade
   them: an outline whose items name feeds by their "xmlUrl". *)

INTERFACE Opml;

IMPORT Rd, Thread, Wr;

TYPE
  Outline = RECORD
    title: TEXT;                (* "" if it has none *)
    xmlUrl: TEXT;               (* the feed *)
    htmlUrl: TEXT := NIL;       (* its web site, NIL if not known *)
  END;
  Outlines = REF ARRAY OF Outline;

EXCEPTION Error (TEXT);

PROCEDURE Read (rd: Rd.T): Outlines RAISES {Error, Rd.Failure, Thread.Alerted};
(* The feeds of the list in "rd", also those in nested outlines, which
   group feeds; each "xmlUrl" once, as an absolute "http" or "https" URL. *)

PROCEDURE Write (wr: Wr.T; title: TEXT; READONLY feeds: ARRAY OF Outline) RAISES {Wr.Failure, Thread.Alerted};
(* The list of "feeds" in OPML 2.0, in UTF-8. *)

END Opml.
