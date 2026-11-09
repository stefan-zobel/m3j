MODULE Accepted;

(* Legal forms, none of which may get a message. *)

IMPORT Long, Word;

EXCEPTION Not_Caught_1;

CONST
  Ones = Long.Not (0L);
  High = Long.Shift (1L, 40);
  Field = Long.Extract (Ones, 8, 16);
  Merged = Long.Insert (0L, Ones, 4, 4);
  Below = Long.LT (0L, Ones);
  WordOnes = Word.Not (0);

TYPE
  Base = OBJECT METHODS get (): INTEGER; END;
  Bare = Base OBJECT METHODS END;
  Both = Base OBJECT METHODS OVERRIDES get := Get; END;
  Only = Base OBJECT OVERRIDES get := Get; END;
  Nothing = Base OBJECT METHODS OVERRIDES END;

PROCEDURE Get (<*UNUSED*> self: Base): INTEGER =
  BEGIN
    RETURN 0;
  END Get;

PROCEDURE Fail () =
  <*FATAL Not_Caught_1*>
  BEGIN
    RAISE Not_Caught_1;
  END Fail;

PROCEDURE Join (r: REFANY; t: TEXT): TEXT =
  BEGIN
    RETURN r & t & r & "end";
  END Join;

VAR
  sum := Ones + High + Field + Merged;
  n := WordOnes;
  o1 := NEW (Bare);
  o2 := NEW (Both);
  o3 := NEW (Only);
  o4 := NEW (Nothing);

BEGIN
  IF Below AND sum # 0L AND n # 0 AND o1 # NIL AND o2 # NIL AND o3 # NIL
       AND o4 # NIL AND Join (NIL, "") # NIL THEN
    Fail ();
  END;
END Accepted.
