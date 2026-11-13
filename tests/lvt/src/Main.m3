(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The program of tools/lvt_test.py: a debugger stops at each line marked
   "probe" and lists the variables it sees there, with their values. *)

MODULE Main;

IMPORT IO, Fmt, Text;

TYPE
  Color = {Red, Green, Blue};
  Rec = RECORD a: INTEGER; b: TEXT END;
  Node = OBJECT val: INTEGER END;

EXCEPTION Fail (INTEGER);

PROCEDURE Inc (VAR x: INTEGER; READONLY r: Rec; n: INTEGER) =
  BEGIN
    x := x + n + r.a; (* probe *)
  END Inc;

PROCEDURE Sum (READONLY a: ARRAY OF INTEGER; b: ARRAY OF INTEGER): INTEGER =
  VAR s := 0;
  BEGIN
    FOR i := FIRST (a) TO LAST (a) DO
      s := s + a[i] * b[i]; (* probe *)
    END;
    RETURN s; (* probe *)
  END Sum;

PROCEDURE Outer (k: INTEGER): INTEGER =
  VAR captured := k * 2;

  PROCEDURE Inner (m: INTEGER): INTEGER =
    BEGIN
      RETURN captured + m; (* probe *)
    END Inner;

  BEGIN
    RETURN Inner (1); (* probe *)
  END Outer;

PROCEDURE Scalars () =
  VAR
    i := 7;
    c := 'x';
    b := TRUE;
    r := 1.5;
    col := Color.Green;
    t := "text";
    rec := Rec {3, "three"};
    arr := ARRAY [0 .. 2] OF INTEGER {1, 2, 3};
    cell := 10;
  BEGIN
    Inc (cell, rec, i);
    IO.Put (Fmt.Int (cell) & "\n"); (* probe *)
    VAR inner := i + 1;
    BEGIN
      IO.Put (Fmt.Int (inner) & "\n"); (* probe *)
    END;
    WITH w = i * 3, alias = arr, j = i DO
      IO.Put (Fmt.Int (w + alias[1] + j) & "\n"); (* probe *)
    END;
    IO.Put (t & Fmt.Bool (b) & Fmt.Real (r) & "\n"); (* probe *)
    IO.Put (Fmt.Char (c) & Fmt.Int (ORD (col)) & "\n");
  END Scalars;

PROCEDURE Cases (o: REFANY) =
  BEGIN
    TYPECASE o OF
    | Node (n) =>
        IO.Put (Fmt.Int (n.val) & "\n"); (* probe *)
    ELSE
    END;
    TRY
      RAISE Fail (42);
    EXCEPT
    | Fail (code) =>
        IO.Put (Fmt.Int (code) & "\n"); (* probe *)
    END;
    IO.Put ("done\n"); (* probe *)
  END Cases;

(* The FOR variable is only in code that cannot be reached, which the
   class file leaves out of max_locals: it must not be named. *)
PROCEDURE Dead (t: TEXT): INTEGER =
  VAR n := 0;
  BEGIN
    IF TRUE THEN RETURN n + 1 END; (* probe *)
    FOR k := 0 TO Text.Length (t) DO INC (n, k) END;
    RETURN n;
  END Dead;

VAR nums := ARRAY [0 .. 1] OF INTEGER {2, 3};

BEGIN
  Scalars ();
  IO.Put (Fmt.Int (Sum (nums, nums)) & "\n");
  IO.Put (Fmt.Int (Outer (5)) & "\n");
  Cases (NEW (Node, val := 9));
  IO.Put (Fmt.Int (Dead ("abc")) & "\n");
END Main.
