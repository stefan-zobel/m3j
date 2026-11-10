(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Digests and HMAC (RFC 2104) of bytes, on the classes of the Java platform.
   The results are bytes; "Hex.Encode" or "Base64.Encode" make text of them.

|    sum := Hex.Encode (Digest.Of (Digest.Algorithm.SHA256, data));
|    mac := Digest.Hmac (Digest.Algorithm.SHA256, key, message);
|    ok := Crypto.Equal (mac, received);

   MD5 and SHA-1 are broken for signatures and certificates; they are here
   for formats that need them, and HMAC with them is still sound. *)

INTERFACE Digest;

IMPORT Crypto, Rd, Thread;

TYPE
  Algorithm = {MD5, SHA1, SHA256, SHA384, SHA512, SHA3_256, SHA3_384, SHA3_512};

CONST
  Lengths = ARRAY Algorithm OF CARDINAL {16, 20, 32, 48, 64, 32, 48, 64};
  (* The length of a digest, and of an HMAC, in bytes. *)

TYPE
  T <: Public;
  Public = MUTEX OBJECT
  METHODS
    update (READONLY b: ARRAY OF CHAR);
    finish (): TEXT;
  END;

(* A digest or an HMAC in progress, for data that need not fit in memory.
   "d.update(b)" adds the bytes of "b". "d.finish()" returns the digest of
   all the bytes that were added and starts "d" again with no data and, for
   an HMAC, the same key.

   A "T" is a monitor: calls from several threads at once do not break it,
   though the bytes from different threads mix in the order in which the
   calls come. *)

PROCEDURE Of (a: Algorithm; data: TEXT): TEXT RAISES {Crypto.Error};
(* The digest of "data". *)

PROCEDURE New (a: Algorithm): T;
(* A digest in progress. *)

PROCEDURE FromRd (a: Algorithm; rd: Rd.T): TEXT RAISES {Rd.Failure, Thread.Alerted};
(* The digest of what remains in "rd", which is read to its end. *)

PROCEDURE Hmac (a: Algorithm; key, data: TEXT): TEXT RAISES {Crypto.Error};
(* The HMAC of "data" with "key", which may have any length, also 0. *)

PROCEDURE NewHmac (a: Algorithm; key: TEXT): T RAISES {Crypto.Error};
(* An HMAC in progress. *)

END Digest.
