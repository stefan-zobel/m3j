MODULE Main;

IMPORT Stack AS S, TextSeq, IO;
FROM Stack IMPORT Max, Top;

CONST Limit = Max;

VAR
  s := S.New();
  names := NEW(TextSeq.T).init();

(* S. in a comment *)

PROCEDURE Fill (n: INTEGER) =
  BEGIN
    FOR i := 1 TO n DO s.push(i); END;
    names.addhi("S.");
  END Fill;

PROCEDURE Report () =
  BEGIN
    TRY
      IO.PutInt(Top(s));
    EXCEPT
    | S.Empty =>
    END;
  END Report;

BEGIN
  Fill(Limit);
  Report();
END Main.
