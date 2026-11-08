(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The calendar of the Java platform, for the module Date of m3j: the
   procedures are methods of the class m3j.rt.Externals. *)

INTERFACE M3jDate;

<*EXTERNAL "M3jDate__Fields"*>
PROCEDURE Fields (t: LONGREAL; local: BOOLEAN;
                  VAR year, month, day, hour, minute, second, offset, weekDay: INTEGER): TEXT;
(* The date of the time t in the local time zone (local) or in UTC, with
   month 0 for January and weekDay 0 for Sunday; offset is UTC minus local
   time in seconds. The result is the name of the time zone. *)

<*EXTERNAL "M3jDate__ToTime"*>
PROCEDURE ToTime (year, month, day, hour, minute, second, offset: INTEGER;
                  VAR ok: INTEGER): LONGREAL;
(* The time of the date given in UTC plus offset seconds; ok is 0 if
   there is no such date. *)

END M3jDate.
