(* The front end of CM3 as a program: it compiles a list of Modula-3 units
   in one session, as the compiler driver of m3j (m3j.jvm.Compile) does,
   and writes the intermediate code of each in binary form. Its
   environment finds the sources of the imported units as the environment
   of m3j's driver (m3j.jvm.FrontEnv) does, so the two front ends see the
   same files under the same names.

   Usage:
     Main [-I DIR]... [-pkg DIR]... -o OUTDIR [-list FILE] [-OPTION]... [FILE]...

   -I DIR    a source directory: its units *.i3, *.m3, *.ig and *.mg,
             named by their paths DIR\FILE (DIR/FILE on POSIX)
   -pkg DIR  a source package: its units in DIR\src and below, named as
             cm3 names them ("../src/rw/IO.m3"), and in DIR\AMD64_NT, named
             by their file names
   -o OUTDIR the directory of the intermediate code: unit k of the session
             (from 1) writes OUTDIR\k.cg
   -list F   the units to compile, one per line: a flag ("Q" if its
             diagnostics are not printed, as for a library module that is
             compiled on trial, else "-"), a tab, its path, a tab, and its
             name in diagnostics
   FILE      a unit to compile, named as given (after those of -list)

   The first source directory or package that has a unit wins. Other
   arguments that start with '-' are options of the front end, which gets
   "-w1" first. The exit code is 1 if a unit that is not compiled on trial
   has errors. *)

MODULE Main;

IMPORT Params, Text, Wr, Rd, FileRd, FileWr, FS, OSError, Stdio;
IMPORT Process, Thread, Fmt, Fingerprint, TextRefTbl, IntRefTbl;
IMPORT M3Compiler, M3Front, M3ID, M3CG, M3CG_BinWr, Target, M3jOS;

<*FATAL Thread.Alerted, Wr.Failure*>

CONST Build = "AMD64_NT";

TYPE
  Source = REF RECORD
    path : TEXT;   (* where the unit is *)
    name : TEXT;   (* how the front end names it *)
  END;

  Entry = RECORD
    quiet : BOOLEAN;
    path  : TEXT;
    name  : TEXT;
  END;

  Entries = REF ARRAY OF Entry;

  Env = M3Compiler.Environment OBJECT
  OVERRIDES
    report_error        := ReportError;
    find_source         := FindSource;
    note_unit           := NoteUnit;
    note_comment        := NoteComment;
    note_interface_use  := NoteInterfaceUse;
    note_generic_use    := NoteGenericUse;
    note_version_stamp  := NoteVersionStamp;
    note_opaque         := NoteOpaque;
    note_revelation     := NoteRevelation;
    note_opaque_magic   := NoteOpaqueMagic;
    find_opaque_magic   := FindOpaqueMagic;
    note_ast            := NoteAst;
    find_ast            := FindAst;
    note_type           := NoteType;
    init_code_generator := InitCodeGenerator;
    note_webinfo        := NoteWebinfo;
    get_implementations := GetImplementations;
  END;

VAR
  posix    := M3jOS.Posix ();
  sep      := ARRAY BOOLEAN OF TEXT {"\\", "/"}[posix];  (* between the parts of a path *)
  units    := NEW (TextRefTbl.Default).init ();  (* file name -> Source *)
  asts     := NEW (IntRefTbl.Default).init ();   (* M3ID.T -> tree *)
  quiet    := FALSE;     (* while TRUE, diagnostics are not printed *)
  out_dir  : TEXT := NIL;
  cg_file  : TEXT := NIL;  (* the file of the current unit's code *)
  cg_wr    : Wr.T := NIL;
  err      := Stdio.stderr;

(*----------------------------------------------------------- the sources ---*)

PROCEDURE IsUnit (file: TEXT): BOOLEAN =
  VAR n := Text.Length (file);  ext: TEXT;
  BEGIN
    IF n < 4 THEN RETURN FALSE END;
    ext := Text.Sub (file, n - 3);
    RETURN Text.Equal (ext, ".i3") OR Text.Equal (ext, ".m3")
        OR Text.Equal (ext, ".ig") OR Text.Equal (ext, ".mg");
  END IsUnit;

PROCEDURE IsDirectory (path: TEXT): BOOLEAN =
  BEGIN
    TRY
      RETURN FS.Status (path).type = FS.DirectoryFileType;
    EXCEPT OSError.E =>
      RETURN FALSE;
    END;
  END IsDirectory;

PROCEDURE Add (file, path, name: TEXT) =
  VAR r: REFANY;
  BEGIN
    IF NOT units.get (file, r) THEN
      EVAL units.put (file, NEW (Source, path := path, name := name));
    END;
  END Add;

(* The entries of directory dir, sorted as m3j's driver sorts paths
   (Windows paths compare without regard to case, POSIX paths by their
   characters). *)
PROCEDURE ListDir (dir: TEXT): REF ARRAY OF TEXT =
  VAR
    it   : FS.Iterator;
    name : TEXT;
    n    := 0;
    a    := NEW (REF ARRAY OF TEXT, 64);
    b    : REF ARRAY OF TEXT;
  BEGIN
    TRY
      it := FS.Iterate (dir);
    EXCEPT OSError.E =>
      RETURN NEW (REF ARRAY OF TEXT, 0);
    END;
    WHILE it.next (name) DO
      IF n = NUMBER (a^) THEN
        b := NEW (REF ARRAY OF TEXT, 2 * n);
        SUBARRAY (b^, 0, n) := a^;
        a := b;
      END;
      a[n] := name;
      INC (n);
    END;
    it.close ();
    b := NEW (REF ARRAY OF TEXT, n);
    b^ := SUBARRAY (a^, 0, n);
    Sort (b^);
    RETURN b;
  END ListDir;

PROCEDURE Sort (VAR a: ARRAY OF TEXT) =
  VAR j: INTEGER;  x: TEXT;
  BEGIN
    FOR i := 1 TO LAST (a) DO
      x := a[i];
      j := i - 1;
      WHILE (j >= 0) AND PathLess (x, a[j]) DO
        a[j + 1] := a[j];
        DEC (j);
      END;
      a[j + 1] := x;
    END;
  END Sort;

PROCEDURE PathLess (a, b: TEXT): BOOLEAN =
  VAR na := Text.Length (a);  nb := Text.Length (b);  ca, cb: CHAR;
  BEGIN
    FOR i := 0 TO MIN (na, nb) - 1 DO
      ca := Text.GetChar (a, i);
      cb := Text.GetChar (b, i);
      IF NOT posix THEN ca := Upper (ca);  cb := Upper (cb) END;
      IF ca # cb THEN RETURN ca < cb END;
    END;
    RETURN na < nb;
  END PathLess;

PROCEDURE Upper (c: CHAR): CHAR =
  BEGIN
    IF ('a' <= c) AND (c <= 'z') THEN
      RETURN VAL (ORD (c) - ORD ('a') + ORD ('A'), CHAR);
    END;
    RETURN c;
  END Upper;

(* Adds the units of the source directory dir. *)
PROCEDURE AddSourceDir (dir: TEXT) =
  VAR e := ListDir (dir);  path: TEXT;
  BEGIN
    FOR i := 0 TO LAST (e^) DO
      path := dir & sep & e[i];
      IF IsUnit (e[i]) AND NOT IsDirectory (path) THEN
        Add (e[i], path, path);
      END;
    END;
  END AddSourceDir;

(* Whether the path rel (its parts separated by '/') is in a directory of
   the given name. *)
PROCEDURE InDir (rel, name: TEXT): BOOLEAN =
  VAR start := 0;  k: INTEGER;
  BEGIN
    LOOP
      k := Text.FindChar (rel, '/', start);
      IF k < 0 THEN RETURN FALSE END;
      IF Text.Equal (Text.Sub (rel, start, k - start), name) THEN RETURN TRUE END;
      start := k + 1;
    END;
  END InDir;

(* Adds the units of the source package pkg: those of pkg\src and its
   subdirectories, then those of pkg\AMD64_NT. Of a unit that cm3 has for
   both systems (src\os\POSIX\OSConfig.i3, src\os\WIN32\OSConfig.i3), the
   one of the system that the program runs on wins. *)
PROCEDURE AddSourcePackage (pkg: TEXT) =
  VAR files := NEW (REF ARRAY OF TEXT, 256);  n := 0;  b: REF ARRAY OF TEXT;

  PROCEDURE Walk (dir, rel: TEXT) =
    VAR e := ListDir (dir);  path: TEXT;
    BEGIN
      FOR i := 0 TO LAST (e^) DO
        path := dir & sep & e[i];
        IF IsDirectory (path) THEN
          Walk (path, rel & "/" & e[i]);
        ELSIF IsUnit (e[i]) THEN
          IF n = NUMBER (files^) THEN
            b := NEW (REF ARRAY OF TEXT, 2 * n);
            SUBARRAY (b^, 0, n) := files^;
            files := b;
          END;
          files[n] := rel & "/" & e[i];
          INC (n);
        END;
      END;
    END Walk;

  VAR rel, file, other: TEXT;  k: INTEGER;
  BEGIN
    IF M3jOS.Posix () THEN other := "WIN32" ELSE other := "POSIX" END;
    IF IsDirectory (pkg & sep & "src") THEN
      Walk (pkg & sep & "src", "src");
      b := NEW (REF ARRAY OF TEXT, n);
      b^ := SUBARRAY (files^, 0, n);
      (* sorted as paths: by their names with the separator of the
         system between the parts *)
      FOR i := 0 TO n - 1 DO b[i] := Native (b[i]) END;
      Sort (b^);
      (* the units in a directory of the other system last *)
      FOR last := FALSE TO TRUE DO
        FOR i := 0 TO n - 1 DO
          rel := Slashes (b[i]);
          IF InDir (rel, other) = last THEN
            k := Text.FindCharR (rel, '/');
            file := Text.Sub (rel, k + 1);
            Add (file, pkg & sep & b[i], "../" & rel);
          END;
        END;
      END;
    END;
    IF IsDirectory (pkg & sep & Build) THEN
      WITH e = ListDir (pkg & sep & Build) DO
        FOR i := 0 TO LAST (e^) DO
          IF IsUnit (e[i]) THEN
            Add (e[i], pkg & sep & Build & sep & e[i], e[i]);
          END;
        END;
      END;
    END;
  END AddSourcePackage;

PROCEDURE Native (t: TEXT): TEXT =
  (* t, its parts separated by '/', with the separator of the system *)
  BEGIN
    RETURN Replace (t, '/', Text.GetChar (sep, 0));
  END Native;

PROCEDURE Slashes (t: TEXT): TEXT =
  BEGIN
    RETURN Replace (t, '\\', '/');
  END Slashes;

PROCEDURE Replace (t: TEXT;  a, b: CHAR): TEXT =
  VAR c := NEW (REF ARRAY OF CHAR, Text.Length (t));
  BEGIN
    Text.SetChars (c^, t);
    FOR i := 0 TO LAST (c^) DO
      IF c[i] = a THEN c[i] := b END;
    END;
    RETURN Text.FromChars (c^);
  END Replace;

(*----------------------------------------------------------- environment ---*)

PROCEDURE ReportError (<*UNUSED*> self: Env;  file: TEXT;  line: INTEGER;
                       msg: TEXT) =
  BEGIN
    IF quiet THEN RETURN END;
    IF file # NIL THEN
      Wr.PutText (err, "\"" & file & "\", line " & Fmt.Int (line) & ": "
                         & msg & "\n");
    ELSE
      Wr.PutText (err, msg & "\n");
    END;
    Wr.Flush (err);
  END ReportError;

PROCEDURE FindSource (<*UNUSED*> self: Env;  unit: M3ID.T;
                      interface, generic: BOOLEAN): M3Compiler.SourceFile =
  VAR ext: TEXT;  name := M3ID.ToText (unit);  r: REFANY;  s: Source;
      file: M3Compiler.SourceFile;
  BEGIN
    IF interface THEN
      IF generic THEN ext := ".ig" ELSE ext := ".i3" END;
    ELSE
      IF generic THEN ext := ".mg" ELSE ext := ".m3" END;
    END;
    IF units.get (name & ext, r) THEN
      s := r;
      file.name := s.name;
      TRY
        file.contents := FS.OpenFileReadonly (s.path);
      EXCEPT OSError.E =>
        file.contents := NIL;
      END;
    ELSE
      file.name := name & ext;
      file.contents := NIL;
    END;
    RETURN file;
  END FindSource;

PROCEDURE NoteUnit (<*UNUSED*> self: Env;  <*UNUSED*> name: M3ID.T;
                    <*UNUSED*> interface: BOOLEAN) =
  BEGIN
  END NoteUnit;

PROCEDURE NoteComment (<*UNUSED*> self: Env;  <*UNUSED*> msg: TEXT) =
  BEGIN
  END NoteComment;

PROCEDURE NoteInterfaceUse (<*UNUSED*> self: Env;  <*UNUSED*> name: M3ID.T;
                            <*UNUSED*> imported: BOOLEAN) =
  BEGIN
  END NoteInterfaceUse;

PROCEDURE NoteGenericUse (<*UNUSED*> self: Env;  <*UNUSED*> name: M3ID.T) =
  BEGIN
  END NoteGenericUse;

PROCEDURE NoteVersionStamp (<*UNUSED*> self: Env;
                            <*UNUSED*> unit, symbol: M3ID.T;
                            <*UNUSED*> READONLY vs: Fingerprint.T;
                            <*UNUSED*> imported, implemented: BOOLEAN) =
  BEGIN
  END NoteVersionStamp;

PROCEDURE NoteOpaque (<*UNUSED*> self: Env;
                      <*UNUSED*> type, super_type: M3Compiler.TypeID;
                      <*UNUSED*> name: M3ID.T) =
  BEGIN
  END NoteOpaque;

PROCEDURE NoteRevelation (<*UNUSED*> self: Env;  <*UNUSED*> unit: M3ID.T;
                          <*UNUSED*> interface: BOOLEAN;
                          <*UNUSED*> lhs, rhs: M3Compiler.TypeID;
                          <*UNUSED*> full, imported: BOOLEAN) =
  BEGIN
  END NoteRevelation;

PROCEDURE NoteOpaqueMagic (<*UNUSED*> self: Env;
                           <*UNUSED*> type, super_type: M3Compiler.TypeID;
                           <*UNUSED*> data_size, data_align,
                                      method_size: INTEGER) =
  BEGIN
  END NoteOpaqueMagic;

PROCEDURE FindOpaqueMagic (<*UNUSED*> self: Env;
                           <*UNUSED*> type: M3Compiler.TypeID;
                           <*UNUSED*> VAR super_type: M3Compiler.TypeID;
                           <*UNUSED*> VAR data_size, data_align,
                                          method_size: INTEGER): BOOLEAN =
  BEGIN
    (* as in m3j: the sizes of opaque object types are never needed *)
    RETURN FALSE;
  END FindOpaqueMagic;

PROCEDURE NoteAst (<*UNUSED*> self: Env;  unit: M3ID.T;  ast: REFANY) =
  BEGIN
    EVAL asts.put (unit, ast);
  END NoteAst;

PROCEDURE FindAst (<*UNUSED*> self: Env;  unit: M3ID.T): REFANY =
  VAR r: REFANY;
  BEGIN
    IF asts.get (unit, r) THEN RETURN r END;
    RETURN NIL;
  END FindAst;

PROCEDURE NoteType (<*UNUSED*> self: Env;
                    <*UNUSED*> type: M3Compiler.TypeID;
                    <*UNUSED*> imported: BOOLEAN) =
  BEGIN
  END NoteType;

PROCEDURE InitCodeGenerator (<*UNUSED*> self: Env): M3CG.T =
  BEGIN
    TRY
      cg_wr := FileWr.Open (cg_file);
    EXCEPT OSError.E =>
      Fail ("cannot write " & cg_file);
    END;
    RETURN M3CG_BinWr.New (cg_wr);
  END InitCodeGenerator;

PROCEDURE NoteWebinfo (<*UNUSED*> self: Env;  <*UNUSED*> t: TEXT) =
  BEGIN
  END NoteWebinfo;

PROCEDURE GetImplementations (<*UNUSED*> self: Env;
                              <*UNUSED*> interface: M3ID.T):
  M3Compiler.ImplList =
  BEGIN
    RETURN NIL;
  END GetImplementations;

(*--------------------------------------------------------------- driver ---*)

PROCEDURE Fail (msg: TEXT) =
  BEGIN
    Wr.PutText (err, "m3front: " & msg & "\n");
    Wr.Flush (err);
    Process.Exit (2);
  END Fail;

(* The entries of the list file. *)
PROCEDURE ReadList (file: TEXT): Entries =
  VAR
    rd   : Rd.T;
    line : TEXT;
    a    := NEW (Entries, 64);
    b    : Entries;
    n    := 0;
    t1, t2: INTEGER;
  BEGIN
    TRY
      rd := FileRd.Open (file);
      WHILE NOT Rd.EOF (rd) DO
        line := Rd.GetLine (rd);
        IF Text.Length (line) > 0 THEN
          t1 := Text.FindChar (line, '\t');
          t2 := Text.FindChar (line, '\t', t1 + 1);
          IF (t1 < 0) OR (t2 < 0) THEN Fail ("bad line in " & file) END;
          IF n = NUMBER (a^) THEN
            b := NEW (Entries, 2 * n);
            SUBARRAY (b^, 0, n) := a^;
            a := b;
          END;
          a[n].quiet := Text.Equal (Text.Sub (line, 0, t1), "Q");
          a[n].path := Text.Sub (line, t1 + 1, t2 - t1 - 1);
          a[n].name := Text.Sub (line, t2 + 1);
          INC (n);
        END;
      END;
      Rd.Close (rd);
    EXCEPT
    | OSError.E => Fail ("cannot read " & file);
    | Rd.Failure, Rd.EndOfFile => Fail ("cannot read " & file);
    END;
    b := NEW (Entries, n);
    b^ := SUBARRAY (a^, 0, n);
    RETURN b;
  END ReadList;

PROCEDURE Append (VAR a: Entries;  READONLY e: Entry) =
  VAR b := NEW (Entries, NUMBER (a^) + 1);
  BEGIN
    SUBARRAY (b^, 0, NUMBER (a^)) := a^;
    b[NUMBER (a^)] := e;
    a := b;
  END Append;

PROCEDURE Run () =
  VAR
    entries  := NEW (Entries, 0);
    options  := NEW (REF ARRAY OF TEXT, Params.Count + 1);
    n_opts   := 1;
    env      := NEW (Env);
    i        := 1;
    arg      : TEXT;
    failed   := 0;
    src      : M3Compiler.SourceFile;
    ok       : BOOLEAN;
  BEGIN
    options[0] := "-w1";
    WHILE i < Params.Count DO
      arg := Params.Get (i);
      IF Text.Equal (arg, "-I") AND i + 1 < Params.Count THEN
        INC (i);  AddSourceDir (Params.Get (i));
      ELSIF Text.Equal (arg, "-pkg") AND i + 1 < Params.Count THEN
        INC (i);  AddSourcePackage (Params.Get (i));
      ELSIF Text.Equal (arg, "-o") AND i + 1 < Params.Count THEN
        INC (i);  out_dir := Params.Get (i);
      ELSIF Text.Equal (arg, "-list") AND i + 1 < Params.Count THEN
        INC (i);
        WITH l = ReadList (Params.Get (i)) DO
          FOR k := 0 TO LAST (l^) DO Append (entries, l[k]) END;
        END;
      ELSIF Text.Length (arg) > 0 AND Text.GetChar (arg, 0) = '-' THEN
        options[n_opts] := arg;  INC (n_opts);
      ELSE
        Append (entries, Entry {quiet := FALSE, path := arg, name := arg});
      END;
      INC (i);
    END;
    IF (out_dir = NIL) OR (NUMBER (entries^) = 0) THEN
      Fail ("usage: Main [-I DIR]... [-pkg DIR]... -o OUTDIR"
              & " [-list FILE] [-OPTION]... [FILE]...");
    END;

    (* the target, as cm3 sets it up for AMD64_NT in the C mode *)
    Target.SetBuild_dir (Build);
    Target.BackendMode := Target.M3BackendMode_t.C;
    Target.BackendModeInitialized := TRUE;
    IF NOT Target.Init (Build, "WIN32", Target.M3BackendMode_t.C) THEN
      Fail ("Target.Init failed");
    END;

    FOR k := 0 TO LAST (entries^) DO
      WITH e = entries[k] DO
        src.name := e.name;
        TRY
          src.contents := FS.OpenFileReadonly (e.path);
        EXCEPT OSError.E =>
          Fail ("cannot read " & e.path);
        END;
        cg_file := out_dir & sep & Fmt.Int (k + 1) & ".cg";
        quiet := e.quiet;
        ok := M3Front.Compile (src, env, SUBARRAY (options^, 0, n_opts));
        quiet := FALSE;
        TRY src.contents.close () EXCEPT OSError.E => END;
        IF cg_wr # NIL THEN
          TRY Wr.Close (cg_wr) EXCEPT Wr.Failure => Fail ("cannot write " & cg_file) END;
          cg_wr := NIL;
        END;
        IF NOT ok AND NOT e.quiet THEN INC (failed) END;
      END;
    END;
    IF failed > 0 THEN Process.Exit (1) END;
  END Run;

BEGIN
  Run ();
END Main.
