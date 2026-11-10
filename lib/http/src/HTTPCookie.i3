(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Cookies of RFC 6265: the "Set-Cookie" field that a server sends and the
   "Cookie" field that a client sends back. A server sets a cookie with

|    c := HTTPCookie.T {name := "session", value := id, path := "/",
|                       secure := TRUE, httpOnly := TRUE,
|                       sameSite := HTTPCookie.SameSite.Lax};
|    resp.headers.add ("Set-Cookie", HTTPCookie.Format (c));

   and reads it back with "HTTPCookie.Get (req.headers, "session")". Each
   cookie needs a field of its own, so use "add", not "set", for
   "Set-Cookie".

   Names, values and attributes are strings of bytes, like all the texts of
   "HTTP". A value may not contain a space, a comma, a semicolon, a double
   quote or a backslash, so a program that stores arbitrary data in a cookie
   encodes it first, for example with "Base64.Encode" and the alphabet
   "Url". A cookie is not a safe place for a secret that the client must
   not read or change: sign or encrypt the value for that. *)

INTERFACE HTTPCookie;

IMPORT HTTP, Time;

CONST
  NoExpires = FIRST (LONGREAL);
  NoMaxAge = -1;

TYPE
  SameSite = {Unset, Strict, Lax, None};

  T = RECORD
    name, value: TEXT;
    path, domain: TEXT := NIL;
    expires: Time.T := NoExpires;
    maxAge: INTEGER := NoMaxAge;
    secure, httpOnly := FALSE;
    sameSite := SameSite.Unset;
  END;

(* "path" and "domain" are "NIL" if the cookie has none. "expires" is the
   time at which the cookie ends, "NoExpires" if it has none. "maxAge" is
   the number of seconds that the cookie lives, "NoMaxAge" if it has none;
   0 asks the client to delete the cookie at once. A client that gets both
   takes "maxAge". A cookie without either ends with the session of the
   client. "NoExpires" is not 0.0, because the start of 1970 is the usual
   date to delete a cookie.

   "secure" keeps the cookie to connections over TLS, "httpOnly" keeps it
   from the scripts of a page, and "sameSite" says whether a browser sends
   it with requests that other sites start. *)

PROCEDURE Format (READONLY c: T): TEXT RAISES {HTTP.Error};
(* The value of a "Set-Cookie" field for "c", for example "id=a3fWa;
   Max-Age=2592000; Path=/; Secure; HttpOnly; SameSite=Lax". The attributes
   come in the order "Expires", "Max-Age", "Domain", "Path", "Secure",
   "HttpOnly", "SameSite".

   Raise "HTTP.Error" with "HTTP.BadMessage" unless all of these hold:

   "name" is a token of RFC 9110, and "value" consists of the characters
   that RFC 6265 allows, perhaps within a pair of double quotes, which are
   then part of the value. Neither may be "NIL".

   "path" starts with a slash and has only printable ASCII characters
   other than a semicolon. "domain" is not empty and has only letters,
   digits, hyphens and dots.

   "expires" is in the years 1601 to 9999, and "maxAge" is not below -1.

   A cookie with "SameSite.None" is "secure". A name that starts with
   "__Secure-", in any case, belongs to a cookie that is "secure"; one that
   starts with "__Host-" to a cookie that is "secure", has the path "/" and
   no domain. Browsers drop cookies that break these rules.

   Raise "HTTP.Error" with "HTTP.TooLarge" if the name and the value have
   together more than 4096 bytes, or the path or the domain more than 1024,
   for browsers drop such cookies too. *)

PROCEDURE ParseSetCookie (value: TEXT; VAR c: T): BOOLEAN;
(* Set "c" from the value of a "Set-Cookie" field and return "TRUE", or
   return "FALSE" if a client must ignore the field. This is the lenient
   algorithm that RFC 6265 gives for clients, in section 5.2, with these
   rules of its successor, draft-ietf-httpbis-rfc6265bis: a control
   character other than a tab anywhere, a name and value of more than 4096
   bytes together or a character above 255 makes the field invalid, an
   attribute with a value of more than 1024 bytes is ignored, and
   "SameSite" is read.

   A field without "=" before its first semicolon or with an empty name is
   invalid. Spaces and tabs around the name, the value and the attributes
   are removed. The names of attributes are compared without regard to
   case; unknown attributes and malformed values are ignored, and a later
   attribute replaces an earlier one of the same name.

   "domain" loses a leading dot and is turned into lower case; an empty
   "Domain" is ignored. A "Path" that does not start with a slash leaves
   "path" as "NIL", which means the default path of the client. A
   "Max-Age" of 0 or less becomes 0, and one that does not fit into an
   "INTEGER" becomes "LAST (INTEGER)". "Expires" takes the many date
   formats that section 5.1.1 of RFC 6265 describes, but a year before 1601
   is invalid. An unknown value of "SameSite" means "SameSite.Unset". *)

PROCEDURE Get (h: HTTP.Headers; name: TEXT): TEXT;
(* The value of the first cookie called "name" in the "Cookie" fields of
   "h", or "NIL" if there is none. Names are compared with regard to case.
   The value is as the client sent it, with the double quotes if it has
   any. *)

END HTTPCookie.
