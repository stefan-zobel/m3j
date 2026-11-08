(* Copyright 1993 Digital Equipment Corporation.               *)
(* Distributed only by permission.                             *)
(* FDTest.m3 *)
(* Last modified on Wed Mar 23 16:04:37 PST 1994 by wobber *)

(* The test fd of the network objects of cm3
   (m3-comm/netobj/tests/fd), for tools/net_test.py:

     test server       prints "port N", the port of its agent (it
                       tries ports from 9601 on until it can listen),
                       exports the object of the test there and
                       serves until the end of its standard input
     test client N     imports that object from the agent on port N
                       of the local host and runs the test

   The original tries the agent of netobjd first and listens on a
   fixed port, and its client is given the host. Its client always
   forks 50 waiters, does not print how many are waiting while they
   call back, nor the time of a null call, and prints when its
   threads have returned. *)

MODULE Main;

IMPORT Atom, AtomList, Test, TCPNetObj, IP,
       NetObj, IO, Params, Fmt, Thread, Text, Scan, Lex, FloatMode;

CONST TestObjName = "FDTestObj";
      FirstPort   = 9601;

<* FATAL Thread.Alerted *>

TYPE
  T = Test.T OBJECT OVERRIDES
    null := DoNull;
    callback := DoCallback;
  END;

TYPE
  Waiter = Test.Waiter OBJECT
    c: Thread.Condition;
    wakeup: BOOLEAN;
  OVERRIDES
    wait := DoWait;
  END;

TYPE
  Fork = Thread.Closure OBJECT
    t: Test.T;
    thread: Thread.T;
    w: Waiter;
  OVERRIDES
    apply := ForkedWait;
  END;

VAR
  mu := NEW(MUTEX);
  forks: CARDINAL := 0;
  waiters: CARDINAL := 0;

(* server-side code *)

PROCEDURE DoNull(<*UNUSED*> t: T)
    RAISES {NetObj.Error, Thread.Alerted} =
  BEGIN
  END DoNull;

PROCEDURE DoCallback(<*UNUSED*> t: T; w: Test.Waiter)
    RAISES {NetObj.Error, Thread.Alerted} =
  BEGIN
    w.wait();
  END DoCallback;

PROCEDURE DoWait(w: Waiter)
    RAISES {NetObj.Error, Thread.Alerted} =
  BEGIN
    LOCK mu DO
      INC(waiters);
      TRY
        WHILE NOT w.wakeup DO Thread.AlertWait(mu, w.c); END;
      FINALLY
        DEC(waiters);
      END;
    END;
  END DoWait;

(* client-side code *)

PROCEDURE NewFork(t: Test.T): Fork =
  VAR fork := NEW(Fork, t := t,
         w := NEW(Waiter, c := NEW(Thread.Condition), wakeup := FALSE));
  BEGIN
    fork.thread := Thread.Fork(fork);
    RETURN fork;
  END NewFork;

PROCEDURE Wakeup(fork: Fork) =
  BEGIN
    fork.w.wakeup := TRUE;
    Thread.Signal(fork.w.c);
  END Wakeup;

PROCEDURE ForkedWait(fork: Fork): REFANY =
  VAR me: CARDINAL;
  BEGIN
    LOCK mu DO me := forks; INC(forks); END;
    (* WHILE waiters < me DO Thread.Pause(1.0D-2); END; *)
    TRY
      fork.t.callback(fork.w);
    EXCEPT
    | NetObj.Error(x) => IO.Put("Forked NetObj.Error(" & ErrStr(x) & ")\n");
    | Thread.Alerted =>
    END;
    LOCK mu DO DEC(forks); END;
    RETURN NIL;
  END ForkedWait;

PROCEDURE DoTest(t: Test.T; n: CARDINAL) =
  VAR forklist := NEW(REF ARRAY OF Fork, n);
  BEGIN
    IO.Put("Waiting for forked threads to call back.\n");
    FOR i := 0 TO n-1 DO forklist[i] := NewFork(t); END;
    LOOP
      LOCK mu DO
        IF waiters = n THEN EXIT; END;
      END;
      Thread.Pause(1.0D-1);
    END;
    IO.Put("waiters=" & Fmt.Int(n) & "\n");
    TRY
      IO.Put("Null call test (" & Fmt.Int(n) & " threads waiting) ... ");
      VAR nn := NumNullCalls; BEGIN
        WHILE nn # 0 DO t.null(); DEC(nn) END;
        IO.Put("done.\n");
      END;
    EXCEPT
    | Thread.Alerted =>
    | NetObj.Error(ec) => IO.Put("NetObj.Error(" & ErrStr(ec) & ")\n");
    END;
    FOR i := 0 TO n-1 DO Wakeup(forklist[i]); END;
    IO.Put("Waiting for forked threads to return.\n");
    LOOP
      LOCK mu DO IF forks = 0 THEN EXIT; END; END;
      Thread.Pause(1.0D-1);
    END;
    IO.Put("Forked threads returned.\n");
  END DoTest;

(* common code *)

PROCEDURE ErrStr(x: AtomList.T): TEXT =
  VAR t: TEXT;
  BEGIN
    IF x = NIL THEN RETURN ""; END;
    t := Atom.ToText(x.head);
    IF x.tail # NIL THEN t := t  & "(" & ErrStr(x.tail) & ")"; END;
    RETURN t;
  END ErrStr;

CONST DefaultNWaiters = 50;
      NumNullCalls = 2000;

VAR nWaiters := DefaultNWaiters;

<* FATAL Lex.Error, FloatMode.Trap *>

BEGIN
  TRY
    IF Text.Equal(Params.Get(1), "server") THEN
      VAR port := FirstPort; agent: NetObj.Address := NIL;
      BEGIN
        WHILE agent = NIL DO
          TRY
            agent := TCPNetObj.Listen(port);
          EXCEPT
          | TCPNetObj.Failed => INC(port);
          END;
        END;
        NetObj.Export (TestObjName, NEW(T), agent);
        IO.Put("port " & Fmt.Int(port) & "\n");
        IO.Put("Exported target at private port\n");
      END;
      IO.Put("Test server running, type <CR> to terminate\n");
      TRY EVAL IO.GetLine() EXCEPT IO.Error => END;
    ELSE
      VAR port := Scan.Int(Params.Get(2));
          addr: IP.Address;
          t: Test.T;
          agent: NetObj.Address;
      BEGIN
        IF NOT IP.GetHostByName("localhost", addr) THEN
          IO.Put("No such host \"localhost\"\n");
        ELSE
          agent := TCPNetObj.Locate(IP.Endpoint{addr, port});
          t := NetObj.Import(TestObjName, agent);
          IO.Put("Located target at private port\n");
          DoTest(t, nWaiters);
        END;
      END;
    END;
  EXCEPT
  | IP.Error => IO.Put("IP.Error\n");
  | TCPNetObj.Failed => IO.Put("TCPNetObj.Failed\n");
  | NetObj.Invalid => IO.Put("NetObj.Invalid\n");
  | NetObj.Error(x) => IO.Put("NetObj.Error(" & ErrStr(x) & ")\n");
  END;
END Main.

