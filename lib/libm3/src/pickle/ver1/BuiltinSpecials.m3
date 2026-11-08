(* Changed for m3j from the UNSAFE module of libm3: TEXT is a Java string
   in m3j, so one special for TEXT takes the place of the specials for
   TextLiteral.T and Text8CString.T. It writes every text as CM3 writes a
   text literal (Pickle writes the fingerprint of TextLiteral.T for it):
   the length and the characters, or for a text with wide characters the
   negated length and two bytes (little-endian) for each character. It
   reads such texts with Text.FromChars and Text.FromWideChars, as CM3
   does. The specials for Atom.T and RefList.T are those of CM3. *)

(* Copyright 1996-2000, Critical Mass, Inc.  All rights reserved. *)
(* See file COPYRIGHT-CMASS for details. *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

MODULE BuiltinSpecials;

IMPORT Atom, Pickle, Rd, RefList;
IMPORT Text, Thread, Wr;

FROM Pickle IMPORT Error, RefID, Special, Reader, Writer;

VAR init_done := FALSE;

PROCEDURE Register () =
  BEGIN
    IF init_done THEN RETURN; END;
    Pickle.RegisterSpecial (NEW (Special, sc := TYPECODE (TEXT),
                                 write := TextPklWrite, read  := TextPklRead));
    Pickle.RegisterSpecial (NEW (Special, sc := TYPECODE (Atom.T),
                                 write := AtomPklWrite, read  := AtomPklRead));
    Pickle.RegisterSpecial (NEW (Special, sc := TYPECODE (RefList.T),
                                 write := ListPklWrite, read  := ListPklRead));
    init_done := TRUE;
  END Register;

(*-------------------------------------------------------- TEXT specials ---*)

PROCEDURE TextPklWrite (<*UNUSED*> sp: Special;  r: REFANY;  pwr: Writer)
  RAISES {Error, Wr.Failure, Thread.Alerted} =
  VAR txt: TEXT := r;  len := Text.Length (txt);
  BEGIN
    IF Text.HasWideChars (txt) THEN
      pwr.writeInt (-len);
      VAR buf := NEW (REF ARRAY OF WIDECHAR, len); BEGIN
        Text.SetWideChars (buf^, txt);
        FOR i := 0 TO len - 1 DO
          Wr.PutChar (pwr.wr, VAL (ORD (buf[i]) MOD 256, CHAR));
          Wr.PutChar (pwr.wr, VAL (ORD (buf[i]) DIV 256, CHAR));
        END;
      END;
    ELSE
      pwr.writeInt (len);
      VAR buf := NEW (REF ARRAY OF CHAR, len); BEGIN
        Text.SetChars (buf^, txt);
        Wr.PutString (pwr.wr, buf^);
      END;
    END;
  END TextPklWrite;

PROCEDURE TextPklRead (<*UNUSED*> sp: Special;  prd: Reader;
                              <*UNUSED*> id: RefID): REFANY
  RAISES {Error, Rd.EndOfFile, Rd.Failure, Thread.Alerted} =
  VAR len: INTEGER := prd.readInt ();
  BEGIN
    IF (len >= 0)
      THEN RETURN Text8Read (prd, len);
      ELSE RETURN Text16Read (prd, -len);
    END;
  END TextPklRead;

PROCEDURE Text8Read (prd: Reader;  len: INTEGER): TEXT
  RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR
    xx  : INTEGER;
    buf := NEW (REF ARRAY OF CHAR, len);
  BEGIN
    xx := Rd.GetSub (prd.rd, buf^);
    IF xx # len THEN TooShort (); END;
    RETURN Text.FromChars (buf^);
  END Text8Read;

PROCEDURE Text16Read (prd: Reader;  len: INTEGER): TEXT
  RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR
    xx  : INTEGER;
    bytes := NEW (REF ARRAY OF CHAR, 2 * len);
    buf := NEW (REF ARRAY OF WIDECHAR, len);
  BEGIN
    xx := Rd.GetSub (prd.rd, bytes^);
    IF xx # 2 * len THEN TooShort (); END;
    FOR i := 0 TO len - 1 DO
      buf[i] := VAL (ORD (bytes[2 * i]) + 256 * ORD (bytes[2 * i + 1]), WIDECHAR);
    END;
    RETURN Text.FromWideChars (buf^);
  END Text16Read;

(*--------------------------------------------------------------- Atom.T ---*)

PROCEDURE AtomPklWrite (<*UNUSED*> sp: Special;  r: REFANY;  pwr: Writer)
  RAISES {Error, Wr.Failure, Thread.Alerted } =
  BEGIN
    pwr.write (Atom.ToText (r));
  END AtomPklWrite;

PROCEDURE AtomPklRead (<*UNUSED*> sp: Special;  prd: Reader;
                       <*UNUSED*> id: RefID): REFANY
  RAISES { Error, Rd.EndOfFile, Rd.Failure, Thread.Alerted } =
  BEGIN
    RETURN Atom.FromText (prd.read ());
  END AtomPklRead;

(*------------------------------------------------------------ RefList.T ---*)
(* Iterative List Special to prevent small stacks from
   overflowing on RefList.Ts of length >~ 25. *)

PROCEDURE ListPklWrite (sp: Special;  r: REFANY;  pwr: Writer)
  RAISES { Error, Wr.Failure, Thread.Alerted } =
  VAR
    l: RefList.T := r;
    len := RefList.Length (l);
  BEGIN
    IF PutSubtypeMark (r, TYPECODE (RefList.T), pwr) THEN
      (* we don't know how to marshal subtypes of RefList.T *)
      Special.write (sp, r, pwr);
    ELSE
      pwr.writeInt (len);
      FOR i := 1 TO len DO
        pwr.write (l.head);
        l := l.tail;
      END;
      <* ASSERT l = NIL *>
    END;
  END ListPklWrite;

PROCEDURE ListPklRead (sp: Special;  prd: Reader;  id: RefID) : REFANY
  RAISES { Error, Rd.EndOfFile, Rd.Failure, Thread.Alerted } =
  VAR
    len: CARDINAL;
    res, tail: RefList.T;
  BEGIN
    IF GetSubtypeMark (prd) THEN
      (* the writer encountered a subtype of RefList.T *)
      res := Special.read (sp, prd, id);
    ELSE
      len := ReadLength (prd);
      res := NEW (RefList.T);
      tail := res;
      FOR i := 1 TO len - 1 DO
        tail.head := prd.read ();
        tail.tail := NEW (RefList.T);
        tail := tail.tail;
      END;
      tail.head := prd.read ();
      tail.tail := NIL;
    END;
    RETURN res;
  END ListPklRead;

(*------------------------------------------------------------- internal ---*)

PROCEDURE ReadLength (prd: Reader): CARDINAL
  RAISES {Error, Rd.EndOfFile, Rd.Failure, Thread.Alerted} =
  VAR len: INTEGER := prd.readInt ();
  BEGIN
    IF len < 0 THEN
      RAISE Error("Pickle.Error: negative length in builtin type");
    END;
    RETURN len;
  END ReadLength;

PROCEDURE PutSubtypeMark (r: REFANY;  tcode: INTEGER;  pwr: Writer): BOOLEAN
  RAISES {Wr.Failure, Thread.Alerted} =
  VAR isSubtype := TYPECODE (r) # tcode;
  BEGIN
    Wr.PutChar (pwr.wr, VAL (ORD (isSubtype), CHAR));
    RETURN isSubtype;
  END PutSubtypeMark;

PROCEDURE GetSubtypeMark (prd: Reader): BOOLEAN
  RAISES {Rd.EndOfFile, Rd.Failure, Thread.Alerted} =
  CONST TrueMark = VAL (ORD (TRUE), CHAR);
  BEGIN
    RETURN Rd.GetChar (prd.rd) = TrueMark;
  END GetSubtypeMark;

PROCEDURE TooShort ()
  RAISES {Error} =
  BEGIN
    RAISE Error ("EOF encountered while reading a TEXT literal");
  END TooShort;

BEGIN
END BuiltinSpecials.
