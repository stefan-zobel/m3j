(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The plain text of a piece of HTML, for lists, titles and search. This is
   no parser of HTML: it takes out what looks like a tag, and the content
   of scripts, styles and comments. *)

INTERFACE HtmlText;

PROCEDURE ToText (html: TEXT): TEXT;
(* The text of "html", with its character references replaced and its
   white space runs, line ends included, made single spaces. *)

PROCEDURE Excerpt (text: TEXT; max: CARDINAL): TEXT;
(* "text" if it has at most "max" characters; otherwise its beginning, cut
   at a space if there is one in its second half, and "...". *)

END HtmlText.
