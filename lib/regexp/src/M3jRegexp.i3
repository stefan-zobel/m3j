(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The layer on the regular expressions of the Java platform, for the module
   Regexp. "compile" never fails: "error" of its result says why the pattern
   is not one, or is NIL. Bounds are the start and end of each group, -1 for
   a group that took no part; an empty array means that the match went over
   its budget or the stack, and so does a NIL result of "replace" or
   "split". A limit of 0 means no limit. *)

<*EXTERNAL "m3j.rt.Regexp":JAVA*> INTERFACE M3jRegexp;

IMPORT Java, JavaObject, M3jRegexpScan;

TYPE T <: JavaObject.T;

PROCEDURE compile (pattern: TEXT; options: Java.Int; limit: INTEGER): T;
PROCEDURE error (self: T): TEXT;
PROCEDURE groups (self: T): Java.Int;
PROCEDURE groupNumber (self: T; name: TEXT): Java.Int;

PROCEDURE quote (text: TEXT): TEXT;
PROCEDURE quoteReplacement (text: TEXT): TEXT;
PROCEDURE concat (READONLY pieces: ARRAY OF TEXT): TEXT;

PROCEDURE fullMatch (self: T; text: TEXT): REF ARRAY OF Java.Int;
PROCEDURE scan (self: T; text: TEXT; from: Java.Int): M3jRegexpScan.T;
PROCEDURE checkReplacement (self: T; replacement: TEXT): TEXT;
PROCEDURE replace (self: T; text, replacement: TEXT; all: BOOLEAN): TEXT;
PROCEDURE split (self: T; text: TEXT; max: Java.Int): REF ARRAY OF TEXT;

END M3jRegexp.
