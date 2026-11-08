(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The module SHA256 of m3j. The one of libm3 is UNSAFE; here the Java platform
   computes the digest (M3jHash). *)

MODULE SHA256;

IMPORT Rd, M3jHash;

<*FATAL ANY*>

PROCEDURE FromText (txt: TEXT): TEXT =
  BEGIN
    RETURN M3jHash.Digest ("SHA-256", txt);
  END FromText;

PROCEDURE FromFile (rd: Rd.T): TEXT =
  BEGIN
    RETURN M3jHash.Digest ("SHA-256", Rd.GetText (rd, LAST (CARDINAL)));
  END FromFile;

BEGIN
END SHA256.
