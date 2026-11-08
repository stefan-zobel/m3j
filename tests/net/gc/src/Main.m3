(* The distributed collection of network objects, between a server and a
   client built by cm3 or m3j (tools/net_test.py):

     gc server           prints "port N", the port of its agent (it tries
                         ports from 9601 on until it can listen), exports
                         a Test.T there and serves until the end of its
                         standard input
     gc client N         imports that object from the agent on port N of
                         the local host and runs the test
     gc client N count   also prints how many of the objects that were
                         dropped the collectors have freed

   A program tells the owner of an object when it gets a surrogate of the
   object (a dirty call) and when its collector has freed the surrogate (a
   clean call); the owner keeps an object that another program has a
   surrogate of. The client gets objects of the server and drops most of
   them, gets objects again that it dropped, and has the server keep
   objects of the client and drop them; the objects that the programs
   still hold must answer at each step, whenever the clean calls of the
   others come. The collector of cm3 frees objects in its own time (on
   AMD64_NT it pins the pages of small objects that the stacks seem to
   refer to, and calls only some of the cleanup procedures of weak
   references), so the output does not depend on what the collectors
   free; with "count", the client waits at most 60 seconds at each step
   for the collectors to free all that was dropped, and prints how many
   objects are left, counted with weak references by the program that
   made them (tools/net_test.py runs it with a server and a client of
   m3j, which must free them all). *)

MODULE Main;

IMPORT Test, NetObj, TCPNetObj, IP, Thread, Params, Scan, IO, Fmt, Text,
       WeakRef, RTCollector, AtomList, Atom, Lex, FloatMode;

TYPE
  Impl = Test.T OBJECT
  OVERRIDES
    make := Make;
    get := Get;
    live := Live;
    hold := Hold;
    drop := Drop;
  END;

  ObjImpl = Test.Obj OBJECT
    n: INTEGER;
  OVERRIDES
    id := Id;
  END;

  Held = REF RECORD
    o: Test.Obj;
    next: Held;
  END;

CONST
  NServer = 100;  (* objects of the server that the client makes *)
  NKept = 10;     (* of which it keeps some for a while *)
  NClient = 20;   (* objects of the client that the server keeps *)
  Rounds = 20;    (* times the client gets objects again *)

VAR
  mu := NEW(MUTEX);
  made, freed := 0;  (* objects made for the other program, and freed *)
  held: Held := NIL; (* in the server, the objects of the client *)
  kept: ARRAY [0 .. NServer - 1] OF Test.Obj;  (* in the server, for get *)

(* both *)

PROCEDURE Freed (<*UNUSED*> READONLY w: WeakRef.T; <*UNUSED*> r: REFANY) =
  BEGIN
    LOCK mu DO INC(freed) END;
  END Freed;

PROCEDURE NewObj (n: INTEGER): Test.Obj =
  VAR o := NEW(ObjImpl, n := n);
  BEGIN
    EVAL WeakRef.FromRef(o, Freed);
    LOCK mu DO INC(made) END;
    RETURN o;
  END NewObj;

PROCEDURE LocalLive (): INTEGER =
  BEGIN
    RTCollector.Collect();
    LOCK mu DO RETURN made - freed END;
  END LocalLive;

PROCEDURE Id (self: ObjImpl): INTEGER =
  BEGIN
    RETURN self.n;
  END Id;

(* the server *)

PROCEDURE Make (<*UNUSED*> self: Impl; id: INTEGER): Test.Obj =
  BEGIN
    RETURN NewObj(id);
  END Make;

PROCEDURE Get (<*UNUSED*> self: Impl; id: INTEGER): Test.Obj =
  BEGIN
    RETURN kept[id];
  END Get;

PROCEDURE Live (<*UNUSED*> self: Impl): INTEGER =
  BEGIN
    RETURN LocalLive();
  END Live;

PROCEDURE Hold (<*UNUSED*> self: Impl; o: Test.Obj) =
  BEGIN
    LOCK mu DO held := NEW(Held, o := o, next := held) END;
  END Hold;

PROCEDURE Drop (<*UNUSED*> self: Impl): INTEGER
  RAISES {NetObj.Error, Thread.Alerted} =
  VAR h: Held;  sum := 0;
  BEGIN
    LOCK mu DO h := held; held := NIL END;
    WHILE h # NIL DO INC(sum, h.o.id()); h := h.next END;
    RTCollector.Collect();
    RETURN sum;
  END Drop;

PROCEDURE Server () =
  <*FATAL NetObj.Error, Thread.Alerted*>
  VAR port := 9601;  agent: NetObj.Address := NIL;
  BEGIN
    FOR i := 0 TO NServer - 1 DO kept[i] := NEW(ObjImpl, n := i) END;
    WHILE agent = NIL DO
      TRY
        agent := TCPNetObj.Listen(port)
      EXCEPT
        TCPNetObj.Failed => INC(port)
      END
    END;
    NetObj.Export("gc", NEW(Impl), agent);
    IO.Put("port " & Fmt.Int(port) & "\n");
    TRY EVAL IO.GetLine() EXCEPT IO.Error => END;
    IO.Put("server done\n");
  END Server;

(* the client; the steps are procedures of their own, so that no frame of
   the client holds an object that it dropped *)

VAR
  objs: ARRAY [0 .. NServer - 1] OF Test.Obj;
  mine: ARRAY [1 .. NClient] OF Test.Obj;
  count := FALSE;

PROCEDURE Collect () =
  BEGIN
    FOR i := 1 TO 5 DO EVAL LocalLive(); Thread.Pause(0.1d0) END;
  END Collect;

PROCEDURE MakeObjects (t: Test.T) RAISES {NetObj.Error, Thread.Alerted} =
  VAR sum := 0;
  BEGIN
    FOR i := 0 TO NServer - 1 DO objs[i] := t.make(i) END;
    FOR i := 0 TO NServer - 1 DO INC(sum, objs[i].id()) END;
    IO.Put("made " & Fmt.Int(NServer) & " objects of the server, sum of ids "
           & Fmt.Int(sum) & "\n");
  END MakeObjects;

PROCEDURE DropObjects (from, to: INTEGER) =
  BEGIN
    FOR i := from TO to DO objs[i] := NIL END;
  END DropObjects;

PROCEDURE SumKept () : INTEGER RAISES {NetObj.Error, Thread.Alerted} =
  VAR sum := 0;
  BEGIN
    FOR i := 0 TO NKept - 1 DO INC(sum, objs[i].id()) END;
    RETURN sum;
  END SumKept;

PROCEDURE SameSurrogate (t: Test.T): BOOLEAN
  RAISES {NetObj.Error, Thread.Alerted} =
  VAR a := t.get(7);  b := t.get(7);
  BEGIN
    RETURN a = b AND a.id() = 7;
  END SameSurrogate;

PROCEDURE GetAgain (t: Test.T; round: INTEGER): BOOLEAN
  RAISES {NetObj.Error, Thread.Alerted} =
  (* gets objects of the server, which the client got and dropped in the
     rounds before, when their clean calls may be on their way *)
  VAR ok := TRUE;
  BEGIN
    FOR i := 0 TO 9 DO
      WITH id = (round * 7 + i * 3) MOD NServer DO
        IF t.get(id).id() # id THEN ok := FALSE END;
      END;
    END;
    RETURN ok;
  END GetAgain;

PROCEDURE GetRounds (t: Test.T): BOOLEAN RAISES {NetObj.Error, Thread.Alerted} =
  VAR ok := TRUE;
  BEGIN
    FOR r := 1 TO Rounds DO
      IF NOT GetAgain(t, r) THEN ok := FALSE END;
      EVAL LocalLive();
      IF r MOD 5 = 0 THEN Thread.Pause(0.1d0) END;
    END;
    RETURN ok;
  END GetRounds;

PROCEDURE GiveMine (t: Test.T): INTEGER RAISES {NetObj.Error, Thread.Alerted} =
  BEGIN
    FOR i := 1 TO NClient DO t.hold(mine[i]) END;
    RETURN t.drop();
  END GiveMine;

PROCEDURE GiveNew (t: Test.T): INTEGER RAISES {NetObj.Error, Thread.Alerted} =
  BEGIN
    FOR i := 1 TO NClient DO t.hold(NewObj(i)) END;
    RETURN t.drop();
  END GiveNew;

PROCEDURE WaitServer (t: Test.T; want: INTEGER): INTEGER
  RAISES {NetObj.Error, Thread.Alerted} =
  VAR n := t.live();  tries := 0;
  BEGIN
    WHILE n # want AND tries < 600 DO
      EVAL LocalLive();
      Thread.Pause(0.1d0);
      n := t.live();
      INC(tries);
    END;
    RETURN n;
  END WaitServer;

PROCEDURE WaitClient (t: Test.T; want: INTEGER): INTEGER
  RAISES {NetObj.Error, Thread.Alerted} =
  VAR n := LocalLive();  tries := 0;
  BEGIN
    WHILE n # want AND tries < 600 DO
      EVAL t.live();  (* the server's collector frees the surrogates *)
      Thread.Pause(0.1d0);
      n := LocalLive();
      INC(tries);
    END;
    RETURN n;
  END WaitClient;

PROCEDURE ShowError (a: AtomList.T) =
  BEGIN
    IO.Put("NetObj.Error:");
    WHILE a # NIL DO IO.Put(" " & Atom.ToText(a.head)); a := a.tail END;
    IO.Put("\n");
  END ShowError;

PROCEDURE Client (port: INTEGER) =
  <*FATAL Thread.Alerted, IP.Error*>
  VAR
    t: Test.T;
    addr: IP.Address;
    agent: NetObj.Address;
  BEGIN
    TRY
      EVAL IP.GetHostByName("localhost", addr);
      agent := TCPNetObj.Locate(IP.Endpoint{addr, port});
      t := NetObj.Import("gc", agent);
      MakeObjects(t);
      DropObjects(NKept, NServer - 1);
      Collect();
      IO.Put("the " & Fmt.Int(NKept) & " objects kept answer, sum of ids "
             & Fmt.Int(SumKept()) & "\n");
      IF count THEN
        IO.Put("live on the server after the client dropped "
               & Fmt.Int(NServer - NKept) & ": "
               & Fmt.Int(WaitServer(t, NKept)) & "\n");
      END;
      IO.Put("the same object twice is the same surrogate: "
             & Fmt.Bool(SameSurrogate(t)) & "\n");
      IO.Put("objects got again after they were dropped answer: "
             & Fmt.Bool(GetRounds(t)) & "\n");
      DropObjects(0, NKept - 1);
      Collect();
      IF count THEN
        IO.Put("live on the server after the client dropped all: "
               & Fmt.Int(WaitServer(t, 0)) & "\n");
      END;
      FOR i := 1 TO NClient DO mine[i] := NEW(ObjImpl, n := i) END;
      IO.Put("the server called back the objects of the client: "
             & Fmt.Int(GiveMine(t)) & "\n");
      Collect();
      EVAL t.live();
      Thread.Pause(0.5d0);
      IO.Put("and again after it dropped them: "
             & Fmt.Int(GiveMine(t)) & "\n");
      IF count THEN
        IO.Put("new objects of the client that the server called back: "
               & Fmt.Int(GiveNew(t)) & "\n");
        IO.Put("live on the client after the server dropped them: "
               & Fmt.Int(WaitClient(t, 0)) & "\n");
      END;
      IO.Put("client done\n");
    EXCEPT
    | NetObj.Error(x) => ShowError(x)
    END;
  END Client;

<*FATAL Lex.Error, FloatMode.Trap*>
BEGIN
  IF Text.Equal(Params.Get(1), "server") THEN
    Server();
  ELSE
    count := Params.Count > 3 AND Text.Equal(Params.Get(3), "count");
    Client(Scan.Int(Params.Get(2)));
  END;
END Main.
