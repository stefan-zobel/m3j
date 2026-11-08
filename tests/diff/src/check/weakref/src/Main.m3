(* Weak references: the node of a live weak reference, weak references
   to the same node, and copies of them.

   With the argument "hand-checked" the program tests what depends on the
   collector, to be compared with a file checked by hand, since cm3's
   collector keeps nodes to which a stack may point, and so differs from
   run to run: the node of a weak reference that died; a cleanup
   procedure that gets the weak reference and its node, which it makes
   reachable again, with a new weak reference with a cleanup procedure;
   a hundred nodes with cleanup procedures; and on m3j, a node that is not
   an object, with a cleanup procedure, which is never cleaned up. *)

MODULE Main;

IMPORT IO, Fmt, Params, RTCollector, Text, Thread, WeakRef;

TYPE
  Node = OBJECT
    n   : INTEGER;
    next: Node := NIL;
  END;

CONST Count = 100;

VAR
  mu := NEW(MUTEX);
  (* LL = mu *)
  cleaned := 0;
  sum := 0;
  sameRef := TRUE;
  table: ARRAY [0 .. Count - 1] OF WeakRef.T;
  saved: Node := NIL;
  savedW: WeakRef.T;
  again := 0;

PROCEDURE Out (t: TEXT) =
  BEGIN
    IO.Put(t & "\n");
  END Out;

PROCEDURE Bool (b: BOOLEAN): TEXT =
  BEGIN
    IF b THEN RETURN "TRUE" ELSE RETURN "FALSE" END;
  END Bool;

(* Collects until done () or about five seconds have passed. *)
PROCEDURE WaitFor (done: PROCEDURE (): BOOLEAN): BOOLEAN =
  BEGIN
    FOR i := 1 TO 100 DO
      IF done() THEN RETURN TRUE END;
      RTCollector.Collect();
      Thread.Pause(0.05D0);
    END;
    RETURN done();
  END WaitFor;

PROCEDURE Cleanup (READONLY w: WeakRef.T; r: REFANY) =
  VAR nd: Node := r;
  BEGIN
    LOCK mu DO
      INC(cleaned);
      INC(sum, nd.n);
      IF table[nd.n] # w THEN sameRef := FALSE END;
    END;
  END Cleanup;

PROCEDURE AllCleaned (): BOOLEAN =
  BEGIN
    LOCK mu DO RETURN cleaned = Count END;
  END AllCleaned;

(* Count nodes with a cleanup procedure, which nobody references after
   this returns. *)
PROCEDURE MakeNodes () =
  VAR nd: Node;
  BEGIN
    FOR i := 0 TO Count - 1 DO
      nd := NEW(Node, n := i);
      LOCK mu DO table[i] := WeakRef.FromRef(nd, Cleanup) END;
    END;
  END MakeNodes;

VAR weakOnly: WeakRef.T;

PROCEDURE MakeWeakOnly () =
  BEGIN
    weakOnly := WeakRef.FromRef(NEW(Node, n := 7));
  END MakeWeakOnly;

PROCEDURE WeakOnlyDead (): BOOLEAN =
  BEGIN
    RETURN WeakRef.ToRef(weakOnly) = NIL;
  END WeakOnlyDead;

VAR weakInt: WeakRef.T;

PROCEDURE MakeWeakInt () =
  VAR r := NEW(REF INTEGER);
  BEGIN
    r^ := 42;
    weakInt := WeakRef.FromRef(r);
  END MakeWeakInt;

PROCEDURE WeakIntDead (): BOOLEAN =
  BEGIN
    RETURN WeakRef.ToRef(weakInt) = NIL;
  END WeakIntDead;

(* The cleanup keeps the node, and the first time makes a new weak
   reference with a cleanup to it. *)
PROCEDURE Keep (<*UNUSED*> READONLY w: WeakRef.T; r: REFANY) =
  BEGIN
    LOCK mu DO
      saved := r;
      INC(again);
    END;
  END Keep;

PROCEDURE KeepAgain (READONLY w: WeakRef.T; r: REFANY) =
  BEGIN
    LOCK mu DO savedW := w END;
    Keep(w, r);
    EVAL WeakRef.FromRef(r, Keep);
    LOCK mu DO saved := NIL END;
  END KeepAgain;

VAR keepW: WeakRef.T;

PROCEDURE MakeKept () =
  VAR nd := NEW(Node, n := 5);
  BEGIN
    nd.next := NEW(Node, n := 6);
    keepW := WeakRef.FromRef(nd, KeepAgain);
  END MakeKept;

PROCEDURE KeptOnce (): BOOLEAN =
  BEGIN
    LOCK mu DO RETURN again >= 1 END;
  END KeptOnce;

PROCEDURE KeptTwice (): BOOLEAN =
  BEGIN
    LOCK mu DO RETURN again >= 2 END;
  END KeptTwice;

PROCEDURE Live () =
  VAR
    nd := NEW(Node, n := 1);
    w1 := WeakRef.FromRef(nd);
    w2 := WeakRef.FromRef(nd);
    w3 := w1;
    r : REFANY;
  BEGIN
    r := WeakRef.ToRef(w1);
    Out("live: " & Bool(r = nd));
    Out("second: " & Bool(WeakRef.ToRef(w2) = nd));
    Out("copy equal: " & Bool(w3 = w1));
    Out("two differ: " & Bool(w1 # w2));
    Out("node: " & Fmt.Int(NARROW(WeakRef.ToRef(w3), Node).n));
  END Live;

(* Runs make in a thread of its own: cm3's collector keeps the pages to
   which the stack of a thread may point, and the stack of the thread is
   gone when it ends. Then allocates enough to leave those pages. *)
PROCEDURE InThread (make: PROCEDURE ()) =
  VAR junk: REF ARRAY OF INTEGER;
  BEGIN
    EVAL Thread.Join(Thread.Fork(NEW(Maker, make := make)));
    FOR i := 1 TO 1000 DO junk := NEW(REF ARRAY OF INTEGER, 100) END;
    junk := NIL;
  END InThread;

TYPE
  Maker = Thread.Closure OBJECT
    make: PROCEDURE ();
  OVERRIDES
    apply := Make;
  END;

PROCEDURE Make (self: Maker): REFANY =
  BEGIN
    self.make();
    RETURN NIL;
  END Make;

VAR refW: WeakRef.T;

PROCEDURE MakeRef () =
  TYPE R = REF RECORD n: INTEGER END;
  VAR r := NEW(R, n := 3);
  BEGIN
    refW := WeakRef.FromRef(r, CleanupRef);
  END MakeRef;

PROCEDURE CleanupRef (<*UNUSED*> READONLY w: WeakRef.T;
                      <*UNUSED*> r: REFANY) =
  BEGIN
    Out("REF cleaned up");
  END CleanupRef;

PROCEDURE HandChecked () =
  VAR ok: BOOLEAN;
  BEGIN
    InThread(MakeRef);

    InThread(MakeWeakOnly);
    Out("weak only, dead: " & Bool(WaitFor(WeakOnlyDead)));

    InThread(MakeWeakInt);
    Out("REF INTEGER, dead: " & Bool(WaitFor(WeakIntDead)));

    InThread(MakeKept);
    ok := WaitFor(KeptOnce);
    Out("first cleanup: " & Bool(ok));
    LOCK mu DO
      Out("same weak reference: " & Bool(savedW = keepW));
    END;
    Out("dead after cleanup: " & Bool(WeakRef.ToRef(keepW) = NIL));

    ok := WaitFor(KeptTwice);
    Out("second cleanup: " & Bool(ok));
    LOCK mu DO
      IF saved # NIL THEN
        Out("kept: " & Fmt.Int(saved.n) & " " & Fmt.Int(saved.next.n));
      END;
      Out("cleanups: " & Fmt.Int(again));
    END;

    InThread(MakeNodes);
    ok := WaitFor(AllCleaned);
    LOCK mu DO
      Out("all cleaned: " & Bool(ok));
      Out("cleaned: " & Fmt.Int(cleaned));
      Out("sum: " & Fmt.Int(sum));
      Out("same weak references: " & Bool(sameRef));
    END;
    FOR i := 0 TO Count - 1 DO
      IF WeakRef.ToRef(table[i]) # NIL THEN Out("not dead: " & Fmt.Int(i)) END;
    END;

    (* m3j: a node that is not an object is never cleaned up *)
    Out("REF with cleanup, alive: " & Bool(WeakRef.ToRef(refW) # NIL));
  END HandChecked;

VAR first: Node;

BEGIN
  (* cm3 forks the thread that calls the cleanup procedures when the first
     weak reference with one is made, and its stack may keep that node:
     here, a node that stays *)
  first := NEW(Node, n := -1);
  EVAL WeakRef.FromRef(first, Keep);

  Live();
  IF Params.Count > 1 AND Text.Equal(Params.Get(1), "hand-checked") THEN
    HandChecked();
  END;
END Main.
