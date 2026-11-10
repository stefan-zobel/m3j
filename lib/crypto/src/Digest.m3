(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Digest;

IMPORT Crypto, CryptoRep, M3jCrypto, Rd, Thread;

CONST BufferSize = 32768;

REVEAL
  T = Public BRANDED "Digest.T" OBJECT
    d: REFANY;
  OVERRIDES
    update := Update;
    finish := Finish;
  END;

PROCEDURE Update (self: T; READONLY b: ARRAY OF CHAR) =
  BEGIN
    LOCK self DO M3jCrypto.DigestUpdate (self.d, b) END;
  END Update;

PROCEDURE Finish (self: T): TEXT =
  BEGIN
    LOCK self DO RETURN M3jCrypto.DigestFinish (self.d) END;
  END Finish;

PROCEDURE Of (a: Algorithm; data: TEXT): TEXT RAISES {Crypto.Error} =
  BEGIN
    CryptoRep.Check (data);
    RETURN M3jCrypto.DigestOf (ORD (a), data);
  END Of;

PROCEDURE New (a: Algorithm): T =
  BEGIN
    RETURN NEW (T, d := M3jCrypto.DigestNew (ORD (a)));
  END New;

PROCEDURE FromRd (a: Algorithm; rd: Rd.T): TEXT RAISES {Rd.Failure, Thread.Alerted} =
  VAR
    d := New (a);
    buf := NEW (REF ARRAY OF CHAR, BufferSize);
    n: CARDINAL;
  BEGIN
    LOOP
      n := Rd.GetSub (rd, buf^);
      IF n = 0 THEN EXIT END;
      d.update (SUBARRAY (buf^, 0, n));
    END;
    RETURN d.finish ();
  END FromRd;

PROCEDURE Hmac (a: Algorithm; key, data: TEXT): TEXT RAISES {Crypto.Error} =
  BEGIN
    CryptoRep.Check (key);
    CryptoRep.Check (data);
    RETURN M3jCrypto.Hmac (ORD (a), key, data);
  END Hmac;

PROCEDURE NewHmac (a: Algorithm; key: TEXT): T RAISES {Crypto.Error} =
  BEGIN
    CryptoRep.Check (key);
    RETURN NEW (T, d := M3jCrypto.HmacNew (ORD (a), key));
  END NewHmac;

BEGIN
END Digest.
