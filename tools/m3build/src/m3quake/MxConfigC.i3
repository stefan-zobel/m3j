(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j from the interface of m3quake, whose procedures are
   implemented in C (MxConfigC.c): a safe module of Modula-3 implements
   them (MxConfigC.m3), for the operating system that the program runs
   on, and HOST returns a TEXT. *)

INTERFACE MxConfigC;

PROCEDURE ifdef_win32(): BOOLEAN;
PROCEDURE HOST(): TEXT;

PROCEDURE CaseInsensitive(): BOOLEAN;
PROCEDURE DeviceSeparator(): CHAR;
PROCEDURE DirectorySeparator(): CHAR;

END MxConfigC.
