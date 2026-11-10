(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The cryptography of the Java platform, for the package crypto of m3j: the
   procedures are methods of the class m3j.rt.Crypto (through
   m3j.rt.Externals). A TEXT stands for bytes; the callers make sure that no
   character is above 255. A failure sets "code" to one of the numbers below.

   The algorithms are numbered in the order of "Digest.Algorithm" and
   "Signature.Algorithm", the kinds of keys in that of "Signature.Kind", the
   curves in that of "Signature.Curve". *)

INTERFACE M3jCrypto;

CONST
  BadKey = 1;
  BadNonce = 2;
  BadTag = 3;
  TooLarge = 4;
  Unsupported = 5;

<*EXTERNAL "M3jCrypto__Random"*>
PROCEDURE Random (n: INTEGER): TEXT;

<*EXTERNAL "M3jCrypto__RandomBytes"*>
PROCEDURE RandomBytes (VAR b: ARRAY OF CHAR);

<*EXTERNAL "M3jCrypto__Equal"*>
PROCEDURE Equal (a, b: TEXT): BOOLEAN;
(* The time depends on the lengths only. *)

<*EXTERNAL "M3jCrypto__DigestNew"*>
PROCEDURE DigestNew (alg: INTEGER): REFANY;

<*EXTERNAL "M3jCrypto__HmacNew"*>
PROCEDURE HmacNew (alg: INTEGER; key: TEXT): REFANY;

<*EXTERNAL "M3jCrypto__DigestUpdate"*>
PROCEDURE DigestUpdate (d: REFANY; READONLY b: ARRAY OF CHAR);

<*EXTERNAL "M3jCrypto__DigestFinish"*>
PROCEDURE DigestFinish (d: REFANY): TEXT;
(* The digest or the MAC; d starts again. *)

<*EXTERNAL "M3jCrypto__DigestOf"*>
PROCEDURE DigestOf (alg: INTEGER; data: TEXT): TEXT;

<*EXTERNAL "M3jCrypto__Hmac"*>
PROCEDURE Hmac (alg: INTEGER; key, data: TEXT): TEXT;

<*EXTERNAL "M3jCrypto__AeadSeal"*>
PROCEDURE AeadSeal (alg: INTEGER; key, nonce, plain, aad: TEXT; VAR code: INTEGER): TEXT;
(* The ciphertext and the tag, or NIL. alg is 0 for AES-GCM and 1 for
   ChaCha20-Poly1305. *)

<*EXTERNAL "M3jCrypto__AeadOpen"*>
PROCEDURE AeadOpen (alg: INTEGER; key, nonce, sealed, aad: TEXT; VAR code: INTEGER): TEXT;
(* The plain text, or NIL. *)

<*EXTERNAL "M3jCrypto__Pbkdf2"*>
PROCEDURE Pbkdf2 (alg: INTEGER; password, salt: TEXT; iterations, length: INTEGER;
                  VAR code: INTEGER): TEXT;

<*EXTERNAL "M3jCrypto__Hkdf"*>
PROCEDURE Hkdf (alg: INTEGER; ikm, salt, info: TEXT; length: INTEGER; VAR code: INTEGER): TEXT;

<*EXTERNAL "M3jCrypto__Ed25519Public"*>
PROCEDURE Ed25519Public (raw: TEXT; VAR code: INTEGER): REFANY;

<*EXTERNAL "M3jCrypto__RsaPublic"*>
PROCEDURE RsaPublic (n, e: TEXT; VAR code: INTEGER): REFANY;

<*EXTERNAL "M3jCrypto__EcPublic"*>
PROCEDURE EcPublic (curve: INTEGER; x, y: TEXT; VAR code: INTEGER): REFANY;

<*EXTERNAL "M3jCrypto__PublicFromDer"*>
PROCEDURE PublicFromDer (der: TEXT; VAR code: INTEGER): REFANY;
(* A key from a SubjectPublicKeyInfo, or NIL. *)

<*EXTERNAL "M3jCrypto__PrivateFromDer"*>
PROCEDURE PrivateFromDer (der: TEXT; VAR code: INTEGER): REFANY;
(* A key from an unencrypted PrivateKeyInfo, or NIL. *)

<*EXTERNAL "M3jCrypto__KeyKind"*>
PROCEDURE KeyKind (k: REFANY): INTEGER;

<*EXTERNAL "M3jCrypto__KeyDer"*>
PROCEDURE KeyDer (k: REFANY): TEXT;
(* The SubjectPublicKeyInfo or the PrivateKeyInfo of k. *)

<*EXTERNAL "M3jCrypto__PublicPart"*>
PROCEDURE PublicPart (k: REFANY; i: INTEGER): TEXT;
(* Part i (0 or 1) of a public key: n and e, x and y, or the raw key and NIL. *)

<*EXTERNAL "M3jCrypto__Generate"*>
PROCEDURE Generate (kind, bits: INTEGER; VAR private: REFANY; VAR code: INTEGER): REFANY;
(* The public key of a new pair, or NIL. "bits" counts for RSA only. *)

<*EXTERNAL "M3jCrypto__Sign"*>
PROCEDURE Sign (alg: INTEGER; k: REFANY; data: TEXT; VAR code: INTEGER): TEXT;
(* The key must be of the kind of alg. *)

<*EXTERNAL "M3jCrypto__Verify"*>
PROCEDURE Verify (alg: INTEGER; k: REFANY; data, sig: TEXT): BOOLEAN;
(* The key must be of the kind of alg. *)

<*EXTERNAL "M3jCrypto__X25519"*>
PROCEDURE X25519 (scalar, u: TEXT; VAR code: INTEGER): TEXT;
(* The function X25519 of RFC 7748, or NIL. *)

END M3jCrypto.
