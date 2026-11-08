(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The files of m3j: File.T and RegularFile.T are handles of the file
   system of the Java platform (M3jFS). The modules FS, FileRd, FileWr and
   Stdio of m3j make them, and FileRd and FileWr read and write their
   buffers of characters through them directly. *)

INTERFACE M3jFile;

IMPORT File, OSError, RegularFile;

TYPE
  Public = File.Public OBJECT
    h: INTEGER := -1;  (* the handle of M3jFS, -1 for a file made elsewhere *)
  END;

REVEAL
  File.T <: Public;

PROCEDURE NewRegular (h: INTEGER): RegularFile.T;
(* The regular file of handle h. *)

PROCEDURE Standard (h: [0 .. 2]): File.T;
(* The standard input (0), output (1) or error output (2): a File.T that is
   not a RegularFile.T, as a pipe or a terminal of cm3. *)

PROCEDURE Read (f: File.T; VAR a: ARRAY OF CHAR; mayBlock := TRUE): INTEGER
  RAISES {OSError.E};
(* As f.read, into characters: the number of characters read, 0 at the
   end, -1 if none is ready and not mayBlock. *)

PROCEDURE Write (f: File.T; READONLY a: ARRAY OF CHAR) RAISES {OSError.E};
(* As f.write, from characters. *)

PROCEDURE Check (err: TEXT) RAISES {OSError.E};
(* Raises OSError.E with a list of one atom, the message err, unless err is
   NIL. *)

END M3jFile.
