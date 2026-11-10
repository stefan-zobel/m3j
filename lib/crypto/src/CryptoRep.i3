(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* What the modules of the package crypto share, beyond the interface
   Crypto. *)

INTERFACE CryptoRep;

IMPORT Atom, Crypto;

PROCEDURE Check (t: TEXT) RAISES {Crypto.Error};
(* Raises "Crypto.Error" with "NotBytes" if "t" has a character above 255. *)

PROCEDURE Fail (code: INTEGER) RAISES {Crypto.Error};
(* Raises "Crypto.Error" for a code of M3jCrypto. *)

PROCEDURE Raise (atom: Atom.T; text: TEXT) RAISES {Crypto.Error};
(* Raises "Crypto.Error" with "atom" and "text", which must be fixed. *)

END CryptoRep.
