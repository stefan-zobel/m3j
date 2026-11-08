(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* New for m3j: the module of Pathname for both systems. libm3 has a
   module for POSIX pathnames (PathnamePosix) and one for those of Windows
   (PathnameWin32), of which a build of cm3 takes the one of its target;
   m3j has both, changed to export interfaces of their own, and this
   module calls the one of the system that the program runs on (M3jOS). *)

MODULE Pathname;

IMPORT M3jOS, PathnamePosix, PathnameWin32;

VAR posix := M3jOS.Posix ();

PROCEDURE Valid (pn: T): BOOLEAN =
  BEGIN
    IF posix THEN RETURN PathnamePosix.Valid (pn) END;
    RETURN PathnameWin32.Valid (pn);
  END Valid;

PROCEDURE Decompose (pn: T): Arcs RAISES {Invalid} =
  BEGIN
    IF posix THEN RETURN PathnamePosix.Decompose (pn) END;
    RETURN PathnameWin32.Decompose (pn);
  END Decompose;

PROCEDURE Compose (a: Arcs): T RAISES {Invalid} =
  BEGIN
    IF posix THEN RETURN PathnamePosix.Compose (a) END;
    RETURN PathnameWin32.Compose (a);
  END Compose;

PROCEDURE Absolute (pn: T): BOOLEAN =
  BEGIN
    IF posix THEN RETURN PathnamePosix.Absolute (pn) END;
    RETURN PathnameWin32.Absolute (pn);
  END Absolute;

PROCEDURE Prefix (pn: T): T =
  BEGIN
    IF posix THEN RETURN PathnamePosix.Prefix (pn) END;
    RETURN PathnameWin32.Prefix (pn);
  END Prefix;

PROCEDURE Last (pn: T): T =
  BEGIN
    IF posix THEN RETURN PathnamePosix.Last (pn) END;
    RETURN PathnameWin32.Last (pn);
  END Last;

PROCEDURE Base (pn: T): T =
  BEGIN
    IF posix THEN RETURN PathnamePosix.Base (pn) END;
    RETURN PathnameWin32.Base (pn);
  END Base;

PROCEDURE Join (pn, base: T; ext: TEXT := NIL): T =
  BEGIN
    IF posix THEN RETURN PathnamePosix.Join (pn, base, ext) END;
    RETURN PathnameWin32.Join (pn, base, ext);
  END Join;

PROCEDURE LastBase (pn: T): T =
  BEGIN
    IF posix THEN RETURN PathnamePosix.LastBase (pn) END;
    RETURN PathnameWin32.LastBase (pn);
  END LastBase;

PROCEDURE LastExt (pn: T): TEXT =
  BEGIN
    IF posix THEN RETURN PathnamePosix.LastExt (pn) END;
    RETURN PathnameWin32.LastExt (pn);
  END LastExt;

PROCEDURE ReplaceExt (pn: T; ext: TEXT): T =
  BEGIN
    IF posix THEN RETURN PathnamePosix.ReplaceExt (pn, ext) END;
    RETURN PathnameWin32.ReplaceExt (pn, ext);
  END ReplaceExt;

BEGIN
  Parent := "..";
  Current := "."
END Pathname.
