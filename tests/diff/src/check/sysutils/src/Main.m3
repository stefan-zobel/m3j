(* The package sysutils: the procedures on texts of TextUtils and
   TextReadingUtils, FastLex, environments (ProcessEnv, EnvUtils), the
   hexadecimal form of fingerprints, PathRepr and OSSpecials, files and
   directories (FSUtils, DirStack), messages to writers (MsgIF, MsgX), a
   thread that copies a reader to a writer (ConnectRdWr), and child
   processes (System: Execute, Exec with redirections, ExecuteList,
   RdExecute, PipeTo, Filter, Wait and Hostname).

   The children are findstr.exe and sort.exe of Windows, named by their
   full pathnames (on Windows, System puts each parameter in quotes, which
   these programs remove, but not the commands of cmd.exe). The program works in a directory "sysdir" in the current
   directory, which it creates and deletes. *)

MODULE Main;

IMPORT ASCII, ConnectRdWr, DirStack, Env, EnvUtils, FastLex, FileWr, Fingerprint,
       FingerprintFmt, Fmt, FSUtils, IO, MsgIF, MsgX, OSError, OSSpecials,
       Pathname, PathRepr, Process, ProcessEnv, Rd, Stdio, System, Text,
       TextRd, TextReadingUtils, TextSeq, TextTextSeqTbl, TextTextTbl,
       TextUtils, TextWr, Thread, Wr;

<*FATAL Rd.Failure, Rd.EndOfFile, Wr.Failure, Thread.Alerted, OSError.E, FSUtils.E,
        System.Error*>

CONST Dir = "sysdir";

VAR
  cmd := Env.Get ("ComSpec");
  sort := Env.Get ("SystemRoot") & "\\System32\\sort.exe";
  findstr := Env.Get ("SystemRoot") & "\\System32\\findstr.exe";

PROCEDURE Line (t: TEXT) =
  BEGIN
    IO.Put (t & "\n");
  END Line;

PROCEDURE Flush () =
  BEGIN
    Wr.Flush (Stdio.stdout);
  END Flush;

PROCEDURE Q (t: TEXT): TEXT =
  (* t in brackets, its newlines, returns and tabs shown as \n, \r, \t. *)
  VAR r := "[";  c: CHAR;
  BEGIN
    IF t = NIL THEN RETURN "NIL" END;
    FOR i := 0 TO Text.Length (t) - 1 DO
      c := Text.GetChar (t, i);
      IF c = '\n' THEN
        r := r & "\\n";
      ELSIF c = '\r' THEN
        r := r & "\\r";
      ELSIF c = '\t' THEN
        r := r & "\\t";
      ELSE
        r := r & Text.FromChar (c);
      END;
    END;
    RETURN r & "]";
  END Q;

PROCEDURE B (b: BOOLEAN): TEXT =
  BEGIN
    RETURN Fmt.Bool (b);
  END B;

PROCEDURE Seq (s: TextSeq.T): TEXT =
  VAR r := Fmt.Int (s.size ()) & ":";
  BEGIN
    FOR i := 0 TO s.size () - 1 DO r := r & " " & Q (s.get (i)) END;
    RETURN r;
  END Seq;

PROCEDURE Sorted (READONLY a: ARRAY OF TEXT): TEXT =
  VAR c := NEW (REF ARRAY OF TEXT, NUMBER (a));  r := Fmt.Int (NUMBER (a)) & ":";
  BEGIN
    c^ := a;
    TextUtils.Sort (c^);
    FOR i := 0 TO LAST (c^) DO r := r & " " & Q (c[i]) END;
    RETURN r;
  END Sorted;

PROCEDURE SortedSeq (s: TextSeq.T): TEXT =
  BEGIN
    RETURN Sorted (TextUtils.TextSeqToArray (s)^);
  END SortedSeq;

PROCEDURE NewSeq (READONLY a: ARRAY OF TEXT): TextSeq.T =
  VAR s := NEW (TextSeq.T).init ();
  BEGIN
    FOR i := 0 TO LAST (a) DO s.addhi (a[i]) END;
    RETURN s;
  END NewSeq;

PROCEDURE Clean (t: TEXT): TEXT =
  (* t without carriage returns, spaces at the ends of lines and its final
     newline. *)
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

(* ------------------------------------------------------------- texts *)

PROCEDURE Texts () =
  VAR
    tbl: TextTextTbl.T := NEW (TextTextTbl.Default).init ();
    a := ARRAY [0 .. 4] OF TEXT {"pear", "apple", "Fig", "banana", "apple"};
  BEGIN
    Line ("SkipLeft " & Q (TextUtils.SkipLeft ("  ab c  ")) & " SkipRight "
            & Q (TextUtils.SkipRight ("  ab c  ")) & " Compress "
            & Q (TextUtils.Compress (" \t ab c \n")) & Q (TextUtils.Compress ("   "))
            & Q (TextUtils.Compress ("x")));
    Line ("SubstChar " & Q (TextUtils.SubstChar ("a/b/c", '/', '\\'))
            & Q (TextUtils.SubstChar ("same", 's', 's')) & " SubstChars "
            & Q (TextUtils.SubstChars ("hello world", ARRAY OF CHAR {'l', 'o'},
                                       ARRAY OF CHAR {'L', '0'})));
    Line ("Substitute " & Q (TextUtils.Substitute ("aXbXcX", "X", "--"))
            & Q (TextUtils.Substitute ("aXbXcX", "X", "", 2))
            & Q (TextUtils.Substitute ("abc", "Z", "z")));
    Line ("RemoveChars " & Q (TextUtils.RemoveChars (" a b\tc\n"))
            & " Squeeze " & Q (TextUtils.Squeeze ("a\n\n\n\n\nb\nc\n\n\n")));
    Line ("MemberOfTextSeq " & B (TextUtils.MemberOfTextSeq (NewSeq (a), "Fig"))
            & " " & B (TextUtils.MemberOfTextSeq (NewSeq (a), "fig")));
    Line ("TextSeqToText " & Q (TextUtils.TextSeqToText (NewSeq (a))) & " "
            & Q (TextUtils.TextSeqToText (NewSeq (a), ", ", 14)) & " "
            & Q (TextUtils.TextSeqToText (NewSeq (ARRAY OF TEXT {"x", NIL, "y"}), "+"))
            & Q (TextUtils.TextSeqToText (NewSeq (ARRAY OF TEXT {"one"})))
            & Q (TextUtils.TextSeqToText (NEW (TextSeq.T).init ())));
    Line ("AddPrefix " & Seq (TextUtils.AddPrefix (NewSeq (ARRAY OF TEXT {"a", "b"}), "pre-"))
            & " AddSuffix " & Seq (TextUtils.AddSuffix (NewSeq (ARRAY OF TEXT {"a"}), ".m3")));
    Line ("Split " & Seq (TextUtils.Split ("a,,b,c", ",")) & " | "
            & Seq (TextUtils.Split ("abc", ",")) & " | "
            & Seq (TextUtils.Split ("x::y::", "::")));
    Line ("Tokenize " & Seq (TextUtils.Tokenize ("  a b\t\tc ")) & " | "
            & Seq (TextUtils.Tokenize ("a,,b", ASCII.Set {','}, FALSE)));
    Line ("Lower " & Q (TextUtils.Lower ("MiXeD 1")) & Q (TextUtils.Lower ("low"))
            & Q (TextUtils.Lower (NIL)) & " Upper " & Q (TextUtils.Upper ("MiXeD 1")));
    Line ("Pos " & Fmt.Int (TextUtils.Pos ("Hello World", "world", FALSE)) & " "
            & Fmt.Int (TextUtils.Pos ("Hello World", "world")) & " "
            & Fmt.Int (TextUtils.Pos ("abc", "")) & " Contains "
            & B (TextUtils.Contains ("Hello", "ell")) & " CountChar "
            & Fmt.Int (TextUtils.CountChar ("Banana", 'a')) & " "
            & Fmt.Int (TextUtils.CountChar ("BANANA", 'a', FALSE)));
    Line ("StartsWith " & B (TextUtils.StartsWith ("Hello", "he", FALSE)) & " "
            & B (TextUtils.StartsWith ("Hello", "he")) & " "
            & B (TextUtils.StartsWith ("He", "Hello")) & " EndsWith "
            & B (TextUtils.EndsWith ("Hello", "LO", FALSE)) & " "
            & B (TextUtils.EndsWith ("Hello", "llo")));
    Line ("BoolVal " & B (TextUtils.BoolVal ("yes")) & " " & B (TextUtils.BoolVal (" Off "))
            & " " & B (TextUtils.BoolVal ("TRUE")) & " " & B (TextUtils.BoolVal ("maybe", TRUE)));
    Line ("Sort " & Sorted (a));
    EVAL tbl.put ("HOME", "/h");
    EVAL tbl.put ("USER", "u");
    Line ("SubstEnvVars " & Q (TextUtils.SubstEnvVars ("home=$HOME, ${USER}x $$ $1 $NONE end", tbl)));
    EVAL tbl.put ("a", "1");
    EVAL tbl.put ("c", "");
    EVAL tbl.put ("r", "<{:a}>");
    TRY
      Line ("SubstituteVariables "
              & Q (TextUtils.SubstituteVariables (
                       "x={:a} y={?b} z={!c:def} w={:d?a} v={:e?zz:const} r={:r}", tbl)));
      Line ("SubstituteVariables " & Q (TextUtils.SubstituteVariables ("{:missing}", tbl)));
    EXCEPT
      TextUtils.Error (e) => Line ("TextUtils.Error " & Q (e));
    END;
    TRY
      EVAL TextUtils.SubstituteVariables ("{!c}", tbl);
    EXCEPT
      TextUtils.Error (e) => Line ("TextUtils.Error " & Q (e));
    END;
    TRY
      EVAL TextUtils.SubstituteVariables ("open {:a", tbl);
    EXCEPT
      TextUtils.Error (e) => Line ("TextUtils.Error " & Q (e));
    END;
  END Texts;

PROCEDURE Reading () =
  VAR rd := TextRd.New ("  token1 'quoted string' \"dq\" rest of line\n  \"str2\" next");
  BEGIN
    Line ("GetToken " & Q (TextReadingUtils.GetToken (rd)));
    Line ("GetTokenOrString " & Q (TextReadingUtils.GetTokenOrString (rd)));
    Line ("GetTokenOrString " & Q (TextReadingUtils.GetTokenOrString (rd)));
    Line ("GetStringOrLine " & Q (TextReadingUtils.GetStringOrLine (rd)));
    Line ("GetString " & Q (TextReadingUtils.GetString (rd)));
    Line ("GetToken " & Q (TextReadingUtils.GetToken (rd)));
    TRY
      Line ("GetToken " & Q (TextReadingUtils.GetToken (rd)));
    EXCEPT
      Rd.EndOfFile => Line ("GetToken: EndOfFile");
    END;
    Line ("Tokenize " & Seq (TextReadingUtils.Tokenize ("a  b\tc ")));
    rd := TextRd.New ("abc123   def");
    Line ("FastLex.Scan " & Q (FastLex.Scan (rd, SET OF CHAR {'a' .. 'z'})));
    Line ("FastLex.Scan " & Q (FastLex.Scan (rd, SET OF CHAR {'0' .. '9'})));
    FastLex.Skip (rd);
    Line ("FastLex.Scan " & Q (FastLex.Scan (rd)) & " EOF " & B (Rd.EOF (rd)));
  END Reading;

(* ------------------------------------------------------- environments *)

PROCEDURE Environments () =
  VAR
    env: ProcessEnv.T := NEW (TextTextTbl.Default).init ();
    cur := ProcessEnv.Current ();
    copy: ProcessEnv.T;
    t: TEXT;
    parsed: EnvUtils.T;
  BEGIN
    ProcessEnv.Set (env, "A", "1");
    ProcessEnv.Set (env, "B", "two words");
    ProcessEnv.Set (env, "C", "3");
    ProcessEnv.Delete (env, "C");
    Line ("ProcessEnv " & B (ProcessEnv.Defined (env, "A")) & " "
            & B (ProcessEnv.Defined (env, "C")) & " " & Q (ProcessEnv.Value (env, "B"))
            & " " & Q (ProcessEnv.Value (env, "C")));
    Line ("Names " & Sorted (ProcessEnv.Names (env)^));
    Line ("SystemRepr " & Sorted (ProcessEnv.SystemRepr (env)^));
    copy := ProcessEnv.Copy (env);
    ProcessEnv.Set (copy, "A", "changed");
    parsed := NEW (TextTextTbl.Default).init ();
    ProcessEnv.Set (parsed, "D", "4");
    ProcessEnv.Add (copy, parsed);
    Line ("Copy " & Q (ProcessEnv.Value (copy, "A")) & " original "
            & Q (ProcessEnv.Value (env, "A")) & " Add " & Sorted (ProcessEnv.Names (copy)^));
    (* the names of the environment in any case (a shell may change it);
       not their number, which on Windows differs: the environment of cm3
       has the hidden variables of the drives ("=C:"), which Java's has not *)
    WITH names = ProcessEnv.Names (cur)^ DO
      t := NIL;
      FOR i := 0 TO LAST (names) DO
        IF Text.Equal (TextUtils.Lower (names[i]), "comspec") THEN
          t := ProcessEnv.Value (cur, names[i]);
        END;
      END;
      Line ("Current ComSpec " & B (t # NIL AND Text.Equal (t, cmd)) & " Names "
              & B (NUMBER (names) > 3));
    END;
    TRY
      ProcessEnv.Set (env, "B", "two");
      t := EnvUtils.ToText (env, ' ');
      parsed := EnvUtils.FromText (t);
      Line ("EnvUtils " & Fmt.Int (Text.Length (t)) & " "
              & Sorted (ProcessEnv.SystemRepr (parsed)^));
      parsed := EnvUtils.FromText ("(@env-start X 1\n Y two env-end@)");
      Line ("EnvUtils.FromText " & Sorted (ProcessEnv.SystemRepr (parsed)^));
      parsed := EnvUtils.FromText ("X 1");
    EXCEPT
      EnvUtils.Error (e) => Line ("EnvUtils.Error " & Q (e));
    END;
  END Environments;

PROCEDURE Fingerprints () =
  VAR fp := Fingerprint.FromText ("sysutils");  fp2: Fingerprint.T;  h: TEXT;
  BEGIN
    h := FingerprintFmt.Hex (fp);
    Line ("FingerprintFmt.Hex " & h & " Scan " & B (FingerprintFmt.Scan (h, fp2))
            & " " & B (fp2 = fp) & " " & B (FingerprintFmt.Scan ("xyz", fp2)));
  END Fingerprints;

PROCEDURE Tables () =
  VAR
    t := NEW (TextTextSeqTbl.Default).init ();
    s: TextSeq.T;
  BEGIN
    EVAL t.put ("k", NewSeq (ARRAY OF TEXT {"v1", "v2"}));
    Line ("TextTextSeqTbl " & B (t.get ("k", s)) & " " & Seq (s) & " " & B (t.get ("z", s)));
  END Tables;

PROCEDURE Paths () =
  BEGIN
    Line ("PathRepr " & Q (PathRepr.Native ("a/b\\c")) & " " & Q (PathRepr.Posix ("a/b\\c"))
            & " " & Q (PathRepr.Win32 ("a/b\\c")) & " " & Q (PathRepr.PathSep) & " "
            & Q (Text.FromChar (PathRepr.PathSepChar)) & " " & Q (PathRepr.Root ("C:\\x\\y"))
            & " " & Q (PathRepr.Root ("x\\y")) & " RootDir " & B (PathRepr.RootDir # NIL));
    Line ("OSSpecials " & Q (OSSpecials.LineBreak) & " "
            & Q (OSSpecials.QuotedProcessArgument ("a b")));
  END Paths;

(* ---------------------------------------------------- files, directories *)

PROCEDURE P (name: TEXT): TEXT =
  BEGIN
    RETURN Dir & "\\" & name;
  END P;

PROCEDURE Files () =
  VAR c: TEXT;
  BEGIN
    TRY
      FSUtils.Mkdir (P ("sub\\deep"));
      FSUtils.PutFile (P ("a.txt"), "hello\nworld\n");
      FSUtils.PutFile (P ("run.bat"), "@echo off\r\n");
      Line ("FileContents " & Q (FSUtils.FileContents (P ("a.txt"))));
      Line ("Exists " & B (FSUtils.Exists (P ("a.txt"))) & " " & B (FSUtils.Exists (P ("no.txt")))
              & " IsFile " & B (FSUtils.IsFile (P ("a.txt"))) & " " & B (FSUtils.IsFile (P ("sub")))
              & " IsDir " & B (FSUtils.IsDir (P ("sub"))) & " " & B (FSUtils.IsDir (P ("a.txt"))));
      Line ("IsReadable " & B (FSUtils.IsReadable (P ("a.txt"))) & " "
              & B (FSUtils.IsReadable (P ("no.txt"))) & " " & B (FSUtils.IsReadable (P ("sub")))
              & " IsWritable " & B (FSUtils.IsWritable (P ("a.txt"))) & " "
              & B (FSUtils.IsWritable (P ("no.txt"))) & " IsExecutable "
              & B (FSUtils.IsExecutable (P ("a.txt"))) & " " & B (FSUtils.IsExecutable (P ("run.bat")))
              & " " & B (FSUtils.IsExecutable (P ("no.exe"))) & " " & B (FSUtils.IsExecutable (cmd)));
      Line ("no.txt still missing " & B (NOT FSUtils.Exists (P ("no.txt"))));
      FSUtils.Cp (P ("a.txt"), P ("sub\\b.txt"));
      FSUtils.Touch (P ("t"));
      FSUtils.TouchFile (P ("sub\\t2"));
      FSUtils.MakeDir (P ("made\\here"));
      Line ("SubFiles " & SortedSeq (FSUtils.SubFiles (Dir, TRUE)) & " SubDirs "
              & SortedSeq (FSUtils.SubDirs (Dir, TRUE)));
      Line ("SubFiles " & SortedSeq (FSUtils.SubFiles (P ("sub"))));
      Line ("copy " & Q (FSUtils.FileContents (P ("sub\\b.txt"))) & " touched "
              & Q (FSUtils.FileContents (P ("t"))));
      c := FSUtils.CanonicalPathname (P ("sub\\..\\a.txt"));
      Line ("CanonicalPathname absolute " & B (Pathname.Absolute (c)) & " "
              & Q (Pathname.Last (c)) & " " & Q (Pathname.Last (Pathname.Prefix (c))));
      FSUtils.Rm (P ("sub\\b.txt"));
      Line ("Rm " & B (FSUtils.Exists (P ("sub\\b.txt"))));
    EXCEPT
      FSUtils.E (e) => Line ("FSUtils.E " & Q (e));
    END;
    TRY
      FSUtils.Rm (P ("no.txt"));
      Line ("Rm no.txt: no error");
    EXCEPT
      FSUtils.E => Line ("Rm no.txt: FSUtils.E");
    END;
    TRY
      FSUtils.Rmdir (P ("sub"));
      Line ("Rmdir sub: no error");
    EXCEPT
      FSUtils.E => Line ("Rmdir sub: FSUtils.E");
    END;
    TRY
      EVAL FSUtils.FileContents (P ("no.txt"));
      Line ("FileContents no.txt: no error");
    EXCEPT
      FSUtils.E => Line ("FileContents no.txt: FSUtils.E");
    END;
  END Files;

PROCEDURE Directories () =
  BEGIN
    TRY
      DirStack.PushDir (Dir);
      Line ("PushDir " & Q (Pathname.Last (DirStack.GetWorkingDir ())) & " a.txt "
              & B (FSUtils.Exists ("a.txt")));
      DirStack.PushDir ("sub");
      Line ("PushDir " & Q (Pathname.Last (DirStack.GetWorkingDir ())) & " t2 "
              & B (FSUtils.IsFile ("t2")));
      DirStack.PopDir ();
      DirStack.PopDir ();
      Line ("PopDir " & B (FSUtils.IsDir (Dir)));
      DirStack.SetWorkingDir ("no-such-dir");
    EXCEPT
      DirStack.Error (e) => Line ("DirStack.Error " & Q (e));
    END;
  END Directories;

(* ------------------------------------------------------------ messages *)

PROCEDURE Messages () =
  VAR wr := TextWr.New ();  m := MsgIF.New (wr, dLevel := 0, vLevel := 2);
  BEGIN
    MsgX.V (m, "verbose 1");
    MsgX.V (m, "verbose 3", level := 3);
    MsgX.D (m, "debug");
    MsgX.Error (m, "an error");
    MsgX.Warning2 (m, "Proc", "a warning");
    MsgX.T (m, "trace");
    MsgX.V (NIL, "to nothing");
    Line ("MsgX " & Q (TextWr.ToText (wr)));
  END Messages;

PROCEDURE Connections () =
  (* The thread closes the writer at the end. *)
  VAR
    wr := FileWr.Open (P ("conn.txt"));
    cl := NEW (ConnectRdWr.T).init (TextRd.New ("copied by a thread"), wr, "test");
    th := Thread.Fork (cl);
  BEGIN
    EVAL Thread.Join (th);
    Line ("ConnectRdWr " & Q (FSUtils.FileContents (P ("conn.txt"))));
  END Connections;

(* ----------------------------------------------------------- processes *)

PROCEDURE Processes () =
  VAR
    h := System.Hostname ();
    rd: Rd.T;
    wr: Wr.T;
    p: Process.T;
    wd := Process.GetWorkingDirectory ();
    a := P ("a.txt");
  BEGIN
    (* cm3 on Windows gives "amnesiac": its gethostname fails in a program
       that has not started Windows Sockets; m3j gives the name *)
    Line ("Hostname " & B (Text.Length (h) > 0));
    FSUtils.PutFile (P ("args.txt"), "/c:world\n");
    TRY
      Flush ();
      Line ("Execute: " & Fmt.Int (System.Execute (findstr & " /c:world " & a)));
      Line ("Execute no match: " & Fmt.Int (System.Execute (findstr & " zzz " & a)));
      Line ("Exec > file: "
              & Fmt.Int (System.Exec (sort, NewSeq (ARRAY OF TEXT {"/r", a, ">", P ("out.txt")}),
                                      wd := wd)) & " " & Q (Clean (FSUtils.FileContents (P ("out.txt")))));
      Line ("Exec >> file: "
              & Fmt.Int (System.Exec (findstr, NewSeq (ARRAY OF TEXT {"hello", a, ">>", P ("out.txt")}),
                                      wd := wd)) & " " & Q (Clean (FSUtils.FileContents (P ("out.txt")))));
      Line ("Exec < file: "
              & Fmt.Int (System.Exec (sort, NewSeq (ARRAY OF TEXT {"<", P ("out.txt"), ">", P ("sorted.txt")}),
                                      wd := wd)) & " " & Q (Clean (FSUtils.FileContents (P ("sorted.txt")))));
      Line ("Exec 2> file: "
              & Fmt.Int (System.Exec (findstr, NewSeq (ARRAY OF TEXT {"x", P ("no.txt"), "2>", P ("err.txt")}),
                                      wd := wd)) & " "
              & B (Text.Length (FSUtils.FileContents (P ("err.txt"))) > 0));
      Line ("Exec &> file: "
              & Fmt.Int (System.Exec (findstr, NewSeq (ARRAY OF TEXT {"o", a, P ("no.txt"), "&>", P ("both.txt")}),
                                      wd := wd)) & " "
              & B (TextUtils.Contains (FSUtils.FileContents (P ("both.txt")), "world")));
      Line ("Exec in wd: "
              & Fmt.Int (System.Exec (sort, NewSeq (ARRAY OF TEXT {"/r", "a.txt", ">", "typed.txt"}),
                                      wd := wd & "\\" & Dir)) & " "
              & Q (Clean (FSUtils.FileContents (P ("typed.txt")))));
      Line ("Exec $< file: "
              & Fmt.Int (System.Exec (findstr, NewSeq (ARRAY OF TEXT {"$<", P ("args.txt"), ">", a, ">", P ("found.txt")}),
                                      wd := wd)) & " "
              & Q (Clean (FSUtils.FileContents (P ("found.txt")))));
      Flush ();
      Line ("ExecuteList ||: " & Fmt.Int (System.ExecuteList (findstr & " zzz " & a & " || "
                                                                  & findstr & " /c:hello " & a)));
      Line ("ExecuteList &&: " & Fmt.Int (System.ExecuteList (findstr & " zzz " & a & " && "
                                                                  & findstr & " /c:hello " & a)));
      Line ("ExecuteList ;: " & Fmt.Int (System.ExecuteList (findstr & " world " & a & " ; "
                                                                 & findstr & " zzz " & a)));
      Line ("ExecuteList |: " & Fmt.Int (System.ExecuteList (findstr & " o " & a & " | "
                                                                 & sort & " /r")));
      Line ("ExecuteList > file: " & Fmt.Int (System.ExecuteList (sort & " /r " & a & " > "
                                                                     & P ("list.txt")))
              & " " & Q (Clean (FSUtils.FileContents (P ("list.txt")))));

      p := System.RdExecute (findstr & " /n o " & a, rd);
      Line ("RdExecute " & Q (Clean (Rd.GetText (rd, LAST (CARDINAL)))) & " "
              & Fmt.Int (System.Wait (p)));
      Rd.Close (rd);

      p := System.RdExecute (findstr & " /n o a.txt", rd, wd := Dir);
      Line ("RdExecute in wd " & Q (Clean (Rd.GetText (rd, LAST (CARDINAL)))) & " "
              & Fmt.Int (System.Wait (p)));
      Rd.Close (rd);

      p := System.Filter (sort, rd, wr);
      Wr.PutText (wr, "pear\r\napple\r\nfig\r\n");
      Wr.Close (wr);
      Line ("Filter " & Q (Clean (Rd.GetText (rd, LAST (CARDINAL)))) & " " & Fmt.Int (Process.Wait (p)));
      Rd.Close (rd);

      Flush ();
      p := System.PipeTo (sort, wr);
      Wr.PutText (wr, "zebra\r\nant\r\n");
      Wr.Close (wr);
      Line ("PipeTo " & Fmt.Int (Process.Wait (p)));
    EXCEPT
      System.ExecuteError (e) => Line ("System.ExecuteError " & Q (e));
    END;
    TRY
      EVAL System.Execute ("no-such-program-sysutils");
      Line ("Execute no-such-program: no error");
    EXCEPT
      System.ExecuteError => Line ("Execute no-such-program: ExecuteError");
    END;
    TRY
      EVAL System.Exec (sort, NewSeq (ARRAY OF TEXT {">"}));
      Line ("Exec >: no error");
    EXCEPT
      System.ExecuteError (e) => Line ("Exec >: " & Q (e));
    END;
    TRY
      EVAL System.Exec (sort, NewSeq (ARRAY OF TEXT {"<", P ("no.txt")}), wd := wd);
      Line ("Exec < no.txt: no error");
    EXCEPT
      System.ExecuteError (e) => Line ("Exec < no.txt: " & Q (TextUtils.Split (e, ":").get (0)));
    END;
  END Processes;

BEGIN
  Texts ();
  Reading ();
  Environments ();
  Fingerprints ();
  Tables ();
  Paths ();
  TRY FSUtils.RmRec (Dir) EXCEPT FSUtils.E => END;
  Files ();
  Directories ();
  Messages ();
  Connections ();
  Processes ();
  TRY
    FSUtils.RmRec (Dir);
    Line ("RmRec " & B (FSUtils.Exists (Dir)));
  EXCEPT
    FSUtils.E (e) => Line ("RmRec: " & Q (e));
  END;
END Main.
