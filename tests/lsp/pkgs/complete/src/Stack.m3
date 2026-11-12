MODULE Stack;

REVEAL
  T = Public BRANDED OBJECT
    a: ARRAY [0 .. Max - 1] OF INTEGER;
  OVERRIDES
    push := Push;
    pop := Pop;
  END;

PROCEDURE Push (s: T; x: INTEGER) =
  BEGIN
    s.a[s.size] := x;
    INC(s.size);
  END Push;

PROCEDURE Pop (s: T): INTEGER =
  BEGIN
    DEC(s.size);
    RETURN s.a[s.size];
  END Pop;

PROCEDURE New (): T =
  BEGIN
    INC(count);
    RETURN NEW(T);
  END New;

PROCEDURE Top (s: T): INTEGER RAISES {Empty} =
  BEGIN
    IF s.size = 0 THEN RAISE Empty; END;
    RETURN s.a[s.size - 1];
  END Top;

BEGIN
END Stack.
