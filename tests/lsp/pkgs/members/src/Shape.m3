MODULE Shape;

REVEAL
  T = Public BRANDED OBJECT
    secret: INTEGER := 0;
  OVERRIDES
    area := Area;
    move := Move;
    describe := Describe;
  END;

PROCEDURE Area (self: T): REAL =
  BEGIN
    RETURN FLOAT(self.secret);
  END Area;

PROCEDURE Move (self: T; dx, dy: INTEGER) =
  BEGIN
    INC(self.origin.x, dx);
    INC(self.origin.y, dy);
  END Move;

PROCEDURE Describe (self: T): TEXT =
  BEGIN
    RETURN self.name;
  END Describe;

PROCEDURE Make (k: Kind): T =
  BEGIN
    RETURN NEW(T, kind := k, name := "shape");
  END Make;

BEGIN
END Shape.
