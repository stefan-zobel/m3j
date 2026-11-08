MODULE CaseLabels EXPORTS Main;

TYPE Color = {Red, Green, Blue, Black};

PROCEDURE R (x: INTEGER;  c: Color): INTEGER =
  VAR n := 0;
  BEGIN
    CASE x OF
    | 1 .. 10 => n := 1;
    | 5 .. 15 => n := 2;
    | 20, 20 => n := 3;
    | 30 .. 40, 35 => n := 4;
    | 50 .. 60 => n := 5;
    | 45 .. 55, 70 => n := 6;
    | 100, 90 .. 110 => n := 7;
    ELSE
    END;
    CASE c OF
    | Color.Red, Color.Green => n := 8;
    | Color.Green .. Color.Black => n := 9;
    END;
    RETURN n;
  END R;

BEGIN
  EVAL R (3, Color.Red);
END CaseLabels.
