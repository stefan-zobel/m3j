(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Deriving keys: PBKDF2 (RFC 8018) from passwords, and HKDF (RFC 5869) from
   keys that are already strong, both with HMAC.

|    salt := Crypto.Random (16);
|    hash := Kdf.Pbkdf2 (Digest.Algorithm.SHA256, Utf8.Encode (password),
|                        salt, 600000, 32);
|    key := Kdf.Hkdf (Digest.Algorithm.SHA256, secret, "", "app v1 key", 32);

   A password is bytes: one that a user typed goes through "Utf8.Encode"
   first, which is what other programs do. PBKDF2 is the only function for
   passwords here; it is weaker against special hardware than memory-hard
   functions, which the platform does not have. Compare its results with
   "Crypto.Equal". *)

INTERFACE Kdf;

IMPORT Crypto, Digest;

CONST
  MaxIterations = 100000000;
  MaxPbkdf2Length = 1024;

PROCEDURE Pbkdf2 (a: Digest.Algorithm; password, salt: TEXT; iterations, length: CARDINAL): TEXT
  RAISES {Crypto.Error};
(* "length" bytes derived from "password" and "salt" with "iterations"
   rounds of HMAC. A count of 0 raises "Crypto.Error" with "Unsupported";
   more than "MaxIterations" rounds, which cannot be interrupted, or more than
   "MaxPbkdf2Length" bytes raise "TooLarge". *)

PROCEDURE Hkdf (a: Digest.Algorithm; ikm, salt, info: TEXT; length: CARDINAL): TEXT
  RAISES {Crypto.Error};
(* "length" bytes derived from the input key material "ikm", bound to the
   context "info". An empty "salt" stands for the zeros of the RFC. More
   than 255 times "Digest.Lengths[a]" bytes raise "Crypto.Error" with
   "TooLarge". *)

END Kdf.
