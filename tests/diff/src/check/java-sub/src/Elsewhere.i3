(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

INTERFACE Elsewhere;

IMPORT Java;

TYPE Parse = PROCEDURE (s: TEXT): Java.Int;

PROCEDURE Value (): Parse;
(* The procedure value of Integer.parseInt, taken in another unit. *)

END Elsewhere.
