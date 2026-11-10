(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Digital signatures with Ed25519 (RFC 8032), RSA (PKCS #1 v1.5 and PSS,
   RFC 8017) and ECDSA on the curves P-256, P-384 and P-521, on the classes
   of the Java platform.

|    pair := Signature.GenerateEd25519 ();
|    sig := Signature.Sign (Signature.Algorithm.Ed25519, pair.private, data);
|    ok := Signature.Verify (Signature.Algorithm.Ed25519, pair.public, data, sig);

   The algorithms are those of JWS (RFC 7518 and 8037): RS256 is "RsaSha256",
   PS256 "PssSha256" (MGF1 with the same digest, a salt as long as the digest),
   ES256 "EcdsaP256Sha256", EdDSA "Ed25519". An ECDSA signature is the fixed
   form "r || s" of JWS, each as long as a coordinate of the curve.

   Every key passes the same checks, wherever it comes from: an RSA modulus
   has 2048 to 16384 bits and an odd exponent, a point of a curve lies on it
   and its coordinates are less than the prime of the curve. *)

INTERFACE Signature;

IMPORT Crypto;

TYPE
  Algorithm = {Ed25519, RsaSha256, RsaSha384, RsaSha512, PssSha256, PssSha384, PssSha512,
               EcdsaP256Sha256, EcdsaP384Sha384, EcdsaP521Sha512};
  Curve = {P256, P384, P521};
  Kind = {Ed25519, Rsa, P256, P384, P521};

CONST
  KindOf = ARRAY Algorithm OF Kind {Kind.Ed25519, Kind.Rsa, Kind.Rsa, Kind.Rsa, Kind.Rsa, Kind.Rsa,
                                    Kind.Rsa, Kind.P256, Kind.P384, Kind.P521};
  (* The kind of key of an algorithm. *)

TYPE
  PublicKey <: REFANY;
  PrivateKey <: REFANY;
  Pair = RECORD public: PublicKey;  private: PrivateKey END;

PROCEDURE Ed25519Public (raw: TEXT): PublicKey RAISES {Crypto.Error};
(* The key of 32 bytes in the encoding of RFC 8032, as in the "x" of a JWK. *)

PROCEDURE RsaPublic (n, e: TEXT): PublicKey RAISES {Crypto.Error};
(* The key with modulus "n" and exponent "e", unsigned big-endian numbers;
   leading zero bytes do not count. *)

PROCEDURE EcPublic (c: Curve; x, y: TEXT): PublicKey RAISES {Crypto.Error};
(* The point ("x", "y"), unsigned big-endian numbers of exactly the length
   of a coordinate of the curve (32, 48 or 66 bytes), as RFC 7518 wants. *)

PROCEDURE PublicFromPem (pem: TEXT): PublicKey RAISES {Crypto.Error};
(* The key of a PEM text with the label "PUBLIC KEY" (a SubjectPublicKeyInfo,
   as from "openssl pkey -pubout"). Text before and after the key is
   ignored. Other labels, such as "RSA PUBLIC KEY", raise "Crypto.Error" with
   "Unsupported"; so do the keys of other algorithms (such as X25519 or
   RSA-PSS) and of other curves, also curves given by their parameters
   instead of their name. *)

PROCEDURE PrivateFromPem (pem: TEXT): PrivateKey RAISES {Crypto.Error};
(* The key of a PEM text with the label "PRIVATE KEY" (an unencrypted
   PKCS #8 key, as from "openssl genpkey"). The older forms "RSA PRIVATE
   KEY" and "EC PRIVATE KEY" and encrypted keys raise "Crypto.Error" with
   "Unsupported"; "openssl pkcs8 -topk8 -nocrypt" converts them. Keys of
   other algorithms and curves are refused as by "PublicFromPem". *)

PROCEDURE PublicToPem (k: PublicKey): TEXT;
PROCEDURE PrivateToPem (k: PrivateKey): TEXT;
(* The key as PEM text, in lines of 64 characters, each ended by a line
   feed. *)

PROCEDURE PublicKind (k: PublicKey): Kind;
PROCEDURE PrivateKind (k: PrivateKey): Kind;

PROCEDURE PublicParts (k: PublicKey; VAR a, b: TEXT);
(* The parts of a key as the procedures above take them: "n" and "e" of RSA,
   "x" and "y" of a curve, the raw key of Ed25519 and NIL. *)

PROCEDURE GenerateEd25519 (): Pair;
PROCEDURE GenerateRsa (bits := 3072): Pair RAISES {Crypto.Error};
PROCEDURE GenerateEc (c: Curve): Pair;
(* A new pair of keys. "bits" is from 2048 to 8192, or "Crypto.Error" with
   "BadKey". *)

PROCEDURE Sign (a: Algorithm; k: PrivateKey; data: TEXT): TEXT RAISES {Crypto.Error};
(* The signature of "data". A key of another kind than "KindOf[a]" raises
   "Crypto.Error" with "BadKey". *)

PROCEDURE Verify (a: Algorithm; k: PublicKey; data, sig: TEXT): BOOLEAN
  RAISES {Crypto.Error};
(* Is "sig" a signature of "data" with the key? A signature that is malformed
   in any way is "FALSE". A key of another kind than "KindOf[a]" raises
   "Crypto.Error" with "BadKey"; a program that takes the algorithm from a
   message, such as the "alg" of a JWT, must treat that as a "no". *)

END Signature.
