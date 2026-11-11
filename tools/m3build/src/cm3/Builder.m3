(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* The builder of m3build for m3j, in place of the builder of cm3 (which
   compiles each unit with the front end and a back end of cm3 and links
   the objects): it gives the units of a program or a library to the
   compiler of m3j (M3jCompile), which compiles the whole program from
   the sources of its units and of those of the packages that it imports.

   A program becomes a jar in the build directory, NAME.jar, with two
   scripts that run it with the java of m3build and the runtime of m3j:
   NAME for POSIX shells and NAME.cmd for Windows. The jar is compiled
   again when a source of its units or of the units it imports, or the
   jar of m3j, is newer than the jar, or when the arguments of the
   compiler changed (they are kept in NAME.m3j). A library is not
   compiled into a file: its units are compiled to check them (m3j
   compiles a library again with each program that uses it), and the
   package keeps its sources and its .M3EXPORTS, as with cm3; its UNSAFE
   modules are left out of the check, with a warning (a program that
   needs one of them fails to compile).

   The units of an imported package reach the compiler through the
   directory of its .M3EXPORTS (the build directory JVM of a package of
   m3j, or AMD64_NT of one of cm3), except those of the packages that
   m3j brings (lib), which the compiler has in its jar. With the
   definition M3J_EXPORTS (the option -exports), nothing is compiled.
   The class path of the bindings of Java classes is in M3J_CLASSPATH:
   what the m3makefile of the package and the .M3EXPORTS of the libraries
   that it imports say with java_classpath.
   m3j compiles Modula-3 only: other sources are an error. *)

MODULE Builder;

IMPORT Arg, M3Unit, Quake, QMachine, M3ID, M3Path, Msg, M3Options;
IMPORT Utils, Text, TextSeq, Pathname, Wr, Thread, OSError, FS, M3jCompile;
IMPORT M3jOS, Rd, FileRd;

TYPE
  UK = M3Path.Kind;

PROCEDURE BuildPgm (prog: TEXT;  READONLY units: M3Unit.Set;
                    <*UNUSED*> sys_libs: Arg.List;  <*UNUSED*> shared: BOOLEAN;
                    m: Quake.Machine) =
  BEGIN
    Compile (prog, units, m, program := TRUE);
  END BuildPgm;

PROCEDURE BuildLib (lib: TEXT;  READONLY units: M3Unit.Set;
                    <*UNUSED*> sys_libs: Arg.List;  <*UNUSED*> shared: BOOLEAN;
                    m: Quake.Machine) =
  BEGIN
    Compile (lib, units, m, program := FALSE);
  END BuildLib;

PROCEDURE BuildCPgm (prog: TEXT;  <*UNUSED*> READONLY units: M3Unit.Set;
                     <*UNUSED*> sys_libs: Arg.List;  <*UNUSED*> shared: BOOLEAN;
                     <*UNUSED*> m: Quake.Machine) =
  BEGIN
    Msg.Error (NIL, "C programs are not supported by m3j: c_program (\"",
               prog, "\")");
    M3Options.exit_code := 1;
  END BuildCPgm;

PROCEDURE CleanUp () =
  BEGIN
  END CleanUp;

PROCEDURE EmitPkgImports (READONLY units: M3Unit.Set) =
  VAR src := units.head;
  BEGIN
    WHILE (src # NIL) DO
      IF (src.imported) AND (src.kind = UK.M3LIB) THEN
        WITH name = M3ID.ToText(src.loc.pkg) DO
          Msg.Out (" ", name);
        END;
      END;
      src := src.next;
    END;
    Msg.Out (Wr.EOL);
  END EmitPkgImports;

PROCEDURE SetupNamingConventions (<*UNUSED*> mach : QMachine.T) =
  BEGIN
    (* the names of the files of m3j do not depend on the system *)
    M3Path.SetTargetOS (M3Path.OSKind.Unix);
  END SetupNamingConventions;

PROCEDURE ProgramFiles (prog: TEXT): ARRAY [0..2] OF TEXT =
  BEGIN
    RETURN ARRAY [0..2] OF TEXT {prog & ".jar", prog, prog & ".cmd"};
  END ProgramFiles;

CONST
  ClassPathVar = "M3J_CLASSPATH";
  JavaOptionsVar = "M3J_JAVA_OPTIONS";  (* the options of java_option, a line each *)

(*------------------------------------------------------------ compiling ---*)

PROCEDURE Compile (name: TEXT;  READONLY units: M3Unit.Set;
                   m: Quake.Machine;  program: BOOLEAN) =
  VAR
    args    := NEW (TextSeq.T).init ();  (* the arguments of the compiler *)
    files   := NEW (TextSeq.T).init ();  (* the local sources it compiles *)
    dirs    := NEW (TextSeq.T).init ();  (* the directories of local sources *)
    pkgs    := NEW (TextSeq.T).init ();  (* the directories of imported packages *)
    home    := M3jCompile.Home ();
    lib     := Pathname.Join (home, "lib", NIL);
    newest  := Utils.ModificationTime (Pathname.Join (home, "m3j.jar", NIL));
    u       := units.head;
    bad     := FALSE;
    classPath := ClassPath (m);
    options := JavaOptions (m);
    file, jar, argFile, all, stamp, dir: TEXT;
  BEGIN
    IF Defined (m, "M3J_EXPORTS") THEN RETURN; END;

    WHILE (u # NIL) DO
      CASE u.kind OF
      | UK.I3, UK.M3, UK.IG, UK.MG =>
          file := M3Unit.FullPath (u);
          newest := MAX (newest, Utils.ModificationTime (file));
          IF u.imported THEN
            AddOnce (pkgs, u.loc.pkg_dir);
          ELSE
            AddOnce (dirs, Pathname.Prefix (file));
            IF (u.kind = UK.M3) AND NOT program AND Unsafe (file) THEN
              (* m3j compiles safe modules only; a program that needs this
                 one fails to compile *)
              Msg.Out ("m3j: warning: UNSAFE module left out of the library: ",
                       file, Wr.EOL);
            ELSIF (u.kind = UK.I3) OR (u.kind = UK.M3) THEN
              files.addhi (file);
            END;
          END;
      | UK.M3LIB =>
          IF u.imported AND (u.loc # NIL) AND (u.loc.pkg_dir # NIL) THEN
            AddOnce (pkgs, u.loc.pkg_dir);
          END;
      | UK.C, UK.CPP, UK.H, UK.B, UK.S, UK.O, UK.LIB, UK.IO, UK.MO,
        UK.IS, UK.MS, UK.IC, UK.MC, UK.IB, UK.MB =>
          IF NOT u.imported THEN
            Msg.Error (NIL, "m3j compiles only Modula-3 sources: ",
                       M3Unit.FullPath (u));
            bad := TRUE;
          END;
      ELSE (* templates, linker data: nothing to compile *)
      END;
      u := u.next;
    END;
    IF bad THEN M3Options.exit_code := 1;  RETURN; END;

    IF NOT program THEN args.addhi ("-lib"); END;
    IF NOT Text.Empty (classPath) THEN
      args.addhi ("-cp");  args.addhi (classPath);
      newest := MAX (newest, ClassPathTime (classPath));
    END;
    FOR i := 0 TO dirs.size () - 1 DO
      args.addhi ("-I");  args.addhi (dirs.get (i));
    END;
    FOR i := 0 TO pkgs.size () - 1 DO
      dir := PackageDir (pkgs.get (i), lib);
      IF dir # NIL THEN args.addhi ("-pkg");  args.addhi (dir); END;
    END;
    FOR i := 0 TO files.size () - 1 DO args.addhi (files.get (i)); END;

    IF program THEN
      jar := ProgramFiles (name)[0];
      args.addhi ("-d");  args.addhi (jar);
      all := Join (args, ' ');
      (* what the jar and the scripts were made from *)
      stamp := all;
      IF NOT Text.Empty (options) THEN stamp := all & "\n" & options; END;
      argFile := name & ".m3j";
      IF Utils.IsFile (jar)
        AND Utils.ModificationTime (jar) >= newest
        AND Text.Equal (ReadFile (argFile), stamp) THEN
        Msg.Explain ("program \"", jar, "\" is up to date");
        RETURN;
      END;
      Msg.Explain ("new sources -> compiling ", jar);
    ELSE
      all := Join (args, ' ');
      Msg.Explain ("checking library \"", name, "\"");
    END;
    Msg.Commands ("m3j ", all);
    IF M3jCompile.Run (Join (args, '\000'), args.size ()) # 0 THEN
      Msg.Explain ("compilation failed => not building ",
                   ARRAY BOOLEAN OF TEXT {"library \"", "program \""}[program],
                   name & "\"");
      M3Options.exit_code := 1;
      IF program THEN Utils.Remove (jar); Utils.Remove (argFile); END;
      RETURN;
    END;
    IF program THEN
      Utils.NoteNewFile (jar);
      WriteText (argFile, stamp);
      WriteScripts (name, jar, home, classPath, options);
    END;
  END Compile;

PROCEDURE Unsafe (file: TEXT): BOOLEAN =
  (* Whether the module in file is UNSAFE: its first word, after comments
     and pragmas. *)
  VAR t := ReadFile (file);  i := 0;  n := Text.Length (t);  depth: INTEGER;
  BEGIN
    LOOP
      WHILE (i < n) AND (Text.GetChar (t, i) IN Blanks) DO INC (i); END;
      IF (i + 1 < n) AND (Text.GetChar (t, i) = '(')
        AND (Text.GetChar (t, i + 1) = '*') THEN
        (* a comment, which may be nested *)
        depth := 1;  INC (i, 2);
        WHILE (i < n) AND (depth > 0) DO
          IF (i + 1 < n) AND (Text.GetChar (t, i) = '(')
            AND (Text.GetChar (t, i + 1) = '*') THEN
            INC (depth);  INC (i, 2);
          ELSIF (i + 1 < n) AND (Text.GetChar (t, i) = '*')
            AND (Text.GetChar (t, i + 1) = ')') THEN
            DEC (depth);  INC (i, 2);
          ELSE
            INC (i);
          END;
        END;
      ELSIF (i + 1 < n) AND (Text.GetChar (t, i) = '<')
        AND (Text.GetChar (t, i + 1) = '*') THEN
        (* a pragma *)
        INC (i, 2);
        WHILE (i + 1 < n) AND NOT ((Text.GetChar (t, i) = '*')
                                   AND (Text.GetChar (t, i + 1) = '>')) DO
          INC (i);
        END;
        INC (i, 2);
      ELSE
        EXIT;
      END;
    END;
    RETURN (i + 6 <= n) AND Text.Equal (Text.Sub (t, i, 6), "UNSAFE");
  END Unsafe;

CONST
  Blanks = SET OF CHAR {' ', '\t', '\n', '\r', '\f'};

PROCEDURE PackageDir (pkg_dir, lib: TEXT): TEXT =
  (* The directory with the .M3EXPORTS of the package in pkg_dir for the
     compiler, or NIL for a package that m3j brings. *)
  VAR d: TEXT;
  BEGIN
    IF SameDir (Pathname.Prefix (pkg_dir), lib) THEN RETURN NIL; END;
    d := Pathname.Join (pkg_dir, "JVM", NIL);
    IF Utils.IsFile (Pathname.Join (d, ".M3EXPORTS", NIL)) THEN RETURN d; END;
    d := Pathname.Join (pkg_dir, "AMD64_NT", NIL);
    IF Utils.IsFile (Pathname.Join (d, ".M3EXPORTS", NIL)) THEN RETURN d; END;
    RETURN pkg_dir;
  END PackageDir;

PROCEDURE SameDir (a, b: TEXT): BOOLEAN =
  BEGIN
    TRY
      a := FS.GetAbsolutePathname (a);
      b := FS.GetAbsolutePathname (b);
    EXCEPT OSError.E =>
      RETURN FALSE;
    END;
    IF M3jOS.Posix () THEN RETURN Text.Equal (a, b); END;
    RETURN Text.Equal (Lower (a), Lower (b));
  END SameDir;

PROCEDURE Lower (t: TEXT): TEXT =
  VAR c := NEW (REF ARRAY OF CHAR, Text.Length (t));
  BEGIN
    Text.SetChars (c^, t);
    FOR i := 0 TO LAST (c^) DO
      IF c[i] = '/' THEN c[i] := '\\';
      ELSIF ('A' <= c[i]) AND (c[i] <= 'Z') THEN
        c[i] := VAL (ORD (c[i]) - ORD ('A') + ORD ('a'), CHAR);
      END;
    END;
    RETURN Text.FromChars (c^);
  END Lower;

PROCEDURE AddOnce (s: TextSeq.T;  x: TEXT) =
  BEGIN
    FOR i := 0 TO s.size () - 1 DO
      IF Text.Equal (s.get (i), x) THEN RETURN; END;
    END;
    s.addhi (x);
  END AddOnce;

PROCEDURE Join (s: TextSeq.T;  sep: CHAR): TEXT =
  VAR r := "";
  BEGIN
    FOR i := 0 TO s.size () - 1 DO
      IF i > 0 THEN r := r & Text.FromChar (sep); END;
      r := r & s.get (i);
    END;
    RETURN r;
  END Join;

PROCEDURE Defined (m: Quake.Machine;  sym: TEXT): BOOLEAN =
  VAR v: TEXT;
  BEGIN
    TRY
      v := Quake.LookUp (m, sym);
    EXCEPT Quake.Error =>
      RETURN FALSE;
    END;
    RETURN (v # NIL) AND NOT Text.Equal (v, "");
  END Defined;

(*------------------------------------------------------ Java class path ---*)

PROCEDURE PathSeparator (): TEXT =
  BEGIN
    RETURN ARRAY BOOLEAN OF TEXT {";", ":"}[M3jOS.Posix ()];
  END PathSeparator;

PROCEDURE ClassPath (m: Quake.Machine): TEXT =
  (* What java_classpath has collected, separated as in a class path. *)
  VAR v: TEXT := NIL;
  BEGIN
    TRY
      v := Quake.LookUp (m, ClassPathVar);
    EXCEPT Quake.Error =>
    END;
    IF v = NIL THEN RETURN ""; END;
    RETURN v;
  END ClassPath;

PROCEDURE OnClassPath (classPath, path: TEXT): BOOLEAN =
  VAR
    sep := Text.GetChar (PathSeparator (), 0);
    start := 0;  n := Text.Length (classPath);  stop: INTEGER;
  BEGIN
    WHILE start <= n DO
      stop := Text.FindChar (classPath, sep, start);
      IF stop < 0 THEN stop := n; END;
      IF Text.Equal (Text.Sub (classPath, start, stop - start), path) THEN RETURN TRUE; END;
      start := stop + 1;
    END;
    RETURN FALSE;
  END OnClassPath;

PROCEDURE JavaOptions (m: Quake.Machine): TEXT =
  (* What java_option has collected, a line each. *)
  VAR v: TEXT := NIL;
  BEGIN
    TRY
      v := Quake.LookUp (m, JavaOptionsVar);
    EXCEPT Quake.Error =>
    END;
    IF v = NIL THEN RETURN ""; END;
    RETURN v;
  END JavaOptions;

PROCEDURE AddJavaOption (m: Quake.Machine;  option: TEXT) RAISES {Quake.Error} =
  VAR old := JavaOptions (m);
  BEGIN
    IF Text.Empty (old) THEN
      Quake.Define (m, JavaOptionsVar, option);
    ELSE
      Quake.Define (m, JavaOptionsVar, old & "\n" & option);
    END;
  END AddJavaOption;

PROCEDURE AddClassPath (m: Quake.Machine;  path: TEXT) RAISES {Quake.Error} =
  VAR old := ClassPath (m);
  BEGIN
    IF Text.Empty (old) THEN
      Quake.Define (m, ClassPathVar, path);
    ELSIF OnClassPath (old, path) THEN
      RETURN;
    ELSE
      Quake.Define (m, ClassPathVar, old & PathSeparator () & path);
    END;
  END AddClassPath;

PROCEDURE ClassPathTime (classPath: TEXT): INTEGER =
  (* The time of the newest jar of the class path: a program is compiled
     against what its bindings find there. *)
  VAR
    sep := Text.GetChar (PathSeparator (), 0);
    newest := Utils.NO_TIME;
    start := 0;  n := Text.Length (classPath);  stop: INTEGER;  e: TEXT;
  BEGIN
    WHILE start <= n DO
      stop := Text.FindChar (classPath, sep, start);
      IF stop < 0 THEN stop := n; END;
      e := Text.Sub (classPath, start, stop - start);
      IF Utils.IsFile (e) THEN newest := MAX (newest, Utils.ModificationTime (e)); END;
      start := stop + 1;
    END;
    RETURN newest;
  END ClassPathTime;

(*---------------------------------------------------------------- files ---*)

PROCEDURE ReadFile (file: TEXT): TEXT =
  VAR rd: Rd.T;  t: TEXT;
  BEGIN
    IF NOT Utils.IsFile (file) THEN RETURN ""; END;
    TRY
      rd := FileRd.Open (file);
      TRY
        t := Rd.GetText (rd, LAST (CARDINAL));
      FINALLY
        Rd.Close (rd);
      END;
    EXCEPT OSError.E, Rd.Failure, Thread.Alerted =>
      RETURN "";
    END;
    RETURN t;
  END ReadFile;

PROCEDURE WriteText (file, text: TEXT) =
  VAR wr := Utils.OpenWriter (file, fatal := TRUE);
  BEGIN
    TRY
      Wr.PutText (wr, text);
    EXCEPT Wr.Failure, Thread.Alerted =>
      Msg.FatalError (NIL, "unable to write ", file);
    END;
    Utils.CloseWriter (wr, file);
  END WriteText;

PROCEDURE ShellQuoted (t: TEXT): TEXT =
  (* t in double quotes for a POSIX shell: with "$", "`" and "\" escaped. *)
  VAR r := "\"";  c: CHAR;
  BEGIN
    FOR i := 0 TO Text.Length (t) - 1 DO
      c := Text.GetChar (t, i);
      IF (c = '$') OR (c = '`') OR (c = '\\') THEN r := r & "\\"; END;
      r := r & Text.FromChar (c);
    END;
    RETURN r & "\"";
  END ShellQuoted;

PROCEDURE CmdQuoted (t: TEXT): TEXT =
  (* t in double quotes for cmd.exe and the java launcher: "%" doubled, so
     that cmd leaves it alone, and the backslashes at the end doubled, so
     that the launcher does not take the closing quote for one of t. *)
  VAR r := "\"";  c: CHAR;  n := Text.Length (t);  tail := n;
  BEGIN
    WHILE (tail > 0) AND (Text.GetChar (t, tail - 1) = '\\') DO DEC (tail); END;
    FOR i := 0 TO n - 1 DO
      c := Text.GetChar (t, i);
      IF c = '%' THEN r := r & "%"; END;
      IF (c = '\\') AND (i >= tail) THEN r := r & "\\"; END;
      r := r & Text.FromChar (c);
    END;
    RETURN r & "\"";
  END CmdQuoted;

PROCEDURE WriteScripts (prog, jar, home, classPath, options: TEXT) =
  (* The scripts that run the program in jar, which is next to them, with
     the java of m3build and the options of java_option, the runtime of m3j
     and the Java classes of its bindings. *)
  VAR
    java    := M3jCompile.Java ();
    runtime := Pathname.Join (home, "m3j-runtime.jar", NIL);
    sep     := PathSeparator ();
    files   := ProgramFiles (prog);
    extra   := "";
    shOpts  := "";
    cmdOpts := "";
    start   := 0;
    stop    : INTEGER;
    o       : TEXT;
  BEGIN
    IF NOT Text.Empty (classPath) THEN extra := sep & classPath; END;
    WHILE start < Text.Length (options) DO
      stop := Text.FindChar (options, '\n', start);
      IF stop < 0 THEN stop := Text.Length (options); END;
      o := Text.Sub (options, start, stop - start);
      shOpts := shOpts & " " & ShellQuoted (o);
      cmdOpts := cmdOpts & " " & CmdQuoted (o);
      start := stop + 1;
    END;
    WriteText (files[1],
      "#!/bin/sh" & "\n" &
      "# runs the program " & prog & ", compiled by m3j" & "\n" &
      "exec \"" & java & "\"" & shOpts & " -cp \"" & runtime & sep &
      "$(dirname \"$0\")/" & jar & extra & "\" m3.Main \"$@\"" & "\n");
    WriteText (files[2],
      "@echo off" & "\r\n" &
      "rem runs the program " & prog & ", compiled by m3j" & "\r\n" &
      "\"" & java & "\"" & cmdOpts & " -cp \"" & runtime & ";%~dp0" & jar & extra &
      "\" m3.Main %*" & "\r\n");
  END WriteScripts;

BEGIN
END Builder.
