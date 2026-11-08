(* Copyright (C) 1992, Digital Equipment Corporation           *)
(* All rights reserved.                                        *)
(* See the file COPYRIGHT for a full description.              *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Last modified on Wed May  5 15:29:31 PDT 1993 by mjordan        *)
(*      modified on Thu Apr 29 17:11:03 1993 by gnelson        *)
(*      modified on Wed Sep 23 12:54:38 PDT 1992 by jdd        *)

(* Changed for m3j: the weak references are those of the Java platform
   (M3jWeak) instead of the ones of cm3's collector (RTWeakRef). A "T"
   holds the number of the weak reference, least significant byte
   first. As in cm3, a thread that is forked when the first weak
   reference with a cleanup procedure is made calls the cleanup
   procedures. *)

MODULE WeakRef;

IMPORT M3jWeak, Thread, Word;

TYPE
  Entry = REF RECORD
    id  : INTEGER;
    p   : CleanUpProc;
    next: Entry;
  END;

CONST Buckets = 256;

VAR
  mu := NEW(MUTEX);
  (* LL = mu: the cleanup procedures of the weak references that have one
     and have not been cleaned up yet, by their number MOD Buckets *)
  cleanups: ARRAY [0 .. Buckets - 1] OF Entry;
  started := FALSE;

PROCEDURE Encode (id: INTEGER): T =
  VAR w: T;
  BEGIN
    FOR i := 0 TO 7 DO w.byte[i] := Word.Extract(id, 8 * i, 8) END;
    RETURN w;
  END Encode;

PROCEDURE Decode (READONLY w: T): INTEGER =
  VAR id := 0;
  BEGIN
    FOR i := 7 TO 0 BY -1 DO id := Word.Or(Word.Shift(id, 8), w.byte[i]) END;
    RETURN id;
  END Decode;

PROCEDURE FromRef (r: REFANY; p: CleanUpProc := NIL): T =
  VAR
    id   : INTEGER;
    start := FALSE;
  BEGIN
    <* ASSERT r # NIL *>
    IF p = NIL THEN
      id := M3jWeak.New(r, FALSE);
    ELSE
      (* the weak reference is in the table before the cleaner can get it *)
      LOCK mu DO
        id := M3jWeak.New(r, TRUE);
        (* only one weak reference with a cleanup procedure per node *)
        <* ASSERT id # 0 *>
        WITH b = cleanups[id MOD Buckets] DO
          b := NEW(Entry, id := id, p := p, next := b);
        END;
        IF NOT started THEN started := TRUE; start := TRUE END;
      END;
      IF start THEN EVAL Thread.Fork(NEW(Thread.Closure, apply := Cleaner)) END;
    END;
    RETURN Encode(id);
  END FromRef;

PROCEDURE ToRef (w: T): REFANY =
  BEGIN
    RETURN M3jWeak.Get(Decode(w));
  END ToRef;

PROCEDURE Cleaner (<*UNUSED*> cl: Thread.Closure): REFANY =
  VAR
    id: INTEGER;
    r : REFANY;
    p : CleanUpProc;
    e, prev: Entry;
  BEGIN
    LOOP
      id := M3jWeak.Next();
      r := M3jWeak.Take(id);
      p := NIL;
      LOCK mu DO
        e := cleanups[id MOD Buckets];
        prev := NIL;
        WHILE e # NIL AND e.id # id DO prev := e; e := e.next END;
        IF e # NIL THEN
          p := e.p;
          IF prev = NIL THEN
            cleanups[id MOD Buckets] := e.next;
          ELSE
            prev.next := e.next;
          END;
        END;
      END;
      IF p # NIL THEN p(Encode(id), r) END;
      r := NIL;
      e := NIL;
    END;
  END Cleaner;

BEGIN
END WeakRef.
