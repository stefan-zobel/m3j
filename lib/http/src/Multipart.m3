(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Multipart;

IMPORT Atom, AtomList, Crypto, Hex, HTTP, Rd, RdClass, Text, TextExtras, Thread, Wr;

CONST
  BufferSize = 16384;
  MaxPadding = 1024;   (* spaces and tabs after a boundary *)
  MaxBoundary = 70;

  Space = SET OF CHAR {' ', '\t'};
  BoundaryChars = SET OF CHAR {'0' .. '9', 'A' .. 'Z', 'a' .. 'z', '\'', '(', ')', '+', '_',
                               ',', '-', '.', '/', ':', '=', '?', ' '};
  TokenChars = SET OF CHAR {'A' .. 'Z', 'a' .. 'z', '0' .. '9', '!', '#', '$', '%', '&', '\'',
                            '*', '+', '-', '.', '^', '_', '`', '|', '~'};
  (* the characters that end a parameter value without quotes *)
  ValueEnd = SET OF CHAR {'\000' .. ' ', ';', '"', '\177'};

TYPE
  State = {Preamble, Body, Line, Padding, Head, Done};
  Step = {Data, Delimiter, WouldBlock};

REVEAL
  Reader = ReaderPublic BRANDED "Multipart.Reader" OBJECT
    rd: Rd.T;
    limits: Limits;
    dl: REF ARRAY OF CHAR := NIL;   (* the delimiter: CR LF "--" boundary *)
    buf: REF ARRAY OF CHAR := NIL;
    lo, hi := 0;                    (* buf[lo .. hi-1] is read and not yet used *)
    total := 0;                     (* the bytes read from "rd" *)
    state := State.Preamble;
    failure: AtomList.T := NIL;
    preamble := 0;
    parts := 0;
    padding := 0;
    fresh := FALSE;                 (* nothing of this body has been looked at *)
    partBytes := 0;
    headBytes := 0;
    fields := 0;
    headers: HTTP.Headers := NIL;
    current: PartRd := NIL;
  OVERRIDES
    next := Next;
  END;

  Writer = WriterPublic BRANDED "Multipart.Writer" OBJECT
    wr: Wr.T;
    boundary: TEXT;
    started, finished := FALSE;
  OVERRIDES
    contentType := WriterContentType;
    part := WriterPart;
    finish := WriterFinish;
  END;

TYPE
  (* The body of a part: its data lie in the buffer of the reader. *)
  PartRd = Rd.T OBJECT
    m: Reader;        (* "NIL" after the reader has moved on *)
    ended := FALSE;
  OVERRIDES
    seek := PartSeek;
    length := PartLength;
  END;

VAR Stale: Atom.T;

(* Failures *)

(* The text of a failure never comes from the data, because atoms are never
   freed and a peer must not be able to make more of them. *)
PROCEDURE Fail (m: Reader; head: Atom.T; what: TEXT) RAISES {Rd.Failure} =
  BEGIN
    m.failure := AtomList.List2 (head, Atom.FromText (what));
    RAISE Rd.Failure (m.failure);
  END Fail;

PROCEDURE Truncated (m: Reader) RAISES {Rd.Failure} =
  BEGIN
    Fail (m, HTTP.BadMessage, "multipart body without its last boundary");
  END Truncated;

(* The buffer *)

(* Reads more bytes into the buffer; "FALSE" at the end of "rd". *)
PROCEDURE Fill (m: Reader): BOOLEAN RAISES {Rd.Failure, Thread.Alerted} =
  VAR n := m.hi - m.lo;  want, got: INTEGER;
  BEGIN
    IF m.lo > 0 THEN
      SUBARRAY (m.buf^, 0, n) := SUBARRAY (m.buf^, m.lo, n);
      m.lo := 0;
      m.hi := n;
    END;
    want := NUMBER (m.buf^) - m.hi;
    <*ASSERT want > 0*>
    TRY
      want := MIN (want, MAX (1, Rd.CharsReady (m.rd)));
      IF m.limits.maxTotalBytes > 0 THEN
        want := MIN (want, m.limits.maxTotalBytes - m.total);
        IF want <= 0 THEN Fail (m, HTTP.TooLarge, "multipart body too large") END;
      END;
      got := Rd.GetSub (m.rd, SUBARRAY (m.buf^, m.hi, want));
    EXCEPT
    | Rd.Failure (e) =>
        m.failure := e;
        RAISE Rd.Failure (e);
    END;
    INC (m.hi, got);
    INC (m.total, got);
    RETURN got > 0;
  END Fill;

(* Fills the buffer until it has "n" bytes; "FALSE" if "rd" ends before. *)
PROCEDURE Have (m: Reader; n: INTEGER): BOOLEAN RAISES {Rd.Failure, Thread.Alerted} =
  BEGIN
    WHILE m.hi - m.lo < n DO
      IF NOT Fill (m) THEN RETURN FALSE END;
    END;
    RETURN TRUE;
  END Have;

PROCEDURE Ready (m: Reader): BOOLEAN RAISES {Rd.Failure} =
  BEGIN
    TRY
      RETURN Rd.CharsReady (m.rd) > 0;
    EXCEPT
    | Rd.Failure (e) =>
        m.failure := e;
        RAISE Rd.Failure (e);
    END;
  END Ready;

(* Is buf[i .. i+n-1] equal to dl[from .. from+n-1]? *)
PROCEDURE Matches (READONLY buf: ARRAY OF CHAR; i: INTEGER; READONLY dl: ARRAY OF CHAR;
                   from, n: INTEGER): BOOLEAN =
  BEGIN
    FOR k := 0 TO n - 1 DO
      IF buf[i + k] # dl[from + k] THEN RETURN FALSE END;
    END;
    RETURN TRUE;
  END Matches;

(* Looks for the delimiter. "Data": the "n" bytes at "lo" are not part of
   one. "Delimiter": one starts at "lo". *)
PROCEDURE Scan (m: Reader; dontBlock: BOOLEAN; VAR n: INTEGER): Step
  RAISES {Rd.Failure, Thread.Alerted} =
  VAR len := NUMBER (m.dl^);  safe, k, w: INTEGER;
  BEGIN
    LOOP
      IF m.fresh THEN
        (* a body that starts with the boundary itself is ambiguous *)
        w := m.hi - m.lo;
        IF w >= len - 2 THEN
          IF Matches (m.buf^, m.lo, m.dl^, 2, len - 2) THEN
            Fail (m, HTTP.BadMessage, "boundary right after the head of a part");
          END;
          m.fresh := FALSE;
        ELSIF NOT Matches (m.buf^, m.lo, m.dl^, 2, w) THEN
          m.fresh := FALSE;
        END;
      END;
      IF NOT m.fresh THEN
        safe := m.hi;
        FOR i := m.lo TO m.hi - 1 DO
          IF m.buf[i] = '\r' THEN
            k := MIN (len, m.hi - i);
            IF Matches (m.buf^, i, m.dl^, 0, k) THEN
              IF k = len AND i = m.lo THEN RETURN Step.Delimiter END;
              safe := i;
              EXIT;
            END;
          END;
        END;
        IF safe > m.lo THEN
          n := safe - m.lo;
          RETURN Step.Data;
        END;
      END;
      IF dontBlock AND NOT Ready (m) THEN RETURN Step.WouldBlock END;
      IF NOT Fill (m) THEN Truncated (m) END;
    END;
  END Scan;

(* The next step in the body of the current part. A delimiter is consumed;
   data are counted, but left to the caller. *)
PROCEDURE BodyStep (m: Reader; dontBlock: BOOLEAN; VAR n: INTEGER): Step
  RAISES {Rd.Failure, Thread.Alerted} =
  VAR s := Scan (m, dontBlock, n);
  BEGIN
    IF s = Step.Delimiter THEN
      INC (m.lo, NUMBER (m.dl^));
      m.state := State.Line;
    ELSIF s = Step.Data THEN
      IF m.limits.maxPartBytes > 0 AND n > m.limits.maxPartBytes - m.partBytes THEN
        Fail (m, HTTP.TooLarge, "part of a multipart body too large");
      END;
      INC (m.partBytes, n);
    END;
    RETURN s;
  END BodyStep;

(* The body of a part *)

PROCEDURE PartSeek (p: PartRd; <*UNUSED*> pos: CARDINAL; dontBlock: BOOLEAN): RdClass.SeekResult
  RAISES {Rd.Failure, Thread.Alerted} =
  VAR m := p.m;  n: INTEGER;
  BEGIN
    IF p.ended THEN RETURN RdClass.SeekResult.Eof END;
    IF m = NIL THEN RAISE Rd.Failure (AtomList.List1 (Stale)) END;
    IF m.failure # NIL THEN RAISE Rd.Failure (m.failure) END;
    CASE BodyStep (m, dontBlock, n) OF
    | Step.Delimiter =>
        p.ended := TRUE;
        RETURN RdClass.SeekResult.Eof;
    | Step.WouldBlock =>
        RETURN RdClass.SeekResult.WouldBlock;
    | Step.Data =>
        p.buff := m.buf;
        p.st := m.lo;
        p.lo := p.cur;
        p.hi := p.cur + n;
        INC (m.lo, n);
        RETURN RdClass.SeekResult.Ready;
    END;
  END PartSeek;

PROCEDURE PartLength (<*UNUSED*> p: PartRd): INTEGER =
  BEGIN
    RETURN -1;
  END PartLength;

(* Ends the use of a body, which the program may still hold. *)
PROCEDURE Detach (p: PartRd) =
  BEGIN
    RdClass.Lock (p);
    TRY
      p.m := NIL;
      p.lo := p.cur;
      p.hi := p.cur;
    FINALLY
      RdClass.Unlock (p);
    END;
  END Detach;

(* Parameters of fields *)

TYPE
  Cursor = RECORD
    t: TEXT;
    n, i: INTEGER;
  END;

  Param = REF RECORD
    name, value: TEXT;
    next: Param := NIL;
  END;

PROCEDURE SkipSpace (VAR c: Cursor) =
  BEGIN
    WHILE c.i < c.n AND Text.GetChar (c.t, c.i) IN Space DO INC (c.i) END;
  END SkipSpace;

PROCEDURE Token (VAR c: Cursor): TEXT =
  VAR start := c.i;
  BEGIN
    WHILE c.i < c.n AND Text.GetChar (c.t, c.i) IN TokenChars DO INC (c.i) END;
    IF c.i = start THEN RETURN NIL END;
    RETURN Text.Sub (c.t, start, c.i - start);
  END Token;

(* A token or a quoted string. In a quoted string a backslash quotes only a
   double quote or a backslash, and is kept before any other character. *)
PROCEDURE ParamValue (VAR c: Cursor; VAR v: TEXT): BOOLEAN =
  VAR start := c.i;  a: REF ARRAY OF CHAR;  j := 0;  ch: CHAR;
  BEGIN
    IF c.i < c.n AND Text.GetChar (c.t, c.i) = '"' THEN
      INC (c.i);
      a := NEW (REF ARRAY OF CHAR, c.n - c.i);
      LOOP
        IF c.i = c.n THEN RETURN FALSE END;
        ch := Text.GetChar (c.t, c.i);
        INC (c.i);
        IF ch = '"' THEN EXIT END;
        IF ch = '\\' AND c.i < c.n AND Text.GetChar (c.t, c.i) IN SET OF CHAR {'"', '\\'} THEN
          ch := Text.GetChar (c.t, c.i);
          INC (c.i);
        END;
        a[j] := ch;
        INC (j);
      END;
      v := Text.FromChars (SUBARRAY (a^, 0, j));
      RETURN TRUE;
    END;
    WHILE c.i < c.n AND NOT Text.GetChar (c.t, c.i) IN ValueEnd DO INC (c.i) END;
    IF c.i = start THEN RETURN FALSE END;
    v := Text.Sub (c.t, start, c.i - start);
    RETURN TRUE;
  END ParamValue;

(* The parameters from "c.i" to the end: *( OWS ";" OWS [ name "=" value ] ) OWS.
   "FALSE" if they are malformed. *)
PROCEDURE Params (VAR c: Cursor; VAR list: Param): BOOLEAN =
  VAR last: Param := NIL;  p: Param;  name, value: TEXT;
  BEGIN
    list := NIL;
    LOOP
      SkipSpace (c);
      IF c.i = c.n THEN RETURN TRUE END;
      IF Text.GetChar (c.t, c.i) # ';' THEN RETURN FALSE END;
      INC (c.i);
      SkipSpace (c);
      IF c.i < c.n AND Text.GetChar (c.t, c.i) # ';' THEN
        name := Token (c);
        IF name = NIL OR c.i = c.n OR Text.GetChar (c.t, c.i) # '=' THEN RETURN FALSE END;
        INC (c.i);
        IF NOT ParamValue (c, value) THEN RETURN FALSE END;
        p := NEW (Param, name := name, value := value);
        IF last = NIL THEN list := p ELSE last.next := p END;
        last := p;
      END;
    END;
  END Params;

PROCEDURE Boundary (contentType: TEXT): TEXT =
  VAR c: Cursor;  type: TEXT;  list, p: Param;  r: TEXT := NIL;
  BEGIN
    IF contentType = NIL OR Text.HasWideChars (contentType) THEN RETURN NIL END;
    c := Cursor {contentType, Text.Length (contentType), 0};
    SkipSpace (c);
    type := Token (c);
    IF type = NIL OR NOT TextExtras.CIEqual (type, "multipart") OR c.i = c.n
         OR Text.GetChar (c.t, c.i) # '/' THEN
      RETURN NIL;
    END;
    INC (c.i);
    IF Token (c) = NIL OR NOT Params (c, list) THEN RETURN NIL END;
    p := list;
    WHILE p # NIL DO
      IF TextExtras.CIEqual (p.name, "boundary") THEN
        IF r # NIL THEN RETURN NIL END;
        r := p.value;
      END;
      p := p.next;
    END;
    IF r = NIL OR NOT ValidBoundary (r) THEN RETURN NIL END;
    RETURN r;
  END Boundary;

PROCEDURE ValidBoundary (b: TEXT): BOOLEAN =
  VAR n: INTEGER;
  BEGIN
    IF b = NIL OR Text.HasWideChars (b) THEN RETURN FALSE END;
    n := Text.Length (b);
    IF n < 1 OR n > MaxBoundary OR Text.GetChar (b, n - 1) = ' ' THEN RETURN FALSE END;
    FOR i := 0 TO n - 1 DO
      IF NOT Text.GetChar (b, i) IN BoundaryChars THEN RETURN FALSE END;
    END;
    RETURN TRUE;
  END ValidBoundary;

(* The head of a part *)

PROCEDURE Trim (t: TEXT; start, end: INTEGER): TEXT =
  BEGIN
    WHILE start < end AND Text.GetChar (t, start) IN Space DO INC (start) END;
    WHILE end > start AND Text.GetChar (t, end - 1) IN Space DO DEC (end) END;
    RETURN Text.Sub (t, start, end - start);
  END Trim;

PROCEDURE Field (m: Reader; line: TEXT) RAISES {Rd.Failure} =
  VAR colon := Text.FindChar (line, ':');  name, value: TEXT;
  BEGIN
    IF Text.GetChar (line, 0) IN Space THEN
      Fail (m, HTTP.BadMessage, "folded field in the head of a part");
    END;
    IF colon <= 0 THEN Fail (m, HTTP.BadMessage, "malformed field in the head of a part") END;
    name := Text.Sub (line, 0, colon);
    value := Trim (line, colon + 1, Text.Length (line));
    IF NOT HTTP.ValidName (name) OR NOT HTTP.ValidValue (value) THEN
      Fail (m, HTTP.BadMessage, "malformed field in the head of a part");
    END;
    IF m.fields >= m.limits.maxHeaders THEN
      Fail (m, HTTP.TooLarge, "too many fields in the head of a part");
    END;
    INC (m.fields);
    m.headers.add (name, value);
  END Field;

(* Reads a line of the head; "TRUE" if it was the empty line at its end. *)
PROCEDURE HeadLine (m: Reader): BOOLEAN RAISES {Rd.Failure, Thread.Alerted} =
  VAR budget := m.limits.maxHeaderBytes - m.headBytes;  from := 0;  e := -1;  len: INTEGER;
      line: TEXT;
  BEGIN
    LOOP
      FOR i := m.lo + from TO m.hi - 2 DO
        IF m.buf[i] = '\r' AND m.buf[i + 1] = '\n' THEN
          e := i;
          EXIT;
        END;
      END;
      IF e >= 0 THEN EXIT END;
      IF m.hi - m.lo >= budget THEN Fail (m, HTTP.TooLarge, "head of a part too large") END;
      from := MAX (0, m.hi - m.lo - 1);
      IF NOT Fill (m) THEN Truncated (m) END;
    END;
    len := e - m.lo + 2;
    IF len > budget THEN Fail (m, HTTP.TooLarge, "head of a part too large") END;
    line := Text.FromChars (SUBARRAY (m.buf^, m.lo, len - 2));
    INC (m.lo, len);
    INC (m.headBytes, len);
    IF len = 2 THEN RETURN TRUE END;
    Field (m, line);
    RETURN FALSE;
  END HeadLine;

PROCEDURE Disposition (m: Reader; v: TEXT; VAR name, filename: TEXT) RAISES {Rd.Failure} =
  VAR c := Cursor {v, Text.Length (v), 0};  list, p: Param;
  BEGIN
    IF Token (c) = NIL OR NOT Params (c, list) THEN
      Fail (m, HTTP.BadMessage, "malformed Content-Disposition of a part");
    END;
    p := list;
    WHILE p # NIL DO
      IF TextExtras.CIEqual (p.name, "name") THEN
        IF name # NIL THEN Fail (m, HTTP.BadMessage, "two names of a part") END;
        name := p.value;
      ELSIF TextExtras.CIEqual (p.name, "filename") THEN
        IF filename # NIL THEN Fail (m, HTTP.BadMessage, "two file names of a part") END;
        filename := p.value;
      END;
      p := p.next;
    END;
  END Disposition;

PROCEDURE MakePart (m: Reader; VAR part: Part) RAISES {Rd.Failure} =
  VAR it := m.headers.iterate ();  name, value, disposition: TEXT := NIL;
  BEGIN
    part.name := NIL;
    part.filename := NIL;
    part.contentType := NIL;
    part.headers := m.headers;
    WHILE it.next (name, value) DO
      IF TextExtras.CIEqual (name, "Content-Disposition") THEN
        IF disposition # NIL THEN
          Fail (m, HTTP.BadMessage, "two Content-Disposition fields in a part");
        END;
        disposition := value;
      ELSIF TextExtras.CIEqual (name, "Content-Type") THEN
        IF part.contentType # NIL THEN
          Fail (m, HTTP.BadMessage, "two Content-Type fields in a part");
        END;
        part.contentType := value;
      END;
    END;
    IF disposition # NIL THEN Disposition (m, disposition, part.name, part.filename) END;
    m.current := NEW (PartRd, m := m, buff := NIL, st := 0, lo := 0, hi := 0, cur := 0,
                      intermittent := TRUE, seekable := FALSE, closed := FALSE);
    part.body := m.current;
  END MakePart;

(* The reader *)

PROCEDURE NewReader (rd: Rd.T; boundary: TEXT; READONLY limits := Defaults): Reader =
  VAR m := NEW (Reader, rd := rd, limits := limits);  n: INTEGER;
  BEGIN
    IF NOT ValidBoundary (boundary) THEN
      m.failure := AtomList.List2 (HTTP.BadMessage,
                                   Atom.FromText ("missing or invalid boundary of a multipart body"));
      RETURN m;
    END;
    n := Text.Length (boundary);
    m.dl := NEW (REF ARRAY OF CHAR, n + 4);
    m.dl[0] := '\r';
    m.dl[1] := '\n';
    m.dl[2] := '-';
    m.dl[3] := '-';
    Text.SetChars (SUBARRAY (m.dl^, 4, n), boundary);
    m.buf := NEW (REF ARRAY OF CHAR, MAX (BufferSize, limits.maxHeaderBytes) + n + 8);
    (* a line end before the data lets the first boundary be found like
       all the others *)
    m.buf[0] := '\r';
    m.buf[1] := '\n';
    m.hi := 2;
    RETURN m;
  END NewReader;

PROCEDURE Next (m: Reader; VAR part: Part): BOOLEAN RAISES {Rd.Failure, Thread.Alerted} =
  VAR n: INTEGER;
  BEGIN
    IF m.failure # NIL THEN RAISE Rd.Failure (m.failure) END;
    IF m.current # NIL THEN
      Detach (m.current);
      m.current := NIL;
    END;
    LOOP
      CASE m.state OF
      | State.Preamble =>
          CASE Scan (m, FALSE, n) OF
          | Step.Delimiter =>
              INC (m.lo, NUMBER (m.dl^));
              m.state := State.Line;
          | Step.Data =>
              IF n > m.limits.maxPreamble - m.preamble THEN
                Fail (m, HTTP.TooLarge, "preamble of a multipart body too long");
              END;
              INC (m.preamble, n);
              INC (m.lo, n);
          | Step.WouldBlock => <*ASSERT FALSE*>
          END;
      | State.Body =>
          IF BodyStep (m, FALSE, n) = Step.Data THEN INC (m.lo, n) END;
      | State.Line =>
          IF NOT Have (m, 2) THEN Truncated (m) END;
          IF m.buf[m.lo] = '-' AND m.buf[m.lo + 1] = '-' THEN
            INC (m.lo, 2);
            m.state := State.Done;
          ELSE
            m.padding := 0;
            m.state := State.Padding;
          END;
      | State.Padding =>
          IF NOT Have (m, 1) THEN Truncated (m) END;
          IF m.buf[m.lo] IN Space THEN
            INC (m.padding);
            IF m.padding > MaxPadding THEN
              Fail (m, HTTP.BadMessage, "too many spaces after a boundary");
            END;
            INC (m.lo);
          ELSE
            IF NOT Have (m, 2) THEN Truncated (m) END;
            IF m.buf[m.lo] # '\r' OR m.buf[m.lo + 1] # '\n' THEN
              Fail (m, HTTP.BadMessage, "garbage after a boundary");
            END;
            INC (m.lo, 2);
            IF m.parts >= m.limits.maxParts THEN
              Fail (m, HTTP.TooLarge, "too many parts in a multipart body");
            END;
            INC (m.parts);
            m.headers := HTTP.NewHeaders ();
            m.headBytes := 0;
            m.fields := 0;
            m.state := State.Head;
          END;
      | State.Head =>
          IF HeadLine (m) THEN
            MakePart (m, part);
            m.state := State.Body;
            m.fresh := TRUE;
            m.partBytes := 0;
            RETURN TRUE;
          END;
      | State.Done =>
          RETURN FALSE;
      END;
    END;
  END Next;

(* The writer *)

PROCEDURE NewWriter (wr: Wr.T; boundary: TEXT := NIL): Writer =
  BEGIN
    IF boundary = NIL THEN boundary := "----" & Hex.Encode (Crypto.Random (16)) END;
    <*ASSERT ValidBoundary (boundary)*>
    RETURN NEW (Writer, wr := wr, boundary := boundary);
  END NewWriter;

PROCEDURE WriterContentType (w: Writer): TEXT =
  BEGIN
    FOR i := 0 TO Text.Length (w.boundary) - 1 DO
      IF NOT Text.GetChar (w.boundary, i) IN TokenChars THEN
        RETURN "multipart/form-data; boundary=\"" & w.boundary & "\"";
      END;
    END;
    RETURN "multipart/form-data; boundary=" & w.boundary;
  END WriterContentType;

(* May "t" be the value of a parameter "name" or "filename"? *)
PROCEDURE ValidParam (t: TEXT): BOOLEAN =
  VAR ch: CHAR;
  BEGIN
    IF t = NIL OR Text.HasWideChars (t) THEN RETURN FALSE END;
    FOR i := 0 TO Text.Length (t) - 1 DO
      ch := Text.GetChar (t, i);
      IF ch < ' ' OR ch = '\177' OR ch = '"' THEN RETURN FALSE END;
    END;
    RETURN TRUE;
  END ValidParam;

(* "t" as a quoted string; it has no double quotes, but backslashes. *)
PROCEDURE Quote (t: TEXT): TEXT =
  VAR n := Text.Length (t);  a := NEW (REF ARRAY OF CHAR, 2 * n + 2);  j := 1;  ch: CHAR;
  BEGIN
    a[0] := '"';
    FOR i := 0 TO n - 1 DO
      ch := Text.GetChar (t, i);
      IF ch = '\\' THEN
        a[j] := '\\';
        INC (j);
      END;
      a[j] := ch;
      INC (j);
    END;
    a[j] := '"';
    RETURN Text.FromChars (SUBARRAY (a^, 0, j + 1));
  END Quote;

PROCEDURE Bad (what: TEXT) RAISES {HTTP.Error} =
  BEGIN
    RAISE HTTP.Error (AtomList.List2 (HTTP.BadMessage, Atom.FromText (what)));
  END Bad;

PROCEDURE WriterPart (w: Writer; name: TEXT; filename, contentType: TEXT): Wr.T
  RAISES {HTTP.Error, Wr.Failure, Thread.Alerted} =
  VAR head: TEXT;
  BEGIN
    <*ASSERT NOT w.finished*>
    IF NOT ValidParam (name) THEN Bad ("invalid name of a part") END;
    IF filename # NIL AND NOT ValidParam (filename) THEN Bad ("invalid file name of a part") END;
    IF contentType # NIL AND (Text.Length (contentType) = 0 OR NOT HTTP.ValidValue (contentType)) THEN
      Bad ("invalid content type of a part");
    END;
    head := "--" & w.boundary & "\r\nContent-Disposition: form-data; name=" & Quote (name);
    IF filename # NIL THEN head := head & "; filename=" & Quote (filename) END;
    head := head & "\r\n";
    IF contentType = NIL AND filename # NIL THEN contentType := "application/octet-stream" END;
    IF contentType # NIL THEN head := head & "Content-Type: " & contentType & "\r\n" END;
    IF w.started THEN head := "\r\n" & head END;
    Wr.PutText (w.wr, head & "\r\n");
    w.started := TRUE;
    RETURN w.wr;
  END WriterPart;

PROCEDURE WriterFinish (w: Writer) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    <*ASSERT NOT w.finished*>
    w.finished := TRUE;
    IF w.started THEN
      Wr.PutText (w.wr, "\r\n--" & w.boundary & "--\r\n");
    ELSE
      Wr.PutText (w.wr, "--" & w.boundary & "--\r\n");
    END;
  END WriterFinish;

BEGIN
  Stale := Atom.FromText ("Multipart.Stale");
END Multipart.
