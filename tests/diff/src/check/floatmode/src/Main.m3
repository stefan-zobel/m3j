(* FloatMode as its specification describes it: the procedures that test
   the modes answer, and those that set them raise Failure for what the
   implementation cannot do. Floating-point operations and integer
   overflow set no flag, and integer DIV by zero is a checked runtime
   error. cm3 on AMD64_NT fails an assertion in every procedure but
   GetRounding, so the output is compared with a file checked by hand. *)

MODULE Main;

IMPORT FloatMode, Fmt, IO, RuntimeError, Thread;
FROM FloatMode IMPORT Flag, Behavior, RoundingMode;

CONST
  FlagName = ARRAY Flag OF TEXT {"Invalid", "Inexact", "Overflow",
    "Underflow", "DivByZero", "IntOverflow", "IntDivByZero"};
  BehaviorName = ARRAY Behavior OF TEXT {"Trap", "SetFlag", "Ignore"};
  RoundingName = ARRAY RoundingMode OF TEXT {"NearestElseEven",
    "TowardMinusInfinity", "TowardPlusInfinity", "TowardZero",
    "NearestElseAwayFromZero", "IBM370", "Other"};

PROCEDURE Flags (s: SET OF Flag): TEXT =
  VAR r := "{"; first := TRUE;
  BEGIN
    FOR f := FIRST (Flag) TO LAST (Flag) DO
      IF f IN s THEN
        IF NOT first THEN r := r & " " END;
        r := r & FlagName[f];
        first := FALSE;
      END;
    END;
    RETURN r & "}";
  END Flags;

PROCEDURE Rounding () =
  BEGIN
    IO.Put ("rounding " & RoundingName[FloatMode.GetRounding ()] & "\n");
    FOR m := FIRST (RoundingMode) TO LAST (RoundingMode) DO
      TRY
        FloatMode.SetRounding (m);
        IO.Put ("  set " & RoundingName[m] & " ok\n");
      EXCEPT
        FloatMode.Failure => IO.Put ("  set " & RoundingName[m] & " Failure\n");
      END;
    END;
    IO.Put ("rounding " & RoundingName[FloatMode.GetRounding ()] & "\n");
  END Rounding;

PROCEDURE Flagset () =
  VAR old: SET OF Flag;
  BEGIN
    IO.Put ("flags " & Flags (FloatMode.GetFlags ()) & "\n");
    TRY
      old := FloatMode.SetFlags (FloatMode.NoFlags);
      IO.Put ("  set {} ok, old " & Flags (old) & "\n");
    EXCEPT
      FloatMode.Failure => IO.Put ("  set {} Failure\n");
    END;
    FOR f := FIRST (Flag) TO LAST (Flag) DO
      TRY
        old := FloatMode.SetFlags (SET OF Flag {f});
        IO.Put ("  set " & Flags (SET OF Flag {f}) & " ok, old "
                  & Flags (old) & "\n");
      EXCEPT
        FloatMode.Failure =>
          IO.Put ("  set " & Flags (SET OF Flag {f}) & " Failure\n");
      END;
      FloatMode.ClearFlag (f);
    END;
    IO.Put ("flags " & Flags (FloatMode.GetFlags ()) & "\n");
  END Flagset;

PROCEDURE Behaviors () =
  BEGIN
    FOR f := FIRST (Flag) TO LAST (Flag) DO
      IO.Put (FlagName[f] & " " & BehaviorName[FloatMode.GetBehavior (f)]);
      FOR b := FIRST (Behavior) TO LAST (Behavior) DO
        TRY
          FloatMode.SetBehavior (f, b);
          IO.Put (", set " & BehaviorName[b] & " ok");
        EXCEPT
          FloatMode.Failure => IO.Put (", set " & BehaviorName[b] & " Failure");
        END;
      END;
      IO.Put ("\n");
    END;
  END Behaviors;

VAR
  zero := 0.0D0;
  big := 1.0D300;
  izero := 0;
  imax := LAST (INTEGER);

PROCEDURE Conditions () =
  VAR x: LONGREAL; i: INTEGER;
  BEGIN
    x := 1.0D0 / zero;           (* DivByZero *)
    x := zero / zero;            (* Invalid *)
    x := big * big;              (* Overflow *)
    x := 1.0D0 / big / big;      (* Underflow *)
    x := 1.0D0 / 3.0D0;          (* Inexact *)
    i := imax + 1;               (* IntOverflow *)
    IO.Put ("after conditions: flags " & Flags (FloatMode.GetFlags ())
              & ", x " & Fmt.LongReal (x) & ", i = FIRST "
              & Fmt.Bool (i = FIRST (INTEGER)) & "\n");
    TRY
      i := 1 DIV izero;
      IO.Put ("DIV by zero: " & Fmt.Int (i) & "\n");
    EXCEPT
    | RuntimeError.E (e) =>
        IO.Put ("DIV by zero: RuntimeError " & RuntimeError.Tag (e) & "\n");
    END;
    IO.Put ("flags " & Flags (FloatMode.GetFlags ()) & "\n");
  END Conditions;

PROCEDURE Forked (<*UNUSED*> cl: Thread.Closure): REFANY =
  BEGIN
    IO.Put ("thread: rounding " & RoundingName[FloatMode.GetRounding ()]
              & ", flags " & Flags (FloatMode.GetFlags ()) & ", "
              & BehaviorName[FloatMode.GetBehavior (Flag.Overflow)] & "\n");
    RETURN NIL;
  END Forked;

BEGIN
  IO.Put ("IEEE " & Fmt.Bool (FloatMode.IEEE) & ", default "
            & RoundingName[FloatMode.RoundDefault] & "\n");
  Rounding ();
  Flagset ();
  Behaviors ();
  Conditions ();
  EVAL Thread.Join (Thread.Fork (NEW (Thread.Closure, apply := Forked)));
END Main.
