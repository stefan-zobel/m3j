(* Changed for m3j from the UNSAFE module of libm3, to safe code that
   writes and reads the same pickles: the bytes of a referent come from
   M3jPickle (the layout of CM3 on AMD64_NT) instead of from memory through
   RTHeap and RTTypeMap, new referents come from M3jPickle.New instead of
   RTAllocator, and the table of the references written is an identity
   table of M3jPickle instead of a hash table on their addresses. Integers
   and fingerprints are written byte by byte (little-endian). TEXT, a Java
   string in m3j, is written as CM3 writes a text literal; a reader takes
   the texts of CM3 of any class. Typecodes are given to types when they
   are first used, so the tables of typecodes grow when needed. *)

(* Copyright 1992 Digital Equipment Corporation             *)
(* Distributed only by permission.                          *)
(* Last modified on Thu Jan 26 13:56:19 PST 1995 by kalsow  *)
(*      modified on Tue Mar  1 16:17:32 PST 1994 by wobber  *)
(*      modified on Mon Mar  8 12:43:59 PST 1993 by mjordan *)
(*      modified on Tue Jan 26 16:21:22 1993 by birrell *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* This module implements Pickles.  See the interface for
   documentation *)

MODULE Pickle;

IMPORT BuiltinSpecials, M3jPickle, Rd, RTType, RTTypeFP, Text, Thread;
IMPORT Word, Wr, Fingerprint;

(* *)
(* Syntax of a pickle, and constants pertaining thereto *)
(* *)

(* Pickle::      Header Value Trailer
   Header::      Header1                - verify it's a pickle
                 Header2                - verify it's a pickle
                 Version                - version of this syntax
                 CharRep                - character encoding
                 Endian                 - byte order
                 IntSize                - size of an INTEGER
                 RealRep                - format of REAL and such
   CharRep::     CharRepISOLatin1
   Endian::      LittleEndian | BigEndian
   IntSize::     IntSize16 | IntSize32 | IntSize64
   RealRep::     RealRepNative
   Value::       '0'                    - r=NIL
                 '1' 4-byte-integer     - pickle-relative index of "r" (0 based)
                 '2' 8-bytes              - COMPATIBILITY, old fingerprint
                 '3' scInt acInt Contents - COMPATIBILITY, old value
                 - '4' is not used, but occurs in Trailer as blunder check.
                 '5' sc Contents        - an actual value
   FingerPrint:: bytes                  - 8 bytes, details t.b.d.
   sc::          LocalCode              - typecode of Special
                                          writing the value
   Contents::    ( bytes | Value )*     - Special.write output
   Trailer::     Trailer1 Trailer2      - verify the parse was OK

   - Note that "LocalCode" may occur within the bytes written by a
     special. E.g. the root special does this to record the allocated
     type of the value. It is written by WriteType and parsed by ReadType.

   LocalCode::   0 8-bytes |            - first occurence of a fingerprint.
                 [1..254] |             - subsequent occurrence, n IN [1..254].
                 255 4-byte-integer     - subsequent occurence, other cases.
   *)

CONST
  Header1 = '&';
  Header2 = '%';
  Version = '2';                       (* current syntax *)
  OldVersion = '1';                    (* old FP and ac syntax *)
  CharRepISOLatin1 = 'I';              (* 8-bit ISO Latin 1 *)
  LittleEndian = 'L';                  (* l.s. byte first *)
  IntSize16 = '4';                     (* 16 bit INTEGER *)
  IntSize32 = '5';                     (* 32 bit INTEGER *)
  IntSize64 = '6';                     (* 64 bit INTEGER *)
  RealRepNative = 'N';                 (* whatever the writing
                                          host used *)
  Trailer1 = '4';                      (* # main cases *)
  Trailer2 = '\n';                     (* keeps editors happy *)

  NilTypecode = 0;                     (* TYPECODE (NULL) *)
  AdrSize = BYTESIZE (ADDRESS);        (* the size of a reference *)


(* *)
(* Constants, types and revelations *)
(* *)

TYPE
  RefArray = REF ARRAY OF REFANY;
    (* array indexed by index in pickle, yields REFANY *)
  TypeTable = REF ARRAY OF INTEGER;
    (* indexed by RTType.TypeCode, yields pickle-relative typecode *)
    (* Or indexed by pickle-relative typecode, yields RTTypes.TypeCode *)
  Bytes = REF ARRAY OF CHAR;

  CharArray = REF ARRAY OF CHAR;
    (* the characters of a Text8.T of CM3 *)
  WideArray = REF ARRAY OF WIDECHAR;
    (* the characters of a Text16.T of CM3 *)

REVEAL
  Writer = WriterPublic BRANDED "Pickle.Writer 1.0" OBJECT
      level := 0;
      refCount: INTEGER;         (* count of refs written in this pickle *)
      refs: M3jPickle.RefTable := NIL;  (* the refs in this pickle *)
      tcCount: INTEGER;          (* count of typecodes in this pickle *)
      tcToPkl: TypeTable := NIL; (* process TC -> pickle TC *)
      pklToTC: TypeTable := NIL; (* pickle TC -> process TC, for erasing tcToPkl *)
    OVERRIDES
      write := WriteRef;
      writeType := WriteType;
      writeInt := WriteInt;
    END;

  Reader = ReaderPublic BRANDED "Pickle.Reader 1.0" OBJECT
      level := 0;
      acPending := 0;            (* COMPATIBILITY - OldVersion *)
      refCount: INTEGER;         (* count of refs read in this pickle *)
      tcCount: INTEGER;          (* count of typecodes in this pickle *)
      refs: RefArray := NIL;     (* array of refs in this pickle *)
      pklToTC: TypeTable := NIL; (* pickle TC -> process TC *)
    OVERRIDES
      read := ReadRef;
      readType := ReadType;
      readInt := ReadInt;
      noteRef := NoteRef;
    END;

  Special = SpecialPublic BRANDED "Pickle.Special 1.0" OBJECT
    OVERRIDES
      write := RootSpecialWrite;
      read := RootSpecialRead;
    END;

CONST Int32Hi = 16_7FFFFFFF;
CONST Int32Lo = -Int32Hi-1;
TYPE Int32 = [Int32Lo..Int32Hi];

TYPE
  HC = { h1, h2, v, c, e, i, r }; (* the chars in a pickle header *)
  HT = { t1, t2 };                (* the chars in a pickle trailer *)
  Header = ARRAY HC OF CHAR;      (* a pickle header string *)
  Trailer = ARRAY HT OF CHAR;     (* a pickle trailer string *)

CONST
  InitRefCapacity  = 99;          (* Init size of {Reader,Writer}.refs *)
  InitTypeCapacity = 99;          (* Init size of *.pklToTC *)

(* The texts of CM3. A text is written as a text literal (TextLiteral.T,
   by the special of TEXT), which CM3 reads; a reader also takes the
   texts that CM3 writes with the root special, as objects of the classes
   of texts of m3core. Their fingerprints are those of CM3. *)

CONST
  TextLiteralFP  = Fingerprint.T {ARRAY [0..7] OF BITS 8 FOR [0..255]
    {16_81, 16_ef, 16_ba, 16_63, 16_f6, 16_28, 16_44, 16_12}};
  Text8CStringFP = Fingerprint.T {ARRAY [0..7] OF BITS 8 FOR [0..255]
    {16_fd, 16_ef, 16_0d, 16_d8, 16_f6, 16_c9, 16_70, 16_13}};

TYPE
  TextClass = { Text8, Text16, Text8Short, Text16Short, TextCat, TextSub };

CONST
  TextClassFP = ARRAY TextClass OF Fingerprint.T {
    Fingerprint.T {ARRAY [0..7] OF BITS 8 FOR [0..255]
      {16_f4, 16_ef, 16_0e, 16_99, 16_2f, 16_0a, 16_7e, 16_04}},
    Fingerprint.T {ARRAY [0..7] OF BITS 8 FOR [0..255]
      {16_27, 16_0f, 16_1b, 16_a7, 16_c8, 16_ed, 16_03, 16_01}},
    Fingerprint.T {ARRAY [0..7] OF BITS 8 FOR [0..255]
      {16_18, 16_f4, 16_5c, 16_e2, 16_b4, 16_67, 16_fe, 16_1e}},
    Fingerprint.T {ARRAY [0..7] OF BITS 8 FOR [0..255]
      {16_bf, 16_2b, 16_31, 16_99, 16_99, 16_4f, 16_3d, 16_10}},
    Fingerprint.T {ARRAY [0..7] OF BITS 8 FOR [0..255]
      {16_46, 16_cd, 16_0f, 16_ea, 16_dd, 16_ca, 16_a9, 16_15}},
    Fingerprint.T {ARRAY [0..7] OF BITS 8 FOR [0..255]
      {16_ed, 16_c7, 16_84, 16_3d, 16_37, 16_e9, 16_44, 16_07}}};

  (* the typecodes in a reader's pklToTC for these classes *)
  TextClassTC = ARRAY TextClass OF INTEGER { -2, -3, -4, -5, -6, -7 };


(* *)
(* Global variables (gasp!); initialized in main body *)
(* *)

VAR myHeader: Header;             (* header for pickles we write *)
VAR myTrailer: Trailer;           (* trailer for pickles we write *)
VAR nullReaderRef: REF INTEGER;   (* null value for reader.refs entries *)


(* *)
(* Top-level sugar: Write and Read *)
(* *)

PROCEDURE Write(wr: Wr.T; r: REFANY)
        RAISES { Error, Wr.Failure, Thread.Alerted } =
  BEGIN
    NEW (Writer, wr := wr).write(r);
  END Write;

PROCEDURE Read(rd: Rd.T): REFANY
      RAISES { Error, Rd.EndOfFile, Rd.Failure, Thread.Alerted } =
  BEGIN
    RETURN NEW (Reader, rd := rd).read();
  END Read;


(* *)
(* Writer methods and subroutines *)
(* *)

PROCEDURE WriteRef(writer: Writer; r: REFANY)
        RAISES { Error, Wr.Failure, Thread.Alerted } =
    VAR index: INTEGER;
    VAR sp: Special;
  BEGIN
    IF writer.level = 0 THEN
      (* Start of a pickle; do the overhead and call ourselves
         recursively *)
      IF writer.refs = NIL THEN
        (* deferred allocation *)
        writer.refs := M3jPickle.NewTable();
        writer.tcCount := 0; (* prevent excessive initialization *)
        writer.tcToPkl := NEW(TypeTable, RTType.MaxTypecode()+1);
        writer.pklToTC := NEW(TypeTable, InitTypeCapacity);
        WITH tcs = writer.tcToPkl^ DO
          FOR i := 0 TO LAST(tcs) DO tcs[i] := 0 END;
        END;
      END;
      (* (Re-)initialize the writer's state *)
      writer.refCount := 0;
      FOR i := 1 TO writer.tcCount DO writer.tcToPkl[writer.pklToTC[i]] := 0 END;
      writer.tcCount := 0;
      Wr.PutString(writer.wr, myHeader);
      INC(writer.level);
      TRY
        WriteRef(writer, r);
      FINALLY
        DEC(writer.level);
        (* Flush the refs table now, to encourage the garbage
           collector. It must be flushed before re-use anyway *)
        M3jPickle.Clear(writer.refs);
      END;(*TRY*)
      Wr.PutString(writer.wr, myTrailer);
    ELSE
      (* Normal case: level#0 *)
      IF r = NIL THEN
        Wr.PutChar(writer.wr, '0');
      ELSE
        index := M3jPickle.Index(writer.refs, r, writer.refCount);
        IF index < 0 THEN
          (* virgin: now in the table with the next index *)
          INC(writer.refCount);
          Wr.PutChar(writer.wr, '5');
          sp := GetSpecial (TYPECODE (r));
          writer.writeType(sp.sc);
          sp.write(r, writer);
        ELSE
          (* recycled *)
          Wr.PutChar(writer.wr, '1');
          writer.writeInt(index);
        END;
      END;(*r#NIL*)
    END;(*IF level=0 THEN ELSE*)
  END WriteRef;

PROCEDURE ExtendWriterTypes(writer: Writer) =
  (* Extend writer.pklToTC *)
    VAR old := writer.pklToTC;
  BEGIN
    writer.pklToTC := NEW(TypeTable, NUMBER(writer.pklToTC^) * 2);
    SUBARRAY(writer.pklToTC^, 0, NUMBER(old^)) := old^;
  END ExtendWriterTypes;

PROCEDURE ExtendWriterTCs(writer: Writer; tc: INTEGER) =
  (* Extend writer.tcToPkl to hold tc *)
    VAR old := writer.tcToPkl;
  BEGIN
    writer.tcToPkl := NEW(TypeTable, MAX(tc+1, RTType.MaxTypecode()+1));
    SUBARRAY(writer.tcToPkl^, 0, NUMBER(old^)) := old^;
    FOR i := NUMBER(old^) TO LAST(writer.tcToPkl^) DO
      writer.tcToPkl[i] := 0;
    END;
  END ExtendWriterTCs;

PROCEDURE WriteType(writer: Writer; tc: INTEGER)
        RAISES { Wr.Failure, Thread.Alerted } =
    VAR fp: Fingerprint.T;
  BEGIN
    IF tc > LAST(writer.tcToPkl^) THEN ExtendWriterTCs(writer, tc) END;
    WITH pickleTC = writer.tcToPkl[tc] DO
      IF pickleTC = 0 THEN
        INC(writer.tcCount);
        IF writer.tcCount >= NUMBER(writer.pklToTC^) THEN
          ExtendWriterTypes(writer);
        END;
        pickleTC := writer.tcCount;
        writer.pklToTC[writer.tcCount] := tc;
        IF tc = TYPECODE(TEXT) THEN
          fp := TextLiteralFP;
        ELSE
          fp := RTTypeFP.ToFingerprint(tc);
        END;
        Wr.PutChar(writer.wr, VAL(0, CHAR));
        FOR i := 0 TO LAST(fp.byte) DO
          Wr.PutChar(writer.wr, VAL(fp.byte[i], CHAR));
        END;
      ELSIF pickleTC < 255 THEN
        Wr.PutChar(writer.wr, VAL(pickleTC, CHAR));
      ELSE
        Wr.PutChar(writer.wr, VAL(255, CHAR));
        writer.writeInt(pickleTC);
      END;
    END;
  END WriteType;

PROCEDURE WriteInt(writer: Writer; i: INTEGER)
        RAISES { Wr.Failure, Thread.Alerted } =
    VAR i32: Int32 := i;
    VAR w := Word.And(i32, 16_FFFFFFFF);
  BEGIN
    FOR k := 0 TO 3 DO
      Wr.PutChar(writer.wr, VAL(Word.And(Word.RightShift(w, 8 * k), 16_FF), CHAR));
    END;
  END WriteInt;

(* *)
(* Reader methods and subroutines *)
(* *)

PROCEDURE ExtendReaderTypes(reader: Reader) =
  (* Extend reader.pklToTC *)
    VAR old := reader.pklToTC;
  BEGIN
    reader.pklToTC := NEW(TypeTable, NUMBER(reader.pklToTC^) * 2);
    SUBARRAY(reader.pklToTC^, 0, NUMBER(old^)) := old^;
  END ExtendReaderTypes;

PROCEDURE GetBinaryInt(rd: Rd.T): INTEGER
        RAISES { Rd.EndOfFile, Rd.Failure, Thread.Alerted } =
    VAR b: ARRAY [0..3] OF CHAR;
    VAR i: INTEGER := 0;
  BEGIN
    IF Rd.GetSub(rd, b) # NUMBER(b) THEN
        RAISE Rd.EndOfFile
    END;
    FOR k := 3 TO 0 BY -1 DO
      i := Word.Or(Word.LeftShift(i, 8), ORD(b[k]));
    END;
    IF i > Int32Hi THEN DEC(i, 16_100000000) END;
    RETURN i;
  END GetBinaryInt;

PROCEDURE GetBytes(rd: Rd.T; VAR b: ARRAY OF CHAR)
        RAISES { Error, Rd.Failure, Thread.Alerted } =
  (* Read NUMBER (b) bytes *)
  BEGIN
    IF Rd.GetSub(rd, b) # NUMBER(b) THEN
      RAISE Error("Malformed pickle (unexpected end)")
    END;
  END GetBytes;

PROCEDURE FromBytes(READONLY b: ARRAY OF CHAR): INTEGER =
  (* The INTEGER of the little-endian bytes b *)
    VAR i: INTEGER := 0;
  BEGIN
    FOR k := LAST(b) TO 0 BY -1 DO
      i := Word.Or(Word.LeftShift(i, 8), ORD(b[k]));
    END;
    RETURN i;
  END FromBytes;

PROCEDURE TypecodeOf(READONLY fp: Fingerprint.T): TypeCode =
  (* The typecode of the type with fingerprint fp in this program *)
  BEGIN
    IF fp = TextLiteralFP OR fp = Text8CStringFP THEN
      RETURN TYPECODE(TEXT);
    END;
    FOR c := FIRST(TextClass) TO LAST(TextClass) DO
      IF fp = TextClassFP[c] THEN RETURN TextClassTC[c] END;
    END;
    RETURN RTTypeFP.FromFingerprint(fp);
  END TypecodeOf;

PROCEDURE ReadFP(reader: Reader): TypeCode
      RAISES { Error, Rd.EndOfFile, Rd.Failure, Thread.Alerted } =
    (* Read a fingerprint (8 bytes), store in reader.pklToTC,
       and return typecode *)
    VAR fp: Fingerprint.T; tc: TypeCode; b: ARRAY [0..7] OF CHAR;
  BEGIN
    IF Rd.GetSub(reader.rd, b) # NUMBER(b) THEN
      RAISE Rd.EndOfFile
    END;
    FOR i := 0 TO 7 DO fp.byte[i] := ORD(b[i]) END;
    INC(reader.tcCount);
    IF reader.tcCount >= NUMBER(reader.pklToTC^) THEN
      ExtendReaderTypes(reader);
    END;
    tc := TypecodeOf(fp);
    IF tc = RTType.NoSuchType THEN
      RAISE Error(
             "Can't read pickle (type not known in this program)")
    END;
    reader.pklToTC[reader.tcCount] := tc;
    RETURN tc
  END ReadFP;

PROCEDURE TCFromIndex(reader: Reader; index: INTEGER): TypeCode
      RAISES { Error } =
  BEGIN
    IF index > reader.tcCount THEN
      RAISE Error("Malformed pickle (TC index too large)")
    END;
    RETURN reader.pklToTC[index]
  END TCFromIndex;

PROCEDURE InvokeSpecial(reader: Reader; sc: TypeCode): REFANY
  RAISES { Error, Rd.EndOfFile, Rd.Failure, Thread.Alerted } =
  VAR sp: Special; r: REFANY; id: RefID;
  BEGIN
    IF sc < 0 THEN
      RAISE Error("Can't read pickle (Special not defined)")
    END;
    sp := GetSpecial (sc);
    IF sp.sc # sc THEN
      RAISE Error("Can't read pickle (Special not defined)")
    END;
    IF reader.refCount >= NUMBER(reader.refs^) THEN
      VAR old := reader.refs;
      BEGIN
        reader.refs := NEW(RefArray, NUMBER(reader.refs^) * 2);
        SUBARRAY(reader.refs^, 0, NUMBER(old^)) := old^;
      END;
    END;
    id := reader.refCount;
    reader.refs[id] := nullReaderRef;
    INC(reader.refCount);
    INC(reader.level);
    r := sp.read(reader, id);
    DEC(reader.level);
    reader.noteRef(r, id);
    RETURN r
  END InvokeSpecial;

PROCEDURE ReadRef(reader: Reader): REFANY
      RAISES { Error, Rd.EndOfFile, Rd.Failure, Thread.Alerted } =
    VAR r: REFANY; (* the result *)
    VAR repCase: CHAR;
  BEGIN
    IF reader.level = 0 THEN
      (* Start of a pickle *)
      IF reader.refs = NIL THEN
        (* deferred allocation *)
        reader.refs := NEW(RefArray, InitRefCapacity);
        reader.pklToTC := NEW(TypeTable, InitTypeCapacity);
      END;
      (* (Re-)initialize the reader's state *)
      reader.refCount := 0;
      reader.tcCount := 0;
      (* Header *)
      VAR headerChars: Header;
      BEGIN
        IF Rd.GetSub(reader.rd, headerChars) # NUMBER(HC) THEN
          RAISE Rd.EndOfFile
        ELSIF (headerChars[HC.h1] # myHeader[HC.h1]) OR
                (headerChars[HC.h2] # myHeader[HC.h2]) THEN
          RAISE Error("Malformed pickle (wrong signature)")
        ELSIF (headerChars[HC.v] # myHeader[HC.v]) AND
              (headerChars[HC.v] # OldVersion) THEN
          RAISE Error("Can't read pickle (wrong version)")
        ELSIF headerChars[HC.c] # myHeader[HC.c] THEN
          RAISE Error("Can't read pickle (char rep)")
        ELSIF headerChars[HC.e] # myHeader[HC.e] THEN
          RAISE Error("Can't read pickle (endian)")
(*
        ELSIF headerChars[HC.i] # myHeader[HC.i] THEN
          RAISE Error("Can't read pickle (INTEGER size)")
*)
        ELSIF headerChars[HC.r] # myHeader[HC.r] THEN
          RAISE Error("Can't read pickle (REAL rep)")
        END;
      END;
    END;
    LOOP
      (* COMPATIBILITY: OldVersion uses '2 fingerprint' *)
      repCase := Rd.GetChar(reader.rd);
      IF repCase # '2' THEN EXIT END;
      EVAL ReadFP(reader)
    END;
    IF repCase = '0' THEN
      r := NIL;
    ELSIF repCase = '1' THEN
      VAR refIndex := reader.readInt();
      BEGIN
        IF refIndex >= reader.refCount THEN
          RAISE Error("Malformed pickle (ref index too large)")
        END;
        r := reader.refs[refIndex];
      END;
    ELSIF repCase = '3' THEN
      (* COMPATIBILITY: OldVersion uses 3 sc ac contents *)
      VAR sc := GetBinaryInt(reader.rd);
      BEGIN
        reader.acPending := GetBinaryInt(reader.rd);
        r := InvokeSpecial(reader, TCFromIndex(reader, sc));
        reader.acPending := 0;
      END
    ELSIF repCase = '5' THEN
      r := InvokeSpecial(reader, reader.readType());
    ELSE
      RAISE Error("Malformed pickle (unknown switch)")
    END;
    IF reader.level = 0 THEN
      IF (Rd.GetChar(reader.rd) # Trailer1) OR
              (Rd.GetChar(reader.rd) # Trailer2) THEN
        RAISE Error("Malformed pickle (wrong trailer)")
      END;
      (* flush the ref table to encourage the garbage collector *)
      WITH refs = reader.refs^ DO
        FOR i := 0 TO reader.refCount-1 DO refs[i] := NIL END;
      END;
    END;
    RETURN r
  END ReadRef;

PROCEDURE ReadType(reader: Reader): TypeCode
      RAISES { Error, Rd.EndOfFile, Rd.Failure, Thread.Alerted } =
    VAR c: CHAR; ac: INTEGER;
  BEGIN
    IF reader.acPending # 0 THEN
      (* COMPATIBILITY *)
      ac := reader.acPending; reader.acPending := 0;
      RETURN TCFromIndex(reader, ac);
    ELSE
      c := Rd.GetChar(reader.rd);
      IF ORD(c) = 0 THEN
        RETURN ReadFP(reader)
      ELSIF ORD(c) < 255 THEN
        RETURN TCFromIndex(reader, ORD(c));
      ELSE
        RETURN TCFromIndex(reader, reader.readInt());
      END;
    END;
  END ReadType;

PROCEDURE ReadInt(reader: Reader): INTEGER
        RAISES {Rd.EndOfFile, Rd.Failure, Thread.Alerted} =
  BEGIN
    RETURN GetBinaryInt(reader.rd)
  END ReadInt;

PROCEDURE NoteRef(reader: Reader; ref: REFANY; id: RefID) =
  BEGIN
    WITH rr = reader.refs[id] DO
      <* ASSERT (rr = nullReaderRef) OR (rr = ref) *>
      rr := ref;
    END;
  END NoteRef;



(*---------------------------------------------------------------- specials ---*)

TYPE
  SpecialTable = REF ARRAY OF Special;
    (* indexed by RTType.TypeCode, yields Special for nearest super-type *)

VAR
  specialsMu     := NEW(MUTEX);
  specials       : SpecialTable := NIL;     (* LL >= specialsMu *)
  theRootSpecial : Special;                 (* LL >= specialsMu *)

PROCEDURE GetSpecial (tc: TypeCode): Special =
  (* LL = 0 *)
  BEGIN
    LOCK specialsMu DO
      IF (specials = NIL) THEN InitSpecials (); END;
      IF (tc >= NUMBER (specials^)) THEN ExpandSpecials (); END;
      RETURN specials[tc];
    END;
  END GetSpecial;

PROCEDURE InitSpecials() =
  (* LL >= specialsMu *)
  VAR max_type := RTType.MaxTypecode ();
  BEGIN
    theRootSpecial := NEW(Special, sc := NilTypecode);
    specials := NEW(SpecialTable, max_type+1);
    FOR i := 0 TO LAST(specials^) DO
      specials[i] := theRootSpecial;
    END;
    Thread.Release (specialsMu);
    TRY BuiltinSpecials.Register ();
    FINALLY Thread.Acquire (specialsMu);
    END;
  END InitSpecials;

PROCEDURE ExpandSpecials() =
  (* LL >= specialsMu *)
  VAR max_type := RTType.MaxTypecode ();
  BEGIN
    IF (max_type > LAST (specials^)) THEN
      VAR new_sp := NEW (SpecialTable, max_type+1);  BEGIN
        SUBARRAY (new_sp^, 0, NUMBER (specials^)) := specials^;
        FOR i := NUMBER (specials^) TO LAST (new_sp^) DO
          new_sp[i] := FindBestSpecial (i);
        END;
        specials := new_sp;
      END;
    END;
  END ExpandSpecials;

PROCEDURE FindBestSpecial (tc: TypeCode): Special =
  VAR sp: Special;
  BEGIN
    LOOP
      tc := RTType.Supertype (tc);
      IF (tc = RTType.NoSuchType) THEN
        RETURN theRootSpecial;
      END;
      IF (0 <= tc) AND (tc < NUMBER (specials^)) THEN
        sp := specials [tc];
        IF (sp # NIL) THEN RETURN sp; END;
      END;
    END;
  END FindBestSpecial;

EXCEPTION DuplicateSpecial;

PROCEDURE RegisterSpecial(sp: Special) =
  <* FATAL DuplicateSpecial *>
  VAR xp: Special;
  BEGIN
    xp := GetSpecial (sp.sc);
    IF xp.sc = sp.sc THEN RAISE DuplicateSpecial; END;
    LOCK specialsMu DO
      FOR i := 0 TO LAST(specials^) DO
        IF (i#NilTypecode) AND RTType.IsSubtype(i,sp.sc) THEN
          (* i is a sub-type of this special *)
          IF (specials[i].sc = NilTypecode) OR
                      RTType.IsSubtype(sp.sc, specials[i].sc) THEN
            (* previous special for i isn't more specific than sp.sc *)
            specials[i] := sp;
          END;
        END;
      END;
    END;
  END RegisterSpecial;

PROCEDURE RootSpecialWrite(<*UNUSED*> sp: Special;
                           r: REFANY; writer: Writer)
        RAISES { Error, Wr.Failure, Thread.Alerted } =
    VAR nDim: INTEGER;
    VAR shape: ARRAY [0..10] OF INTEGER;
    VAR img := M3jPickle.ImageOf(r);
    VAR size, pos, off: INTEGER;
    VAR buf: Bytes;
  BEGIN
    IF img = NIL THEN
      RAISE Error("Can't write pickle (" & M3jPickle.Problem(r) & ")")
    END;
    writer.writeType(TYPECODE(r));
    nDim := M3jPickle.Shape(r, shape);
    FOR i := 0 TO nDim-1 DO
      writer.writeInt(shape[i]);
    END;
    size := M3jPickle.Size(img);
    buf := NEW(Bytes, size);
    M3jPickle.GetBytes(img, buf^);
    (* the data fields, with the refs in their places *)
    pos := 0;
    FOR i := 0 TO M3jPickle.Slots(img) - 1 DO
      off := M3jPickle.SlotOffset(img, i);
      IF off # pos THEN
        Wr.PutString(writer.wr, SUBARRAY(buf^, pos, off - pos));
      END;
      IF M3jPickle.SlotIsTraced(img, i) THEN
        writer.write(M3jPickle.GetRef(img, i));
      ELSE
        (* Other REF fields, including procedures, are discarded on
           write *)
      END;
      pos := off + AdrSize;
    END;
    (* Write remainder of the data fields *)
    IF size # pos THEN
      Wr.PutString(writer.wr, SUBARRAY(buf^, pos, size - pos));
    END;
  END RootSpecialWrite;

PROCEDURE RootSpecialRead(<*UNUSED*> sp: Special;
                          reader: Reader; id: RefID): REFANY
    RAISES { Error, Rd.EndOfFile, Rd.Failure, Thread.Alerted } =
    VAR nDim: INTEGER;
    VAR shape: ARRAY [0..10] OF INTEGER;
    VAR r: REFANY;
    VAR img: M3jPickle.Image;
    VAR size, pos, off: INTEGER;
    VAR buf: Bytes;
    VAR ac := reader.readType();
  BEGIN
    IF ac < 0 THEN RETURN ReadText(reader, ac) END;
    nDim := RTType.GetNDimensions(ac);
    IF nDim > NUMBER(shape) THEN
      RAISE Error("Can't read pickle (too many dimensions)")
    END;
    FOR i := 0 TO nDim-1 DO
      shape[i] := reader.readInt();
    END;
    r := M3jPickle.New(ac, SUBARRAY(shape, 0, nDim));
    IF r = NIL THEN
      RAISE Error("Can't read pickle (cannot allocate the type)")
    END;
    reader.noteRef(r, id);
    img := M3jPickle.ImageOf(r);
    IF img = NIL THEN
      RAISE Error("Can't read pickle (" & M3jPickle.Problem(r) & ")")
    END;
    size := M3jPickle.Size(img);
    buf := NEW(Bytes, size);
    pos := 0;
    FOR i := 0 TO M3jPickle.Slots(img) - 1 DO
      (* read data fields preceding the ref *)
      off := M3jPickle.SlotOffset(img, i);
      EVAL Rd.GetSub(reader.rd, SUBARRAY(buf^, pos, off - pos));
      IF M3jPickle.SlotIsTraced(img, i) THEN
        IF NOT M3jPickle.SetRef(img, i, reader.read()) THEN
          RAISE Error("Malformed pickle (a reference of a wrong type)")
        END;
      ELSE
        (* Other REF fields, including procedures, are discarded on
           write *)
        EVAL M3jPickle.SetRef(img, i, NIL);
      END;
      pos := off + AdrSize;
    END;
    (* Read remainder of the data fields *)
    EVAL Rd.GetSub(reader.rd, SUBARRAY(buf^, pos, size - pos));
    M3jPickle.SetBytes(img, buf^);
    RETURN r
  END RootSpecialRead;

(*------------------------------------------------------- texts of CM3 ---*)

PROCEDURE ReadText(reader: Reader; ac: TypeCode): TEXT
    RAISES { Error, Rd.EndOfFile, Rd.Failure, Thread.Alerted } =
  (* The data of an object of a class of texts of CM3 (TextClassTC),
     after the methods: the fields of the class in its layout. *)
  VAR b: ARRAY [0..39] OF CHAR; len: INTEGER;
  BEGIN
    IF ac = TextClassTC[TextClass.Text8] THEN
      (* contents: REF ARRAY OF CHAR, with a null character *)
      TYPECASE reader.read() OF
      | NULL => RETURN "";
      | CharArray(a) =>
          RETURN Text.FromChars(SUBARRAY(a^, 0, MAX(NUMBER(a^) - 1, 0)));
      ELSE
      END;
    ELSIF ac = TextClassTC[TextClass.Text16] THEN
      TYPECASE reader.read() OF
      | NULL => RETURN "";
      | WideArray(a) =>
          RETURN Text.FromWideChars(SUBARRAY(a^, 0, MAX(NUMBER(a^) - 1, 0)));
      ELSE
      END;
    ELSIF ac = TextClassTC[TextClass.Text8Short] THEN
      (* len: CARDINAL; contents: ARRAY [0..15] OF CHAR *)
      GetBytes(reader.rd, SUBARRAY(b, 0, 24));
      len := FromBytes(SUBARRAY(b, 0, 8));
      IF 0 <= len AND len <= 16 THEN
        RETURN Text.FromChars(SUBARRAY(b, 8, len));
      END;
    ELSIF ac = TextClassTC[TextClass.Text16Short] THEN
      (* len: CARDINAL; contents: ARRAY [0..15] OF WIDECHAR *)
      GetBytes(reader.rd, SUBARRAY(b, 0, 40));
      len := FromBytes(SUBARRAY(b, 0, 8));
      IF 0 <= len AND len <= 16 THEN
        VAR w: ARRAY [0..15] OF WIDECHAR; BEGIN
          FOR i := 0 TO len - 1 DO
            w[i] := VAL(FromBytes(SUBARRAY(b, 8 + 2 * i, 2)), WIDECHAR);
          END;
          RETURN Text.FromWideChars(SUBARRAY(w, 0, len));
        END;
      END;
    ELSIF ac = TextClassTC[TextClass.TextCat] THEN
      (* a, b: TEXT; a_len, b_len: CARDINAL; a_or_b_wide: BOOLEAN *)
      VAR x := AsText(reader.read()); y := AsText(reader.read()); BEGIN
        GetBytes(reader.rd, SUBARRAY(b, 0, 24));
        RETURN x & y;
      END;
    ELSIF ac = TextClassTC[TextClass.TextSub] THEN
      (* base: TEXT; start, len: CARDINAL *)
      VAR x := AsText(reader.read()); start: INTEGER; BEGIN
        GetBytes(reader.rd, SUBARRAY(b, 0, 16));
        start := FromBytes(SUBARRAY(b, 0, 8));
        len := FromBytes(SUBARRAY(b, 8, 8));
        IF 0 <= start AND 0 <= len THEN
          RETURN Text.Sub(x, start, len);
        END;
      END;
    END;
    RAISE Error("Malformed pickle (a text of a wrong form)");
  END ReadText;

PROCEDURE AsText(x: REFANY): TEXT RAISES { Error } =
  BEGIN
    TYPECASE x OF
    | NULL => RETURN "";
    | TEXT(t) => RETURN t;
    ELSE RAISE Error("Malformed pickle (a text of a wrong form)");
    END;
  END AsText;

(*---------------------------------------------------------- initialization ---*)

PROCEDURE InitHeader() =
  BEGIN
    myHeader[HC.h1] := Header1;
    myHeader[HC.h2] := Header2;
    myHeader[HC.v] := Version;
    myHeader[HC.c] := CharRepISOLatin1;
    myHeader[HC.e] := LittleEndian;    (* AMD64 *)
    IF BITSIZE(INTEGER) = 16 THEN
      myHeader[HC.i] := IntSize16
    ELSIF BITSIZE(INTEGER) = 32 THEN
      myHeader[HC.i] := IntSize32
    ELSIF BITSIZE(INTEGER) = 64 THEN
      myHeader[HC.i] := IntSize64
    ELSE
      myHeader[HC.i] := '?'
    END;
    myHeader[HC.r] := RealRepNative;
    myTrailer[HT.t1] := Trailer1;
    myTrailer[HT.t2] := Trailer2;
  END InitHeader;

BEGIN

  InitHeader();
  nullReaderRef := NEW(REF INTEGER);

END Pickle.
