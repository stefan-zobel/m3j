(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The identity of references, for tables keyed by references that a
   program of cm3 hashes by address (LOOPHOLE (r, Word.T)), which m3j
   does not compile: the procedure is a method of the class
   m3j.rt.Externals. *)

INTERFACE M3jRef;

<*EXTERNAL "M3jRef__Hash"*>
PROCEDURE Hash (r: REFANY): INTEGER;
(* A hash of the identity of r: the same for the same reference as long as
   the program runs, 0 for NIL. *)

END M3jRef.
