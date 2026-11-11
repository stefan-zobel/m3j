(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Times in the format of RFC 5322, the format of mail and of RSS, for
   example "Tue, 01 Jul 2003 10:52:37 +0200". An offset here is in seconds
   east of UTC, as in the text of the format: "+0200" is 7200. That is the
   opposite sign of the field "offset" of "Date.T". *)

INTERFACE RFC5322;

IMPORT Time;

PROCEDURE Format (t: Time.T; offset := 0): TEXT;
(* The time "t", to the second, as seen at "offset", with the day of the
   week: "Tue, 01 Jul 2003 10:52:37 +0200". It is a checked runtime error
   if "offset" is not a whole number of minutes or not less than a day in
   size, or if the year is not in 1900 to 9999. *)

PROCEDURE Parse (t: TEXT; VAR r: Time.T; VAR offset: INTEGER): BOOLEAN;
(* Set "r" and "offset" from "t" and return "TRUE", if "t" is a time of
   RFC 5322, with its obsolete forms. Otherwise return "FALSE" and leave
   them alone. Accepted are:

   - an optional day of the week, by its three letters or its full name,
     which is not checked against the date, and a comma;
   - the day in one or two digits, the month by its three letters, in any
     case, and the year in four digits, or in two (below 50 is 20xx,
     otherwise 19xx) or three (1900 added);
   - the hour in one or two digits, the minutes, and optional seconds,
     where 60 is accepted at the end of the last minute of a month in UTC
     and becomes the next second;
   - the zone as "+hhmm" or "-hhmm", less than a day, or one of "UT", "GMT", "Z", "EST",
     "EDT", "CST", "CDT", "MST", "MDT", "PST" and "PDT"; the other military
     zones of one letter and "-0000" give 0;
   - white space between the parts, and a comment in parentheses at the
     end, as in "+0000 (UTC)".

   Case does not matter in names. The year must be from 1900 to 9999. *)

END RFC5322.
