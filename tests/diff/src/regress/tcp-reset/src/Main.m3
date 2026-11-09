(* A connection closed while it has unread bytes is reset. What the other
   side sees when it writes, and when it reads. *)

MODULE Main;

IMPORT Atom, AtomList, Fmt, IO, IP, Rd, TCP, Text, Thread, Wr;

<*FATAL ANY*>

(* the atoms of an error list; numbers of errors of the system as "#" *)
PROCEDURE Atoms (l: AtomList.T): TEXT =
  VAR t := "";
  BEGIN
    WHILE l # NIL DO
      WITH n = Atom.ToText (l.head) DO
        IF Text.Length (n) > 0 AND Text.GetChar (n, 0) IN SET OF CHAR {'0' .. '9'} THEN
          t := t & " #";
        ELSE
          t := t & " " & n;
        END;
      END;
      l := l.tail;
    END;
    RETURN "[" & t & " ]";
  END Atoms;

VAR
  a: IP.Address;
  c := TCP.NewConnector (IP.Endpoint {IP.NullAddress, IP.NullPort});
  ep: IP.Endpoint;
  b: ARRAY [0 .. 999] OF CHAR;

(* A connection whose other end has been reset. *)
PROCEDURE ResetPair (): TCP.T =
  VAR client := TCP.Connect (ep);  server := TCP.Accept (c);
  BEGIN
    server.put (b);
    Thread.Pause (0.2D0);
    TCP.Close (client);
    Thread.Pause (0.2D0);
    RETURN server;
  END ResetPair;

PROCEDURE WriteFirst () =
  VAR t := ResetPair ();  n: CARDINAL;
  BEGIN
    TRY
      FOR i := 1 TO 5 DO
        t.put (b);
        Thread.Pause (0.05D0);
      END;
      IO.Put ("put: no error\n");
    EXCEPT
    | Wr.Failure (l) => IO.Put ("put: Wr.Failure " & Atoms (l) & "\n");
    END;
    TRY
      n := t.get (b);
      IO.Put ("get after put: " & Fmt.Int (n) & "\n");
    EXCEPT
    | Rd.Failure (l) => IO.Put ("get after put: Rd.Failure " & Atoms (l) & "\n");
    END;
    TCP.Close (t);
  END WriteFirst;

PROCEDURE ReadFirst () =
  VAR t := ResetPair ();
  BEGIN
    TRY
      IO.Put ("get: " & Fmt.Int (t.get (b)) & "\n");
    EXCEPT
    | Rd.Failure (l) => IO.Put ("get: Rd.Failure " & Atoms (l) & "\n");
    END;
    TCP.Close (t);
  END ReadFirst;

BEGIN
  EVAL IP.GetHostByName ("localhost", a);
  ep := IP.Endpoint {a, TCP.GetEndPoint (c).port};
  WriteFirst ();
  ReadFirst ();
  TCP.CloseConnector (c);
  IO.Put ("end\n");
END Main.
