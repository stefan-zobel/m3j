(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The common parts of the package crypto: its exception, randomness and a
   comparison of secrets. The package rests on the digests, ciphers and
   signatures of the Java platform; the modules choose the algorithms, the
   sizes and the checks.

   A TEXT stands for bytes here, one character for each byte, as in the rest
   of the library. A character above 255 raises "Error" with "NotBytes": text
   in Unicode, such as a string from JSON or a password that a user typed,
   goes through "Utf8.Encode" first.

   Secrets cannot be wiped from memory: a TEXT cannot change, and the garbage
   collector may have copied it. Only "Equal" and the primitives of the
   platform take a time that does not depend on secret data. *)

INTERFACE Crypto;

IMPORT Atom, AtomList;

EXCEPTION Error (AtomList.T);

VAR BadKey, BadNonce, BadTag, NotBytes, TooLarge, Unsupported: Atom.T;

(* An "Error" carries one of these atoms and an atom with a description.

   "BadKey": a key of the wrong length or kind, a key that breaks a rule
   (an RSA key of less than 2048 bits, a point that is not on its curve), or
   a key that does not fit the algorithm.

   "BadNonce": a nonce of the wrong length.

   "BadTag": a message that is not authentic: it, its nonce or its
   associated data was changed, or the key is not the one that sealed it.

   "NotBytes": a TEXT with a character above 255.

   "TooLarge": a length or a count beyond the limits of a procedure.

   "Unsupported": a format or a parameter that the package does not take,
   such as a PEM file of another kind. *)

PROCEDURE Random (n: CARDINAL): TEXT;
(* "n" random bytes from the generator of the platform, which is suitable for
   keys and nonces. *)

PROCEDURE RandomBytes (VAR b: ARRAY OF CHAR);
(* Fills "b" with random bytes. *)

PROCEDURE Equal (a, b: TEXT): BOOLEAN;
(* Are "a" and "b" equal? The time depends on their lengths, not on their
   characters, so that a MAC or a token can be compared with one from
   elsewhere. *)

END Crypto.
