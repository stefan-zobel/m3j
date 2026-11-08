MODULE Main;

IMPORT IO, Fmt, Wr, Stdio, TextIntTbl, Shapes, Util;

CONST Greeting = "hello";

VAR
  p: Util.Point;
  total: INTEGER := 0;
  table := NEW (TextIntTbl.Default).init ();
  circle := Shapes.NewCircle ("disk", 2.0);

PROCEDURE Show (w: Wr.T; n: INTEGER) =
  BEGIN
    IO.Put (Fmt.Int (n) & "\n", w);
  END Show;

BEGIN
  p.x := Util.Limit;
  total := Util.Add (p.x, 1);
  EVAL table.put (Greeting, total);
  Show (Stdio.stdout, total);
  IO.PutReal (circle.area ());
END Main.
