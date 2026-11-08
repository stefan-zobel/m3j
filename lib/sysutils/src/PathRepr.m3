(* New for m3j: the module of PathRepr for both systems, made of
   PathReprCommon, WIN32/PathReprWin32 and POSIX/PathReprPosix of
   sysutils, of which a build of cm3 takes the one of its target; Native
   and the separator follow the system that the program runs on
   (M3jOS). *)

(* Copyright 1999-2002 elego Software Solutions GmbH, Berlin, Germany.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(*--------------------------------------------------------------------------*)
MODULE PathRepr;

IMPORT Pathname, Process, OSError;
IMPORT TextUtils, M3jOS;

VAR posix := M3jOS.Posix();

(*--------------------------------------------------------------------------*)
PROCEDURE Native(pn : TEXT) : TEXT =
  BEGIN
    IF posix THEN
      RETURN Posix(pn);
    END;
    RETURN Win32(pn);
  END Native;

(*--------------------------------------------------------------------------*)
PROCEDURE Posix(pn : TEXT) : TEXT =
  BEGIN
    RETURN TextUtils.SubstChar(pn, '\\', '/');
  END Posix;

(*--------------------------------------------------------------------------*)
PROCEDURE Win32(pn : TEXT) : TEXT =
  BEGIN
    RETURN TextUtils.SubstChar(pn, '/', '\\');
  END Win32;

(*--------------------------------------------------------------------------*)
PROCEDURE Root(pn : TEXT) : TEXT =
  BEGIN
    TRY
      RETURN Pathname.Decompose(pn).getlo();
    EXCEPT
      Pathname.Invalid => RETURN NIL;
    END;
  END Root;

BEGIN
  IF posix THEN
    PathSepChar := '/';
    PathSep     := "/";
  ELSE
    PathSepChar := '\\';
    PathSep     := "\\";
  END;
  TRY
    RootDir := Root(Process.GetWorkingDirectory());
  EXCEPT
    OSError.E => RootDir := Pathname.Current;
    (* ignore error and let other subsystems fail if they need this *)
  END;
END PathRepr.
