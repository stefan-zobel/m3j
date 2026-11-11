(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* What a news feed says, in RSS 2.0 (and its forerunners 0.91 and 0.92),
   RSS 1.0 (RDF) or Atom 1.0, read into one form.

   Text that a feed marks as HTML stays HTML: "summary" and "content".
   Everything else is plain text, with tags and character references of
   HTML taken out where feeds are known to put them, as in titles. Links
   are absolute and their scheme is "http" or "https"; a link that is not
   is dropped, so that a feed cannot smuggle "javascript:" into a page. *)

INTERFACE Feed;

IMPORT Rd, Thread, Time;

TYPE
  Format = {RSS, RDF, Atom};

  Entry = REF RECORD
    id: TEXT := NIL;            (* the guid or atom:id, NIL if there is none *)
    title := "";
    link: TEXT := NIL;
    author := "";
    date: Time.T := 0.0D0;      (* when it was published, else updated *)
    dated := FALSE;             (* whether "date" holds *)
    dateText: TEXT := NIL;      (* the date as the feed has it, NIL if none *)
    summary := "";              (* HTML *)
    content := "";              (* HTML, the full text if the feed has it *)
    base: TEXT := NIL;          (* for the relative references in the HTML *)
  END;

  Entries = REF ARRAY OF Entry;

  T = REF RECORD
    format: Format;
    title := "";
    link: TEXT := NIL;          (* the web site *)
    description := "";
    entries: Entries;
  END;

EXCEPTION Error (TEXT);         (* not a feed, or broken XML *)

PROCEDURE Parse (rd: Rd.T; url: TEXT): T RAISES {Error, Rd.Failure, Thread.Alerted};
(* The feed in the bytes of "rd", in the encoding that its XML declares.
   "url" is where it came from, the base of relative links. *)

CONST FormatNames = ARRAY Format OF TEXT {"RSS", "RDF", "Atom"};

END Feed.
