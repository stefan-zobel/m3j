(* Copyright (C) 1993, Digital Equipment Corporation           *)
(* All rights reserved.                                        *)
(* See the file COPYRIGHT for a full description.              *)

(* Last modified on Mon May 17 22:34:00 PDT 1993 by mjordan    *)

(* Changed for m3j: safe, the hash of a key is that of its identity
   (M3jRef.Hash) instead of its address (LOOPHOLE (k, Word.T)). The
   table is only read with get and written with put, so the order of
   its keys does not show. *)

MODULE FRefRefTbl;

IMPORT RefRefTbl, Refany, Word, M3jRef;

REVEAL
  Default = RefRefTbl.Default BRANDED OBJECT
  OVERRIDES
    keyEqual := KeyEqual;
    keyHash := KeyHash;
  END;

PROCEDURE KeyEqual(<*UNUSED*> t: Default; READONLY k1, k2: Refany.T): BOOLEAN=
  BEGIN
    RETURN k1 = k2;
  END KeyEqual;

PROCEDURE KeyHash(<*UNUSED*> t: Default; READONLY k: Refany.T): Word.T=
  BEGIN
    RETURN M3jRef.Hash(k);
  END KeyHash;

BEGIN
END FRefRefTbl.


    
