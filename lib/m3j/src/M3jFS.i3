(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The file system of the Java platform, for the module FS of m3j: the
   procedures are methods of the class m3j.rt.Externals. A result or VAR
   parameter "err" is NIL on success, else the message of the error.
   The handles 0, 1 and 2 are the standard input, output and error
   output. *)

INTERFACE M3jFS;

IMPORT File;

<*EXTERNAL "M3jFS__Status"*>
PROCEDURE Status (p: TEXT; VAR type: INTEGER; VAR time: LONGREAL; VAR size: LONGINT): TEXT;
(* type: 0 a regular file, 1 a directory, 2 something else. *)

<*EXTERNAL "M3jFS__SetModificationTime"*>
PROCEDURE SetModificationTime (p: TEXT; t: LONGREAL): TEXT;

<*EXTERNAL "M3jFS__Absolute"*>
PROCEDURE Absolute (p: TEXT; VAR err: TEXT): TEXT;

<*EXTERNAL "M3jFS__CreateDirectory"*>
PROCEDURE CreateDirectory (p: TEXT): TEXT;

<*EXTERNAL "M3jFS__DeleteDirectory"*>
PROCEDURE DeleteDirectory (p: TEXT): TEXT;

<*EXTERNAL "M3jFS__DeleteFile"*>
PROCEDURE DeleteFile (p: TEXT): TEXT;

<*EXTERNAL "M3jFS__Rename"*>
PROCEDURE Rename (p0, p1: TEXT): TEXT;

<*EXTERNAL "M3jFS__Entries"*>
PROCEDURE Entries (p: TEXT; VAR err: TEXT): TEXT;
(* The names of the entries of directory p, each followed by '\000'. *)

<*EXTERNAL "M3jFS__Open"*>
PROCEDURE Open (p: TEXT; readonly, truncate: BOOLEAN; create: INTEGER; VAR err: TEXT): INTEGER;
(* A handle of the file p; create: 0 never, 1 if it does not exist, 2 always (it must not exist). *)

<*EXTERNAL "M3jFS__Read"*>
PROCEDURE Read (h: INTEGER; VAR b: ARRAY OF File.Byte; mayBlock: BOOLEAN; VAR err: TEXT): INTEGER;
(* The number of bytes read (0 at the end, -1 if none is ready and not mayBlock). *)

<*EXTERNAL "M3jFS__Write"*>
PROCEDURE Write (h: INTEGER; READONLY b: ARRAY OF File.Byte): TEXT;

<*EXTERNAL "M3jFS__ReadChars"*>
PROCEDURE ReadChars (h: INTEGER; VAR b: ARRAY OF CHAR; mayBlock: BOOLEAN; VAR err: TEXT): INTEGER;
(* As Read, into characters (one per byte). *)

<*EXTERNAL "M3jFS__WriteChars"*>
PROCEDURE WriteChars (h: INTEGER; READONLY b: ARRAY OF CHAR): TEXT;
(* As Write, from characters (one byte each). *)

<*EXTERNAL "M3jFS__IsTerminal"*>
PROCEDURE IsTerminal (): BOOLEAN;
(* Whether the program runs in a terminal. *)

<*EXTERNAL "M3jFS__Seek"*>
PROCEDURE Seek (h: INTEGER; origin, offset: INTEGER; VAR err: TEXT): INTEGER;

<*EXTERNAL "M3jFS__HandleStatus"*>
PROCEDURE HandleStatus (h: INTEGER; VAR time: LONGREAL; VAR size: LONGINT): TEXT;

<*EXTERNAL "M3jFS__Readable"*>
PROCEDURE Readable (h: INTEGER): BOOLEAN;
(* Whether handle h may be read (not the standard output and error output). *)

<*EXTERNAL "M3jFS__Writable"*>
PROCEDURE Writable (h: INTEGER): BOOLEAN;
(* Whether handle h may be written (not the standard input, not a file opened read-only). *)

<*EXTERNAL "M3jFS__Close"*>
PROCEDURE Close (h: INTEGER): TEXT;

<*EXTERNAL "M3jFS__Pipe"*>
PROCEDURE Pipe (VAR hr, hw: INTEGER): TEXT;
(* A new pipe: its read end hr and write end hw. *)

<*EXTERNAL "M3jFS__Access"*>
PROCEDURE Access (p: TEXT; mode: INTEGER): BOOLEAN;
(* Whether file p exists (mode 0) and the program may read it (mode 1),
   write it (mode 2) or execute it (mode 3), as access of POSIX tells. *)

END M3jFS.
