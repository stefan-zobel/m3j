(* The module HeaderOps of m3j. In cm3 it is UNSAFE: it sends and
   receives the record of a header through LOOPHOLE to characters. Here
   the four bytes of the fixed part (version, operation, and the length
   of the data, little endian) are set and read one by one, in the same
   order. *)

(* Copyright 1992 Digital Equipment Corporation. *)
(* Distributed only by permission. *)
(* Created on Sat Jan 11 15:49:00 PST 1992 by gnelson *)
(* Last modified on Wed Aug 31 16:14:27 PDT 1994 by wobber *)
(*      modified on Sun Jan 12 16:17:07 PST 1992 by meehan *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

MODULE HeaderOps;

IMPORT Atom, AtomList, TCP, ConnFD, Text, Thread, Rd, Wr;

CONST
  CurrentVersion = 1;
  MaxHeaderData = 500;
  FixedSize = 4;     (* version, opCode, length (2 bytes) *)

TYPE
  Header = RECORD
    version, opCode: [0..255];
    length: [0..65535];  (* non-inclusive, little-endian on the wire *)
    data: ARRAY [0..MaxHeaderData-1] OF CHAR;
  END;

VAR ProtocolError: Atom.T;

PROCEDURE Send(t: TCP.T; op: Op; hisEP, myEP: TEXT := NIL)
    RAISES {Wr.Failure, Thread.Alerted} =
  VAR hdr: Header;
      buf: ARRAY [0..FixedSize+MaxHeaderData-1] OF CHAR;
  BEGIN
    hdr.version := CurrentVersion;
    hdr.opCode := ORD(op);
    hdr.length := 0;
    IF op = Op.Connect OR op = Op.Ping THEN
      StuffText(hdr, hisEP);
      StuffText(hdr, myEP);
    END;
    buf[0] := VAL(hdr.version, CHAR);
    buf[1] := VAL(hdr.opCode, CHAR);
    buf[2] := VAL(hdr.length MOD 256, CHAR);
    buf[3] := VAL(hdr.length DIV 256, CHAR);
    SUBARRAY(buf, FixedSize, hdr.length) := SUBARRAY(hdr.data, 0, hdr.length);
    t.put(SUBARRAY(buf, 0, hdr.length+FixedSize));
  END Send;

PROCEDURE StuffText(VAR hdr: Header; t: TEXT) =
  VAR len := MIN(Text.Length(t), NUMBER(hdr.data)-(hdr.length+1));
  BEGIN
    Text.SetChars(SUBARRAY(hdr.data, hdr.length, len), t);
    hdr.data[hdr.length+len] := '\000';
    INC(hdr.length, len+1);
  END StuffText;

PROCEDURE Receive(
    t: TCP.T;
    timeout: LONGREAL;
    VAR myEP: TEXT;
    VAR hisEP: TEXT) : Op
    RAISES {Rd.Failure, Thread.Alerted, ConnFD.TimedOut} =
  PROCEDURE RaiseProtocolError() RAISES {Rd.Failure} =
    BEGIN
      RAISE Rd.Failure(AtomList.List1(ProtocolError));
    END RaiseProtocolError;
    VAR hdr: Header;
      fx: ARRAY [0..FixedSize-1] OF CHAR;
      x, pos: INTEGER;
  BEGIN
    x := t.get(fx, timeout);
    hdr.version := ORD(fx[0]);
    hdr.opCode := ORD(fx[1]);
    hdr.length := ORD(fx[2]) + 256 * ORD(fx[3]);
    IF x # FixedSize OR
           hdr.length > NUMBER(hdr.data) OR
           hdr.version # CurrentVersion THEN
      RaiseProtocolError();
    END;
    IF hdr.length # 0 THEN
      x := t.get(SUBARRAY(hdr.data, 0, hdr.length), timeout);
      IF x # hdr.length THEN RaiseProtocolError(); END;
    END;
    CASE hdr.opCode OF
    | ORD(Op.Connect), ORD(Op.Ping) =>
        pos := 0;
        myEP := ExtractText(hdr, pos);
        hisEP := ExtractText(hdr, pos);
        IF myEP = NIL OR hisEP = NIL THEN RaiseProtocolError(); END;
    | ORD(Op.PingAck), ORD(Op.PingError) =>
    ELSE RaiseProtocolError();
    END;
    RETURN VAL(hdr.opCode, Op);
  END Receive;

PROCEDURE ExtractText(VAR hdr: Header; VAR pos: INTEGER) : TEXT =
  VAR t: TEXT;
  BEGIN
    FOR i := pos TO hdr.length-1 DO
      IF hdr.data[i] = '\000' THEN
        t := Text.FromChars(SUBARRAY(hdr.data, pos, i-pos));
        pos := i + 1;
        RETURN t;
      END;
    END;
    RETURN NIL;
  END ExtractText;

BEGIN
  ProtocolError := Atom.FromText("TCPNetObj.ProtocolError");
END HeaderOps.
