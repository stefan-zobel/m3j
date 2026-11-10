(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Times in the format of RFC 3339, for example "1985-04-12T23:20:50.52Z".

   An offset here is in seconds east of UTC, as in the text of the format:
   "+01:00" is 3600. That is the opposite sign of the field "offset" of
   "Date.T". *)

INTERFACE RFC3339;

IMPORT Time;

PROCEDURE Format (t: Time.T; offset := 0; digits := 0): TEXT;
(* The time "t" as seen at "offset", with "digits" digits of the fraction of
   a second. "t" is first rounded to microseconds, since a "Time.T" is rarely
   exact in decimal, and further digits are then cut off. It is a checked
   runtime error if "offset" is not a whole number of minutes or not less
   than a day in size, if "digits" is not in 0 to 6, or if the year is not in
   0 to 9999. *)

PROCEDURE Parse (t: TEXT; VAR r: Time.T; VAR offset: INTEGER): BOOLEAN;
(* Set "r" and "offset" from "t" and return "TRUE", if "t" is a valid time
   of RFC 3339. Otherwise return "FALSE" and leave them alone. "T" and "Z"
   may be in lower case. "-00:00", an unknown offset, gives 0. The seconds
   may be 60 only at the end of the last minute of a month in UTC, and
   "Time.T" having no leap seconds such a time becomes the next second. *)

END RFC3339.
