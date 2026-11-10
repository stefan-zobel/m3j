(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE X25519;

IMPORT Crypto, CryptoRep, M3jCrypto, Text;

VAR Base: TEXT;   (* the u-coordinate 9 of the base point *)

PROCEDURE NewPrivate (): TEXT =
  BEGIN
    RETURN Crypto.Random (KeyLength);
  END NewPrivate;

PROCEDURE Public (private: TEXT): TEXT RAISES {Crypto.Error} =
  BEGIN
    RETURN Agree (private, Base);
  END Public;

PROCEDURE Agree (private, peerPublic: TEXT): TEXT RAISES {Crypto.Error} =
  VAR code := 0;  r: TEXT;
  BEGIN
    CryptoRep.Check (private);
    CryptoRep.Check (peerPublic);
    r := M3jCrypto.X25519 (private, peerPublic, code);
    IF r = NIL THEN CryptoRep.Fail (code) END;
    RETURN r;
  END Agree;

VAR b: ARRAY [0 .. KeyLength - 1] OF CHAR;

BEGIN
  FOR i := 1 TO LAST (b) DO b[i] := '\000' END;
  b[0] := '\011';
  Base := Text.FromChars (b);
END X25519.
