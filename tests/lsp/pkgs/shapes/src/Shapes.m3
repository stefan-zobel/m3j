MODULE Shapes;

PROCEDURE CircleArea (c: Circle): REAL =
  BEGIN
    RETURN 3.14159 * c.radius * c.radius;
  END CircleArea;

PROCEDURE NewCircle (name: TEXT; radius: REAL): Circle =
  BEGIN
    RETURN NEW (Circle, name := name, radius := radius);
  END NewCircle;

BEGIN
END Shapes.
