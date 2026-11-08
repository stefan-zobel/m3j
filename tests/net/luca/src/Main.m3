(* Copyright 1993 Digital Equipment Corporation.               *)
(* Distributed only by permission.                             *)
(* LucaTest.m3 *)
(* Last modified on Fri Mar 11 15:18:58 PST 1994 by wobber *)

(* The test luca of the network objects of cm3
   (m3-comm/netobj/tests/luca), for tools/net_test.py:

     test server       prints "port N", the port of its agent (it
                       tries ports from 9601 on until it can listen),
                       exports the object of the test there and
                       serves until the end of its standard input
     test client N     imports that object from the agent on port N
                       of the local host and runs the test

   The original tries the agent of netobjd first and listens on a
   fixed port, and its client is given the host. Its client writes the
   file that the server opens, in their common directory, and
   prints whether it read the same text from the readers of the
   server instead of the text; it then asks for a file that does
   not exist, and prints how many atoms the list of the error has
   instead of their texts, which depend on the system and its
   language. *)

MODULE Main;

IMPORT Atom, AtomList, Test, TCPNetObj, IP, OSError, FileRd, FileWr,
       NetObj, IO, Params, Fmt, Thread, Rd, Wr, Text, TextWr, Scan,
       Lex, FloatMode;

CONST TestObjName = "LucaTestObj";
      FirstPort   = 9601;

TYPE
  T = Test.T OBJECT OVERRIDES
    getRd := DoGetRd;
    getRdEmbedded := DoGetRdEmbedded;
  END;

<* FATAL Thread.Alerted *>

(* server-side code *)

PROCEDURE DoGetRd(<*UNUSED*> t: T; path: TEXT): Rd.T
    RAISES {NetObj.Error, OSError.E} =
  BEGIN
    RETURN FileRd.Open(path);
  END DoGetRd;

PROCEDURE DoGetRdEmbedded(<*UNUSED*> t: T; path: TEXT): Test.RdPkl
    RAISES {NetObj.Error, OSError.E} =
  BEGIN
    RETURN NEW(Test.RdPkl, rd := FileRd.Open(path));
  END DoGetRdEmbedded;

(* client-side code *)

VAR sample: TEXT;

PROCEDURE WriteSample(path: TEXT) RAISES {OSError.E} =
  VAR wr := TextWr.New();
      fwr: Wr.T;
      <* FATAL Wr.Failure *>
  BEGIN
    FOR i := 1 TO 500 DO
      Wr.PutText(wr, "This is line " & Fmt.Int(i) & " of the sample text"
                       & Fmt.Pad("", i MOD 40, '.') & "\n");
    END;
    sample := TextWr.ToText(wr);
    fwr := FileWr.Open(path);
    Wr.PutText(fwr, sample);
    Wr.Close(fwr);
  END WriteSample;

PROCEDURE TestPath(t: Test.T; path: TEXT) RAISES {OSError.E, NetObj.Error} =
  VAR rd: Rd.T;
  BEGIN
    IO.Put("Opening " & path & " ... ");
    rd := t.getRd(path);
    IO.Put("ok\n");
    SuckRd(rd);
    IO.Put("Opening " & path & " ... ");
    rd := t.getRdEmbedded(path).rd;
    IO.Put("ok\n");
    SuckRd(rd);
  END TestPath;

PROCEDURE SuckRd(rd: Rd.T) =
  VAR buf: ARRAY [0..99] OF CHAR;
      n: CARDINAL;
      wr := TextWr.New();
      got: TEXT;
      <* FATAL Wr.Failure *>
  BEGIN
    TRY
      TRY
        LOOP
          n := Rd.GetSub(rd, buf);
          Wr.PutString(wr, SUBARRAY(buf, 0, n));
          IF n # NUMBER(buf) THEN EXIT; END;
        END;
      FINALLY
        Rd.Close(rd);
      END;
      got := TextWr.ToText(wr);
      IO.Put("Read " & Fmt.Int(Text.Length(got)) & " chars, same text: "
             & Fmt.Bool(Text.Equal(got, sample)) & "\n");
    EXCEPT
    | Rd.Failure(x) =>
        IO.Put("Rd.Failure(" & ErrStr(x) & ")\n");
    END;
  END SuckRd;

(* common code *)

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
          WriteSample("luca.txt");
          TestPath(t, "luca.txt");
          TestPath(t, "missing.txt");
        END;
      END;
    END;
  EXCEPT
  | IP.Error => IO.Put("IP.Error\n");
  | TCPNetObj.Failed => IO.Put("TCPNetObj.Failed\n");
  | NetObj.Invalid => IO.Put("NetObj.Invalid\n");
  | NetObj.Error(x) => IO.Put("NetObj.Error(" & ErrStr(x) & ")\n");
  | OSError.E(x) =>
      IO.Put("OSError.E(" & Fmt.Int(AtomList.Length(x)) & " atoms)\n");
  END;
END Main.

