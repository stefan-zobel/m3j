MODULE Main;

IMPORT IO, Fmt, Params, Greet, WordTbl, NumSeq, NameList, IntPair, SortedRankTbl;

VAR
  words := NEW (WordTbl.Default).init ();
  nums  := NEW (NumSeq.T).init ();
  names := NameList.List3 ("ann", "bob", "cy");
  ranks := NEW (SortedRankTbl.Default).init ();
  p     := IntPair.T {1, 2};
  n     : INTEGER;
  k     : INTEGER;
  v     : TEXT;
  it    : SortedRankTbl.Iterator;
BEGIN
  IF Params.Count > 1 THEN
    IO.Put (Greet.Hello (Params.Get (1)) & "\n");
  ELSE
    IO.Put (Greet.Hello ("world") & "\n");
  END;
  EVAL words.put ("one", 1);
  EVAL words.put ("two", 2);
  IF words.get ("two", n) THEN IO.Put ("two = " & Fmt.Int (n) & "\n") END;
  FOR i := 1 TO 5 DO nums.addhi (i * i) END;
  n := 0;
  FOR i := 0 TO nums.size () - 1 DO INC (n, nums.get (i)) END;
  IO.Put ("sum of squares = " & Fmt.Int (n) & "\n");
  IO.Put ("names: " & Fmt.Int (NameList.Length (names)) & ", last "
          & NameList.Nth (names, 2) & "\n");
  EVAL ranks.put (3, "third");
  EVAL ranks.put (1, "first");
  EVAL ranks.put (2, "second");
  it := ranks.iterateOrdered ();
  WHILE it.next (k, v) DO IO.Put (Fmt.Int (k) & " " & v & "\n") END;
  p := IntPair.Swap (p);
  IO.Put ("swapped: " & Fmt.Int (p.a) & " " & Fmt.Int (p.b) & "\n");
END Main.
