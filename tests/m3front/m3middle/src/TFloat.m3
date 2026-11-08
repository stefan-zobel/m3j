(* Changed for m3j from the UNSAFE module of m3middle: ToBytes and
   FromBytes take the bits of REAL and LONGREAL from M3jFloat instead of
   reading the memory of a variable through its address, so the module is
   safe. The bytes are in the order of AMD64 (little endian), and EXTENDED
   is LONGREAL. *)

(* Copyright (C) 1993, Digital Equipment Corporation           *)
(* All rights reserved.                                        *)
(* See the file COPYRIGHT for a full description.              *)
(*                                                             *)
(* File: TFloat.m3                                             *)
(* Last Modified On Thu Jul 28 10:18:03 PDT 1994 By kalsow     *)
(*      Modified On Thu May 20 08:46:32 PDT 1993 By muller     *)

MODULE TFloat;

IMPORT Convert, TargetMap, Word, M3jFloat;
FROM Target IMPORT Float, Precision;

PROCEDURE New (READONLY chars: ARRAY OF CHAR;  pre: Precision;
               VAR f: Float): BOOLEAN =
  VAR used: INTEGER;
  BEGIN
    f.pre      := pre;
    f.exponent := 0;
    TRY
      IF pre = Precision.Short THEN
        f.fraction := FLOAT(Convert.ToFloat (chars, used), EXTENDED);
      ELSIF pre = Precision.Long THEN
        f.fraction := FLOAT(Convert.ToLongFloat (chars, used), EXTENDED);
      ELSE
        f.fraction := Convert.ToExtended (chars, used);
      END;
    EXCEPT Convert.Failed => RETURN FALSE;
    END;
    RETURN (used = NUMBER (chars)) AND Normalize (f);
  END New;

PROCEDURE Prec (READONLY f: Float): Precision =
  BEGIN
    RETURN f.pre;
  END Prec;

PROCEDURE Add (READONLY a, b: Float;  VAR f: Float): BOOLEAN =
  BEGIN
    IF (a.pre # b.pre) THEN RETURN FALSE END;
    f.pre      := a.pre;
    f.exponent := 0;
    f.fraction := a.fraction + b.fraction;
    RETURN Normalize (f);
  END Add;

PROCEDURE Subtract (READONLY a, b: Float;  VAR f: Float): BOOLEAN =
  BEGIN
    IF (a.pre # b.pre) THEN RETURN FALSE END;
    f.pre      := a.pre;
    f.exponent := 0;
    f.fraction := a.fraction - b.fraction;
    RETURN Normalize (f);
  END Subtract;

PROCEDURE Multiply (READONLY a, b: Float;  VAR f: Float): BOOLEAN =
  BEGIN
    IF (a.pre # b.pre) THEN RETURN FALSE END;
    f.pre      := a.pre;
    f.exponent := 0;
    f.fraction := a.fraction * b.fraction;
    RETURN Normalize (f);
  END Multiply;

PROCEDURE Divide (READONLY a, b: Float;  VAR f: Float): BOOLEAN =
  BEGIN
    IF (a.pre # b.pre) THEN RETURN FALSE END;
    IF (b.fraction = 0.0x+0) THEN RETURN FALSE END;
    f.pre      := a.pre;
    f.exponent := 0;
    f.fraction := a.fraction / b.fraction;
    RETURN Normalize (f);
  END Divide;

PROCEDURE Mod (READONLY a, b: Float;  VAR f: Float): BOOLEAN =
  VAR x, y: EXTENDED;
  BEGIN
    IF (a.pre # b.pre) THEN RETURN FALSE END;
    x := a.fraction;
    y := b.fraction;
    IF (y = 0.0x+0) THEN RETURN FALSE END;
    f.pre      := a.pre;
    f.exponent := 0;
    f.fraction := x - y * FLOAT (FLOOR (x / y), EXTENDED);
    RETURN Normalize (f);
  END Mod;

PROCEDURE EQ (READONLY a, b: Float): BOOLEAN =
  BEGIN
    IF (a.pre # b.pre) THEN RETURN FALSE END;
    IF (a.exponent # b.exponent) THEN RETURN FALSE END;
    IF (a.fraction # b.fraction) THEN RETURN FALSE END;
    RETURN TRUE;
  END EQ;

PROCEDURE LT (READONLY a, b: Float): BOOLEAN =
  BEGIN
    IF (a.pre # b.pre) THEN RETURN FALSE END;
    RETURN (a.fraction < b.fraction);
  END LT;

PROCEDURE LE (READONLY a, b: Float): BOOLEAN =
  BEGIN
    IF (a.pre # b.pre) THEN RETURN FALSE END;
    RETURN (a.fraction <= b.fraction);
  END LE;

PROCEDURE FloatF (READONLY a: Float;  p: Precision;  VAR f: Float): BOOLEAN =
  BEGIN
    f.pre      := p;
    f.exponent := a.exponent;
    f.fraction := a.fraction;
    RETURN Normalize (f);
  END FloatF;

PROCEDURE FloatI (READONLY i: INTEGER;  p: Precision;  VAR f: Float): BOOLEAN =
  BEGIN
    f.pre      := p;
    f.exponent := 0;
    f.fraction := FLOAT (i, EXTENDED);
    RETURN Normalize (f);
  END FloatI;

PROCEDURE Trunc (READONLY a: Float): INTEGER =
  BEGIN
    CASE a.pre OF
    | Precision.Short    => RETURN TRUNC (ToReal (a));
    | Precision.Long     => RETURN TRUNC (ToLongreal (a));
    | Precision.Extended => RETURN TRUNC (ToExtended (a));
    END;
  END Trunc;

PROCEDURE Round (READONLY a: Float): INTEGER =
  BEGIN
    CASE a.pre OF
    | Precision.Short    => RETURN ROUND (ToReal (a));
    | Precision.Long     => RETURN ROUND (ToLongreal (a));
    | Precision.Extended => RETURN ROUND (ToExtended (a));
    END;
  END Round;

PROCEDURE Floor (READONLY a: Float): INTEGER =
  BEGIN
    CASE a.pre OF
    | Precision.Short    => RETURN FLOOR (ToReal (a));
    | Precision.Long     => RETURN FLOOR (ToLongreal (a));
    | Precision.Extended => RETURN FLOOR (ToExtended (a));
    END;
  END Floor;

PROCEDURE Ceiling (READONLY a: Float): INTEGER =
  BEGIN
    CASE a.pre OF
    | Precision.Short    => RETURN CEILING (ToReal (a));
    | Precision.Long     => RETURN CEILING (ToLongreal (a));
    | Precision.Extended => RETURN CEILING (ToExtended (a));
    END;
  END Ceiling;

PROCEDURE ToChars (READONLY f: Float;  VAR buf: ARRAY OF CHAR): INTEGER =
  <*FATAL Convert.Failed *>
  VAR zz: ARRAY [0..45] OF CHAR;  len: INTEGER;
  BEGIN
    <*ASSERT f.exponent = 0*>
    IF f.pre = Precision.Short THEN
      len := Convert.FromFloat (zz, FLOAT(f.fraction, REAL), 13, Convert.Style.Sci);
    ELSIF f.pre = Precision.Long THEN
      len := Convert.FromLongFloat (zz, FLOAT(f.fraction, LONGREAL), 17, Convert.Style.Sci);
    ELSE
      len := Convert.FromExtended (zz, f.fraction, 36, Convert.Style.Sci);
    END;
    IF (len > NUMBER (buf)) THEN RETURN -1 END;
    SUBARRAY (buf, 0, len) := SUBARRAY (zz, 0, len);
    RETURN len;
  END ToChars;

PROCEDURE ToBytes (READONLY f: Float;  VAR buf: ARRAY OF Byte): INTEGER =
  VAR
    bits : INTEGER;
    len := TargetMap.Float_types[f.pre].size DIV BITSIZE (Byte);
  BEGIN
    IF (NUMBER (buf) < len) THEN RETURN -1 END;
    IF (f.pre = Precision.Short)
      THEN bits := M3jFloat.RealBits (ToReal (f));
      ELSE bits := M3jFloat.LongBits (FLOAT (ToExtended (f), LONGREAL));
    END;
    FOR i := 0 TO len - 1 DO
      buf[i] := Word.Extract (bits, 8 * i, 8);
    END;
    RETURN len;
  END ToBytes;

PROCEDURE FromBytes (READONLY buf: ARRAY OF Byte;  p: Precision;
                    VAR f: Float) =
  VAR
    len  := NUMBER (buf);
    bits := 0;
  BEGIN
    len := TargetMap.Float_types[p].size DIV BITSIZE (Byte);
    <*ASSERT len <= NUMBER (buf) *>

    f.pre      := p;
    f.exponent := 0;

    FOR i := len - 1 TO 0 BY -1 DO
      bits := Word.Or (Word.LeftShift (bits, 8), buf[i]);
    END;
    CASE p OF
    | Precision.Short =>
        f.fraction := FLOAT (M3jFloat.RealFromBits (bits), EXTENDED);
    | Precision.Long, Precision.Extended =>
        f.fraction := FLOAT (M3jFloat.LongFromBits (bits), EXTENDED);
    END;
  END FromBytes;

(*-------------------------------------------------------------- internal ---*)

PROCEDURE Normalize (VAR f: Float): BOOLEAN =
  BEGIN
    RETURN (f.exponent = 0);
  END Normalize;

PROCEDURE ToReal (READONLY f: Float): REAL =
  BEGIN
    RETURN FLOAT (f.fraction, REAL);
  END ToReal;

PROCEDURE ToLongreal (READONLY f: Float): LONGREAL =
  BEGIN
    RETURN FLOAT (f.fraction, LONGREAL);
  END ToLongreal;

PROCEDURE ToExtended (READONLY f: Float): EXTENDED =
  BEGIN
    RETURN f.fraction;
  END ToExtended;

BEGIN
END TFloat.
