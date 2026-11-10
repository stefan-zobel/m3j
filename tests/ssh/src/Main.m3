(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The program that tools/ssh_test.py runs: it answers requests on the
   standard input, one per line, with one line each, "ok ..." or
   "error ATOM TEXT" for an "SSH.Error", "Rd.Failure" or "Wr.Failure".
   Texts in requests and answers are groups of four hexadecimal digits, the
   UTF-16 code units of their characters, or "-" for "NIL". Data are given
   as "-" (none), "hHEX" (the bytes in hexadecimal) or "pN" (N bytes of the
   pattern (7i + i DIV 256) MOD 256); of data that come back the answer
   shows the length, an FNV-1a hash of 32 bits and the first 1000 bytes in
   hexadecimal ("-" if none).

     connect PORT USER PASSWORD KEYFILE KEYTEXTFILE PASSPHRASE CHECK
             KNOWNHOSTS FINGERPRINT TIMEOUT KEEPALIVE
                           CHECK is k, n or f; ok FINGERPRINT
     hostkey               ok FINGERPRINT
     close                 ends the session
     run MAXOUT TIMEOUT INPUT COMMAND
                           ok STATUS OUT ERR (length, hash, bytes each)
     proc MODE MAXBUF INPUT COMMAND
                           a started command: seq reads its outputs one
                           after the other, par in two threads, outonly
                           closes stderr first, noread reads nothing;
                           ok STATUS SIGNAL OUT ERR (length and hash);
                           nowait reads the output and does not wait:
                           ok OUT
     alertrun SECONDS COMMAND
     alertread SECONDS COMMAND
     alertwait SECONDS COMMAND
                           alerts the thread in "run", in a read of the
                           output or in "wait" after SECONDS: ok alerted
     many N                N threads run a command at the same time
     pause SECONDS

     sopen, sclose         an SFTP.T on the session
     sget PATH OFFSET      ok DATA (length and hash)
     sput PATH MODE APPEND DATA
     sstat PATH, slstat PATH
                           ok KIND SIZE MODE UID GID ATIME MTIME
     slist DIR             ok N NAME:KIND:SIZE...
     smkdir PATH MODE, srmdir PATH, sremove PATH, srename FROM TO,
     ssymlink TARGET LINK, sreadlink PATH, srealpath PATH,
     schmod PATH MODE, stimes PATH ATIME MTIME
     sbusy PATH            an operation while PATH is open for reading;
                           ok ATOM
     salert PATH SECONDS   alerts a reader of PATH after SECONDS *)

MODULE Main;

IMPORT Atom, AtomList, FileRd, FloatMode, Fmt, Lex, OSError, Rd, Scan, SFTP, SSH, Stdio, Text,
       TextWr, Thread, Word, Wr;

<*FATAL FloatMode.Trap, Lex.Error, OSError.E*>

CONST HexDigits = "0123456789abcdef";

VAR
  s: SSH.T := NIL;
  f: SFTP.T := NIL;

(* texts and data *)

PROCEDURE Arg (t: TEXT): TEXT =
  VAR n: INTEGER;  r: REF ARRAY OF WIDECHAR;  v: INTEGER;
  BEGIN
    IF Text.Equal (t, "-") THEN RETURN NIL END;
    n := Text.Length (t) DIV 4;
    r := NEW (REF ARRAY OF WIDECHAR, n);
    FOR i := 0 TO n - 1 DO
      v := 0;
      FOR k := 4 * i TO 4 * i + 3 DO
        v := v * 16 + Text.FindChar (HexDigits, Text.GetChar (t, k));
      END;
      r[i] := VAL (v, WIDECHAR);
    END;
    RETURN Text.FromWideChars (r^);
  END Arg;

PROCEDURE Hex16 (t: TEXT): TEXT =
  VAR wr := TextWr.New ();  c: INTEGER;
  <*FATAL Wr.Failure, Thread.Alerted*>
  BEGIN
    IF t = NIL THEN RETURN "-" END;
    FOR i := 0 TO Text.Length (t) - 1 DO
      c := ORD (Text.GetWideChar (t, i));
      FOR k := 3 TO 0 BY -1 DO
        Wr.PutChar (wr, Text.GetChar (HexDigits, Word.And (Word.RightShift (c, 4 * k), 15)));
      END;
    END;
    RETURN TextWr.ToText (wr);
  END Hex16;

PROCEDURE Pattern (n: CARDINAL): TEXT =
  VAR a := NEW (REF ARRAY OF CHAR, n);
  BEGIN
    FOR i := 0 TO n - 1 DO a[i] := VAL ((i * 7 + i DIV 256) MOD 256, CHAR) END;
    RETURN Text.FromChars (a^);
  END Pattern;

PROCEDURE Data (t: TEXT): TEXT =
  VAR n: INTEGER;  a: REF ARRAY OF CHAR;
  BEGIN
    IF Text.Equal (t, "-") THEN RETURN "" END;
    IF Text.GetChar (t, 0) = 'p' THEN RETURN Pattern (Scan.Int (Text.Sub (t, 1))) END;
    n := (Text.Length (t) - 1) DIV 2;
    a := NEW (REF ARRAY OF CHAR, n);
    FOR i := 0 TO n - 1 DO
      a[i] := VAL (Text.FindChar (HexDigits, Text.GetChar (t, 1 + 2 * i)) * 16
                     + Text.FindChar (HexDigits, Text.GetChar (t, 2 + 2 * i)), CHAR);
    END;
    RETURN Text.FromChars (a^);
  END Data;

PROCEDURE Fnv (t: TEXT): INTEGER =
  VAR h := 16_811C9DC5;
  BEGIN
    FOR i := 0 TO Text.Length (t) - 1 DO
      h := Word.And (Word.Times (Word.Xor (h, ORD (Text.GetChar (t, i))), 16777619), 16_FFFFFFFF);
    END;
    RETURN h;
  END Fnv;

PROCEDURE Hex (t: TEXT): TEXT =
  VAR n := MIN (Text.Length (t), 1000);  r: REF ARRAY OF CHAR;  c: INTEGER;
  BEGIN
    IF n = 0 THEN RETURN "-" END;
    r := NEW (REF ARRAY OF CHAR, 2 * n);
    FOR i := 0 TO n - 1 DO
      c := ORD (Text.GetChar (t, i));
      r[2 * i] := Text.GetChar (HexDigits, c DIV 16);
      r[2 * i + 1] := Text.GetChar (HexDigits, c MOD 16);
    END;
    RETURN Text.FromChars (r^);
  END Hex;

PROCEDURE Summary (t: TEXT; bytes: BOOLEAN): TEXT =
  VAR r := Fmt.Int (Text.Length (t)) & " " & Fmt.Int (Fnv (t));
  BEGIN
    IF bytes THEN r := r & " " & Hex (t) END;
    RETURN r;
  END Summary;

PROCEDURE Describe (e: AtomList.T): TEXT =
  VAR r := Atom.ToText (e.head);
  BEGIN
    IF e.tail # NIL THEN r := r & " " & Atom.ToText (e.tail.head) END;
    RETURN r;
  END Describe;

PROCEDURE ReadAll (rd: Rd.T): TEXT RAISES {Rd.Failure, Thread.Alerted} =
  VAR wr := TextWr.New ();  buf := NEW (REF ARRAY OF CHAR, 32768);  n: CARDINAL;
  <*FATAL Wr.Failure*>
  BEGIN
    LOOP
      n := Rd.GetSub (rd, buf^);
      IF n = 0 THEN EXIT END;
      Wr.PutString (wr, SUBARRAY (buf^, 0, n));
    END;
    RETURN TextWr.ToText (wr);
  END ReadAll;

PROCEDURE Seconds (t: TEXT): LONGREAL =
  BEGIN
    RETURN Scan.LongReal (t);
  END Seconds;

PROCEDURE Words (line: TEXT): REF ARRAY OF TEXT =
  VAR n := 1;  r: REF ARRAY OF TEXT;  start := 0;  k := 0;  end: INTEGER;
  BEGIN
    FOR i := 0 TO Text.Length (line) - 1 DO
      IF Text.GetChar (line, i) = ' ' THEN INC (n) END;
    END;
    r := NEW (REF ARRAY OF TEXT, n);
    LOOP
      end := Text.FindChar (line, ' ', start);
      IF end < 0 THEN r[k] := Text.Sub (line, start); EXIT END;
      r[k] := Text.Sub (line, start, end - start);
      INC (k);
      start := end + 1;
    END;
    RETURN r;
  END Words;

(* threads *)

TYPE
  Reader = Thread.Closure OBJECT
    rd: Rd.T;
    data: TEXT := NIL;
    failure: TEXT := NIL;
    alerted := FALSE;
  OVERRIDES
    apply := ReadApply;
  END;

PROCEDURE ReadApply (r: Reader): REFANY =
  BEGIN
    TRY
      r.data := ReadAll (r.rd);
    EXCEPT
    | Rd.Failure (e) => r.failure := Describe (e);
    | Thread.Alerted => r.alerted := TRUE;
    END;
    RETURN NIL;
  END ReadApply;

TYPE
  Runner = Thread.Closure OBJECT
    command: TEXT;
    result: TEXT := NIL;
  OVERRIDES
    apply := RunApply;
  END;

PROCEDURE RunApply (r: Runner): REFANY =
  VAR out, err: TEXT;
  BEGIN
    TRY
      EVAL s.run (r.command, out, err);
      r.result := "error finished";
    EXCEPT
    | SSH.Error (e) => r.result := "error " & Describe (e);
    | Thread.Alerted => r.result := "ok alerted";
    END;
    RETURN NIL;
  END RunApply;

TYPE
  Waiter = Thread.Closure OBJECT
    p: SSH.Process;
    result: TEXT := NIL;
  OVERRIDES
    apply := WaitApply;
  END;

PROCEDURE WaitApply (w: Waiter): REFANY =
  BEGIN
    TRY
      EVAL w.p.wait ();
      w.result := "error finished";
    EXCEPT
    | SSH.Error (e) => w.result := "error " & Describe (e);
    | Thread.Alerted => w.result := "ok alerted";
    END;
    RETURN NIL;
  END WaitApply;

TYPE
  Echo = Thread.Closure OBJECT
    i: INTEGER;
    ok := FALSE;
    failure: TEXT := NIL;
  OVERRIDES
    apply := EchoApply;
  END;

PROCEDURE EchoApply (e: Echo): REFANY =
  VAR out, err: TEXT;  status: INTEGER;
  BEGIN
    TRY
      status := s.run ("echo " & Fmt.Int (e.i), out, err);
      e.ok := status = 0 AND Text.Equal (out, Fmt.Int (e.i) & "\n");
    EXCEPT
    | SSH.Error (x) => e.failure := Describe (x);
    | Thread.Alerted => e.failure := "alerted";
    END;
    RETURN NIL;
  END EchoApply;

(* Runs "cl" in a thread and alerts it after "seconds"; its result. *)
PROCEDURE AlertAfter (cl: Thread.Closure; seconds: LONGREAL) =
  VAR th := Thread.Fork (cl);
  BEGIN
    Thread.Pause (seconds);
    Thread.Alert (th);
    EVAL Thread.Join (th);
  END AlertAfter;

(* requests *)

PROCEDURE Connect (READONLY w: ARRAY OF TEXT): TEXT RAISES ANY =
  VAR o := SSH.Defaults;  keyText := Arg (w[5]);  rd: Rd.T;
  BEGIN
    IF s # NIL THEN s.close (); s := NIL; f := NIL END;
    o.port := Scan.Int (w[1]);
    o.user := Arg (w[2]);
    o.password := Arg (w[3]);
    o.keyFile := Arg (w[4]);
    IF keyText # NIL THEN
      rd := FileRd.Open (keyText);
      o.key := Rd.GetText (rd, 1000000);
      Rd.Close (rd);
    END;
    o.passphrase := Arg (w[6]);
    IF Text.Equal (w[7], "n") THEN
      o.hostCheck := SSH.HostCheck.AcceptNew;
    ELSIF Text.Equal (w[7], "f") THEN
      o.hostCheck := SSH.HostCheck.Fingerprint;
    END;
    o.knownHosts := Arg (w[8]);
    o.fingerprint := Arg (w[9]);
    o.timeout := Seconds (w[10]);
    o.keepAlive := Seconds (w[11]);
    s := SSH.Connect ("127.0.0.1", o);
    RETURN "ok " & s.hostKey ();
  END Connect;

PROCEDURE Run (READONLY w: ARRAY OF TEXT): TEXT RAISES ANY =
  VAR out, err: TEXT;  status: INTEGER;
  BEGIN
    status := s.run (Arg (w[4]), out, err, input := Data (w[3]), maxOutput := Scan.Int (w[1]),
                     timeout := Seconds (w[2]));
    RETURN "ok " & Fmt.Int (status) & " " & Summary (out, TRUE) & " " & Summary (err, TRUE);
  END Run;

PROCEDURE Proc (READONLY w: ARRAY OF TEXT): TEXT RAISES ANY =
  VAR
    mode := w[1];  p := s.start (Arg (w[4]), maxBuffer := Scan.Int (w[2]));  input := Data (w[3]);
    out, err := "";  ro, re: Reader;  to, te: Thread.T;  status: INTEGER;  sig: TEXT;
  BEGIN
    TRY
      IF Text.Equal (mode, "par") THEN
        ro := NEW (Reader, rd := p.stdout);
        re := NEW (Reader, rd := p.stderr);
        to := Thread.Fork (ro);
        te := Thread.Fork (re);
        Wr.PutText (p.stdin, input);
        Wr.Close (p.stdin);
        EVAL Thread.Join (to);
        EVAL Thread.Join (te);
        IF ro.failure # NIL THEN RETURN "error " & ro.failure END;
        IF re.failure # NIL THEN RETURN "error " & re.failure END;
        out := ro.data;
        err := re.data;
      ELSE
        IF Text.Equal (mode, "outonly") THEN Rd.Close (p.stderr) END;
        Wr.PutText (p.stdin, input);
        Wr.Close (p.stdin);
        IF Text.Equal (mode, "nowait") THEN RETURN "ok " & Summary (ReadAll (p.stdout), FALSE) END;
        IF Text.Equal (mode, "seq") OR Text.Equal (mode, "outonly") THEN out := ReadAll (p.stdout) END;
        IF Text.Equal (mode, "seq") THEN err := ReadAll (p.stderr) END;
      END;
      status := p.wait ();
      sig := p.signal ();
      IF sig = NIL THEN sig := "-" END;
      RETURN "ok " & Fmt.Int (status) & " " & sig & " " & Summary (out, FALSE) & " " & Summary (err, FALSE);
    FINALLY
      p.close ();
    END;
  END Proc;

PROCEDURE AlertRead (READONLY w: ARRAY OF TEXT): TEXT RAISES ANY =
  VAR p := s.start (Arg (w[2]));  r := NEW (Reader, rd := p.stdout);
  BEGIN
    AlertAfter (r, Seconds (w[1]));
    p.close ();
    IF r.alerted THEN RETURN "ok alerted" END;
    IF r.failure # NIL THEN RETURN "error " & r.failure END;
    RETURN "error finished";
  END AlertRead;

PROCEDURE AlertWait (READONLY w: ARRAY OF TEXT): TEXT RAISES ANY =
  VAR p := s.start (Arg (w[2]));  r := NEW (Waiter, p := p);
  BEGIN
    AlertAfter (r, Seconds (w[1]));
    p.close ();
    RETURN r.result;
  END AlertWait;

PROCEDURE Many (READONLY w: ARRAY OF TEXT): TEXT RAISES ANY =
  VAR n := Scan.Int (w[1]);  e := NEW (REF ARRAY OF Echo, n);  t := NEW (REF ARRAY OF Thread.T, n);
  BEGIN
    FOR i := 0 TO n - 1 DO
      e[i] := NEW (Echo, i := i);
      t[i] := Thread.Fork (e[i]);
    END;
    FOR i := 0 TO n - 1 DO EVAL Thread.Join (t[i]) END;
    FOR i := 0 TO n - 1 DO
      IF e[i].failure # NIL THEN RETURN "error " & e[i].failure END;
      IF NOT e[i].ok THEN RETURN "error wrong output of " & Fmt.Int (i) END;
    END;
    RETURN "ok " & Fmt.Int (n);
  END Many;

PROCEDURE Attributes (READONLY a: SFTP.Attributes): TEXT =
  BEGIN
    RETURN Fmt.Int (ORD (a.kind)) & " " & Fmt.Int (a.size) & " " & Fmt.Int (a.mode, 8) & " "
             & Fmt.Int (a.uid) & " " & Fmt.Int (a.gid) & " " & Fmt.Int (TRUNC (a.atime)) & " "
             & Fmt.Int (TRUNC (a.mtime));
  END Attributes;

PROCEDURE Sftp (READONLY w: ARRAY OF TEXT): TEXT RAISES ANY =
  VAR
    op := w[0];  rd: Rd.T;  wr: Wr.T;  a: SFTP.Attributes;  data, r: TEXT;  e: SFTP.Entries;
    busy: TEXT := "none";  reader: Reader;
  BEGIN
    IF Text.Equal (op, "sopen") THEN
      f := SFTP.Open (s);
    ELSIF Text.Equal (op, "sclose") THEN
      f.close ();
      f := NIL;
    ELSIF Text.Equal (op, "sget") THEN
      rd := f.open (Arg (w[1]), Scan.Int (w[2]));
      TRY data := ReadAll (rd) FINALLY Rd.Close (rd) END;
      RETURN "ok " & Summary (data, FALSE);
    ELSIF Text.Equal (op, "sput") THEN
      wr := f.create (Arg (w[1]), Scan.Int (w[2], 8), Text.Equal (w[3], "1"));
      Wr.PutText (wr, Data (w[4]));
      Wr.Close (wr);
    ELSIF Text.Equal (op, "sstat") THEN
      f.stat (Arg (w[1]), a);
      RETURN "ok " & Attributes (a);
    ELSIF Text.Equal (op, "slstat") THEN
      f.lstat (Arg (w[1]), a);
      RETURN "ok " & Attributes (a);
    ELSIF Text.Equal (op, "slist") THEN
      e := f.list (Arg (w[1]));
      r := "ok " & Fmt.Int (NUMBER (e^));
      FOR i := 0 TO LAST (e^) DO
        r := r & " " & Hex16 (e[i].name) & ":" & Fmt.Int (ORD (e[i].attr.kind)) & ":"
               & Fmt.Int (e[i].attr.size);
      END;
      RETURN r;
    ELSIF Text.Equal (op, "smkdir") THEN
      f.mkdir (Arg (w[1]), Scan.Int (w[2], 8));
    ELSIF Text.Equal (op, "srmdir") THEN
      f.rmdir (Arg (w[1]));
    ELSIF Text.Equal (op, "sremove") THEN
      f.remove (Arg (w[1]));
    ELSIF Text.Equal (op, "srename") THEN
      f.rename (Arg (w[1]), Arg (w[2]));
    ELSIF Text.Equal (op, "ssymlink") THEN
      f.symlink (Arg (w[1]), Arg (w[2]));
    ELSIF Text.Equal (op, "sreadlink") THEN
      RETURN "ok " & Hex16 (f.readlink (Arg (w[1])));
    ELSIF Text.Equal (op, "srealpath") THEN
      RETURN "ok " & Hex16 (f.realpath (Arg (w[1])));
    ELSIF Text.Equal (op, "schmod") THEN
      f.chmod (Arg (w[1]), Scan.Int (w[2], 8));
    ELSIF Text.Equal (op, "stimes") THEN
      f.setTimes (Arg (w[1]), Seconds (w[2]), Seconds (w[3]));
    ELSIF Text.Equal (op, "sbusy") THEN
      rd := f.open (Arg (w[1]));
      TRY
        f.stat (".", a);
      EXCEPT
      | SSH.Error (x) => busy := Atom.ToText (x.head);
      END;
      Rd.Close (rd);
      f.stat (".", a);
      RETURN "ok " & busy;
    ELSIF Text.Equal (op, "salert") THEN
      rd := f.open (Arg (w[1]));
      reader := NEW (Reader, rd := rd);
      AlertAfter (reader, Seconds (w[2]));
      Rd.Close (rd);
      IF reader.alerted THEN RETURN "ok alerted" END;
      IF reader.failure # NIL THEN RETURN "error " & reader.failure END;
      RETURN "error finished";
    ELSE
      RETURN "error unknown request";
    END;
    RETURN "ok";
  END Sftp;

PROCEDURE Do (READONLY w: ARRAY OF TEXT): TEXT RAISES ANY =
  VAR op := w[0];  out, err: TEXT;
  BEGIN
    IF Text.Equal (op, "connect") THEN
      RETURN Connect (w);
    ELSIF Text.Equal (op, "hostkey") THEN
      RETURN "ok " & s.hostKey ();
    ELSIF Text.Equal (op, "close") THEN
      s.close ();
      s := NIL;
      f := NIL;
      RETURN "ok";
    ELSIF Text.Equal (op, "run") THEN
      RETURN Run (w);
    ELSIF Text.Equal (op, "proc") THEN
      RETURN Proc (w);
    ELSIF Text.Equal (op, "alertrun") THEN
      WITH r = NEW (Runner, command := Arg (w[2])) DO
        AlertAfter (r, Seconds (w[1]));
        RETURN r.result;
      END;
    ELSIF Text.Equal (op, "alertread") THEN
      RETURN AlertRead (w);
    ELSIF Text.Equal (op, "alertwait") THEN
      RETURN AlertWait (w);
    ELSIF Text.Equal (op, "many") THEN
      RETURN Many (w);
    ELSIF Text.Equal (op, "pause") THEN
      Thread.Pause (Seconds (w[1]));
      RETURN "ok";
    ELSIF Text.Equal (op, "echo") THEN
      EVAL s.run ("echo probe", out, err);
      RETURN "ok " & Hex (out);
    ELSE
      RETURN Sftp (w);
    END;
  END Do;

VAR line, answer: TEXT;

<*FATAL Rd.Failure, Wr.Failure, Thread.Alerted*>
BEGIN
  LOOP
    TRY
      line := Rd.GetLine (Stdio.stdin);
    EXCEPT
    | Rd.EndOfFile => EXIT;
    END;
    TRY
      answer := Do (Words (line)^);
    EXCEPT
    | SSH.Error (e) => answer := "error " & Describe (e);
    | Rd.Failure (e) => answer := "error " & Describe (e);
    | Wr.Failure (e) => answer := "error " & Describe (e);
    | Thread.Alerted => answer := "error alerted";
    ELSE answer := "error exception";
    END;
    Wr.PutText (Stdio.stdout, answer & "\n");
    Wr.Flush (Stdio.stdout);
  END;
END Main.
