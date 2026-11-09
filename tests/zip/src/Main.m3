(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Tests of the library zip; tools/zip_test.py runs them.

     batch LIST          for each line of LIST, one of
                           d IN OUT FORMAT CHUNK MAX CONCAT
                           c IN OUT FORMAT CHUNK LEVEL FLUSHEVERY HOW
                         with the result in OUT.res
     zipread FILE OUT MAX  the entries of FILE into OUT, their data into OUT.bin
     zipwrite SPEC OUT   an archive from the lines of SPEC, one result per line
                         in OUT.res
     safe LIST           SafeName of each name of LIST, as digits
     feed                sources and targets that fail or block, the methods
     big MB OUT          writes a generated stream
     bigread IN MB       reads it back, comparing it
     bomb IN MAX         reads IN with a limit
     many N              N small streams, each freed
     speed IN            the rates *)

MODULE Main;

IMPORT Atom, AtomList, Deflate, FileRd, FileWr, Fmt, IO, NullWr, OSError, Params, Rd, RdClass, Scan, Text,
       TextRd, TextWr, Thread, Time, Wr, WrClass, Zip;
FROM Deflate IMPORT Format;

<*FATAL ANY*>

(* A reader that gives the bytes of another one in pieces of "size", or, for a
   "size" of 0, of the lengths 1, 2, 3, ... 17, 1, 2, ... *)
TYPE
  ChunkRd = Rd.T OBJECT
    src: Rd.T;
    size: CARDINAL;
    next := 1;
  OVERRIDES
    seek := ChunkSeek;
    length := NoLength;
  END;

PROCEDURE ChunkSeek (r: ChunkRd; <*UNUSED*> pos: CARDINAL; <*UNUSED*> dontBlock: BOOLEAN): RdClass.SeekResult
  RAISES {Rd.Failure, Thread.Alerted} =
  VAR size := r.size;  n: CARDINAL;
  BEGIN
    IF size = 0 THEN
      size := r.next;
      r.next := r.next MOD 17 + 1;
    END;
    n := Rd.GetSub (r.src, SUBARRAY (r.buff^, 0, MIN (size, NUMBER (r.buff^))));
    IF n = 0 THEN RETURN RdClass.SeekResult.Eof END;
    r.st := 0;
    r.lo := r.cur;
    r.hi := r.cur + n;
    RETURN RdClass.SeekResult.Ready;
  END ChunkSeek;

PROCEDURE NoLength (<*UNUSED*> r: Rd.T): INTEGER =
  BEGIN
    RETURN -1;
  END NoLength;

PROCEDURE Chunked (src: Rd.T; size: INTEGER): Rd.T =
  BEGIN
    IF size < 0 THEN RETURN src END;
    RETURN NEW (ChunkRd, src := src, size := size, buff := NEW (REF ARRAY OF CHAR, 70000), st := 0,
                lo := 0, hi := 0, cur := 0, intermittent := TRUE, seekable := FALSE, closed := FALSE);
  END Chunked;

(* The name of the kind of a failure. *)
PROCEDURE AtomName (e: AtomList.T): TEXT =
  BEGIN
    IF e = NIL THEN RETURN "NIL" END;
    RETURN Atom.ToText (e.head);
  END AtomName;

PROCEDURE Words (line: TEXT): REF ARRAY OF TEXT =
  VAR
    n := 1;  start := 0;  k := 0;  sp: INTEGER;  res: REF ARRAY OF TEXT;
  BEGIN
    FOR i := 0 TO Text.Length (line) - 1 DO
      IF Text.GetChar (line, i) = ' ' THEN INC (n) END;
    END;
    res := NEW (REF ARRAY OF TEXT, n);
    LOOP
      sp := Text.FindChar (line, ' ', start);
      IF sp < 0 THEN
        res[k] := Text.Sub (line, start);
        EXIT;
      END;
      res[k] := Text.Sub (line, start, sp - start);
      INC (k);
      start := sp + 1;
    END;
    RETURN res;
  END Words;

PROCEDURE FormatOf (t: TEXT): Format =
  BEGIN
    IF Text.Equal (t, "gzip") THEN RETURN Format.Gzip
    ELSIF Text.Equal (t, "zlib") THEN RETURN Format.Zlib
    ELSE RETURN Format.Raw
    END;
  END FormatOf;

PROCEDURE PutFile (path, text: TEXT) =
  VAR wr := FileWr.Open (path);
  BEGIN
    Wr.PutText (wr, text);
    Wr.Close (wr);
  END PutFile;

(* The rest of "rd" to "out"; with a limit byte by byte, because "Rd.GetSub"
   loses the bytes before a failure. *)
PROCEDURE Copy (rd: Rd.T; out: Wr.T; limited: BOOLEAN): CARDINAL RAISES ANY =
  VAR buf: ARRAY [0 .. 9999] OF CHAR;  n: CARDINAL;  total := 0;
  BEGIN
    TRY
      LOOP
        IF limited THEN
          TRY
            buf[0] := Rd.GetChar (rd);
            n := 1;
          EXCEPT
          | Rd.EndOfFile => n := 0;
          END;
        ELSE
          n := Rd.GetSub (rd, buf);
        END;
        IF n = 0 THEN EXIT END;
        Wr.PutString (out, SUBARRAY (buf, 0, n));
        INC (total, n);
      END;
    EXCEPT
    | Rd.Failure (e) => copied := total; RAISE Rd.Failure (e);
    END;
    copied := total;
    RETURN total;
  END Copy;

VAR copied := 0;   (* the bytes of the last "Copy", also after a failure *)

PROCEDURE Decompress (w: REF ARRAY OF TEXT) =
  VAR
    o := Deflate.ReadOptions {maxBytes := Scan.Int (w[5]), concatenated := Text.Equal (w[6], "1"),
                              closeSource := TRUE};
    file := FileRd.Open (w[1]);
    out := FileWr.Open (w[2]);
    rd := Deflate.NewRd (Chunked (file, Scan.Int (w[4])), FormatOf (w[3]), o);
    res := "OK";
  BEGIN
    TRY
      EVAL Copy (rd, out, o.maxBytes > 0);
      (* the end stays the end *)
      IF NOT Rd.EOF (rd) THEN res := "NOT AT THE END" END;
    EXCEPT
    | Rd.Failure (e) =>
        res := "ERROR " & AtomName (e);
        TRY
          EVAL Rd.GetChar (rd);
          res := "READ AFTER A FAILURE";
        EXCEPT
        | Rd.Failure (e2) => IF e2.head # e.head THEN res := "ANOTHER FAILURE" END;
        | Rd.EndOfFile => res := "END AFTER A FAILURE";
        END;
    END;
    Rd.Close (rd);
    Wr.Close (out);
    PutFile (w[2] & ".res", res & "\n");
  END Decompress;

PROCEDURE Compress (w: REF ARRAY OF TEXT) =
  VAR
    rd := FileRd.Open (w[1]);
    out := FileWr.Open (w[2]);
    marks := TextWr.New ();
    how := w[7];
    o := Deflate.WriteOptions {level := Scan.Int (w[5]), closeTarget := FALSE};
    wr := Deflate.NewWr (out, FormatOf (w[3]), o);
    chunk := MAX (1, Scan.Int (w[4]));
    every := Scan.Int (w[6]);
    buf := NEW (REF ARRAY OF CHAR, chunk);
    n: CARDINAL;
    pieces, written := 0;
  BEGIN
    LOOP
      n := Rd.GetSub (rd, buf^);
      IF n = 0 THEN EXIT END;
      Wr.PutString (wr, SUBARRAY (buf^, 0, n));
      INC (written, n);
      INC (pieces);
      IF every > 0 AND pieces MOD every = 0 THEN
        Wr.Flush (wr);
        Wr.PutText (marks, Fmt.Int (written) & " " & Fmt.Int (Wr.Index (out)) & "\n");
      END;
    END;
    IF Text.Equal (how, "finish") THEN Deflate.Finish (wr) END;
    Wr.Close (wr);
    Wr.Close (out);
    Rd.Close (rd);
    PutFile (w[2] & ".res", "OK\n");
    PutFile (w[2] & ".marks", TextWr.ToText (marks));
  END Compress;

PROCEDURE Batch (list: TEXT) =
  VAR rd := FileRd.Open (list);  w: REF ARRAY OF TEXT;
  BEGIN
    WHILE NOT Rd.EOF (rd) DO
      w := Words (Rd.GetLine (rd));
      IF Text.Equal (w[0], "d") THEN Decompress (w) ELSE Compress (w) END;
    END;
    Rd.Close (rd);
  END Batch;

PROCEDURE Hex (t: TEXT): TEXT =
  VAR wr := TextWr.New ();
  BEGIN
    FOR i := 0 TO Text.Length (t) - 1 DO
      Wr.PutText (wr, Fmt.Pad (Fmt.Int (ORD (Text.GetWideChar (t, i)), 16), 4, '0'));
    END;
    RETURN TextWr.ToText (wr);
  END Hex;

PROCEDURE Unhex (t: TEXT): TEXT =
  VAR a := NEW (REF ARRAY OF WIDECHAR, Text.Length (t) DIV 4);
  BEGIN
    FOR i := 0 TO LAST (a^) DO
      a[i] := VAL (Scan.Int (Text.Sub (t, 4 * i, 4), 16), WIDECHAR);
    END;
    RETURN Text.FromWideChars (a^);
  END Unhex;

PROCEDURE ZipRead (path, out: TEXT; max: INTEGER) =
  VAR
    wr := FileWr.Open (out);
    bin := FileWr.Open (out & ".bin");
    r: Zip.Reader;
    e: Zip.Entry;
    rd: Rd.T;
    len: CARDINAL;
    res: TEXT;
    found: CARDINAL;
  BEGIN
    TRY
      r := Zip.Open (path);
      Wr.PutText (wr, "N " & Fmt.Int (r.count ()) & "\n");
      FOR i := 0 TO r.count () - 1 DO
        e := r.entry (i);
        len := 0;
        res := "OK";
        TRY
          rd := r.open (i, maxBytes := max);
          TRY
            len := Copy (rd, bin, max > 0);
          EXCEPT
          | Rd.Failure (x) => res := AtomName (x);  len := copied;
          END;
          Rd.Close (rd);
        EXCEPT
        | Zip.Error (x) => res := AtomName (x);
        END;
        IF NOT r.find (e.name, found) OR found # i THEN res := "FIND" END;
        Wr.PutText (wr, "E " & Hex (e.name) & " " & Fmt.Int (e.size) & " " & Fmt.Int (e.compressedSize)
                          & " " & Fmt.Int (e.crc) & " " & Fmt.Bool (e.directory)
                          & " " & Fmt.LongReal (e.modified) & " " & Fmt.Int (len) & " " & res & "\n");
      END;
      IF r.find ("no such entry", found) THEN Wr.PutText (wr, "FOUND\n") END;
      r.close ();
    EXCEPT
    | Zip.Error (x) => Wr.PutText (wr, "ERROR " & AtomName (x) & "\n");
    | OSError.E => Wr.PutText (wr, "ERROR OSError\n");
    END;
    Wr.Close (wr);
    Wr.Close (bin);
  END ZipRead;

PROCEDURE ZipWrite (spec, out: TEXT) =
  VAR
    rd := FileRd.Open (spec);
    res := FileWr.Open (out & ".res");
    file := FileWr.Open (out);
    z := Zip.ToWr (file);
    w: REF ARRAY OF TEXT;
    wr: Wr.T;
    data: Rd.T;
  BEGIN
    WHILE NOT Rd.EOF (rd) DO
      w := Words (Rd.GetLine (rd));
      TRY
        wr := z.add (Unhex (w[0]), FLOAT (Scan.Int (w[1]), LONGREAL), Scan.Int (w[2]));
        IF Text.Length (w[3]) > 0 THEN
          data := FileRd.Open (w[3]);
          Wr.PutText (wr, Rd.GetText (data, LAST (CARDINAL)));
          Rd.Close (data);
        END;
        Wr.PutText (res, "OK\n");
      EXCEPT
      | Zip.Error (x) => Wr.PutText (res, "ERROR " & AtomName (x) & "\n");
      END;
    END;
    z.finish ();
    Wr.Close (file);
    Wr.Close (res);
    Rd.Close (rd);
  END ZipWrite;

PROCEDURE Safe (list: TEXT) =
  VAR rd := FileRd.Open (list);
  BEGIN
    WHILE NOT Rd.EOF (rd) DO
      IF Zip.SafeName (Unhex (Rd.GetLine (rd))) THEN IO.Put ("1") ELSE IO.Put ("0") END;
    END;
    IO.Put ("\n");
    Rd.Close (rd);
  END Safe;

(* feed *)

VAR
  SrcFailure := AtomList.List1 (Atom.FromText ("Test.Source"));
  DstFailure := AtomList.List1 (Atom.FromText ("Test.Target"));

TYPE
  (* The bytes of "data", one piece at a time; then a failure if "fail", or
     waits for an alert if "block", or the end. *)
  ScriptRd = Rd.T OBJECT
    data: TEXT;
    pos := 0;
    fail, block := FALSE;
    closes := 0;
  OVERRIDES
    seek := ScriptSeek;
    length := NoLength;
    close := ScriptClose;
  END;

PROCEDURE ScriptSeek (r: ScriptRd; <*UNUSED*> p: CARDINAL; dontBlock: BOOLEAN): RdClass.SeekResult
  RAISES {Rd.Failure, Thread.Alerted} =
  VAR n := MIN (NUMBER (r.buff^), Text.Length (r.data) - r.pos);
      mu: MUTEX;  c: Thread.Condition;
  BEGIN
    IF n > 0 THEN
      Text.SetChars (r.buff^, Text.Sub (r.data, r.pos, n));
      INC (r.pos, n);
      r.st := 0;
      r.lo := r.cur;
      r.hi := r.cur + n;
      RETURN RdClass.SeekResult.Ready;
    END;
    IF r.fail THEN RAISE Rd.Failure (SrcFailure) END;
    IF r.block THEN
      IF dontBlock THEN RETURN RdClass.SeekResult.WouldBlock END;
      mu := NEW (MUTEX);
      c := NEW (Thread.Condition);
      LOCK mu DO Thread.AlertWait (mu, c) END;
    END;
    RETURN RdClass.SeekResult.Eof;
  END ScriptSeek;

PROCEDURE ScriptClose (r: ScriptRd) =
  BEGIN
    INC (r.closes);
  END ScriptClose;

PROCEDURE Script (data: TEXT; fail, block := FALSE; piece := 1000): ScriptRd =
  BEGIN
    RETURN NEW (ScriptRd, data := data, fail := fail, block := block,
                buff := NEW (REF ARRAY OF CHAR, piece), st := 0, lo := 0, hi := 0, cur := 0,
                intermittent := TRUE, seekable := FALSE, closed := FALSE);
  END Script;

TYPE
  (* Takes "room" bytes, then fails. *)
  LimitWr = Wr.T OBJECT
    room: INTEGER;
    out: TextWr.T;
    flushes, closes := 0;
  OVERRIDES
    seek := LimitSeek;
    flush := LimitFlush;
    close := LimitClose;
  END;

PROCEDURE Spend (w: LimitWr) RAISES {Wr.Failure, Thread.Alerted} =
  VAR n := w.cur - w.lo;
  BEGIN
    IF n > w.room THEN RAISE Wr.Failure (DstFailure) END;
    DEC (w.room, n);
    Wr.PutString (w.out, SUBARRAY (w.buff^, 0, n));
    w.lo := w.cur;
    w.hi := w.lo + NUMBER (w.buff^);
  END Spend;

PROCEDURE LimitSeek (w: LimitWr; <*UNUSED*> n: CARDINAL) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    Spend (w);
  END LimitSeek;

PROCEDURE LimitFlush (w: LimitWr) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    Spend (w);
    INC (w.flushes);
  END LimitFlush;

PROCEDURE LimitClose (w: LimitWr) =
  BEGIN
    INC (w.closes);
  END LimitClose;

PROCEDURE Limited (room: INTEGER): LimitWr =
  BEGIN
    RETURN NEW (LimitWr, room := room, out := TextWr.New (), buff := NEW (REF ARRAY OF CHAR, 256),
                st := 0, lo := 0, cur := 0, hi := 256, buffered := TRUE, seekable := FALSE, closed := FALSE);
  END Limited;

PROCEDURE Pack (t: TEXT; format := Format.Gzip): TEXT =
  VAR out := TextWr.New ();  wr := Deflate.NewWr (out, format);
  BEGIN
    Wr.PutText (wr, t);
    Wr.Close (wr);
    RETURN TextWr.ToText (out);
  END Pack;

PROCEDURE Noise (n: INTEGER): TEXT =
  VAR wr := TextWr.New ();  x := 12345;
  BEGIN
    FOR i := 1 TO n DO
      x := (x * 1103515245 + 12345) MOD 2147483648;
      Wr.PutChar (wr, VAL (x DIV 65536 MOD 256, CHAR));
    END;
    RETURN TextWr.ToText (wr);
  END Noise;

PROCEDURE Say (t: TEXT) =
  BEGIN
    IO.Put (t & "\n");
  END Say;

PROCEDURE ReadAll (rd: Rd.T): TEXT =
  BEGIN
    TRY
      RETURN "text " & Fmt.Int (Text.Length (Rd.GetText (rd, LAST (CARDINAL))));
    EXCEPT
    | Rd.Failure (e) => RETURN "failure " & AtomName (e);
    | Thread.Alerted => RETURN "alerted";
    END;
  END ReadAll;

PROCEDURE PutCatch (wr: Wr.T; t: TEXT): TEXT =
  BEGIN
    TRY
      Wr.PutText (wr, t);
      RETURN "ok";
    EXCEPT
    | Wr.Failure (e) => RETURN "failure " & AtomName (e);
    END;
  END PutCatch;

PROCEDURE FlushCatch (wr: Wr.T): TEXT =
  BEGIN
    TRY
      Wr.Flush (wr);
      RETURN "ok";
    EXCEPT
    | Wr.Failure (e) => RETURN "failure " & AtomName (e);
    END;
  END FlushCatch;

PROCEDURE CloseCatch (wr: Wr.T): TEXT =
  BEGIN
    TRY
      Wr.Close (wr);
      RETURN "ok";
    EXCEPT
    | Wr.Failure (e) => RETURN "failure " & AtomName (e);
    END;
  END CloseCatch;

TYPE
  Alerter = Thread.Closure OBJECT
    target: Thread.T;
  OVERRIDES
    apply := AlertLater;
  END;

PROCEDURE AlertLater (a: Alerter): REFANY =
  BEGIN
    Thread.Pause (0.3D0);
    Thread.Alert (a.target);
    RETURN NIL;
  END AlertLater;

PROCEDURE Feed () =
  VAR
    plain := Noise (100000);
    gz := Pack (plain);
    rd: Rd.T;
    src: ScriptRd;
    lim: LimitWr;
    wr: Wr.T;
    out: TextWr.T;
    part: TEXT;
  BEGIN
    (* the source fails *)
    Say ("source failure in the header: " & ReadAll (Deflate.NewRd (Script (Text.Sub (gz, 0, 5), fail := TRUE))));
    Say ("source failure in the data: " & ReadAll (Deflate.NewRd (Script (Text.Sub (gz, 0, 50000), fail := TRUE))));

    (* an alert while waiting for the source *)
    src := Script (Text.Sub (gz, 0, 50000), block := TRUE);
    EVAL Thread.Fork (NEW (Alerter, target := Thread.Self ()));
    Say ("alert: " & ReadAll (Deflate.NewRd (src)));

    (* the source is closed only if asked *)
    src := Script (gz);
    rd := Deflate.NewRd (src);
    Rd.Close (rd);
    Say ("closes without closeSource: " & Fmt.Int (src.closes));
    src := Script (gz);
    rd := Deflate.NewRd (src, o := Deflate.ReadOptions {closeSource := TRUE});
    Rd.Close (rd);
    Say ("closes with closeSource: " & Fmt.Int (src.closes));

    (* the class of the reader *)
    rd := Deflate.NewRd (TextRd.New (gz));
    Say ("seekable: " & Fmt.Bool (Rd.Seekable (rd)) & ", intermittent: " & Fmt.Bool (Rd.Intermittent (rd))
           & ", length: " & Fmt.Int (Rd.Length (rd)));
    Say ("first: " & Fmt.Bool (Rd.GetChar (rd) = Text.GetChar (plain, 0)));
    Rd.UnGetChar (rd);
    Say ("again: " & Fmt.Bool (Rd.GetChar (rd) = Text.GetChar (plain, 0)));
    Say ("ready: " & Fmt.Bool (Rd.CharsReady (rd) > 0));
    Say ("rest: " & ReadAll (rd));
    Say ("ready at the end: " & Fmt.Int (Rd.CharsReady (rd)));
    Say ("eof: " & Fmt.Bool (Rd.EOF (rd)));
    Rd.Close (rd);

    (* the bytes that have arrived can be read while more is awaited *)
    part := Pack ("complete");
    src := Script (part, block := TRUE, piece := 3);
    rd := Deflate.NewRd (src);
    FOR i := 1 TO 8 DO EVAL Rd.GetChar (rd) END;
    Say ("after the data of a member, with the source waiting, ready: " & Fmt.Int (Rd.CharsReady (rd)));
    Rd.Close (rd);

    (* without "concatenated" the reader does not wait for more *)
    src := Script (part, block := TRUE, piece := 3);
    rd := Deflate.NewRd (src, o := Deflate.ReadOptions {concatenated := FALSE});
    Say ("one member from a waiting source: " & ReadAll (rd));

    (* the target fails *)
    lim := Limited (100);
    wr := Deflate.NewWr (lim);
    Say ("put to a failing target: " & PutCatch (wr, plain));
    Say ("again: " & PutCatch (wr, plain));
    Say ("flush: " & FlushCatch (wr));
    Say ("close: " & CloseCatch (wr));

    (* the target is closed only if asked *)
    lim := Limited (1000000);
    wr := Deflate.NewWr (lim);
    Wr.PutText (wr, "x");
    Wr.Close (wr);
    Say ("flushes, closes: " & Fmt.Int (lim.flushes) & " " & Fmt.Int (lim.closes));
    lim := Limited (1000000);
    wr := Deflate.NewWr (lim, o := Deflate.WriteOptions {closeTarget := TRUE});
    Wr.PutText (wr, "x");
    Wr.Close (wr);
    Say ("flushes, closes with closeTarget: " & Fmt.Int (lim.flushes) & " " & Fmt.Int (lim.closes));

    (* Finish does not flush *)
    lim := Limited (1000000);
    wr := Deflate.NewWr (lim);
    Wr.PutText (wr, "x");
    Deflate.Finish (wr);
    Say ("flushes after Finish: " & Fmt.Int (lim.flushes));
    Deflate.Finish (wr);
    Say ("put after Finish: " & PutCatch (wr, plain));
    Say ("flush after Finish: " & FlushCatch (wr));
    Say ("close after Finish: " & CloseCatch (wr));

    (* Discard *)
    out := TextWr.New ();
    wr := Deflate.NewWr (out);
    Wr.PutText (wr, "x");
    Deflate.Discard (wr);
    Say ("put after Discard: " & PutCatch (wr, plain));
    Deflate.Discard (wr);
    Say ("close after Discard: " & CloseCatch (wr));
    Say ("bytes after Discard: " & Fmt.Int (Text.Length (TextWr.ToText (out))));

    (* two flushes in a row add nothing the second time *)
    out := TextWr.New ();
    wr := Deflate.NewWr (out);
    Wr.PutText (wr, "x");
    Wr.Flush (wr);
    part := TextWr.ToText (out);
    Wr.Flush (wr);
    Say ("bytes of a second flush: " & Fmt.Int (Text.Length (TextWr.ToText (out))));
    Wr.Close (wr);
    Say ("flushed stream: " & Fmt.Int (Text.Length (part)) & " " & ReadAll (Deflate.NewRd (TextRd.New (part & TextWr.ToText (out)))));

    FeedZip ();
  END Feed;

PROCEDURE FeedZip () =
  VAR
    file := TextWr.New ();
    z := Zip.ToWr (file);
    a, b: Wr.T;
    lim: LimitWr;
    rd1, rd2: Rd.T;
    r: Zip.Reader;
    path := "feed.zip";
    out: Wr.T;
    bytes: TEXT;
  BEGIN
    a := z.add ("a.txt", level := 9);
    Wr.PutText (a, "first");
    b := z.add ("b.txt");
    Say ("put to an ended entry: " & PutCatch (a, Noise (1000)));
    Say ("flush of an ended entry: " & FlushCatch (a));
    Wr.PutText (b, "second");
    Wr.Close (b);
    Say ("put to a closed entry: " & Fmt.Bool (Wr.Closed (b)));
    TRY
      EVAL z.add ("a.txt");
      Say ("no error for a second a.txt");
    EXCEPT
    | Zip.Error (e) => Say ("second a.txt: " & AtomName (e));
    END;
    a := z.add ("c.txt");
    Wr.PutText (a, "third");
    z.finish ();
    z.finish ();
    Say ("put after finish: " & PutCatch (a, Noise (1000)));
    TRY
      EVAL z.add ("d.txt");
      Say ("no error after finish");
    EXCEPT
    | Wr.Failure (e) => Say ("add after finish: " & AtomName (e));
    END;

    bytes := TextWr.ToText (file);
    out := FileWr.Open (path);
    Wr.PutText (out, bytes);
    Wr.Close (out);
    r := Zip.Open (path);
    Say ("entries: " & Fmt.Int (r.count ()));
    rd1 := r.open (0);
    rd2 := r.open (1);
    Say ("two at a time: " & Fmt.Char (Rd.GetChar (rd1)) & Fmt.Char (Rd.GetChar (rd2)));
    Say ("rest: " & ReadAll (rd1) & ", " & ReadAll (rd2));
    Say ("length: " & Fmt.Int (Rd.Length (rd1)));
    rd1 := r.open (2);
    r.close ();
    Say ("read after close: " & ReadAll (rd1));
    TRY
      EVAL r.open (0);
      Say ("no error for open after close");
    EXCEPT
    | Zip.Error (e) => Say ("open after close: " & AtomName (e));
    END;

    lim := Limited (10);
    z := Zip.ToWr (lim);
    TRY
      a := z.add ("a.txt");
      Wr.PutText (a, Noise (100000));
      z.finish ();
      Say ("no failure of the target");
    EXCEPT
    | Wr.Failure (e) => Say ("target of an archive: " & AtomName (e));
    END;
    TRY
      EVAL z.add ("b.txt");
      Say ("no failure after one");
    EXCEPT
    | Wr.Failure (e) => Say ("add after a failure: " & AtomName (e));
    END;
    TRY
      z.finish ();
      Say ("no failure of finish");
    EXCEPT
    | Wr.Failure (e) => Say ("finish after a failure: " & AtomName (e));
    END;
  END FeedZip;

(* big *)

CONST Mega = 1048576;

(* Fills "a" with the next bytes of the generator: pieces that repeat, so that
   the stream compresses and yet is not trivial. *)
PROCEDURE Generate (VAR a: ARRAY OF CHAR; VAR x: INTEGER) =
  BEGIN
    FOR i := 0 TO LAST (a) DO
      IF i MOD 64 = 0 THEN x := (x * 1103515245 + 12345) MOD 2147483648 END;
      a[i] := VAL (x DIV 65536 MOD 64 + 32, CHAR);
    END;
  END Generate;

PROCEDURE Big (mb: INTEGER; path: TEXT) =
  VAR
    file := FileWr.Open (path);
    wr := Deflate.NewWr (file);
    buf := NEW (REF ARRAY OF CHAR, Mega);
    x := 1;
  BEGIN
    FOR i := 1 TO mb DO
      Generate (buf^, x);
      Wr.PutString (wr, buf^);
    END;
    Wr.Close (wr);
    Wr.Close (file);
  END Big;

PROCEDURE BigRead (path: TEXT; mb: INTEGER) RAISES ANY =
  VAR
    rd := Deflate.NewRd (FileRd.Open (path), o := Deflate.ReadOptions {closeSource := TRUE});
    want := NEW (REF ARRAY OF CHAR, Mega);
    got := NEW (REF ARRAY OF CHAR, Mega);
    x := 1;
    n: CARDINAL;
  BEGIN
    FOR i := 1 TO mb DO
      Generate (want^, x);
      n := Rd.GetSub (rd, got^);
      IF n # Mega OR want^ # got^ THEN
        Say ("DIFFERENT in megabyte " & Fmt.Int (i));
        RETURN;
      END;
    END;
    IF Rd.GetChar (rd) = 'x' THEN Say ("x") END;
  END BigRead;

PROCEDURE BigReadCatch (path: TEXT; mb: INTEGER) =
  BEGIN
    TRY
      BigRead (path, mb);
      Say ("MORE THAN EXPECTED");
    EXCEPT
    | Rd.EndOfFile => Say ("OK");
    END;
  END BigReadCatch;

PROCEDURE Bomb (path: TEXT; max: INTEGER) =
  VAR
    rd := Deflate.NewRd (FileRd.Open (path), o := Deflate.ReadOptions {maxBytes := max, closeSource := TRUE});
    sink := NEW (NullWr.T).init ();
  BEGIN
    TRY
      Say ("END " & Fmt.Int (Copy (rd, sink, max > 0)));
    EXCEPT
    | Rd.Failure (e) => Say (AtomName (e) & " " & Fmt.Int (copied));
    END;
    Rd.Close (rd);
  END Bomb;

PROCEDURE Many (n: INTEGER) =
  VAR
    sum := 0;
    data := "some text that is compressed";
  BEGIN
    FOR i := 1 TO n DO
      sum := sum + Text.Length (Rd.GetText (Deflate.NewRd (TextRd.New (Pack (data))), 1000));
    END;
    Say (Fmt.Int (sum));
  END Many;

PROCEDURE Speed (path: TEXT) =
  VAR
    rd := FileRd.Open (path);
    text := Rd.GetText (rd, LAST (CARDINAL));
    len := FLOAT (Text.Length (text), LONGREAL) / FLOAT (Mega, LONGREAL);
    out: TextWr.T;
    gz: TEXT;
    t0: Time.T;
    best := 0.0D0;
    wr: Wr.T;
    res: TEXT;
    buf: ARRAY [0 .. 65535] OF CHAR;
    r: Rd.T;
  BEGIN
    Rd.Close (rd);
    FOR pass := 1 TO 3 DO
      out := TextWr.New ();
      t0 := Time.Now ();
      wr := Deflate.NewWr (out);
      Wr.PutText (wr, text);
      Wr.Close (wr);
      gz := TextWr.ToText (out);
      best := MAX (best, len / (Time.Now () - t0));
    END;
    res := "compress " & Fmt.LongReal (best, Fmt.Style.Fix, 1) & " MB/s, ratio "
             & Fmt.LongReal (FLOAT (Text.Length (gz), LONGREAL) / FLOAT (Text.Length (text), LONGREAL),
                             Fmt.Style.Fix, 3);
    best := 0.0D0;
    FOR pass := 1 TO 3 DO
      t0 := Time.Now ();
      r := Deflate.NewRd (TextRd.New (gz));
      WHILE Rd.GetSub (r, buf) > 0 DO END;
      best := MAX (best, len / (Time.Now () - t0));
    END;
    Say (res & ", decompress " & Fmt.LongReal (best, Fmt.Style.Fix, 1) & " MB/s");
  END Speed;

VAR cmd := Params.Get (1);

BEGIN
  IF Text.Equal (cmd, "batch") THEN Batch (Params.Get (2));
  ELSIF Text.Equal (cmd, "zipread") THEN ZipRead (Params.Get (2), Params.Get (3), Scan.Int (Params.Get (4)));
  ELSIF Text.Equal (cmd, "zipwrite") THEN ZipWrite (Params.Get (2), Params.Get (3));
  ELSIF Text.Equal (cmd, "safe") THEN Safe (Params.Get (2));
  ELSIF Text.Equal (cmd, "feed") THEN Feed ();
  ELSIF Text.Equal (cmd, "big") THEN Big (Scan.Int (Params.Get (2)), Params.Get (3));
  ELSIF Text.Equal (cmd, "bigread") THEN BigReadCatch (Params.Get (2), Scan.Int (Params.Get (3)));
  ELSIF Text.Equal (cmd, "bomb") THEN Bomb (Params.Get (2), Scan.Int (Params.Get (3)));
  ELSIF Text.Equal (cmd, "many") THEN Many (Scan.Int (Params.Get (2)));
  ELSIF Text.Equal (cmd, "speed") THEN Speed (Params.Get (2));
  ELSE IO.Put ("unknown command\n");
  END;
END Main.
