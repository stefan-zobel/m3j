INTERFACE Shape;

IMPORT Base;

TYPE
  Kind = {Circle, Square};
  Point = RECORD x, y: INTEGER; END;
  PointRef = REF Point;
  T <: Public;
  Public = Base.T OBJECT
    origin: Point;
    kind  : Kind;
  METHODS
    area (): REAL;
    move (dx, dy: INTEGER);
  END;

PROCEDURE Make (k: Kind): T;

END Shape.
