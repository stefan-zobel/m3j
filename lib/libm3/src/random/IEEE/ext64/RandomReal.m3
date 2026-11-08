(* Changed for m3j from the module of libm3, which is UNSAFE: the result
   is not packed into the representation of the number with LOOPHOLE but
   computed from the same exponent and significand with Scalb, which gives
   the same bits. *)

(* Copyright (C) 1990, Digital Equipment Corporation           *)
(* All rights reserved.                                        *)
(* See the file COPYRIGHT for a full description.              *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Last modified on Mon Jan 13 09:26:55 PST 1997 by mcjones    *)
(*      modified on Fri Aug  6 13:40:02 PDT 1993 by kalsow     *)
(*      modified on Tue Jan 30 10:57:23 1990 by muller         *)
(*      modified on Thu Jan 25 22:46:15 PST 1990 by stolfi     *)

MODULE RandomReal;

IMPORT Random, Word, RealFloat, LongFloat;

(* Implements Random.Real and Random.LongReal for IEEE floating-point
   format (both big-endian and little-endian). *)

PROCEDURE Real (r: Random.T): REAL =
  CONST ExponentBias = 127;  (* Exponent bias for REAL *)
        FractionBits = 23;   (* Number of explicit fraction bits *)
        WordSize = 32;       (* Size of INTEGER in bits *)
  VAR frac, exp, significand: INTEGER;
  BEGIN
    (* Generate a random fraction and get its first non-zero word: *)
    exp := ExponentBias - 1;
    frac := r.integer ();
    WHILE (frac = 0) AND (exp >= WordSize) DO
      (* This loop is (almost) never executed: *)
      DEC (exp, WordSize);
      frac := r.integer ();
    END;
 
    (* Normalize: *)
    WHILE (frac > 0) AND (exp > 0) DO
      (* This loop is executed about once on the average. *)
      frac := Word.Shift (frac, 1);
      DEC (exp);
    END;
    IF ((ExponentBias - 1 - exp) MOD WordSize) >  WordSize - FractionBits THEN
      (* Needs more random bits *)
      frac := r.integer ();
    END;

    (* The REAL with sign 0, this exponent and this significand: *)
    significand := Word.Shift (Word.And (frac, 16_7fffffff),
                               -(WordSize - 1 - FractionBits));
    IF exp = 0 THEN
      RETURN RealFloat.Scalb (FLOAT (significand), 1 - ExponentBias - FractionBits);
    END;
    RETURN RealFloat.Scalb (FLOAT (Word.Shift (1, FractionBits) + significand),
                            exp - ExponentBias - FractionBits);
  END Real;

PROCEDURE Longreal (r: Random.T): LONGREAL =
  CONST ExponentBias = 1023; (* Exponent bias for LONGREAL *)
        FractionBits = 20;   (* Number of fraction bits in high half *)
        WordSize = 32;       (* Size of INTEGER in bits *)
  VAR frac, exp, significand: INTEGER;
  BEGIN
    (* Generate a random fraction and get the first non-zero word: *)
    exp := ExponentBias - 1;
    frac := r.integer ();
    WHILE (frac = 0) AND (exp >= WordSize) DO
      (* This loop is (almost) never executed: *)
      DEC (exp, WordSize);
      frac := r.integer ();
    END;
 
    (* Normalize: *)
    WHILE (frac > 0) AND (exp > 0) DO
      (* This loop is executed about once on the average. *)
      frac := Word.Shift (frac, 1);
      DEC (exp);
    END;
    IF ((ExponentBias - 1 - exp) MOD WordSize) >  WordSize - FractionBits THEN
      (* Needs more random bits *)
      frac := r.integer ();
    END;

    (* The LONGREAL with sign 0, this exponent and this significand (its
       high 20 bits from frac, its low 32 bits random): *)
    significand := Word.Or (
      Word.Shift (Word.Shift (Word.And (frac, 16_7fffffff),
                              -(WordSize - 1 - FractionBits)), 32),
      Word.And (r.integer (min := -16_7fffffff-1, max :=16_7fffffff),
                16_ffffffff));
    IF exp = 0 THEN
      RETURN LongFloat.Scalb (FLOAT (significand, LONGREAL),
                              1 - ExponentBias - FractionBits - 32);
    END;
    RETURN LongFloat.Scalb (FLOAT (Word.Shift (1, FractionBits + 32) + significand, LONGREAL),
                            exp - ExponentBias - FractionBits - 32);
  END Longreal;

PROCEDURE Extended (r: Random.T): EXTENDED =
  BEGIN
    RETURN FLOAT (Longreal (r), EXTENDED);
  END Extended;

BEGIN
END RandomReal.
