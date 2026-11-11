(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The matches of a pattern in a text, for the module Regexp: "next (s, i)"
   gives the bounds of the match after the first "i" (as
   "M3jRegexp.fullMatch"), NIL if there is none, an empty array if a search
   went over its budget or the stack. *)

<*EXTERNAL "m3j.rt.RegexpScan":JAVA*> INTERFACE M3jRegexpScan;

IMPORT Java, JavaObject;

TYPE T <: JavaObject.T;

PROCEDURE next (self: T; index: Java.Int): REF ARRAY OF Java.Int;

END M3jRegexpScan.
