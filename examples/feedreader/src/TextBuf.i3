(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* A text made of pieces, which are joined once at the end, so that adding
   a piece costs no copy of what is there. A "TextWr" would do the same for
   bytes, but these pieces are Unicode. *)

INTERFACE TextBuf;

TYPE
  T <: Public;
  Public = OBJECT
  METHODS
    put (t: TEXT);
    text (): TEXT;              (* all the pieces so far *)
  END;

PROCEDURE New (): T;

END TextBuf.
