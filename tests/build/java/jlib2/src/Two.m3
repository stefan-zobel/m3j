(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Two;

IMPORT Hello;

PROCEDURE Say (): TEXT =
  BEGIN
    RETURN Hello.hi ("two");
  END Say;

BEGIN
END Two.
