(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Tests of the library xml; tools/xml_test.py runs them.

     batch LIST      for each line "FILE FLAG..." of LIST, the tokens of FILE
                     in canonical form into FILE.out
     text FILE       the same for one file, read into a TEXT, to standard output
     nav             the methods that go through a document
     source          inputs that fail or block
     stream          how far the input is read at each token
     big MB [speed]  a generated document of MB megabytes *)

MODULE Main;

IMPORT FileRd, FileWr, Fmt, IO, Params, Rd, RdClass, Text, TextRd, TextWr, Thread,
       Time, Wr, XmlReader;
FROM XmlReader IMPORT Kind, Problem;

<*FATAL ANY*>

CONST
  KindNames = ARRAY Kind OF TEXT {"None", "Start", "End", "Text", "Comment", "PI", "EOF"};
  ProblemNames = ARRAY Problem OF TEXT {"Malformed", "Encoding", "Limit", "Unexpected",
                                        "BadValue", "Closed"};

(* Printable ASCII as it is, all else as code units in hexadecimal. *)
PROCEDURE Esc (t: TEXT): TEXT =
  VAR wr := TextWr.New (); c: INTEGER;
  BEGIN
    FOR i := 0 TO Text.Length (t) - 1 DO
      c := ORD (Text.GetWideChar (t, i));
      IF c >= 16_20 AND c < 16_7F AND c # ORD ('\\') THEN
        Wr.PutChar (wr, VAL (c, CHAR));
      ELSE
        Wr.PutText (wr, "\\" & Fmt.Pad (Fmt.Int (c, 16), 4, '0'));
      END;
    END;
    RETURN TextWr.ToText (wr);
  END Esc;

PROCEDURE Name (uri, local, prefix: TEXT): TEXT =
  BEGIN
    RETURN Esc (uri) & "|" & Esc (local) & "|" & Esc (prefix);
  END Name;

PROCEDURE Dump (r: XmlReader.T; wr: Wr.T) =
  VAR k: Kind; a: XmlReader.Attribute;
  BEGIN
    LOOP
      TRY
        k := r.next ();
      EXCEPT
      | XmlReader.Error (f) =>
          Wr.PutText (wr, "ERROR " & ProblemNames[f.problem] & " "
                            & Fmt.Int (f.line) & ":" & Fmt.Int (f.column) & "\n");
          Wr.PutText (wr, "# " & Esc (f.message) & "\n");
          RETURN;
      END;
      CASE k OF
      | Kind.Start =>
          Wr.PutText (wr, "S " & Name (r.uri (), r.local (), r.prefix ()));
          FOR i := 0 TO r.attrCount () - 1 DO
            a := r.attrAt (i);
            Wr.PutText (wr, " " & Name (a.uri, a.local, a.prefix) & "=" & Esc (a.value));
          END;
          Wr.PutText (wr, "\n");
      | Kind.End => Wr.PutText (wr, "E " & Name (r.uri (), r.local (), r.prefix ()) & "\n");
      | Kind.Text => Wr.PutText (wr, "T " & Esc (r.value ()) & "\n");
      | Kind.Comment => Wr.PutText (wr, "C " & Esc (r.value ()) & "\n");
      | Kind.PI => Wr.PutText (wr, "P " & Esc (r.target ()) & " " & Esc (r.value ()) & "\n");
      | Kind.EOF => Wr.PutText (wr, "EOF\n"); RETURN;
      | Kind.None => <* ASSERT FALSE *>
      END;
    END;
  END Dump;

PROCEDURE Number (flag: TEXT): INTEGER =
  BEGIN
    RETURN Scan (Text.Sub (flag, Text.FindChar (flag, '=') + 1));
  END Number;

PROCEDURE Scan (t: TEXT): INTEGER =
  VAR n := 0;
  BEGIN
    FOR i := 0 TO Text.Length (t) - 1 DO
      n := n * 10 + ORD (Text.GetChar (t, i)) - ORD ('0');
    END;
    RETURN n;
  END Scan;

PROCEDURE Starts (t, prefix: TEXT): BOOLEAN =
  BEGIN
    RETURN Text.Equal (Text.Sub (t, 0, Text.Length (prefix)), prefix);
  END Starts;

(* Splits a line of the list at its blanks, and applies the flags. *)
PROCEDURE ParseLine (line: TEXT; VAR file: TEXT; VAR o: XmlReader.Options) =
  VAR a := 0; b: INTEGER; word: TEXT; first := TRUE; n := Text.Length (line);
  BEGIN
    WHILE a < n DO
      b := Text.FindChar (line, ' ', a);
      IF b < 0 THEN b := n END;
      word := Text.Sub (line, a, b - a);
      a := b + 1;
      IF first THEN
        file := word;
        first := FALSE;
      ELSIF Text.Equal (word, "comments") THEN o.comments := TRUE;
      ELSIF Text.Equal (word, "instructions") THEN o.instructions := TRUE;
      ELSIF Text.Equal (word, "nowhitespace") THEN o.whitespace := FALSE;
      ELSIF Text.Equal (word, "reject") THEN o.rejectDoctype := TRUE;
      ELSIF Starts (word, "depth=") THEN o.maxDepth := Number (word);
      ELSIF Starts (word, "attrs=") THEN o.maxAttributes := Number (word);
      ELSIF Starts (word, "names=") THEN o.maxNameChars := Number (word);
      ELSIF Starts (word, "token=") THEN o.maxTokenChars := Number (word);
      ELSE
        <* ASSERT FALSE *>
      END;
    END;
  END ParseLine;

PROCEDURE Batch (list: TEXT) =
  VAR rd := FileRd.Open (list); file: TEXT; in: Rd.T; out: Wr.T; o: XmlReader.Options; r: XmlReader.T;
  BEGIN
    WHILE NOT Rd.EOF (rd) DO
      o := XmlReader.Defaults;
      ParseLine (Rd.GetLine (rd), file, o);
      in := FileRd.Open (file);
      out := FileWr.Open (file & ".out");
      r := XmlReader.FromRd (in, o);
      Dump (r, out);
      r.close ();
      Rd.Close (in);
      Wr.Close (out);
    END;
    Rd.Close (rd);
  END Batch;

PROCEDURE TextFile (file: TEXT) =
  VAR
    in := FileRd.Open (file);
    wide := Rd.GetText (in, LAST (CARDINAL));
    out := TextWr.New ();
    chars := NEW (REF ARRAY OF WIDECHAR, Text.Length (wide) DIV 2);
  BEGIN
    Rd.Close (in);
    (* the file holds UTF-16 LE, without a mark *)
    FOR i := 0 TO LAST (chars^) DO
      chars[i] := VAL (ORD (Text.GetChar (wide, 2 * i))
                         + 256 * ORD (Text.GetChar (wide, 2 * i + 1)), WIDECHAR);
    END;
    Dump (XmlReader.FromText (Text.FromWideChars (chars^)), out);
    IO.Put (TextWr.ToText (out));
  END TextFile;

(* Navigation. *)

PROCEDURE Show (name: TEXT; v: TEXT) =
  BEGIN
    IF v = NIL THEN v := "<NIL>" END;
    IO.Put (name & " = [" & Esc (v) & "]\n");
  END Show;

PROCEDURE ShowFailure (name: TEXT; f: XmlReader.Failure) =
  BEGIN
    IO.Put (name & " raises " & ProblemNames[f.problem] & " at "
              & Fmt.Int (f.line) & ":" & Fmt.Int (f.column));
    (* the texts of the parser depend on the language of the system *)
    IF f.problem # Problem.Malformed THEN IO.Put (": " & Esc (f.message)) END;
    IO.Put ("\n");
  END ShowFailure;

CONST Catalog =
  "<?xml version=\"1.0\"?>\n<!-- head -->\n"
  & "<catalog xmlns=\"urn:c\" xmlns:x=\"urn:x\" x:v=\"7\" v=\"8\">\n"
  & "  <book id=\"1\" in-print=\"true\"><title>A &amp; B<![CDATA[ <C>]]></title><price>1.5</price></book>\n"
  & "  <other><deep><deeper/></deep></other>\n"
  & "  text in the catalog\n"
  & "  <book id=\"2\" in-print=\"0\"><title><!-- none --></title></book>\n"
  & "  <x:book id=\"x\"/>\n"
  & "</catalog>\n";

PROCEDURE Catalogue () =
  VAR r := XmlReader.FromText (Catalog);
  BEGIN
    r.root ("catalog", "urn:c");
    Show ("root", r.qname ());
    Show ("version", r.version ());
    Show ("encoding", r.encoding ());
    Show ("attr v", r.attr ("v"));
    Show ("attr v any", r.attr ("v", NIL));
    Show ("attr v urn:x", r.attr ("v", "urn:x"));
    Show ("attr none", r.attr ("nope"));
    Show ("lookup x", r.lookup ("x"));
    Show ("lookup default", r.lookup (""));
    Show ("lookup xml", r.lookup ("xml"));
    Show ("lookup unknown", r.lookup ("nope"));
    IO.Put ("depth " & Fmt.Int (r.depth ()) & "\n");
    WHILE r.child () DO
      IO.Put ("child " & r.qname () & " depth " & Fmt.Int (r.depth ()) & " at "
                & Fmt.Int (r.line ()) & ":" & Fmt.Int (r.column ()) & "\n");
      IF r.is ("book", "urn:x") THEN
        Show ("foreign id", r.need ("id"));
        r.skip ();
      ELSIF r.is ("book") THEN
        IO.Put ("id " & Fmt.Int (r.toInt (r.need ("id"))) & " in print "
                  & Fmt.Bool (r.toBool (r.need ("in-print"))) & "\n");
        WHILE r.child () DO
          IF r.is ("title") THEN
            Show ("title", r.content ());
          ELSIF r.is ("price") THEN
            IO.Put ("price " & Fmt.LongReal (r.toReal (r.content ())) & "\n");
          ELSE
            r.skip ();
          END;
        END;
      ELSE
        r.skip ();
      END;
      IO.Put ("after, on " & KindNames[r.kind ()] & " " & r.qname () & "\n");
    END;
    IO.Put ("end of root, on " & KindNames[r.kind ()] & " depth " & Fmt.Int (r.depth ()) & "\n");
    IO.Put ("child " & Fmt.Bool (r.child ()) & " on " & KindNames[r.kind ()]
              & " depth " & Fmt.Int (r.depth ()) & "\n");
    IO.Put ("next " & KindNames[r.next ()] & "\n");
    r.close ();
  END Catalogue;

TYPE Action = PROCEDURE (r: XmlReader.T) RAISES ANY;

PROCEDURE Fails (name, doc: TEXT; a: Action) =
  VAR r := XmlReader.FromText (doc);
  BEGIN
    TRY
      a (r);
      IO.Put (name & " passes, on " & KindNames[r.kind ()] & "\n");
    EXCEPT
    | XmlReader.Error (f) =>
        ShowFailure (name, f);
        (* Unexpected and BadValue leave the reader usable *)
        TRY
          IO.Put ("  then next is " & KindNames[r.next ()] & "\n");
        EXCEPT
        | XmlReader.Error (g) => ShowFailure ("  then next", g);
        END;
    END;
  END Fails;

PROCEDURE RootWrong (r: XmlReader.T) RAISES ANY = BEGIN r.root ("b") END RootWrong;
PROCEDURE RootWrongUri (r: XmlReader.T) RAISES ANY = BEGIN r.root ("a", "urn:n") END RootWrongUri;
PROCEDURE RootTwice (r: XmlReader.T) RAISES ANY = BEGIN r.root (); r.root () END RootTwice;
PROCEDURE ContentChild (r: XmlReader.T) RAISES ANY = BEGIN r.root (); EVAL r.content () END ContentChild;
PROCEDURE ContentNoStart (r: XmlReader.T) RAISES ANY = BEGIN EVAL r.content () END ContentNoStart;
PROCEDURE SkipNoStart (r: XmlReader.T) RAISES ANY = BEGIN r.skip () END SkipNoStart;
PROCEDURE NeedMissing (r: XmlReader.T) RAISES ANY = BEGIN r.root (); EVAL r.need ("x") END NeedMissing;
PROCEDURE ExpectEnd (r: XmlReader.T) RAISES ANY =
  BEGIN r.root (); r.skip (); r.expect ("a") END ExpectEnd;
PROCEDURE BadInt (r: XmlReader.T) RAISES ANY = BEGIN r.root (); EVAL r.toInt ("12x") END BadInt;
PROCEDURE NilInt (r: XmlReader.T) RAISES ANY = BEGIN r.root (); EVAL r.toInt (r.attr ("x")) END NilInt;
PROCEDURE BigInt (r: XmlReader.T) RAISES ANY =
  BEGIN r.root (); EVAL r.toInt ("99999999999999999999999") END BigInt;
PROCEDURE BadBool (r: XmlReader.T) RAISES ANY = BEGIN r.root (); EVAL r.toBool ("yes") END BadBool;
PROCEDURE BadReal (r: XmlReader.T) RAISES ANY = BEGIN r.root (); EVAL r.toReal ("one") END BadReal;
PROCEDURE RootEmptyDoc (r: XmlReader.T) RAISES ANY = BEGIN r.root () END RootEmptyDoc;

PROCEDURE Spaces () =
  VAR r := XmlReader.FromText ("<a> 12 </a>");
  BEGIN
    r.root ();
    IO.Put ("toInt " & Fmt.Int (r.toInt (" 12\n")) & " toBool " & Fmt.Bool (r.toBool (" true\t"))
              & " toReal " & Fmt.LongReal (r.toReal (" 2.5 ")) & "\n");
  END Spaces;

PROCEDURE Accessors () =
  VAR r := XmlReader.FromText ("<?p q?><a b='c'>x<!--y--></a>",
                               XmlReader.Options {comments := TRUE, instructions := TRUE});
  PROCEDURE State (name: TEXT) =
    BEGIN
      IO.Put (name & ": " & KindNames[r.kind ()] & " local [" & r.local () & "] value ["
                & r.value () & "] target [" & r.target () & "] attrs " & Fmt.Int (r.attrCount ())
                & " depth " & Fmt.Int (r.depth ()) & "\n");
    END State;
  BEGIN
    State ("before");
    WHILE r.next () # Kind.EOF DO State ("token") END;
    State ("end");
    r.close ();
    State ("closed");
    TRY
      EVAL r.next ();
    EXCEPT
    | XmlReader.Error (f) => ShowFailure ("next after close", f);
    END;
  END Accessors;

PROCEDURE Nav () =
  BEGIN
    Catalogue ();
    Spaces ();
    Accessors ();
    Fails ("root of another name", "<a/>", RootWrong);
    Fails ("root of another namespace", "<a/>", RootWrongUri);
    Fails ("root twice", "<a/>", RootTwice);
    Fails ("content with a child", "<a><b/></a>", ContentChild);
    Fails ("content not on a start", "<a/>", ContentNoStart);
    Fails ("skip not on a start", "<a/>", SkipNoStart);
    Fails ("need of a missing attribute", "<a/>", NeedMissing);
    Fails ("expect on an end", "<a/>", ExpectEnd);
    Fails ("toInt of a bad value", "<a/>", BadInt);
    Fails ("toInt of NIL", "<a/>", NilInt);
    Fails ("toInt of a huge value", "<a/>", BigInt);
    Fails ("toBool of a bad value", "<a/>", BadBool);
    Fails ("toReal of a bad value", "<a/>", BadReal);
    Fails ("root of an empty document", "", RootEmptyDoc);
    Fails ("root of a malformed document", "<a", RootEmptyDoc);
  END Nav;

(* A reader of pieces, which counts them, and can fail or block at the end. *)

TYPE
  Ending = {Normal, Failure, Block};
  PieceRd = Rd.T OBJECT
    pieces: REF ARRAY OF TEXT;
    taken := 0;
    ending := Ending.Normal;
  OVERRIDES
    seek := PieceSeek;
  END;

PROCEDURE NewPieces (READONLY a: ARRAY OF TEXT; ending := Ending.Normal): PieceRd =
  VAR p := NEW (REF ARRAY OF TEXT, NUMBER (a));
  BEGIN
    p^ := a;
    RETURN NEW (PieceRd, pieces := p, ending := ending,
                buff := NEW (REF ARRAY OF CHAR, 4096), st := 0, lo := 0, hi := 0, cur := 0,
                intermittent := TRUE, seekable := FALSE, closed := FALSE);
  END NewPieces;

PROCEDURE PieceSeek (rd: PieceRd; <*UNUSED*> pos: CARDINAL; <*UNUSED*> dontBlock: BOOLEAN): RdClass.SeekResult
  RAISES {Rd.Failure, Thread.Alerted} =
  VAR n: INTEGER;
  BEGIN
    IF rd.taken = NUMBER (rd.pieces^) THEN
      CASE rd.ending OF
      | Ending.Normal => RETURN RdClass.SeekResult.Eof;
      | Ending.Failure => RAISE Rd.Failure (NIL);
      | Ending.Block => Thread.AlertPause (60.0D0); RETURN RdClass.SeekResult.Eof;
      END;
    END;
    n := Text.Length (rd.pieces[rd.taken]);
    Text.SetChars (rd.buff^, rd.pieces[rd.taken]);
    INC (rd.taken);
    rd.st := 0;
    rd.lo := rd.cur;
    rd.hi := rd.cur + n;
    RETURN RdClass.SeekResult.Ready;
  END PieceSeek;

PROCEDURE SourceFailure () =
  VAR r := XmlReader.FromRd (NewPieces (ARRAY OF TEXT {"<a><b>x"}, Ending.Failure));
      k: Kind;
  BEGIN
    TRY
      LOOP
        k := r.next ();
        IO.Put ("token " & KindNames[k] & "\n");
        IF k = Kind.EOF THEN EXIT END;
      END;
    EXCEPT
    | Rd.Failure => IO.Put ("Rd.Failure came through\n");
    END;
    IO.Put ("kind " & KindNames[r.kind ()] & " local [" & r.local () & "]\n");
    TRY
      EVAL r.next ();
    EXCEPT
    | XmlReader.Error (f) => ShowFailure ("next after Rd.Failure", f);
    END;
  END SourceFailure;

TYPE Blocked = Thread.Closure OBJECT
    r: XmlReader.T;
    result: TEXT := NIL;
  OVERRIDES
    apply := BlockedApply;
  END;

PROCEDURE BlockedApply (self: Blocked): REFANY =
  BEGIN
    TRY
      WHILE self.r.next () # Kind.EOF DO END;
      self.result := "ended";
    EXCEPT
    | Thread.Alerted => self.result := "Thread.Alerted came through";
    END;
    RETURN NIL;
  END BlockedApply;

PROCEDURE SourceAlert () =
  VAR
    b := NEW (Blocked, r := XmlReader.FromRd (NewPieces (ARRAY OF TEXT {"<a><b>x"}, Ending.Block)));
    th := Thread.Fork (b);
  BEGIN
    Thread.Pause (0.5D0);
    Thread.Alert (th);
    EVAL Thread.Join (th);
    IO.Put (b.result & "\n");
    TRY
      EVAL b.r.next ();
    EXCEPT
    | XmlReader.Error (f) => ShowFailure ("next after Thread.Alerted", f);
    END;
  END SourceAlert;

PROCEDURE AfterError () =
  VAR r := XmlReader.FromText ("<a></b>");
  BEGIN
    FOR i := 1 TO 3 DO
      TRY
        IO.Put ("token " & KindNames[r.next ()] & "\n");
      EXCEPT
      | XmlReader.Error (f) => ShowFailure ("next", f);
      END;
    END;
    IO.Put ("kind " & KindNames[r.kind ()] & "\n");
  END AfterError;

PROCEDURE FromTextRd () =
  VAR r := XmlReader.FromRd (TextRd.New ("<?xml version='1.0' encoding='ISO-8859-1'?><a>\351</a>"));
  BEGIN
    r.root ();
    Show ("encoding", r.encoding ());
    Show ("version", r.version ());
    Show ("content", r.content ());
  END FromTextRd;

PROCEDURE Source () =
  BEGIN
    SourceFailure ();
    SourceAlert ();
    AfterError ();
    FromTextRd ();
  END Source;

PROCEDURE Stream () =
  VAR
    rd := NewPieces (ARRAY OF TEXT {"<a><b>x</b>", "<c/>", "</a>"});
    r := XmlReader.FromRd (rd);
    k: Kind;
  BEGIN
    REPEAT
      k := r.next ();
      IO.Put (KindNames[k] & " after " & Fmt.Int (rd.taken) & " pieces\n");
    UNTIL k = Kind.EOF;
  END Stream;

(* A generated document that is never held in memory. *)

TYPE
  GenRd = Rd.T OBJECT
    left: INTEGER;
    n := 0;
    state := 0;
  OVERRIDES
    seek := GenSeek;
  END;

PROCEDURE GenSeek (rd: GenRd; <*UNUSED*> pos: CARDINAL; <*UNUSED*> dontBlock: BOOLEAN): RdClass.SeekResult =
  VAR at := 0; t: TEXT;
  BEGIN
    IF rd.state = 2 THEN RETURN RdClass.SeekResult.Eof END;
    WHILE at + 200 < NUMBER (rd.buff^) AND rd.state < 2 DO
      IF rd.state = 0 THEN
        t := "<r>";
        rd.state := 1;
      ELSIF rd.left > 0 THEN
        INC (rd.n);
        t := "<item id=\"" & Fmt.Int (rd.n) & "\" k=\"v\">text " & Fmt.Int (rd.n) & "</item>\n";
        DEC (rd.left, Text.Length (t));
      ELSE
        t := "</r>";
        rd.state := 2;
      END;
      Text.SetChars (SUBARRAY (rd.buff^, at, Text.Length (t)), t);
      INC (at, Text.Length (t));
    END;
    rd.st := 0;
    rd.lo := rd.cur;
    rd.hi := rd.cur + at;
    RETURN RdClass.SeekResult.Ready;
  END GenSeek;

PROCEDURE Big (mb: INTEGER; speed: BOOLEAN) =
  VAR
    bytes := mb * 1000 * 1000;
    r := XmlReader.FromRd (NEW (GenRd, left := bytes,
                                buff := NEW (REF ARRAY OF CHAR, 65536), st := 0, lo := 0, hi := 0, cur := 0,
                                intermittent := FALSE, seekable := FALSE, closed := FALSE));
    events, sum := 0;
    k: Kind;
    t0 := Time.Now ();
    sec: LONGREAL;
  BEGIN
    REPEAT
      k := r.next ();
      INC (events);
      IF k = Kind.Start AND r.is ("item") THEN INC (sum, r.toInt (r.need ("id")) MOD 7) END;
      IF k = Kind.Text THEN INC (sum, Text.Length (r.value ())) END;
    UNTIL k = Kind.EOF;
    sec := Time.Now () - t0;
    IO.Put ("events " & Fmt.Int (events) & " sum " & Fmt.Int (sum) & "\n");
    IF speed THEN
      IO.Put (Fmt.F ("          %s MB/s, %s million tokens/s\n",
                     Fmt.LongReal (FLOAT (mb, LONGREAL) / sec, Fmt.Style.Fix, 0),
                     Fmt.LongReal (FLOAT (events, LONGREAL) / 1.0D6 / sec, Fmt.Style.Fix, 1)));
    END;
  END Big;

VAR mode := Params.Get (1);

BEGIN
  IF Text.Equal (mode, "batch") THEN Batch (Params.Get (2));
  ELSIF Text.Equal (mode, "text") THEN TextFile (Params.Get (2));
  ELSIF Text.Equal (mode, "nav") THEN Nav ();
  ELSIF Text.Equal (mode, "source") THEN Source ();
  ELSIF Text.Equal (mode, "stream") THEN Stream ();
  ELSIF Text.Equal (mode, "big") THEN
    Big (Scan (Params.Get (2)), Params.Count > 3);
  ELSE
    IO.Put ("usage: xmltest batch LIST | text FILE | nav | source | stream | big MB [speed]\n");
  END;
END Main.
