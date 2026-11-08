(* Changed for m3j from the module of libm3: Open makes a pipe of the Java
   platform (M3jFS.Pipe), which cm3 makes in PipePosix and PipeWin32. *)

(* Copyright (C) 1992, Digital Equipment Corporation.       *)
(* All rights reserved.                                     *)
(* See the file COPYRIGHT for a full description.           *)
(* Last modified on Thu Jul 14 13:57:24 PDT 1994 by mcjones *)
(*      modified on Tue Feb  2 18:39:44 PST 1993 by mjordan *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

MODULE Pipe;

IMPORT Atom, File, M3jFile, M3jFS, OSError;

REVEAL T = File.T BRANDED "Pipe.T (m3j)" OBJECT END;

PROCEDURE Open (VAR hr, hw: T) RAISES {OSError.E} =
  VAR r, w: INTEGER;
  BEGIN
    M3jFile.Check (M3jFS.Pipe (r, w));
    hr := NEW (T, h := r);
    hw := NEW (T, h := w);
  END Open;

BEGIN
  FileType := Atom.FromText("Pipe")
END Pipe.
