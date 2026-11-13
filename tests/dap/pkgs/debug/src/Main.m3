(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The program of tools/dap_test.py: variables of every kind of type, in
   every kind of home (a slot, the frame of a procedure, a cell, a
   variable of the module), the statements that bind names, a procedure
   big enough to be split in chunks (with -Dm3j.maxMethod), a generic
   instance, a second thread and exceptions. The lines that the tests
   stop at are marked with a comment of the form "@name". With the
   argument "raise" the program ends with an exception that nobody
   handles, with "fail" with a runtime error. *)

MODULE Main;

IMPORT IO, Fmt, Params, Text, Thread, RuntimeError, Shapes, IntStack;

TYPE
  Color = {Red, Green, Blue};
  Small = [1 .. 10];
  Letter = ['a' .. 'z'];
  Palette = SET OF Color;
  Digits = SET OF [0 .. 99];
  Grid = ARRAY [1 .. 2], Color OF INTEGER;
  Point = RECORD x, y: INTEGER END;
  List = REF RECORD head: INTEGER; tail: List END;
  Counter = OBJECT
    n: INTEGER;
  METHODS
    inc () := Inc;
  END;
  Named = Counter OBJECT name: TEXT END;
  Fun = PROCEDURE (x: INTEGER): INTEGER;
  Worker = Thread.Closure OBJECT steps: INTEGER OVERRIDES apply := Work END;

EXCEPTION Oops (INTEGER);

VAR
  count: INTEGER := 0;
  color := Color.Green;
  palette := Palette {Color.Red, Color.Blue};
  digits := Digits {1, 3, 64, 99};
  greeting: TEXT := "hello";
  origin := Point {0, 0};
  grid: Grid;
  list: List := NIL;
  counter: Counter := NIL;
  fun: Fun := Twice;
  ratio: REAL := 0.5;
  precise: LONGREAL := 0.25D0;
  big: LONGINT := 10000000000L;
  initial: CHAR := 'M';
  wide: WIDECHAR := W'\x20AC';
  done := FALSE;
  small: Small := 3;
  letter: Letter := 'q';

PROCEDURE Inc (self: Counter) =
  BEGIN
    INC (self.n); (* @inc *)
  END Inc;

PROCEDURE Twice (x: INTEGER): INTEGER =
  BEGIN
    RETURN 2 * x;
  END Twice;

(* formals of every mode, and locals of the scalar types *)
PROCEDURE Scalars (VAR v: INTEGER; READONLY p: Point; a: ARRAY OF INTEGER;
                   VAR chars: ARRAY OF CHAR) =
  VAR
    i: INTEGER := 7;
    n: CARDINAL := 8;
    l: LONGINT := -9L;
    b := TRUE;
    c := 'x';
    w: WIDECHAR := W'y';
    r: REAL := 1.5;
    d: LONGREAL := 2.5D0;
    e: EXTENDED := 3.5X0;
    col := Color.Blue;
    s: Small := 4;
    t: TEXT := "text";
    nil: REFANY := NIL;
  BEGIN
    v := v + i + p.x + a[0] + ORD (chars[0]); (* @scalars *)
    IF b AND c = 'x' AND w = W'y' AND col = Color.Blue AND nil = NIL THEN
      v := v + n + s + VAL (l, INTEGER) + TRUNC (r + FLOAT (d, REAL) + FLOAT (e, REAL)) + ORD (t = NIL);
    END;
  END Scalars;

(* values of the structured types *)
PROCEDURE Structured () =
  VAR
    pt := Point {3, 4};
    g: Grid;
    pal := Palette {Color.Green};
    dig := Digits {0, 70};
    lst := NEW (List, head := 1, tail := NEW (List, head := 2, tail := NIL));
    named := NEW (Named, n := 5, name := "five");
    shape := Shapes.Circle (2.0);
    any: REFANY := named;
    f := fun;
    open := NEW (REF ARRAY OF INTEGER, 3);
    stack := IntStack.New ();
  BEGIN
    FOR k := 1 TO 2 DO
      FOR c := FIRST (Color) TO LAST (Color) DO
        g[k, c] := 10 * k + ORD (c);
      END;
    END;
    open[0] := 7;
    IntStack.Push (stack, 42);
    named.inc ();
    count := count + pt.x + g[2, Color.Blue] + f (1) + open[0]; (* @structured *)
    IF pal = Palette {} OR dig = Digits {} OR lst = NIL OR shape = NIL OR any = NIL THEN
      count := 0;
    END;
    EVAL Shapes.Square (1.0).area () + shape.area ();
  END Structured;

(* variables of an enclosing procedure, in its frame *)
PROCEDURE Outer (k: INTEGER): INTEGER =
  VAR captured := k * 2; plain := 1;

  PROCEDURE Inner (m: INTEGER): INTEGER =
    BEGIN
      captured := captured + m; (* @inner *)
      RETURN captured;
    END Inner;

  BEGIN
    RETURN Inner (plain) + plain;
  END Outer;

(* the statements that bind names *)
PROCEDURE Bindings (): INTEGER =
  VAR sum := 0; any: REFANY := NEW (List, head := 9, tail := NIL);
  BEGIN
    WITH p = origin, q = origin.x + 1 DO
      sum := sum + p.y + q; (* @with *)
    END;
    FOR i := 1 TO 3 DO
      sum := sum + i; (* @for *)
    END;
    TYPECASE any OF
    | NULL => sum := 0;
    | List (l) =>
        sum := sum + l.head; (* @typecase *)
    ELSE
    END;
    TRY
      RAISE Oops (sum);
    EXCEPT
    | Oops (code) =>
        sum := sum + code; (* @except *)
    END;
    TRY
      IF sum > 0 THEN RETURN sum END;
    FINALLY
      INC (count); (* @finally *)
    END;
    RETURN 0;
  END Bindings;

(* split in chunks when it is compiled with -Dm3j.maxMethod=300 *)
PROCEDURE Big (n: INTEGER): INTEGER =
  VAR a := n; b := 2 * n; c := 3 * n; text := "big";
  BEGIN
    a := a + b; (* @big *)
    IF a > c THEN b := b + 1 ELSE c := c + 1 END;
    IF a > b THEN c := c + a ELSE a := a + c END;
    IF Fmt.Int (a) # text THEN a := a + 1 END;
    b := b * 2 + c;
    c := c * 2 + a;
    IF a > c THEN b := b + 1 ELSE c := c + 1 END;
    IF a > b THEN c := c + a ELSE a := a + c END;
    IF Fmt.Int (b) # text THEN b := b + 1 END;
    a := a + b + c; (* @bigmiddle *)
    IF a > c THEN b := b + 1 ELSE c := c + 1 END;
    IF a > b THEN c := c + a ELSE a := a + c END;
    IF Fmt.Int (c) # text THEN c := c + 1 END;
    b := b * 3 + c;
    c := c * 3 + a;
    IF a > c THEN b := b + 1 ELSE c := c + 1 END;
    IF a > b THEN c := c + a ELSE a := a + c END;
    RETURN a + b + c; (* @bigend *)
  END Big;

PROCEDURE Work (self: Worker): REFANY =
  VAR total := 0;
  BEGIN
    FOR i := 1 TO self.steps DO
      total := total + i; (* @work *)
    END;
    RETURN NIL;
  END Work;

(* the variables of the module that nothing else uses *)
PROCEDURE Others (): INTEGER =
  VAR n := 0;
  BEGIN
    IF color IN palette AND 64 IN digits AND list = NIL AND grid[1, Color.Red] = 0 THEN INC (n) END;
    IF ratio < 1.0 AND precise < 1.0D0 AND big > 0L AND ORD (initial) # ORD (wide) AND small < ORD (letter) THEN INC (n) END;
    RETURN n + Text.Length (greeting);
  END Others;

PROCEDURE Fail (READONLY a: ARRAY OF INTEGER; i: INTEGER): INTEGER =
  BEGIN
    RETURN a[i]; (* @fail *)
  END Fail;

VAR
  v := 1;
  chars := ARRAY [0 .. 2] OF CHAR {'a', 'b', 'c'};
  thread: Thread.T;
BEGIN
  counter := NEW (Counter, n := 0);
  counter.inc ();
  Scalars (v, origin, ARRAY OF INTEGER {5, 6}, chars);
  Structured ();
  v := v + Outer (3) + Bindings () + Big (4);
  thread := Thread.Fork (NEW (Worker, steps := 3));
  EVAL Thread.Join (thread);
  TRY
    v := v + Fail (ARRAY OF INTEGER {1}, 1);
  EXCEPT
  | RuntimeError.E => v := v + 1;
  END;
  IF Params.Count > 1 AND Text.Equal (Params.Get (1), "raise") THEN
    RAISE Oops (v); (* @raise *)
  ELSIF Params.Count > 1 AND Text.Equal (Params.Get (1), "fail") THEN
    v := v + Fail (ARRAY OF INTEGER {1}, 2);
  END;
  v := v + Others ();
  done := TRUE;
  IO.Put ("v = " & Fmt.Int (v) & ", count = " & Fmt.Int (count) & ", shapes = " & Fmt.Int (Shapes.made) & "\n");
END Main.
