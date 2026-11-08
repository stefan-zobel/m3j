(* Copyright 1993 Digital Equipment Corporation.               *)
(* Distributed only by permission.                             *)
(* NetObjTest.m3 *)
(* Last modified on Tue Aug 24 17:01:51 PDT 1993 by wobber *)

(* The test echorw of the network objects of cm3
   (m3-comm/netobj/tests/echorw), for tools/net_test.py:

     test server       prints "port N", the port of its agent (it
                       tries ports from 9601 on until it can listen),
                       exports the object of the test there and
                       serves until the end of its standard input
     test client N     imports that object from the agent on port N
                       of the local host and runs the test

   The original tries the agent of netobjd first and listens on a
   fixed port, and its client is given the host. Its client passes a
   reader of a text of many lines and a text writer instead of
   its standard input and output, and prints whether the writer
   got the text back. *)

MODULE Main;

IMPORT EchoRW, TCPNetObj, IP, NetObj, Rd, Wr, IO, Params, Fmt, Thread,
       Text, TextRd, TextWr, Scan, Lex, FloatMode;

CONST EchoObjName = "EchoObj";
      FirstPort   = 9601;

(* server side code *)

TYPE
  T = EchoRW.T OBJECT OVERRIDES
    echo := DoEcho;
  END;

  Copier = Thread.Closure OBJECT
    rd: Rd.T;
    wr: Wr.T;
  OVERRIDES
    apply := DoCopy;
  END;

  <* FATAL NetObj.Error *>
  <* FATAL NetObj.Invalid *>
  <* FATAL Thread.Alerted *>
  <* FATAL Wr.Failure *>
  <* FATAL Rd.Failure *>
  <* FATAL IP.Error *>
  <* FATAL TCPNetObj.Failed *>

PROCEDURE DoEcho (<*UNUSED*> t: T; rd: Rd.T; wr: Wr.T) =
  BEGIN
    EVAL Thread.Fork(NEW(Copier, rd := rd, wr := wr));
  END DoEcho;

PROCEDURE DoCopy(c: Copier): REFANY =
  BEGIN
    TRY
      LOOP
        Wr.PutText(c.wr, Rd.GetLine(c.rd) & "\n");
        Wr.Flush(c.wr);
      END;
    EXCEPT
    | Rd.Failure, Rd.EndOfFile, Wr.Failure, Thread.Alerted =>
    END;
    RETURN NIL;
  END DoCopy;

(* client side code *)

CONST NLines = 300;

PROCEDURE Input (): TEXT =
  (* lines of many lengths, one of them half as long as the buffers of
     the connections (cm3's surrogate writer writes past the end of its
     buffer when it is given almost a buffer full after a flush) *)
  VAR wr := TextWr.New();
  BEGIN
    FOR i := 1 TO NLines DO
      Wr.PutText(wr, "line " & Fmt.Int(i) & ": ");
      IF i = NLines DIV 2 THEN
        Wr.PutText(wr, Fmt.Pad("", 4000, '#'));
      ELSE
        Wr.PutText(wr, Fmt.Pad("", i MOD 70, VAL(ORD('a') + i MOD 26, CHAR)));
      END;
      Wr.PutChar(wr, '\n');
    END;
    RETURN TextWr.ToText(wr);
  END Input;

PROCEDURE Client (t: EchoRW.T) =
  VAR in := Input();
      wr := TextWr.New();
      got := "";
      tries := 0;
  BEGIN
    t.echo(TextRd.New(in), wr);
    (* the server copies the lines in a thread of its own *)
    WHILE Text.Length(got) < Text.Length(in) AND tries < 1200 DO
      Thread.Pause(5.0d-2);
      got := got & TextWr.ToText(wr);
      INC(tries);
    END;
    IO.Put("Echoed " & Fmt.Int(Text.Length(got)) & " of "
           & Fmt.Int(Text.Length(in)) & " chars, same text: "
           & Fmt.Bool(Text.Equal(got, in)) & "\n");
  END Client;

<* FATAL Lex.Error, FloatMode.Trap *>

(* common code *)

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
      NetObj.Export (EchoObjName, NEW(T), agent);
      IO.Put("port " & Fmt.Int(port) & "\n");
      IO.Put("Exported target at private port\n");
    END;
    IO.Put("Echo server running, type <CR> to terminate\n");
    TRY EVAL IO.GetLine() EXCEPT IO.Error => END;
  ELSE
    VAR port := Scan.Int(Params.Get(2));
        addr: IP.Address;
        t: EchoRW.T;
        agent: NetObj.Address;
    BEGIN
      IF NOT IP.GetHostByName("localhost", addr) THEN
        IO.Put("No such host \"localhost\"\n");
      ELSE
        agent := TCPNetObj.Locate(IP.Endpoint{addr, port});
        t := NetObj.Import(EchoObjName, agent);
        IO.Put("Located target at private port\n");
        Client(t);
      END;
    END;
  END;
END Main.

