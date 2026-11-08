(* The module WireRep of m3j. In cm3 it is UNSAFE: it views a "T" as a
   record of the time, the number of the object (32-bit integers, in the
   byte order of the machine) and the "SpaceID.T", with LOOPHOLE. Here the
   bytes of a "T" are set and read one by one, in the order of cm3 on
   AMD64_NT (little endian). *)

(* Copyright (C) 1992, Digital Equipment Corporation.          *)
(* Distributed only by permission.                             *)
(* WireRep.m3 *)
(* Last modified on Thu Apr 13 13:47:41 PDT 1995 by kalsow *)
(*      modified on Fri Feb 11 14:38:21 PST 1994 by wobber *)
(*      modified on Wed Jun 10 17:14:36 PDT 1992 by owicki *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

MODULE WireRep;

IMPORT NetObjEpoch, SpaceID, Time, Word;

TYPE
  Int32 = BITS 32 FOR [ -16_7fffffff-1 .. 16_7fffffff ];

CONST
  TsOffset = 0;      (* the bytes of the time *)
  NumOffset = 4;     (* the bytes of the number of the object *)
  SpaceOffset = 8;   (* the bytes of the SpaceID.T *)

VAR myTs: Int32 := GetTime();
    myObjNum := 0;
    mu := NEW(MUTEX);

PROCEDURE GetTime (): Int32 =
  BEGIN
    RETURN ROUND (Time.Now () - NetObjEpoch.T);
  END GetTime;

PROCEDURE PutInt32 (VAR t: T; at: CARDINAL; i: Int32) =
  VAR w: Word.T := i;
  BEGIN
    FOR k := 0 TO 3 DO
      t.byte[at + k] := Word.Extract(w, 8 * k, 8);
    END;
  END PutInt32;

PROCEDURE GetInt32 (READONLY t: T; at: CARDINAL): Int32 =
  VAR w: Word.T := 0;
  BEGIN
    FOR k := 3 TO 0 BY -1 DO
      w := Word.Or(Word.LeftShift(w, 8), t.byte[at + k]);
    END;
    IF Word.And(w, 16_80000000) # 0 THEN
      (* sign extension *)
      w := Word.Or(w, Word.Not(16_FFFFFFFF));
    END;
    RETURN w;
  END GetInt32;

PROCEDURE New() : T =
  VAR wt: T;
      space: SpaceID.T;
  BEGIN
    LOCK mu DO
      IF myObjNum = LAST(Int32) THEN myTs := GetTime(); END;
      INC(myObjNum);
      PutInt32(wt, TsOffset, myTs);
      PutInt32(wt, NumOffset, myObjNum);
    END;
    space := SpaceID.Mine();
    FOR k := 0 TO LAST(space.byte) DO
      wt.byte[SpaceOffset + k] := space.byte[k];
    END;
    RETURN wt;
  END New;

PROCEDURE Equal(t1, t2: T) : BOOLEAN =
  BEGIN
    RETURN (t1 = t2);
  END Equal;

PROCEDURE Hash(t: T) : Word.T =
  BEGIN
    RETURN Word.Xor(GetInt32(t, NumOffset), GetInt32(t, TsOffset));
  END Hash;

PROCEDURE GetSpaceID(t: T) : SpaceID.T =
  VAR space: SpaceID.T;
  BEGIN
    FOR k := 0 TO LAST(space.byte) DO
      space.byte[k] := t.byte[SpaceOffset + k];
    END;
    RETURN space;
  END GetSpaceID;

BEGIN
END WireRep.
