(* The interface NetObjEpoch of m3j for Win32. In cm3 a "Time.T" on Win32
   counts from 1601 and "T" is the time of 1970; the "Time.T" of m3j counts
   from 1970 on every system, so here "T" is 0, as for POSIX. *)

(* Copyright (C) 1995, Digital Equipment Corporation        *)
(* All rights reserved.                                     *)
(* See the file COPYRIGHT for a full description.           *)
(*                                                          *)
(* Last modified on Thu Apr 13 13:46:27 PDT 1995 by kalsow  *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

INTERFACE NetObjEpoch;

CONST T = 0.0d0;
(* "X - T" gives the network object time that corresponds to a
   "Time.T" of "X".  The base of network object time is Jan 1, 1970. *)

END NetObjEpoch.
