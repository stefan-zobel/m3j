(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Text in HTML and XML: escaping for the content of an element and for an
   attribute value, and the character references of HTML.

|    Wr.PutText (wr, "<a href=\"" & Html.Escape (url) & "\">" & Html.Escape (title) & "</a>");

   Characters are Unicode; a character above 16_FFFF is a surrogate pair
   of "WIDECHAR"s, and stays one. *)

INTERFACE Html;

PROCEDURE Escape (t: TEXT): TEXT;
(* "t" with "&", "<", ">", the double and the single quote replaced by
   "&amp;", "&lt;", "&gt;", "&quot;" and "&#39;": safe in the content of an
   element and in an attribute value in either kind of quotes, in HTML and
   in XML. *)

PROCEDURE Unescape (t: TEXT): TEXT;
(* "t" with each character reference replaced by its character: decimal
   ("&#233;"), hexadecimal ("&#xE9;") and the 252 named references of
   HTML 4 ("&eacute;", "&nbsp;"), and "&apos;". The semicolon is required;
   a reference that is malformed or has an unknown name stays as it is. A
   number that is no character, 0 or a surrogate or above 16_10FFFF,
   becomes U+FFFD; one from 16_80 to 16_9F stands for the character of
   windows-1252 there, as in HTML 5. *)

PROCEDURE Lookup (name: TEXT): INTEGER;
(* The code of the named reference "name" ("eacute" without "&" and ";"),
   -1 if there is no such reference. *)

END Html.
