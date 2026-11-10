(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The Base64 encodings of RFC 4648. A "TEXT" stands for bytes here, one
   character for each byte.

   "Decode" is strict, since Base64 often sits in front of a signature and a
   value should have just one form: the bits that are left over in the last
   character must be zero, and a character of the other alphabet is an
   error. *)

INTERFACE Base64;

TYPE
  Alphabet = {Standard, Url};
  Padding = {Required, Forbidden, Optional};

(* "Standard" has the characters "+" and "/", "Url" has "-" and "_" in their
   place. *)

PROCEDURE Encode (t: TEXT; a := Alphabet.Standard; pad := TRUE): TEXT;
(* The bytes of "t" in Base64, with "=" at the end if "pad". It is a checked
   runtime error if "t" has a character above 255. *)

PROCEDURE Decode (t: TEXT; VAR r: TEXT; a := Alphabet.Standard;
                  p := Padding.Required; ignoreSpace := FALSE): BOOLEAN;
(* Set "r" to the bytes that "t" stands for and return "TRUE", if "t" is
   valid. Otherwise return "FALSE" and leave "r" alone. With "ignoreSpace",
   spaces, tabs, carriage returns and line feeds count for nothing wherever
   they are, as in PEM. With "Optional", the padding may be missing, but if
   there is any it must be right. *)

END Base64.
