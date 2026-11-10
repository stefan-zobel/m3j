(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Kdf;

IMPORT Crypto, CryptoRep, Digest, M3jCrypto;

PROCEDURE Pbkdf2 (a: Digest.Algorithm; password, salt: TEXT; iterations, length: CARDINAL): TEXT
  RAISES {Crypto.Error} =
  VAR code := 0;  r: TEXT;
  BEGIN
    CryptoRep.Check (password);
    CryptoRep.Check (salt);
    r := M3jCrypto.Pbkdf2 (ORD (a), password, salt, iterations, length, code);
    IF r = NIL THEN CryptoRep.Fail (code) END;
    RETURN r;
  END Pbkdf2;

PROCEDURE Hkdf (a: Digest.Algorithm; ikm, salt, info: TEXT; length: CARDINAL): TEXT
  RAISES {Crypto.Error} =
  VAR code := 0;  r: TEXT;
  BEGIN
    CryptoRep.Check (ikm);
    CryptoRep.Check (salt);
    CryptoRep.Check (info);
    r := M3jCrypto.Hkdf (ORD (a), ikm, salt, info, length, code);
    IF r = NIL THEN CryptoRep.Fail (code) END;
    RETURN r;
  END Hkdf;

BEGIN
END Kdf.
