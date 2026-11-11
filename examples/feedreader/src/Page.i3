(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The frame of the pages of the web interface, and the pieces of HTML that
   they share. Every text from a feed or a user goes through "E", which
   escapes it; markup is written only by the program. *)

INTERFACE Page;

IMPORT TextBuf, Time;

PROCEDURE E (t: TEXT): TEXT;
(* "t" escaped for HTML, also in an attribute value; "" for NIL. *)

PROCEDURE Marked (t: TEXT): TEXT;
(* "t" escaped, with what '\001' and '\002' enclose, the words that a search
   found, in a "mark" element. *)

PROCEDURE When (t: Time.T): TEXT;
(* A "time" element for "t", shown in UTC; the script of the pages shows it
   in the time of the reader. *)

PROCEDURE Begin (title: TEXT; user, csrf, query: TEXT := NIL): TextBuf.T;
(* The beginning of a page: its head, and for a user who is logged in, the
   bar with the search, the link to the feeds and the button to log out.
   "query" is what the search field shows. *)

PROCEDURE Hidden (b: TextBuf.T; name, value: TEXT);
(* A hidden field of a form. *)

PROCEDURE End (b: TextBuf.T): TEXT;
(* The end of the page, and all of it. *)

END Page.
