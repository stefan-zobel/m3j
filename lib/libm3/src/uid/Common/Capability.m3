(* Changed for m3j from the module of libm3: safe, the bytes of the
   random number set one by one instead of with a LOOPHOLE (the same bytes
   as cm3 on AMD64_NT, a little-endian machine with 64-bit integers), and
   Hash computed from them (the same value). *)

(* Copyright (C) 1989, Digital Equipment Corporation           *)
(* All rights reserved.                                        *)
(* See the file COPYRIGHT for a full description.              *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Last modified on Thu Jul 15 15:43:43 PDT 1993 by swart    *)

MODULE Capability;

IMPORT Random, TimeStamp, Thread, Word;

VAR state := NEW(Thread.Mutex OBJECT r: Random.T := NIL; END);

PROCEDURE New (): T =
  VAR t: T;
  BEGIN
    t.ts := TimeStamp.New();
    LOCK state DO
      IF state.r = NIL THEN state.r := NEW(Random.Default).init() END;
      WITH v = state.r.integer() DO
        FOR i := 0 TO 7 DO t.random[i] := Word.Extract (v, 8 * i, 8) END;
      END;
    END;
    RETURN t;
  END New;

PROCEDURE Equal(READONLY t1, t2: T): BOOLEAN =
  BEGIN RETURN t1 = t2; END Equal;

PROCEDURE Hash (READONLY t: T): INTEGER =
  (* The first four bytes, the first one most significant, as a signed
     32-bit integer. *)
  VAR h := 0;
  BEGIN
    FOR i := 0 TO 3 DO h := h * 256 + t.random[i] END;
    IF h >= 16_80000000 THEN DEC (h, 16_100000000) END;
    RETURN h;
  END Hash;

BEGIN
END Capability.
