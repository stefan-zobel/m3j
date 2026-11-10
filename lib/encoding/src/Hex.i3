(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Bytes as hexadecimal digits. A "TEXT" stands for bytes here, one character
   for each byte, as with the rest of this package. *)

INTERFACE Hex;

PROCEDURE Encode (t: TEXT): TEXT;
(* The bytes of "t" as two lower-case digits each. It is a checked runtime
   error if "t" has a character above 255. *)

PROCEDURE Decode (t: TEXT; VAR r: TEXT): BOOLEAN;
(* Set "r" to the bytes that "t" stands for and return "TRUE", if "t" is an
   even number of digits of either case. Otherwise return "FALSE" and leave
   "r" alone. *)

END Hex.
