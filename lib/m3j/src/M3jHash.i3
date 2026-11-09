(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The digests of the Java platform, for the modules MD5 and SHA256 of m3j:
   the procedures are methods of the class m3j.rt.Externals. *)

INTERFACE M3jHash;

<*EXTERNAL "M3jHash__Digest"*>
PROCEDURE Digest (alg: TEXT; t: TEXT): TEXT;
(* The digest of the characters of t with the algorithm alg ("MD5",
   "SHA-256"), in lower-case hex. *)

<*EXTERNAL "M3jHash__New"*>
PROCEDURE New (alg: TEXT): REFANY;
(* A digest in progress with the algorithm alg, for input that need not fit
   in memory. *)

<*EXTERNAL "M3jHash__Update"*>
PROCEDURE Update (d: REFANY; READONLY b: ARRAY OF CHAR);
(* Adds the characters of b to the digest d. *)

<*EXTERNAL "M3jHash__Finish"*>
PROCEDURE Finish (d: REFANY): TEXT;
(* The digest of all the characters given to d, in lower-case hex. *)

END M3jHash.
