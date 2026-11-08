(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The operating system whose conventions the program follows, decided
   when the program runs: the system that it runs on, or the one that the
   system property m3j.os of the JVM names ("POSIX" or "WIN32"). The
   runtime of m3j implements this interface in Java (the class
   m3.i.M3jOS). *)

INTERFACE M3jOS;

PROCEDURE Posix (): BOOLEAN;
(* TRUE on POSIX systems (Linux, macOS), FALSE on Windows. *)

END M3jOS.
