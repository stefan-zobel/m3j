(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The processes of the Java platform, for the module Process of m3j: the
   procedures are methods of the class m3j.rt.Externals. *)

INTERFACE M3jProc;

<*EXTERNAL "M3jProc__Create"*>
PROCEDURE Create (cmd, params: TEXT; nparams: INTEGER; env: TEXT; nenv: INTEGER;
                  wd: TEXT; stdin, stdout, stderr: INTEGER; VAR id: INTEGER;
                  VAR err: TEXT): INTEGER;
(* A new process (its handle; its identifier in id) that runs cmd with the
   nparams parameters separated by NUL in params, the nenv environment
   variables separated by NUL in env (NIL: those of the program), in the
   working directory wd (NIL: that of the program), and the handles of
   M3jFS stdin, stdout and stderr (-1: none). err is NIL on success, else
   the message of the error. *)

<*EXTERNAL "M3jProc__Wait"*>
PROCEDURE Wait (p: INTEGER): INTEGER;
(* Waits until the process p has ended; its exit code. *)

<*EXTERNAL "M3jProc__MyID"*>
PROCEDURE MyID (): INTEGER;
(* The identifier of the process of the program. *)

<*EXTERNAL "M3jProc__GetWorkingDirectory"*>
PROCEDURE GetWorkingDirectory (): TEXT;
(* The working directory of the program, an absolute pathname. *)

<*EXTERNAL "M3jProc__SetWorkingDirectory"*>
PROCEDURE SetWorkingDirectory (p: TEXT): TEXT;
(* Makes p the working directory of the program; NIL on success, else the
   message of the error. *)

END M3jProc.
