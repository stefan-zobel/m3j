(* The module StubLib of m3j. In cm3 it is UNSAFE: it views the buffers
   of the message readers and writers as headers, integers and object
   IDs through LOOPHOLE, and the characters of texts through pointers.
   Here the bytes of the buffers are read and set one by one, in the
   data representation of cm3 on AMD64_NT (64-bit integers, little
   endian, IEEE floating point); numbers from a sender with another
   representation are converted as in cm3. The bytes that align a
   number are zero (cm3 leaves what the buffer held). A text is written
   with 16-bit characters if it has characters beyond the first 256
   ("Text.HasWideChars"), since m3j has no classes of texts. *)

(* Copyright 1992 Digital Equipment Corporation.               *)
(* Distributed only by permission.                             *)
(* StubLib.m3 *)
(* Last modified on Tue Jan 31 08:47:30 PST 1995 by kalsow *)
(*      modified on Wed Aug 31 16:52:41 PDT 1994 by wobber *)
(*      modified on Wed Feb 10 17:10:17 PST 1993 by owicki *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

MODULE StubLib EXPORTS StubLib, NetObjF;

IMPORT NetObj, NetObjRep, NetObjRT, Pickle, Pickle2, Protocol, Transport,
       TransportUtils, Voucher, WireRep;
IMPORT Atom, AtomList, Rd, RTType, Wr, Text,
       Thread, RdClass, WrClass, UnsafeRd, UnsafeWr, FloatMode, Swap;
IMPORT PickleStubs, WideChar, M3jFloat, Word;
FROM Word IMPORT And, Or, LeftShift;

FROM Protocol IMPORT Op;

REVEAL RdClass.Private <: MUTEX;
REVEAL WrClass.Private <: MUTEX;

CONST ReplacementWt = 16_FFFD;

(* most if not all of the following could be inline in stub code *)

(* Since clients of "Conn" must avoid accessing them concurrently,
   we operate on the embedded streams without locking them.
*)

TYPE UInt32 = BITS 32 FOR [0 .. 16_7FFFFFFF];

(* The sizes and the places of the fields of the headers of "Protocol"
   (a "DataRep" is "private", "intFmt", "floatFmt", "charSet"). *)
CONST
  HeaderSize = 4;
  MsgHeaderSize = 8;       (* hdr, pad *)
  CallHeaderSize = 24;     (* hdr, prot, obj *)
  ProtOffset = 4;
  ObjOffset = 8;
  WireRepSize = 16;

TYPE ObjectStack = RECORD
    pos: CARDINAL := 0;
    objs: REF ARRAY OF NetObj.T := NIL;
  END;

CONST DefaultObjStackSize = 8;

REVEAL
  Conn = Transport.Conn BRANDED OBJECT
    protocol: StubProtocol := 0;
    objStack: ObjectStack := ObjectStack {};
    inObj: BOOLEAN := FALSE;
  END;

(* The field "t.objStack" is used to record the set of network objects
   marshalled during any single method invocation (at either client or
   server).  This record is required for cleanup at the termination of
   the call.   The "t.objStack" field is managed by the network object
   runtime and should not be modified by any transport implementation. *)

REVEAL
  Transport.T = TransportUtils.Public BRANDED OBJECT
  OVERRIDES
    serviceCall := ServiceCall;
  END;

(* Pickle.Reader and Pickle.Writer subtypes and free list headers *)

TYPE SpecWr = Pickle.Writer OBJECT
  c: Conn;
  next: SpecWr;
  END;

TYPE SpecWr2 = Pickle2.Writer OBJECT
  c: Conn;
  next: SpecWr2;
  END;

TYPE SpecRd = Pickle.Reader OBJECT
  c: Conn;
  rep: DataRep;
  next: SpecRd;
  END;

TYPE SpecRd2 = Pickle2.Reader OBJECT
  c: Conn;
  rep: DataRep;
  next: SpecRd2;
  END;

VAR mu: MUTEX;
    freeWr: SpecWr;
    freeRd: SpecRd;
    freeWr2: SpecWr2;
    freeRd2: SpecRd2;


(* the bytes of the buffers *)

PROCEDURE RdIndex(rd: Rd.T): CARDINAL =
  (* the index in "rd.buff" of the character at "rd.cur" *)
  BEGIN
    RETURN rd.st + rd.cur - rd.lo;
  END RdIndex;

PROCEDURE WrIndex(wr: Wr.T): CARDINAL =
  (* the index in "wr.buff" of the character at "wr.cur" *)
  BEGIN
    RETURN wr.st + wr.cur - wr.lo;
  END WrIndex;

PROCEDURE GetNum(READONLY b: ARRAY OF CHAR; i, n: CARDINAL;
                 bigEndian: BOOLEAN): Word.T =
  (* the "n" bytes at "b[i]" as an unsigned number *)
  VAR w: Word.T := 0;
  BEGIN
    IF bigEndian THEN
      FOR k := 0 TO n-1 DO w := Or(LeftShift(w, 8), ORD(b[i+k])); END;
    ELSE
      FOR k := n-1 TO 0 BY -1 DO w := Or(LeftShift(w, 8), ORD(b[i+k])); END;
    END;
    RETURN w;
  END GetNum;

PROCEDURE PutNum(VAR b: ARRAY OF CHAR; i, n: CARDINAL; w: Word.T) =
  (* the low "n" bytes of "w" at "b[i]", little endian *)
  BEGIN
    FOR k := 0 TO n-1 DO
      b[i+k] := VAL(Word.Extract(w, 8*k, 8), CHAR);
    END;
  END PutNum;

PROCEDURE SignExtend32(w: Word.T): INTEGER =
  BEGIN
    IF And(w, 16_80000000) # 0 THEN
      RETURN Or(w, Word.Not(16_FFFFFFFF));
    ELSE
      RETURN And(w, 16_FFFFFFFF);
    END;
  END SignExtend32;

PROCEDURE GetRep(READONLY b: ARRAY OF CHAR; i: CARDINAL): DataRep =
  BEGIN
    RETURN DataRep{private := ORD(b[i]), intFmt := ORD(b[i+1]),
                   floatFmt := ORD(b[i+2]), charSet := ORD(b[i+3])};
  END GetRep;

PROCEDURE PutRep(VAR b: ARRAY OF CHAR; i: CARDINAL; READONLY rep: DataRep) =
  BEGIN
    b[i] := VAL(rep.private, CHAR);
    b[i+1] := VAL(rep.intFmt, CHAR);
    b[i+2] := VAL(rep.floatFmt, CHAR);
    b[i+3] := VAL(rep.charSet, CHAR);
  END PutRep;

PROCEDURE PutMsgHeader(wr: Wr.T; op: Op) =
  (* a "Protocol.MsgHeader" for "op" at "wr.cur"; the caller makes sure
     that there is room for it *)
  VAR i := WrIndex(wr);
  BEGIN
    PutRep(wr.buff^, i, NativeRep);
    wr.buff[i] := VAL(ORD(op), CHAR);
    PutNum(wr.buff^, i + HeaderSize, MsgHeaderSize - HeaderSize, 0);
    INC(wr.cur, MsgHeaderSize);
  END PutMsgHeader;

PROCEDURE GetMsgHeader(rd: Rd.T): DataRep =
  (* the "hdr" of the "Protocol.MsgHeader" at "rd.cur"; the caller makes
     sure that it is in the buffer *)
  VAR rep := GetRep(rd.buff^, RdIndex(rd));
  BEGIN
    INC(rd.cur, MsgHeaderSize);
    RETURN rep;
  END GetMsgHeader;


PROCEDURE ServiceCall(<*UNUSED*> tt: Transport.T; c: Conn) : BOOLEAN
    RAISES {Thread.Alerted} =
  BEGIN
      TRY
        VAR
          dispatcher: Dispatcher;
          obj: NetObj.T;
          rd := c.rd;
          hdr: DataRep;
          prot: StubProtocol;
          w: WireRep.T;
          i: CARDINAL;
        BEGIN
          IF rd.hi - rd.cur < CallHeaderSize THEN
            RaiseUnmarshalFailure();
          END;
          i := RdIndex(rd);
          hdr := GetRep(rd.buff^, i);
          IF hdr.private # ORD(Op.MethodCall) THEN
            RaiseUnmarshalFailure();
          END;
          INC(rd.cur, CallHeaderSize);
          (* the protocol is in the byte order of the caller *)
          prot := SignExtend32(GetNum(rd.buff^, i + ProtOffset, 4,
                                      hdr.intFmt >= BigEndianFmt));
          FOR k := 0 TO WireRepSize-1 DO
            w.byte[k] := ORD(rd.buff[i + ObjOffset + k]);
          END;
          c.protocol := prot;
          obj := NetObjRT.FindTarget(w, prot, dispatcher);
          TRY
            c.objStack.pos := 0;
            dispatcher(c, obj, hdr, prot);
            IF (c.objStack.pos # 0) THEN
              c.wr.nextMsg();
              IF NOT rd.nextMsg() OR rd.hi - rd.cur < MsgHeaderSize THEN
                RETURN FALSE;
              END;
              VAR hh := GetMsgHeader(rd);
              BEGIN
                IF hh.private # ORD(Op.ResultAck) THEN RETURN FALSE; END;
              END;
            ELSE
              c.wr.nextMsg();
            END;
          FINALLY
            IF (c.objStack.pos # 0) THEN
              NetObjRT.Unpin(SUBARRAY(c.objStack.objs^, 0, c.objStack.pos));
              FOR i := 0 TO c.objStack.pos-1 DO
                c.objStack.objs[i] := NIL;
              END;
            END;
          END;
        END;
      EXCEPT
      | Rd.Failure, Wr.Failure => RETURN FALSE;
      | NetObj.Error(ec) =>
          TRY
            (* this test checks whether we have started marshalling results *)
            IF c.wr.cur = 0 THEN
              PutMsgHeader(c.wr, Op.CallFailed);
              OutRef(c, ec);
            END;
            c.wr.nextMsg();
          EXCEPT
          | Wr.Failure => RETURN FALSE;
          END;
      END;
    RETURN TRUE;
  END ServiceCall;


(* exports to StubLib *)

PROCEDURE StartCall(obj: NetObj.T; stubProt: StubProtocol) : Conn
    RAISES {NetObj.Error, Thread.Alerted} =
  VAR c := NARROW(obj.r, Transport.Location).new();
  BEGIN
    c.objStack.pos := 0;
    c.inObj := FALSE;
    c.protocol := stubProt;
    VAR wr := c.wr;
        i := WrIndex(wr);
    BEGIN
      <* ASSERT (wr.hi - wr.cur >= CallHeaderSize) *>
      INC(wr.cur, CallHeaderSize);
      PutRep(wr.buff^, i, NativeRep);
      wr.buff[i] := VAL(ORD(Op.MethodCall), CHAR);
      PutNum(wr.buff^, i + ProtOffset, 4, stubProt);
      FOR k := 0 TO WireRepSize-1 DO
        wr.buff[i + ObjOffset + k] := VAL(obj.w.byte[k], CHAR);
      END;
    END;
    RETURN c;
  END StartCall;

PROCEDURE AwaitResult(c: Conn) : DataRep
    RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted} =
  VAR hdr: DataRep;
      rd := c.rd;
  BEGIN
    c.wr.nextMsg();
    TRY
      IF NOT rd.nextMsg() OR rd.hi - rd.cur < MsgHeaderSize THEN
        RaiseUnmarshalFailure();
      END;
      hdr := GetMsgHeader(rd);
    EXCEPT
    | Thread.Alerted => RAISE NetObj.Error(AtomList.List1(NetObj.Alerted));
    END;
    CASE hdr.private OF
    | ORD(Op.Return) =>
    | ORD(Op.CallFailed) =>
        RAISE NetObj.Error(InRef(c, hdr, TYPECODE(AtomList.T)));
    ELSE
      RaiseUnmarshalFailure();
    END;
    RETURN hdr;
  END AwaitResult;

PROCEDURE EndCall(c: Conn; reUse: BOOLEAN)
    RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    TRY
      IF c.objStack.pos # 0 THEN
        NetObjRT.Unpin(SUBARRAY(c.objStack.objs^, 0, c.objStack.pos));
        FOR i := 0 TO c.objStack.pos-1 DO
          c.objStack.objs[i] := NIL;
        END;
      END;
      IF reUse AND c.inObj (* OR NOT UnsafeRd.FastEOF(c.rd) *) THEN
        PutMsgHeader(c.wr, Op.ResultAck);
        c.wr.nextMsg();
      END;
    FINALLY
      c.loc.free(c, reUse);
    END;
  END EndCall;

PROCEDURE StartResult(c: Conn) =
  BEGIN
    PutMsgHeader(c.wr, Op.Return);
  END StartResult;

PROCEDURE InChars(c: Conn; rep: DataRep; VAR arr: ARRAY OF CHAR)
    RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  BEGIN
    IF NUMBER(arr) <= 0 THEN RETURN; END;
    IF CharSetField(rep.charSet) # CharSetField(NativeRep.charSet) THEN
      RaiseError(NetObj.UnsupportedDataRep);
    END;
    IF c.rd.getSub(arr) # NUMBER(arr) THEN
      RaiseUnmarshalFailure();
    END;
  END InChars;

PROCEDURE InWideChars(c: Conn; rep: DataRep; VAR arr: ARRAY OF WIDECHAR)
    RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  VAR cnt: INTEGER := NUMBER(arr);
  VAR IntVal: UInt32;
  VAR b0, b1: CHAR;
  BEGIN
    IF cnt <= 0 THEN RETURN; END;
    IF CharSetField(rep.charSet) # CharSetField(NativeRep.charSet) THEN
      RaiseError(NetObj.UnsupportedDataRep);
    END;
    TRY
      IF WideChar32(rep.charSet) THEN
         (* Size 32 in the writing system => WC21 in the pickle. *)
        IF WideChar32(NativeRep.charSet) THEN (* 32 on both systems. *)
          FOR RI := 0 TO LAST(arr) DO
            IntVal := PickleStubs.InWC21(c.rd);
            IF IntVal > WideChar.WideChar32Max THEN
              RaiseError
                (Atom.FromText("Malformed pickle: WIDECHAR out of range."));
            END;
            arr[RI] := VAL(IntVal, WIDECHAR);
          END;
        ELSE (* Remote 32, local 16. *)
          FOR RI := 0 TO LAST(arr) DO
            IntVal := PickleStubs.InWC21(c.rd);
            IF IntVal > WideChar.WideChar16Max THEN IntVal := ReplacementWt; END;
            arr[RI] := VAL(IntVal, WIDECHAR);
          END;
        END
      ELSE (* size 16 in the pickle, in the byte order of the sender *)
        FOR RI := 0 TO LAST(arr) DO
          b0 := UnsafeRd.FastGetChar(c.rd);
          b1 := UnsafeRd.FastGetChar(c.rd);
          IF rep.intFmt < BigEndianFmt THEN
            arr[RI] := VAL(ORD(b0) + 256 * ORD(b1), WIDECHAR);
          ELSE
            arr[RI] := VAL(ORD(b1) + 256 * ORD(b0), WIDECHAR);
          END;
        END;
      END;
    EXCEPT Rd.EndOfFile => RaiseUnmarshalFailure();
    END;
  END InWideChars;

PROCEDURE OutChars(c: Conn; READONLY arr: ARRAY OF CHAR)
    RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    c.wr.putString(arr);
  END OutChars;

PROCEDURE OutWideChars(c: Conn; READONLY arr: ARRAY OF WIDECHAR)
    RAISES {Wr.Failure, Thread.Alerted} =
  VAR cnt: INTEGER := NUMBER (arr);
      buf: ARRAY [0..511] OF CHAR;
      n, w: INTEGER;
      done := 0;
  BEGIN
    IF cnt <= 0 THEN RETURN; END;
    IF BITSIZE(WIDECHAR) = 16 THEN
      (* two bytes a character, little endian *)
      WHILE done < cnt DO
        n := MIN(cnt - done, NUMBER(buf) DIV 2);
        FOR k := 0 TO n-1 DO
          w := ORD(arr[done+k]);
          buf[2*k] := VAL(w MOD 256, CHAR);
          buf[2*k+1] := VAL(w DIV 256, CHAR);
        END;
        c.wr.putString(SUBARRAY(buf, 0, 2*n));
        INC(done, n);
      END;
    ELSE (* Writing on 32-bit WIDECHAR system. *)
      FOR RI := 0 TO cnt-1 DO
        PickleStubs.OutWC21(c.wr, arr[RI]);
      END;
    END;
  END OutWideChars;

PROCEDURE InBytes(c: Conn; VAR arr: ARRAY OF Byte8)
    RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  VAR buf: ARRAY [0..511] OF CHAR;
      n: INTEGER;
      done := 0;
  BEGIN
    WHILE done < NUMBER(arr) DO
      n := MIN(NUMBER(arr) - done, NUMBER(buf));
      IF c.rd.getSub(SUBARRAY(buf, 0, n)) # n THEN
        RaiseUnmarshalFailure();
      END;
      FOR k := 0 TO n-1 DO arr[done+k] := ORD(buf[k]); END;
      INC(done, n);
    END;
  END InBytes;

PROCEDURE OutBytes(c: Conn; READONLY arr: ARRAY OF Byte8)
    RAISES {Wr.Failure, Thread.Alerted} =
  VAR buf: ARRAY [0..511] OF CHAR;
      n: INTEGER;
      done := 0;
  BEGIN
    WHILE done < NUMBER(arr) DO
      n := MIN(NUMBER(arr) - done, NUMBER(buf));
      FOR k := 0 TO n-1 DO buf[k] := VAL(arr[done+k], CHAR); END;
      c.wr.putString(SUBARRAY(buf, 0, n));
      INC(done, n);
    END;
  END OutBytes;

CONST
  BigEndianFmt   = 16;
  IntFmt32Little = 0;
  IntFmt64Little = 1;
  IntFmt32Big    = BigEndianFmt;
  IntFmt64Big    = BigEndianFmt + 1;

  FloatIEEE      = 0;
  FloatOther     = 1;

(* this code is integer-length dependent *)
(* we also rely on the invariant that MsgRd/MsgWr will
   provide contiguous 8-byte chunks at proper alignment ..
   as long as there is no intervening flush *)

PROCEDURE InInteger(c: Conn;
                    rep: DataRep;
                    min := FIRST(INTEGER);
                    max := LAST(INTEGER)): INTEGER
    RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  VAR i: INTEGER;
      big := rep.intFmt >= BigEndianFmt;
  BEGIN
    IF rep.intFmt = NativeRep.intFmt THEN (* Identical representations. *)
      i := GetNum(c.rd.buff^, AlignRd(c.rd, BYTESIZE(INTEGER)),
                  BYTESIZE(INTEGER), big);
      INC(c.rd.cur, BYTESIZE(INTEGER));
    ELSE
      CASE rep.intFmt OF
      | IntFmt32Little, IntFmt32Big =>
          i := SignExtend32(GetNum(c.rd.buff^, AlignRd(c.rd, 4), 4, big));
          INC(c.rd.cur, 4);
      | IntFmt64Little, IntFmt64Big =>
          CASE NativeRep.intFmt OF
          | IntFmt64Little, IntFmt64Big => (* 64->64, swapped. *)
              i := GetNum(c.rd.buff^, AlignRd(c.rd, 8), 8, big);
              INC(c.rd.cur, 8);
          ELSE RaiseError(NetObj.UnsupportedDataRep);
          END;
      ELSE RaiseError(NetObj.UnsupportedDataRep);
      END;
    END;
    IF i < min OR i > max THEN RaiseUnmarshalFailure(); END;
    RETURN i;
  END InInteger;

PROCEDURE InInt32(c: Conn;
                    rep: DataRep;
                    min := FIRST(Int32);
                    max := LAST(Int32)): Int32
    RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  VAR i: INTEGER;
  BEGIN
    CASE rep.intFmt OF
    | IntFmt32Little, IntFmt32Big, IntFmt64Little =>
        i := SignExtend32(GetNum(c.rd.buff^, AlignRd(c.rd, 4), 4,
                                 rep.intFmt >= BigEndianFmt));
        INC(c.rd.cur, 4);
    ELSE
      IF rep.intFmt = NativeRep.intFmt THEN
        i := SignExtend32(GetNum(c.rd.buff^, AlignRd(c.rd, 4), 4,
                                 rep.intFmt >= BigEndianFmt));
        INC(c.rd.cur, 4);
      ELSE
        RaiseError(NetObj.UnsupportedDataRep);
      END;
    END;
    IF i < min OR i > max THEN RaiseUnmarshalFailure(); END;
    RETURN i;
  END InInt32;

PROCEDURE AlignRd(rd: Rd.T; nb: CARDINAL) : CARDINAL
    RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  (* the index in "rd.buff" of the next "nb" bytes, aligned to "nb" *)
  VAR diff := rd.cur MOD nb;
  BEGIN
    (* here we rely on the alignment invariants of MsgRd.T *)
    IF diff # 0 THEN
      VAR n := rd.cur + nb - diff; BEGIN
        IF n > rd.hi THEN RaiseUnmarshalFailure(); END;
        rd.cur := n;
      END;
    END;
    IF rd.cur = rd.hi THEN EVAL rd.seek(rd.cur, FALSE); END;
    IF rd.hi - rd.cur < nb THEN RaiseUnmarshalFailure(); END;
    RETURN RdIndex(rd);
  END AlignRd;

  (*
    A MsgRd fragment must be 64-bit aligned.  Fragments of types call, return,
    or call-failed, must either be a multiple of 8 bytes in length, or else
    contain the end-of-message.    MsgRd buffers must be 64-bit aligned in
    length.
   *)

PROCEDURE OutInteger(c: Conn; i: INTEGER)
    RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    PutNum(c.wr.buff^, AlignWr(c.wr, BYTESIZE(INTEGER)), BYTESIZE(INTEGER), i);
    INC(c.wr.cur, BYTESIZE(INTEGER));
  END OutInteger;

PROCEDURE OutInt32(c: Conn; i: Int32)
    RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    PutNum(c.wr.buff^, AlignWr(c.wr, 4), 4, i);
    INC(c.wr.cur, 4);
  END OutInt32;

PROCEDURE AlignWr(wr: Wr.T; align: CARDINAL) : CARDINAL
    RAISES {Wr.Failure, Thread.Alerted} =
  (* the index in "wr.buff" for the next "align" bytes, aligned *)
  VAR diff := wr.cur MOD align;
  BEGIN
    (* here we rely on the alignment invariants of MsgWr.T *)
    IF diff # 0 THEN
      PutNum(wr.buff^, WrIndex(wr), align-diff, 0);
      INC(wr.cur, align-diff);
    END;
    IF wr.cur = wr.hi THEN wr.seek(wr.cur); END;
    RETURN WrIndex(wr);
  END AlignWr;

  (*
    A MsgWr fragment must be 64-bit aligned.  Fragments of types call, return,
    or call-failed, must either be a multiple of 8 bytes in length, or else
    contain the end-of-message.  MsgWr buffers must be 64-bit aligned in
    length.
   *)

PROCEDURE InByte(c: Conn;
                 max := LAST(Byte8)): Byte8
     RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  VAR b: Byte8;
  BEGIN
    TRY
      b := ORD(UnsafeRd.FastGetChar(c.rd));
    EXCEPT
    | Rd.EndOfFile => RaiseUnmarshalFailure();
    END;
    IF b > max THEN
      RaiseUnmarshalFailure();
    END;
    RETURN b
  END InByte;

PROCEDURE OutByte(c: Conn; b: Byte8)
   RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    UnsafeWr.FastPutChar(c.wr, VAL(b, CHAR));
  END OutByte;

TYPE MSpec = {Pickle, Text, NetObj, Reader, Writer, Texts};

PROCEDURE InRef(c: Conn; rep: DataRep; tc: INTEGER): REFANY
     RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  VAR r: REFANY;
  BEGIN
    CASE InByte(c) OF
    | ORD(MSpec.Pickle) =>
        IF (c.protocol # 2)
          THEN  r := InPickle(c, rep, tc);
          ELSE  r := InPickle2(c, rep, tc);
        END;
    | ORD(MSpec.Text) => r := InText(c, rep);
    | ORD(MSpec.NetObj) => r := InObject(c, tc);
    | ORD(MSpec.Reader) => r := InReader(c);
    | ORD(MSpec.Writer) => r := InWriter(c);
    | ORD(MSpec.Texts) => r := InTexts(c, rep);
    ELSE RaiseUnmarshalFailure();
    END;
    RETURN r;
  END InRef;

PROCEDURE OutRef(c: Conn; r: REFANY)
   RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    TYPECASE r OF
    | TEXT(x) => OutByte(c, ORD(MSpec.Text)); OutText(c, x);
    | NetObj.T(x) => OutByte(c, ORD(MSpec.NetObj)); OutObject(c, x);
    | Rd.T(x) => OutByte(c, ORD(MSpec.Reader)); OutReader(c, x);
    | Wr.T(x) => OutByte(c, ORD(MSpec.Writer)); OutWriter(c, x);
    | REF ARRAY OF TEXT(x) => OutByte(c, ORD(MSpec.Texts)); OutTexts(c, x);
    ELSE
      OutByte(c, ORD(MSpec.Pickle));
      IF (c.protocol # 2)
        THEN OutPickle(c, r);
        ELSE OutPickle2(c, r);
      END;
    END;
  END OutRef;

PROCEDURE InPickle(c: Conn; rep: DataRep; tc: INTEGER): REFANY
     RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  VAR r: REFANY;  srd := NewRd(c, rep);  ok := FALSE;
  BEGIN
    TRY
      TRY
        r := srd.read();
        ok := TRUE;
      FINALLY
        IF ok THEN FreeRd(srd); END;
      END;
    EXCEPT
    | Rd.EndOfFile => RaiseUnmarshalFailure();
    | Pickle.Error(cause) =>
        RAISE NetObj.Error(
          AtomList.List2(UnmarshalFailure, Atom.FromText(cause)));
    END;
    IF tc # -1 AND NOT RTType.IsSubtype(TYPECODE(r), tc) THEN
      RaiseUnmarshalFailure();
    END;
    RETURN r;
  END InPickle;

PROCEDURE InPickle2(c: Conn; rep: DataRep; tc: INTEGER): REFANY
     RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  VAR r: REFANY;  srd := NewRd2(c, rep);  ok := FALSE;
  BEGIN
    TRY
      TRY
        r := srd.read();
        ok := TRUE;
      FINALLY
        IF ok THEN FreeRd2(srd); END;
      END;
    EXCEPT
    | Rd.EndOfFile => RaiseUnmarshalFailure();
    | Pickle2.Error(cause) =>
        RAISE NetObj.Error(
          AtomList.List2(UnmarshalFailure, Atom.FromText(cause)));
    END;
    IF tc # -1 AND NOT RTType.IsSubtype(TYPECODE(r), tc) THEN
      RaiseUnmarshalFailure();
    END;
    RETURN r;
  END InPickle2;

PROCEDURE OutPickle(c: Conn; r: REFANY)
   RAISES {Wr.Failure, Thread.Alerted} =
  VAR swr := NewWr(c); ok := FALSE;
  BEGIN
    TRY
      TRY
        swr.write(r);
        ok := TRUE;
      FINALLY
        IF ok THEN FreeWr(swr); END;
      END;
    EXCEPT
    | Pickle.Error(cause) =>
        RAISE Wr.Failure(AtomList.List1(Atom.FromText(cause)));
    END;
  END OutPickle;

PROCEDURE OutPickle2(c: Conn; r: REFANY)
   RAISES {Wr.Failure, Thread.Alerted} =
  VAR swr := NewWr2(c); ok := FALSE;
  BEGIN
    TRY
      TRY
        swr.write(r);
        ok := TRUE;
      FINALLY
        IF ok THEN FreeWr2(swr); END;
      END;
    EXCEPT
    | Pickle2.Error(cause) =>
        RAISE Wr.Failure(AtomList.List1(Atom.FromText(cause)));
    END;
  END OutPickle2;

PROCEDURE InCardinal(c: Conn; rep: DataRep;
     lim: CARDINAL := LAST(CARDINAL)): CARDINAL
     RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  BEGIN
    RETURN InInteger(c, rep, 0, lim);
  END InCardinal;

PROCEDURE OutCardinal(c: Conn; card: CARDINAL)
   RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    OutInteger(c, card);
  END OutCardinal;

PROCEDURE InFloatBytes(c: Conn; rep: DataRep; n: CARDINAL): Word.T
    RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  (* the bits of a floating-point number of "n" bytes *)
  VAR b: ARRAY [0..7] OF CHAR;
  BEGIN
    IF rep.floatFmt # NativeRep.floatFmt THEN
      RaiseError(NetObj.UnsupportedDataRep);
    END;
    IF c.rd.getSub(SUBARRAY(b, 0, n)) # n THEN
      RaiseUnmarshalFailure();
    END;
    RETURN GetNum(b, 0, n, rep.intFmt >= BigEndianFmt);
  END InFloatBytes;

PROCEDURE OutFloatBytes(c: Conn; bits: Word.T; n: CARDINAL)
   RAISES {Wr.Failure, Thread.Alerted} =
  VAR b: ARRAY [0..7] OF CHAR;
  BEGIN
    PutNum(b, 0, n, bits);
    c.wr.putString(SUBARRAY(b, 0, n));
  END OutFloatBytes;

PROCEDURE InReal(c: Conn; rep: DataRep): REAL
    RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  BEGIN
    RETURN M3jFloat.RealFromBits(InFloatBytes(c, rep, BYTESIZE(REAL)));
  END InReal;

PROCEDURE OutReal(c: Conn; i: REAL)
   RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    OutFloatBytes(c, M3jFloat.RealBits(i), BYTESIZE(REAL));
  END OutReal;

PROCEDURE InLongreal(c: Conn; rep: DataRep): LONGREAL
    RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  BEGIN
    RETURN M3jFloat.LongFromBits(InFloatBytes(c, rep, BYTESIZE(LONGREAL)));
  END InLongreal;

PROCEDURE OutLongreal(c: Conn; i: LONGREAL)
   RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    OutFloatBytes(c, M3jFloat.LongBits(i), BYTESIZE(LONGREAL));
  END OutLongreal;

PROCEDURE InExtended(c: Conn; rep: DataRep): EXTENDED
    RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  BEGIN
    (* an EXTENDED has the bytes of a LONGREAL, as in cm3 on AMD64_NT *)
    RETURN FLOAT(M3jFloat.LongFromBits(InFloatBytes(c, rep, 8)), EXTENDED);
  END InExtended;

PROCEDURE OutExtended(c: Conn; i: EXTENDED)
   RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    OutFloatBytes(c, M3jFloat.LongBits(FLOAT(i, LONGREAL)), 8);
  END OutExtended;

PROCEDURE InBoolean(c: Conn) : BOOLEAN
    RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  VAR res: BOOLEAN;
  BEGIN
    TRY
      res := UnsafeRd.FastGetChar(c.rd) # '\000';
    EXCEPT
    | Rd.EndOfFile => RaiseUnmarshalFailure();
    END;
    RETURN res;
  END InBoolean;

PROCEDURE OutBoolean(c: Conn; bool: BOOLEAN)
    RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    IF bool THEN
      UnsafeWr.FastPutChar(c.wr, '\001');
    ELSE
      UnsafeWr.FastPutChar(c.wr, '\000');
    END;
  END OutBoolean;

PROCEDURE InText(c: Conn; rep: DataRep) : TEXT
   RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  VAR len := InInt32(c, rep);
  BEGIN
    IF len = -1 THEN
      RETURN NIL;
    ELSIF len = 0 THEN
      RETURN "";
    ELSIF len < 0 THEN
      RaiseUnmarshalFailure();
      RETURN NIL;
    ELSIF InByte(c) # ORD(FALSE) THEN
      RETURN InText16(c, rep, len);
    ELSE
      RETURN InText8(c, rep, len);
    END;
  END InText;

PROCEDURE InText16(c: Conn; rep: DataRep;  len: INTEGER) : TEXT
  RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  VAR buf: ARRAY [0..255] OF WIDECHAR;  big: REF ARRAY OF WIDECHAR;
  BEGIN
    IF len <= NUMBER(buf) THEN
      WITH z = SUBARRAY(buf, 0, len) DO
        InWideChars(c, rep, z);
        RETURN Text.FromWideChars(z);
      END;
    ELSE
      big := NEW(REF ARRAY OF WIDECHAR, len);
      InWideChars(c, rep, big^);
      RETURN Text.FromWideChars(big^);
    END;
  END InText16;

PROCEDURE InText8(c: Conn; rep: DataRep;  len: INTEGER) : TEXT
  RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  VAR buf: ARRAY [0..255] OF CHAR;  big: REF ARRAY OF CHAR;
  BEGIN
    IF len <= NUMBER(buf) THEN
      WITH z = SUBARRAY(buf, 0, len) DO
        InChars(c, rep, z);
        RETURN Text.FromChars(z);
      END;
    ELSE
      big := NEW(REF ARRAY OF CHAR, len);
      InChars(c, rep, big^);
      RETURN Text.FromChars(big^);
    END;
  END InText8;

PROCEDURE OutText(c: Conn; txt: TEXT)
  RAISES {Wr.Failure, Thread.Alerted} =
  VAR len: INTEGER;  wide: BOOLEAN;
  BEGIN
    IF txt = NIL THEN
      OutInt32(c, -1);
    ELSE
      len := Text.Length(txt);
      OutInt32(c, len);
      IF len > 0 THEN
        wide := Text.HasWideChars(txt);
        OutByte(c, ORD(wide));
        IF wide THEN
          OutText16(c, txt, len);
        ELSE (* 8-bit characters only *)
          OutText8(c, txt, len);
        END;
      END;
    END;
  END OutText;

PROCEDURE OutText16(c: Conn;  txt: TEXT;  len: INTEGER)
  RAISES {Wr.Failure, Thread.Alerted} =
  VAR cnt := 0;  buf: ARRAY [0..511] OF WIDECHAR;
  BEGIN
    WHILE cnt < len DO
      Text.SetWideChars (buf, txt, start := cnt);
      OutWideChars(c, SUBARRAY(buf, 0, MIN (len-cnt, NUMBER(buf))));
      INC(cnt, NUMBER(buf));
    END;
  END OutText16;

PROCEDURE OutText8(c: Conn;  txt: TEXT;  len: INTEGER)
  RAISES {Wr.Failure, Thread.Alerted} =
  VAR cnt := 0;  buf: ARRAY [0..511] OF CHAR;
  BEGIN
    WHILE cnt < len DO
      Text.SetChars (buf, txt, start := cnt);
      OutChars(c, SUBARRAY(buf, 0, MIN (len-cnt, NUMBER(buf))));
      INC(cnt, NUMBER(buf));
    END;
  END OutText8;

PROCEDURE InTexts(c: Conn; rep: DataRep): REF ARRAY OF TEXT
  RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  VAR n: CARDINAL;
  VAR rt: REF ARRAY OF TEXT;
  BEGIN
    n := InInt32(c, rep, 0);
    rt := NEW(REF ARRAY OF TEXT, n);
    IF n > 0 THEN
      FOR i := 0 TO n-1 DO rt[i] := InText(c, rep); END;
    END;
    RETURN rt;
  END InTexts;

PROCEDURE OutTexts(c: Conn; rt: REF ARRAY OF TEXT)
    RAISES {Wr.Failure, Thread.Alerted} =
  VAR n: CARDINAL;
  BEGIN
    IF (rt = NIL) THEN n := 0 ELSE n := NUMBER(rt^); END;
    OutInt32(c, n);
    IF n > 0 THEN
      FOR i := 0 TO n-1 DO OutText(c, rt[i]); END;
    END;
  END OutTexts;

TYPE
  VT = Voucher.T OBJECT
    stream: REFANY;
  OVERRIDES
    claimRd := ClaimRd;
    claimWr := ClaimWr;
  END;

PROCEDURE InReader(c: Conn) : Rd.T
    RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  VAR obj: NetObj.T := NIL;
      rd: Rd.T := NIL;
  BEGIN
    obj := InObject(c);
    TYPECASE obj OF
    | NULL =>
    | Voucher.T(v) =>  rd := v.claimRd();
    ELSE
        RaiseUnmarshalFailure();
    END;
    RETURN rd;
  END InReader;

PROCEDURE OutReader(c: Conn; rd: Rd.T)
    RAISES {Wr.Failure, Thread.Alerted} =
  VAR obj: NetObj.T := NIL;
  BEGIN
    IF rd # NIL THEN
      obj := NEW(VT, stream := rd);
      (* EVAL WeakRef.FromRef(obj, CleanupVT); *)
    END;
    OutObject(c, obj);
  END OutReader;

PROCEDURE InWriter(c: Conn) : Wr.T
    RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  VAR obj: NetObj.T := NIL;
      wr: Wr.T := NIL;
  BEGIN
    obj := InObject(c);
    TYPECASE obj OF
    | NULL =>
    | Voucher.T(v) =>  wr := v.claimWr();
    ELSE
        RaiseUnmarshalFailure();
    END;
    RETURN wr;
  END InWriter;

PROCEDURE OutWriter(c: Conn; wr: Wr.T)
    RAISES {Wr.Failure, Thread.Alerted} =
  VAR obj: NetObj.T := NIL;
  BEGIN
    IF wr # NIL THEN
      obj := NEW(VT, stream := wr);
      (* EVAL WeakRef.FromRef(obj, CleanupVT); *)
    END;
    OutObject(c, obj);
  END OutWriter;

PROCEDURE ClaimRd(v: VT) : Rd.T =
  BEGIN
    TYPECASE v.stream OF
    | Rd.T(rd) => RETURN rd;
    ELSE RETURN NIL;
    END;
  END ClaimRd;

PROCEDURE ClaimWr(v: VT) : Wr.T =
  BEGIN
    TYPECASE v.stream OF
    | Wr.T(wr) => RETURN wr;
    ELSE RETURN NIL;
    END;
  END ClaimWr;

CONST Align64 = 8;   (* to achieve 64-bit alignment *)

PROCEDURE OutObject (c: Conn; o: NetObj.T)
    RAISES {Wr.Failure, Thread.Alerted} =
  VAR
    wr := c.wr;
    i := AlignWr(wr, Align64);
    w: WireRep.T;
  BEGIN
    IF o = NIL THEN
      w := WireRep.NullT;
    ELSE
      w := NetObjRT.InsertAndPin(o);
    END;
    IF wr.hi - wr.cur >= WireRepSize THEN
      FOR k := 0 TO WireRepSize-1 DO
        wr.buff[i + k] := VAL(w.byte[k], CHAR);
      END;
      INC(wr.cur, WireRepSize);
    ELSE
      OutBytes(c, w.byte);
    END;
    VAR s := c.objStack.objs; BEGIN
      IF s = NIL THEN
        s := NEW(REF ARRAY OF NetObj.T, DefaultObjStackSize);
        c.objStack.objs := s;
      ELSIF c.objStack.pos = NUMBER(s^) THEN
        s := NEW(REF ARRAY OF NetObj.T, 2 * c.objStack.pos);
        SUBARRAY(s^, 0, c.objStack.pos) := c.objStack.objs^;
        c.objStack.objs := s;
      END;
      s[c.objStack.pos] := o;
      INC(c.objStack.pos);
    END;
  END OutObject;

PROCEDURE InObject (c: Conn; tc: INTEGER := -1): NetObj.T
    RAISES {NetObj.Error, Rd.Failure, Thread.Alerted} =
  VAR
    o: NetObj.T;
    rd := c.rd;
    i := AlignRd(rd, Align64);
    w: WireRep.T;
  BEGIN
    IF rd.hi - rd.cur >= WireRepSize THEN
      FOR k := 0 TO WireRepSize-1 DO
        w.byte[k] := ORD(rd.buff[i + k]);
      END;
      INC(rd.cur, WireRepSize);
    ELSE
      InBytes(c, w.byte);
    END;
    IF w = WireRep.NullT THEN RETURN NIL END;
    o := NetObjRT.Find(w, c.loc);
    IF tc # -1 AND NOT RTType.IsSubtype(TYPECODE(o), tc) THEN
      RaiseUnmarshalFailure();
    END;
    c.inObj := TRUE;
    RETURN o;
  END InObject;


(* Procedures for Pickling -- free list management *)

PROCEDURE NewWr(c: Conn): SpecWr =
  VAR pwr: SpecWr;
  BEGIN
    LOCK mu DO
      IF freeWr # NIL THEN
        pwr := freeWr; freeWr := pwr.next;
      ELSE
        pwr := NEW(SpecWr);
      END;
    END;
    pwr.wr := c.wr;
    pwr.c := c;
    pwr.next := NIL;
    RETURN pwr
  END NewWr;

PROCEDURE FreeWr(pwr: SpecWr) =
  BEGIN
    LOCK mu DO
      pwr.next := freeWr;
      pwr.c := NIL;
      pwr.wr := NIL;
      freeWr := pwr;
    END;
  END FreeWr;

PROCEDURE NewWr2(c: Conn): SpecWr2 =
  VAR pwr: SpecWr2;
  BEGIN
    LOCK mu DO
      IF freeWr2 # NIL THEN
        pwr := freeWr2; freeWr2 := pwr.next;
      ELSE
        pwr := NEW(SpecWr2);
      END;
    END;
    pwr.wr := c.wr;
    pwr.c := c;
    pwr.next := NIL;
    RETURN pwr
  END NewWr2;

PROCEDURE FreeWr2(pwr: SpecWr2) =
  BEGIN
    LOCK mu DO
      pwr.next := freeWr2;
      pwr.c := NIL;
      pwr.wr := NIL;
      freeWr2 := pwr;
    END;
  END FreeWr2;

PROCEDURE NewRd(c: Conn; rep: DataRep): SpecRd =
  VAR prd: SpecRd;
  BEGIN
    LOCK mu DO
      IF freeRd # NIL THEN
        prd := freeRd; freeRd := prd.next;
      ELSE
        prd := NEW(SpecRd);
      END;
    END;
    prd.rd := c.rd;
    prd.c := c;
    prd.rep := rep;
    prd.next := NIL;
    RETURN prd
  END NewRd;

PROCEDURE FreeRd(prd: SpecRd) =
  BEGIN
    LOCK mu DO
      prd.c := NIL;
      prd.rd := NIL;
      prd.next := freeRd;
      freeRd := prd;
    END;
  END FreeRd;

PROCEDURE NewRd2(c: Conn; rep: DataRep): SpecRd2 =
  VAR prd: SpecRd2;
  BEGIN
    LOCK mu DO
      IF freeRd2 # NIL THEN
        prd := freeRd2; freeRd2 := prd.next;
      ELSE
        prd := NEW(SpecRd2);
      END;
    END;
    prd.rd := c.rd;
    prd.c := c;
    prd.rep := rep;
    prd.next := NIL;
    RETURN prd
  END NewRd2;

PROCEDURE FreeRd2(prd: SpecRd2) =
  BEGIN
    LOCK mu DO
      prd.c := NIL;
      prd.rd := NIL;
      prd.next := freeRd2;
      freeRd2 := prd;
    END;
  END FreeRd2;

(* Pickle Special routines *)

PROCEDURE OutSpecial(self: Pickle.Special;
                 r: REFANY;
                 writer: Pickle.Writer)
  RAISES  {Pickle.Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    TYPECASE writer OF
    | SpecWr(wtr) => OutRef(wtr.c, r);
    ELSE
      TYPECASE r OF
      | NetObj.T(x) =>
          IF NOT ISTYPE(x.r, Transport.Location) THEN
            (* This will gratuitously pickle the ExportInfo ref
               embedded in x.r.  It would be better to exclude this
               if and when possible, but it shouldn't hurt for now. *)
            Pickle.Special.write(self, r, writer);
          ELSE
            RAISE Pickle.Error("Can't pickle a surrogate object");
          END;
      ELSE RAISE Pickle.Error("Can't Pickle Rd.T or Wr.T");
      END;
    END;
  END OutSpecial;

PROCEDURE OutSpecial2(self: Pickle2.Special;
                 r: REFANY;
                 writer: Pickle2.Writer)
  RAISES  {Pickle2.Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    TYPECASE writer OF
    | SpecWr2(wtr) => OutRef(wtr.c, r);
    ELSE
      TYPECASE r OF
      | NetObj.T(x) =>
          IF NOT ISTYPE(x.r, Transport.Location) THEN
            (* This will gratuitously pickle the ExportInfo ref
               embedded in x.r.  It would be better to exclude this
               if and when possible, but it shouldn't hurt for now. *)
            Pickle2.Special.write(self, r, writer);
          ELSE
            RAISE Pickle2.Error("Can't pickle a surrogate object");
          END;
      ELSE RAISE Pickle2.Error("Can't Pickle Rd.T or Wr.T");
      END;
    END;
  END OutSpecial2;

PROCEDURE InSpecial(self: Pickle.Special;
                reader: Pickle.Reader;
                id: Pickle.RefID): REFANY
  RAISES { Pickle.Error, Rd.EndOfFile, Rd.Failure, Thread.Alerted } =
  BEGIN
    TRY
      TYPECASE reader OF
      | SpecRd(rdr) =>
          RETURN InRef(rdr.c, rdr.rep, self.sc);
      ELSE
        TYPECASE Pickle.Special.read(self, reader, id) OF
        | NetObj.T(x) =>
            x.w := WireRep.NullT;
            x.r := NIL;
            RETURN x;
        ELSE RAISE Pickle.Error("Can't Unpickle Rd.T or Wr.T");
        END;
      END;
    EXCEPT
      NetObj.Error(cause) => RAISE Pickle.Error(Atom.ToText(cause.head));
    END;
  END InSpecial;

PROCEDURE InSpecial2(self: Pickle2.Special;
                reader: Pickle2.Reader;
                id: Pickle2.RefID): REFANY
  RAISES { Pickle2.Error, Rd.EndOfFile, Rd.Failure, Thread.Alerted } =
  BEGIN
    TRY
      TYPECASE reader OF
      | SpecRd2(rdr) =>
          RETURN InRef(rdr.c, rdr.rep, self.sc);
      ELSE
        TYPECASE Pickle2.Special.read(self, reader, id) OF
        | NetObj.T(x) =>
            x.w := WireRep.NullT;
            x.r := NIL;
            RETURN x;
        ELSE RAISE Pickle2.Error("Can't Unpickle Rd.T or Wr.T");
        END;
      END;
    EXCEPT
      NetObj.Error(cause) => RAISE Pickle2.Error(Atom.ToText(cause.head));
    END;
  END InSpecial2;

(* NetObjF routines for checking if it's a netobj pickler *)
PROCEDURE IsNetObjWriter(wr: Pickle2.Writer): BOOLEAN =
  BEGIN
    RETURN ISTYPE(wr, SpecWr2);
  END IsNetObjWriter;

PROCEDURE IsNetObjReader(rd: Pickle2.Reader): BOOLEAN =
  BEGIN
    RETURN ISTYPE(rd, SpecRd2);
  END IsNetObjReader;

PROCEDURE RaiseUnmarshalFailure() RAISES {NetObj.Error} =
  BEGIN
    RaiseError(UnmarshalFailure);
  END RaiseUnmarshalFailure;

PROCEDURE RaiseCommFailure(ec: AtomList.T) RAISES {NetObj.Error} =
  BEGIN
    RAISE NetObj.Error(AtomList.Cons(NetObj.CommFailure, ec));
  END RaiseCommFailure;

PROCEDURE RaiseError(a: Atom.T) RAISES {NetObj.Error} =
  BEGIN
    RAISE NetObj.Error(AtomList.List1(a));
  END RaiseError;

PROCEDURE NativeEndian(rep: DataRep) : BOOLEAN =
  VAR endian := Swap.GetEndian ();
  BEGIN
    RETURN (rep.intFmt >= BigEndianFmt) = (endian = Swap.Endian.Big);
  END NativeEndian;

PROCEDURE ChooseIntFmt(): Byte8 =
  VAR endian := Swap.GetEndian ();
  BEGIN
    IF BYTESIZE(INTEGER) = 8 THEN
      IF endian = Swap.Endian.Little THEN
        RETURN IntFmt64Little;
      ELSE
        RETURN IntFmt64Big;
      END;
    ELSE
      IF endian = Swap.Endian.Little THEN
        RETURN IntFmt32Little;
      ELSE
        RETURN IntFmt32Big;
      END;
    END;
  END ChooseIntFmt;

PROCEDURE ChooseFloatFmt(): Byte8 =
  BEGIN
    IF FloatMode.IEEE THEN
      RETURN FloatIEEE;
    ELSE
      RETURN FloatOther;
    END;
  END ChooseFloatFmt;

CONST CharSet = 0;
CONST WideWIDECHAR = ORD(LAST(WIDECHAR)) > WideChar.WideChar16Max;

PROCEDURE ChooseCharSet(): Byte8 =
(* Pack the original charSet (which has only a single value 0)
   and the WIDECHAR size into this byte.  Remote machines that
   don't do this will have zeros here, which correctly means
   16-bits.  They will unnecessarily choke on reading CHAR (yes, CHAR,
   not WIDECHAR) from a machine with 32-bit WIDECHAR, with message
   (UnsupportedDataRep). If they read a WIDECHAR, they will get lost
   in the pickle and probably soon after suffer some kind of messy pickle
   error. *)
  BEGIN
    RETURN Or(And(CharSet, 16_7F), And(LeftShift(ORD(WideWIDECHAR),7),16_80));
  END ChooseCharSet;

PROCEDURE WideChar32(charSet: Byte8):BOOLEAN =
  (* Widechars are 32-bits. *)
  BEGIN
    RETURN And(charSet, 16_80) # 0;
  END WideChar32;

PROCEDURE CharSetField(charSet: Byte8): Byte8 =
  BEGIN
    RETURN And(charSet, 16_7F);
  END CharSetField;

BEGIN
  NativeRep := DataRep{private := 0,
                       intFmt := ChooseIntFmt(),
                       charSet := ChooseCharSet(),
                       floatFmt := ChooseFloatFmt()};
  UnmarshalFailure := Atom.FromText("NetObj.UnmarshalFailure");

  (* Initialization for Pickle specials and free list *)
  mu := NEW(MUTEX);

  Pickle.RegisterSpecial(
      NEW(Pickle.Special, sc:= TYPECODE(NetObj.T),
                          write := OutSpecial,
                          read := InSpecial));
  Pickle.RegisterSpecial(
      NEW(Pickle.Special, sc:= TYPECODE(Rd.T),
                          write := OutSpecial,
                          read := InSpecial));
  Pickle.RegisterSpecial(
      NEW(Pickle.Special, sc:= TYPECODE(Wr.T),
                          write := OutSpecial,
                          read := InSpecial));

  Pickle2.RegisterSpecial(
      NEW(Pickle2.Special, sc:= TYPECODE(NetObj.T),
                          write := OutSpecial2,
                          read := InSpecial2));
  Pickle2.RegisterSpecial(
      NEW(Pickle2.Special, sc:= TYPECODE(Rd.T),
                          write := OutSpecial2,
                          read := InSpecial2));
  Pickle2.RegisterSpecial(
      NEW(Pickle2.Special, sc:= TYPECODE(Wr.T),
                          write := OutSpecial2,
                          read := InSpecial2));

END StubLib.
