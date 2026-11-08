(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The training program of the AOT cache that build.sh makes: it uses the
   common parts of the language and of the library, so that the classes of
   the compiler and of the runtime that a typical program needs are in the
   cache. *)

MODULE Main;
IMPORT IO, Fmt, Text, Wr, Stdio, Thread, IntIntTbl, Params;

TYPE
  Shape = OBJECT METHODS area (): INTEGER END;
  Rect = Shape OBJECT w, h: INTEGER OVERRIDES area := RectArea END;
  Pt = RECORD x, y: INTEGER END;
  Set = SET OF [0 .. 99];

EXCEPTION Odd (INTEGER);

PROCEDURE RectArea (r: Rect): INTEGER =
  BEGIN
    RETURN r.w * r.h;
  END RectArea;

PROCEDURE Check (i: INTEGER) RAISES {Odd} =
  BEGIN
    IF i MOD 2 = 1 THEN RAISE Odd (i) END;
  END Check;

PROCEDURE Sum (READONLY a: ARRAY OF Pt; VAR n: INTEGER): INTEGER =
  VAR s := 0;
  BEGIN
    FOR i := FIRST (a) TO LAST (a) DO INC (s, a[i].x + a[i].y) END;
    n := NUMBER (a);
    RETURN s;
  END Sum;

VAR
  pts := NEW (REF ARRAY OF Pt, 100);
  tbl := NEW (IntIntTbl.Default).init ();
  s := Set {1, 3 .. 7};
  t := "";
  n, v, total := 0;
  mu := NEW (MUTEX);
  sh: Shape := NEW (Rect, w := 3, h := 4);

BEGIN
  FOR i := 0 TO LAST (pts^) DO pts[i] := Pt {i, 2 * i} END;
  INC (total, Sum (pts^, n) + n);
  FOR i := 0 TO 99 DO EVAL tbl.put (i, i * i) END;
  IF tbl.get (7, v) THEN INC (total, v) END;
  FOR i := 0 TO 99 DO
    TRY Check (i) EXCEPT Odd (k) => INC (total, k) END;
    IF i IN s THEN INC (total) END;
  END;
  LOCK mu DO INC (total, sh.area ()) END;
  FOR i := 1 TO 20 DO t := t & Fmt.Int (i) & "," END;
  INC (total, Text.Length (t) + Params.Count);
  IO.Put (Fmt.Int (total) & " " & Fmt.Real (1.5) & " " & Fmt.LongReal (FLOAT (total, LONGREAL) / 3.0d0) & "\n");
  TRY Wr.Flush (Stdio.stdout) EXCEPT Wr.Failure, Thread.Alerted => END;
END Main.
