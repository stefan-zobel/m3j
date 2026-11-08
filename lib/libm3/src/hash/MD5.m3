(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The module MD5 of m3j. The one of libm3 is UNSAFE; here the Java platform
   computes the digest (M3jHash). *)

MODULE MD5;

IMPORT Rd, M3jHash;

<*FATAL ANY*>

PROCEDURE FromText (txt: TEXT): TEXT =
  BEGIN
    RETURN M3jHash.Digest ("MD5", txt);
  END FromText;

PROCEDURE FromFile (rd: Rd.T): TEXT =
  BEGIN
    RETURN M3jHash.Digest ("MD5", Rd.GetText (rd, LAST (CARDINAL)));
  END FromFile;

BEGIN
END MD5.
