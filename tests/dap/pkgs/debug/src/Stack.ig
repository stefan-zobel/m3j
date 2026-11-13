(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

GENERIC INTERFACE Stack (Elem);

TYPE
  T = REF RECORD
    items: REF ARRAY OF Elem.T;
    size: CARDINAL := 0;
  END;

PROCEDURE New (): T;
PROCEDURE Push (s: T; e: Elem.T);
PROCEDURE Pop (s: T): Elem.T;

END Stack.
