(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The package patternmatching: each regular expression of the tests of
   RegEx in cm3 (tests.input, testsdiffer.input: PATTERN SUBJECT RESULT)
   compiled, decompiled, dumped and executed, with the positions of its
   \( \) pairs, also on a part of the subject; Glob.Match for a table of
   patterns and names under each combination of its options; and a few
   GlobTree expressions. *)

MODULE Main;

IMPORT FileRd, Fmt, Glob, GlobTree, Lex, OSError, Rd, RegEx, Stdio, Text,
       Thread, Word, Wr;

<*FATAL Thread.Alerted, Wr.Failure, Rd.Failure, OSError.E*>

VAR out := Stdio.stdout;

PROCEDURE Put (t: TEXT) =
  BEGIN
    Wr.PutText(out, t);
  END Put;

PROCEDURE Show (t: TEXT): TEXT =
  (* t in quotes, with the characters outside of the printable ASCII as
     escapes *)
  VAR r := "\"";
  BEGIN
    FOR i := 0 TO Text.Length(t) - 1 DO
      WITH c = Text.GetChar(t, i) DO
        IF c < ' ' OR c > '~' OR c = '\\' OR c = '"' THEN
          r := r & "\\" & Fmt.Pad(Fmt.Int(ORD(c), 8), 3, '0');
        ELSE
          r := r & Text.FromChar(c);
        END;
      END;
    END;
    RETURN r & "\"";
  END Show;

PROCEDURE Field (rd: Rd.T): TEXT RAISES {Rd.EndOfFile} =
  (* A field as the tests of cm3 read it: in quotes, or up to a blank. *)
  VAR c: CHAR; t: TEXT;
  BEGIN
    Lex.Skip(rd, SET OF CHAR{'\t', ' '});
    c := Rd.GetChar(rd);
    IF c = '"' OR c = '\'' THEN
      t := Lex.Scan(rd, SET OF CHAR{FIRST(CHAR) .. LAST(CHAR)}
                          - SET OF CHAR{c});
      EVAL Rd.GetChar(rd);
    ELSE
      Rd.UnGetChar(rd);
      t := Lex.Scan(rd);
    END;
    RETURN t;
  END Field;

PROCEDURE Memory (READONLY m: RegEx.Memory): TEXT =
  VAR r := "";
  BEGIN
    FOR i := FIRST(m) TO LAST(m) DO
      IF m[i].start # -1 OR m[i].stop # -1 THEN
        r := r & " " & Fmt.Int(i) & ":" & Fmt.Int(m[i].start) & "-"
               & Fmt.Int(m[i].stop);
      END;
    END;
    RETURN r;
  END Memory;

PROCEDURE Execute (pat: RegEx.Pattern; data: TEXT; start, len: CARDINAL;
                   name: TEXT) =
  VAR mem := NEW(REF RegEx.Memory); at: INTEGER;
  BEGIN
    at := RegEx.Execute(pat, data, start, len, mem);
    Put("  " & name & " " & Fmt.Int(at));
    IF at # -1 THEN Put(Memory(mem^)); END;
    Put("\n");
  END Execute;

PROCEDURE TestRegEx (pstr, dstr, expect: TEXT) =
  VAR pat: RegEx.Pattern; ok: BOOLEAN;
  BEGIN
    Put(Show(pstr) & " " & Show(dstr) & " " & expect & "\n");
    TRY
      pat := RegEx.Compile(pstr);
    EXCEPT
    | RegEx.Error (e) =>
        Put("  error " & e & " "
              & ARRAY BOOLEAN OF TEXT{"unexpected", "as expected"}[
                  Text.Equal(expect, "ERROR")] & "\n");
        RETURN;
    END;
    Put("  decompiled " & Show(RegEx.Decompile(pat)) & "\n");
    Put("  dump " & Show(RegEx.Dump(pat)) & "\n");
    ok := RegEx.Execute(pat, dstr) # -1;
    Put("  " & ARRAY BOOLEAN OF TEXT{"FALSE", "TRUE"}[ok] & " "
          & ARRAY BOOLEAN OF TEXT{"unexpected", "as expected"}[
              Text.Equal(expect, Fmt.Bool(ok))] & "\n");
    Execute(pat, dstr, 0, LAST(CARDINAL), "all");
    Execute(pat, dstr, 1, LAST(CARDINAL) - 1, "from 1");
    Execute(pat, dstr, 0, 2, "first 2");
    Execute(pat, dstr, 1, 2, "2 from 1");
    Execute(pat, dstr, Text.Length(dstr), 0, "at the end, empty");
    Execute(pat, dstr, Text.Length(dstr) + 1, 5, "after the end");
  END TestRegEx;

PROCEDURE ReadTests (file: TEXT) =
  VAR rd := FileRd.Open(file); c: CHAR; pstr, dstr, expect: TEXT;
  BEGIN
    Put("== " & file & "\n");
    TRY
      LOOP
        LOOP
          Lex.Skip(rd);
          c := Rd.GetChar(rd);
          IF c # '#' THEN Rd.UnGetChar(rd); EXIT; END;
          EVAL Rd.GetLine(rd);
        END;
        pstr := Field(rd);
        dstr := Field(rd);
        Lex.Skip(rd, SET OF CHAR{'\t', ' '});
        expect := Lex.Scan(rd);
        TestRegEx(pstr, dstr, expect);
      END;
    EXCEPT
    | Rd.EndOfFile =>
    END;
    Rd.Close(rd);
  END ReadTests;

PROCEDURE MoreRegEx () =
  (* The edges that the files of cm3 do not reach. Not the empty pattern:
     RegEx.Compile reads its variable next_c before it sets it then, so the
     result of cm3 depends on what was on the stack. *)
  CONST
    Cases = ARRAY OF TEXT{
              "x*", "", "x*", "abc", "b*", "abbbc",
              "\\(a*\\)\\(b*\\)c", "aabbc", "\\(a\\(b\\)\\)\\2\\1", "abbab",
              "[a-]", "-", "[-a]", "-", "[]a]", "]", "[^]a]", "b", "a[",
              "a[", "[a", "a", "[c-a]", "b", "\\(", "(", "\\)", ")",
              "\\(\\(\\(\\(\\(\\(\\(\\(\\(a\\)\\)\\)\\)\\)\\)\\)\\)\\)",
              "a", "\\(\\(\\(\\(\\(\\(\\(\\(\\(\\(a\\)\\)\\)\\)\\)\\)\\)\\)\\)\\)",
              "a", "\\1", "a", "\\(a\\)\\2", "aa", "a\\", "a\\", "\\.", ".",
              "\\.", "a", "*a", "*a", "+", "+", "?", "?", "^*", "*", "a**",
              "aa", "a$b", "a$b", "a^b", "a^b", "$$", "$", "^^", "^",
              "\\(^a\\)", "a", "\\(a$\\)", "a", ".*", "\200\377",
              "[\200-\377]+", "a\200\377", "a.c", "a\000c", "\\(x\\)*y",
              "xxxy", "\\(x*\\)\\1y", "xxxxy"};
  BEGIN
    Put("== more\n");
    FOR i := 0 TO LAST(Cases) BY 2 DO
      TestRegEx(Cases[i], Cases[i + 1], "-");
    END;
  END MoreRegEx;

CONST
  GlobPatterns = ARRAY OF TEXT{
                   "", "*", "?", "a*", "*.c", ".*", "*/*", "a/*", "a/*/c",
                   "a/*c", "*/", "[abc]", "[!abc]", "[^a-c]", "[a-]", "[]a]",
                   "[\\]]", "[a-c", "[z-a]", "[a\\-c]", "[A-C]", "[!.]*", "\\*",
                   "a\\", "\\a", "\\", "A*", "*b", "a?c", "a/b", "/a", "a/",
                   "/", "*.", "**", "a*b*c", "a**c", "*c*", "a/b/c", "a/b/",
                   "a\000x", "[a\000]"};
  GlobNames = ARRAY OF TEXT{
                "", "a", "b", "c", "abc", "ABC", "a.c", ".a", "a/b", "a/b/c",
                "a/.b", "a/bc/c", "/a", "a/", "/", "*", "]", "-", "\\", "a\\b",
                "x.c", ".x.c", "aXbYc", "a\000x", "\000"};

PROCEDURE TestGlob () =
  VAR mask: INTEGER; options: Glob.MatchOptions; e := 0;
  BEGIN
    Put("== Glob: for each pattern and name the combinations of NoEscape,\n"
          & "   Pathname, Period, LeadingDir, CaseFold and PrefixDirs (bit 0\n"
          & "   to 5) that match, as bits of a number\n");
    FOR p := FIRST(GlobPatterns) TO LAST(GlobPatterns) DO
      FOR n := FIRST(GlobNames) TO LAST(GlobNames) DO
        mask := 0;
        FOR i := 0 TO 63 DO
          options := Glob.MatchOptions{};
          FOR b := 0 TO 5 DO
            IF i DIV Bit[b] MOD 2 = 1 THEN
              options := options + Glob.MatchOptions{VAL(b, Glob.MatchOption)};
            END;
          END;
          TRY
            IF Glob.Match(GlobPatterns[p], GlobNames[n], options) THEN
              mask := Word.Or(mask, Word.LeftShift(1, i));
            END;
          EXCEPT
          | RegEx.Error => INC(e);
          END;
        END;
        Put(Show(GlobPatterns[p]) & " " & Show(GlobNames[n]) & " "
              & Fmt.Unsigned(mask, 16) & "\n");
      END;
    END;
    Put("errors " & Fmt.Int(e) & "\n");
    Put("== Glob with UseSimpleRE\n");
    (* not the empty pattern, see MoreRegEx *)
    FOR p := FIRST(GlobPatterns) + 1 TO LAST(GlobPatterns) DO
      FOR n := FIRST(GlobNames) TO LAST(GlobNames) DO
        TRY
          Put(Show(GlobPatterns[p]) & " " & Show(GlobNames[n]) & " "
                & Fmt.Bool(Glob.Match(GlobPatterns[p], GlobNames[n],
                                      Glob.MatchOptions{
                                        Glob.MatchOption.UseSimpleRE}))
                & "\n");
        EXCEPT
        | RegEx.Error (m) =>
            Put(Show(GlobPatterns[p]) & " " & Show(GlobNames[n]) & " error "
                  & m & "\n");
        END;
      END;
    END;
  END TestGlob;

CONST Bit = ARRAY [0 .. 5] OF INTEGER{1, 2, 4, 8, 16, 32};

PROCEDURE TestGlobTree () =
  VAR
    c        := GlobTree.Match("*.c");
    h        := GlobTree.Match("*.h");
    dot      := GlobTree.Match(".*", Glob.MatchOptions{Glob.MatchOption.Period});
    upper    := GlobTree.Match("*.C", Glob.MatchOptions{Glob.MatchOption.CaseFold});
    re       := GlobTree.Match("^x[0-9]*\\.", Glob.MatchOptions{
                                                  Glob.MatchOption.UseSimpleRE});
    bad      := GlobTree.Match("[", Glob.MatchOptions{
                                       Glob.MatchOption.UseSimpleRE});
    Trees := ARRAY [0 .. 12] OF GlobTree.T{
               GlobTree.True, GlobTree.False, c, GlobTree.Not(c),
               GlobTree.Or(c, h), GlobTree.And(GlobTree.Or(c, h), GlobTree.Not(dot)),
               GlobTree.And(upper, GlobTree.Not(c)), re, GlobTree.Or(re, dot),
               GlobTree.And(GlobTree.False, bad), GlobTree.Or(GlobTree.True, bad),
               GlobTree.Not(GlobTree.Not(h)), bad};
    Names := ARRAY [0 .. 6] OF TEXT{"a.c", "a.h", ".x.c", "A.C", "x12.c", "x.o", ""};
  BEGIN
    Put("== GlobTree\n");
    FOR t := FIRST(Trees) TO LAST(Trees) DO
      Put(Fmt.Int(t));
      FOR n := FIRST(Names) TO LAST(Names) DO
        TRY
          Put(" " & Fmt.Bool(Trees[t].test(Names[n])));
        EXCEPT
        | RegEx.Error (m) => Put(" error " & m);
        END;
      END;
      Put("\n");
    END;
  END TestGlobTree;

BEGIN
  ReadTests("tests.input");
  ReadTests("testsdiffer.input");
  MoreRegEx();
  TestGlob();
  TestGlobTree();
  Wr.Close(out);
END Main.
