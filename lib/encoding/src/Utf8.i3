(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Between characters and the bytes of UTF-8. In a "TEXT" of characters each
   element is a character of Unicode, and a character above U+FFFF is a pair
   of surrogates, since a "WIDECHAR" has 16 bits. In a "TEXT" of bytes each
   element is one byte.

   Text that goes on to a checksum, a signature or an escape must be turned
   into bytes here first: as a byte, "\351" and the character U+00E9 cannot
   be told apart, and they are different input in UTF-8. *)

INTERFACE Utf8;

PROCEDURE Encode (t: TEXT): TEXT;
(* The bytes of the characters of "t" in UTF-8, with all characters taken as
   code points, also those below 256. A surrogate without a partner becomes
   U+FFFD. *)

PROCEDURE Decode (t: TEXT; VAR r: TEXT): BOOLEAN;
(* Set "r" to the characters of the bytes in "t" and return "TRUE", if "t" is
   valid UTF-8: not cut off, no form longer than necessary, no surrogate, no
   code point above U+10FFFF. Otherwise, or if "t" has a character above 255,
   return "FALSE" and leave "r" alone. *)

END Utf8.
