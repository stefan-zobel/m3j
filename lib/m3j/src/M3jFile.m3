(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The files of m3j (see M3jFile.i3). *)

MODULE M3jFile;

IMPORT Atom, AtomList, File, OSError, Pipe, RegularFile, M3jFS;

REVEAL
  File.T = Public BRANDED "File.T (m3j)" OBJECT
  OVERRIDES
    read := FileRead;
    write := FileWrite;
    status := HandleStatus;
    close := Close;
  END;

  RegularFile.T = RegularFile.Public BRANDED "RegularFile.T (m3j)" OBJECT
  OVERRIDES
    seek := Seek;
    flush := Flush;
    lock := Lock;
    unlock := Unlock;
  END;

PROCEDURE Check (err: TEXT) RAISES {OSError.E} =
  BEGIN
    IF err # NIL THEN
      RAISE OSError.E (AtomList.List1 (Atom.FromText (err)));
    END;
  END Check;

(* As in cm3, reading a handle that is not readable or writing one that
   is not writable is a checked runtime error. *)
EXCEPTION IllegalDirection;

PROCEDURE BadDirection () =
  <*FATAL IllegalDirection*>
  BEGIN
    RAISE IllegalDirection;
  END BadDirection;

PROCEDURE NewRegular (h: INTEGER): RegularFile.T =
  BEGIN
    RETURN NEW (RegularFile.T, h := h);
  END NewRegular;

PROCEDURE Standard (h: [0 .. 2]): File.T =
  BEGIN
    RETURN NEW (File.T, h := h);
  END Standard;

PROCEDURE Read (f: File.T; VAR a: ARRAY OF CHAR; mayBlock := TRUE): INTEGER
  RAISES {OSError.E} =
  VAR err: TEXT;  n: INTEGER;
  BEGIN
    IF f.h >= 0 THEN
      IF NOT M3jFS.Readable (f.h) THEN BadDirection () END;
      n := M3jFS.ReadChars (f.h, a, mayBlock, err);
      Check (err);
    ELSE
      VAR b := NEW (REF ARRAY OF File.Byte, NUMBER (a)); BEGIN
        n := f.read (b^, mayBlock);
        FOR i := 0 TO n - 1 DO a[i] := VAL (b[i], CHAR) END;
      END;
    END;
    RETURN n;
  END Read;

PROCEDURE Write (f: File.T; READONLY a: ARRAY OF CHAR) RAISES {OSError.E} =
  BEGIN
    IF f.h >= 0 THEN
      IF NOT M3jFS.Writable (f.h) THEN BadDirection () END;
      Check (M3jFS.WriteChars (f.h, a));
    ELSE
      VAR b := NEW (REF ARRAY OF File.Byte, NUMBER (a)); BEGIN
        FOR i := 0 TO LAST (a) DO b[i] := ORD (a[i]) END;
        f.write (b^);
      END;
    END;
  END Write;

PROCEDURE FileRead (f: File.T; VAR b: ARRAY OF File.Byte; mayBlock: BOOLEAN := TRUE): INTEGER
  RAISES {OSError.E} =
  VAR err: TEXT;  n: INTEGER;
  BEGIN
    IF NOT M3jFS.Readable (f.h) THEN BadDirection () END;
    n := M3jFS.Read (f.h, b, mayBlock, err);
    Check (err);
    RETURN n;
  END FileRead;

PROCEDURE FileWrite (f: File.T; READONLY b: ARRAY OF File.Byte) RAISES {OSError.E} =
  BEGIN
    IF NOT M3jFS.Writable (f.h) THEN BadDirection () END;
    Check (M3jFS.Write (f.h, b));
  END FileWrite;

PROCEDURE HandleStatus (f: File.T): File.Status RAISES {OSError.E} =
  VAR s: File.Status;  time: LONGREAL;  size: LONGINT;
  BEGIN
    Check (M3jFS.HandleStatus (f.h, time, size));
    IF ISTYPE (f, RegularFile.T) THEN
      s.type := RegularFile.FileType;
    ELSIF ISTYPE (f, Pipe.T) THEN
      s.type := Pipe.FileType;
    ELSIF M3jFS.IsTerminal () THEN
      s.type := Atom.FromText ("Terminal");
    ELSE
      s.type := Atom.FromText ("Pipe");
    END;
    s.modificationTime := time;
    s.size := size;
    RETURN s;
  END HandleStatus;

PROCEDURE Close (f: File.T) RAISES {OSError.E} =
  BEGIN
    Check (M3jFS.Close (f.h));
  END Close;

PROCEDURE Seek (f: RegularFile.T; origin: RegularFile.Origin; offset: INTEGER): INTEGER
  RAISES {OSError.E} =
  VAR err: TEXT;  r := M3jFS.Seek (f.h, ORD (origin), offset, err);
  BEGIN
    Check (err);
    RETURN r;
  END Seek;

PROCEDURE Flush (<*UNUSED*> f: RegularFile.T) =
  BEGIN
  END Flush;

PROCEDURE Lock (<*UNUSED*> f: RegularFile.T): BOOLEAN =
  BEGIN
    RETURN TRUE;
  END Lock;

PROCEDURE Unlock (<*UNUSED*> f: RegularFile.T) =
  BEGIN
  END Unlock;

BEGIN
END M3jFile.
