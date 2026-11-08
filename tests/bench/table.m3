(* The generic hash table of libm3 (IntIntTbl). *)
MODULE Main;
IMPORT IO, Fmt, IntIntTbl;

CONST N = 1000000;

VAR tbl: IntIntTbl.T;  v, sum := 0;

BEGIN
  FOR r := 1 TO 5 DO
    tbl := NEW (IntIntTbl.Default).init ();
    FOR i := 0 TO N - 1 DO EVAL tbl.put (i * 7919 MOD 10000019, i) END;
    FOR i := 0 TO 2 * N - 1 DO
      IF tbl.get (i * 7919 MOD 10000019, v) THEN INC (sum, v) END;
    END;
    FOR i := 0 TO N - 1 BY 2 DO EVAL tbl.delete (i * 7919 MOD 10000019, v) END;
    INC (sum, tbl.size ());
  END;
  IO.Put (Fmt.Int (sum) & "\n");
END Main.
