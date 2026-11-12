INTERFACE Stack;

CONST Max = 100;

TYPE
  Color = {Red, Green};
  T <: Public;
  Public = OBJECT
    size: CARDINAL := 0;
  METHODS
    push (x: INTEGER);
    pop (): INTEGER;
  END;

VAR count: INTEGER;

EXCEPTION Empty;

PROCEDURE New (): T;
PROCEDURE Top (s: T): INTEGER RAISES {Empty};

END Stack.
