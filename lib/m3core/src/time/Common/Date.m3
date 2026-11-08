(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The module Date of m3j: in cm3 it is UNSAFE and calls the operating
   system (DateWin32.m3, DatePosix.m3); here the calendar of the Java
   platform does the conversions (M3jDate). As in DateWin32, "offset" is
   UTC minus local time in seconds, and a UTC date has the zone "UTC". *)

MODULE Date;

IMPORT Time, M3jDate;

REVEAL TimeZone = BRANDED "Date.TimeZone (m3j)" REF RECORD local: BOOLEAN END;

PROCEDURE FromTime (t: Time.T; z: TimeZone := NIL): T =
  VAR
    d: T;
    year, month, day, hour, minute, second, offset, weekDay: INTEGER;
  BEGIN
    IF z = NIL THEN z := Local END;
    d.zone := M3jDate.Fields (t, z.local, year, month, day, hour, minute,
                              second, offset, weekDay);
    d.year := year;
    d.month := VAL (month, Month);
    d.day := day;
    d.hour := hour;
    d.minute := minute;
    d.second := second;
    d.offset := offset;
    d.weekDay := VAL (weekDay, WeekDay);
    RETURN d;
  END FromTime;

PROCEDURE ToTime (READONLY d: T): Time.T RAISES {Error} =
  VAR ok: INTEGER;  t: Time.T;
  BEGIN
    t := M3jDate.ToTime (d.year, ORD (d.month), d.day, d.hour, d.minute,
                         d.second, d.offset, ok);
    IF ok = 0 THEN RAISE Error END;
    RETURN t;
  END ToTime;

BEGIN
  Local := NEW (TimeZone, local := TRUE);
  UTC := NEW (TimeZone, local := FALSE);
END Date.
