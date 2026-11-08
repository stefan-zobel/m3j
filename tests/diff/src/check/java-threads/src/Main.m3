(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Procedures called back from Java threads, under load: plain threads, a
   pool of an executor, parallel streams and sorts, the bins of a concurrent
   map; closures of nested procedures; mutexes, conditions and alerts shared
   between Java threads and forked threads; the Thread.T of a Java thread;
   Modula-3 exceptions and runtime errors in callbacks, handled there, or
   leaving them through a pool or a parallel stream.

   The output does not depend on the order in which the threads run: it has
   checks only, and is the same for every scale. The argument, a positive
   number (default 1), scales the amount of work. *)

MODULE Main;

IMPORT Fmt, FloatMode, IO, Java, JavaThrowable, Lex, Params, RuntimeError,
       Scan, Text, Thread, Word;
IMPORT JArrays, JExecutor, JExecutors, JFuture, JInteger, JIntStream, JMap,
       JThread;

CONST Workers = 8;

EXCEPTION Boom (INTEGER);

VAR scale := 1;

PROCEDURE Check (name: TEXT; ok: BOOLEAN; detail: TEXT := "") =
  BEGIN
    IF ok THEN
      IO.Put (name & ": ok\n");
    ELSE
      IO.Put (name & ": FAILED " & detail & "\n");
    END;
  END Check;

(* Runs body in Workers Java threads and waits for them. *)
PROCEDURE RunAll (body: JThread.Body) =
  VAR t: ARRAY [1 .. Workers] OF JThread.T;
  BEGIN
    FOR i := FIRST (t) TO LAST (t) DO t[i] := JThread.New (body); END;
    FOR i := FIRST (t) TO LAST (t) DO JThread.start (t[i]); END;
    FOR i := FIRST (t) TO LAST (t) DO JThread.join (t[i]); END;
  END RunAll;

(* A counter of a procedure, under a mutex of that procedure. *)
PROCEDURE Counter () =
  VAR
    n     := 20000 * scale;
    total := 0;
    mu    := NEW (MUTEX);
  PROCEDURE Body () =
    BEGIN
      FOR i := 1 TO n DO
        LOCK mu DO INC (total); END;
      END;
    END Body;
  BEGIN
    RunAll (Body);
    Check ("counter under a mutex", total = n * Workers, Fmt.Int (total));
  END Counter;

(* A thread per level of a recursion, each with the closure of its level. *)
PROCEDURE Nest (depth: INTEGER): INTEGER =
  VAR
    own  := 0;
    rest := 0;
    t    : JThread.T;
  PROCEDURE Body () =
    BEGIN
      FOR i := 1 TO 1000 DO INC (own, depth); END;
    END Body;
  BEGIN
    t := JThread.New (Body);
    JThread.start (t);
    IF depth > 1 THEN rest := Nest (depth - 1); END;
    JThread.join (t);
    RETURN own + rest;
  END Nest;

(* Every Java thread has its own Thread.T, the same on every call. *)
PROCEDURE Selves () =
  CONST Group = 16;
  VAR
    mu       := NEW (MUTEX);
    full     := NEW (Thread.Condition);
    seen     : ARRAY [0 .. Group - 1] OF Thread.T;
    in       := 0;
    same     := TRUE;
    distinct := TRUE;
    t        : ARRAY [0 .. Group - 1] OF JThread.T;
  PROCEDURE Body () =
    VAR me := Thread.Self ();
    BEGIN
      LOCK mu DO
        IF me = NIL OR me # Thread.Self () THEN same := FALSE; END;
        seen[in] := me;
        INC (in);
        IF in = Group THEN
          Thread.Broadcast (full);
        ELSE
          WHILE in < Group DO Thread.Wait (mu, full); END;
        END;
      END;
    END Body;
  BEGIN
    FOR r := 1 TO 64 * scale DO
      in := 0;
      FOR i := FIRST (t) TO LAST (t) DO t[i] := JThread.New (Body); END;
      FOR i := FIRST (t) TO LAST (t) DO JThread.start (t[i]); END;
      FOR i := FIRST (t) TO LAST (t) DO JThread.join (t[i]); END;
      FOR i := FIRST (seen) TO LAST (seen) DO
        FOR j := i + 1 TO LAST (seen) DO
          IF seen[i] = seen[j] THEN distinct := FALSE; END;
        END;
      END;
    END;
    Check ("Thread.Self is stable", same);
    Check ("Thread.Self is distinct", distinct);
  END Selves;

(* Tasks of a pool, which allocate and share a counter. *)
PROCEDURE Pool () =
  VAR
    n     := 4000 * scale;
    pool  := JExecutors.newFixedThreadPool (Workers);
    all   := NEW (REF ARRAY OF JFuture.T, n);
    total := 0;
    mu    := NEW (MUTEX);
    nils  := 0;
  PROCEDURE Task () =
    VAR t := Fmt.Int (1234) & "x";
    BEGIN
      LOCK mu DO INC (total, Text.Length (t)); END;
    END Task;
  BEGIN
    FOR i := 0 TO n - 1 DO all[i] := JExecutor.submit (pool, Task); END;
    FOR i := 0 TO n - 1 DO
      IF JFuture.get (all[i]) = NIL THEN INC (nils); END;
    END;
    JExecutor.shutdown (pool);
    Check ("tasks of a pool", total = n * 5, Fmt.Int (total));
    Check ("results of a pool", nils = n, Fmt.Int (nils));
  END Pool;

(* Callbacks with arguments and results from the workers of a stream. *)
PROCEDURE Streams () =
  VAR
    n    := 200000 * scale;
    a    := NEW (REF ARRAY OF INTEGER, n);
    bad  := 0;
    want := 0;
  PROCEDURE Put (i: Java.Int) =
    BEGIN
      a[i] := i + 1;
    END Put;
  PROCEDURE Mod7 (i: Java.Int): Java.Int =
    BEGIN
      RETURN i MOD 7;
    END Mod7;
  BEGIN
    JIntStream.forEach (JIntStream.parallel (JIntStream.range (0, n)), Put);
    FOR i := 0 TO n - 1 DO
      IF a[i] # i + 1 THEN INC (bad); END;
      INC (want, i MOD 7);
    END;
    Check ("forEach of a parallel stream", bad = 0, Fmt.Int (bad));
    Check ("map of a parallel stream",
           JIntStream.sum (JIntStream.map (JIntStream.parallel (JIntStream.range (0, n)), Mod7)) = want);
  END Streams;

(* A callback that runs under the locks of the bins of a concurrent map. *)
PROCEDURE Merge () =
  CONST Keys = 16;
  VAR
    map   := JMap.New ();
    n     := 20000 * scale;
    total := 0;
  PROCEDURE Plus (a, b: REFANY): REFANY =
    BEGIN
      RETURN Java.FromLong (Java.ToInt (a) + Java.ToInt (b));
    END Plus;
  PROCEDURE Body () =
    BEGIN
      FOR i := 0 TO n - 1 DO
        EVAL JMap.merge (map, Java.FromInt (i MOD Keys), Java.FromLong (1), Plus);
      END;
    END Body;
  BEGIN
    RunAll (Body);
    FOR k := 0 TO Keys - 1 DO
      INC (total, Java.ToInt (JMap.get (map, Java.FromInt (k))));
    END;
    Check ("merge of a concurrent map", total = n * Workers, Fmt.Int (total));
  END Merge;

PROCEDURE ByText (a, b: TEXT): Java.Int =
  BEGIN
    RETURN Text.Compare (a, b);
  END ByText;

(* A comparison called from the workers of a parallel sort. *)
PROCEDURE Sort () =
  VAR
    n      := 50000 * scale;
    texts  := NEW (REF ARRAY OF TEXT, n);
    s      : Word.T := 42;
    before := 0;
    after  := 0;
    bad    := 0;
    array  : REFANY;
    got    : CARDINAL;
  BEGIN
    FOR i := 0 TO n - 1 DO
      s := Word.Plus (Word.Times (s, 6364136223846793005), 1442695040888963407);
      texts[i] := Fmt.Int (Word.RightShift (s, 33));
      INC (before, Text.Length (texts[i]));
    END;
    array := Java.NewStrings (texts^);
    JArrays.parallelSort (array, ByText);
    got := Java.GetStrings (array, texts^);
    FOR i := 0 TO n - 1 DO
      INC (after, Text.Length (texts[i]));
      IF i > 0 AND Text.Compare (texts[i - 1], texts[i]) > 0 THEN INC (bad); END;
    END;
    Check ("parallel sort", got = n AND bad = 0 AND before = after, Fmt.Int (bad));
  END Sort;

TYPE
  Caller = Thread.Closure OBJECT
    sum := 0;
  OVERRIDES
    apply := CallerApply;
  END;

PROCEDURE CallerApply (self: Caller): REFANY =
  VAR own := 0;
  PROCEDURE Add (i: Java.Int) =
    BEGIN
      INC (own, i);
    END Add;
  BEGIN
    FOR r := 1 TO 200 * scale DO
      JIntStream.forEach (JIntStream.range (0, 100), Add);
    END;
    self.sum := own;
    RETURN NIL;
  END CallerApply;

(* Forked threads that all call Java, with a closure of their own. *)
PROCEDURE Forked () =
  VAR
    c     : ARRAY [1 .. Workers] OF Caller;
    t     : ARRAY [1 .. Workers] OF Thread.T;
    total := 0;
  BEGIN
    FOR i := FIRST (t) TO LAST (t) DO
      c[i] := NEW (Caller);
      t[i] := Thread.Fork (c[i]);
    END;
    FOR i := FIRST (t) TO LAST (t) DO
      EVAL Thread.Join (t[i]);
      INC (total, c[i].sum);
    END;
    Check ("forked threads call Java", total = Workers * 200 * scale * 4950, Fmt.Int (total));
  END Forked;

(* A bounded buffer: Java threads produce, forked threads consume. *)
CONST Size = 8;

VAR
  bufMu    := NEW (MUTEX);
  notEmpty := NEW (Thread.Condition);
  notFull  := NEW (Thread.Condition);
  buf      : ARRAY [0 .. Size - 1] OF INTEGER;
  held     := 0;
  head     := 0;
  perWorker: INTEGER;

PROCEDURE Put (x: INTEGER) =
  BEGIN
    LOCK bufMu DO
      WHILE held = Size DO Thread.Wait (bufMu, notFull); END;
      buf[(head + held) MOD Size] := x;
      INC (held);
      Thread.Signal (notEmpty);
    END;
  END Put;

PROCEDURE Get (): INTEGER =
  VAR x: INTEGER;
  BEGIN
    LOCK bufMu DO
      WHILE held = 0 DO Thread.Wait (bufMu, notEmpty); END;
      x := buf[head];
      head := (head + 1) MOD Size;
      DEC (held);
      Thread.Signal (notFull);
    END;
    RETURN x;
  END Get;

PROCEDURE Producer () =
  BEGIN
    FOR i := 1 TO perWorker DO Put (i); END;
  END Producer;

TYPE
  Consumer = Thread.Closure OBJECT
    sum := 0;
  OVERRIDES
    apply := ConsumerApply;
  END;

PROCEDURE ConsumerApply (self: Consumer): REFANY =
  BEGIN
    FOR i := 1 TO perWorker DO INC (self.sum, Get ()); END;
    RETURN NIL;
  END ConsumerApply;

PROCEDURE Buffer () =
  VAR
    c     : ARRAY [1 .. Workers] OF Consumer;
    t     : ARRAY [1 .. Workers] OF Thread.T;
    total := 0;
  BEGIN
    perWorker := 5000 * scale;
    FOR i := FIRST (t) TO LAST (t) DO
      c[i] := NEW (Consumer);
      t[i] := Thread.Fork (c[i]);
    END;
    RunAll (Producer);
    FOR i := FIRST (t) TO LAST (t) DO
      EVAL Thread.Join (t[i]);
      INC (total, c[i].sum);
    END;
    Check ("buffer between Java and forked threads",
           total = Workers * perWorker * (perWorker + 1) DIV 2 AND held = 0, Fmt.Int (total));
  END Buffer;

(* Alerts of Java threads that wait on a condition. *)
PROCEDURE Alerts () =
  VAR
    mu     := NEW (MUTEX);
    ready  := NEW (Thread.Condition);
    never  := NEW (Thread.Condition);
    target : Thread.T := NIL;
    caught := 0;
    n      := 500 * scale;
    t      : JThread.T;
  PROCEDURE Body () =
    BEGIN
      LOCK mu DO
        target := Thread.Self ();
        Thread.Broadcast (ready);
        TRY
          LOOP Thread.AlertWait (mu, never); END;
        EXCEPT
        | Thread.Alerted => INC (caught);
        END;
      END;
    END Body;
  BEGIN
    FOR r := 1 TO n DO
      LOCK mu DO target := NIL; END;
      t := JThread.New (Body);
      JThread.start (t);
      LOCK mu DO
        WHILE target = NIL DO Thread.Wait (mu, ready); END;
      END;
      Thread.Alert (target);
      JThread.join (t);
    END;
    Check ("alerts of Java threads", caught = n, Fmt.Int (caught));
  END Alerts;

(* Exceptions that callbacks on pool threads raise and handle themselves. *)
PROCEDURE Handled () =
  VAR
    n     := 2000 * scale;
    pool  := JExecutors.newFixedThreadPool (Workers);
    all   := NEW (REF ARRAY OF JFuture.T, n);
    mu    := NEW (MUTEX);
    a     : ARRAY [0 .. 1] OF INTEGER;
    index := 5;
    boom  := 0;
    range := 0;
    java  := 0;
  PROCEDURE Task () =
    BEGIN
      TRY
        RAISE Boom (7);
      EXCEPT
      | Boom (x) => IF x = 7 THEN LOCK mu DO INC (boom); END; END;
      END;
      TRY
        a[index] := 1;
      EXCEPT
      | RuntimeError.E (e) =>
          IF e = RuntimeError.T.SubscriptOutOfRange THEN LOCK mu DO INC (range); END; END;
      END;
      TRY
        EVAL JInteger.parseInt ("x");
      EXCEPT
      | Java.Exception => LOCK mu DO INC (java); END;
      END;
    END Task;
  BEGIN
    FOR i := 0 TO n - 1 DO all[i] := JExecutor.submit (pool, Task); END;
    FOR i := 0 TO n - 1 DO EVAL JFuture.get (all[i]); END;
    JExecutor.shutdown (pool);
    Check ("exception handled in a callback", boom = n, Fmt.Int (boom));
    Check ("runtime error handled in a callback", range = n, Fmt.Int (range));
    Check ("Java exception handled in a callback", java = n, Fmt.Int (java));
  END Handled;

(* An exception that leaves a task: Future.get reports it as its cause. *)
PROCEDURE ThroughFuture () =
  VAR
    n    := 2000 * scale;
    pool := JExecutors.newFixedThreadPool (Workers);
    good := 0;
    f    : JFuture.T;
    last := "no exception";
    cause: JavaThrowable.T;
  PROCEDURE Task () RAISES {Boom} =
    BEGIN
      RAISE Boom (42);
    END Task;
  BEGIN
    FOR i := 1 TO n DO
      f := JExecutor.submit (pool, Task);
      TRY
        EVAL JFuture.get (f);
        last := "no exception";
      EXCEPT
      | Java.Exception (t) =>
          cause := JavaThrowable.getCause (t);
          IF Java.IsInstance (t, "java.util.concurrent.ExecutionException")
             AND Java.IsInstance (cause, "m3j.rt.M3Exception") THEN
            INC (good);
          ELSE
            last := Java.ToText (t);
          END;
      END;
    END;
    JExecutor.shutdown (pool);
    Check ("exception out of a task", good = n, last);
  END ThroughFuture;

(* An exception that leaves a callback of a parallel stream arrives at the
   caller of the stream as itself, with its argument. *)
PROCEDURE ThroughStream () =
  <* FATAL ANY *>
  CONST Size = 10000;
  VAR
    n      := 200 * scale;
    good   := 0;
    errors := 0;
    last   := "no exception";
    a      : ARRAY [0 .. 1] OF INTEGER;
  PROCEDURE Raise (i: Java.Int) RAISES {Boom} =
    BEGIN
      RAISE Boom (i + 1);
    END Raise;
  PROCEDURE Fail (i: Java.Int) =
    BEGIN
      a[i + 2] := 1;
    END Fail;
  BEGIN
    FOR r := 1 TO n DO
      TRY
        JIntStream.forEachRaises (JIntStream.parallel (JIntStream.range (0, Size)), Raise);
      EXCEPT
      | Boom (x) =>
          IF 1 <= x AND x <= Size THEN INC (good); ELSE last := "argument " & Fmt.Int (x); END;
      | Java.Exception (t) => last := Java.ToText (t);
      | RuntimeError.E (e) => last := "runtime error " & RuntimeError.Tag (e);
      END;
      TRY
        JIntStream.forEach (JIntStream.parallel (JIntStream.range (0, Size)), Fail);
      EXCEPT
      | Java.Exception (t) => last := Java.ToText (t);
      | RuntimeError.E (e) =>
          IF e = RuntimeError.T.SubscriptOutOfRange THEN
            INC (errors);
          ELSE
            last := "runtime error " & RuntimeError.Tag (e);
          END;
      END;
    END;
    Check ("exception out of a parallel stream", good = n, last);
    Check ("runtime error out of a parallel stream", errors = n, last);
  END ThroughStream;

PROCEDURE Scale () =
  <* FATAL Lex.Error, FloatMode.Trap *>
  BEGIN
    IF Params.Count > 1 THEN scale := Scan.Int (Params.Get (1)); END;
  END Scale;

BEGIN
  Scale ();
  Counter ();
  Check ("closures of a recursion", Nest (64) = 1000 * 64 * 65 DIV 2);
  Selves ();
  Pool ();
  Streams ();
  Merge ();
  Sort ();
  Forked ();
  Buffer ();
  Alerts ();
  Handled ();
  ThroughFuture ();
  ThroughStream ();
END Main.
