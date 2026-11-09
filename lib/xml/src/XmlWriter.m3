(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE XmlWriter;

IMPORT FileWr, Fmt, LongFloat, OSError, Text, TextIntTbl, Thread, Wr, XmlName;

CONST
  BufSize = 8192;
  Chunk = 4096;
  Room = 32;                    (* the most that one character can add *)
  TableFrom = 64;               (* attributes before a table is worth it *)
  XmlnsNS = "http://www.w3.org/2000/xmlns/";

TYPE
  Mode = {Text, Attr, CData, Raw};

  Binding = RECORD prefix, uri: TEXT END;

  Attribute = RECORD qname, value: TEXT; local, uri: TEXT END;

  Frame = RECORD
    qname: TEXT;
    base: CARDINAL;             (* the bindings of this element begin here *)
    children, hasText, preserve := FALSE;
  END;

REVEAL
  T = Public BRANDED "XmlWriter.T" OBJECT
    wr: Wr.T;                   (* NIL after close *)
    file: Wr.T := NIL;          (* what close closes *)
    o: Options;
    buf: REF ARRAY OF CHAR;
    chunk: REF ARRAY OF WIDECHAR;
    n: CARDINAL := 0;
    closed := FALSE;            (* nothing more is accepted *)
    broken := FALSE;            (* and wr failed, so it is left alone *)
    began := FALSE;             (* the declaration has had its turn *)
    wrote := FALSE;             (* markup has been written *)
    rooted := FALSE;            (* the root element has begun *)
    pending := FALSE;           (* the start tag on top has not been written *)
    frames: REF ARRAY OF Frame;
    d: CARDINAL := 0;
    binds: REF ARRAY OF Binding;
    nb: CARDINAL := 0;
    attrs: REF ARRAY OF Attribute;
    na: CARDINAL := 0;
    seen: TextIntTbl.T := NIL;  (* of attrs, from TableFrom on *)
    generated: CARDINAL := 0;   (* the last number of a new prefix *)
  OVERRIDES
    start := Start;
    attr := Attr;
    declare := Declare;
    end := End;
    element := Element;
    text := PutTextNode;
    cdata := CData;
    comment := Comment;
    pi := PI;
    depth := Depth;
    flush := Flush;
    finish := Finish;
    close := Close;
  END;

PROCEDURE ToWr (wr: Wr.T; READONLY o := Defaults): T =
  VAR t := NEW (T, wr := wr, o := o, buf := NEW (REF ARRAY OF CHAR, BufSize),
                chunk := NEW (REF ARRAY OF WIDECHAR, Chunk),
                frames := NEW (REF ARRAY OF Frame, 16),
                binds := NEW (REF ARRAY OF Binding, 16),
                attrs := NEW (REF ARRAY OF Attribute, 16));
  BEGIN
    IF o.indent # NIL THEN
      FOR i := 0 TO Text.Length (o.indent) - 1 DO
        WITH c = Text.GetChar (o.indent, i) DO
          <*ASSERT c = ' ' OR c = '\t'*>
        END;
      END;
    END;
    t.binds[0] := Binding {"xml", XmlNS};
    t.nb := 1;
    RETURN t;
  END ToWr;

PROCEDURE Open (path: TEXT; READONLY o := Defaults): T RAISES {OSError.E} =
  VAR wr := FileWr.Open (path); t := ToWr (wr, o);
  BEGIN
    t.file := wr;
    RETURN t;
  END Open;

PROCEDURE ValidName (t: TEXT): BOOLEAN =
  BEGIN
    RETURN XmlName.Valid (t);
  END ValidName;

PROCEDURE Bool (b: BOOLEAN): TEXT =
  BEGIN
    IF b THEN RETURN "true" ELSE RETURN "false" END;
  END Bool;

PROCEDURE Real (r: LONGREAL): TEXT =
  BEGIN
    IF LongFloat.IsNaN (r) THEN RETURN "NaN" END;
    IF NOT LongFloat.Finite (r) THEN
      IF r < 0.0D0 THEN RETURN "-INF" ELSE RETURN "INF" END;
    END;
    RETURN Fmt.LongReal (r);
  END Real;

PROCEDURE Format (f: Failure): TEXT =
  BEGIN
    RETURN f.message;
  END Format;

PROCEDURE Fail (t: T; p: Problem; message: TEXT): Failure =
  BEGIN
    t.closed := TRUE;
    RETURN NEW (Failure, problem := p, message := message);
  END Fail;

PROCEDURE Guard (t: T) RAISES {Error} =
  BEGIN
    IF t.closed THEN
      RAISE Error (NEW (Failure, problem := Problem.Closed,
                        message := "the writer has ended"));
    END;
  END Guard;

(* A failure of wr leaves "broken" set, because the assignment after the call
   is not reached. *)
PROCEDURE Drain (t: T) RAISES {Wr.Failure, Thread.Alerted} =
  VAR n := t.n; was := t.closed;
  BEGIN
    IF n > 0 THEN
      t.closed := TRUE;
      t.broken := TRUE;
      t.n := 0;
      Wr.PutString (t.wr, SUBARRAY (t.buf^, 0, n));
      t.broken := FALSE;
      t.closed := was;
    END;
  END Drain;

PROCEDURE Lit (t: T; s: TEXT) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    FOR i := 0 TO Text.Length (s) - 1 DO
      IF t.n = BufSize THEN Drain (t) END;
      t.buf[t.n] := Text.GetChar (s, i);
      INC (t.n);
    END;
  END Lit;

PROCEDURE Bad (t: T; c: INTEGER) RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    IF NOT t.o.replaceInvalid THEN
      RAISE Error (Fail (t, Problem.Content,
                         "the character U+" & Fmt.Pad (Fmt.Int (c, 16), 4, '0')
                           & " cannot be in XML"));
    END;
    Lit (t, "\357\277\275");
  END Bad;

(* Writes s, in UTF-8, with what its mode has to replace. A surrogate and "]"
   may be the last of a chunk, so they are remembered across chunks. *)
PROCEDURE Put (t: T; s: TEXT; m: Mode) RAISES {Error, Wr.Failure, Thread.Alerted} =
  VAR
    len := Text.Length (s);
    pos := 0;
    cnt, c, v: INTEGER;
    high := -1;                 (* a high surrogate waiting for its partner *)
    run := 0;                   (* "]" directly before, in a CDATA section *)
  BEGIN
    WHILE pos < len DO
      cnt := MIN (len - pos, Chunk);
      Text.SetWideChars (t.chunk^, s, pos);
      FOR j := 0 TO cnt - 1 DO
        c := ORD (t.chunk[j]);
        IF t.n > BufSize - Room THEN Drain (t) END;
        IF high >= 0 THEN
          IF c >= 16_DC00 AND c <= 16_DFFF THEN
            v := 16_10000 + (high - 16_D800) * 1024 + (c - 16_DC00);
            high := -1;
            WITH b = t.buf^ DO
              b[t.n] := VAL (16_F0 + v DIV 262144, CHAR);
              b[t.n + 1] := VAL (16_80 + v DIV 4096 MOD 64, CHAR);
              b[t.n + 2] := VAL (16_80 + v DIV 64 MOD 64, CHAR);
              b[t.n + 3] := VAL (16_80 + v MOD 64, CHAR);
            END;
            INC (t.n, 4);
            run := 0;
            c := -1;
          ELSE
            Bad (t, high);
            high := -1;
          END;
        END;
        IF c >= 16_80 THEN
          run := 0;
          IF c < 16_800 THEN
            t.buf[t.n] := VAL (16_C0 + c DIV 64, CHAR);
            t.buf[t.n + 1] := VAL (16_80 + c MOD 64, CHAR);
            INC (t.n, 2);
          ELSIF c >= 16_D800 AND c <= 16_DBFF THEN
            high := c;
          ELSIF c >= 16_DC00 AND c <= 16_DFFF OR c >= 16_FFFE THEN
            Bad (t, c);
          ELSE
            t.buf[t.n] := VAL (16_E0 + c DIV 4096, CHAR);
            t.buf[t.n + 1] := VAL (16_80 + c DIV 64 MOD 64, CHAR);
            t.buf[t.n + 2] := VAL (16_80 + c MOD 64, CHAR);
            INC (t.n, 3);
          END;
        ELSIF c >= 0 THEN
          CASE c OF
          | 16_09, 16_0A =>
              IF m = Mode.Attr THEN
                Lit (t, "&#" & Fmt.Int (c) & ";");
              ELSE
                t.buf[t.n] := VAL (c, CHAR);
                INC (t.n);
              END;
              run := 0;
          | 16_0D =>
              CASE m OF
              | Mode.Text, Mode.Attr => Lit (t, "&#13;");
              | Mode.CData => Lit (t, "]]>&#13;<![CDATA[");
              | Mode.Raw => t.buf[t.n] := '\r'; INC (t.n);
              END;
              run := 0;
          | 16_00 .. 16_08, 16_0B, 16_0C, 16_0E .. 16_1F =>
              Bad (t, c);
              run := 0;
          | ORD ('&') =>
              IF m = Mode.Text OR m = Mode.Attr THEN
                Lit (t, "&amp;");
              ELSE
                t.buf[t.n] := '&';
                INC (t.n);
              END;
              run := 0;
          | ORD ('<') =>
              IF m = Mode.Text OR m = Mode.Attr THEN
                Lit (t, "&lt;");
              ELSE
                t.buf[t.n] := '<';
                INC (t.n);
              END;
              run := 0;
          | ORD ('>') =>
              IF m = Mode.Text THEN
                Lit (t, "&gt;");
              ELSIF m = Mode.CData AND run >= 2 THEN
                Lit (t, "]]><![CDATA[>");
              ELSE
                t.buf[t.n] := '>';
                INC (t.n);
              END;
              run := 0;
          | ORD ('"') =>
              IF m = Mode.Attr THEN
                Lit (t, "&quot;");
              ELSE
                t.buf[t.n] := '"';
                INC (t.n);
              END;
              run := 0;
          | ORD (']') =>
              t.buf[t.n] := ']';
              INC (t.n);
              INC (run);
          ELSE
            t.buf[t.n] := VAL (c, CHAR);
            INC (t.n);
            run := 0;
          END;
        END;
      END;
      INC (pos, cnt);
    END;
    IF high >= 0 THEN Bad (t, high) END;
  END Put;

(* What Put would refuse, before anything is written. *)
PROCEDURE Check (t: T; s: TEXT) RAISES {Error, Wr.Failure, Thread.Alerted} =
  VAR c: INTEGER; len := Text.Length (s); i := 0;
  BEGIN
    IF t.o.replaceInvalid THEN RETURN END;
    WHILE i < len DO
      c := ORD (Text.GetWideChar (s, i));
      IF c >= 16_D800 AND c <= 16_DBFF AND i + 1 < len
           AND ORD (Text.GetWideChar (s, i + 1)) >= 16_DC00
           AND ORD (Text.GetWideChar (s, i + 1)) <= 16_DFFF THEN
        INC (i);
      ELSIF c < 16_20 AND c # 16_09 AND c # 16_0A AND c # 16_0D
              OR c >= 16_D800 AND c <= 16_DFFF OR c >= 16_FFFE THEN
        Bad (t, c);
      END;
      INC (i);
    END;
  END Check;

PROCEDURE CheckName (t: T; s, what: TEXT) RAISES {Error} =
  BEGIN
    IF NOT XmlName.Valid (s) THEN
      RAISE Error (Fail (t, Problem.Name, what & " is not a valid name"));
    END;
  END CheckName;

PROCEDURE CheckPrefix (t: T; p: TEXT) RAISES {Error} =
  BEGIN
    IF p # NIL AND NOT Text.Empty (p) THEN CheckName (t, p, "a prefix") END;
  END CheckPrefix;

PROCEDURE NsFail (t: T; message: TEXT): Failure =
  BEGIN
    RETURN Fail (t, Problem.Namespace, message);
  END NsFail;

(* What prefix stands for at the top, among the first "below" bindings: NIL
   if nothing, and "" for a default namespace that was never bound. *)
PROCEDURE LookupBelow (t: T; prefix: TEXT; below: CARDINAL): TEXT =
  BEGIN
    FOR i := below - 1 TO 0 BY -1 DO
      IF Text.Equal (t.binds[i].prefix, prefix) THEN RETURN t.binds[i].uri END;
    END;
    IF Text.Empty (prefix) THEN RETURN "" END;
    RETURN NIL;
  END LookupBelow;

PROCEDURE Lookup (t: T; prefix: TEXT): TEXT =
  BEGIN
    RETURN LookupBelow (t, prefix, t.nb);
  END Lookup;

(* Records the binding on the top element. Every name of the element goes
   through here, so that a later declaration cannot change what a name written
   earlier means. A binding that changes nothing is not written, see
   WriteTag. *)
PROCEDURE Bind (t: T; prefix, uri: TEXT) RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    IF Text.Equal (prefix, "xmlns") THEN
      RAISE Error (NsFail (t, "the prefix xmlns cannot be used"));
    END;
    IF Text.Equal (uri, XmlnsNS) THEN
      RAISE Error (NsFail (t, "the namespace of xmlns cannot be bound"));
    END;
    IF Text.Equal (prefix, "xml") # Text.Equal (uri, XmlNS) THEN
      RAISE Error (NsFail (t, "the prefix xml and its namespace belong together"));
    END;
    IF Text.Empty (uri) AND NOT Text.Empty (prefix) THEN
      RAISE Error (NsFail (t, "a prefix cannot be bound to no namespace"));
    END;
    Check (t, uri);
    FOR i := t.frames[t.d - 1].base TO t.nb - 1 DO
      IF Text.Equal (t.binds[i].prefix, prefix) THEN
        IF Text.Equal (t.binds[i].uri, uri) THEN RETURN END;
        RAISE Error (NsFail (t, "this element has already bound the prefix \""
                                  & prefix & "\" to another namespace"));
      END;
    END;
    IF t.nb = NUMBER (t.binds^) THEN
      VAR b := NEW (REF ARRAY OF Binding, 2 * t.nb);
      BEGIN
        SUBARRAY (b^, 0, t.nb) := t.binds^;
        t.binds := b;
      END;
    END;
    t.binds[t.nb] := Binding {prefix, uri};
    INC (t.nb);
  END Bind;

(* A prefix bound to uri in scope and not hidden, or NIL. *)
PROCEDURE FindPrefix (t: T; uri: TEXT): TEXT =
  BEGIN
    FOR i := t.nb - 1 TO 0 BY -1 DO
      WITH b = t.binds[i] DO
        IF NOT Text.Empty (b.prefix) AND Text.Equal (b.uri, uri)
             AND Text.Equal (Lookup (t, b.prefix), uri) THEN
          RETURN b.prefix;
        END;
      END;
    END;
    RETURN NIL;
  END FindPrefix;

PROCEDURE NewPrefix (t: T): TEXT =
  VAR p: TEXT;
  BEGIN
    REPEAT
      INC (t.generated);
      p := "ns" & Fmt.Int (t.generated);
    UNTIL Lookup (t, p) = NIL;
    RETURN p;
  END NewPrefix;

PROCEDURE Qualified (prefix, local: TEXT): TEXT =
  BEGIN
    IF Text.Empty (prefix) THEN RETURN local END;
    RETURN prefix & ":" & local;
  END Qualified;

PROCEDURE Begin (t: T) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    IF NOT t.began THEN
      t.began := TRUE;
      IF t.o.declaration THEN
        Lit (t, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        t.wrote := TRUE;
      END;
    END;
  END Begin;

PROCEDURE Indent (t: T; level: CARDINAL) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    Lit (t, "\n");
    FOR i := 1 TO level DO Lit (t, t.o.indent) END;
  END Indent;

(* Writes the start tag that waits. With "empty" the element has no content,
   and ends here. A binding that the enclosing elements have already is not
   written. *)
PROCEDURE WriteTag (t: T; empty := FALSE) RAISES {Error, Wr.Failure, Thread.Alerted} =
  VAR outer: TEXT;
  BEGIN
    IF NOT t.pending THEN RETURN END;
    t.pending := FALSE;
    WITH f = t.frames[t.d - 1] DO
      Lit (t, "<");
      Put (t, f.qname, Mode.Raw);
      FOR i := f.base TO t.nb - 1 DO
        WITH b = t.binds[i] DO
          outer := LookupBelow (t, b.prefix, f.base);
          IF outer = NIL OR NOT Text.Equal (outer, b.uri) THEN
            IF Text.Empty (b.prefix) THEN
              Lit (t, " xmlns=\"");
            ELSE
              Lit (t, " xmlns:");
              Put (t, b.prefix, Mode.Raw);
              Lit (t, "=\"");
            END;
            Put (t, b.uri, Mode.Attr);
            Lit (t, "\"");
          END;
        END;
      END;
      FOR i := 0 TO t.na - 1 DO
        Lit (t, " ");
        Put (t, t.attrs[i].qname, Mode.Raw);
        Lit (t, "=\"");
        Put (t, t.attrs[i].value, Mode.Attr);
        Lit (t, "\"");
      END;
      t.na := 0;
      t.seen := NIL;
      IF NOT empty THEN
        Lit (t, ">");
      ELSIF t.o.selfClose THEN
        Lit (t, "/>");
      ELSE
        Lit (t, "></");
        Put (t, f.qname, Mode.Raw);
        Lit (t, ">");
      END;
    END;
  END WriteTag;

(* Before markup that is not text: an element, a comment or an instruction. *)
PROCEDURE Opening (t: T) RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    WriteTag (t);
    Begin (t);
    IF t.d > 0 THEN
      WITH f = t.frames[t.d - 1] DO
        f.children := TRUE;
        IF t.o.indent # NIL AND NOT f.preserve AND NOT f.hasText THEN
          Indent (t, t.d);
        END;
      END;
    ELSIF t.o.indent # NIL AND t.wrote THEN
      Indent (t, 0);
    END;
    t.wrote := TRUE;
  END Opening;

PROCEDURE Push (t: T; qname: TEXT) =
  VAR preserve := t.d > 0 AND t.frames[t.d - 1].preserve;
  BEGIN
    IF t.d = NUMBER (t.frames^) THEN
      VAR f := NEW (REF ARRAY OF Frame, 2 * t.d);
      BEGIN
        SUBARRAY (f^, 0, t.d) := t.frames^;
        t.frames := f;
      END;
    END;
    t.frames[t.d] := Frame {qname := qname, base := t.nb, preserve := preserve};
    INC (t.d);
  END Push;

PROCEDURE Start (t: T; local, uri, prefix: TEXT)
  RAISES {Error, Wr.Failure, Thread.Alerted} =
  VAR p: TEXT;
  BEGIN
    Guard (t);
    CheckName (t, local, "the name of an element");
    CheckPrefix (t, prefix);
    IF t.d = 0 AND t.rooted THEN
      RAISE Error (Fail (t, Problem.Structure, "a document has one root element"));
    END;
    Opening (t);
    Push (t, local);
    t.rooted := TRUE;
    t.pending := TRUE;
    IF uri = NIL THEN
      IF prefix # NIL AND NOT Text.Empty (prefix) THEN
        p := Lookup (t, prefix);
        IF p = NIL THEN
          RAISE Error (NsFail (t, "the prefix \"" & prefix & "\" is not bound"));
        END;
        Bind (t, prefix, p);
        t.frames[t.d - 1].qname := Qualified (prefix, local);
      END;
    ELSIF Text.Empty (uri) THEN
      IF prefix # NIL AND NOT Text.Empty (prefix) THEN
        RAISE Error (NsFail (t, "a name without a namespace has no prefix"));
      END;
      Bind (t, "", "");
    ELSE
      IF prefix # NIL THEN
        p := prefix;
      ELSIF Text.Equal (Lookup (t, ""), uri) THEN
        p := "";
      ELSE
        p := FindPrefix (t, uri);
        IF p = NIL THEN p := "" END;
      END;
      Bind (t, p, uri);
      t.frames[t.d - 1].qname := Qualified (p, local);
    END;
  END Start;

(* Whether the top element has this attribute already; if not, it counts as
   having it from now on, as far as the table goes. *)
PROCEDURE Duplicate (t: T; local, uri: TEXT): BOOLEAN =
  VAR dummy: INTEGER;
  BEGIN
    IF t.seen = NIL AND t.na < TableFrom THEN
      FOR i := 0 TO t.na - 1 DO
        IF Text.Equal (t.attrs[i].local, local) AND Text.Equal (t.attrs[i].uri, uri) THEN
          RETURN TRUE;
        END;
      END;
      RETURN FALSE;
    END;
    IF t.seen = NIL THEN
      t.seen := NEW (TextIntTbl.Default).init (2 * t.na);
      FOR i := 0 TO t.na - 1 DO
        EVAL t.seen.put (t.attrs[i].local & " " & t.attrs[i].uri, 0);
      END;
    END;
    WITH key = local & " " & uri DO
      IF t.seen.get (key, dummy) THEN RETURN TRUE END;
      EVAL t.seen.put (key, 0);
    END;
    RETURN FALSE;
  END Duplicate;

PROCEDURE Attr (t: T; local, value, uri, prefix: TEXT)
  RAISES {Error, Wr.Failure, Thread.Alerted} =
  VAR p := ""; u := "";
  BEGIN
    Guard (t);
    IF NOT t.pending THEN
      RAISE Error (Fail (t, Problem.Structure,
                         "an attribute must follow the start of its element"));
    END;
    CheckName (t, local, "the name of an attribute");
    CheckPrefix (t, prefix);
    Check (t, value);
    IF uri = NIL OR Text.Empty (uri) THEN
      IF prefix # NIL AND NOT Text.Empty (prefix) THEN
        RAISE Error (NsFail (t, "a name without a namespace has no prefix"));
      END;
      IF Text.Equal (local, "xmlns") THEN
        RAISE Error (NsFail (t, "namespaces are bound with declare"));
      END;
    ELSE
      u := uri;
      IF prefix # NIL THEN
        IF Text.Empty (prefix) THEN
          RAISE Error (NsFail (t, "an attribute with a namespace needs a prefix"));
        END;
        p := prefix;
      ELSE
        p := FindPrefix (t, uri);
        IF p = NIL THEN p := NewPrefix (t) END;
      END;
      Bind (t, p, uri);
    END;
    IF Duplicate (t, local, u) THEN
      RAISE Error (Fail (t, Problem.Structure, "the attribute \"" & local
                                                 & "\" is there already"));
    END;
    IF t.na = NUMBER (t.attrs^) THEN
      VAR a := NEW (REF ARRAY OF Attribute, 2 * t.na);
      BEGIN
        SUBARRAY (a^, 0, t.na) := t.attrs^;
        t.attrs := a;
      END;
    END;
    t.attrs[t.na] := Attribute {qname := Qualified (p, local), value := value,
                                local := local, uri := u};
    INC (t.na);
    IF Text.Equal (u, XmlNS) AND Text.Equal (local, "space") THEN
      t.frames[t.d - 1].preserve := Text.Equal (value, "preserve");
    END;
  END Attr;

PROCEDURE Declare (t: T; prefix, uri: TEXT)
  RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    Guard (t);
    IF NOT t.pending THEN
      RAISE Error (Fail (t, Problem.Structure,
                         "a declaration must follow the start of its element"));
    END;
    CheckPrefix (t, prefix);
    Bind (t, prefix, uri);
  END Declare;

PROCEDURE End (t: T) RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    Guard (t);
    IF t.d = 0 THEN
      RAISE Error (Fail (t, Problem.Structure, "no element is open"));
    END;
    IF t.pending THEN
      WriteTag (t, empty := TRUE);
    ELSE
      WITH f = t.frames[t.d - 1] DO
        IF t.o.indent # NIL AND f.children AND NOT f.hasText AND NOT f.preserve THEN
          Indent (t, t.d - 1);
        END;
        Lit (t, "</");
        Put (t, f.qname, Mode.Raw);
        Lit (t, ">");
      END;
    END;
    DEC (t.d);
    t.nb := t.frames[t.d].base;
  END End;

PROCEDURE Element (t: T; local, text, uri, prefix: TEXT)
  RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    Start (t, local, uri, prefix);
    PutTextNode (t, text);
    End (t);
  END Element;

PROCEDURE BeforeText (t: T; s: TEXT): BOOLEAN
  RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    Guard (t);
    IF t.d = 0 THEN
      RAISE Error (Fail (t, Problem.Structure, "text must be inside the root element"));
    END;
    IF Text.Empty (s) THEN RETURN FALSE END;
    WriteTag (t);
    t.frames[t.d - 1].hasText := TRUE;
    RETURN TRUE;
  END BeforeText;

PROCEDURE PutTextNode (t: T; s: TEXT) RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    IF BeforeText (t, s) THEN Put (t, s, Mode.Text) END;
  END PutTextNode;

PROCEDURE CData (t: T; s: TEXT) RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    IF BeforeText (t, s) THEN
      Lit (t, "<![CDATA[");
      Put (t, s, Mode.CData);
      Lit (t, "]]>");
    END;
  END CData;

(* Whether c1 and c2 are in s, next to each other. *)
PROCEDURE Has (s: TEXT; c1, c2: CHAR): BOOLEAN =
  VAR i := Text.FindChar (s, c1);
  BEGIN
    WHILE i >= 0 AND i + 1 < Text.Length (s) DO
      IF Text.GetWideChar (s, i + 1) = VAL (ORD (c2), WIDECHAR) THEN RETURN TRUE END;
      i := Text.FindChar (s, c1, i + 1);
    END;
    RETURN FALSE;
  END Has;

PROCEDURE Comment (t: T; s: TEXT) RAISES {Error, Wr.Failure, Thread.Alerted} =
  VAR n := Text.Length (s);
  BEGIN
    Guard (t);
    IF Has (s, '-', '-') OR n > 0 AND Text.GetWideChar (s, n - 1) = W'-' THEN
      RAISE Error (Fail (t, Problem.Content,
                         "a comment cannot hold -- or end with -"));
    END;
    Opening (t);
    Lit (t, "<!--");
    Put (t, s, Mode.Raw);
    Lit (t, "-->");
  END Comment;

PROCEDURE IsXml (s: TEXT): BOOLEAN =
  BEGIN
    IF Text.Length (s) # 3 THEN RETURN FALSE END;
    RETURN ORD (Text.GetWideChar (s, 0)) IN SET OF [0 .. 255] {ORD ('x'), ORD ('X')}
             AND ORD (Text.GetWideChar (s, 1)) IN SET OF [0 .. 255] {ORD ('m'), ORD ('M')}
             AND ORD (Text.GetWideChar (s, 2)) IN SET OF [0 .. 255] {ORD ('l'), ORD ('L')};
  END IsXml;

PROCEDURE PI (t: T; target, data: TEXT) RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    Guard (t);
    CheckName (t, target, "the target of an instruction");
    IF IsXml (target) THEN
      RAISE Error (Fail (t, Problem.Name, "the target xml is reserved"));
    END;
    IF Has (data, '?', '>') THEN
      RAISE Error (Fail (t, Problem.Content, "an instruction cannot hold ?>"));
    END;
    Opening (t);
    Lit (t, "<?");
    Put (t, target, Mode.Raw);
    IF NOT Text.Empty (data) THEN
      Lit (t, " ");
      Put (t, data, Mode.Raw);
    END;
    Lit (t, "?>");
  END PI;

PROCEDURE Depth (t: T): CARDINAL =
  BEGIN
    RETURN t.d;
  END Depth;

PROCEDURE FlushWr (t: T) RAISES {Wr.Failure, Thread.Alerted} =
  VAR was := t.closed;
  BEGIN
    Drain (t);
    t.closed := TRUE;
    t.broken := TRUE;
    Wr.Flush (t.wr);
    t.broken := FALSE;
    t.closed := was;
  END FlushWr;

PROCEDURE Flush (t: T) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    IF t.wr # NIL AND NOT t.broken THEN FlushWr (t) END;
  END Flush;

PROCEDURE Finish (t: T) RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    Guard (t);
    IF NOT t.rooted THEN
      RAISE Error (Fail (t, Problem.Structure, "the document has no root element"));
    END;
    IF t.d > 0 THEN
      RAISE Error (Fail (t, Problem.Structure, "an element is still open"));
    END;
    IF t.o.indent # NIL THEN Lit (t, "\n") END;
    FlushWr (t);
    t.closed := TRUE;
  END Finish;

PROCEDURE Close (t: T) RAISES {Wr.Failure, Thread.Alerted} =
  VAR f := t.file;
  BEGIN
    t.closed := TRUE;
    t.wr := NIL;
    t.file := NIL;
    IF f # NIL THEN Wr.Close (f) END;
  END Close;

BEGIN
END XmlWriter.
