(* Threads under load: the first fork while the main thread holds a mutex,
   counters and transfers under contended mutexes, a bounded buffer and a
   barrier on conditions, exits from LOCK in every way, strict turns
   between threads, alerts (pending, during a wait, a pause or a join, and
   alerts that arrive while the thread tests for them), many short
   threads, typecodes, atoms and a writer used by many threads at once,
   and deep recursion in a forked thread.

   The output does not depend on the order in which the threads run: it
   has counts, sums and checks only. The argument, a positive number
   (default 1), scales the amount of work. *)

MODULE Main;

IMPORT Atom, Fmt, FloatMode, IO, Lex, Params, Scan, Stdio, Text, TextWr,
       Thread, Word, Wr;

VAR scale := 1;

PROCEDURE Out (t: TEXT) =
  <* FATAL Wr.Failure, Thread.Alerted *>
  BEGIN
    IO.Put (t & "\n");
    Wr.Flush (Stdio.stdout);
  END Out;

PROCEDURE Check (name: TEXT; ok: BOOLEAN; detail: TEXT) =
  BEGIN
    IF ok THEN
      Out (name & ": ok " & detail);
    ELSE
      Out (name & ": FAILED " & detail);
    END;
  END Check;

PROCEDURE B (b: BOOLEAN): TEXT =
  BEGIN
    IF b THEN RETURN "T" ELSE RETURN "F" END;
  END B;

(* A pseudo-random number in [0, 2^31) from a state of a thread. *)
PROCEDURE Next (VAR s: Word.T): INTEGER =
  BEGIN
    s := Word.Plus (Word.Times (s, 6364136223846793005), 1442695040888963407);
    RETURN Word.RightShift (s, 33);
  END Next;

(* A gate that threads wait at until it is opened. *)
TYPE
  Gate = OBJECT
    mu  : MUTEX;
    c   : Thread.Condition;
    open                   := FALSE;
  END;

PROCEDURE NewGate (): Gate =
  BEGIN
    RETURN NEW (Gate, mu := NEW (MUTEX), c := NEW (Thread.Condition));
  END NewGate;

PROCEDURE Pass (g: Gate) =
  BEGIN
    LOCK g.mu DO
      WHILE NOT g.open DO Thread.Wait (g.mu, g.c); END;
    END;
  END Pass;

PROCEDURE Open (g: Gate) =
  BEGIN
    LOCK g.mu DO g.open := TRUE; END;
    Thread.Broadcast (g.c);
  END Open;

(* A thread that pauses. *)
TYPE SleeperCl = Thread.Closure OBJECT
    secs: LONGREAL;
  OVERRIDES
    apply := SleeperApply;
  END;

PROCEDURE SleeperApply (cl: SleeperCl): REFANY =
  BEGIN
    Thread.Pause (cl.secs);
    RETURN NIL;
  END SleeperApply;

(* 1. The first fork of the program while the main thread holds a mutex:
   the new thread must wait for it, then both count under it. *)

TYPE FirstCl = Thread.Closure OBJECT OVERRIDES apply := FirstApply; END;

VAR
  firstMu    := NEW (MUTEX);
  firstCount := 0;

PROCEDURE FirstApply (<*UNUSED*> cl: FirstCl): REFANY =
  BEGIN
    FOR i := 1 TO 10000 * scale DO
      LOCK firstMu DO INC (firstCount); END;
    END;
    RETURN NIL;
  END FirstApply;

PROCEDURE First () =
  VAR
    t   : Thread.T;
    seen: INTEGER;
  BEGIN
    LOCK firstMu DO
      t := Thread.Fork (NEW (FirstCl));
      Thread.Pause (0.05d0);
      seen := firstCount;
    END;
    FOR i := 1 TO 10000 * scale DO
      LOCK firstMu DO INC (firstCount); END;
    END;
    EVAL Thread.Join (t);
    Check ("first fork", seen = 0 AND firstCount = 20000 * scale,
           Fmt.Int (seen) & " " & Fmt.Int (firstCount));
  END First;

(* 2. A counter under one mutex and four counters under a mutex each. *)

TYPE
  CountCl = Thread.Closure OBJECT
    n: INTEGER;
  OVERRIDES
    apply := CountApply;
  END;

VAR
  countMu  := NEW (MUTEX);
  counter  := 0;
  stripeMu : ARRAY [0 .. 3] OF MUTEX;
  stripe   := ARRAY [0 .. 3] OF INTEGER {0, ..};

PROCEDURE CountApply (cl: CountCl): REFANY =
  BEGIN
    FOR i := 1 TO cl.n DO
      LOCK countMu DO INC (counter); END;
      LOCK stripeMu[i MOD 4] DO INC (stripe[i MOD 4]); END;
    END;
    RETURN NIL;
  END CountApply;

PROCEDURE Count () =
  CONST N = 8;
  VAR
    t: ARRAY [1 .. N] OF Thread.T;
    n          := 20000 * scale;
    sum        := 0;
    detail     := Fmt.Int (N) & "x" & Fmt.Int (n) & ":";
  BEGIN
    FOR i := 0 TO 3 DO stripeMu[i] := NEW (MUTEX); END;
    FOR i := 1 TO N DO t[i] := Thread.Fork (NEW (CountCl, n := n)); END;
    FOR i := 1 TO N DO EVAL Thread.Join (t[i]); END;
    detail := detail & " " & Fmt.Int (counter);
    FOR i := 0 TO 3 DO
      INC (sum, stripe[i]);
      detail := detail & " " & Fmt.Int (stripe[i]);
    END;
    Check ("counters", counter = N * n AND sum = N * n, detail);
  END Count;

(* 3. Transfers between accounts, each under the mutexes of both accounts
   taken in order, while an auditor takes all of them and adds up. *)

CONST Accounts = 16;

TYPE
  BankCl = Thread.Closure OBJECT
    seed: Word.T;
    n   : INTEGER;
  OVERRIDES
    apply := BankApply;
  END;
  AuditCl = Thread.Closure OBJECT OVERRIDES apply := AuditApply; END;

VAR
  bankMu  : ARRAY [0 .. Accounts - 1] OF MUTEX;
  balance : ARRAY [0 .. Accounts - 1] OF INTEGER;
  bankBad              := 0;
  bankDone             := FALSE;
  bankFlagMu           := NEW (MUTEX);

PROCEDURE Transfer (VAR from, to: INTEGER; amount: INTEGER) =
  BEGIN
    DEC (from, amount);
    INC (to, amount);
  END Transfer;

PROCEDURE BankApply (cl: BankCl): REFANY =
  VAR a, b, amount: INTEGER;
  BEGIN
    FOR i := 1 TO cl.n DO
      a := Next (cl.seed) MOD Accounts;
      b := Next (cl.seed) MOD Accounts;
      amount := Next (cl.seed) MOD 100;
      IF a # b THEN
        LOCK bankMu[MIN (a, b)] DO
          LOCK bankMu[MAX (a, b)] DO
            Transfer (balance[a], balance[b], amount);
          END;
        END;
      END;
    END;
    RETURN NIL;
  END BankApply;

PROCEDURE AuditApply (<*UNUSED*> cl: AuditCl): REFANY =
  VAR
    sum : INTEGER;
    done         := FALSE;
  BEGIN
    WHILE NOT done DO
      LOCK bankFlagMu DO done := bankDone; END;
      FOR i := 0 TO Accounts - 1 DO Thread.Acquire (bankMu[i]); END;
      sum := 0;
      FOR i := 0 TO Accounts - 1 DO INC (sum, balance[i]); END;
      FOR i := Accounts - 1 TO 0 BY -1 DO Thread.Release (bankMu[i]); END;
      IF sum # Accounts * 1000 THEN INC (bankBad); END;
    END;
    RETURN NIL;
  END AuditApply;

PROCEDURE Bank () =
  CONST N = 6;
  VAR
    t     : ARRAY [1 .. N] OF Thread.T;
    audit : Thread.T;
    sum         := 0;
    detail      := "";
  BEGIN
    FOR i := 0 TO Accounts - 1 DO
      bankMu[i] := NEW (MUTEX);
      balance[i] := 1000;
    END;
    audit := Thread.Fork (NEW (AuditCl));
    FOR i := 1 TO N DO
      t[i] := Thread.Fork (NEW (BankCl, seed := i * 7919, n := 20000 * scale));
    END;
    FOR i := 1 TO N DO EVAL Thread.Join (t[i]); END;
    LOCK bankFlagMu DO bankDone := TRUE; END;
    EVAL Thread.Join (audit);
    FOR i := 0 TO Accounts - 1 DO
      INC (sum, balance[i]);
      detail := detail & " " & Fmt.Int (balance[i]);
    END;
    Check ("bank", sum = Accounts * 1000 AND bankBad = 0,
           Fmt.Int (bankBad) & detail);
  END Bank;

(* 4. A bounded buffer: producers and consumers on two conditions. *)

CONST Slots = 8;

TYPE
  ProdCl = Thread.Closure OBJECT
    id, n: INTEGER;
  OVERRIDES
    apply := ProdApply;
  END;
  ConsCl = Thread.Closure OBJECT
    sum, got := 0;
  OVERRIDES
    apply := ConsApply;
  END;

VAR
  bufMu     := NEW (MUTEX);
  nonEmpty  := NEW (Thread.Condition);
  nonFull   := NEW (Thread.Condition);
  buf       : ARRAY [0 .. Slots - 1] OF INTEGER;
  head      := 0;
  count     := 0;
  producing := 0;

PROCEDURE ProdApply (cl: ProdCl): REFANY =
  BEGIN
    FOR j := 1 TO cl.n DO
      LOCK bufMu DO
        WHILE count = Slots DO Thread.Wait (bufMu, nonFull); END;
        buf[(head + count) MOD Slots] := cl.id * 1000000 + j;
        INC (count);
        Thread.Signal (nonEmpty);
      END;
    END;
    LOCK bufMu DO DEC (producing); END;
    Thread.Broadcast (nonEmpty);
    RETURN NIL;
  END ProdApply;

PROCEDURE ConsApply (cl: ConsCl): REFANY =
  BEGIN
    LOOP
      LOCK bufMu DO
        WHILE count = 0 AND producing > 0 DO
          Thread.Wait (bufMu, nonEmpty);
        END;
        IF count = 0 THEN EXIT; END;
        INC (cl.sum, buf[head]);
        INC (cl.got);
        head := (head + 1) MOD Slots;
        DEC (count);
        Thread.Signal (nonFull);
      END;
    END;
    RETURN NIL;
  END ConsApply;

PROCEDURE Buffer () =
  CONST
    P = 4;
    C = 3;
  VAR
    tp   : ARRAY [1 .. P] OF Thread.T;
    tc   : ARRAY [1 .. C] OF Thread.T;
    cons : ARRAY [1 .. C] OF ConsCl;
    n            := 2500 * scale;
    sum, got     := 0;
    expect       := 0;
  BEGIN
    producing := P;
    FOR i := 1 TO C DO
      cons[i] := NEW (ConsCl);
      tc[i] := Thread.Fork (cons[i]);
    END;
    FOR i := 1 TO P DO tp[i] := Thread.Fork (NEW (ProdCl, id := i, n := n)); END;
    FOR i := 1 TO P DO EVAL Thread.Join (tp[i]); END;
    FOR i := 1 TO C DO
      EVAL Thread.Join (tc[i]);
      INC (sum, cons[i].sum);
      INC (got, cons[i].got);
    END;
    FOR i := 1 TO P DO
      INC (expect, i * 1000000 * n + n * (n + 1) DIV 2);
    END;
    Check ("buffer", got = P * n AND sum = expect AND count = 0,
           Fmt.Int (got) & " " & Fmt.Int (sum));
  END Buffer;

(* 5. A barrier on a condition with Broadcast, round after round. *)

CONST BarN = 6;

TYPE
  BarCl = Thread.Closure OBJECT
    id, rounds: INTEGER;
  OVERRIDES
    apply := BarApply;
  END;

VAR
  barMu      := NEW (MUTEX);
  barC       := NEW (Thread.Condition);
  barArrived := 0;
  barGen     := 0;
  barBad     := 0;
  barSum     : ARRAY [0 .. 999] OF INTEGER;

PROCEDURE Barrier () =
  VAR gen: INTEGER;
  BEGIN
    LOCK barMu DO
      gen := barGen;
      INC (barArrived);
      IF barArrived = BarN THEN
        barArrived := 0;
        INC (barGen);
        Thread.Broadcast (barC);
      ELSE
        WHILE gen = barGen DO Thread.Wait (barMu, barC); END;
      END;
    END;
  END Barrier;

PROCEDURE BarApply (cl: BarCl): REFANY =
  BEGIN
    FOR r := 0 TO cl.rounds - 1 DO
      LOCK barMu DO INC (barSum[r MOD 1000], cl.id); END;
      Barrier ();
      LOCK barMu DO
        IF barSum[r MOD 1000] # BarN * (BarN + 1) DIV 2 THEN INC (barBad); END;
      END;
      Barrier ();
      LOCK barMu DO barSum[r MOD 1000] := 0; END;
      Barrier ();
    END;
    RETURN NIL;
  END BarApply;

PROCEDURE Bar () =
  VAR
    t     : ARRAY [1 .. BarN] OF Thread.T;
    rounds                                := 100 * scale;
  BEGIN
    FOR i := 0 TO LAST (barSum) DO barSum[i] := 0; END;
    FOR i := 1 TO BarN DO
      t[i] := Thread.Fork (NEW (BarCl, id := i, rounds := rounds));
    END;
    FOR i := 1 TO BarN DO EVAL Thread.Join (t[i]); END;
    Check ("barrier", barBad = 0 AND barGen = 3 * rounds,
           Fmt.Int (barBad) & " " & Fmt.Int (barGen));
  END Bar;

(* 6. Leaving LOCK by an exception, EXIT, RETURN and RETURN through
   FINALLY, in many threads on one mutex. *)

EXCEPTION Boom (INTEGER);

TYPE
  ExCl = Thread.Closure OBJECT
    n  : INTEGER;
    sum         := 0;
  OVERRIDES
    apply := ExApply;
  END;

VAR
  exMu    := NEW (MUTEX);
  exCount := 0;

PROCEDURE Exiter (i: INTEGER): INTEGER RAISES {Boom} =
  BEGIN
    LOOP
      LOCK exMu DO
        INC (exCount);
        CASE i MOD 4 OF
        | 0 => RAISE Boom (i);
        | 1 => EXIT;
        | 2 => RETURN 2;
        ELSE
          TRY RETURN 3; FINALLY INC (exCount); END;
        END;
      END;
    END;
    RETURN 1;
  END Exiter;

PROCEDURE ExApply (cl: ExCl): REFANY =
  BEGIN
    FOR i := 1 TO cl.n DO
      TRY
        INC (cl.sum, Exiter (i));
      EXCEPT
        Boom (k) => INC (cl.sum, k MOD 7);
      END;
    END;
    RETURN NIL;
  END ExApply;

PROCEDURE Exits () =
  CONST N = 6;
  VAR
    cl : ARRAY [1 .. N] OF ExCl;
    t  : ARRAY [1 .. N] OF Thread.T;
    sum                             := 0;
  BEGIN
    FOR i := 1 TO N DO
      cl[i] := NEW (ExCl, n := 10000 * scale + i);
      t[i] := Thread.Fork (cl[i]);
    END;
    FOR i := 1 TO N DO
      EVAL Thread.Join (t[i]);
      INC (sum, cl[i].sum);
    END;
    LOCK exMu DO END;
    Out ("exits: " & Fmt.Int (sum) & " " & Fmt.Int (exCount));
  END Exits;

(* 7. Strict turns: two threads on one condition with Signal, three on one
   with Broadcast. *)

TYPE
  TurnCl = Thread.Closure OBJECT
    me, ways, n: INTEGER;
    broadcast  : BOOLEAN;
  OVERRIDES
    apply := TurnApply;
  END;

VAR
  turnMu := NEW (MUTEX);
  turnC  := NEW (Thread.Condition);
  turn   := 0;

PROCEDURE TurnApply (cl: TurnCl): REFANY =
  BEGIN
    FOR i := 1 TO cl.n DO
      LOCK turnMu DO
        WHILE turn MOD cl.ways # cl.me DO Thread.Wait (turnMu, turnC); END;
        INC (turn);
        IF cl.broadcast THEN
          Thread.Broadcast (turnC);
        ELSE
          Thread.Signal (turnC);
        END;
      END;
    END;
    RETURN NIL;
  END TurnApply;

PROCEDURE Turns () =
  VAR
    t: ARRAY [0 .. 2] OF Thread.T;
    n                             := 50 * scale;
    r: TEXT;
  BEGIN
    turn := 0;
    FOR i := 0 TO 1 DO
      t[i] := Thread.Fork (NEW (TurnCl, me := i, ways := 2, n := n,
                                broadcast := FALSE));
    END;
    FOR i := 0 TO 1 DO EVAL Thread.Join (t[i]); END;
    r := Fmt.Int (turn);
    turn := 0;
    FOR i := 0 TO 2 DO
      t[i] := Thread.Fork (NEW (TurnCl, me := i, ways := 3, n := n,
                                broadcast := TRUE));
    END;
    FOR i := 0 TO 2 DO EVAL Thread.Join (t[i]); END;
    Out ("turns: " & r & " " & Fmt.Int (turn));
  END Turns;

(* 8. Alerts that a thread gives itself: an alert stays pending until a
   procedure that can be alerted, or TestAlert, takes it. *)

TYPE SelfAlertCl = Thread.Closure OBJECT OVERRIDES apply := SelfAlertApply; END;

PROCEDURE SelfAlertApply (<*UNUSED*> cl: SelfAlertCl): REFANY =
  VAR
    r  := "";
    me := Thread.Self ();
    u : Thread.T;
    mu := NEW (MUTEX);
    c  := NEW (Thread.Condition);
  BEGIN
    (* TestAlert takes it once *)
    Thread.Alert (me);
    r := r & B (Thread.TestAlert ()) & B (Thread.TestAlert ());
    (* Pause leaves it *)
    Thread.Alert (me);
    Thread.Pause (0.02d0);
    r := r & " " & B (Thread.TestAlert ());
    (* AlertPause takes it at once *)
    Thread.Alert (me);
    Thread.Pause (0.02d0);
    TRY
      Thread.AlertPause (10.0d0);
      r := r & " paused";
    EXCEPT
      Thread.Alerted => r := r & " alerted";
    END;
    r := r & B (Thread.TestAlert ());
    (* AlertJoin takes it at once *)
    u := Thread.Fork (NEW (SleeperCl, secs := 1.0d0));
    Thread.Alert (me);
    Thread.Pause (0.02d0);
    TRY
      EVAL Thread.AlertJoin (u);
      r := r & " joined";
    EXCEPT
      Thread.Alerted => r := r & " alerted";
    END;
    r := r & B (Thread.TestAlert ());
    EVAL Thread.Join (u);
    (* AlertWait takes it at once, and returns with the mutex locked *)
    Thread.Alert (me);
    Thread.Pause (0.02d0);
    LOCK mu DO
      TRY
        Thread.AlertWait (mu, c);
        r := r & " woken";
      EXCEPT
        Thread.Alerted => r := r & " alerted";
      END;
    END;
    r := r & B (Thread.TestAlert ());
    (* Join leaves it *)
    u := Thread.Fork (NEW (SleeperCl, secs := 0.02d0));
    Thread.Alert (me);
    EVAL Thread.Join (u);
    r := r & " " & B (Thread.TestAlert ());
    (* a wait on a condition leaves it *)
    u := Thread.Fork (NEW (SignalerCl, mu := mu, c := c));
    Thread.Alert (me);
    LOCK mu DO
      WHILE NOT signaled DO Thread.Wait (mu, c); END;
    END;
    EVAL Thread.Join (u);
    r := r & " " & B (Thread.TestAlert ());
    (* AlertPause of no time *)
    Thread.Alert (me);
    TRY
      Thread.AlertPause (0.0d0);
      r := r & " paused";
    EXCEPT
      Thread.Alerted => r := r & " alerted";
    END;
    TRY
      Thread.AlertPause (0.0d0);
      r := r & " paused";
    EXCEPT
      Thread.Alerted => r := r & " alerted";
    END;
    RETURN r;
  END SelfAlertApply;

TYPE
  SignalerCl = Thread.Closure OBJECT
    mu: MUTEX;
    c : Thread.Condition;
  OVERRIDES
    apply := SignalerApply;
  END;

VAR signaled := FALSE;

PROCEDURE SignalerApply (cl: SignalerCl): REFANY =
  BEGIN
    Thread.Pause (0.05d0);
    LOCK cl.mu DO signaled := TRUE; END;
    Thread.Signal (cl.c);
    RETURN NIL;
  END SignalerApply;

PROCEDURE SelfAlerts () =
  BEGIN
    Out ("self alerts: "
           & NARROW (Thread.Join (Thread.Fork (NEW (SelfAlertCl))), TEXT));
  END SelfAlerts;

(* 9. Alerts of a thread that waits on a condition, pauses, joins a
   thread, or waits for a mutex. *)

TYPE
  WaiterCl = Thread.Closure OBJECT
    mu: MUTEX;
    c : Thread.Condition;
  OVERRIDES
    apply := WaiterApply;
  END;
  PauserCl = Thread.Closure OBJECT OVERRIDES apply := PauserApply; END;
  BlockerCl = Thread.Closure OBJECT
    mu: MUTEX;
    c : Thread.Condition;
    go           := FALSE;
  OVERRIDES
    apply := BlockerApply;
  END;
  JoinerCl = Thread.Closure OBJECT
    other: Thread.T;
  OVERRIDES
    apply := JoinerApply;
  END;
  LockerCl = Thread.Closure OBJECT
    mu: MUTEX;
  OVERRIDES
    apply := LockerApply;
  END;

PROCEDURE WaiterApply (cl: WaiterCl): REFANY =
  VAR r := "woken";
  BEGIN
    LOCK cl.mu DO
      TRY
        LOOP Thread.AlertWait (cl.mu, cl.c); END;
      EXCEPT
        Thread.Alerted => r := "alerted";
      END;
    END;
    RETURN r;
  END WaiterApply;

PROCEDURE PauserApply (<*UNUSED*> cl: PauserCl): REFANY =
  BEGIN
    TRY
      Thread.AlertPause (100.0d0);
      RETURN "paused";
    EXCEPT
      Thread.Alerted => RETURN "alerted";
    END;
  END PauserApply;

PROCEDURE BlockerApply (cl: BlockerCl): REFANY =
  BEGIN
    LOCK cl.mu DO
      WHILE NOT cl.go DO Thread.Wait (cl.mu, cl.c); END;
    END;
    RETURN "blocker";
  END BlockerApply;

PROCEDURE JoinerApply (cl: JoinerCl): REFANY =
  BEGIN
    TRY
      EVAL Thread.AlertJoin (cl.other);
      RETURN "joined";
    EXCEPT
      Thread.Alerted => RETURN "alerted";
    END;
  END JoinerApply;

PROCEDURE LockerApply (cl: LockerCl): REFANY =
  BEGIN
    LOCK cl.mu DO END;
    RETURN "locked " & B (Thread.TestAlert ());
  END LockerApply;

PROCEDURE OtherAlerts () =
  VAR
    mu     := NEW (MUTEX);
    c      := NEW (Thread.Condition);
    w, p, j, l: Thread.T;
    b      : BlockerCl;
    bt     : Thread.T;
    r      : TEXT;
  BEGIN
    w := Thread.Fork (NEW (WaiterCl, mu := mu, c := c));
    p := Thread.Fork (NEW (PauserCl));
    b := NEW (BlockerCl, mu := NEW (MUTEX), c := NEW (Thread.Condition));
    bt := Thread.Fork (b);
    j := Thread.Fork (NEW (JoinerCl, other := bt));
    Thread.Pause (0.05d0);
    (* signals and broadcasts do not end AlertWait's loop *)
    Thread.Signal (c);
    Thread.Broadcast (c);
    Thread.Alert (w);
    Thread.Alert (p);
    Thread.Alert (j);
    r := NARROW (Thread.Join (w), TEXT) & " " & NARROW (Thread.Join (p), TEXT)
           & " " & NARROW (Thread.Join (j), TEXT);
    (* an alert does not end a wait that cannot be alerted *)
    Thread.Alert (bt);
    Thread.Pause (0.02d0);
    LOCK b.mu DO b.go := TRUE; END;
    Thread.Signal (b.c);
    r := r & " " & NARROW (Thread.Join (bt), TEXT);
    (* nor the wait for a mutex *)
    LOCK mu DO
      l := Thread.Fork (NEW (LockerCl, mu := mu));
      Thread.Pause (0.02d0);
      Thread.Alert (l);
      Thread.Pause (0.02d0);
    END;
    r := r & " " & NARROW (Thread.Join (l), TEXT);
    Out ("other alerts: " & r);
  END OtherAlerts;

(* 10. Many alerts and wake-ups for threads in AlertWait. *)

TYPE
  StormCl = Thread.Closure OBJECT
    need: INTEGER;
    got         := 0;
  OVERRIDES
    apply := StormApply;
  END;

VAR
  stormMu   := NEW (MUTEX);
  stormC    := NEW (Thread.Condition);
  stormDone := 0;

PROCEDURE StormApply (cl: StormCl): REFANY =
  BEGIN
    LOCK stormMu DO
      WHILE cl.got < cl.need DO
        TRY
          Thread.AlertWait (stormMu, stormC);
        EXCEPT
          Thread.Alerted => INC (cl.got);
        END;
      END;
      INC (stormDone);
    END;
    RETURN NIL;
  END StormApply;

PROCEDURE Storm () =
  CONST N = 4;
  VAR
    cl : ARRAY [1 .. N] OF StormCl;
    t  : ARRAY [1 .. N] OF Thread.T;
    done                            := FALSE;
    alert : ARRAY [1 .. N] OF BOOLEAN;
    round                           := 0;
    got                             := 0;
  BEGIN
    FOR i := 1 TO N DO
      cl[i] := NEW (StormCl, need := 20 * scale);
      t[i] := Thread.Fork (cl[i]);
    END;
    WHILE NOT done DO
      LOCK stormMu DO
        done := stormDone = N;
        FOR i := 1 TO N DO alert[i] := cl[i].got < cl[i].need; END;
      END;
      FOR i := 1 TO N DO
        IF alert[i] THEN Thread.Alert (t[i]); END;
      END;
      INC (round);
      IF round MOD 3 = 0 THEN Thread.Broadcast (stormC); END;
      IF round MOD 5 = 0 THEN Thread.Signal (stormC); END;
      Thread.Pause (0.0001d0);
    END;
    FOR i := 1 TO N DO
      EVAL Thread.Join (t[i]);
      INC (got, cl[i].got);
    END;
    Out ("alert storm: " & Fmt.Int (got));
  END Storm;

(* 11. Alerts that arrive while the thread takes them with TestAlert: no
   alert may be seen twice, by a later AlertPause or AlertJoin. *)

TYPE
  RaceCl = Thread.Closure OBJECT
    rounds   : INTEGER;
    join     : BOOLEAN;
    spurious           := 0;
  OVERRIDES
    apply := RaceApply;
  END;

VAR
  raceMu  := NEW (MUTEX);
  raceC   := NEW (Thread.Condition);
  raceAck := 0;

PROCEDURE RaceApply (cl: RaceCl): REFANY =
  VAR u: Thread.T;
  BEGIN
    FOR i := 1 TO cl.rounds DO
      WHILE NOT Thread.TestAlert () DO END;
      TRY
        IF cl.join THEN
          u := Thread.Fork (NEW (SleeperCl, secs := 0.002d0));
          EVAL Thread.AlertJoin (u);
        ELSE
          Thread.AlertPause (0.001d0);
        END;
      EXCEPT
        Thread.Alerted =>
          INC (cl.spurious);
          IF cl.join THEN EVAL Thread.Join (u); END;
      END;
      LOCK raceMu DO raceAck := i; END;
      Thread.Signal (raceC);
    END;
    RETURN NIL;
  END RaceApply;

PROCEDURE Race (join: BOOLEAN; rounds: INTEGER): TEXT =
  VAR
    cl := NEW (RaceCl, rounds := rounds, join := join);
    t  := Thread.Fork (cl);
  BEGIN
    raceAck := 0;
    FOR i := 1 TO rounds DO
      Thread.Alert (t);
      LOCK raceMu DO
        WHILE raceAck < i DO Thread.Wait (raceMu, raceC); END;
      END;
    END;
    EVAL Thread.Join (t);
    RETURN Fmt.Int (rounds) & " " & Fmt.Int (cl.spurious);
  END Race;

PROCEDURE Races () =
  BEGIN
    Out ("alert races: " & Race (FALSE, 200 * scale) & ", "
           & Race (TRUE, 50 * scale));
  END Races;

(* 12. Many short threads, each with a result; Self is the handle that
   Fork returned. *)

TYPE
  SquareCl = Thread.Closure OBJECT
    k : INTEGER;
    me: Thread.T;
  OVERRIDES
    apply := SquareApply;
  END;

PROCEDURE SquareApply (cl: SquareCl): REFANY =
  VAR r := NEW (REF INTEGER);
  BEGIN
    cl.me := Thread.Self ();
    r^ := cl.k * cl.k;
    RETURN r;
  END SquareApply;

PROCEDURE Many () =
  CONST Batch = 50;
  VAR
    cl : ARRAY [0 .. Batch - 1] OF SquareCl;
    t  : ARRAY [0 .. Batch - 1] OF Thread.T;
    sum, k, bad                             := 0;
  BEGIN
    FOR b := 1 TO 20 * scale DO
      FOR j := 0 TO Batch - 1 DO
        INC (k);
        cl[j] := NEW (SquareCl, k := k);
        t[j] := Thread.Fork (cl[j]);
      END;
      FOR j := Batch - 1 TO 0 BY -1 DO
        INC (sum, NARROW (Thread.Join (t[j]), REF INTEGER)^);
        IF cl[j].me # t[j] THEN INC (bad); END;
      END;
    END;
    Check ("many threads", bad = 0 AND sum = k * (k + 1) * (2 * k + 1) DIV 6,
           Fmt.Int (k) & " " & Fmt.Int (sum));
  END Many;

(* 13. The first use of types in many threads at once: each thread gets
   the same typecodes, and type tests work. *)

TYPE
  O0 = OBJECT a: INTEGER;  END;
  O1 = O0 OBJECT b: INTEGER;  END;
  O2 = O1 OBJECT c: TEXT;  END;
  O3 = O0 OBJECT d: REAL;  END;
  O4 = O3 OBJECT e: CHAR;  END;
  O5 = O2 OBJECT f: BOOLEAN;  END;
  R0 = REF RECORD x, y: INTEGER;  END;
  R1 = REF ARRAY OF [0 .. 999];
  R2 = REF ARRAY OF O1;
  R3 = REF [0 .. 77];
  R4 = REF ARRAY [0 .. 3] OF CHAR;
  R5 = REF RECORD o: O4;  END;

CONST Kinds = 12;

TYPE
  Codes = ARRAY [0 .. Kinds - 1] OF INTEGER;
  TypeCl = Thread.Closure OBJECT
    id  : INTEGER;
    gate: Gate;
    tc  : Codes;
    bad         := 0;
  OVERRIDES
    apply := TypeApply;
  END;

PROCEDURE Make (k: INTEGER): REFANY =
  BEGIN
    CASE k OF
    | 0 => RETURN NEW (O0);
    | 1 => RETURN NEW (O1);
    | 2 => RETURN NEW (O2);
    | 3 => RETURN NEW (O3);
    | 4 => RETURN NEW (O4);
    | 5 => RETURN NEW (O5);
    | 6 => RETURN NEW (R0);
    | 7 => RETURN NEW (R1, 3);
    | 8 => RETURN NEW (R2, 2);
    | 9 => RETURN NEW (R3);
    | 10 => RETURN NEW (R4);
    ELSE
      RETURN NEW (R5);
    END;
  END Make;

PROCEDURE Code (k: INTEGER): INTEGER =
  BEGIN
    CASE k OF
    | 0 => RETURN TYPECODE (O0);
    | 1 => RETURN TYPECODE (O1);
    | 2 => RETURN TYPECODE (O2);
    | 3 => RETURN TYPECODE (O3);
    | 4 => RETURN TYPECODE (O4);
    | 5 => RETURN TYPECODE (O5);
    | 6 => RETURN TYPECODE (R0);
    | 7 => RETURN TYPECODE (R1);
    | 8 => RETURN TYPECODE (R2);
    | 9 => RETURN TYPECODE (R3);
    | 10 => RETURN TYPECODE (R4);
    ELSE
      RETURN TYPECODE (R5);
    END;
  END Code;

PROCEDURE Kind (r: REFANY): INTEGER =
  BEGIN
    TYPECASE r OF
    | NULL => RETURN -1;
    | O5 => RETURN 5;
    | O4 => RETURN 4;
    | O2 => RETURN 2;
    | O3 => RETURN 3;
    | O1 => RETURN 1;
    | O0 => RETURN 0;
    | R0 => RETURN 6;
    | R1 => RETURN 7;
    | R2 => RETURN 8;
    | R3 => RETURN 9;
    | R4 => RETURN 10;
    | R5 => RETURN 11;
    ELSE
      RETURN 99;
    END;
  END Kind;

PROCEDURE TypeApply (cl: TypeCl): REFANY =
  VAR
    k: INTEGER;
    r: REFANY;
  BEGIN
    Pass (cl.gate);
    FOR i := 0 TO Kinds - 1 DO
      k := (i + cl.id * 5) MOD Kinds;
      IF cl.id MOD 2 = 0 THEN
        cl.tc[k] := Code (k);
        r := Make (k);
      ELSE
        r := Make (k);
        cl.tc[k] := Code (k);
      END;
      IF TYPECODE (r) # cl.tc[k] OR Kind (r) # k THEN INC (cl.bad); END;
      IF (k <= 5) # ISTYPE (r, O0) THEN INC (cl.bad); END;
      IF (k = 1 OR k = 2 OR k = 5) # ISTYPE (r, O1) THEN INC (cl.bad); END;
    END;
    RETURN NIL;
  END TypeApply;

PROCEDURE Types () =
  CONST N = 8;
  VAR
    gate := NewGate ();
    cl : ARRAY [1 .. N] OF TypeCl;
    t  : ARRAY [1 .. N] OF Thread.T;
    bad                             := 0;
  BEGIN
    FOR i := 1 TO N DO
      cl[i] := NEW (TypeCl, id := i, gate := gate);
      t[i] := Thread.Fork (cl[i]);
    END;
    Thread.Pause (0.02d0);
    Open (gate);
    FOR i := 1 TO N DO
      EVAL Thread.Join (t[i]);
      INC (bad, cl[i].bad);
      IF cl[i].tc # cl[1].tc THEN INC (bad); END;
    END;
    FOR i := 0 TO Kinds - 1 DO
      FOR j := i + 1 TO Kinds - 1 DO
        IF cl[1].tc[i] = cl[1].tc[j] THEN INC (bad); END;
      END;
    END;
    Check ("typecodes", bad = 0, Fmt.Int (bad));
  END Types;

(* 14. Atoms made from the same texts in many threads at once. *)

CONST AtomCount = 200;

TYPE
  AtomCl = Thread.Closure OBJECT
    id   : INTEGER;
    gate : Gate;
    atoms: ARRAY [0 .. AtomCount - 1] OF Atom.T;
    bad                                          := 0;
  OVERRIDES
    apply := AtomApply;
  END;

PROCEDURE AtomApply (cl: AtomCl): REFANY =
  VAR
    k: INTEGER;
    name: TEXT;
  BEGIN
    Pass (cl.gate);
    FOR i := 0 TO AtomCount - 1 DO
      k := (i * 7 + cl.id * 13) MOD AtomCount;
      name := "atom-" & Fmt.Int (k) & "-" & Fmt.Int (k * k, 16);
      cl.atoms[k] := Atom.FromText (name);
      IF NOT Text.Equal (Atom.ToText (cl.atoms[k]), name) THEN
        INC (cl.bad);
      END;
    END;
    RETURN NIL;
  END AtomApply;

PROCEDURE Atoms () =
  CONST N = 8;
  VAR
    gate := NewGate ();
    cl : ARRAY [1 .. N] OF AtomCl;
    t  : ARRAY [1 .. N] OF Thread.T;
    bad                             := 0;
  BEGIN
    FOR i := 1 TO N DO
      cl[i] := NEW (AtomCl, id := i, gate := gate);
      t[i] := Thread.Fork (cl[i]);
    END;
    Open (gate);
    FOR i := 1 TO N DO
      EVAL Thread.Join (t[i]);
      INC (bad, cl[i].bad);
      FOR k := 0 TO AtomCount - 1 DO
        IF cl[i].atoms[k] # cl[1].atoms[k] THEN INC (bad); END;
      END;
    END;
    Check ("atoms", bad = 0, Fmt.Int (bad));
  END Atoms;

(* 15. A writer used by many threads at once: each PutText is whole. *)

TYPE
  WrCl = Thread.Closure OBJECT
    wr: Wr.T;
    ch: CHAR;
    n : INTEGER;
  OVERRIDES
    apply := WrApply;
  END;

PROCEDURE WrApply (cl: WrCl): REFANY =
  <* FATAL Wr.Failure, Thread.Alerted *>
  VAR
    one := Text.FromChar (cl.ch);
    s   := one & one & one & one;
  BEGIN
    FOR i := 1 TO cl.n DO
      IF i MOD 2 = 0 THEN
        Wr.PutText (cl.wr, s);
      ELSE
        Wr.PutString (cl.wr, ARRAY OF CHAR {cl.ch, cl.ch, cl.ch, cl.ch});
      END;
    END;
    RETURN NIL;
  END WrApply;

PROCEDURE Writer () =
  CONST N = 6;
  VAR
    wr                              := TextWr.New ();
    t  : ARRAY [0 .. N - 1] OF Thread.T;
    n                               := 5000 * scale;
    text: TEXT;
    counts := ARRAY [0 .. N - 1] OF INTEGER {0, ..};
    bad, len                        := 0;
    ch: CHAR;
  BEGIN
    FOR i := 0 TO N - 1 DO
      t[i] := Thread.Fork (NEW (WrCl, wr := wr, ch := VAL (ORD ('a') + i, CHAR),
                                n := n));
    END;
    FOR i := 0 TO N - 1 DO EVAL Thread.Join (t[i]); END;
    text := TextWr.ToText (wr);
    len := Text.Length (text);
    FOR k := 0 TO len DIV 4 - 1 DO
      ch := Text.GetChar (text, 4 * k);
      FOR m := 1 TO 3 DO
        IF Text.GetChar (text, 4 * k + m) # ch THEN INC (bad); END;
      END;
      IF ch >= 'a' AND ch < VAL (ORD ('a') + N, CHAR) THEN
        INC (counts[ORD (ch) - ORD ('a')]);
      ELSE
        INC (bad);
      END;
    END;
    FOR i := 0 TO N - 1 DO
      IF counts[i] # n THEN INC (bad); END;
    END;
    Check ("writer", bad = 0 AND len = 4 * N * n,
           Fmt.Int (len) & " " & Fmt.Int (bad));
  END Writer;

(* 16. Deep recursion in a forked thread with a stack of a given size. *)

TYPE
  DeepCl = Thread.SizedClosure OBJECT
    depth: INTEGER;
  OVERRIDES
    apply := DeepApply;
  END;

PROCEDURE Depth (n: INTEGER): INTEGER =
  BEGIN
    IF n = 0 THEN RETURN 0 ELSE RETURN 1 + Depth (n - 1) END;
  END Depth;

PROCEDURE DeepApply (cl: DeepCl): REFANY =
  VAR r := NEW (REF INTEGER);
  BEGIN
    r^ := Depth (cl.depth);
    RETURN r;
  END DeepApply;

PROCEDURE Deep () =
  VAR
    t := Thread.Fork (NEW (DeepCl, depth := 100000, stackSize := 2000000));
  BEGIN
    Out ("deep: " & Fmt.Int (NARROW (Thread.Join (t), REF INTEGER)^));
  END Deep;

BEGIN
  IF Params.Count > 1 THEN
    TRY
      scale := Scan.Int (Params.Get (1));
    EXCEPT
      Lex.Error, FloatMode.Trap => scale := 1;
    END;
  END;
  First ();
  Count ();
  Bank ();
  Buffer ();
  Bar ();
  Exits ();
  Turns ();
  SelfAlerts ();
  OtherAlerts ();
  Storm ();
  Races ();
  Many ();
  Types ();
  Atoms ();
  Writer ();
  Deep ();
END Main.
