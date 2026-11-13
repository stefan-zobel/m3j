MODULE Main EXPORTS Main;

IMPORT Shape, IO, TextSeq, Fmt AS Format;
FROM Shape IMPORT Kind;

TYPE Points = ARRAY [0 .. 2] OF Shape.Point;

EXCEPTION Failed(INTEGER);

VAR
  sh := Shape.Make(Kind.Circle);
  p: Shape.Point;
  pr := NEW(Shape.PointRef);
  a: Points;
  count := 0;
  names := NEW(TextSeq.T).init();

PROCEDURE Report (s: Shape.T; label: TEXT) =
  CONST Prefix = "shape ";
  VAR total := 0;

  PROCEDURE Line (t: TEXT) =
    BEGIN
      IO.Put(Prefix & t & "\n");
    END Line;

  BEGIN
    WITH o = s.origin DO
      total := o.x + o.y;
    END;
    FOR i := 0 TO 2 DO
      INC(total, a[i].x);
    END;
    Line(label & Format.Int(total));
  END Report;

PROCEDURE Check (r: REFANY) =
  BEGIN
    TYPECASE r OF
    | Shape.T (t) => IO.Put(t.describe());
    | Shape.PointRef (q) => IO.Put(Format.Int(q.x));
    ELSE
    END;
    TRY
      RAISE Failed(1);
    EXCEPT
    | Failed (code) => INC(count, code);
    END;
  END Check;

BEGIN
  p := sh.origin;
  pr.y := p.x;
  a[0].x := count;
  count := sh.origin.y;
  sh.move(1, 2);
  EVAL Shape.Make(Shape.Kind.Square).area();
  Report(sh, "at ");
  Check(sh);
  names.addhi("done");
END Main.
