(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Main;

IMPORT Hello, IO;

VAR f: PROCEDURE (who: TEXT): TEXT := Hello.hi;

BEGIN
  IO.Put (f ("library") & "\n");
END Main.
