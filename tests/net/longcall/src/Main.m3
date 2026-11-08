(* Copyright 1993 Digital Equipment Corporation. *)
(* Distributed only by permission. *)
(* NetObjTest.m3 *)
(* Last modified on Thu Aug 26 10:30:04 PDT 1993 by wobber *)

(* The test longcall of the network objects of cm3
   (m3-comm/netobj/tests/longcall), for tools/net_test.py:

     test server       prints "port N", the port of its agent (it
                       tries ports from 9601 on until it can listen),
                       exports the object of the test there and
                       serves until the end of its standard input
     test client N     imports that object from the agent on port N
                       of the local host and runs the test

   The original tries the agent of netobjd first and listens on a
   fixed port, and its client is given the host. Its client makes three
   calls, one for each line of its input in the original, and prints
   what each call raised instead of asserting it. The server sees
   that a call was alerted when its scavenger looks at the
   connections, once a minute, after the test has ended. *)

MODULE Main;

IMPORT LongCall, TCPNetObj, IP, NetObj, IO, Params, Fmt, Thread,
       Atom, AtomList, Text, Scan, Lex, FloatMode;

CONST
  LongCallObjName = "LongCallObj";
  FirstPort       = 9601;

(* server side code *)

TYPE
  T = LongCall.T OBJECT
        mu  : MUTEX;
        cond: Thread.Condition;
      OVERRIDES
        wait := DoLongWait;
      END;

<* FATAL NetObj.Error *>
<* FATAL NetObj.Invalid *>
<* FATAL Thread.Alerted *>
<* FATAL IP.Error *>
<* FATAL TCPNetObj.Failed *>

VAR seqno := 1;
VAR gmu := NEW(MUTEX);

PROCEDURE DoLongWait (t: T)
  RAISES {NetObj.Error, Thread.Alerted} =
  VAR seq: CARDINAL;
  BEGIN
    LOCK gmu DO seq := seqno; INC(seqno); END;
    TRY
      IO.Put(Fmt.F("Blocking seqno=%s\n", Fmt.Int(seq)));
      LOCK t.mu DO Thread.AlertWait(t.mu, t.cond); END;
    EXCEPT
    | Thread.Alerted => IO.Put(Fmt.F("Alerted seqno=%s\n", Fmt.Int(seq)));
    END;
  END DoLongWait;


(* client side code *)

TYPE
  Fork = Thread.Closure OBJECT
           t: LongCall.T;
         OVERRIDES
           apply := LongCallFork;
         END;

PROCEDURE LongCallFork (f: Fork): REFANY =
  BEGIN
    TRY
      f.t.wait();
      IO.Put("Call returned\n");
    EXCEPT
    | NetObj.Error (e) =>
      IO.Put("Call raised NetObj.Error(" & ErrStr(e) & "), alerted: "
             & Fmt.Bool(e.head = NetObj.Alerted) & "\n");
    END;
    RETURN NIL;
  END LongCallFork;

PROCEDURE ErrStr(x: AtomList.T): TEXT =
  VAR t: TEXT;
  BEGIN
    IF x = NIL THEN RETURN ""; END;
    t := Atom.ToText(x.head);
    IF x.tail # NIL THEN t := t  & "(" & ErrStr(x.tail) & ")"; END;
    RETURN t;
  END ErrStr;

<* FATAL Lex.Error, FloatMode.Trap *>

BEGIN
  IF Text.Equal(Params.Get(1), "server") THEN
    VAR t := NEW(T, mu := NEW(MUTEX), cond := NEW(Thread.Condition));
        port := FirstPort;
        agent: NetObj.Address := NIL;
    BEGIN
      WHILE agent = NIL DO
        TRY
          agent := TCPNetObj.Listen(port);
        EXCEPT
        | TCPNetObj.Failed => INC(port);
        END;
      END;
      NetObj.Export(LongCallObjName, t, agent);
      IO.Put("port " & Fmt.Int(port) & "\n");
      IO.Put("Exported target at private port\n");
      IO.Put("Test server running, type <CR> to terminate\n");
      TRY EVAL IO.GetLine() EXCEPT IO.Error => END;
    END;
  ELSE
    VAR
      port                  := Scan.Int(Params.Get(2));
      addr : IP.Address;
      fork                  := NEW(Fork, t := NIL);
      agent: NetObj.Address;
      tt   : Thread.T;
    BEGIN
      IF NOT IP.GetHostByName("localhost", addr) THEN
        IO.Put("No such host \"localhost\"\n");
      ELSE
        agent := TCPNetObj.Locate(IP.Endpoint{addr, port});
        fork.t := NetObj.Import(LongCallObjName, agent);
        IO.Put("Located target at private port\n");
        FOR i := 1 TO 3 DO
          IO.Put("Forking call\n");
          tt := Thread.Fork(fork);
          Thread.Pause(1.0D0);
          Thread.Alert(tt);
          EVAL Thread.Join(tt);
          IO.Put("Joined call\n");
        END;
      END;
    END;
  END;
END Main.

