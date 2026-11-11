(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Links as feeds and web pages have them: relative, with spaces, with
   characters outside of ASCII. *)

INTERFACE Links;

PROCEDURE Resolve (base, ref: TEXT): TEXT;
(* "ref" relative to "base", as an absolute "http" or "https" URL; NIL if
   it is none, or "ref" is NIL or empty. White space around "ref" is
   ignored, and characters that a URL may not have, such as spaces and
   those outside of ASCII, are escaped in UTF-8 first. "base" may be NIL
   if "ref" is absolute. *)

PROCEDURE Web (url: TEXT): BOOLEAN;
(* Whether "url" is an absolute "http" or "https" URL with a host. *)

PROCEDURE Host (url: TEXT): TEXT;
(* The host of "url", in lower case, or "" if it has none. *)

END Links.
