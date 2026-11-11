(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* News for those who wait for it, such as the pages that show new entries
   as they come: a counter that grows each time a fetch brings new entries. *)

INTERFACE Events;

IMPORT Thread;

PROCEDURE Signal ();
(* New entries have come. *)

PROCEDURE Count (): INTEGER;
(* How often "Signal" was called so far. *)

PROCEDURE Wait (seen: INTEGER; timeout: LONGREAL): INTEGER RAISES {Thread.Alerted};
(* Waits until "Count () > seen", but no longer than "timeout" seconds, and
   returns "Count ()". *)

END Events.
