(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The module FS of m3j. In cm3, FS.m3 only sets DirectoryFileType and
   the UNSAFE FSWin32.m3 (FSPosix.m3) calls the operating system. Here the
   file system of the Java platform does the work (M3jFS); the files are
   its handles (M3jFile). An error raises OSError.E with a list of one atom,
   the message of the system. *)

MODULE FS;

IMPORT Atom, File, OSError, Pathname, RegularFile, Text, Time, M3jFS, M3jFile;
FROM M3jFile IMPORT Check;

REVEAL
  Iterator = PublicIterator BRANDED "FS.Iterator (m3j)" OBJECT
    dir: TEXT;
    names: TEXT;
    pos: INTEGER := 0;
  OVERRIDES
    next := Next;
    nextWithStatus := NextWithStatus;
    close := IteratorClose;
  END;

PROCEDURE GetAbsolutePathname (p: Pathname.T): Pathname.T RAISES {OSError.E} =
  VAR err: TEXT;  r := M3jFS.Absolute (p, err);
  BEGIN
    Check (err);
    RETURN r;
  END GetAbsolutePathname;

PROCEDURE OpenFile (
    p: Pathname.T;
    truncate: BOOLEAN := TRUE;
    create: CreateOption := CreateOption.Ok;
    <*UNUSED*> template: File.T := NIL;
    <*UNUSED*> access: AccessOption := AccessOption.Default): File.T
  RAISES {OSError.E} =
  VAR err: TEXT;  h := M3jFS.Open (p, FALSE, truncate, ORD (create), err);
  BEGIN
    Check (err);
    RETURN M3jFile.NewRegular (h);
  END OpenFile;

PROCEDURE OpenFileReadonly (p: Pathname.T): File.T RAISES {OSError.E} =
  VAR err: TEXT;  h := M3jFS.Open (p, TRUE, FALSE, 0, err);
  BEGIN
    Check (err);
    RETURN M3jFile.NewRegular (h);
  END OpenFileReadonly;

PROCEDURE CreateDirectory (p: Pathname.T) RAISES {OSError.E} =
  BEGIN
    Check (M3jFS.CreateDirectory (p));
  END CreateDirectory;

PROCEDURE DeleteDirectory (p: Pathname.T) RAISES {OSError.E} =
  BEGIN
    Check (M3jFS.DeleteDirectory (p));
  END DeleteDirectory;

PROCEDURE DeleteFile (p: Pathname.T) RAISES {OSError.E} =
  BEGIN
    Check (M3jFS.DeleteFile (p));
  END DeleteFile;

PROCEDURE Rename (p0, p1: Pathname.T) RAISES {OSError.E} =
  BEGIN
    Check (M3jFS.Rename (p0, p1));
  END Rename;

PROCEDURE Iterate (p: Pathname.T): Iterator RAISES {OSError.E} =
  VAR err: TEXT;  names := M3jFS.Entries (p, err);
  BEGIN
    Check (err);
    RETURN NEW (Iterator, dir := p, names := names);
  END Iterate;

PROCEDURE Next (it: Iterator; VAR name: TEXT): BOOLEAN =
  VAR e: INTEGER;
  BEGIN
    IF it.names = NIL OR it.pos >= Text.Length (it.names) THEN RETURN FALSE END;
    e := Text.FindChar (it.names, '\000', it.pos);
    name := Text.Sub (it.names, it.pos, e - it.pos);
    it.pos := e + 1;
    RETURN TRUE;
  END Next;

PROCEDURE NextWithStatus (it: Iterator; VAR name: TEXT; VAR stat: File.Status): BOOLEAN
  RAISES {OSError.E} =
  BEGIN
    IF NOT Next (it, name) THEN RETURN FALSE END;
    stat := Status (Pathname.Join (it.dir, name, NIL));
    RETURN TRUE;
  END NextWithStatus;

PROCEDURE IteratorClose (it: Iterator) =
  BEGIN
    it.names := NIL;
  END IteratorClose;

PROCEDURE Status (p: Pathname.T): File.Status RAISES {OSError.E} =
  VAR s: File.Status;  type: INTEGER;  time: LONGREAL;  size: LONGINT;
  BEGIN
    Check (M3jFS.Status (p, type, time, size));
    IF type = 1 THEN s.type := DirectoryFileType ELSE s.type := RegularFile.FileType END;
    s.modificationTime := time;
    s.size := size;
    RETURN s;
  END Status;

PROCEDURE SetModificationTime (p: Pathname.T; READONLY t: Time.T) RAISES {OSError.E} =
  BEGIN
    Check (M3jFS.SetModificationTime (p, t));
  END SetModificationTime;

(* the methods of the file handles *)

BEGIN
  DirectoryFileType := Atom.FromText ("Directory");
END FS.
