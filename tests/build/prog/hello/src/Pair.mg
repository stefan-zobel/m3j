GENERIC MODULE Pair (Elem);

PROCEDURE Swap (READONLY p: T): T =
  VAR a: Elem.T := p.a;
  BEGIN
    RETURN T {p.b, a};
  END Swap;

BEGIN
END Pair.
