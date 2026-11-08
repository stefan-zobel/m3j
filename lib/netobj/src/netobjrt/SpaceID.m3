(* The module SpaceID of m3j. In cm3 it is UNSAFE: it converts the time
   stamp to characters with LOOPHOLE. Here the 16 bytes of the time stamp
   are copied one by one, which gives the same characters, and their
   fingerprint is that of the text of them ("Fingerprint.FromChars" with
   "Fingerprint.OfEmpty", which m3j does not provide, gives the same). *)

(* Copyright 1992 Digital Equipment Corporation.               *)
(* Distributed only by permission.                             *)
(* SpaceID.m3 *)
(* Last modified on Mon Jul 19 15:04:37 PDT 1993 by wobber *)
(*      modified on Fri Aug  7 15:00:56 PDT 1992 by evers  *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

MODULE SpaceID;

IMPORT Fingerprint, Text, TimeStamp;

VAR myT: T;

PROCEDURE Mine() : T = BEGIN RETURN myT; END Mine;

PROCEDURE ComputeFP() : T =
  VAR ts := TimeStamp.New();
      chars: ARRAY [0..15] OF CHAR;
  BEGIN
    FOR i := 0 TO 15 DO chars[i] := VAL(ts.r[i], CHAR); END;
    RETURN Fingerprint.FromText(Text.FromChars(chars));
  END ComputeFP;

BEGIN
  myT := ComputeFP();
END SpaceID.
