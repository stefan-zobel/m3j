(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The module SHA256 of m3j. The one of libm3 is UNSAFE; here the Java platform
   computes the digest (M3jHash). *)

MODULE SHA256;

IMPORT Rd, M3jHash;

<*FATAL ANY*>

CONST BlockLen = 65536;

PROCEDURE FromText (txt: TEXT): TEXT =
  BEGIN
    RETURN M3jHash.Digest ("SHA-256", txt);
  END FromText;

PROCEDURE FromFile (rd: Rd.T): TEXT =
  VAR
    d := M3jHash.New ("SHA-256");
    buf := NEW (REF ARRAY OF CHAR, BlockLen);
    count: CARDINAL;
  BEGIN
    REPEAT
      count := Rd.GetSub (rd, buf^);
      M3jHash.Update (d, SUBARRAY (buf^, 0, count));
    UNTIL count < BlockLen;
    RETURN M3jHash.Finish (d);
  END FromFile;

BEGIN
END SHA256.
