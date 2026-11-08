(* Copyright 1993 Digital Equipment Corporation.               *)
(* Distributed only by permission.                             *)
(* NetObjTest.m3 *)
(* Last modified on Mon Dec 12 15:53:31 PST 1994 by wobber *)

(* The test perf of the network objects of cm3
   (m3-comm/netobj/tests/perf), for tools/net_test.py:

     test server       prints "port N", the port of its agent (it
                       tries ports from 9601 on until it can listen),
                       exports the object of the test there and
                       serves until the end of its standard input
     test client N     imports that object from the agent on port N
                       of the local host and runs the test

   The original tries the agent of netobjd first and listens on a
   fixed port, and its client is given the host. Its client runs all the
   tests, as with -short, and prints "done" instead of the time
   of a call or the speed of a stream; the reader test also checks
   the bytes it reads. *)

MODULE Main;

IMPORT Test, XRd, TCPNetObj, IP,
       NetObj, Rd, Wr, IO, Params, NullWr, Text, Fmt, Thread, Scan, Lex,
       FloatMode;

FROM Test IMPORT ObjType;

CONST TestObjName = "TestObj";
      FirstPort   = 9601;

(* server side code *)

TYPE
  T = Test.T OBJECT OVERRIDES
    null := NullCall;
    ten := TenIntCall;
    objarg := OneObjCall;
    rd := GetReader;
    wr := GetWriter;
    newObj := NewObject;
    newReturn := NewReturn;
    pickle := PickleCall;
  END;

  Return = Test.Return OBJECT
    type: ObjType;
    obj: NetObj.T := NIL;
  OVERRIDES
    doIt := ReturnObj;
  END;

  ObjectArg = NetObj.T OBJECT
    x: INTEGER := 0;
  END;

  <* FATAL NetObj.Error *>
  <* FATAL NetObj.Invalid *>
  <* FATAL Thread.Alerted *>
  <* FATAL Wr.Failure *>
  <* FATAL Rd.Failure *>
  <* FATAL IP.Error *>
  <* FATAL TCPNetObj.Failed *>

VAR fixedObj := NEW(ObjectArg);

PROCEDURE NullCall (<*UNUSED*> t: T) =
  BEGIN
  END NullCall;

PROCEDURE TenIntCall (<*UNUSED*> t: T;
              <*UNUSED*> a0, a1, a2, a3, a4, a5, a6, a7, a8, a9: INTEGER) =
  BEGIN
  END TenIntCall;

PROCEDURE OneObjCall (<*UNUSED*> t: T; <*UNUSED*> o: NetObj.T) =
  BEGIN
  END OneObjCall;

PROCEDURE PickleCall (<*UNUSED*> t: T; <*UNUSED*> r: REFANY) =
  BEGIN
  END PickleCall;

PROCEDURE GetReader (<*UNUSED*> t: T): Rd.T =
  BEGIN
    RETURN NEW (XRd.T).init (1000000000)
  END GetReader;

PROCEDURE GetWriter (<*UNUSED*> t: T): Wr.T =
  BEGIN
    RETURN NEW (NullWr.T).init ()
  END GetWriter;


(* client side code *)

CONST BuffSize = 8*1024 - 16;
      ShortStreamTestLength = 5000000;
      ShortCallTestReps = 1000;

VAR buff : ARRAY [0..BuffSize-1] OF CHAR;
    callReps: CARDINAL := ShortCallTestReps;
    streamBytes: CARDINAL := ShortStreamTestLength;

PROCEDURE DoNullCallTest(t: Test.T) =
  VAR nn := callReps;
  BEGIN
    IO.Put("Null call test ... ");
    WHILE nn # 0 DO t.null(); DEC(nn) END;
    IO.Put("done.\n");
  END DoNullCallTest;

PROCEDURE DoTenIntTest(t: Test.T) =
  VAR nn := callReps;
  BEGIN
    IO.Put("Ten integer call test ... ");
    WHILE nn # 0 DO t.ten(1,2,3,4,5,6,7,8,9,10); DEC(nn) END;
    IO.Put("done.\n");
  END DoTenIntTest;

PROCEDURE DoPickleTest(t: Test.T; small: BOOLEAN) =
  VAR nn := callReps;
      r: REFANY;
  BEGIN
    IF small THEN
      IO.Put("REF CHAR pickle call test  ... ");
      r := NEW(REF CHAR);
    ELSE
      IO.Put("Linked list pickle call test  ... ");
      r := InitLinkedList();
    END;
    WHILE nn # 0 DO t.pickle(r); DEC(nn) END;
    IO.Put("done.\n");
  END DoPickleTest;

TYPE LinkedList = REF RECORD last, next: LinkedList; END;

CONST NListElems = 25;

PROCEDURE InitLinkedList(): LinkedList =
  VAR this, first, last: LinkedList := NIL;
  BEGIN
    FOR i := 0 TO NListElems-1 DO
      this := NEW(LinkedList, last := last, next := NIL);
      IF first = NIL THEN
        first := this;
      ELSE
        last.next := this;
      END;
      last := this;
    END;
    RETURN first;
  END InitLinkedList;

PROCEDURE DoObjArgTest(t: Test.T; argType: ObjType) =
  VAR nn := callReps;
      obj: NetObj.T := NIL;
  BEGIN
    CASE argType OF
    | ObjType.Surrogate =>
        IO.Put("Same surrogate object argument test ... ");
        obj := t.newObj();
    | ObjType.Concrete =>
        IO.Put("Same concrete object argument test ... ");
        obj := fixedObj;
    | ObjType.Unique =>
        IO.Put("Unique object argument test ... ");
    END;
    IF argType # ObjType.Unique THEN
      WHILE nn # 0 DO t.objarg(obj); DEC(nn) END;
    ELSE
      WHILE nn # 0 DO t.objarg(NEW(ObjectArg)); DEC(nn) END;
    END;
    IO.Put("done.\n");
  END DoObjArgTest;

PROCEDURE DoObjRetTest(t: Test.T; argType: ObjType) =
  VAR nn := callReps;
      cl: Test.Return;
  BEGIN
    CASE argType OF
    | ObjType.Surrogate =>
        IO.Put("Same surrogate object result test ... ");
    | ObjType.Concrete =>
        IO.Put("Same concrete object result test ... ");
    | ObjType.Unique =>
        IO.Put("Unique object result test ... ");
    END;
    cl := t.newReturn(argType, fixedObj);
    WHILE nn # 0 DO EVAL cl.doIt(); DEC(nn) END;
    IO.Put("done.\n");
  END DoObjRetTest;

PROCEDURE DoReaderTest(t: Test.T) =
  VAR rd: Rd.T;
      nn := streamBytes;
      n, bad: CARDINAL := 0;
  BEGIN
    IO.Put("Reader test ... ");
    rd := t.rd();
    WHILE nn # 0 DO
      n := Rd.GetSub (rd, buff);
      FOR i := 0 TO n - 1 DO
        IF buff[i] # 'X' THEN INC(bad) END;
      END;
      DEC (nn, MIN (nn, BuffSize))
    END;
    IO.Put("done, other bytes than X: " & Fmt.Int(bad) & ".\n");
  END DoReaderTest;

PROCEDURE DoWriterTest(t: Test.T) =
  VAR wr: Wr.T;
      nn := streamBytes;
  BEGIN
    IO.Put("Writer test ... ");
    wr := t.wr();
    WHILE nn # 0 DO
      Wr.PutString (wr, buff);
      DEC (nn, MIN (nn, BuffSize))
    END;
    Wr.Flush (wr);
    IO.Put("done.\n");
  END DoWriterTest;

PROCEDURE NewReturn(<*UNUSED*> t: T;
                   type: ObjType; surr: NetObj.T): Test.Return =
  VAR res := NEW(Return, type := type);
  BEGIN
    CASE type OF
    | ObjType.Surrogate => res.obj := surr;
    | ObjType.Concrete => res.obj := fixedObj;
    | ObjType.Unique =>
    END;
    RETURN res;
  END NewReturn;

PROCEDURE NewObject (<*UNUSED*> t: T) : NetObj.T =
  BEGIN
    RETURN NEW(ObjectArg);
  END NewObject;

PROCEDURE ReturnObj(r: Return): NetObj.T =
  BEGIN
    CASE r.type OF
    | ObjType.Surrogate, ObjType.Concrete => RETURN r.obj;
    | ObjType.Unique => RETURN NEW(ObjectArg);
    END;
  END ReturnObj;


(* common code *)

<* FATAL Lex.Error, FloatMode.Trap *>

BEGIN
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
        DoNullCallTest(t);
        DoTenIntTest(t);
        DoObjArgTest(t, ObjType.Surrogate);
        DoObjArgTest(t, ObjType.Concrete);
        DoObjArgTest(t, ObjType.Unique);
        DoObjRetTest(t, ObjType.Surrogate);
        DoObjRetTest(t, ObjType.Concrete);
        DoObjRetTest(t, ObjType.Unique);
        DoPickleTest(t, TRUE);
        DoPickleTest(t, FALSE);
        DoReaderTest(t);
        DoWriterTest(t);
      END;
    END;
  END;
END Main.

