(* Changed for m3j: only the kinds of conversions between packings (see
   ConvertPacking.i3); the converters of the data of referents in memory
   are left out. *)

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

MODULE ConvertPacking;

IMPORT RTPacking;

PROCEDURE GetWordKind(from: RTPacking.T; to: RTPacking.T): CPKind =
(* The result is good for all ordinal types except LONGINT. *)
  BEGIN
    IF from.word_size = to.word_size THEN
      IF from.little_endian = to.little_endian THEN
        RETURN CPKind.Copy;
      ELSE
        RETURN CPKind.Swap;
      END;
    ELSE
      IF from.little_endian = to.little_endian THEN
        IF from.word_size = 32 THEN
          RETURN CPKind.Copy32to64;
        ELSE
          RETURN CPKind.Copy64to32;
        END;
      ELSE
        IF from.word_size = 32 THEN
          RETURN CPKind.Swap32to64;
        ELSE
          RETURN CPKind.Swap64to32;
        END;
      END;
    END;
  END GetWordKind;

PROCEDURE GetLongintKind(from: RTPacking.T; to: RTPacking.T): CPKind =
(* The result is good only for LONGINT. *)
  BEGIN
    IF from.long_size = to.long_size THEN
      IF from.little_endian = to.little_endian THEN
        RETURN CPKind.Copy;
      ELSE
        RETURN CPKind.Swap;
      END;
    ELSE
      IF from.little_endian = to.little_endian THEN
        IF from.long_size = 32 THEN
          RETURN CPKind.Copy32to64;
        ELSE
          RETURN CPKind.Copy64to32;
        END;
      ELSE
        IF from.long_size = 32 THEN
          RETURN CPKind.Swap32to64;
        ELSE
          RETURN CPKind.Swap64to32;
        END;
      END;
    END;
  END GetLongintKind;

PROCEDURE GetWidecharKind(from: RTPacking.T; to: RTPacking.T): CPKind =
(* The result is good only for WIDECHAR. *)
  BEGIN
    IF from.widechar_size = to.widechar_size THEN
      IF from.little_endian = to.little_endian THEN
        RETURN CPKind.Copy;
      ELSE
        RETURN CPKind.Swap;
      END;
    ELSE (* WIDECHAR sizes differ. *)
      IF from.little_endian = to.little_endian THEN
        IF from.widechar_size = 16 THEN
          RETURN CPKind.Copy16to32;
        ELSE
          <* ASSERT from.widechar_size = 32 *>
          RETURN CPKind.Copy32to16;
        END;
      ELSE
        IF from.widechar_size = 16 THEN
          RETURN CPKind.Swap16to32;
        ELSE
          RETURN CPKind.Swap32to16;
        END;
      END;
    END;
  END GetWidecharKind;

BEGIN
END ConvertPacking.
