(* Changed for m3j from the UNSAFE module of m3core: the packing of the
   host is that of CM3 on AMD64_NT, whose layout m3j keeps for pickles
   (64-bit words and LONGINT, 64-bit alignment, little-endian, IEEE
   floating point, 16-bit WIDECHAR, small sets), instead of being measured
   with addresses. Encode and Decode are those of CM3. *)

(* Copyright (C) 1994, Digital Equipment Corporation           *)
(* All rights reserved.                                        *)
(* See the file COPYRIGHT for a full description.              *)
(*                                                             *)
(* Last modified on Mon Jun 20 15:34:10 PDT 1994 by kalsow     *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

MODULE RTPacking;

IMPORT Word;

CONST
  Bits = ARRAY [0..4] OF INTEGER { 8, 16, 32, 64, 128 };

CONST
  local = T { word_size     := 64,
              long_size     := 64,
              max_align     := 64,
              struct_align  := 8,
              float         := FloatKind.IEEE,
              little_endian := TRUE,
              lazy_align    := FALSE,
              widechar_size := 16,
              small_sets    := TRUE };

PROCEDURE Local (): T =
  BEGIN
    RETURN local;
  END Local;

PROCEDURE Encode (READONLY t: T): INTEGER =
  VAR n := 0;
  VAR wc: INTEGER;
  BEGIN
    IF t.widechar_size = 32
    THEN wc := BitSize (32)
    ELSE wc := BitSize (8)
    END;
      (* Always BitSize (t.widechar_size) would be the consistent thing to use
         here, but that will cause older versions that don't put WIDECHAR size
         in a T to suffer an assert failure during Decode.  The only WIDECHAR
         sizes are 16 and 32, and Decode interprets 8 to really mean 16,
         so it will correctly decode values written by older Encode.
         So we also encode 16 in the old way too, so older Decode will not
         choke on Ts we write with 16-bit WIDECHAR. *)
    n := Word.Or (Word.Shift (n, 1), ORD (t.small_sets));
    n := Word.Or (Word.Shift (n, 2), wc );
    n := Word.Or (Word.Shift (n, 1), ORD (t.lazy_align));
    n := Word.Or (Word.Shift (n, 2), BitSize (t.long_size));
    n := Word.Or (Word.Shift (n, 2), BitSize (t.word_size));
    n := Word.Or (Word.Shift (n, 2), BitSize (t.max_align));
    n := Word.Or (Word.Shift (n, 2), BitSize (t.struct_align));
    n := Word.Or (Word.Shift (n, 1), ORD (t.little_endian));
    n := Word.Or (Word.Shift (n, 2), ORD (t.float));
    RETURN n;
  END Encode;

PROCEDURE Decode (i: INTEGER): T =
  VAR t: T;
  BEGIN
    t.float       := VAL (Word.And (i, 3), FloatKind); i := Word.Shift (i, -2);
    t.little_endian := VAL (Word.And (i, 1), BOOLEAN); i := Word.Shift (i, -1);
    t.struct_align  := Bits[Word.And (i, 3)];    i := Word.Shift (i, -2);
    t.max_align     := Bits[Word.And (i, 3)];    i := Word.Shift (i, -2);
    t.word_size     := Bits[Word.And (i, 3)];    i := Word.Shift (i, -2);
    t.long_size     := Bits[Word.And (i, 3)];    i := Word.Shift (i, -2);
    t.lazy_align    := VAL (Word.And (i, 1), BOOLEAN); i := Word.Shift (i, -1);
    t.widechar_size := Bits[Word.And (i, 3)];    i := Word.Shift (i, -2);
    IF t.widechar_size = 8
    THEN (* Was written by older RTPacking, without a widechar_size. *)
      t.widechar_size := 16
    END;
    t.small_sets    := VAL (Word.And (i, 1), BOOLEAN); i := Word.Shift (i, -1);
    (* Ignore any remaining bits, in case we get a value from a later version
       that has additional properties. *)
    RETURN t;
  END Decode;

PROCEDURE BitSize (n: CARDINAL): [FIRST (Bits) .. LAST (Bits)]  =
  BEGIN
    FOR i := FIRST (Bits) TO LAST (Bits) DO
      IF (Bits[i] = n) THEN RETURN i; END;
    END;
    <*ASSERT FALSE*>
  END BitSize;

BEGIN
END RTPacking.
