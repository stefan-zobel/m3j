(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Tests of XmlWriter; tools/xml_test.py runs them.

     batch LIST     for each line "SCRIPT OUT" of LIST, the calls of SCRIPT;
                    the document goes to OUT and the result to OUT.res
     names          the characters that names may have, by ranges
     reals N        Real and toReal on N numbers
     sink           writers that fail or block, flush, Open and close
     big MB         a generated document of MB megabytes on standard output
     speed MB       the same to nowhere, with the rate

   The first line of a script is "options DECLARATION SELFCLOSE REPLACE
   INDENT", the others a method and its arguments. An argument is its
   code units in hexadecimal, "-" for NIL and "=" for the empty text. *)

MODULE Main;

IMPORT FileRd, FileWr, Fmt, IO, LongFloat, Params, Random, Rd, Scan, Stdio, Text, TextWr,
       Thread, Time, Wr, WrClass, XmlReader, XmlWriter;
FROM XmlWriter IMPORT Problem;

<*FATAL ANY*>

CONST
  ProblemNames = ARRAY Problem OF TEXT {"Name", "Content", "Structure", "Namespace",
                                        "Closed"};

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

PROCEDURE Do (w: XmlWriter.T; READONLY f: ARRAY OF TEXT) RAISES {XmlWriter.Error} =
  VAR op := f[0];
  BEGIN
    IF Text.Equal (op, "start") THEN
      w.start (Arg (f[1]), Arg (f[2]), Arg (f[3]));
    ELSIF Text.Equal (op, "attr") THEN
      w.attr (Arg (f[1]), Arg (f[2]), Arg (f[3]), Arg (f[4]));
    ELSIF Text.Equal (op, "declare") THEN
      w.declare (Arg (f[1]), Arg (f[2]));
    ELSIF Text.Equal (op, "end") THEN
      w.end ();
    ELSIF Text.Equal (op, "element") THEN
      w.element (Arg (f[1]), Arg (f[2]), Arg (f[3]), Arg (f[4]));
    ELSIF Text.Equal (op, "text") THEN
      w.text (Arg (f[1]));
    ELSIF Text.Equal (op, "cdata") THEN
      w.cdata (Arg (f[1]));
    ELSIF Text.Equal (op, "comment") THEN
      w.comment (Arg (f[1]));
    ELSIF Text.Equal (op, "pi") THEN
      w.pi (Arg (f[1]), Arg (f[2]));
    ELSIF Text.Equal (op, "finish") THEN
      w.finish ();
    ELSE
      <*ASSERT FALSE*>
    END;
  END Do;

PROCEDURE Run (script, out: TEXT) =
  VAR
    rd := FileRd.Open (script);
    file := FileWr.Open (out);
    res := FileWr.Open (out & ".res");
    f: ARRAY [0 .. 5] OF TEXT;
    o := XmlWriter.Defaults;
    w: XmlWriter.T;
  BEGIN
    EVAL Fields (Rd.GetLine (rd), f);
    o.declaration := Text.Equal (f[1], "1");
    o.selfClose := Text.Equal (f[2], "1");
    o.replaceInvalid := Text.Equal (f[3], "1");
    o.indent := Arg (f[4]);
    w := XmlWriter.ToWr (file, o);
    TRY
      WHILE NOT Rd.EOF (rd) DO
        EVAL Fields (Rd.GetLine (rd), f);
        Do (w, f);
      END;
      Wr.PutText (res, "OK " & Fmt.Int (w.depth ()) & "\n");
    EXCEPT
    | XmlWriter.Error (x) =>
        Wr.PutText (res, "ERROR " & ProblemNames[x.problem] & "\n");
        TRY
          w.comment ("after");
          Wr.PutText (res, "AFTER none\n");
        EXCEPT
        | XmlWriter.Error (y) => Wr.PutText (res, "AFTER " & ProblemNames[y.problem] & "\n");
        END;
    END;
    w.flush ();
    w.close ();
    Wr.Close (file);
    Wr.Close (res);
    Rd.Close (rd);
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

(* Ranges of a property of the characters, printed in ascending order. *)
TYPE
  Ranges = RECORD tag: TEXT; lo := -1; hi := -1 END;

PROCEDURE Note (VAR r: Ranges; c: INTEGER; yes: BOOLEAN) =
  BEGIN
    IF yes THEN
      IF r.lo < 0 THEN
        r.lo := c;
      ELSIF r.hi + 1 # c THEN
        IO.Put (r.tag & " " & Fmt.Int (r.lo) & " " & Fmt.Int (r.hi) & "\n");
        r.lo := c;
      END;
      r.hi := c;
    END;
  END Note;

PROCEDURE Done (VAR r: Ranges) =
  BEGIN
    IF r.lo >= 0 THEN
      IO.Put (r.tag & " " & Fmt.Int (r.lo) & " " & Fmt.Int (r.hi) & "\n");
    END;
  END Done;

PROCEDURE Reads (doc: TEXT): BOOLEAN =
  VAR r := XmlReader.FromText (doc);
  BEGIN
    TRY
      WHILE r.next () # XmlReader.Kind.EOF DO END;
      RETURN TRUE;
    EXCEPT
    | XmlReader.Error => RETURN FALSE;
    END;
  END Reads;

PROCEDURE Names () =
  VAR
    s := Ranges {"S"}; n := Ranges {"N"}; rs := Ranges {"RS"}; rn := Ranges {"RN"};
    u: TEXT;
    pair := Text.FromWideChars (ARRAY OF WIDECHAR {W'\xD800', W'\xDC00'});
  BEGIN
    FOR c := 16_21 TO 16_FFFF DO
      IF c < 16_D800 OR c > 16_DFFF THEN
        u := Text.FromWideChar (VAL (c, WIDECHAR));
        Note (s, c, XmlWriter.ValidName (u & "a"));
        Note (n, c, XmlWriter.ValidName ("a" & u));
        IF c >= 16_80 THEN
          Note (rs, c, Reads ("<" & u & "a/>"));
          Note (rn, c, Reads ("<a" & u & "/>"));
        END;
      END;
    END;
    Done (s);
    Done (n);
    Done (rs);
    Done (rn);
    IO.Put ("surrogates " & Fmt.Bool (XmlWriter.ValidName (pair)) & " "
              & Fmt.Bool (XmlWriter.ValidName ("a" & pair)) & "\n");
    IO.Put ("empty " & Fmt.Bool (XmlWriter.ValidName ("")) & "\n");
  END Names;

PROCEDURE Same (a, b: LONGREAL): BOOLEAN =
  BEGIN
    IF LongFloat.IsNaN (a) OR LongFloat.IsNaN (b) THEN
      RETURN LongFloat.IsNaN (a) AND LongFloat.IsNaN (b);
    END;
    RETURN a = b AND LongFloat.Sign (a) = LongFloat.Sign (b);
  END Same;

PROCEDURE Reals (n: INTEGER) =
  VAR
    rnd := NEW (Random.Default).init (fixed := TRUE);
    r := XmlReader.FromText ("<a/>");
    bad := 0;
    x: LONGREAL;

  PROCEDURE Try (x: LONGREAL) =
    VAR t := XmlWriter.Real (x);
    BEGIN
      IF NOT Same (r.toReal (t), x) THEN
        INC (bad);
        IF bad < 20 THEN IO.Put ("wrong: " & t & "\n") END;
      END;
    END Try;

  BEGIN
    Try (0.0D0);
    Try (-0.0D0);
    Try (1.0D0);
    Try (-1.5D0);
    Try (LAST (LONGREAL));
    Try (FIRST (LONGREAL));
    Try (LongFloat.Scalb (1.0D0, -1074));
    Try (LongFloat.Scalb (1.0D0, -1022));
    Try (LongFloat.Scalb (1.0D0 / 0.0D0, 0));
    Try (LongFloat.Scalb (-1.0D0 / 0.0D0, 0));
    Try (LongFloat.Scalb (0.0D0 / 0.0D0, 0));
    FOR i := 1 TO n DO
      x := LongFloat.Scalb (rnd.longreal (), rnd.integer (-1060, 1020));
      IF rnd.boolean () THEN x := -x END;
      Try (x);
    END;
    IO.Put ("reals " & Fmt.Int (n) & " wrong " & Fmt.Int (bad) & "\n");
    IO.Put (XmlWriter.Real (1.5D0) & " " & XmlWriter.Real (1.0D0 / 0.0D0) & " "
              & XmlWriter.Real (-1.0D0 / 0.0D0) & " " & XmlWriter.Real (0.0D0 / 0.0D0) & " "
              & XmlWriter.Bool (TRUE) & " " & XmlWriter.Bool (FALSE) & "\n");
  END Reals;

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

PROCEDURE SinkFlush (<*UNUSED*> s: Sink) =
  BEGIN
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

CONST
  NoDeclaration = XmlWriter.Options {declaration := FALSE};

PROCEDURE Show (s: Sink; label: TEXT) =
  VAR t := TextWr.ToText (s.data);
  BEGIN
    Wr.PutText (s.data, t);
    IO.Put (label & ": [" & t & "]\n");
  END Show;

PROCEDURE SinkFlushing () =
  VAR s := NewSink (); w := XmlWriter.ToWr (s, NoDeclaration);
  BEGIN
    w.start ("a");
    w.flush ();
    Show (s, "start tag waits");
    w.text ("x");
    w.flush ();
    Show (s, "after text");
    w.start ("b");
    w.attr ("k", "v");
    w.flush ();
    Show (s, "start tag waits again");
    w.end ();
    w.flush ();
    Show (s, "element done");
    w.end ();
    w.finish ();
    Show (s, "finished");
    TRY
      w.text ("late");
    EXCEPT
    | XmlWriter.Error (f) => IO.Put ("after finish: " & ProblemNames[f.problem] & "\n");
    END;
    w.flush ();
    w.close ();
    w.close ();
    w.flush ();
    IO.Put ("close and flush again are harmless\n");
  END SinkFlushing;

PROCEDURE Write (w: XmlWriter.T) RAISES {XmlWriter.Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    w.start ("root");
    FOR i := 1 TO 3000 DO
      w.start ("item");
      w.attr ("id", Fmt.Int (i));
      w.text ("some text " & Fmt.Int (i));
      w.end ();
    END;
    w.end ();
    w.finish ();
  END Write;

PROCEDURE SinkFailing () =
  VAR
    s := NewSink ();
    w := XmlWriter.ToWr (s, NoDeclaration);
    full, t: TEXT;
    count, raised, prefix, closed := 0;
    limit := 0;
  BEGIN
    Write (w);
    full := TextWr.ToText (s.data);
    WHILE limit < Text.Length (full) DO
      s := NewSink ();
      s.limit := limit;
      w := XmlWriter.ToWr (s, NoDeclaration);
      INC (count);
      TRY
        Write (w);
      EXCEPT
      | Wr.Failure => INC (raised);
      END;
      t := TextWr.ToText (s.data);
      IF Text.Equal (t, Text.Sub (full, 0, Text.Length (t))) THEN INC (prefix) END;
      TRY
        w.comment ("later");
      EXCEPT
      | XmlWriter.Error (f) => IF f.problem = Problem.Closed THEN INC (closed) END;
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
    w: XmlWriter.T;
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
    b := NEW (Blocked, w := XmlWriter.ToWr (s, NoDeclaration));
    th := Thread.Fork (b);
    Thread.Pause (0.5D0);
    Thread.Alert (th);
    EVAL Thread.Join (th);
    IO.Put (b.result & "\n");
    TRY
      b.w.comment ("later");
    EXCEPT
    | XmlWriter.Error (f) => IO.Put ("after Thread.Alerted: " & ProblemNames[f.problem] & "\n");
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
  VAR w := XmlWriter.Open ("sink.xml"); rd: Rd.T;
  BEGIN
    TRY
      w.start ("a");
      w.element ("b", Text.FromWideChar (W'\x00E4'));
      w.end ();
      w.finish ();
    FINALLY
      w.close ();
    END;
    rd := FileRd.Open ("sink.xml");
    IO.Put ("file: " & Esc (Rd.GetText (rd, 200)) & "\n");
    Rd.Close (rd);
    w := XmlWriter.Open ("sink2.xml");
    TRY
      w.start ("a");
      w.end ();
      w.end ();
    EXCEPT
    | XmlWriter.Error (f) => IO.Put ("error: " & XmlWriter.Format (f) & "\n");
    END;
    w.close ();
    IO.Put ("closed after an error\n");
  END SinkFile;

PROCEDURE Generate (w: XmlWriter.T; mb: INTEGER)
  RAISES {XmlWriter.Error, Wr.Failure, Thread.Alerted} =
  VAR left := mb * 1000 * 1000; n := 0; id: TEXT;
  BEGIN
    w.start ("root");
    w.text ("\n");
    WHILE left > 0 DO
      INC (n);
      id := Fmt.Int (n);
      DEC (left, 31 + 2 * Text.Length (id));
      w.start ("item");
      w.attr ("id", id);
      w.attr ("k", "v");
      w.text ("text " & id);
      w.end ();
      w.text ("\n");
    END;
    w.end ();
    w.finish ();
  END Generate;

PROCEDURE Speed (mb: INTEGER) =
  VAR s := NewSink (); t0: Time.T; secs: LONGREAL;
  BEGIN
    s.keep := FALSE;
    t0 := Time.Now ();
    Generate (XmlWriter.ToWr (s, NoDeclaration), mb);
    secs := Time.Now () - t0;
    IO.Put (Fmt.Int (mb) & " MB in " & Fmt.LongReal (secs, Fmt.Style.Fix, 2) & " s: "
              & Fmt.LongReal (FLOAT (s.got, LONGREAL) / 1.0D6 / secs, Fmt.Style.Fix, 1)
              & " MB/s\n");
  END Speed;

VAR mode := Params.Get (1);

BEGIN
  IF Text.Equal (mode, "batch") THEN
    Batch (Params.Get (2));
  ELSIF Text.Equal (mode, "names") THEN
    Names ();
  ELSIF Text.Equal (mode, "reals") THEN
    Reals (Scan.Int (Params.Get (2)));
  ELSIF Text.Equal (mode, "sink") THEN
    SinkFlushing ();
    SinkFailing ();
    SinkAlert ();
    SinkFile ();
  ELSIF Text.Equal (mode, "big") THEN
    Generate (XmlWriter.ToWr (Stdio.stdout, NoDeclaration), Scan.Int (Params.Get (2)));
  ELSIF Text.Equal (mode, "speed") THEN
    Speed (Scan.Int (Params.Get (2)));
  ELSE
    IO.Put ("unknown mode\n");
  END;
END Main.
