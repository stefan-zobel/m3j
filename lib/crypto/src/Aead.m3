(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Aead;

IMPORT Crypto, CryptoRep, M3jCrypto, Text;

PROCEDURE Seal (a: Algorithm; key, plain: TEXT; aad := ""): TEXT RAISES {Crypto.Error} =
  VAR nonce := Crypto.Random (NonceLength);
  BEGIN
    RETURN nonce & SealWith (a, key, nonce, plain, aad);
  END Seal;

PROCEDURE Open (a: Algorithm; key, sealed: TEXT; aad := ""): TEXT RAISES {Crypto.Error} =
  VAR nonce, rest: TEXT;
  BEGIN
    CryptoRep.Check (sealed);
    IF Text.Length (sealed) < NonceLength THEN
      (* the key is still checked first; then the tag is missing *)
      nonce := Zeros;
      rest := "";
    ELSE
      nonce := Text.Sub (sealed, 0, NonceLength);
      rest := Text.Sub (sealed, NonceLength);
    END;
    RETURN OpenWith (a, key, nonce, rest, aad);
  END Open;

PROCEDURE SealWith (a: Algorithm; key, nonce, plain: TEXT; aad := ""): TEXT
  RAISES {Crypto.Error} =
  VAR code := 0;  r: TEXT;
  BEGIN
    CryptoRep.Check (key);
    CryptoRep.Check (nonce);
    CryptoRep.Check (plain);
    CryptoRep.Check (aad);
    r := M3jCrypto.AeadSeal (ORD (a), key, nonce, plain, aad, code);
    IF r = NIL THEN CryptoRep.Fail (code) END;
    RETURN r;
  END SealWith;

PROCEDURE OpenWith (a: Algorithm; key, nonce, sealed: TEXT; aad := ""): TEXT
  RAISES {Crypto.Error} =
  VAR code := 0;  r: TEXT;
  BEGIN
    CryptoRep.Check (key);
    CryptoRep.Check (nonce);
    CryptoRep.Check (sealed);
    CryptoRep.Check (aad);
    r := M3jCrypto.AeadOpen (ORD (a), key, nonce, sealed, aad, code);
    IF r = NIL THEN CryptoRep.Fail (code) END;
    RETURN r;
  END OpenWith;

VAR Zeros := Text.FromChars (ARRAY [0 .. NonceLength - 1] OF CHAR {'\000', ..});

BEGIN
END Aead.
