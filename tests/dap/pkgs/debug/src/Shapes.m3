(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Shapes;

REVEAL
  T = Public BRANDED OBJECT
    size: REAL;
  OVERRIDES
    area := Area;
  END;

PROCEDURE Area (self: T): REAL =
  BEGIN
    IF self.kind = Kind.Circle THEN
      RETURN 3.0 * self.size * self.size; (* @area *)
    ELSE
      RETURN self.size * self.size;
    END;
  END Area;

PROCEDURE Circle (r: REAL): T =
  BEGIN
    INC (made);
    RETURN NEW (T, kind := Kind.Circle, size := r);
  END Circle;

PROCEDURE Square (side: REAL): T =
  BEGIN
    INC (made);
    RETURN NEW (T, kind := Kind.Square, size := side);
  END Square;

BEGIN
END Shapes.
