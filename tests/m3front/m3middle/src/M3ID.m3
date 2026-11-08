(* Changed for m3j from the UNSAFE module of m3middle: each id keeps its
   characters, followed by a NUL, in an array of its own instead of in
   shared untraced buffers, and they are compared and measured with loops
   instead of through addresses and Cstring.strcmp and strlen; ToStr is
   left out. The hash values, the numbering of the ids and the order of
   IsLT are those of the original. So the module is safe. *)

(* Copyright (C) 1994, Digital Equipment Corporation           *)
(* All rights reserved.                                        *)
(* See the file COPYRIGHT for a full description.              *)
(*                                                             *)
(* File: M3ID.m3                                               *)
(* Last modified on Fri Aug 26 15:28:04 PDT 1994 by kalsow     *)
(*      modified on Wed Nov 28 02:23:29 1990 by muller         *)

MODULE M3ID;

IMPORT Text, Word;
IMPORT M3Buf, IO;

CONST
  NullChar    = '\000';

TYPE
  StrPtr     = REF ARRAY OF CHAR;  (* the characters and a NUL *)
  DescBuffer = REF ARRAY OF Desc;

TYPE
  Desc = RECORD
    start  : StrPtr  := NIL;
    hash   : INTEGER := 0;
    text   : TEXT    := NIL;
  END;

  Mark = [0..255];

VAR
  next_t    : T := 1;
  ids       := NEW (DescBuffer, 2000);

  hashMask  : INTEGER := 2047; (* == 2^11-1 == 11 bits on *)
  hashTable := NEW (REF ARRAY OF T, 2048);

  classes   : ARRAY [0..200] OF [0..255];

  marks     : REF ARRAY OF Mark := NIL;
  next_mark : Mark := 0;

(*-------------------------------------------------------------- exported ---*)

PROCEDURE Add (x: TEXT;  class: [0..255]): T =
  VAR
    t   : T;
    len := Text.Length (x);
    buf : ARRAY [0..1024] OF CHAR;
  BEGIN
    IF len > NUMBER(buf) THEN
      IO.Put(x);
    END;
    <*ASSERT len <= NUMBER (buf) *>
    Text.SetChars (buf, x);
    t := FromStr (buf, len);
    IF (class # 0) THEN classes [t] := class; END;
    IF (ids[t].text = NIL) THEN ids[t].text := x; END;
    RETURN t;
  END Add;

PROCEDURE FromStr (READONLY buf: ARRAY OF CHAR;  length: INTEGER): T =
  VAR hash, n, p0, p1: INTEGER;  bucket: CARDINAL;  t: T;  chars: StrPtr;
  BEGIN
    length := MIN (length, NUMBER (buf));
    hash := 0;
    FOR i := 0 TO length - 1 DO
      hash := Word.Plus (Word.Times (17, hash), ORD (buf[i]));
    END;

    bucket := Word.And (hash, hashMask);
    LOOP
      t := hashTable[bucket];
      IF (t = NoID) THEN (* empty! *) EXIT; END;
      IF (ids[t].hash = hash) THEN
        p0 := 0;
        chars := ids[t].start;
        p1 := 0;
        n  := length;
        WHILE (n > 0) AND (buf[p0] = chars[p1]) DO
          DEC (n);
          INC (p0);
          INC (p1);
        END;
        IF (n = 0) AND (chars[p1] = NullChar) THEN RETURN t; END;
      END;
      INC (bucket);
      IF (bucket >= NUMBER (hashTable^)) THEN bucket := 0; END;
    END;

    (* we didn't find a match => build a new one *)
    t := next_t;  INC (next_t);
    IF (t >= NUMBER (ids^)) THEN ExpandIDs (); END;
    hashTable[bucket] := t;

    (* initialize the descriptor and stuff the characters *)
    WITH z = ids[t] DO
      z.start := NEW (StrPtr, length + 1);
      z.hash  := hash;
      z.text  := NIL;
      SUBARRAY (z.start^, 0, length) := SUBARRAY (buf, 0, length);
      z.start [length] := NullChar;
    END;

    (* make sure we're not overloading the hash table *)
    IF (2 * next_t > NUMBER (hashTable^)) THEN ExpandHashTable (); END;

    RETURN t;
  END FromStr;

PROCEDURE ToText (t: T): TEXT =
  VAR ptr: StrPtr;  len: INTEGER;  x: TEXT;
  BEGIN
    <*ASSERT t < next_t*>
    IF (t = NoID) THEN RETURN NIL END;
    x := ids[t].text;
    IF (x = NIL) THEN
      ptr := ids[t].start;
      len := StrLen (ptr);
      x   := Text.FromChars (SUBARRAY (ptr^, 0, len));
      ids[t].text := x;
    END;
    RETURN x;
  END ToText;

PROCEDURE Put (wr: M3Buf.T;  t: T) =
  VAR ptr := ids[t].start;  len: INTEGER;
  BEGIN
    <*ASSERT t < next_t*>
    IF (t = NoID) THEN RETURN END;
    len := StrLen (ptr);
    M3Buf.PutSub (wr, SUBARRAY (ptr^, 0, len));
  END Put;

PROCEDURE GetClass (t: T): INTEGER =
  BEGIN
    <*ASSERT t < next_t*>
    IF (t < LAST (classes))
      THEN RETURN classes[t];
      ELSE <*ASSERT t < next_t *>  RETURN 0;
    END;
  END GetClass;

PROCEDURE Hash (t: T): INTEGER =
  BEGIN
    <*ASSERT t < next_t*>
    RETURN ids[t].hash;
  END Hash;

PROCEDURE AdvanceMarks () =
  BEGIN
    IF (marks = NIL) OR (next_t >= NUMBER (marks^)) THEN
      marks := NEW (REF ARRAY OF Mark, NUMBER (ids^));
      next_mark := 0;  (* reset since the allocator returns zeroed storage *)
    END;
    next_mark := Word.And (next_mark + 1, 16_ff);
  END AdvanceMarks;

PROCEDURE SetMark (t: T): BOOLEAN =
  VAR old: Mark;
  BEGIN
    <*ASSERT t < next_t*>
    old := marks[t];
    marks[t] := next_mark;
    RETURN (old = next_mark);
  END SetMark;

PROCEDURE IsLT (a, b: T): BOOLEAN =
  VAR pa, pb: StrPtr;  i := 0;
  BEGIN
    <*ASSERT a < next_t AND b < next_t *>
    pa := ids[a].start;
    pb := ids[b].start;
    (* as strcmp: the characters up to the first NUL *)
    WHILE (pa[i] = pb[i]) AND (pa[i] # NullChar) DO INC (i) END;
    RETURN pa[i] < pb[i];
  END IsLT;

(*-------------------------------------------------------------- internal ---*)

PROCEDURE StrLen (p: StrPtr): INTEGER =
  (* as strlen: the number of characters before the first NUL *)
  VAR len := 0;
  BEGIN
    WHILE (p[len] # NullChar) DO INC (len) END;
    RETURN len;
  END StrLen;

PROCEDURE ExpandIDs () =
  VAR n := NUMBER (ids^);  new := NEW (DescBuffer, n+n);
  BEGIN
    SUBARRAY (new^, 0, n) := ids^;
    ids := new;
  END ExpandIDs;

PROCEDURE ExpandHashTable () =
  VAR
    n_old   := NUMBER (hashTable^);
    n_new   := n_old + n_old;
    new     := NEW (REF ARRAY OF T, n_new);
    newMask := hashMask + hashMask + 1;
    t       : T;
    bucket  : INTEGER;
  BEGIN
    FOR i := 0 TO n_new - 1 DO new[i] := NoID END;

    FOR i := 0 TO n_old - 1 DO
      t := hashTable [i];
      IF (t # NoID) THEN
        bucket := Word.And (ids[t].hash, newMask);
        WHILE (new[bucket] # NoID) DO
          INC (bucket);
          IF (bucket >= n_new) THEN bucket := 0; END;
        END;
        new[bucket] := t;
      END;
    END;

    hashMask := newMask;
    hashTable := new;
  END ExpandHashTable;

BEGIN
END M3ID.
