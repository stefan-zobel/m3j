(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Crypto EXPORTS Crypto, CryptoRep;

IMPORT Atom, AtomList, M3jCrypto, Text;

PROCEDURE Random (n: CARDINAL): TEXT =
  BEGIN
    RETURN M3jCrypto.Random (n);
  END Random;

PROCEDURE RandomBytes (VAR b: ARRAY OF CHAR) =
  BEGIN
    M3jCrypto.RandomBytes (b);
  END RandomBytes;

PROCEDURE Equal (a, b: TEXT): BOOLEAN =
  BEGIN
    <*ASSERT a # NIL AND b # NIL*>
    RETURN M3jCrypto.Equal (a, b);
  END Equal;

PROCEDURE Check (t: TEXT) RAISES {Error} =
  BEGIN
    IF Text.HasWideChars (t) THEN
      Raise (NotBytes, "a character above 255 where bytes are expected");
    END;
  END Check;

(* The texts are fixed, because an atom made from data would stay for good. *)
PROCEDURE Fail (code: INTEGER) RAISES {Error} =
  BEGIN
    CASE code OF
    | M3jCrypto.BadKey => Raise (BadKey, "a key of the wrong length or kind, or one that breaks a rule");
    | M3jCrypto.BadNonce => Raise (BadNonce, "a nonce of the wrong length");
    | M3jCrypto.BadTag => Raise (BadTag, "the message is not authentic");
    | M3jCrypto.TooLarge => Raise (TooLarge, "beyond the limits of the procedure");
    | M3jCrypto.Unsupported => Raise (Unsupported, "a format or a parameter that is not supported");
    END;
  END Fail;

PROCEDURE Raise (atom: Atom.T; text: TEXT) RAISES {Error} =
  BEGIN
    RAISE Error (AtomList.List2 (atom, Atom.FromText (text)));
  END Raise;

BEGIN
  BadKey := Atom.FromText ("Crypto.BadKey");
  BadNonce := Atom.FromText ("Crypto.BadNonce");
  BadTag := Atom.FromText ("Crypto.BadTag");
  NotBytes := Atom.FromText ("Crypto.NotBytes");
  TooLarge := Atom.FromText ("Crypto.TooLarge");
  Unsupported := Atom.FromText ("Crypto.Unsupported");
END Crypto.
