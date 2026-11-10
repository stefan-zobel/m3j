(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Universally unique identifiers of RFC 9562: random ones (version 4) and
   ones that begin with the time (version 7), from the random generator of
   "Crypto".

|    id := UUID.Format (UUID.NewV7 ());   (* "019a6c1e-7f3a-7c4d-9e21-..." *) *)

INTERFACE UUID;

TYPE T = ARRAY [0 .. 15] OF CHAR;

PROCEDURE NewV4 (): T;
(* 122 random bits. *)

PROCEDURE NewV7 (): T;
(* The milliseconds since 1970 in the first 48 bits, then 74 random bits.
   Identifiers of different milliseconds sort by their time, as bytes and as
   text; within a millisecond their order is random. *)

PROCEDURE Format (READONLY u: T): TEXT;
(* The form "8-4-4-4-12" in lower-case hexadecimal digits. *)

PROCEDURE Parse (t: TEXT; VAR u: T): BOOLEAN;
(* Reads the form "8-4-4-4-12" with digits of either case; "FALSE" for any
   other text. *)

PROCEDURE Version (READONLY u: T): [0 .. 15];
(* The version in "u", such as 4 or 7. *)

END UUID.
