(* Copyright (C) 2023, Peter McKinna *)
(* All rights reserved.              *)
(* Licensed under the MIT license.   *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j: ExitCode and Stop. *)

INTERFACE LSP;

PROCEDURE HandleMsg (msg : TEXT) : BOOLEAN;
PROCEDURE CheckFile(uri : TEXT);

PROCEDURE ExitCode () : INTEGER;
(* the exit code of the server: 0 if a shutdown request came, else 1 *)

PROCEDURE Stop ();
(* stops the thread that compiles *)

END LSP.
