(* The identity of threads: ThreadF.MyId gives a thread the same id each
   time, an id greater than zero, and threads that are alive at the same
   time have different ids. The ids themselves differ from run to run and
   between systems, so the output has checks only. *)

MODULE Main;

IMPORT Fmt, IO, Thread, ThreadF;

TYPE
  Closure = Thread.Closure OBJECT
    n: CARDINAL;
  OVERRIDES
    apply := Run;
  END;

VAR
  mu := NEW (MUTEX);
  changed := NEW (Thread.Condition);
  ids: ARRAY [0 .. 2] OF ThreadF.Id;
  stable: ARRAY [0 .. 2] OF BOOLEAN;
  recorded := 0;
  done := FALSE;

PROCEDURE Record (n: CARDINAL) =
  VAR id := ThreadF.MyId ();
  BEGIN
    LOCK mu DO
      ids[n] := id;
      stable[n] := ThreadF.MyId () = id;
      INC (recorded);
      Thread.Broadcast (changed);
    END;
  END Record;

PROCEDURE Run (cl: Closure): REFANY =
  BEGIN
    Record (cl.n);
    (* stay alive until the main thread has seen all three ids *)
    LOCK mu DO
      WHILE NOT done DO Thread.Wait (mu, changed) END;
    END;
    RETURN NIL;
  END Run;

PROCEDURE Check (what: TEXT; ok: BOOLEAN) =
  BEGIN
    IO.Put (what & ": " & Fmt.Bool (ok) & "\n");
  END Check;

VAR t1, t2: Thread.T;

BEGIN
  Record (0);
  t1 := Thread.Fork (NEW (Closure, n := 1));
  t2 := Thread.Fork (NEW (Closure, n := 2));
  LOCK mu DO
    WHILE recorded < 3 DO Thread.Wait (mu, changed) END;
    done := TRUE;
    Thread.Broadcast (changed);
  END;
  EVAL Thread.Join (t1);
  EVAL Thread.Join (t2);

  FOR i := 0 TO 2 DO
    Check ("thread " & Fmt.Int (i) & " gets the same id twice", stable[i]);
    Check ("thread " & Fmt.Int (i) & " has an id greater than 0", ids[i] > 0);
  END;
  Check ("the three ids differ",
         ids[0] # ids[1] AND ids[0] # ids[2] AND ids[1] # ids[2]);
  Check ("the main thread keeps its id", ThreadF.MyId () = ids[0]);
  IO.Put ("states: " & Fmt.Int (NUMBER (ThreadF.State)) & ", the last is "
            & Fmt.Int (ORD (LAST (ThreadF.State))) & "\n");
  Check ("dead is the last state", LAST (ThreadF.State) = ThreadF.State.dead);
END Main.
