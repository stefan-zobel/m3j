(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Main;

IMPORT Greeter, Extra, IO, Fmt;

BEGIN
  IO.Put (Greeter.greet ("m3build") & " " & Fmt.Int (Extra.twice (21)) & "\n");
END Main.
