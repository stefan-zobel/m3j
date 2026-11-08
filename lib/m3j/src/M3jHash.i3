(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The digests of the Java platform, for the modules MD5 and SHA256 of m3j:
   the procedure is a method of the class m3j.rt.Externals. *)

INTERFACE M3jHash;

<*EXTERNAL "M3jHash__Digest"*>
PROCEDURE Digest (alg: TEXT; t: TEXT): TEXT;
(* The digest of the characters of t with the algorithm alg ("MD5",
   "SHA-256"), in lower-case hex. *)

END M3jHash.
