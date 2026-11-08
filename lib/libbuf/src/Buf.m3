(* Copyright (C) 1994, Digital Equipment Corporation           *)
(* All rights reserved.                                        *)
(* See the file COPYRIGHT for a full description.              *)
(*                                                             *)
(* Last modified on Wed Jun 14 08:20:59 PDT 1995 by kalsow     *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j from the UNSAFE module of cm3: FromFile reads the file
   into an array of bytes and copies them to the characters of the
   buffer, instead of reading into the buffer through a LOOPHOLE of its
   address; it stops at the end of the file if the file is shorter than
   its size said. So the module is safe. *)

MODULE Buf;

IMPORT FS, File, OSError, Text;

CONST
  N = 16_10000; (* the bytes read at once *)

PROCEDURE FromFile (path: TEXT;  src: File.T; pad : CARDINAL): T
  RAISES {OSError.E} =
  VAR f: File.T;  len, next, got: INTEGER;  t: T;
      b: ARRAY [0..N-1] OF File.Byte;
  BEGIN
    IF (src = NIL)
      THEN f := FS.OpenFileReadonly (path);
      ELSE f := src;
    END;
    len := VAL(f.status().size, INTEGER);
    t := NEW (T, len + pad);

    next := 0;
    WHILE (len > 0) DO
      got := f.read (SUBARRAY (b, 0, MIN (len, N)), mayBlock := TRUE);
      IF got <= 0 THEN EXIT; END;
      FOR i := 0 TO got - 1 DO t[next + i] := VAL (b[i], CHAR); END;
      INC (next, got);
      DEC (len, got);
    END;

    IF (src = NIL) THEN f.close (); END;
    RETURN t;
  END FromFile;

PROCEDURE FromText (txt: TEXT): T =
  VAR t := NEW (T, Text.Length (txt));
  BEGIN
    Text.SetChars (t^, txt);
    RETURN t;
  END FromText;

BEGIN
  <*ASSERT BYTESIZE (File.Byte) = BYTESIZE (CHAR) *>
END Buf.
