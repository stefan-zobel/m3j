(* Binary trees: allocation of many small records. *)
MODULE Main;
IMPORT IO, Fmt;

TYPE Node = REF RECORD left, right: Node END;

PROCEDURE Make (d: INTEGER): Node =
  BEGIN
    IF d = 0 THEN RETURN NEW (Node) END;
    RETURN NEW (Node, left := Make (d - 1), right := Make (d - 1));
  END Make;

PROCEDURE Check (n: Node): INTEGER =
  BEGIN
    IF n.left = NIL THEN RETURN 1 END;
    RETURN 1 + Check (n.left) + Check (n.right);
  END Check;

CONST MaxDepth = 20;

VAR long := Make (MaxDepth);  sum, iters: INTEGER;
BEGIN
  FOR d := 4 TO MaxDepth BY 2 DO
    iters := 1;
    FOR k := 1 TO MaxDepth - d + 4 DO iters := iters * 2 END;
    sum := 0;
    FOR i := 1 TO iters DO INC (sum, Check (Make (d))) END;
    IO.Put (Fmt.Int (iters) & " trees of depth " & Fmt.Int (d) & ": " & Fmt.Int (sum) & "\n");
  END;
  IO.Put ("long lived: " & Fmt.Int (Check (long)) & "\n");
END Main.
