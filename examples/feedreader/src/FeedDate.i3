(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The dates of feeds. Atom has those of RFC 3339, RSS those of RFC 822,
   which RFC 5322 now describes, and Dublin Core's "dc:date" those of ISO
   8601. Feeds stray from all three, so the forms that are often seen are
   accepted as well. *)

INTERFACE FeedDate;

IMPORT Time;

PROCEDURE Parse (t: TEXT; VAR r: Time.T): BOOLEAN;
(* Set "r" from "t" and return "TRUE", if "t" is a date in one of these
   forms; white space around it is ignored:

   - RFC 3339, also without seconds, with a space for the "T", or without
     a zone, which then is UTC; or only the date, which then is midnight
     in UTC;
   - RFC 5322, also without a zone, or with a zone by a name that is not
     among those of RFC 5322, such as "UTC" or "CEST".

   Otherwise return "FALSE" and leave "r" alone. *)

PROCEDURE Format (t: Time.T): TEXT;
(* "t" in RFC 3339, in UTC, to the second. *)

END FeedDate.
