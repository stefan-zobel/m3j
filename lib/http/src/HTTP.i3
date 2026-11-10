(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* What the server "HTTPServer" and the client "HTTPClient" have in common:
   errors, header fields, and the encodings of URLs, dates and basic
   authentication.

   All the texts that this interface reads or makes are strings of bytes,
   one "CHAR" for each byte, as they are on the wire. *)

INTERFACE HTTP;

IMPORT Atom, AtomList, Time;

VAR Timeout, TooLarge, BadMessage, Unreachable, BadUrl: Atom.T;

(* "Timeout": a limit of time has expired. "TooLarge": a message exceeds a
   limit. "BadMessage": a message or a value breaks the rules of HTTP.
   "Unreachable": no connection to the server could be made. "BadUrl": the
   syntax of a URL or an escape in it is wrong. *)

EXCEPTION Error (AtomList.T);

(* An "Error" carries one of the atoms above and an atom with a description
   of the error. *)

TYPE
  Headers <: HeadersPublic;
  HeadersPublic = OBJECT
  METHODS
    get (name: TEXT): TEXT;
    add (name, value: TEXT);
    set (name, value: TEXT);
    remove (name: TEXT);
    iterate (): Iterator;
  END;

  Iterator <: IteratorPublic;
  IteratorPublic = OBJECT
  METHODS
    next (VAR name, value: TEXT): BOOLEAN;
  END;

(* "Headers" are the fields of the head of a message, in order. A name may
   occur several times, and names are compared without regard to case.

   "h.get(name)" is the value of the first field of that name, or "NIL".
   Use "iterate" where a name may occur several times.

   "h.add(name, value)" appends a field.

   "h.set(name, value)" gives the first field of that name the value and
   removes the others of that name; if there is none, it appends a field.

   "h.remove(name)" removes all the fields of that name.

   "h.iterate()" returns an iterator over the fields in order: "next"
   returns "FALSE" at the end.

   A "Headers" is not monitored: a client must serialize the calls on one
   object. The methods do not check names and values, which the server and
   the client do before they send a field; see "ValidName". *)

PROCEDURE NewHeaders (): Headers;
(* An empty "Headers". *)

PROCEDURE ValidName (name: TEXT): BOOLEAN;
(* Is "name" a token of RFC 9110, as the name of a field must be? *)

PROCEDURE ValidValue (value: TEXT): BOOLEAN;
(* May "value" be the value of a field? It may not contain a control
   character other than a horizontal tab, so also neither CR nor LF. *)

PROCEDURE Escape (t: TEXT; form := FALSE): TEXT;
(* "t" with percent escapes in place of all the bytes outside the unreserved
   characters of RFC 3986: letters, digits and "-._~". With "form", for
   application/x-www-form-urlencoded, a space becomes "+". A character
   above 255 is taken as a Unicode character and goes out in UTF-8, a pair
   of surrogates as the one character it stands for. *)

PROCEDURE Unescape (t: TEXT; form := FALSE): TEXT RAISES {Error};
(* The bytes that "t" stands for. With "form", "+" stands for a space.
   Raise "Error" with "BadUrl" if an escape is malformed. *)

PROCEDURE FormatDate (t: Time.T): TEXT;
(* The time "t" in the preferred format of HTTP, for example "Sun, 06 Nov
   1994 08:49:37 GMT". *)

PROCEDURE ParseDate (t: TEXT; VAR r: Time.T): BOOLEAN;
(* Set "r" to the time in "t" and return "TRUE", if "t" is in one of the
   three formats of RFC 9110: the preferred one, that of RFC 850 ("Sunday,
   06-Nov-94 08:49:37 GMT") or that of asctime ("Sun Nov  6 08:49:37
   1994"). A year of two digits is the one that is closest to the present
   year. The name of the day must be valid, but it need not match the
   date. *)

PROCEDURE BasicAuth (user, password: TEXT): TEXT RAISES {Error};
(* The value of an "Authorization" field for the scheme "Basic". Characters
   above 255 go out in UTF-8. Raise "Error" with "BadMessage" if "user"
   contains a colon, which the scheme cannot represent. *)

PROCEDURE ParseBasicAuth (value: TEXT; VAR user, password: TEXT): BOOLEAN;
(* Set "user" and "password" from the value of an "Authorization" field and
   return "TRUE", if it is of the scheme "Basic" and well formed. The
   Base64 must be padded, and the bits that are left over in its last
   character must be zero. *)

PROCEDURE Reason (status: INTEGER): TEXT;
(* The usual reason phrase of the status code, or "" if it has none. *)

END HTTP.
