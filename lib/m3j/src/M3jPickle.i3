(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The parts of Pickle that depend on the representation of values: the
   bytes of a referent as a CM3 program on AMD64_NT keeps them in memory,
   the references in it, and new referents of a typecode. The runtime of
   m3j implements this interface in Java (the class m3.i.M3jPickle), from
   the layouts that the compiler records for every type. *)

INTERFACE M3jPickle;

TYPE
  Image <: ROOT;
  (* The data of a referent: its bytes, as CM3 keeps them after the
     header (and after the method suite of an object), with the slots
     of its references. *)

  RefTable <: ROOT;
  (* A table from references (by identity) to indexes. *)

PROCEDURE ImageOf (r: REFANY): Image;
(* The image of the referent of r, or NIL if it cannot be pickled
   (Problem says why). r # NIL. *)

PROCEDURE Problem (r: REFANY): TEXT;
(* Why the referent of r cannot be pickled. *)

PROCEDURE Size (img: Image): INTEGER;
(* The number of bytes of the data of img. *)

PROCEDURE GetBytes (img: Image; VAR buf: ARRAY OF CHAR);
(* The bytes of the data of img, with zeros in the slots of references.
   NUMBER (buf) >= Size (img). *)

PROCEDURE SetBytes (img: Image; READONLY buf: ARRAY OF CHAR);
(* Set the scalar fields of the referent from the bytes buf of its data
   (the slots of references are not changed). *)

PROCEDURE Slots (img: Image): INTEGER;
(* The number of slots of references in the data of img, in the order of
   their offsets. *)

PROCEDURE SlotOffset (img: Image; i: INTEGER): INTEGER;
(* The offset in bytes of slot i. A slot takes BYTESIZE (ADDRESS) bytes. *)

PROCEDURE SlotIsTraced (img: Image; i: INTEGER): BOOLEAN;
(* TRUE if slot i holds a traced reference; otherwise it holds an
   untraced reference or a procedure, which a pickle leaves out. *)

PROCEDURE GetRef (img: Image; i: INTEGER): REFANY;
(* The traced reference in slot i. *)

PROCEDURE SetRef (img: Image; i: INTEGER; v: REFANY): BOOLEAN;
(* Store v in slot i (NIL for a slot that is not traced); FALSE if the
   type of the slot does not admit v. *)

PROCEDURE Shape (r: REFANY; VAR s: ARRAY OF INTEGER): INTEGER;
(* The number of open dimensions of the referent of r (0 if it is not an
   open array), with their sizes in s. *)

PROCEDURE New (tc: INTEGER; READONLY shape: ARRAY OF INTEGER): REFANY;
(* A new referent of typecode tc (an open array with the given shape), or
   NIL if no such referent can be made. *)

PROCEDURE NewTable (): RefTable;

PROCEDURE Index (t: RefTable; r: REFANY; next: INTEGER): INTEGER;
(* The index of r in t; if r is not in t, enter it with the index next
   and return -1. *)

PROCEDURE Clear (t: RefTable);
(* Remove all entries of t. *)

END M3jPickle.
