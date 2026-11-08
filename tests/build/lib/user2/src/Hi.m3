MODULE Hi;

IMPORT HiSeq, WordSeq, Greet;

PROCEDURE Words (): TEXT =
  VAR a := NEW (HiSeq.T).init ();  b := NEW (WordSeq.T).init ();  r := "";
  BEGIN
    a.addhi ("library");
    b.addhi ("template");
    FOR i := 0 TO a.size () - 1 DO r := r & Greet.Hello (a.get (i)) & "\n" END;
    FOR i := 0 TO b.size () - 1 DO r := r & Greet.Hello (b.get (i)) & "\n" END;
    RETURN r;
  END Words;

BEGIN
END Hi.
