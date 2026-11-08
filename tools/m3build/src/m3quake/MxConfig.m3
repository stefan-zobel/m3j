(* Copyright 1996-2000 Critical Mass, Inc. All rights reserved.    *)
(* See file COPYRIGHT-CMASS for details. *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j from the UNSAFE module of m3quake: MxConfigC.HOST
   returns a TEXT (MxConfigC is a module of Modula-3 in m3j, not of C), so
   HOST does not convert and free a string of C. So the module is safe.
   The configuration file is m3j.cfg (MxConfig.Filename), found in the
   current directory, the source directories, the directory or file that
   the environment variable M3J_CONFIG names, or else the directory of
   m3j (M3jCompile.Home). *)

MODULE MxConfig;

IMPORT Pathname, M3File, M3ID, Quake, RTIO, Thread;
IMPORT QMachine, MxConfigC, M3jCompile;

VAR
  mu     : MUTEX         := NEW (MUTEX);
  found  : BOOLEAN       := FALSE;
  config : TEXT          := NIL;
  mach   : Quake.Machine := NIL;
  trace  : BOOLEAN       := FALSE;

PROCEDURE FindFile (): TEXT =
  BEGIN
    IF NOT found THEN
      LOCK mu DO FindConfig (); END;
    END;
    RETURN config;
  END FindFile;

PROCEDURE Get (param: TEXT): TEXT =
(* Avoid this function.
 * It uses a global Quake.Machine and does not honor command line defines.
 *)
  BEGIN
    LOCK mu DO
      EvalConfig ();
      TRY
        RETURN Quake.LookUp (mach, param);
      EXCEPT Quake.Error =>
        RETURN NIL;
      END;
    END;
  END Get;

PROCEDURE EnableQuakeTrace() =
  BEGIN
    trace := TRUE;
  END EnableQuakeTrace;

(*----------------------------------------------------------- internal ---*)

PROCEDURE FindConfig () =
  (* LL = mu *)
  VAR txt: TEXT;
  BEGIN
    IF (found) THEN RETURN END;

    (* try the current directory *)
    IF TryConfig (".", Filename) THEN RETURN END;

    (* try the immediate source directory *)
    IF TryConfig ("src", Filename) THEN RETURN END;

    (* try the sibling source directory *)
    IF TryConfig ("..", "src", Filename) THEN RETURN END;

    (* try the M3J_CONFIG environment variable *)
    txt := QMachine.GetEnv (NIL, "M3J_CONFIG");
    IF (txt # NIL) THEN
      IF M3File.IsReadable (txt) AND NOT M3File.IsDirectory (txt) THEN
        IF TryConfig (txt) THEN RETURN END;
      END;
      IF TryConfig (txt, Filename) THEN RETURN END;
    END;

    (* try the directory of m3j *)
    IF TryConfig (M3jCompile.Home (), Filename) THEN RETURN END;

    (* oh well, make sure we don't try this silly exercise again... *)
    config := NIL;
    found := TRUE;
  END FindConfig;

PROCEDURE TryConfig (a, b, c: TEXT := NIL): BOOLEAN =
  BEGIN
    config := a;
    IF (b # NIL) THEN config := Pathname.Join (config, b, NIL); END;
    IF (c # NIL) THEN config := Pathname.Join (config, c, NIL); END;
    found := M3File.IsReadable (config);
    RETURN found;
  END TryConfig;

PROCEDURE EvalConfig () =
(* Avoid this function.
 * It sets a global Quake.Machine and does not honor command line defines.
 *)
  (* LL = mu *)
  BEGIN
    IF (mach # NIL) THEN RETURN END;
    FindConfig ();
    mach := Quake.NewMachine (Quake.NewIDMap (Str2ID, Txt2ID, ID2Txt));
    mach.trace (trace);
    TRY
      IF (config # NIL) THEN Quake.Run (mach, config); END;
    EXCEPT 
      Quake.Error(e) => RTIO.PutText ("quake runtime error: " & e);
                        RTIO.Flush ();
    | Thread.Alerted => RTIO.PutText ("interrupted");
                        RTIO.Flush ();
    END;
  END EvalConfig;

PROCEDURE Str2ID (READONLY x: ARRAY OF CHAR): Quake.ID =
  BEGIN
    RETURN M3ID.FromStr (x);
  END Str2ID;

PROCEDURE Txt2ID (t: TEXT): Quake.ID =
  BEGIN
    RETURN M3ID.Add (t);
  END Txt2ID;

PROCEDURE ID2Txt (i: Quake.ID): TEXT =
  BEGIN
    RETURN M3ID.ToText (i);
  END ID2Txt;

PROCEDURE HOST() : TEXT =
BEGIN
  RETURN MxConfigC.HOST();
END HOST;

PROCEDURE HOST_OS_TYPE() : OS_TYPE =
BEGIN
  IF MxConfigC.ifdef_win32() THEN
    RETURN OS_TYPE.WIN32;
  END;
  RETURN OS_TYPE.POSIX;
END HOST_OS_TYPE;

PROCEDURE HOST_OS_TYPE_TEXT() : TEXT =
BEGIN
  IF MxConfigC.ifdef_win32() THEN
    RETURN "WIN32";
  END;
  RETURN "POSIX";
END HOST_OS_TYPE_TEXT;

BEGIN
END MxConfig.
