(* The file system interfaces at their edges: creating, renaming and
   deleting files and directories (also where that fails), the status of
   files and directories, setting the modification time, absolute path
   names, listing a directory, the options of FS.OpenFile, and regular file
   handles (seek, read, write, readonly handles, which cannot be written).
   The program works in a directory "fsdir" in the current directory, which
   it creates and deletes. The messages of errors depend on the system; only
   the fact of an error is printed.

   With an argument, the program tests FS.SetModificationTime instead, which
   fails in cm3 on Windows (it opens the file read-only and then cannot set
   its time); its output is checked by hand. *)

MODULE Main;

IMPORT IO, Fmt, FS, File, RegularFile, OSError, Pathname, Wr, Rd, FileWr, FileRd, Text, Thread,
       Params, RuntimeError;

<* FATAL Thread.Alerted, Wr.Failure, Rd.Failure *>

CONST
  Dir = "fsdir";

VAR
  failures := 0;

PROCEDURE Line (t: TEXT) =
  BEGIN
    IO.Put(t & "\n");
  END Line;

PROCEDURE In (name: TEXT): TEXT =
  BEGIN
    RETURN Pathname.Join(Dir, name, NIL);
  END In;

PROCEDURE Failed (what: TEXT) =
  BEGIN
    INC(failures);
    Line(what & ": OSError");
  END Failed;

PROCEDURE WriteFile (p, text: TEXT) RAISES {OSError.E} =
  VAR w := FileWr.Open(p);
  BEGIN
    Wr.PutText(w, text);
    Wr.Close(w);
  END WriteFile;

PROCEDURE ReadFile (p: TEXT): TEXT RAISES {OSError.E} =
  VAR r := FileRd.Open(p);  t: TEXT;
  BEGIN
    t := Rd.GetText(r, LAST(CARDINAL));
    Rd.Close(r);
    RETURN t;
  END ReadFile;

PROCEDURE TypeName (t: File.Type): TEXT =
  BEGIN
    IF t = RegularFile.FileType THEN
      RETURN "regular"
    ELSIF t = FS.DirectoryFileType THEN
      RETURN "directory"
    ELSE
      RETURN "other"
    END;
  END TypeName;

PROCEDURE ShowStatus (p: TEXT) =
  VAR s: File.Status;
  BEGIN
    TRY
      s := FS.Status(p);
      IF s.type = FS.DirectoryFileType THEN
        Line("status " & p & ": directory");
      ELSE
        Line("status " & p & ": " & TypeName(s.type) & " " & Fmt.LongInt(s.size));
      END;
    EXCEPT
    | OSError.E => Failed("status " & p);
    END;
  END ShowStatus;

PROCEDURE Directories () =
  BEGIN
    TRY FS.CreateDirectory(Dir); Line("created " & Dir) EXCEPT OSError.E => Failed("create") END;
    TRY FS.CreateDirectory(Dir) EXCEPT OSError.E => Failed("create again") END;
    TRY
      FS.CreateDirectory(In("sub"));
      FS.CreateDirectory(Pathname.Join(In("sub"), "inner", NIL));
      Line("created sub and sub/inner");
    EXCEPT
      OSError.E => Failed("create sub");
    END;
    TRY FS.CreateDirectory(Pathname.Join(In("none"), "x", NIL)) EXCEPT OSError.E => Failed("create in a missing directory") END;
    ShowStatus(Dir);
    ShowStatus(In("missing"));
  END Directories;

PROCEDURE Files () =
  VAR t: TEXT;
  BEGIN
    TRY
      WriteFile(In("a.txt"), "hello\n");
      ShowStatus(In("a.txt"));
      Line("a.txt: " & ReadFile(In("a.txt")));
    EXCEPT
      OSError.E => Failed("write a.txt");
    END;
    (* renaming *)
    TRY
      FS.Rename(In("a.txt"), In("b.txt"));
      Line("renamed a.txt to b.txt");
    EXCEPT
      OSError.E => Failed("rename");
    END;
    ShowStatus(In("a.txt"));
    TRY
      WriteFile(In("c.txt"), "other text\n");
      FS.Rename(In("b.txt"), In("c.txt"));
      t := ReadFile(In("c.txt"));
      Line("renamed b.txt onto c.txt: " & t);
    EXCEPT
      OSError.E => Failed("rename onto a file");
    END;
    ShowStatus(In("b.txt"));
    TRY FS.Rename(In("missing"), In("d.txt")) EXCEPT OSError.E => Failed("rename a missing file") END;
    TRY
      FS.Rename(In("sub"), In("sub2"));
      Line("renamed sub to sub2");
      ShowStatus(Pathname.Join(In("sub2"), "inner", NIL));
    EXCEPT
      OSError.E => Failed("rename a directory");
    END;
  END Files;

PROCEDURE Absolute () =
  VAR a, b, c: TEXT;
  BEGIN
    TRY
      a := FS.GetAbsolutePathname(In("c.txt"));
      b := FS.GetAbsolutePathname(Pathname.Join(Pathname.Join(Dir, Pathname.Parent, NIL),
                                                Pathname.Join(Dir, "c.txt", NIL), NIL));
      c := FS.GetAbsolutePathname(Pathname.Join(Pathname.Current, In("c.txt"), NIL));
      Line("absolute " & Fmt.Bool(Pathname.Absolute(a)) & " " & Pathname.Last(a) & " "
           & Pathname.Last(Pathname.Prefix(a)) & " " & Fmt.Bool(Text.Equal(a, b)) & " "
           & Fmt.Bool(Text.Equal(a, c)));
      (* a file that does not exist has an absolute name too *)
      b := FS.GetAbsolutePathname(In("nothing.txt"));
      Line("absolute " & Fmt.Bool(Pathname.Absolute(b)) & " " & Pathname.Last(b) & " "
           & Fmt.Bool(Text.Equal(Pathname.Prefix(a), Pathname.Prefix(b))));
      Line("absolute of absolute " & Fmt.Bool(Text.Equal(a, FS.GetAbsolutePathname(a))));
    EXCEPT
      OSError.E => Failed("absolute");
    END;
  END Absolute;

PROCEDURE Listing () =
  VAR
    it: FS.Iterator;
    name: TEXT;
    s: File.Status;
  BEGIN
    TRY
      WriteFile(In("z.txt"), "zz");
      WriteFile(In("e.txt"), "eee");
      it := FS.Iterate(Dir);
      WHILE it.next(name) DO Line("entry " & name) END;
      it.close();
      it := FS.Iterate(Dir);
      WHILE it.nextWithStatus(name, s) DO
        IF s.type = FS.DirectoryFileType THEN
          Line("entry " & name & " directory");
        ELSE
          Line("entry " & name & " " & TypeName(s.type) & " " & Fmt.LongInt(s.size));
        END;
      END;
      it.close();
    EXCEPT
      OSError.E => Failed("iterate");
    END;
    TRY it := FS.Iterate(In("missing")) EXCEPT OSError.E => Failed("iterate a missing directory") END;
  END Listing;

PROCEDURE Bytes (t: TEXT): REF ARRAY OF File.Byte =
  VAR b := NEW(REF ARRAY OF File.Byte, Text.Length(t));
  BEGIN
    FOR i := 0 TO LAST(b^) DO b[i] := ORD(Text.GetChar(t, i)) END;
    RETURN b;
  END Bytes;

PROCEDURE Show (READONLY b: ARRAY OF File.Byte; n: INTEGER): TEXT =
  VAR t := "";
  BEGIN
    FOR i := 0 TO n - 1 DO t := t & Text.FromChar(VAL(b[i], CHAR)) END;
    RETURN t;
  END Show;

PROCEDURE Handles () =
  VAR
    h: File.T;
    buf: ARRAY [0 .. 63] OF File.Byte;
    n: INTEGER;
    p := In("h.bin");
  BEGIN
    TRY h := FS.OpenFile(p, create := FS.CreateOption.Never) EXCEPT OSError.E => Failed("open Never, missing") END;
    TRY
      h := FS.OpenFile(p, create := FS.CreateOption.Always);
      h.write(Bytes("0123456789")^);
      WITH r = NARROW(h, RegularFile.T) DO
        Line("seek end " & Fmt.Int(r.seek(RegularFile.Origin.End, 0)));
        Line("seek begin " & Fmt.Int(r.seek(RegularFile.Origin.Beginning, 3)));
        n := h.read(buf);
        Line("read " & Fmt.Int(n) & " " & Show(buf, n));
        Line("seek current " & Fmt.Int(r.seek(RegularFile.Origin.Current, -4)));
        h.write(Bytes("ab")^);
        EVAL r.seek(RegularFile.Origin.Beginning, 0);
        n := h.read(buf);
        Line("read " & Fmt.Int(n) & " " & Show(buf, n));
        n := h.read(buf);
        Line("read at the end " & Fmt.Int(n));
        r.flush();
      END;
      Line("handle size " & Fmt.LongInt(h.status().size) & " " & TypeName(h.status().type));
      h.close();
    EXCEPT
      OSError.E => Failed("open Always");
    END;
    TRY h := FS.OpenFile(p, create := FS.CreateOption.Always) EXCEPT OSError.E => Failed("open Always, exists") END;
    TRY
      h := FS.OpenFile(p, truncate := FALSE, create := FS.CreateOption.Never);
      WITH r = NARROW(h, RegularFile.T) DO
        EVAL r.seek(RegularFile.Origin.End, 0);
      END;
      h.write(Bytes("XYZ")^);
      h.close();
      Line("appended: " & ReadFile(p));
      h := FS.OpenFile(p);
      Line("truncated size " & Fmt.LongInt(h.status().size));
      h.write(Bytes("new")^);
      h.close();
      Line("rewritten: " & ReadFile(p));
    EXCEPT
      OSError.E => Failed("open Never");
    END;
    TRY
      h := FS.OpenFileReadonly(p);
      n := h.read(buf);
      Line("readonly read " & Fmt.Int(n) & " " & Show(buf, n));
      TRY
        h.write(Bytes("no")^);
      EXCEPT
      | OSError.E => Failed("write to a readonly handle");
      | RuntimeError.E (e) => Line("write to a readonly handle: " & RuntimeError.Tag(e));
      END;
      h.close();
    EXCEPT
      OSError.E => Failed("open readonly");
    END;
    TRY h := FS.OpenFileReadonly(In("missing")) EXCEPT OSError.E => Failed("open readonly, missing") END;
    TRY h := FS.OpenFile(Dir) EXCEPT OSError.E => Failed("open a directory") END;
  END Handles;

PROCEDURE Deleting () =
  BEGIN
    TRY FS.DeleteFile(Dir) EXCEPT OSError.E => Failed("delete a directory as a file") END;
    TRY FS.DeleteDirectory(In("c.txt")) EXCEPT OSError.E => Failed("delete a file as a directory") END;
    TRY FS.DeleteDirectory(In("sub2")) EXCEPT OSError.E => Failed("delete a directory that is not empty") END;
    TRY FS.DeleteFile(In("missing")) EXCEPT OSError.E => Failed("delete a missing file") END;
    TRY FS.DeleteDirectory(In("missing")) EXCEPT OSError.E => Failed("delete a missing directory") END;
    TRY
      FS.DeleteDirectory(Pathname.Join(In("sub2"), "inner", NIL));
      FS.DeleteDirectory(In("sub2"));
      FS.DeleteFile(In("c.txt"));
      FS.DeleteFile(In("e.txt"));
      FS.DeleteFile(In("z.txt"));
      FS.DeleteFile(In("h.bin"));
      Line("deleted the files");
      FS.DeleteDirectory(Dir);
      Line("deleted " & Dir);
    EXCEPT
      OSError.E => Failed("delete");
    END;
    ShowStatus(Dir);
  END Deleting;

PROCEDURE ModificationTime () =
  VAR s: File.Status;  p := In("t.txt");
  BEGIN
    TRY
      FS.CreateDirectory(Dir);
      WriteFile(p, "time");
      FS.SetModificationTime(p, 1.0D9);
      s := FS.Status(p);
      Line("time " & Fmt.Int(ROUND(s.modificationTime)));
      FS.SetModificationTime(p, 1234567890.5D0);
      s := FS.Status(p);
      Line("time " & Fmt.LongReal(s.modificationTime, Fmt.Style.Fix, 1));
    EXCEPT
      OSError.E => Failed("set time");
    END;
    TRY FS.SetModificationTime(In("missing"), 1.0D9) EXCEPT OSError.E => Failed("set time of a missing file") END;
    TRY
      FS.DeleteFile(p);
      FS.DeleteDirectory(Dir);
    EXCEPT
      OSError.E => Failed("delete");
    END;
  END ModificationTime;

PROCEDURE All () =
  BEGIN
    Line("-- directories");
    Directories();
    Line("-- files");
    Files();
    Line("-- absolute names");
    Absolute();
    Line("-- listing");
    Listing();
    Line("-- handles");
    Handles();
    Line("-- deleting");
    Deleting();
    Line(Fmt.Int(failures) & " errors");
  END All;

BEGIN
  IF Params.Count > 1 THEN ModificationTime() ELSE All() END;
END Main.
