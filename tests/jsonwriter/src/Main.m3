(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Tests of JsonWriter; tools/json_test.py runs them.

     batch LIST     for each line "SCRIPT OUT" of LIST, the calls of SCRIPT;
                    the document goes to OUT, the result to OUT.res and what
                    JsonReader reads from the document to OUT.tree
     sink           writers that fail or block, flush, Open and close
     big MB         a generated document of MB megabytes on standard output
     speed MB       the same to nowhere, with the rate

   The first line of a script is "options INDENT REPLACE UNIQUE MAXDEPTH
   TREE", the others a method and its arguments. A text is its code units in
   hexadecimal, "-" for NIL and "=" for the empty text. An integer is in
   decimal, a real as well or "inf", "-inf" or "nan". *)

MODULE Main;

IMPORT FileRd, FileWr, Fmt, IO, JsonReader, JsonWriter, Params, Rd, Scan, Stdio, Text, TextWr,
       Thread, Time, Wr, WrClass;
FROM JsonWriter IMPORT Problem;

<*FATAL ANY*>

CONST
  ProblemNames = ARRAY Problem OF TEXT {"Content", "Structure", "Closed"};

PROCEDURE Fields (line: TEXT; VAR a: ARRAY OF TEXT): CARDINAL =
  VAR n := 0; i := 0; j: INTEGER; len := Text.Length (line);
  BEGIN
    WHILE i < len DO
      j := Text.FindChar (line, ' ', i);
      IF j < 0 THEN j := len END;
      a[n] := Text.Sub (line, i, j - i);
      INC (n);
      i := j + 1;
    END;
    RETURN n;
  END Fields;

PROCEDURE Digit (c: CHAR): INTEGER =
  BEGIN
    IF c >= 'a' THEN RETURN ORD (c) - ORD ('a') + 10 END;
    RETURN ORD (c) - ORD ('0');
  END Digit;

PROCEDURE Arg (f: TEXT): TEXT =
  VAR a: REF ARRAY OF WIDECHAR;
  BEGIN
    IF Text.Equal (f, "-") THEN RETURN NIL END;
    IF Text.Equal (f, "=") THEN RETURN "" END;
    a := NEW (REF ARRAY OF WIDECHAR, Text.Length (f) DIV 4);
    FOR i := 0 TO LAST (a^) DO
      a[i] := VAL (Digit (Text.GetChar (f, 4 * i)) * 4096
                     + Digit (Text.GetChar (f, 4 * i + 1)) * 256
                     + Digit (Text.GetChar (f, 4 * i + 2)) * 16
                     + Digit (Text.GetChar (f, 4 * i + 3)), WIDECHAR);
    END;
    RETURN Text.FromWideChars (a^);
  END Arg;

PROCEDURE RealArg (f: TEXT): LONGREAL =
  BEGIN
    IF Text.Equal (f, "inf") THEN RETURN 1.0D0 / 0.0D0 END;
    IF Text.Equal (f, "-inf") THEN RETURN -1.0D0 / 0.0D0 END;
    IF Text.Equal (f, "nan") THEN RETURN 0.0D0 / 0.0D0 END;
    RETURN Scan.LongReal (f);
  END RealArg;

PROCEDURE Do (w: JsonWriter.T; READONLY f: ARRAY OF TEXT; VAR finished: BOOLEAN)
  RAISES {JsonWriter.Error} =
  VAR op := f[0];
  BEGIN
    IF Text.Equal (op, "object") THEN
      w.object (Arg (f[1]));
    ELSIF Text.Equal (op, "array") THEN
      w.array (Arg (f[1]));
    ELSIF Text.Equal (op, "end") THEN
      w.end ();
    ELSIF Text.Equal (op, "key") THEN
      w.key (Arg (f[1]));
    ELSIF Text.Equal (op, "text") THEN
      w.text (Arg (f[1]), Arg (f[2]));
    ELSIF Text.Equal (op, "int") THEN
      w.int (Scan.Int (f[1]), Arg (f[2]));
    ELSIF Text.Equal (op, "real") THEN
      w.real (RealArg (f[1]), Arg (f[2]));
    ELSIF Text.Equal (op, "bool") THEN
      w.bool (Text.Equal (f[1], "1"), Arg (f[2]));
    ELSIF Text.Equal (op, "null") THEN
      w.null (Arg (f[1]));
    ELSIF Text.Equal (op, "number") THEN
      w.number (Arg (f[1]), Arg (f[2]));
    ELSIF Text.Equal (op, "finish") THEN
      w.finish ();
      finished := TRUE;
    ELSE
      <*ASSERT FALSE*>
    END;
  END Do;

(* The code units of t in hexadecimal, four digits each, or "=" if there
   are none. *)
PROCEDURE Units (t: TEXT): TEXT =
  VAR wr: TextWr.T;
  BEGIN
    IF Text.Empty (t) THEN RETURN "=" END;
    wr := TextWr.New ();
    FOR i := 0 TO Text.Length (t) - 1 DO
      Wr.PutText (wr, Fmt.Pad (Fmt.Int (ORD (Text.GetWideChar (t, i)), 16), 4, '0'));
    END;
    RETURN TextWr.ToText (wr);
  END Units;

(* What JsonReader reads, a line for each value: an object from "{" to "}"
   with a line "k" for each key, an array from "[" to "]"; an integer that
   fits is "i", another number "f" with the nearest LONGREAL, or "x" with
   its text if that is not finite. *)
PROCEDURE Dump (wr: Wr.T; r: JsonReader.T) =
  BEGIN
    CASE r.peek () OF
    | JsonReader.Kind.Object =>
        Wr.PutText (wr, "{\n");
        r.object ();
        WHILE r.member () DO
          Wr.PutText (wr, "k " & Units (r.name ()) & "\n");
          Dump (wr, r);
        END;
        Wr.PutText (wr, "}\n");
    | JsonReader.Kind.Array =>
        Wr.PutText (wr, "[\n");
        r.array ();
        WHILE r.element () DO Dump (wr, r) END;
        Wr.PutText (wr, "]\n");
    | JsonReader.Kind.Text => Wr.PutText (wr, "s " & Units (r.text ()) & "\n");
    | JsonReader.Kind.Number =>
        TRY
          Wr.PutText (wr, "i " & Fmt.Int (r.int ()) & "\n");
        EXCEPT
        | JsonReader.Error =>
            TRY
              Wr.PutText (wr, "f " & Fmt.LongReal (r.real ()) & "\n");
            EXCEPT
            | JsonReader.Error => Wr.PutText (wr, "x " & r.number () & "\n");
            END;
        END;
    | JsonReader.Kind.Bool =>
        IF r.bool () THEN Wr.PutText (wr, "b true\n") ELSE Wr.PutText (wr, "b false\n") END;
    | JsonReader.Kind.Null => r.null (); Wr.PutText (wr, "n\n");
    ELSE
      Wr.PutText (wr, "?\n");
      r.skip ();
    END;
  END Dump;

PROCEDURE Tree (out: TEXT) =
  VAR
    r := JsonReader.Open (out, JsonReader.Options {maxDepth := 0});
    wr := FileWr.Open (out & ".tree");
  BEGIN
    Dump (wr, r);
    r.finish ();
    r.close ();
    Wr.Close (wr);
  END Tree;

PROCEDURE Run (script, out: TEXT) =
  VAR
    rd := FileRd.Open (script);
    file := FileWr.Open (out);
    res := FileWr.Open (out & ".res");
    f: ARRAY [0 .. 5] OF TEXT;
    o := JsonWriter.Defaults;
    w: JsonWriter.T;
    tree, finished := FALSE;
  BEGIN
    EVAL Fields (Rd.GetLine (rd), f);
    o.indent := Arg (f[1]);
    o.replaceInvalid := Text.Equal (f[2], "1");
    o.uniqueKeys := Text.Equal (f[3], "1");
    o.maxDepth := Scan.Int (f[4]);
    tree := Text.Equal (f[5], "1");
    w := JsonWriter.ToWr (file, o);
    TRY
      WHILE NOT Rd.EOF (rd) DO
        EVAL Fields (Rd.GetLine (rd), f);
        Do (w, f, finished);
      END;
      Wr.PutText (res, "OK " & Fmt.Int (w.depth ()) & "\n");
    EXCEPT
    | JsonWriter.Error (x) =>
        Wr.PutText (res, "ERROR " & ProblemNames[x.problem] & "\n");
        TRY
          w.null ();
          Wr.PutText (res, "AFTER none\n");
        EXCEPT
        | JsonWriter.Error (y) => Wr.PutText (res, "AFTER " & ProblemNames[y.problem] & "\n");
        END;
    END;
    w.flush ();
    w.close ();
    Wr.Close (file);
    Wr.Close (res);
    Rd.Close (rd);
    IF tree AND finished THEN Tree (out) END;
  END Run;

PROCEDURE Batch (list: TEXT) =
  VAR rd := FileRd.Open (list); f: ARRAY [0 .. 1] OF TEXT;
  BEGIN
    WHILE NOT Rd.EOF (rd) DO
      EVAL Fields (Rd.GetLine (rd), f);
      Run (f[0], f[1]);
    END;
    Rd.Close (rd);
  END Batch;

(* A writer that keeps what it gets, up to a limit, and then fails or blocks. *)
TYPE
  Sink = Wr.T OBJECT
    limit := LAST (INTEGER);
    block := FALSE;
    keep := TRUE;
    got := 0;
    data: TextWr.T;
  OVERRIDES
    seek := SinkSeek;
    putString := SinkPut;
    flush := SinkFlush;
    close := SinkClose;
  END;

PROCEDURE NewSink (): Sink =
  BEGIN
    RETURN NEW (Sink, closed := FALSE, seekable := FALSE, buffered := TRUE,
                st := 0, data := TextWr.New ());
  END NewSink;

PROCEDURE SinkSeek (<*UNUSED*> s: Sink; <*UNUSED*> n: CARDINAL) =
  BEGIN
  END SinkSeek;

(* After its failure a writer must not be touched any more. *)
PROCEDURE SinkFlush (s: Sink) RAISES {Wr.Failure} =
  BEGIN
    IF s.got >= s.limit THEN RAISE Wr.Failure (NIL) END;
  END SinkFlush;

PROCEDURE SinkClose (<*UNUSED*> s: Sink) =
  BEGIN
  END SinkClose;

PROCEDURE SinkPut (s: Sink; READONLY a: ARRAY OF CHAR)
  RAISES {Wr.Failure, Thread.Alerted} =
  VAR n := MIN (NUMBER (a), s.limit - s.got);
  BEGIN
    IF s.block THEN Thread.AlertPause (60.0D0) END;
    IF s.keep THEN Wr.PutString (s.data, SUBARRAY (a, 0, n)) END;
    INC (s.got, n);
    IF n < NUMBER (a) THEN RAISE Wr.Failure (NIL) END;
  END SinkPut;

PROCEDURE Show (s: Sink; label: TEXT) =
  VAR t := TextWr.ToText (s.data);
  BEGIN
    Wr.PutText (s.data, t);
    IO.Put (label & ": [" & t & "]\n");
  END Show;

PROCEDURE SinkFlushing () =
  VAR s := NewSink (); w := JsonWriter.ToWr (s);
  BEGIN
    w.object ();
    w.flush ();
    Show (s, "opened");
    w.text ("x", "a");
    w.flush ();
    Show (s, "after a member");
    w.key ("b");
    w.flush ();
    Show (s, "after a key");
    w.array ();
    w.int (1);
    w.end ();
    w.flush ();
    Show (s, "array done");
    IO.Put ("depth " & Fmt.Int (w.depth ()) & "\n");
    w.end ();
    w.finish ();
    Show (s, "finished");
    TRY
      w.text ("late");
    EXCEPT
    | JsonWriter.Error (f) => IO.Put ("after finish: " & ProblemNames[f.problem] & "\n");
    END;
    w.flush ();
    w.close ();
    w.close ();
    w.flush ();
    IO.Put ("close and flush again are harmless\n");
  END SinkFlushing;

PROCEDURE Write (w: JsonWriter.T) RAISES {JsonWriter.Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    w.object ();
    w.array ("items");
    FOR i := 1 TO 3000 DO
      w.object ();
      w.int (i, "id");
      w.text ("some text " & Fmt.Int (i), "text");
      w.end ();
    END;
    w.end ();
    w.end ();
    w.finish ();
  END Write;

PROCEDURE SinkFailing () =
  VAR
    s := NewSink ();
    w := JsonWriter.ToWr (s);
    full, t: TEXT;
    count, raised, prefix, closed := 0;
    limit := 0;
  BEGIN
    Write (w);
    full := TextWr.ToText (s.data);
    WHILE limit < Text.Length (full) DO
      s := NewSink ();
      s.limit := limit;
      w := JsonWriter.ToWr (s);
      INC (count);
      TRY
        Write (w);
      EXCEPT
      | Wr.Failure => INC (raised);
      END;
      t := TextWr.ToText (s.data);
      IF Text.Equal (t, Text.Sub (full, 0, Text.Length (t))) THEN INC (prefix) END;
      TRY
        w.null ();
      EXCEPT
      | JsonWriter.Error (f) => IF f.problem = Problem.Closed THEN INC (closed) END;
      END;
      w.flush ();
      w.close ();
      INC (limit, 997);
    END;
    IO.Put ("failing writers " & Fmt.Bool (count > 20) & ": raised "
              & Fmt.Bool (raised = count) & ", a prefix " & Fmt.Bool (prefix = count)
              & ", closed afterwards " & Fmt.Bool (closed = count) & "\n");
  END SinkFailing;

TYPE
  Blocked = Thread.Closure OBJECT
    w: JsonWriter.T;
    result := "nothing came through";
  OVERRIDES
    apply := BlockedApply;
  END;

PROCEDURE BlockedApply (self: Blocked): REFANY =
  BEGIN
    TRY
      Write (self.w);
    EXCEPT
    | Thread.Alerted => self.result := "Thread.Alerted came through";
    END;
    RETURN NIL;
  END BlockedApply;

PROCEDURE SinkAlert () =
  VAR s := NewSink (); b: Blocked; th: Thread.T;
  BEGIN
    s.block := TRUE;
    b := NEW (Blocked, w := JsonWriter.ToWr (s));
    th := Thread.Fork (b);
    Thread.Pause (0.5D0);
    Thread.Alert (th);
    EVAL Thread.Join (th);
    IO.Put (b.result & "\n");
    TRY
      b.w.null ();
    EXCEPT
    | JsonWriter.Error (f) => IO.Put ("after Thread.Alerted: " & ProblemNames[f.problem] & "\n");
    END;
  END SinkAlert;

(* Printable ASCII as it is, a byte beyond it in hexadecimal. *)
PROCEDURE Esc (t: TEXT): TEXT =
  VAR wr := TextWr.New (); c: INTEGER;
  BEGIN
    FOR i := 0 TO Text.Length (t) - 1 DO
      c := ORD (Text.GetChar (t, i));
      IF c >= 16_20 AND c < 16_7F THEN
        Wr.PutChar (wr, VAL (c, CHAR));
      ELSE
        Wr.PutText (wr, "\\" & Fmt.Pad (Fmt.Int (c, 16), 2, '0'));
      END;
    END;
    RETURN TextWr.ToText (wr);
  END Esc;

PROCEDURE SinkFile () =
  VAR w := JsonWriter.Open ("sink.json"); rd: Rd.T;
  BEGIN
    TRY
      w.array ();
      w.text (Text.FromWideChar (W'\x00E4'));
      w.end ();
      w.finish ();
    FINALLY
      w.close ();
    END;
    rd := FileRd.Open ("sink.json");
    IO.Put ("file: " & Esc (Rd.GetText (rd, 200)) & "\n");
    Rd.Close (rd);
    w := JsonWriter.Open ("sink2.json");
    TRY
      w.array ();
      w.end ();
      w.end ();
    EXCEPT
    | JsonWriter.Error (f) => IO.Put ("error: " & JsonWriter.Format (f) & "\n");
    END;
    w.close ();
    IO.Put ("closed after an error\n");
  END SinkFile;

(* The same key made in different ways, before and after the table of keys
   takes over. *)
PROCEDURE SinkKeys () =

  PROCEDURE Refused (keys: CARDINAL): BOOLEAN =
    VAR
      w := JsonWriter.ToWr (NewSink ());
      wide := ARRAY [0 .. 1] OF WIDECHAR {W'k', W'0'};
    BEGIN
      TRY
        w.object ();
        FOR i := 0 TO keys - 1 DO w.int (i, "k" & Fmt.Int (i)) END;
        w.int (0, Text.FromWideChars (wide));
        RETURN FALSE;
      EXCEPT
      | JsonWriter.Error (f) => RETURN f.problem = Problem.Structure;
      END;
    END Refused;

  BEGIN
    IO.Put ("the same key, few " & Fmt.Bool (Refused (5)) & ", many "
              & Fmt.Bool (Refused (500)) & "\n");
  END SinkKeys;

PROCEDURE Generate (w: JsonWriter.T; mb: INTEGER)
  RAISES {JsonWriter.Error, Wr.Failure, Thread.Alerted} =
  VAR left := mb * 1000 * 1000; n := 0; id: TEXT;
  BEGIN
    w.array ();
    WHILE left > 0 DO
      INC (n);
      id := Fmt.Int (n);
      DEC (left, 20 + 2 * Text.Length (id));
      w.object ();
      w.int (n, "id");
      w.text ("text " & id, "t");
      w.end ();
    END;
    w.end ();
    w.finish ();
  END Generate;

PROCEDURE Speed (mb: INTEGER) =
  VAR s := NewSink (); t0: Time.T; secs: LONGREAL;
  BEGIN
    s.keep := FALSE;
    t0 := Time.Now ();
    Generate (JsonWriter.ToWr (s), mb);
    secs := Time.Now () - t0;
    IO.Put (Fmt.Int (mb) & " MB in " & Fmt.LongReal (secs, Fmt.Style.Fix, 2) & " s: "
              & Fmt.LongReal (FLOAT (s.got, LONGREAL) / 1.0D6 / secs, Fmt.Style.Fix, 1)
              & " MB/s\n");
  END Speed;

VAR mode := Params.Get (1);

BEGIN
  IF Text.Equal (mode, "batch") THEN
    Batch (Params.Get (2));
  ELSIF Text.Equal (mode, "sink") THEN
    SinkFlushing ();
    SinkFailing ();
    SinkAlert ();
    SinkFile ();
    SinkKeys ();
  ELSIF Text.Equal (mode, "big") THEN
    Generate (JsonWriter.ToWr (Stdio.stdout), Scan.Int (Params.Get (2)));
  ELSIF Text.Equal (mode, "speed") THEN
    Speed (Scan.Int (Params.Get (2)));
  ELSE
    IO.Put ("unknown mode\n");
  END;
END Main.
