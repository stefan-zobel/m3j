(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

GENERIC MODULE Stack (Elem);

PROCEDURE New (): T =
  BEGIN
    RETURN NEW (T, items := NEW (REF ARRAY OF Elem.T, 4));
  END New;

PROCEDURE Push (s: T; e: Elem.T) =
  VAR bigger: REF ARRAY OF Elem.T;
  BEGIN
    IF s.size = NUMBER (s.items^) THEN
      bigger := NEW (REF ARRAY OF Elem.T, 2 * s.size);
      SUBARRAY (bigger^, 0, s.size) := s.items^;
      s.items := bigger;
    END;
    s.items[s.size] := e; (* @push *)
    INC (s.size);
  END Push;

PROCEDURE Pop (s: T): Elem.T =
  BEGIN
    DEC (s.size);
    RETURN s.items[s.size];
  END Pop;

BEGIN
END Stack.
