(* Objects: allocation and method dispatch over a type hierarchy. *)
MODULE Main;
IMPORT IO, Fmt;

TYPE
  Shape = OBJECT METHODS area (): INTEGER; scale (k: INTEGER) END;
  Rect = Shape OBJECT w, h: INTEGER OVERRIDES area := RectArea; scale := RectScale END;
  Square = Rect OBJECT OVERRIDES area := SquareArea END;
  Circle = Shape OBJECT r: INTEGER OVERRIDES area := CircleArea; scale := CircleScale END;

PROCEDURE RectArea (s: Rect): INTEGER = BEGIN RETURN s.w * s.h END RectArea;
PROCEDURE SquareArea (s: Square): INTEGER = BEGIN RETURN s.w * s.w END SquareArea;
PROCEDURE RectScale (s: Rect; k: INTEGER) = BEGIN s.w := s.w * k MOD 1000; s.h := s.h * k MOD 1000 END RectScale;
PROCEDURE CircleArea (s: Circle): INTEGER = BEGIN RETURN 3 * s.r * s.r END CircleArea;
PROCEDURE CircleScale (s: Circle; k: INTEGER) = BEGIN s.r := s.r * k MOD 1000 END CircleScale;

CONST N = 1000;

VAR shapes: ARRAY [0 .. N - 1] OF Shape;
    sum := 0;

BEGIN
  FOR r := 1 TO 20000 DO
    FOR i := 0 TO N - 1 DO
      CASE (i + r) MOD 3 OF
      | 0 => shapes[i] := NEW (Rect, w := i MOD 17 + 1, h := r MOD 13 + 1);
      | 1 => shapes[i] := NEW (Square, w := i MOD 11 + 1, h := 0);
      | 2 => shapes[i] := NEW (Circle, r := (i + r) MOD 19 + 1);
      END;
    END;
    FOR i := 0 TO N - 1 DO shapes[i].scale (r MOD 5 + 1) END;
    FOR i := 0 TO N - 1 DO INC (sum, shapes[i].area ()) END;
  END;
  IO.Put (Fmt.Int (sum) & "\n");
END Main.
