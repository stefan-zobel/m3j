(* Changed for m3j from the UNSAFE module of libm3, to safe code that
   writes and reads the same bytes: numbers are written byte by byte in
   the packing of AMD64_NT (little-endian, 64-bit INTEGER and LONGINT,
   16-bit WIDECHAR) and read byte by byte in the packing of the writer,
   instead of through LOOPHOLEs of their storage and Swap; the bits of
   floating-point numbers come from M3jFloat; texts are made with
   Text.FromChars and Text.FromWideChars. *)

(*
 * For information about this program, contact Blair MacIntyre
 * (bm@cs.columbia.edu) or Steven Feiner (feiner@cs.columbia.edu)
 * at the Computer Science Dept., Columbia University,
 * 1214 Amsterdam Ave. Mailstop 0401, New York, NY, 10027.
 *
 * Copyright (C) 1995, 1996 by The Trustees of Columbia University in the
 * City of New York.  Blair MacIntyre, Computer Science Department.
 *
 * This file is released under the same conditions as Pickle.m3. See COPYRIGHT.
 *
 *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

MODULE PickleStubs;

IMPORT Pickle2 AS Pickle, PickleRd, PickleWr, RTPacking, RTType;
IMPORT ConvertPacking, M3jFloat;
FROM PickleRd IMPORT myPacking;
FROM ConvertPacking IMPORT CPKind;
FROM Swap IMPORT Int32;

IMPORT Rd, Wr, Text, Thread;
IMPORT Word;
FROM Word IMPORT And, Or, LeftShift, RightShift;

TYPE
  UInt8 = BITS 8 FOR [0 .. 16_FF];

(*---------bytes of numbers-----------*)

PROCEDURE GetBytes(reader: Pickle.Reader; VAR b: ARRAY OF CHAR)
    RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  (* Read NUMBER(b) bytes. *)
  BEGIN
    IF Rd.GetSub(reader.rd, b) # NUMBER(b) THEN
      RaiseUnmarshalFailure();
    END;
  END GetBytes;

PROCEDURE FromBytes(READONLY b: ARRAY OF CHAR; bigEndian: BOOLEAN): Word.T =
  (* The unsigned number of the bytes b. *)
  VAR i: Word.T := 0;
  BEGIN
    IF bigEndian THEN
      FOR k := 0 TO LAST(b) DO
        i := Or(LeftShift(i, 8), ORD(b[k]));
      END;
    ELSE
      FOR k := LAST(b) TO 0 BY -1 DO
        i := Or(LeftShift(i, 8), ORD(b[k]));
      END;
    END;
    RETURN i;
  END FromBytes;

PROCEDURE SignExtend32(i: Word.T): INTEGER =
  (* The signed number of the low 32 bits of i. *)
  BEGIN
    i := And(i, 16_FFFFFFFF);
    IF i > 16_7FFFFFFF THEN DEC(i, 16_100000000) END;
    RETURN i;
  END SignExtend32;

PROCEDURE PutBytes(writer: Pickle.Writer; i: Word.T; n: CARDINAL)
    RAISES {Wr.Failure, Thread.Alerted} =
  (* Write the n low bytes of i, little-endian. *)
  BEGIN
    FOR k := 0 TO n - 1 DO
      Wr.PutChar(writer.wr, VAL(And(RightShift(i, 8 * k), 16_FF), CHAR));
    END;
  END PutBytes;

PROCEDURE IsSwap(kind: CPKind): BOOLEAN =
  (* TRUE if kind converts from the other byte order. *)
  BEGIN
    CASE kind OF
    | CPKind.Swap, CPKind.Swap32to64, CPKind.Swap64to32,
      CPKind.Swap16to32, CPKind.Swap32to16 => RETURN TRUE;
    ELSE RETURN FALSE;
    END;
  END IsSwap;

(*---------marshalling/unmarshalling routines-----------*)

PROCEDURE OutRef (writer: Pickle.Writer; r: REFANY)
  RAISES {Pickle.Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    writer.write(r);
  END OutRef;

PROCEDURE InRef (reader: Pickle.Reader; tc := -1): REFANY
  RAISES {Pickle.Error, Rd.EndOfFile, Rd.Failure, Thread.Alerted} =
  BEGIN
    WITH ref = reader.read() DO
      IF tc # -1 AND NOT RTType.IsSubtype(TYPECODE(ref), tc) THEN
        RaiseUnmarshalFailure();
      END;
      RETURN ref;
    END;
  END InRef;

PROCEDURE InChars(reader: Pickle.Reader; VAR arr: ARRAY OF CHAR)
    RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  BEGIN
    (* No longer an charSet infomation.
      IF reader.packing.charSet # myPacking.charSet THEN
        RaiseUnsupportedDataRep();
      END; *)
    IF Rd.GetSub(reader.rd, arr) # NUMBER(arr) THEN
      RaiseUnmarshalFailure();
    END;
  END InChars;

(* WC21 is a variable-length encoding of widechar values, used only in
   pickles and net objects.  The standard Unicode encodings explicitly disallow
   surrogate code points as unencoded values.  Programs may have a legitimate
   need to store and/or manipulate surrogate values in memory, and these should
   be picklable too.  WC21 supports the entire code point range, which requires
   21 bits.

   The first byte has 7 (least significant) data bits and one bit (msb of the
   byte) that, if set, indicates another byte follows.  If present, the second
   byte is just like the first and supplies the next more significant 7 data
   bits.  If it calls for a third byte, that contains the 7 most significant
   data bits.  The bytes are always in least- to most-significant order in the
   pickle, regardless of endianness of writing or reading machine.

   Note that this can encode values beyond the Unicode code point range.
   When writing, we prevent this by accepting a parameter of type WIDECHAR.
   When reading, we return an integer and let the caller apply a range
   check, which could be only 16_FFFF, when compiled with WIDECHAR of this
   size.
*)

PROCEDURE InWC21(rd: Rd.T): UInt32
RAISES{Rd.EndOfFile, Rd.Failure, Thread.Alerted} =
(* Unmarshal one WIDECHAR value in WC21 encoding and return in a 32-bit int,
   where caller can range check. *)

  VAR B0, B1, B2: UInt8;
  VAR intVal: INTEGER;
  BEGIN
    B0 := ORD(Rd.GetChar(rd));
    intVal := And(B0, 16_7F);
    IF And(B0, 16_80) # 0 THEN (* A second byte follows. *)
      B1 := ORD(Rd.GetChar(rd));
      intVal := Or(intVal, LeftShift (And (B1, 16_7F), 7));
      IF And(B1, 16_80) # 0 THEN (* A third byte follows. *)
        B2 := ORD(Rd.GetChar(rd));
        intVal := Or(intVal, LeftShift (And(B2, 16_7F), 14));
      END;
    END;
    RETURN intVal
  END InWC21;

PROCEDURE InWideChars(reader: Pickle.Reader; VAR arr: ARRAY OF WIDECHAR)
    RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  VAR IntVal: INTEGER;
  VAR b: ARRAY [0..1] OF CHAR;
  BEGIN
    IF NUMBER(arr) <= 0 THEN RETURN; END;
    TRY
      IF reader.packing.widechar_size = 32 THEN
         (* Size 32 in the writing system => WC21 in the pickle. *)
        FOR RI := 0 TO LAST(arr) DO
          IntVal := InWC21(reader.rd);
          IF IntVal > 16_FFFF THEN IntVal := ReplacementWt; END;
          arr[RI] := VAL(IntVal, WIDECHAR);
        END;
      ELSE (* size 16 in the pickle. *)
        FOR RI := 0 TO LAST(arr) DO
          GetBytes(reader, b);
          arr[RI] := VAL(FromBytes(b, IsSwap(reader.widecharConvKind)), WIDECHAR);
        END;
      END;
    EXCEPT Rd.EndOfFile => RaiseUnmarshalFailure();
    END;
  END InWideChars;

PROCEDURE OutChars(writer: Pickle.Writer; READONLY arr: ARRAY OF CHAR)
    RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    Wr.PutString(writer.wr, arr);
  END OutChars;

PROCEDURE OutWC21(wr: Wr.T; wc: WIDECHAR)
  RAISES {Wr.Failure, Thread.Alerted} =
(* Marshal one wide char in surrogate-tolerant WC21 encoding. *)

  VAR intVal, B0, B1, B2: UInt32;
  BEGIN
    intVal := ORD(wc);
    B0 := And(intVal, 16_7F);
    IF And(intVal, 16_FFFFFF80) = 0 THEN (* No 2nd byte is needed. *)
      Wr.PutChar(wr, VAL(B0, CHAR));
    ELSE
      B0 := Or (B0, 16_80);
      Wr.PutChar(wr, VAL(B0, CHAR));
      B1 := RightShift(And(intVal, 16_3F80), 7);
      IF And(intVal, 16_FFFFC000) = 0 THEN (* No 3rd byte is needed. *)
        Wr.PutChar(wr, VAL(B1, CHAR));
      ELSE
        B1 := Or (B1, 16_80);
        Wr.PutChar(wr, VAL(B1, CHAR));
        B2 := RightShift(And(intVal, 16_1FC000), 14);
        Wr.PutChar(wr, VAL(B2, CHAR));
      END;
    END;
  END OutWC21;

PROCEDURE OutWideChars(writer: Pickle.Writer; READONLY arr: ARRAY OF WIDECHAR)
    RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    IF writer.packing.widechar_size = 16 THEN
      FOR RI := 0 TO LAST(arr) DO
        PutBytes(writer, ORD(arr[RI]), 2);
      END;
    ELSE (* Writing on 32-bit WIDECHAR system. *)
      FOR RI := 0 TO LAST(arr) DO
        OutWC21(writer.wr, arr[RI]);
      END;
    END;
  END OutWideChars;

PROCEDURE InBytes(reader: Pickle.Reader; VAR arr: ARRAY OF Byte8)
    RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  VAR b := NEW(REF ARRAY OF CHAR, NUMBER(arr));
  BEGIN
    GetBytes(reader, b^);
    FOR i := 0 TO LAST(arr) DO arr[i] := ORD(b[i]) END;
  END InBytes;

PROCEDURE OutBytes(writer: Pickle.Writer; READONLY arr: ARRAY OF Byte8)
    RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    FOR i := 0 TO LAST(arr) DO
      Wr.PutChar(writer.wr, VAL(arr[i], CHAR));
    END;
  END OutBytes;

(* this code is integer-length dependent *)

PROCEDURE ReadWord(reader: Pickle.Reader; kind: CPKind; signed: BOOLEAN)
  : INTEGER
    RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  (* Read an INTEGER or LONGINT that the writer wrote, converted as kind
     says; sign-extended from 32 bits if signed. *)
  VAR b: ARRAY [0..7] OF CHAR;
  VAR i: Word.T;
  BEGIN
    CASE kind OF
    | CPKind.Copy, CPKind.Swap =>
      GetBytes(reader, b);
      i := FromBytes(b, kind = CPKind.Swap);
    | CPKind.Copy32to64, CPKind.Swap32to64 =>
      GetBytes(reader, SUBARRAY(b, 0, 4));
      i := FromBytes(SUBARRAY(b, 0, 4), kind = CPKind.Swap32to64);
      IF signed THEN i := SignExtend32(i) END;
    ELSE
      (* the writer's words are wider than ours (64 bits) *)
      RaiseUnsupportedDataRep();
    END;
    RETURN i;
  END ReadWord;

PROCEDURE InInteger(reader: Pickle.Reader;
                    min := FIRST(INTEGER);
                    max := LAST(INTEGER)): INTEGER
    RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  VAR i: INTEGER;
  BEGIN
    i := ReadWord(reader, reader.wordConvKind, TRUE);
    IF i < min OR i > max THEN RaiseUnmarshalFailure(); END;
    RETURN i;
  END InInteger;

PROCEDURE InWord (reader: Pickle.Reader;
                  min            := 0;
                  max            := -1 ): INTEGER
  RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
(* Unmarshal an unsigned Word.T, checking that its unsigned value is in
   "[min..max]".  In case the word size changes, use unsigned extension
   or overflow check. *)

  VAR i: INTEGER;
  BEGIN
    i := ReadWord(reader, reader.wordConvKind, FALSE);
    IF Word.LT (i,min) OR Word.GT(i, max) THEN RaiseUnmarshalFailure(); END;
    RETURN i;
  END InWord;

PROCEDURE InLongint(reader: Pickle.Reader;
                    min := FIRST(LONGINT);
                    max := LAST(LONGINT)): LONGINT
    RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  VAR i: LONGINT;
  BEGIN
    i := VAL(ReadWord(reader, reader.longConvKind, TRUE), LONGINT);
    IF i < min OR i > max THEN RaiseUnmarshalFailure(); END;
    RETURN i;
  END InLongint;

(* Read 4 bytes, regardless of BYTESIZE(INTEGER). *)
PROCEDURE InInt32(reader: Pickle.Reader;
                    min := FIRST(Int32);
                    max := LAST(Int32)): Int32
    RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  VAR b: ARRAY [0..3] OF CHAR;
  VAR i: INTEGER;
  BEGIN
    GetBytes(reader, b);
    i := SignExtend32(FromBytes(b, IsSwap(reader.wordConvKind)));
    IF i < min OR i > max THEN RaiseUnmarshalFailure(); END;
    RETURN i;
  END InInt32;

PROCEDURE OutInteger(writer: Pickle.Writer; i: INTEGER)
    RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    PutBytes(writer, i, BYTESIZE(INTEGER));
  END OutInteger;

PROCEDURE OutLongint(writer: Pickle.Writer; i: LONGINT)
    RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    PutBytes(writer, VAL(i, INTEGER), BYTESIZE(LONGINT));
  END OutLongint;

(* Write i in 4 bytes, regardless of BYTESIZE(INTEGER). *)
PROCEDURE OutInt32(writer: Pickle.Writer; i: Int32)
    RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    PutBytes(writer, i, 4);
  END OutInt32;

PROCEDURE InByte(reader: Pickle.Reader;
                 max := LAST(Byte8)): Byte8
     RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  VAR b: Byte8;
  BEGIN
    TRY
      b := ORD(Rd.GetChar(reader.rd));
    EXCEPT
    | Rd.EndOfFile => RaiseUnmarshalFailure();
    END;
    IF b > max THEN
      RaiseUnmarshalFailure();
    END;
    RETURN b
  END InByte;

PROCEDURE OutByte(writer: Pickle.Writer; b: Byte8)
   RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    Wr.PutChar(writer.wr, VAL(b, CHAR));
  END OutByte;

PROCEDURE InCardinal(reader: Pickle.Reader;
     lim: CARDINAL := LAST(CARDINAL)): CARDINAL
     RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  BEGIN
    RETURN InInteger(reader, 0, lim);
  END InCardinal;

PROCEDURE OutCardinal(writer: Pickle.Writer; card: CARDINAL)
   RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    OutInteger(writer, card);
  END OutCardinal;

PROCEDURE InLongcard(reader: Pickle.Reader;
     lim: LONGCARD := LAST(LONGCARD)): LONGCARD
     RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  BEGIN
    RETURN InLongint(reader, 0L, lim);
  END InLongcard;

PROCEDURE OutLongcard(writer: Pickle.Writer; card: LONGCARD)
   RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    OutLongint(writer, card);
  END OutLongcard;

PROCEDURE InFloatBits(reader: Pickle.Reader; VAR b: ARRAY OF CHAR): Word.T
    RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  (* The bits of a floating-point number of NUMBER(b) bytes. *)
  BEGIN
    IF reader.packing.float # myPacking.float THEN
      RaiseUnsupportedDataRep();
    END;
    GetBytes(reader, b);
    RETURN FromBytes(b, NOT NativeEndian(reader.packing));
  END InFloatBits;

PROCEDURE InReal(reader: Pickle.Reader): REAL
    RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  VAR b: ARRAY [0..BYTESIZE(REAL)-1] OF CHAR;
  BEGIN
    RETURN M3jFloat.RealFromBits(InFloatBits(reader, b));
  END InReal;

PROCEDURE OutReal(writer: Pickle.Writer; i: REAL)
   RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    PutBytes(writer, M3jFloat.RealBits(i), BYTESIZE(REAL));
  END OutReal;

PROCEDURE InLongreal(reader: Pickle.Reader): LONGREAL
    RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  VAR b: ARRAY [0..BYTESIZE(LONGREAL)-1] OF CHAR;
  BEGIN
    RETURN M3jFloat.LongFromBits(InFloatBits(reader, b));
  END InLongreal;

PROCEDURE OutLongreal(writer: Pickle.Writer; i: LONGREAL)
   RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    PutBytes(writer, M3jFloat.LongBits(i), BYTESIZE(LONGREAL));
  END OutLongreal;

PROCEDURE InExtended(reader: Pickle.Reader): EXTENDED
    RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  (* EXTENDED is LONGREAL on AMD64_NT *)
  VAR b: ARRAY [0..BYTESIZE(EXTENDED)-1] OF CHAR;
  BEGIN
    RETURN FLOAT(M3jFloat.LongFromBits(InFloatBits(reader, b)), EXTENDED);
  END InExtended;

PROCEDURE OutExtended(writer: Pickle.Writer; i: EXTENDED)
   RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    PutBytes(writer, M3jFloat.LongBits(FLOAT(i, LONGREAL)), BYTESIZE(EXTENDED));
  END OutExtended;

PROCEDURE InBoolean(reader: Pickle.Reader) : BOOLEAN
    RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  VAR res: BOOLEAN;
  BEGIN
    TRY
      res := Rd.GetChar(reader.rd) # '\000';
    EXCEPT
    | Rd.EndOfFile => RaiseUnmarshalFailure();
    END;
    RETURN res;
  END InBoolean;

PROCEDURE OutBoolean(writer: Pickle.Writer; bool: BOOLEAN)
    RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    IF bool THEN
      Wr.PutChar(writer.wr, '\001');
    ELSE
      Wr.PutChar(writer.wr, '\000');
    END;
  END OutBoolean;

PROCEDURE InText(reader: Pickle.Reader) : TEXT
   RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  VAR len: INTEGER;
  BEGIN
    len := InInt32(reader);
    IF len = -1 THEN
      RETURN NIL;
    ELSIF len = 0 THEN
      RETURN "";
    ELSIF len < 0 THEN
      RaiseUnmarshalFailure();
      RETURN NIL;
    ELSIF InByte(reader) # ORD(FALSE) THEN
      RETURN InText16(reader, len);
    ELSE
      RETURN InText8(reader, len);
    END;
  END InText;

PROCEDURE InText16(reader: Pickle.Reader;  len: INTEGER) : TEXT
   RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  VAR buf := NEW(REF ARRAY OF WIDECHAR, len);
  BEGIN
    InWideChars(reader, buf^);
    RETURN Text.FromWideChars(buf^);
  END InText16;

PROCEDURE InText8(reader: Pickle.Reader;  len: INTEGER) : TEXT
   RAISES {Pickle.Error, Rd.Failure, Thread.Alerted} =
  VAR buf := NEW(REF ARRAY OF CHAR, len);
  BEGIN
    InChars(reader, buf^);
    RETURN Text.FromChars(buf^);
  END InText8;

PROCEDURE OutText(writer: Pickle.Writer; txt: TEXT)
  RAISES {Wr.Failure, Thread.Alerted} =
  VAR len: INTEGER;
  BEGIN
    IF txt = NIL THEN
      OutInt32(writer, -1);
    ELSE
      len := Text.Length(txt);
      OutInt32(writer, len);
      IF len > 0 THEN
        IF Text.HasWideChars(txt) THEN
          OutByte(writer, ORD(TRUE));
          VAR buf := NEW(REF ARRAY OF WIDECHAR, len); BEGIN
            Text.SetWideChars(buf^, txt);
            OutWideChars(writer, buf^);
          END;
        ELSE (* 8-bit characters only *)
          OutByte(writer, ORD(FALSE));
          VAR buf := NEW(REF ARRAY OF CHAR, len); BEGIN
            Text.SetChars(buf^, txt);
            OutChars(writer, buf^);
          END;
        END;
      END;
    END;
  END OutText;

PROCEDURE NativeEndian(packing: RTPacking.T) : BOOLEAN =
  BEGIN
    RETURN packing.little_endian = myPacking.little_endian;
  END NativeEndian;

PROCEDURE RaiseUnmarshalFailure() RAISES {Pickle.Error} =
  BEGIN
    RaiseError("UnmarshalFailure");
  END RaiseUnmarshalFailure;

PROCEDURE RaiseUnsupportedDataRep() RAISES {Pickle.Error} =
  BEGIN
    RaiseError("UnsupportedDataRep");
  END RaiseUnsupportedDataRep;

PROCEDURE RaiseError(t: TEXT) RAISES {Pickle.Error} =
  BEGIN
    RAISE Pickle.Error(t);
  END RaiseError;

BEGIN
END PickleStubs.
