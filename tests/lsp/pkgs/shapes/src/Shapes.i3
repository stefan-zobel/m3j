INTERFACE Shapes;

TYPE
  T = OBJECT
    name: TEXT;
  METHODS
    area (): REAL;
  END;

  Circle = T OBJECT
    radius: REAL;
  OVERRIDES
    area := CircleArea;
  END;

PROCEDURE CircleArea (c: Circle): REAL;

PROCEDURE NewCircle (name: TEXT; radius: REAL): Circle;

END Shapes.
