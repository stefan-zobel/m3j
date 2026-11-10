(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Signature;

IMPORT Base64, Crypto, CryptoRep, M3jCrypto, Text;

REVEAL
  PublicKey = BRANDED "Signature.PublicKey" REF RECORD
    k: REFANY;
    kind: Kind;
  END;

  PrivateKey = BRANDED "Signature.PrivateKey" REF RECORD
    k: REFANY;
    kind: Kind;
  END;

CONST
  Dashes = "-----";
  PublicLabel = "PUBLIC KEY";
  PrivateLabel = "PRIVATE KEY";
  LineLength = 64;

PROCEDURE NewPublic (k: REFANY; code: INTEGER): PublicKey RAISES {Crypto.Error} =
  BEGIN
    IF k = NIL THEN CryptoRep.Fail (code) END;
    RETURN NEW (PublicKey, k := k, kind := VAL (M3jCrypto.KeyKind (k), Kind));
  END NewPublic;

PROCEDURE Ed25519Public (raw: TEXT): PublicKey RAISES {Crypto.Error} =
  VAR code := 0;
  BEGIN
    CryptoRep.Check (raw);
    RETURN NewPublic (M3jCrypto.Ed25519Public (raw, code), code);
  END Ed25519Public;

PROCEDURE RsaPublic (n, e: TEXT): PublicKey RAISES {Crypto.Error} =
  VAR code := 0;
  BEGIN
    CryptoRep.Check (n);
    CryptoRep.Check (e);
    RETURN NewPublic (M3jCrypto.RsaPublic (n, e, code), code);
  END RsaPublic;

PROCEDURE EcPublic (c: Curve; x, y: TEXT): PublicKey RAISES {Crypto.Error} =
  VAR code := 0;
  BEGIN
    CryptoRep.Check (x);
    CryptoRep.Check (y);
    RETURN NewPublic (M3jCrypto.EcPublic (ORD (c), x, y, code), code);
  END EcPublic;

(* The first place of "s" in "t" from "start", or -1. *)
PROCEDURE Find (t, s: TEXT; start: INTEGER): INTEGER =
  VAR n := Text.Length (t);  m := Text.Length (s);  first := Text.GetChar (s, 0);  i := start;
  BEGIN
    LOOP
      i := Text.FindChar (t, first, i);
      IF i < 0 OR i > n - m THEN RETURN -1 END;
      IF Text.Equal (Text.Sub (t, i, m), s) THEN RETURN i END;
      INC (i);
    END;
  END Find;

PROCEDURE Malformed () RAISES {Crypto.Error} =
  BEGIN
    CryptoRep.Raise (Crypto.BadKey, "not a valid PEM text");
  END Malformed;

(* The bytes of the first PEM block of "pem", which must have "label". *)
PROCEDURE Unarmor (pem, label: TEXT): TEXT RAISES {Crypto.Error} =
  VAR start, stop: INTEGER;  der: TEXT;
  BEGIN
    CryptoRep.Check (pem);
    start := Find (pem, Dashes & "BEGIN ", 0);
    IF start < 0 THEN Malformed () END;
    INC (start, 11);
    stop := Find (pem, Dashes, start);
    IF stop < 0 THEN Malformed () END;
    IF NOT Text.Equal (Text.Sub (pem, start, stop - start), label) THEN
      CryptoRep.Raise (Crypto.Unsupported, "a PEM text of another kind");
    END;
    start := stop + Text.Length (Dashes);
    stop := Find (pem, Dashes & "END " & label & Dashes, start);
    IF stop < 0 THEN Malformed () END;
    IF NOT Base64.Decode (Text.Sub (pem, start, stop - start), der, ignoreSpace := TRUE) THEN
      Malformed ();
    END;
    RETURN der;
  END Unarmor;

PROCEDURE Armor (der, label: TEXT): TEXT =
  VAR b := Base64.Encode (der);  n := Text.Length (b);  r := Dashes & "BEGIN " & label & Dashes & "\n";  i := 0;
  BEGIN
    WHILE i < n DO
      r := r & Text.Sub (b, i, LineLength) & "\n";
      INC (i, LineLength);
    END;
    RETURN r & Dashes & "END " & label & Dashes & "\n";
  END Armor;

PROCEDURE PublicFromPem (pem: TEXT): PublicKey RAISES {Crypto.Error} =
  VAR code := 0;  der := Unarmor (pem, PublicLabel);
  BEGIN
    RETURN NewPublic (M3jCrypto.PublicFromDer (der, code), code);
  END PublicFromPem;

PROCEDURE PrivateFromPem (pem: TEXT): PrivateKey RAISES {Crypto.Error} =
  VAR code := 0;  der := Unarmor (pem, PrivateLabel);  k := M3jCrypto.PrivateFromDer (der, code);
  BEGIN
    IF k = NIL THEN CryptoRep.Fail (code) END;
    RETURN NEW (PrivateKey, k := k, kind := VAL (M3jCrypto.KeyKind (k), Kind));
  END PrivateFromPem;

PROCEDURE PublicToPem (k: PublicKey): TEXT =
  BEGIN
    RETURN Armor (M3jCrypto.KeyDer (k.k), PublicLabel);
  END PublicToPem;

PROCEDURE PrivateToPem (k: PrivateKey): TEXT =
  BEGIN
    RETURN Armor (M3jCrypto.KeyDer (k.k), PrivateLabel);
  END PrivateToPem;

PROCEDURE PublicKind (k: PublicKey): Kind =
  BEGIN
    RETURN k.kind;
  END PublicKind;

PROCEDURE PrivateKind (k: PrivateKey): Kind =
  BEGIN
    RETURN k.kind;
  END PrivateKind;

PROCEDURE PublicParts (k: PublicKey; VAR a, b: TEXT) =
  BEGIN
    a := M3jCrypto.PublicPart (k.k, 0);
    b := M3jCrypto.PublicPart (k.k, 1);
  END PublicParts;

PROCEDURE Generate (kind: Kind; bits: INTEGER): Pair RAISES {Crypto.Error} =
  VAR code := 0;  private: REFANY;  public := M3jCrypto.Generate (ORD (kind), bits, private, code);
  BEGIN
    IF public = NIL THEN CryptoRep.Fail (code) END;
    RETURN Pair {NEW (PublicKey, k := public, kind := kind), NEW (PrivateKey, k := private, kind := kind)};
  END Generate;

PROCEDURE GenerateEd25519 (): Pair =
  <*FATAL Crypto.Error*>
  BEGIN
    RETURN Generate (Kind.Ed25519, 0);
  END GenerateEd25519;

PROCEDURE GenerateRsa (bits := 3072): Pair RAISES {Crypto.Error} =
  BEGIN
    RETURN Generate (Kind.Rsa, bits);
  END GenerateRsa;

PROCEDURE GenerateEc (c: Curve): Pair =
  <*FATAL Crypto.Error*>
  BEGIN
    RETURN Generate (VAL (ORD (Kind.P256) + ORD (c), Kind), 0);
  END GenerateEc;

PROCEDURE Mismatch () RAISES {Crypto.Error} =
  BEGIN
    CryptoRep.Raise (Crypto.BadKey, "a key of another kind than the algorithm wants");
  END Mismatch;

PROCEDURE Sign (a: Algorithm; k: PrivateKey; data: TEXT): TEXT RAISES {Crypto.Error} =
  VAR code := 0;  r: TEXT;
  BEGIN
    CryptoRep.Check (data);
    IF k.kind # KindOf[a] THEN Mismatch () END;
    r := M3jCrypto.Sign (ORD (a), k.k, data, code);
    IF r = NIL THEN CryptoRep.Fail (code) END;
    RETURN r;
  END Sign;

PROCEDURE Verify (a: Algorithm; k: PublicKey; data, sig: TEXT): BOOLEAN RAISES {Crypto.Error} =
  BEGIN
    CryptoRep.Check (data);
    IF k.kind # KindOf[a] THEN Mismatch () END;
    IF Text.HasWideChars (sig) THEN RETURN FALSE END;
    RETURN M3jCrypto.Verify (ORD (a), k.k, data, sig);
  END Verify;

BEGIN
END Signature.
