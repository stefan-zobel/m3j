(* Copyright 1996-1998 John D. Polstra.
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
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgment:
 *      This product includes software developed by John D. Polstra.
 * 4. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
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
 *
 * $Id: Glob.m3,v 1.1 2001-12-01 16:07:31 wagner Exp $ *)

(* fnmatch, from FreeBSD (usr.bin/csup/fnmatch.c, revision 216370):
 *
 * Copyright (c) 1989, 1993, 1994
 *	The Regents of the University of California.  All rights reserved.
 *
 * This code is derived from software contributed to Berkeley by
 * Guido van Rossum.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 4. Neither the name of the University nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j: a safe module. cm3 calls the function fnmatch of C
   (from FreeBSD, in libglob of the package); here it is written in
   Modula-3, with the same flags and the same results. As in C, a text
   ends at its first null character, and CaseFold folds only the
   letters of ASCII (tolower in the locale "C"). *)

MODULE Glob;

IMPORT RegEx, Text;

CONST EOS = W'\000000';

TYPE
  Str = RECORD
    t  : TEXT;
    len: CARDINAL;              (* up to the first null character *)
  END;

PROCEDURE Match(pattern, string: TEXT;
                options := MatchOptions{}): BOOLEAN RAISES {RegEx.Error} =
  BEGIN
    IF MatchOption.UseSimpleRE IN options THEN
      VAR
        rePattern : RegEx.Pattern;
      BEGIN
        rePattern := RegEx.Compile(pattern);
        RETURN RegEx.Execute(rePattern, string) > -1;
      END;
    END;
    RETURN FnMatch(CString(pattern), CString(string), 0, 0, options);
  END Match;

PROCEDURE CString (t: TEXT): Str =
  VAR n := Text.Length(t);
  BEGIN
    FOR i := 0 TO n - 1 DO
      IF Text.GetWideChar(t, i) = EOS THEN n := i; EXIT; END;
    END;
    RETURN Str{t, n};
  END CString;

PROCEDURE At (READONLY x: Str; i: CARDINAL): WIDECHAR =
  BEGIN
    IF i < x.len THEN RETURN Text.GetWideChar(x.t, i); END;
    RETURN EOS;
  END At;

PROCEDURE Lower (c: WIDECHAR): WIDECHAR =
  BEGIN
    IF W'A' <= c AND c <= W'Z' THEN
      RETURN VAL(ORD(c) - ORD(W'A') + ORD(W'a'), WIDECHAR);
    END;
    RETURN c;
  END Lower;

PROCEDURE LeadingPeriod (READONLY str: Str; s, start: CARDINAL;
                         options: MatchOptions): BOOLEAN =
  (* A period at s that only a period in the pattern may match. *)
  BEGIN
    RETURN At(str, s) = W'.' AND MatchOption.Period IN options
             AND (s = start OR MatchOption.Pathname IN options
                                 AND At(str, s - 1) = W'/');
  END LeadingPeriod;

PROCEDURE HasSlash (READONLY str: Str; s: CARDINAL): BOOLEAN =
  BEGIN
    RETURN FindSlash(str, s) >= 0;
  END HasSlash;

PROCEDURE FindSlash (READONLY str: Str; s: CARDINAL): INTEGER =
  BEGIN
    WHILE s < str.len DO
      IF Text.GetWideChar(str.t, s) = W'/' THEN RETURN s; END;
      INC(s);
    END;
    RETURN -1;
  END FindSlash;

PROCEDURE FnMatch (READONLY pat, str: Str; p, s: CARDINAL;
                   options: MatchOptions): BOOLEAN =
  (* Whether str from s matches pat from p (fnmatch returns 0). *)
  VAR
    start          := s;
    c, test        : WIDECHAR;
    pathname       := MatchOption.Pathname IN options;
    escape         := NOT MatchOption.NoEscape IN options;
    slash          : INTEGER;
    next           : INTEGER;
  BEGIN
    LOOP
      c := At(pat, p);
      INC(p);
      IF c = EOS THEN
        IF MatchOption.LeadingDir IN options AND At(str, s) = W'/' THEN
          RETURN TRUE;
        END;
        RETURN At(str, s) = EOS;
      ELSIF c = W'?' THEN
        IF At(str, s) = EOS THEN RETURN FALSE; END;
        IF At(str, s) = W'/' AND pathname THEN RETURN FALSE; END;
        IF LeadingPeriod(str, s, start, options) THEN RETURN FALSE; END;
        INC(s);
      ELSIF c = W'*' THEN
        c := At(pat, p);
        (* Collapse multiple stars. *)
        WHILE c = W'*' DO INC(p); c := At(pat, p); END;
        IF LeadingPeriod(str, s, start, options) THEN RETURN FALSE; END;
        (* Optimize for pattern with * at end or before /. *)
        IF c = EOS THEN
          IF pathname THEN
            RETURN MatchOption.LeadingDir IN options
                     OR NOT HasSlash(str, s);
          END;
          RETURN TRUE;
        ELSIF c = W'/' AND pathname THEN
          slash := FindSlash(str, s);
          IF slash < 0 THEN RETURN FALSE; END;
          s := slash;
        ELSE
          (* General case, use recursion. *)
          LOOP
            test := At(str, s);
            IF test = EOS THEN EXIT; END;
            IF FnMatch(pat, str, p, s,
                       options - MatchOptions{MatchOption.Period}) THEN
              RETURN TRUE;
            END;
            IF test = W'/' AND pathname THEN EXIT; END;
            INC(s);
          END;
          RETURN FALSE;
        END;
      ELSIF c = W'[' THEN
        IF At(str, s) = EOS THEN RETURN FALSE; END;
        IF At(str, s) = W'/' AND pathname THEN RETURN FALSE; END;
        next := RangeMatch(pat, p, At(str, s), options);
        IF next < 0 THEN RETURN FALSE; END;
        p := next;
        INC(s);
      ELSE
        IF c = W'\\' AND escape THEN
          c := At(pat, p);
          INC(p);
          IF c = EOS THEN c := W'\\'; DEC(p); END;
        END;
        test := At(str, s);
        IF c = test THEN
        ELSIF MatchOption.CaseFold IN options AND Lower(c) = Lower(test) THEN
        ELSIF MatchOption.PrefixDirs IN options AND test = EOS
                AND (c = W'/' AND s # start
                       OR s = start + 1 AND At(str, start) = W'/') THEN
          RETURN TRUE;
        ELSE
          RETURN FALSE;
        END;
        INC(s);
      END;
    END;
  END FnMatch;

PROCEDURE RangeMatch (READONLY pat: Str; p: CARDINAL; test: WIDECHAR;
                      options: MatchOptions): INTEGER =
  (* Whether the bracket expression at p (after the "[") takes test: the
     position after its "]" if so, else -1 (also for a bad expression). A
     leading "^" is taken as "!", for consistency with the regular
     expressions. *)
  VAR
    negate, ok := FALSE;
    c, c2      : WIDECHAR;
    escape     := NOT MatchOption.NoEscape IN options;
    fold       := MatchOption.CaseFold IN options;
  BEGIN
    c := At(pat, p);
    IF c = W'!' OR c = W'^' THEN negate := TRUE; INC(p); END;
    IF fold THEN test := Lower(test); END;
    LOOP
      c := At(pat, p);
      INC(p);
      IF c = W']' THEN EXIT; END;
      IF c = W'\\' AND escape THEN c := At(pat, p); INC(p); END;
      IF c = EOS THEN RETURN -1; END;
      IF fold THEN c := Lower(c); END;
      IF At(pat, p) = W'-' AND At(pat, p + 1) # EOS
           AND At(pat, p + 1) # W']' THEN
        c2 := At(pat, p + 1);
        INC(p, 2);
        IF c2 = W'\\' AND escape THEN c2 := At(pat, p); INC(p); END;
        IF c2 = EOS THEN RETURN -1; END;
        IF fold THEN c2 := Lower(c2); END;
        IF c <= test AND test <= c2 THEN ok := TRUE; END;
      ELSIF c = test THEN
        ok := TRUE;
      END;
    END;
    IF ok = negate THEN RETURN -1; END;
    RETURN p;
  END RangeMatch;

BEGIN
END Glob.
