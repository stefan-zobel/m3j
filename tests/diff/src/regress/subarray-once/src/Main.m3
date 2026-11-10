(* SUBARRAY whose start and count are calls with side effects: each is
   evaluated once, and the check of the bounds and the result use the same
   values. From and Count give a larger value on each further call, so a
   second evaluation would change the length of the result or move it past
   the end of the array. *)

MODULE Main;

IMPORT IO, Fmt, Text;

TYPE
  Row = ARRAY OF INTEGER;
  Grid = ARRAY OF ARRAY OF INTEGER;
  Pair = RECORD a, b: INTEGER END;

VAR
  froms, counts := 0;
  a: ARRAY [0 .. 9] OF INTEGER;
  two := ARRAY [0 .. 1] OF INTEGER {100, 200};
  r := NEW (REF Row, 10);
  g := NEW (REF Grid, 5, 3);
  p := NEW (REF ARRAY OF Pair, 6);
  chars := ARRAY [0 .. 7] OF CHAR {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'};

PROCEDURE Reset () =
  BEGIN
    froms := 0;
    counts := 0;
    FOR i := FIRST (a) TO LAST (a) DO a[i] := i END;
    FOR i := FIRST (r^) TO LAST (r^) DO r[i] := 10 * i END;
    FOR i := FIRST (g^) TO LAST (g^) DO
      FOR j := FIRST (g[i]) TO LAST (g[i]) DO g[i, j] := 10 * i + j END;
    END;
    FOR i := FIRST (p^) TO LAST (p^) DO p[i] := Pair {i, -i} END;
  END Reset;

PROCEDURE From (n: INTEGER): INTEGER =
  BEGIN
    INC (froms);
    RETURN n + froms - 1;
  END From;

PROCEDURE Count (n: INTEGER): INTEGER =
  BEGIN
    INC (counts);
    RETURN n + counts - 1;
  END Count;

PROCEDURE Calls (): TEXT =
  BEGIN
    RETURN " (from " & Fmt.Int (froms) & ", count " & Fmt.Int (counts) & ")";
  END Calls;

PROCEDURE Show (READONLY x: Row): TEXT =
  VAR t := "[" & Fmt.Int (NUMBER (x)) & "]";
  BEGIN
    FOR i := FIRST (x) TO LAST (x) DO t := t & " " & Fmt.Int (x[i]) END;
    RETURN t;
  END Show;

PROCEDURE ShowGrid (READONLY x: Grid): TEXT =
  VAR t := "[" & Fmt.Int (NUMBER (x)) & "]";
  BEGIN
    FOR i := FIRST (x) TO LAST (x) DO
      t := t & " (";
      FOR j := FIRST (x[i]) TO LAST (x[i]) DO
        IF j > 0 THEN t := t & " " END;
        t := t & Fmt.Int (x[i, j]);
      END;
      t := t & ")";
    END;
    RETURN t;
  END ShowGrid;

PROCEDURE ShowPairs (READONLY x: ARRAY OF Pair): TEXT =
  VAR t := "[" & Fmt.Int (NUMBER (x)) & "]";
  BEGIN
    FOR i := FIRST (x) TO LAST (x) DO
      t := t & " " & Fmt.Int (x[i].a) & "/" & Fmt.Int (x[i].b);
    END;
    RETURN t;
  END ShowPairs;

PROCEDURE Fill (VAR x: Row; v: INTEGER) =
  BEGIN
    FOR i := FIRST (x) TO LAST (x) DO x[i] := v END;
  END Fill;

PROCEDURE Line (t: TEXT) =
  BEGIN
    IO.Put (t & Calls () & "\n");
  END Line;

VAR n: INTEGER;  t: TEXT;

BEGIN
  Reset ();
  Line ("readonly " & Show (SUBARRAY (a, From (2), Count (3))));

  Reset ();
  Line ("to the end " & Show (SUBARRAY (a, From (2), Count (8))));

  Reset ();
  n := NUMBER (SUBARRAY (a, From (1), Count (4)));
  Line ("number " & Fmt.Int (n));

  Reset ();
  SUBARRAY (a, From (3), Count (2)) := two;
  Line ("assigned " & Show (a));

  Reset ();
  two := SUBARRAY (a, From (6), Count (2));
  Line ("read " & Show (two));
  two := ARRAY [0 .. 1] OF INTEGER {100, 200};

  Reset ();
  Fill (SUBARRAY (a, From (5), Count (5)), 7);
  Line ("var " & Show (a));

  Reset ();
  SUBARRAY (r^, From (0), Count (4)) := SUBARRAY (a, 6, 4);
  Line ("open " & Show (r^));

  Reset ();
  Line ("grid " & ShowGrid (SUBARRAY (g^, From (1), Count (4))));

  Reset ();
  SUBARRAY (g^, From (0), Count (2)) := SUBARRAY (g^, 3, 2);
  Line ("grid assigned " & ShowGrid (g^));

  Reset ();
  Line ("records " & ShowPairs (SUBARRAY (p^, From (2), Count (4))));

  Reset ();
  t := Text.FromChars (SUBARRAY (chars, From (1), Count (7)));
  Line ("chars " & t);

  Reset ();
  n := 0;
  FOR i := 1 TO 3 DO
    INC (n, NUMBER (SUBARRAY (a, From (0), Count (3))));
  END;
  Line ("loop " & Fmt.Int (n));
END Main.
