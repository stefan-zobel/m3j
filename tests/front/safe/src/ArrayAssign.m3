MODULE ArrayAssign;

(* Assignments of open arrays to fixed arrays, and SUBARRAY of fixed and
   open arrays with constant and variable start and length, as values and
   as designators. *)

TYPE
  Row = ARRAY [0 .. 3] OF INTEGER;
  Grid = ARRAY [0 .. 1] OF Row;
  Open = ARRAY OF INTEGER;
  Open2 = ARRAY OF ARRAY OF INTEGER;
  Mixed = ARRAY OF Row;
  Rec = RECORD a: Row;  b: CHAR END;

EXCEPTION
  Wrong (Row);

VAR
  row: Row;
  grid: Grid;
  rec: Rec;
  o := NEW (REF Open, 4);
  o2 := NEW (REF Open2, 2, 4);
  m := NEW (REF Mixed, 2);
  start := 1;
  len := 2;

PROCEDURE Fill (VAR x: ARRAY OF INTEGER;  v: INTEGER) =
  BEGIN
    FOR i := FIRST (x) TO LAST (x) DO x[i] := v + i END;
  END Fill;

PROCEDURE Sum (READONLY x: ARRAY OF INTEGER): INTEGER =
  VAR s := 0;
  BEGIN
    FOR i := FIRST (x) TO LAST (x) DO INC (s, x[i]) END;
    RETURN s;
  END Sum;

PROCEDURE Sum2 (READONLY x: ARRAY OF ARRAY OF INTEGER): INTEGER =
  VAR s := 0;
  BEGIN
    FOR i := FIRST (x) TO LAST (x) DO INC (s, Sum (x[i])) END;
    RETURN s;
  END Sum2;

PROCEDURE Copy (READONLY src: ARRAY OF INTEGER): Row =
  VAR r: Row;
  BEGIN
    r := src;
    RETURN r;
  END Copy;

PROCEDURE Result (READONLY src: ARRAY OF INTEGER): Row =
  BEGIN
    RETURN src;
  END Result;

PROCEDURE Result2 (READONLY src: ARRAY OF ARRAY OF INTEGER): Grid =
  BEGIN
    RETURN src;
  END Result2;

PROCEDURE Raise (READONLY src: ARRAY OF INTEGER) RAISES {Wrong} =
  BEGIN
    RAISE Wrong (src);
  END Raise;

PROCEDURE Copy2 (READONLY src: ARRAY OF ARRAY OF INTEGER;  VAR dst: Grid) =
  BEGIN
    dst := src;
    dst[0] := src[1];
  END Copy2;

PROCEDURE Values (VAR local: ARRAY [0 .. 7] OF INTEGER): INTEGER =
  VAR s := 0;
  BEGIN
    (* fixed array *)
    INC (s, Sum (SUBARRAY (local, 2, 4)));
    INC (s, Sum (SUBARRAY (local, start, 4)));
    INC (s, Sum (SUBARRAY (local, 2, len)));
    INC (s, Sum (SUBARRAY (local, start, len)));
    (* open array *)
    INC (s, Sum (SUBARRAY (o^, 1, 2)));
    INC (s, Sum (SUBARRAY (o^, start, 2)));
    INC (s, Sum (SUBARRAY (o^, 1, len)));
    INC (s, Sum (SUBARRAY (o^, start, len)));
    (* open array of open arrays *)
    INC (s, Sum (SUBARRAY (o2[1], start, len)));
    INC (s, NUMBER (SUBARRAY (o2^, 1, 1)));
    INC (s, Sum2 (SUBARRAY (o2^, 1, 1)));
    INC (s, Sum2 (SUBARRAY (o2^, start, 1)));
    INC (s, NUMBER (SUBARRAY (o2^, start, len - 1)));
    (* open array of fixed arrays *)
    INC (s, NUMBER (SUBARRAY (m^, 0, 1)));
    INC (s, NUMBER (SUBARRAY (m^, start, len - 1)));
    RETURN s;
  END Values;

PROCEDURE Designators (VAR local: ARRAY [0 .. 7] OF INTEGER) =
  BEGIN
    (* fixed array *)
    SUBARRAY (local, 2, 4) := row;
    SUBARRAY (local, start, 4) := row;
    SUBARRAY (local, 2, len) := SUBARRAY (row, 0, len);
    SUBARRAY (local, start, len) := SUBARRAY (row, start, len);
    Fill (SUBARRAY (local, 4, 4), 5);
    Fill (SUBARRAY (local, start, 3), 5);
    Fill (SUBARRAY (local, 1, len), 5);
    Fill (SUBARRAY (local, start, len), 5);
    (* open array *)
    SUBARRAY (o^, 1, 2) := SUBARRAY (local, 3, 2);
    SUBARRAY (o^, start, 2) := SUBARRAY (local, start, 2);
    SUBARRAY (o^, 1, len) := SUBARRAY (local, 0, len);
    SUBARRAY (o^, start, len) := SUBARRAY (local, start, len);
    Fill (SUBARRAY (o^, 0, 2), 7);
    Fill (SUBARRAY (o^, start, 2), 7);
    Fill (SUBARRAY (o^, 0, len), 7);
    Fill (SUBARRAY (o^, start, len), 7);
    (* nested *)
    SUBARRAY (o2[1], start, 2) := SUBARRAY (row, 1, 2);
    SUBARRAY (o2^, 0, 1) := SUBARRAY (o2^, 1, 1);
    SUBARRAY (o2^, start, len - 1) := SUBARRAY (o2^, 0, 1);
    SUBARRAY (m^, start, len - 1) := SUBARRAY (grid, 0, 1);
    SUBARRAY (grid, start, 1) := SUBARRAY (m^, 0, 1);
    WITH w = SUBARRAY (local, start, len) DO w[0] := w[1] END;
    WITH w = SUBARRAY (o^, start, len) DO w[1] := w[0] END;
  END Designators;

PROCEDURE Run (): INTEGER =
  VAR local: ARRAY [0 .. 7] OF INTEGER;  g: Grid;
  BEGIN
    Fill (o^, 10);
    FOR i := 0 TO 1 DO Fill (o2[i], i) END;
    Fill (local, 0);
    row := o^;
    grid := o2^;
    grid[1] := o2[0];
    m[0] := row;
    m[1] := grid[0];
    grid := m^;
    rec.a := o^;
    row := Copy (o^);
    Copy2 (o2^, g);
    row := SUBARRAY (local, 2, 4);
    row := SUBARRAY (local, start, 4);
    row := SUBARRAY (o^, 0, 4);
    grid := SUBARRAY (m^, 0, 2);
    row := Result (o^);
    g := Result2 (o2^);
    TRY Raise (o^) EXCEPT Wrong (r) => row := r END;
    Designators (local);
    RETURN Values (local) + Sum (row) + Sum (rec.a) + g[1][2];
  END Run;

BEGIN
END ArrayAssign.
