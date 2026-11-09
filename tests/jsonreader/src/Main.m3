(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Reads documents with JsonReader for tools/json_test.py.

     batch LIST OUT      each line of LIST is a document with its options; OUT
                         gets the pieces of each, and how it ended
     api SCRIPT          calls methods one by one, and prints what comes of it
     numbers LIST OUT    each line of LIST is a number, read in every way
     feed FILE           readers that deliver little, fail or block
     big FILE            checks a generated document record by record
     speed FILE          the rate of reading FILE *)

MODULE Main;

IMPORT FileRd, Fmt, FileWr, Json, JsonReader, JsonWriter, Params, Random, Rd, RdClass, Stdio,
       Scan, Text, TextWr, Thread, Time, Wr;

FROM JsonReader IMPORT Kind;

<*FATAL ANY*>

CONST
  Random1 = -1;                 (* delivered in pieces of random sizes *)
  FromTheText = -2;             (* the document is a text of UTF-16 units *)

VAR
  ProblemNames := ARRAY JsonReader.Problem OF TEXT {"Malformed", "Encoding", "Limit",
                                                    "Unexpected", "BadValue", "Closed"};
  KindNames := ARRAY Kind OF TEXT {"Object", "ObjectEnd", "Array", "ArrayEnd", "Key", "Text",
                                   "Number", "Bool", "Null", "EOF"};

TYPE
  Bytes = REF ARRAY OF CHAR;

  (* A reader that delivers data in pieces, like a connection, and fails or
     waits for good at a place. *)
  Feed = Rd.T OBJECT
    data: Bytes;
    size := 0;                  (* of a piece; Random1 for a random one *)
    rand: Random.T := NIL;
    failAt := -1;
    blockAt := -1;
    reached := FALSE;           (* it has been asked for what lies at blockAt *)
  OVERRIDES
    seek := FeedSeek;
    length := FeedLength;
  END;

PROCEDURE FeedSeek (f: Feed; <*UNUSED*> pos: CARDINAL; dontBlock: BOOLEAN): RdClass.SeekResult
  RAISES {Rd.Failure, Thread.Alerted} =
  VAR start := f.hi; m: INTEGER;
  BEGIN
    IF start = f.failAt THEN RAISE Rd.Failure (NIL) END;
    IF start = f.blockAt THEN
      IF dontBlock THEN RETURN RdClass.SeekResult.WouldBlock END;
      f.reached := TRUE;
      LOOP Thread.AlertPause (3600.0D0) END;
    END;
    IF start >= NUMBER (f.data^) THEN RETURN RdClass.SeekResult.Eof END;
    IF f.size = Random1 THEN m := f.rand.integer (1, 9); ELSE m := f.size; END;
    m := MIN (m, NUMBER (f.data^) - start);
    IF f.failAt > start THEN m := MIN (m, f.failAt - start) END;
    IF f.blockAt > start THEN m := MIN (m, f.blockAt - start) END;
    f.buff := f.data;
    f.st := start;
    f.lo := start;
    f.hi := start + m;
    f.cur := start;
    RETURN RdClass.SeekResult.Ready;
  END FeedSeek;

PROCEDURE FeedLength (f: Feed): INTEGER =
  BEGIN
    RETURN NUMBER (f.data^);
  END FeedLength;

PROCEDURE NewFeed (data: Bytes; size: INTEGER; failAt, blockAt := -1): Feed =
  BEGIN
    RETURN NEW (Feed, data := data, size := size, failAt := failAt, blockAt := blockAt,
                rand := NEW (Random.Default).init (fixed := TRUE),
                buff := NIL, st := 0, lo := 0, hi := 0, cur := 0,
                intermittent := TRUE, seekable := FALSE, closed := FALSE);
  END NewFeed;

PROCEDURE Fields (line: TEXT; VAR a: ARRAY OF TEXT): CARDINAL =
  VAR n := 0; i := 0; j: INTEGER; len := Text.Length (line);
  BEGIN
    WHILE n < NUMBER (a) AND i <= len DO
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

PROCEDURE FromHex (h: TEXT): Bytes =
  VAR a := NEW (Bytes, Text.Length (h) DIV 2);
  BEGIN
    FOR i := 0 TO LAST (a^) DO
      a[i] := VAL (Digit (Text.GetChar (h, 2 * i)) * 16 + Digit (Text.GetChar (h, 2 * i + 1)), CHAR);
    END;
    RETURN a;
  END FromHex;

PROCEDURE UnitsFromHex (h: TEXT): TEXT =
  VAR a := NEW (REF ARRAY OF WIDECHAR, Text.Length (h) DIV 4); v: INTEGER;
  BEGIN
    FOR i := 0 TO LAST (a^) DO
      v := 0;
      FOR j := 0 TO 3 DO v := v * 16 + Digit (Text.GetChar (h, 4 * i + j)) END;
      a[i] := VAL (v, WIDECHAR);
    END;
    RETURN Text.FromWideChars (a^);
  END UnitsFromHex;

PROCEDURE Hex (t: TEXT): TEXT =
  VAR wr := TextWr.New ();
  BEGIN
    FOR i := 0 TO Text.Length (t) - 1 DO
      Wr.PutText (wr, Fmt.Pad (Fmt.Int (ORD (Text.GetWideChar (t, i)), 16), 4, '0'));
    END;
    RETURN TextWr.ToText (wr);
  END Hex;

PROCEDURE HexBytes (t: TEXT): TEXT =
  VAR wr := TextWr.New ();
  BEGIN
    FOR i := 0 TO Text.Length (t) - 1 DO
      Wr.PutText (wr, Fmt.Pad (Fmt.Int (ORD (Text.GetChar (t, i)), 16), 2, '0'));
    END;
    RETURN TextWr.ToText (wr);
  END HexBytes;

PROCEDURE Where (f: JsonReader.Failure): TEXT =
  BEGIN
    RETURN ProblemNames[f.problem] & " " & Fmt.Int (f.line) & " " & Fmt.Int (f.column) & " "
             & Fmt.Int (f.offset);
  END Where;

PROCEDURE Events (r: JsonReader.T; out: Wr.T) RAISES ANY =
  BEGIN
    LOOP
      CASE r.peek () OF
      | Kind.Object => r.object (); Wr.PutText (out, "{\n");
      | Kind.ObjectEnd => EVAL r.member (); Wr.PutText (out, "}\n");
      | Kind.Array => r.array (); Wr.PutText (out, "[\n");
      | Kind.ArrayEnd => EVAL r.element (); Wr.PutText (out, "]\n");
      | Kind.Key => EVAL r.member (); Wr.PutText (out, "K " & Hex (r.name ()) & "\n");
      | Kind.Text => Wr.PutText (out, "T " & Hex (r.text ()) & "\n");
      | Kind.Number => Wr.PutText (out, "N " & r.number () & "\n");
      | Kind.Bool => Wr.PutText (out, "B " & Fmt.Bool (r.bool ()) & "\n");
      | Kind.Null => r.null (); Wr.PutText (out, "Z\n");
      | Kind.EOF => r.finish (); EXIT;
      END;
    END;
  END Events;

(* What was read is written again, in the shortest form. *)
PROCEDURE Echo (r: JsonReader.T; out: Wr.T) RAISES ANY =
  VAR wr := TextWr.New (); w := JsonWriter.ToWr (wr, JsonWriter.Options {maxDepth := 0, uniqueKeys := FALSE});
  BEGIN
    LOOP
      CASE r.peek () OF
      | Kind.Object => r.object (); w.object ();
      | Kind.ObjectEnd => EVAL r.member (); w.end ();
      | Kind.Array => r.array (); w.array ();
      | Kind.ArrayEnd => EVAL r.element (); w.end ();
      | Kind.Key => EVAL r.member (); w.key (r.name ());
      | Kind.Text => w.text (r.text ());
      | Kind.Number => w.number (r.number ());
      | Kind.Bool => w.bool (r.bool ());
      | Kind.Null => r.null (); w.null ();
      | Kind.EOF => r.finish (); EXIT;
      END;
    END;
    w.finish ();
    Wr.PutText (out, "W " & HexBytes (TextWr.ToText (wr)) & "\n");
  END Echo;

PROCEDURE ReaderOf (chunk: INTEGER; READONLY o: JsonReader.Options; h: TEXT): JsonReader.T =
  BEGIN
    IF chunk = FromTheText THEN RETURN JsonReader.FromText (UnitsFromHex (h), o) END;
    RETURN JsonReader.FromRd (NewFeed (FromHex (h), chunk), o);
  END ReaderOf;

PROCEDURE OptionsOf (replace, depth, str, num: TEXT): JsonReader.Options =
  VAR o := JsonReader.Defaults;
  BEGIN
    o.replaceInvalid := Text.Equal (replace, "1");
    o.maxDepth := Scan.Int (depth);
    o.maxStringChars := Scan.Int (str);
    o.maxNumberChars := Scan.Int (num);
    RETURN o;
  END OptionsOf;

PROCEDURE Batch (list, outName: TEXT) =
  VAR
    rd := FileRd.Open (list);
    out := FileWr.Open (outName);
    f: ARRAY [0 .. 6] OF TEXT;
    r: JsonReader.T;
  BEGIN
    WHILE NOT Rd.EOF (rd) DO
      EVAL Fields (Rd.GetLine (rd), f);
      r := ReaderOf (Scan.Int (f[0]), OptionsOf (f[1], f[2], f[3], f[4]), f[6]);
      TRY
        IF Text.Equal (f[5], "e") THEN
          Events (r, out);
        ELSIF Text.Equal (f[5], "s") THEN
          r.skip ();
          r.finish ();
        ELSE
          Echo (r, out);
        END;
        Wr.PutText (out, "OK\n");
      EXCEPT
      | JsonReader.Error (x) =>
          Wr.PutText (out, "ERROR " & Where (x) & "\n");
          TRY
            EVAL r.peek ();
            Wr.PutText (out, "AFTER none\n");
          EXCEPT
          | JsonReader.Error (y) =>
              Wr.PutText (out, "AFTER " & ProblemNames[y.problem] & "\n");
          END;
      END;
      Wr.PutText (out, ".\n");
    END;
    Wr.Close (out);
    Rd.Close (rd);
  END Batch;

PROCEDURE Do (r: JsonReader.T; READONLY f: ARRAY OF TEXT): TEXT =
  VAR op := f[0];
  BEGIN
    TRY
      IF Text.Equal (op, "peek") THEN RETURN KindNames[r.peek ()];
      ELSIF Text.Equal (op, "object") THEN r.object ();
      ELSIF Text.Equal (op, "array") THEN r.array ();
      ELSIF Text.Equal (op, "member") THEN RETURN Fmt.Bool (r.member ());
      ELSIF Text.Equal (op, "element") THEN RETURN Fmt.Bool (r.element ());
      ELSIF Text.Equal (op, "end") THEN r.end ();
      ELSIF Text.Equal (op, "name") THEN RETURN Hex (r.name ());
      ELSIF Text.Equal (op, "is") THEN RETURN Fmt.Bool (r.is (UnitsFromHex (f[1])));
      ELSIF Text.Equal (op, "text") THEN RETURN Hex (r.text ());
      ELSIF Text.Equal (op, "int") THEN RETURN Fmt.Int (r.int ());
      ELSIF Text.Equal (op, "real") THEN RETURN Fmt.LongReal (r.real ());
      ELSIF Text.Equal (op, "number") THEN RETURN r.number ();
      ELSIF Text.Equal (op, "bool") THEN RETURN Fmt.Bool (r.bool ());
      ELSIF Text.Equal (op, "null") THEN r.null ();
      ELSIF Text.Equal (op, "skip") THEN r.skip ();
      ELSIF Text.Equal (op, "finish") THEN r.finish ();
      ELSIF Text.Equal (op, "close") THEN r.close ();
      ELSIF Text.Equal (op, "depth") THEN RETURN Fmt.Int (r.depth ());
      ELSIF Text.Equal (op, "pos") THEN
        RETURN Fmt.Int (r.line ()) & " " & Fmt.Int (r.column ()) & " " & Fmt.Int (r.offset ());
      ELSE
        RETURN "BAD OPERATION";
      END;
      RETURN "ok";
    EXCEPT
    | JsonReader.Error (x) => RETURN "ERROR " & Where (x);
    END;
  END Do;

(* DOC chunk replace depth string number hex, then one call on each line, then
   END. *)
PROCEDURE Api (script: TEXT) =
  VAR
    rd := FileRd.Open (script);
    f: ARRAY [0 .. 6] OF TEXT;
    r: JsonReader.T;
  BEGIN
    WHILE NOT Rd.EOF (rd) DO
      EVAL Fields (Rd.GetLine (rd), f);
      IF Text.Equal (f[0], "DOC") THEN
        r := ReaderOf (Scan.Int (f[1]), OptionsOf (f[2], f[3], f[4], f[5]), f[6]);
      ELSIF Text.Equal (f[0], "END") THEN
        Wr.PutText (Stdio.stdout, ".\n");
      ELSE
        Wr.PutText (Stdio.stdout, Do (r, f) & "\n");
      END;
    END;
    Rd.Close (rd);
    Wr.Flush (Stdio.stdout);
  END Api;

PROCEDURE Numbers (list, outName: TEXT) =
  VAR
    rd := FileRd.Open (list);
    out := FileWr.Open (outName);
    line: TEXT;
  BEGIN
    WHILE NOT Rd.EOF (rd) DO
      line := Rd.GetLine (rd);
      TRY
        Wr.PutText (out, "I " & Fmt.Int (JsonReader.FromText (line).int ()));
      EXCEPT
      | JsonReader.Error (x) => Wr.PutText (out, "I " & ProblemNames[x.problem]);
      END;
      TRY
        Wr.PutText (out, " R " & Fmt.LongReal (JsonReader.FromText (line).real ()));
      EXCEPT
      | JsonReader.Error (x) => Wr.PutText (out, " R " & ProblemNames[x.problem]);
      END;
      TRY
        WITH r = JsonReader.FromText (line), n = r.number () DO
          r.finish ();
          Wr.PutText (out, " N " & n);
        END;
      EXCEPT
      | JsonReader.Error (x) => Wr.PutText (out, " N " & ProblemNames[x.problem]);
      END;
      Wr.PutText (out, "\n");
    END;
    Wr.Close (out);
    Rd.Close (rd);
  END Numbers;

PROCEDURE Say (t: TEXT) =
  BEGIN
    Wr.PutText (Stdio.stdout, t & "\n");
    Wr.Flush (Stdio.stdout);
  END Say;

PROCEDURE BytesOf (t: TEXT): Bytes =
  VAR a := NEW (Bytes, Text.Length (t));
  BEGIN
    Text.SetChars (a^, t);
    RETURN a;
  END BytesOf;

(* A failure of the input at each place: the reader ends, as with an error of
   the document, and every piece before that place has been read. *)
PROCEDURE Failing () =
  CONST Doc = "{\"a\": [1, \"\\u00e4x\", true, null], \"b\": 12.5e3}";
  VAR
    data := BytesOf (Doc);
    wrong := 0;
    r: JsonReader.T;
    feed: Feed;
    failed: BOOLEAN;
  BEGIN
    FOR at := 0 TO NUMBER (data^) - 1 DO
      feed := NewFeed (data, 3, failAt := at);
      r := JsonReader.FromRd (feed);
      failed := FALSE;
      TRY
        r.skip ();
      EXCEPT
      | Rd.Failure => failed := TRUE;
      END;
      IF NOT failed THEN INC (wrong) END;
      TRY
        EVAL r.peek ();
        INC (wrong);
      EXCEPT
      | JsonReader.Error (x) => IF x.problem # JsonReader.Problem.Closed THEN INC (wrong) END;
      END;
    END;
    Say ("failure at each place: wrong " & Fmt.Int (wrong));
  END Failing;

TYPE
  Blocked = Thread.Closure OBJECT
    r: JsonReader.T;
    result: TEXT := "none";
  OVERRIDES
    apply := BlockedApply;
  END;

PROCEDURE BlockedApply (self: Blocked): REFANY =
  BEGIN
    TRY
      self.r.finish ();
      self.result := "finished";
    EXCEPT
    | Thread.Alerted => self.result := "alerted";
    END;
    RETURN NIL;
  END BlockedApply;

(* A whole value comes in without the reader waiting for more, and only finish
   waits for the end of the connection. *)
PROCEDURE Connection () =
  CONST Doc = "[1, {\"a\": \"b\"}]";
  VAR
    feed := NewFeed (BytesOf (Doc), 4, blockAt := Text.Length (Doc));
    r := JsonReader.FromRd (feed);
    b: Blocked;
    th: Thread.T;
  BEGIN
    r.skip ();
    Say ("value read: depth " & Fmt.Int (r.depth ()) & ", waiting "
           & Fmt.Bool (feed.reached));
    b := NEW (Blocked, r := r);
    th := Thread.Fork (b);
    WHILE NOT feed.reached DO Thread.Pause (0.01D0) END;
    Thread.Alert (th);
    EVAL Thread.Join (th);
    Say ("finish: " & b.result);
    TRY
      EVAL r.peek ();
      Say ("after: none");
    EXCEPT
    | JsonReader.Error (x) => Say ("after: " & ProblemNames[x.problem]);
    END;
  END Connection;

PROCEDURE Files (path: TEXT) =
  VAR r := JsonReader.Open (path); rd := FileRd.Open (path); q: JsonReader.T;
  BEGIN
    r.skip ();
    r.finish ();
    r.close ();
    r.close ();
    Say ("open and close: done");
    q := JsonReader.FromRd (rd);
    q.close ();
    Say ("the reader given stays open: " & Fmt.Bool (NOT Rd.Closed (rd)));
    Rd.Close (rd);
  END Files;

PROCEDURE Big (path: TEXT) =
  VAR
    r := JsonReader.Open (path);
    id := 0;
    bad := 0;
    sum := 0.0D0;

  PROCEDURE Check (ok: BOOLEAN) =
    BEGIN
      IF NOT ok THEN INC (bad) END;
    END Check;

  BEGIN
    r.array ();
    WHILE r.element () DO
      r.object ();
      WHILE r.member () DO
        IF r.is ("id") THEN
          Check (r.int () = id);
        ELSIF r.is ("title") THEN
          Check (Text.Equal (r.text (), Title (id)));
        ELSIF r.is ("ratio") THEN
          sum := sum + r.real ();
        ELSIF r.is ("tags") THEN
          r.array ();
          WHILE r.element () DO Check (Text.Equal (r.text (), "x\n\"y")) END;
        ELSE
          r.skip ();
        END;
      END;
      INC (id);
    END;
    r.finish ();
    r.close ();
    Say (Fmt.Int (id) & " " & Fmt.Int (bad) & " " & Fmt.LongReal (sum));
  END Big;

PROCEDURE Title (id: INTEGER): TEXT =
  BEGIN
    RETURN "book " & Fmt.Int (id) & " \344\366";
  END Title;

(* Goes through all pieces of a value and makes all its texts. *)
PROCEDURE ReadAll (r: JsonReader.T): INTEGER =
  VAR n := 0;
  BEGIN
    LOOP
      CASE r.peek () OF
      | Kind.Object => r.object ();
      | Kind.ObjectEnd => EVAL r.member ();
      | Kind.Array => r.array ();
      | Kind.ArrayEnd => EVAL r.element ();
      | Kind.Key => EVAL r.member ();
      | Kind.Text => INC (n, Text.Length (r.text ()));
      | Kind.Number => EVAL r.real (); INC (n);
      | Kind.Bool => EVAL r.bool ();
      | Kind.Null => r.null ();
      | Kind.EOF => r.finish (); RETURN n;
      END;
    END;
  END ReadAll;

PROCEDURE Rate (label: TEXT; bytes: INTEGER; start: Time.T) =
  VAR s := Time.Now () - start;
  BEGIN
    Say (label & " " & Fmt.LongReal (FLOAT (bytes, LONGREAL) / 1.0D6 / s, Fmt.Style.Fix, 0)
           & " MB/s");
  END Rate;

PROCEDURE Speed (path: TEXT) =
  VAR
    rd := FileRd.Open (path);
    bytes := Rd.Length (rd);
    head: TEXT;
    start: Time.T;
    r: JsonReader.T;
  BEGIN
    Rd.Close (rd);
    FOR pass := 1 TO 3 DO
      start := Time.Now ();
      r := JsonReader.Open (path);
      r.skip ();
      r.finish ();
      r.close ();
      Rate ("skip", bytes, start);
    END;
    FOR pass := 1 TO 3 DO
      start := Time.Now ();
      r := JsonReader.Open (path);
      EVAL ReadAll (r);
      r.close ();
      Rate ("read", bytes, start);
    END;
    rd := FileRd.Open (path);
    head := Rd.GetText (rd, MIN (bytes, 32 * 1000 * 1000));
    Rd.Close (rd);
    (* a shorter document that is complete, for the parser that needs it all *)
    WITH cut = Text.FindCharR (head, '}') DO
      head := Text.Sub (head, 0, cut + 1) & "]";
    END;
    FOR pass := 1 TO 3 DO
      start := Time.Now ();
      EVAL Json.ParseBuf (head);
      Rate ("Json", Text.Length (head), start);
    END;
    FOR pass := 1 TO 3 DO
      start := Time.Now ();
      r := JsonReader.FromText (head);
      EVAL ReadAll (r);
      r.close ();
      Rate ("same text", Text.Length (head), start);
    END;
  END Speed;

VAR mode := Params.Get (1);

BEGIN
  IF Text.Equal (mode, "batch") THEN
    Batch (Params.Get (2), Params.Get (3));
  ELSIF Text.Equal (mode, "api") THEN
    Api (Params.Get (2));
  ELSIF Text.Equal (mode, "numbers") THEN
    Numbers (Params.Get (2), Params.Get (3));
  ELSIF Text.Equal (mode, "feed") THEN
    Failing ();
    Connection ();
    Files (Params.Get (2));
  ELSIF Text.Equal (mode, "big") THEN
    Big (Params.Get (2));
  ELSIF Text.Equal (mode, "speed") THEN
    Speed (Params.Get (2));
  END;
END Main.
