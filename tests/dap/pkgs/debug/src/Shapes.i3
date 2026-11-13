(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* An interface with an opaque object type, revealed in Shapes.m3. *)

INTERFACE Shapes;

TYPE
  Kind = {Circle, Square};
  T <: Public;
  Public = OBJECT
    kind: Kind;
  METHODS
    area (): REAL;
  END;

VAR made: CARDINAL := 0;

PROCEDURE Circle (r: REAL): T;
PROCEDURE Square (side: REAL): T;

END Shapes.
