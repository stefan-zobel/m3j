(* Process and Pipe: exit codes of child processes and their identifiers;
   the standard output of the program given to a child for its output and
   for its error output; pipes to the input and from the output of a
   filter, also with much data while another thread writes; a regular
   file for the output of a child (which appends to what the program
   wrote, and after which the program goes on writing at its end) and for
   the input (from the position of the handle); the environment and the
   working directory of a child; SetWorkingDirectory, after which relative
   pathnames and children start in the new directory; a pipe within the
   program; and the errors of a missing program and directory.

   The children are cmd.exe and sort.exe of Windows, named by their full
   pathnames, and their parameters have no spaces (cm3 on Windows joins
   them with spaces, without quotes). The program works in a directory
   "procdir" in the current directory, which it creates and deletes. *)

MODULE Main;

IMPORT Env, File, FileRd, FileWr, Fmt, FS, IO, OSError, Pathname, Pipe,
       Process, Rd, Stdio, Text, Thread, Wr;

<*FATAL Rd.Failure, Rd.EndOfFile, Wr.Failure, Thread.Alerted*>

CONST Dir = "procdir";

VAR
  cmd := Env.Get ("ComSpec");
  sort := Env.Get ("SystemRoot") & "\\System32\\sort.exe";

PROCEDURE Line (t: TEXT) =
  BEGIN
    IO.Put (t & "\n");
  END Line;

PROCEDURE Clean (t: TEXT): TEXT =
  (* t without carriage returns, spaces at the ends of lines (cm3 on
     Windows ends the command line with a space, which echo prints) and
     its final newline. *)
  VAR r := "";  c: CHAR;
  BEGIN
    FOR i := 0 TO Text.Length (t) - 1 DO
      c := Text.GetChar (t, i);
      IF c = '\n' OR c = '\r' THEN
        WHILE Text.Length (r) > 0 AND Text.GetChar (r, Text.Length (r) - 1) = ' ' DO
          r := Text.Sub (r, 0, Text.Length (r) - 1);
        END;
      END;
      IF c # '\r' THEN r := r & Text.FromChar (c) END;
    END;
    WHILE Text.Length (r) > 0 AND Text.GetChar (r, Text.Length (r) - 1) = ' ' DO
      r := Text.Sub (r, 0, Text.Length (r) - 1);
    END;
    WHILE Text.Length (r) > 0 AND Text.GetChar (r, Text.Length (r) - 1) = '\n' DO
      r := Text.Sub (r, 0, Text.Length (r) - 1);
    END;
    RETURN r;
  END Clean;

PROCEDURE Flat (t: TEXT): TEXT =
  (* t with its newlines shown as "|". *)
  VAR r := "";  c: CHAR;
  BEGIN
    FOR i := 0 TO Text.Length (t) - 1 DO
      c := Text.GetChar (t, i);
      IF c = '\n' THEN r := r & "|" ELSE r := r & Text.FromChar (c) END;
    END;
    RETURN r;
  END Flat;

PROCEDURE Run (prog: TEXT; READONLY params: ARRAY OF TEXT;
               env: REF ARRAY OF TEXT := NIL; wd: TEXT := NIL;
               stdin: File.T := NIL; VAR code: Process.ExitCode): TEXT
  RAISES {OSError.E} =
  (* Runs prog, its output read through a pipe; the output, cleaned. *)
  VAR hrSelf, hwChild: Pipe.T;  p: Process.T;  out: TEXT;
  BEGIN
    Pipe.Open (hr := hrSelf, hw := hwChild);
    TRY
      p := Process.Create (prog, params, env, wd, stdin, hwChild, NIL);
    FINALLY
      hwChild.close ();
    END;
    WITH rd = NEW (FileRd.T).init (hrSelf) DO
      out := Rd.GetText (rd, LAST (CARDINAL));
      Rd.Close (rd);
    END;
    code := Process.Wait (p);
    RETURN Clean (out);
  END Run;

PROCEDURE ExitCodes () RAISES {OSError.E} =
  VAR p: Process.T;
  BEGIN
    FOR i := 0 TO 4 DO
      WITH n = ARRAY [0 .. 4] OF TEXT {"0", "1", "3", "255", "65536"}[i] DO
        p := Process.Create (cmd, ARRAY OF TEXT {"/c", "exit", n});
        Line ("exit " & n & ": " & Fmt.Int (Process.Wait (p)));
      END;
    END;
    p := Process.Create (cmd, ARRAY OF TEXT {"/c", "exit", "0"});
    Line ("ids: " & Fmt.Bool (Process.GetID (p) # Process.NullID) & " "
            & Fmt.Bool (Process.GetID (p) # Process.GetMyID ()) & " "
            & Fmt.Bool (Process.GetMyID () # Process.NullID));
    EVAL Process.Wait (p);
  END ExitCodes;

PROCEDURE Inherited () RAISES {OSError.E} =
  VAR in, out, err: File.T;  p: Process.T;
  BEGIN
    Process.GetStandardFileHandles (in, out, err);
    Line ("standard handles: " & Fmt.Bool (in # NIL AND out # NIL AND err # NIL));
    (* The children print files with "type", whose output does not depend
       on how the command line ends. *)
    WITH wr = FileWr.Open (Dir & "\\msg.txt") DO
      Wr.PutText (wr, "inherited\r\nto stderr\r\n");
      Wr.Close (wr);
    END;
    Wr.Flush (Stdio.stdout);
    p := Process.Create (cmd, ARRAY OF TEXT {"/c", "type", Dir & "\\msg.txt"},
                         stdout := out);
    Line ("code " & Fmt.Int (Process.Wait (p)));
    p := Process.Create (cmd, ARRAY OF TEXT {"/c", "type", Dir & "\\msg.txt", "1>&2"},
                         stderr := out);
    Line ("code " & Fmt.Int (Process.Wait (p)));
    p := Process.Create (cmd, ARRAY OF TEXT {"/c", "type", Dir & "\\msg.txt"});
    Line ("no handles: code " & Fmt.Int (Process.Wait (p)));
  END Inherited;

PROCEDURE Filter () RAISES {OSError.E} =
  VAR hrChild, hwChild, hrSelf, hwSelf: Pipe.T;  p: Process.T;
  BEGIN
    Pipe.Open (hr := hrChild, hw := hwSelf);
    Pipe.Open (hr := hrSelf, hw := hwChild);
    p := Process.Create (sort, ARRAY OF TEXT {}, NIL, NIL, hrChild, hwChild, NIL);
    hrChild.close ();
    hwChild.close ();
    WITH wr = NEW (FileWr.T).init (hwSelf),
         rd = NEW (FileRd.T).init (hrSelf) DO
      Wr.PutText (wr, "pear\r\napple\r\nfig\r\n");
      Wr.Close (wr);
      Line ("filter: " & Flat (Clean (Rd.GetText (rd, LAST (CARDINAL)))));
      Rd.Close (rd);
    END;
    Line ("code " & Fmt.Int (Process.Wait (p)));
  END Filter;

TYPE Writer = Thread.Closure OBJECT wr: Wr.T; n: INTEGER OVERRIDES apply := WriteLines END;

PROCEDURE WriteLines (w: Writer): REFANY =
  BEGIN
    FOR i := w.n - 1 TO 0 BY -1 DO
      Wr.PutText (w.wr, "line" & Fmt.Pad (Fmt.Int (i), 6, '0') & "\r\n");
    END;
    Wr.Close (w.wr);
    RETURN NIL;
  END WriteLines;

PROCEDURE Big () RAISES {OSError.E} =
  CONST N = 20000;
  VAR
    hrChild, hwChild, hrSelf, hwSelf: Pipe.T;  p: Process.T;  t: Thread.T;
    count := 0;  sum := 0;  ordered := TRUE;  last := "";  l: TEXT;
  BEGIN
    Pipe.Open (hr := hrChild, hw := hwSelf);
    Pipe.Open (hr := hrSelf, hw := hwChild);
    p := Process.Create (sort, ARRAY OF TEXT {}, NIL, NIL, hrChild, hwChild, NIL);
    hrChild.close ();
    hwChild.close ();
    t := Thread.Fork (NEW (Writer, wr := NEW (FileWr.T).init (hwSelf), n := N));
    WITH rd = NEW (FileRd.T).init (hrSelf) DO
      WHILE NOT Rd.EOF (rd) DO
        l := Clean (Rd.GetLine (rd));
        IF Text.Compare (last, l) >= 0 THEN ordered := FALSE END;
        last := l;
        INC (count);
        INC (sum, Text.Length (l));
      END;
      Rd.Close (rd);
    END;
    EVAL Thread.Join (t);
    Line ("big: " & Fmt.Int (count) & " lines, " & Fmt.Int (sum) & " chars, ordered "
            & Fmt.Bool (ordered) & ", last " & last);
    Line ("code " & Fmt.Int (Process.Wait (p)));
  END Big;

PROCEDURE PutBytes (f: File.T; t: TEXT) RAISES {OSError.E} =
  VAR b := NEW (REF ARRAY OF File.Byte, Text.Length (t));
  BEGIN
    FOR i := 0 TO LAST (b^) DO b[i] := ORD (Text.GetChar (t, i)) END;
    f.write (b^);
  END PutBytes;

PROCEDURE ReadFile (path: TEXT): TEXT RAISES {OSError.E} =
  VAR r: TEXT;
  BEGIN
    WITH rd = FileRd.Open (path) DO
      r := Rd.GetText (rd, LAST (CARDINAL));
      Rd.Close (rd);
    END;
    RETURN Clean (r);
  END ReadFile;

PROCEDURE Files () RAISES {OSError.E} =
  VAR f: File.T;  p: Process.T;  code: Process.ExitCode;  out: TEXT;
      b: ARRAY [0 .. 1] OF File.Byte;
  BEGIN
    f := FS.OpenFile (Dir & "\\out.txt");
    PutBytes (f, "first\r\n");
    p := Process.Create (cmd, ARRAY OF TEXT {"/c", "echo", "middle"}, stdout := f);
    Line ("code " & Fmt.Int (Process.Wait (p)));
    PutBytes (f, "last\r\n");
    f.close ();
    Line ("out.txt: " & Flat (ReadFile (Dir & "\\out.txt")));

    f := FS.OpenFile (Dir & "\\in.txt");
    PutBytes (f, "c\r\nb\r\na\r\n");
    f.close ();
    f := FS.OpenFileReadonly (Dir & "\\in.txt");
    EVAL f.read (b);
    out := Run (sort, ARRAY OF TEXT {}, stdin := f, code := code);
    f.close ();
    Line ("input from position 2: " & Flat (out) & ", code " & Fmt.Int (code));
  END Files;

PROCEDURE EnvAndDir () RAISES {OSError.E} =
  VAR code: Process.ExitCode;  env := NEW (REF ARRAY OF TEXT, 2);  out: TEXT;
  BEGIN
    env[0] := "M3TEST=forty-two";
    env[1] := "SystemRoot=" & Env.Get ("SystemRoot");
    out := Run (cmd, ARRAY OF TEXT {"/c", "echo", "%M3TEST%"}, env := env, code := code);
    Line ("env: " & out & ", code " & Fmt.Int (code));
    out := Run (cmd, ARRAY OF TEXT {"/c", "echo", "%M3TEST%"}, code := code);
    Line ("own env: " & out & ", code " & Fmt.Int (code));
    out := Run (cmd, ARRAY OF TEXT {"/c", "cd"}, wd := Dir, code := code);
    Line ("wd: " & Pathname.Last (out) & ", code " & Fmt.Int (code));
  END EnvAndDir;

PROCEDURE WorkingDirectory () RAISES {OSError.E} =
  VAR old, now, out: TEXT;  code: Process.ExitCode;
  BEGIN
    old := Process.GetWorkingDirectory ();
    Process.SetWorkingDirectory (Dir);
    now := Process.GetWorkingDirectory ();
    Line ("now in: " & Pathname.Last (now) & ", parent the old one: "
            & Fmt.Bool (Text.Equal (Pathname.Prefix (now), old)));
    WITH wr = FileWr.Open ("rel.txt") DO
      Wr.PutText (wr, "relative\r\n");
      Wr.Close (wr);
    END;
    Line ("absolute: " & Fmt.Bool (Text.Equal (FS.GetAbsolutePathname ("rel.txt"),
                                               Pathname.Join (now, "rel.txt", NIL))));
    out := Run (cmd, ARRAY OF TEXT {"/c", "cd"}, code := code);
    Line ("child in: " & Pathname.Last (out));
    out := Run (cmd, ARRAY OF TEXT {"/c", "type", "rel.txt"}, code := code);
    Line ("child reads: " & out & ", code " & Fmt.Int (code));
    Process.SetWorkingDirectory ("..");
    Line ("back: " & Fmt.Bool (Text.Equal (Process.GetWorkingDirectory (), old)));
    Line ("rel.txt there: " & ReadFile (Dir & "\\rel.txt"));
  END WorkingDirectory;

PROCEDURE InProgram () RAISES {OSError.E} =
  VAR hr, hw: Pipe.T;
  BEGIN
    Pipe.Open (hr, hw);
    Line ("pipe types: " & Fmt.Bool (hr.status ().type = Pipe.FileType) & " "
            & Fmt.Bool (hw.status ().type = Pipe.FileType));
    WITH wr = NEW (FileWr.T).init (hw) DO
      Wr.PutText (wr, "through the pipe");
      Wr.Close (wr);
    END;
    WITH rd = NEW (FileRd.T).init (hr) DO
      Line ("read: " & Rd.GetText (rd, LAST (CARDINAL)) & ", eof "
              & Fmt.Bool (Rd.EOF (rd)));
      Rd.Close (rd);
    END;
  END InProgram;

PROCEDURE Errors () =
  VAR p: Process.T;
  BEGIN
    TRY
      p := Process.Create ("no-such-program-of-m3j", ARRAY OF TEXT {});
      Line ("missing program: created");
    EXCEPT
      OSError.E => Line ("missing program: OSError.E");
    END;
    TRY
      p := Process.Create (cmd, ARRAY OF TEXT {"/c", "exit"}, wd := "no-such-dir-of-m3j");
      Line ("missing wd: created");
    EXCEPT
      OSError.E => Line ("missing wd: OSError.E");
    END;
    TRY
      Process.SetWorkingDirectory ("no-such-dir-of-m3j");
      Line ("SetWorkingDirectory: no error");
    EXCEPT
      OSError.E => Line ("SetWorkingDirectory: OSError.E");
    END;
  END Errors;

PROCEDURE Clear () =
  BEGIN
    FOR i := 0 TO 3 DO
      TRY
        FS.DeleteFile (Dir & "\\" & ARRAY [0 .. 3] OF TEXT {"out.txt", "in.txt", "rel.txt",
                                                           "msg.txt"}[i]);
      EXCEPT
        OSError.E =>
      END;
    END;
    TRY FS.DeleteDirectory (Dir) EXCEPT OSError.E => END;
  END Clear;

BEGIN
  Clear ();
  TRY
    FS.CreateDirectory (Dir);
    ExitCodes ();
    Inherited ();
    Filter ();
    Big ();
    Files ();
    EnvAndDir ();
    WorkingDirectory ();
    InProgram ();
    Errors ();
  EXCEPT
    OSError.E => Line ("unexpected OSError.E");
  END;
  Clear ();
END Main.
