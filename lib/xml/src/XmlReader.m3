(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE XmlReader;

IMPORT FileRd, FloatMode, Fmt, IEEESpecial, Java, Lex, M3jXml, M3jXmlFill, OSError,
       Rd, Scan, Text, Thread;

TYPE
  Filler = M3jXmlFill.T OBJECT
    rd: Rd.T;
  METHODS
    fill (max: Java.Int): REF ARRAY OF CHAR RAISES {Rd.Failure, Thread.Alerted} := Fill;
  END;

REVEAL
  T = Public BRANDED "XmlReader.T" OBJECT
    x: M3jXml.T;
    file: Rd.T := NIL;          (* what close closes *)
    k := Kind.None;
    d: CARDINAL := 0;
  OVERRIDES
    next := Next;
    kind := GetKind;
    depth := Depth;
    close := Close;
    local := Local;
    uri := Uri;
    prefix := Prefix;
    qname := QName;
    is := Is;
    attr := Attr;
    need := Need;
    attrCount := AttrCount;
    attrAt := AttrAt;
    lookup := Lookup;
    value := Value;
    target := Target;
    root := Root;
    child := Child;
    content := Content;
    skip := Skip;
    expect := Expect;
    toInt := ToInt;
    toBool := ToBool;
    toReal := ToReal;
    line := Line;
    column := Column;
    offset := Offset;
    version := Version;
    encoding := Encoding;
  END;

(* What the source has now, and at least one byte, so that a connection
   does not have to fill a buffer before the parser sees anything. *)
PROCEDURE Fill (self: Filler; max: Java.Int): REF ARRAY OF CHAR
  RAISES {Rd.Failure, Thread.Alerted} =
  VAR a: REF ARRAY OF CHAR; n: INTEGER;
  BEGIN
    IF Rd.EOF (self.rd) THEN RETURN NIL END;
    a := NEW (REF ARRAY OF CHAR, MIN (max, MAX (1, Rd.CharsReady (self.rd))));
    n := Rd.GetSub (self.rd, a^);
    IF n < NUMBER (a^) THEN
      WITH b = NEW (REF ARRAY OF CHAR, n) DO
        b^ := SUBARRAY (a^, 0, n);
        RETURN b;
      END;
    END;
    RETURN a;
  END Fill;

PROCEDURE FromRd (rd: Rd.T; READONLY o := Defaults): T =
  BEGIN
    RETURN NEW (T, x := M3jXml.fromFill (
                          NEW (Filler, rd := rd),
                          o.comments, o.instructions, o.whitespace, o.rejectDoctype,
                          o.maxDepth, o.maxAttributes, o.maxNameChars, o.maxTokenChars));
  END FromRd;

PROCEDURE FromText (t: TEXT; READONLY o := Defaults): T =
  BEGIN
    RETURN NEW (T, x := M3jXml.fromText (
                          t,
                          o.comments, o.instructions, o.whitespace, o.rejectDoctype,
                          o.maxDepth, o.maxAttributes, o.maxNameChars, o.maxTokenChars));
  END FromText;

PROCEDURE Open (path: TEXT; READONLY o := Defaults): T RAISES {OSError.E} =
  VAR rd := FileRd.Open (path); t := FromRd (rd, o);
  BEGIN
    t.file := rd;
    RETURN t;
  END Open;

PROCEDURE Format (f: Failure): TEXT =
  BEGIN
    RETURN Fmt.Int (f.line) & ":" & Fmt.Int (f.column) & ": " & f.message;
  END Format;

PROCEDURE Complain (t: T; p: Problem; message: TEXT): Failure =
  BEGIN
    RETURN NEW (Failure, problem := p, line := Line (t), column := Column (t),
                message := message);
  END Complain;

(* The parser is not asked about the current token during the call of next:
   an exception of the input leaves it in no usable state, and k stays None. *)
PROCEDURE Next (t: T): Kind RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR n: INTEGER;
  BEGIN
    IF t.k = Kind.End THEN DEC (t.d) END;
    t.k := Kind.None;
    n := M3jXml.next (t.x);
    IF n < 0 THEN
      RAISE Error (NEW (Failure,
                        problem := VAL (M3jXml.problem (t.x), Problem),
                        line := M3jXml.errorLine (t.x),
                        column := M3jXml.errorColumn (t.x),
                        message := M3jXml.errorMessage (t.x)));
    END;
    t.k := VAL (n, Kind);
    IF t.k = Kind.Start THEN INC (t.d) END;
    RETURN t.k;
  END Next;

PROCEDURE GetKind (t: T): Kind =
  BEGIN
    RETURN t.k;
  END GetKind;

PROCEDURE Depth (t: T): CARDINAL =
  BEGIN
    RETURN t.d;
  END Depth;

PROCEDURE Close (t: T) =
  BEGIN
    t.k := Kind.None;
    M3jXml.close (t.x);
    IF t.file # NIL THEN
      TRY
        Rd.Close (t.file);
      EXCEPT
      | Rd.Failure, Thread.Alerted =>   (* nothing can be done about it *)
      END;
      t.file := NIL;
    END;
  END Close;

PROCEDURE OnTag (t: T): BOOLEAN =
  BEGIN
    RETURN t.k = Kind.Start OR t.k = Kind.End;
  END OnTag;

PROCEDURE Local (t: T): TEXT =
  BEGIN
    IF OnTag (t) THEN RETURN M3jXml.local (t.x) END;
    RETURN "";
  END Local;

PROCEDURE Uri (t: T): TEXT =
  BEGIN
    IF OnTag (t) THEN RETURN M3jXml.uri (t.x) END;
    RETURN "";
  END Uri;

PROCEDURE Prefix (t: T): TEXT =
  BEGIN
    IF OnTag (t) THEN RETURN M3jXml.prefix (t.x) END;
    RETURN "";
  END Prefix;

PROCEDURE QName (t: T): TEXT =
  VAR p := Prefix (t);
  BEGIN
    IF Text.Empty (p) THEN RETURN Local (t) END;
    RETURN p & ":" & Local (t);
  END QName;

PROCEDURE Is (t: T; local, uri: TEXT): BOOLEAN =
  BEGIN
    RETURN OnTag (t) AND Text.Equal (M3jXml.local (t.x), local)
             AND (uri = NIL OR Text.Equal (M3jXml.uri (t.x), uri));
  END Is;

PROCEDURE AttrCount (t: T): CARDINAL =
  BEGIN
    IF t.k = Kind.Start THEN RETURN M3jXml.attrCount (t.x) END;
    RETURN 0;
  END AttrCount;

PROCEDURE AttrAt (t: T; i: CARDINAL): Attribute =
  BEGIN
    <* ASSERT i < AttrCount (t) *>
    RETURN Attribute {local := M3jXml.attrLocal (t.x, i), uri := M3jXml.attrUri (t.x, i),
                      prefix := M3jXml.attrPrefix (t.x, i), value := M3jXml.attrValue (t.x, i)};
  END AttrAt;

PROCEDURE Attr (t: T; local, uri: TEXT): TEXT =
  BEGIN
    FOR i := 0 TO AttrCount (t) - 1 DO
      IF Text.Equal (M3jXml.attrLocal (t.x, i), local)
           AND (uri = NIL OR Text.Equal (M3jXml.attrUri (t.x, i), uri)) THEN
        RETURN M3jXml.attrValue (t.x, i);
      END;
    END;
    RETURN NIL;
  END Attr;

PROCEDURE Need (t: T; local, uri: TEXT): TEXT RAISES {Error} =
  VAR v := Attr (t, local, uri);
  BEGIN
    IF v = NIL THEN
      RAISE Error (Complain (t, Problem.Unexpected,
                             "the element " & QName (t) & " has no attribute " & local));
    END;
    RETURN v;
  END Need;

PROCEDURE Lookup (t: T; prefix: TEXT): TEXT =
  BEGIN
    IF OnTag (t) AND prefix # NIL THEN RETURN M3jXml.lookup (t.x, prefix) END;
    RETURN NIL;
  END Lookup;

PROCEDURE Value (t: T): TEXT =
  BEGIN
    IF t.k = Kind.Text OR t.k = Kind.Comment OR t.k = Kind.PI THEN
      RETURN M3jXml.value (t.x);
    END;
    RETURN "";
  END Value;

PROCEDURE Target (t: T): TEXT =
  BEGIN
    IF t.k = Kind.PI THEN RETURN M3jXml.target (t.x) END;
    RETURN "";
  END Target;

PROCEDURE Root (t: T; local, uri: TEXT) RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR k: Kind;
  BEGIN
    IF t.d # 0 THEN
      RAISE Error (Complain (t, Problem.Unexpected, "the root element has been reached before"));
    END;
    REPEAT k := Next (t) UNTIL k = Kind.Start OR k = Kind.EOF;
    IF k = Kind.EOF THEN
      RAISE Error (Complain (t, Problem.Unexpected, "the root element has been read before"));
    END;
    IF local # NIL THEN Expect (t, local, uri) END;
  END Root;

PROCEDURE Child (t: T): BOOLEAN RAISES {Error, Rd.Failure, Thread.Alerted} =
  BEGIN
    LOOP
      CASE Next (t) OF
      | Kind.Start => RETURN TRUE;
      | Kind.End, Kind.EOF => RETURN FALSE;
      ELSE
      END;
    END;
  END Child;

PROCEDURE RequireStart (t: T; method: TEXT) RAISES {Error} =
  BEGIN
    IF t.k # Kind.Start THEN
      RAISE Error (Complain (t, Problem.Unexpected, method & " needs a start tag"));
    END;
  END RequireStart;

PROCEDURE Content (t: T): TEXT RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR result := ""; name: TEXT;
  BEGIN
    RequireStart (t, "content");
    name := QName (t);
    LOOP
      CASE Next (t) OF
      | Kind.Text => result := result & M3jXml.value (t.x);
      | Kind.End, Kind.EOF => RETURN result;
      | Kind.Start =>
          RAISE Error (Complain (t, Problem.Unexpected,
                                 "the element " & name & " has the child element " & QName (t)));
      ELSE
      END;
    END;
  END Content;

PROCEDURE Skip (t: T) RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR level: CARDINAL; k: Kind;
  BEGIN
    RequireStart (t, "skip");
    level := t.d;
    REPEAT k := Next (t) UNTIL (k = Kind.End AND t.d = level) OR k = Kind.EOF;
  END Skip;

PROCEDURE Expect (t: T; local, uri: TEXT) RAISES {Error} =
  BEGIN
    IF t.k # Kind.Start OR NOT Is (t, local, uri) THEN
      RAISE Error (Complain (t, Problem.Unexpected, "expected the element " & local));
    END;
  END Expect;

PROCEDURE BadValue (t: T; v, type: TEXT): Failure =
  BEGIN
    IF v = NIL THEN
      RETURN Complain (t, Problem.BadValue, "no value, where " & type & " is needed");
    END;
    RETURN Complain (t, Problem.BadValue, "not " & type & ": " & v);
  END BadValue;

PROCEDURE ToInt (t: T; v: TEXT): INTEGER RAISES {Error} =
  BEGIN
    IF v # NIL THEN
      TRY
        RETURN Scan.Int (v);
      EXCEPT
      | Lex.Error, FloatMode.Trap =>
      END;
    END;
    RAISE Error (BadValue (t, v, "an integer"));
  END ToInt;

PROCEDURE ToReal (t: T; v: TEXT): LONGREAL RAISES {Error} =
  VAR s: TEXT;
  BEGIN
    IF v # NIL THEN
      s := Trim (v);
      IF Text.Equal (s, "INF") THEN RETURN IEEESpecial.LongPosInf END;
      IF Text.Equal (s, "-INF") THEN RETURN IEEESpecial.LongNegInf END;
      IF Text.Equal (s, "NaN") THEN RETURN IEEESpecial.LongNan END;
      TRY
        RETURN Scan.LongReal (v);
      EXCEPT
      | Lex.Error, FloatMode.Trap =>
      END;
    END;
    RAISE Error (BadValue (t, v, "a number"));
  END ToReal;

PROCEDURE IsSpace (t: TEXT; i: INTEGER): BOOLEAN =
  BEGIN
    CASE ORD (Text.GetWideChar (t, i)) OF
    | 16_20, 16_09, 16_0A, 16_0D => RETURN TRUE;
    ELSE RETURN FALSE;
    END;
  END IsSpace;

PROCEDURE Trim (v: TEXT): TEXT =
  VAR a := 0; b := Text.Length (v);
  BEGIN
    WHILE a < b AND IsSpace (v, a) DO INC (a) END;
    WHILE b > a AND IsSpace (v, b - 1) DO DEC (b) END;
    RETURN Text.Sub (v, a, b - a);
  END Trim;

PROCEDURE ToBool (t: T; v: TEXT): BOOLEAN RAISES {Error} =
  VAR s: TEXT;
  BEGIN
    IF v # NIL THEN
      s := Trim (v);
      IF Text.Equal (s, "true") OR Text.Equal (s, "1") THEN RETURN TRUE END;
      IF Text.Equal (s, "false") OR Text.Equal (s, "0") THEN RETURN FALSE END;
    END;
    RAISE Error (BadValue (t, v, "a boolean"));
  END ToBool;

PROCEDURE Line (t: T): INTEGER =
  BEGIN
    RETURN M3jXml.line (t.x);
  END Line;

PROCEDURE Column (t: T): INTEGER =
  BEGIN
    RETURN M3jXml.column (t.x);
  END Column;

PROCEDURE Offset (t: T): INTEGER =
  BEGIN
    RETURN M3jXml.offset (t.x);
  END Offset;

PROCEDURE Version (t: T): TEXT =
  BEGIN
    RETURN M3jXml.version (t.x);
  END Version;

PROCEDURE Encoding (t: T): TEXT =
  BEGIN
    RETURN M3jXml.encoding (t.x);
  END Encoding;

BEGIN
END XmlReader.
