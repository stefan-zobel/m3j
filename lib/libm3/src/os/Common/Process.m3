(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* New for m3j: the module of Process, in place of cm3's UNSAFE
   ProcessPosix and ProcessWin32. The processes are those of the Java
   platform (M3jProc); the file handles given to them are those of M3jFile.
   The Java platform cannot change the working directory of the process,
   so m3j keeps a working directory of the program, against which the
   file system resolves relative pathnames and in which new processes
   start. An error raises OSError.E with a list of one atom, the message
   of the system. *)

MODULE Process;

IMPORT File, M3jFile, M3jProc, OSError, Pathname, RTProcess;

REVEAL
  T = BRANDED "Process.T (m3j)" REF RECORD
    p, id: INTEGER;
    waitOk := TRUE;
  END;

EXCEPTION WaitAlreadyCalled;
<*FATAL WaitAlreadyCalled*>

VAR stdin_, stdout_, stderr_: File.T;

PROCEDURE Handle (f: File.T): INTEGER RAISES {OSError.E} =
  BEGIN
    IF f = NIL THEN RETURN -1 END;
    IF f.h < 0 THEN M3jFile.Check ("not a file handle of the system") END;
    RETURN f.h;
  END Handle;

PROCEDURE Join (READONLY a: ARRAY OF TEXT): TEXT =
  (* The texts of a, separated by NUL. *)
  VAR r := "";
  BEGIN
    FOR i := 0 TO LAST (a) DO
      IF i > 0 THEN r := r & "\000" END;
      r := r & a[i];
    END;
    RETURN r;
  END Join;

PROCEDURE Create (
    cmd: Pathname.T;
    READONLY params: ARRAY OF TEXT;
    env: REF ARRAY OF TEXT := NIL;
    wd: Pathname.T := NIL;
    stdin, stdout, stderr: File.T := NIL): T
  RAISES {OSError.E} =
  VAR envs: TEXT := NIL;  nenv := 0;  id: INTEGER;  err: TEXT;  p: INTEGER;
  BEGIN
    IF env # NIL THEN envs := Join (env^); nenv := NUMBER (env^) END;
    p := M3jProc.Create (cmd, Join (params), NUMBER (params), envs, nenv, wd,
                         Handle (stdin), Handle (stdout), Handle (stderr),
                         id, err);
    M3jFile.Check (err);
    RETURN NEW (T, p := p, id := id);
  END Create;

PROCEDURE Wait (p: T): ExitCode =
  BEGIN
    IF NOT p.waitOk THEN RAISE WaitAlreadyCalled END;
    p.waitOk := FALSE;
    RETURN M3jProc.Wait (p.p);
  END Wait;

PROCEDURE Exit (n: ExitCode) =
  BEGIN
    RTProcess.Exit (n);
  END Exit;

PROCEDURE Crash (msg: TEXT) =
  BEGIN
    RTProcess.Crash (msg);
  END Crash;

PROCEDURE RegisterExitor (p: PROCEDURE ()) =
  BEGIN
    RTProcess.RegisterExitor (p);
  END RegisterExitor;

PROCEDURE GetID (p: T): ID =
  BEGIN
    RETURN p.id;
  END GetID;

PROCEDURE GetMyID (): ID =
  BEGIN
    RETURN M3jProc.MyID ();
  END GetMyID;

PROCEDURE GetStandardFileHandles (VAR stdin, stdout, stderr: File.T) =
  BEGIN
    stdin := stdin_;
    stdout := stdout_;
    stderr := stderr_;
  END GetStandardFileHandles;

PROCEDURE GetWorkingDirectory (): Pathname.T RAISES {OSError.E} =
  BEGIN
    RETURN M3jProc.GetWorkingDirectory ();
  END GetWorkingDirectory;

PROCEDURE SetWorkingDirectory (path: Pathname.T) RAISES {OSError.E} =
  BEGIN
    M3jFile.Check (M3jProc.SetWorkingDirectory (path));
  END SetWorkingDirectory;

BEGIN
  stdin_ := M3jFile.Standard (0);
  stdout_ := M3jFile.Standard (1);
  stderr_ := M3jFile.Standard (2);
END Process.
