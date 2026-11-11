(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Main;

IMPORT IO, JSystem;

PROCEDURE Show (key: TEXT) =
  VAR v := JSystem.getProperty (key);
  BEGIN
    IF v = NIL THEN v := "(none)" END;
    IO.Put (key & "=[" & v & "]\n");
  END Show;

BEGIN
  Show ("m3j.first");
  Show ("m3j.second");
END Main.
