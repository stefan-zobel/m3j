(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The references of RFC 3986, their parts and percent escapes.

   The parts of a "T" stay as they are in the text, with their escapes. A part
   that is missing is "NIL", and that is different from one that is empty:
   "http://a?" has an empty query, "http://a" has none. Only "path" is always
   there and may be empty. An IP literal keeps its brackets in "host". A port
   that is missing or empty is -1.

   A "TEXT" stands for bytes here, one character for each byte. Characters of
   Unicode have to go through "Utf8.Encode" before they are escaped. *)

INTERFACE URI;

TYPE
  T = RECORD
    scheme, userinfo, host: TEXT := NIL;
    port := -1;
    path := "";
    query, fragment: TEXT := NIL;
  END;

  Pair = RECORD name, value: TEXT END;
  Pairs = REF ARRAY OF Pair;

  QueryMode = {Raw, Form};

(* "Form" is the coding of application/x-www-form-urlencoded: names and values
   are decoded, and "+" stands for a space. With "Raw", names and values stay
   as they are in the query, with their escapes and their "+". *)

CONST
  Unreserved = SET OF CHAR {'a' .. 'z', 'A' .. 'Z', '0' .. '9', '-', '.', '_', '~'};

PROCEDURE Parse (t: TEXT; VAR u: T): BOOLEAN;
(* Set "u" to the parts of "t" and return "TRUE", if "t" is a URI or a relative
   reference by the grammar of RFC 3986. Otherwise return "FALSE" and leave
   "u" alone. Refused are among others a backslash, a space, a control
   character, a character above 126, a malformed escape, a malformed IP
   literal and a port above 65535. *)

PROCEDURE Format (READONLY u: T): TEXT;
(* The reference that the parts of "u" make. "userinfo" and "port" count only
   if "host" is there. The parts are not checked. *)

PROCEDURE Resolve (READONLY base, ref: T): T;
(* The target of "ref" relative to "base", which should have a scheme, by
   section 5.2 of RFC 3986. *)

PROCEDURE Normalize (READONLY u: T): T;
(* "u" after the normalization by syntax of section 6.2.2 of RFC 3986: scheme
   and host in lower case, escapes of unreserved characters decoded, the
   others in upper case, and no dot segments in a path that begins with a
   slash. Nothing depends on the scheme, so a default port stays.

   Where a value is a security decision, like the "redirect_uri" of OAuth,
   compare the text as it came, not the result of this procedure. *)

PROCEDURE ParseQuery (q: TEXT; VAR r: Pairs; mode := QueryMode.Form): BOOLEAN;
(* Set "r" to the pairs of the query "q", which may be "NIL", and return
   "TRUE". Members are separated by "&", and empty members are dropped; a
   member without "=" has an empty value. Return "FALSE" and leave "r" alone
   if "q" has a character above 255 or, with "Form", a malformed escape. *)

PROCEDURE FormatQuery (p: Pairs; mode := QueryMode.Form): TEXT;
(* The query of the pairs, each with its "=". With "Form", names and values
   are escaped; with "Raw" they must be escaped already. It is a checked
   runtime error if a name or value has a character above 255. *)

PROCEDURE Escape (t: TEXT; READONLY safe := Unreserved; form := FALSE): TEXT;
(* "t" with an escape in upper-case hexadecimal for each byte that is not in
   "safe". "%" is always escaped. With "form", a space becomes "+" and "+" is
   always escaped. It is a checked runtime error if "t" has a character above
   255. *)

PROCEDURE Unescape (t: TEXT; VAR r: TEXT; form := FALSE): BOOLEAN;
(* Set "r" to the bytes that "t" stands for and return "TRUE". With "form",
   "+" stands for a space. Return "FALSE" and leave "r" alone if an escape is
   malformed or "t" has a character above 255. *)

END URI.
