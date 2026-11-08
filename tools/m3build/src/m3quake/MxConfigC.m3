(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The procedures of MxConfigC of m3quake, which cm3 implements in C
   (MxConfigC.c), for m3j: they answer for the operating system that the
   program runs on (M3jOS). The host is "JVM", the target of m3j, where cm3
   returns the name of its target on the machine (such as "AMD64_NT"). *)

MODULE MxConfigC;

IMPORT M3jOS;

PROCEDURE ifdef_win32(): BOOLEAN =
  BEGIN
    RETURN NOT M3jOS.Posix ();
  END ifdef_win32;

PROCEDURE HOST(): TEXT =
  BEGIN
    RETURN "JVM";
  END HOST;

PROCEDURE CaseInsensitive(): BOOLEAN =
  BEGIN
    RETURN NOT M3jOS.Posix ();
  END CaseInsensitive;

PROCEDURE DeviceSeparator(): CHAR =
  BEGIN
    IF M3jOS.Posix () THEN RETURN '\000' ELSE RETURN ':' END;
  END DeviceSeparator;

PROCEDURE DirectorySeparator(): CHAR =
  BEGIN
    (* on Windows, callers accept '/' as well *)
    IF M3jOS.Posix () THEN RETURN '/' ELSE RETURN '\\' END;
  END DirectorySeparator;

BEGIN
END MxConfigC.
