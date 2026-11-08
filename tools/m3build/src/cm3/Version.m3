(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Version;

IMPORT M3jCompile;

PROCEDURE Created (): TEXT =
  BEGIN
    RETURN "not available";
  END Created;

PROCEDURE M3j (): TEXT =
  BEGIN
    RETURN M3jCompile.Version ();
  END M3j;

BEGIN
END Version.
