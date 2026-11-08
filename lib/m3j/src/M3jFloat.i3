(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The bits of floating-point numbers, as the Java platform gives them
   (IEEE 754): the procedures are methods of the class m3j.rt.Externals.
   EXTENDED is LONGREAL on AMD64_NT. *)

INTERFACE M3jFloat;

<*EXTERNAL "M3jFloat__RealBits"*>
PROCEDURE RealBits (x: REAL): INTEGER;
(* The 32 bits of x, in [0 .. 2^32-1]. *)

<*EXTERNAL "M3jFloat__LongBits"*>
PROCEDURE LongBits (x: LONGREAL): INTEGER;
(* The 64 bits of x. *)

<*EXTERNAL "M3jFloat__RealFromBits"*>
PROCEDURE RealFromBits (b: INTEGER): REAL;
(* The REAL with the bits b MOD 2^32. *)

<*EXTERNAL "M3jFloat__LongFromBits"*>
PROCEDURE LongFromBits (b: INTEGER): LONGREAL;
(* The LONGREAL with the bits b. *)

END M3jFloat.
