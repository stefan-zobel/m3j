(* New for m3j: the module of OSSpecials for both systems, made of
   WIN32/OSSpecialsWin32 and POSIX/OSSpecialsPosix of sysutils, of which a
   build of cm3 takes the one of its target; it follows the system that
   the program runs on (M3jOS). *)

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

(*---------------------------------------------------------------------------*)
MODULE OSSpecials;

IMPORT M3jOS;

VAR posix := M3jOS.Posix();

(*---------------------------------------------------------------------------*)
PROCEDURE QuotedProcessArgument(arg : TEXT) : TEXT =
  BEGIN
    IF posix THEN
      RETURN arg;
    END;
    RETURN "\"" & arg & "\"";
  END QuotedProcessArgument;

(*---------------------------------------------------------------------------*)
BEGIN
  IF posix THEN
    LineBreak := "\n";
  ELSE
    LineBreak := "\r\n";
  END;
END OSSpecials.
