(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Authenticated encryption with associated data: AES-GCM (NIST SP 800-38D)
   and ChaCha20-Poly1305 (RFC 8439), on the ciphers of the Java platform.

|    key := Crypto.Random (32);
|    sealed := Aead.Seal (Aead.Algorithm.AesGcm, key, "secret", aad := "id 7");
|    plain := Aead.Open (Aead.Algorithm.AesGcm, key, sealed, aad := "id 7");

   A key has 16, 24 or 32 bytes for AES-GCM and 32 for ChaCha20-Poly1305. A
   nonce has 12 bytes and a tag 16. The associated data "aad" is
   authenticated but not encrypted; it must be given to "Open" as it was given
   to "Seal".

   A nonce must never be used twice with the same key: that gives away the
   key of the authentication and the difference of the two plain texts.
   "Seal" takes a random nonce, which is safe for up to 2^32 messages with
   one key; "SealWith" is for protocols that count their nonces.

   A message is handled in one piece in memory, also by the platform, which
   holds back all of the plain text until the tag has been checked. *)

INTERFACE Aead;

IMPORT Crypto;

TYPE Algorithm = {AesGcm, ChaCha20Poly1305};

CONST
  NonceLength = 12;
  TagLength = 16;

PROCEDURE Seal (a: Algorithm; key, plain: TEXT; aad := ""): TEXT RAISES {Crypto.Error};
(* "plain" encrypted with a new random nonce: the nonce, the ciphertext and
   the tag, in that order. *)

PROCEDURE Open (a: Algorithm; key, sealed: TEXT; aad := ""): TEXT RAISES {Crypto.Error};
(* The plain text of a result of "Seal". If "sealed" is not authentic,
   "Crypto.Error" with "BadTag", and nothing of the plain text is given
   out. *)

PROCEDURE SealWith (a: Algorithm; key, nonce, plain: TEXT; aad := ""): TEXT
  RAISES {Crypto.Error};
(* "plain" encrypted with "nonce": the ciphertext and the tag. *)

PROCEDURE OpenWith (a: Algorithm; key, nonce, sealed: TEXT; aad := ""): TEXT
  RAISES {Crypto.Error};
(* The plain text of a result of "SealWith", as "Open". *)

END Aead.
