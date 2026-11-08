(* Changed for m3j: only the kinds of conversions between packings, which
   the readers and writers of pickles keep (PickleRd, PickleWr) and
   PickleStubs uses. The converters of CM3 (T, ReadVisitor, WriteVisitor,
   New), which convert the data of a referent in memory, are left out:
   m3j writes and reads the data of referents in the packing of AMD64_NT
   only (see Pickle2.m3). *)

(*
 * For information about this program, contact Blair MacIntyre
 * (bm@cs.columbia.edu) or Steven Feiner (feiner@cs.columbia.edu)
 * at the Computer Science Dept., Columbia University,
 * 1214 Amsterdam Ave. Mailstop 0401, New York, NY, 10027.
 *
 * Copyright (C) 1995, 1996 by The Trustees of Columbia University in the
 * City of New York.  Blair MacIntyre, Computer Science Department.
 *
 *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

INTERFACE ConvertPacking;

IMPORT RTPacking;

EXCEPTION Error(TEXT);

CONST Brand = "ConvertPacking 1.0";

(* These are the data conversions we currently support. *)
TYPE
  CPKind = {Copy, Swap, Copy32to64, Copy64to32, Swap32to64, Swap64to32,
            Copy16to32, Copy32to16, Swap16to32, Swap32to16
           };

PROCEDURE GetWordKind(from: RTPacking.T; local: RTPacking.T): CPKind;
(* The result is good for all ordinal types except LONGINT. *)

PROCEDURE GetLongintKind(from: RTPacking.T; local: RTPacking.T): CPKind;
(* The result is good only for LONGINT. *)

PROCEDURE GetWidecharKind(from: RTPacking.T; local: RTPacking.T): CPKind;
(* The result is good only for WIDECHAR. *)

TYPE UInt32 = BITS 32 FOR [0 .. 16_7FFFFFFF];

END ConvertPacking.
