(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Key agreement with X25519 (RFC 7748), on the classes of the Java platform.
   Keys are 32 bytes, as the RFC encodes them.

|    mine := X25519.NewPrivate ();
|    send (X25519.Public (mine));
|    shared := X25519.Agree (mine, received);
|    key := Kdf.Hkdf (Digest.Algorithm.SHA256, shared, "", "app v1", 32);

   The shared secret is not uniformly random: derive keys from it with
   "Kdf.Hkdf", and bind both public keys into "info" where the protocol
   needs that. *)

INTERFACE X25519;

IMPORT Crypto;

CONST KeyLength = 32;

PROCEDURE NewPrivate (): TEXT;
(* A new private key: 32 random bytes. *)

PROCEDURE Public (private: TEXT): TEXT RAISES {Crypto.Error};
(* The public key of "private". *)

PROCEDURE Agree (private, peerPublic: TEXT): TEXT RAISES {Crypto.Error};
(* The shared secret of "private" and the public key of the other side. The
   highest bit of "peerPublic" is ignored, as the RFC says. A public key of
   small order, which makes the secret zero and known to everybody, raises
   "Crypto.Error" with "BadKey", as does a key of the wrong length. *)

END X25519.
