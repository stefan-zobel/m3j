(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE HTTPServer;

IMPORT Atom, AtomList, ConnFD, Deflate, Fmt, HTTP, IP, Rd, RdClass, TCP, TCPMisc,
       Text, TextExtras, Thread, Time, TLS, Wr, WrClass;

CONST
  InputSize = 16384;
  OutputSize = 16384;
  BodyBufferSize = 8192;
  ChunkLineBytes = 256;     (* the size of a chunk and its extensions *)
  LingerBytes = 1024 * 1024;
  LingerTime = 2.0D0;
  MaxLengthDigits = 18;     (* fits an INTEGER on every platform that has 64 bits *)

EXCEPTION
  Reject (INTEGER);         (* the request gets this status and the connection ends *)
  Gone;                     (* the client went away between requests *)

TYPE
  Connectors = REF RECORD
    c: TCP.Connector;
    next: Connectors;
  END;

REVEAL
  T = Public BRANDED "HTTPServer.T" OBJECT
    o: Options;
    mu: MUTEX;
    changed: Thread.Condition;
    handlers: REF ARRAY OF Handler := NIL;  (* replaced as a whole, never changed *)
    conns: Conn := NIL;
    active := 0;
    stopping := FALSE;
    finished := FALSE;  (* "shutdown" is done *)
    connectors: Connectors := NIL;
    watchdog: Thread.T := NIL;
  OVERRIDES
    register := Register;
    serve := Serve;
    shutdown := Shutdown;
  END;

  Response = ResponsePublic BRANDED "HTTPServer.Response" OBJECT
    c: Conn;
    req: Request;
    wr: BodyWr := NIL;
    compression := FALSE;   (* the handler has called "compress" *)
    gz: Wr.T := NIL;        (* compresses to "wr", if the body is compressed *)
    headSent := FALSE;
    chunked := FALSE;
    noBody := FALSE;
    remaining := -1;   (* of a declared Content-Length, or -1 *)
    done := FALSE;
  OVERRIDES
    body := ResponseBody;
    compress := ResponseCompress;
  END;

TYPE
  (* A connection and the thread that serves it. *)
  Conn = Thread.Closure OBJECT
    srv: T;
    tcp: TCP.T;
    fd: ConnFD.T;
    ctx: TLS.Context;
    peer: IP.Endpoint;
    th: Thread.T;
    prev, next: Conn := NIL;

    in, out: REF ARRAY OF CHAR;
    pos, end := 0;      (* in[pos .. end-1] has been read and not yet used *)
    outN := 0;
    deadline: Time.T;   (* of the line reader *)
    budget := 0;        (* bytes that the head may still use *)
    keep := FALSE;      (* keep the connection after this response *)
    expecting := FALSE; (* the client waits for 100 Continue *)
    resp: Response := NIL;
    wrote := FALSE;     (* part of this response has gone out *)
    headMethod := FALSE;

    (* protected by srv.mu *)
    idle := FALSE;
    writeDeadline: Time.T := 0.0D0;   (* 0.0 if no write is under way *)
    timedOut := FALSE;
  OVERRIDES
    apply := Run;
  END;

  BodyRd = Rd.T OBJECT
    c: Conn;
    chunked := FALSE;
    remaining := 0;    (* bytes left in the body, or in this chunk *)
    length_ := -1;
    total := 0;
    finished := FALSE;
    started := FALSE;  (* some chunk has begun *)
  OVERRIDES
    seek := BodySeek;
    length := BodyLength;
  END;

  BodyWr = Wr.T OBJECT
    resp: Response;
  OVERRIDES
    seek := BodyWrSeek;
    flush := BodyWrFlush;
  END;

  Watchdog = Thread.Closure OBJECT
    srv: T;
  OVERRIDES
    apply := Watch;
  END;

VAR
  Invalid := Atom.FromText ("HTTPServer.Invalid");

(* the list of failures *)

PROCEDURE ReadError (atom: Atom.T; what: TEXT): AtomList.T =
  BEGIN
    RETURN AtomList.List2 (atom, Atom.FromText (what));
  END ReadError;

PROCEDURE StatusError (status: INTEGER): AtomList.T =
  BEGIN
    CASE status OF
    | 408 => RETURN ReadError (HTTP.Timeout, "timeout in the body of a request");
    | 413 => RETURN ReadError (HTTP.TooLarge, "the body of a request is too large");
    ELSE RETURN ReadError (HTTP.BadMessage, "malformed body of a request");
    END;
  END StatusError;

PROCEDURE StatusOf (e: AtomList.T): INTEGER =
  BEGIN
    IF e # NIL THEN
      IF e.head = HTTP.Timeout THEN RETURN 408;
      ELSIF e.head = HTTP.TooLarge THEN RETURN 413;
      ELSIF e.head = HTTP.BadMessage THEN RETURN 400;
      END;
    END;
    RETURN 500;
  END StatusOf;

PROCEDURE Note (t: T; msg: TEXT) =
  BEGIN
    IF t.o.log # NIL THEN t.o.log.log (msg) END;
  END Note;

(* the output of a connection *)

PROCEDURE Send (c: Conn; READONLY a: ARRAY OF CHAR) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    LOCK c.srv.mu DO
      c.writeDeadline := Time.Now () + c.srv.o.writeTimeout;
    END;
    TRY
      TRY
        c.wrote := TRUE;
        c.fd.put (a);
      EXCEPT
      | Wr.Failure (e) =>
          LOCK c.srv.mu DO
            IF c.timedOut THEN e := ReadError (HTTP.Timeout, "timeout in a write"); END;
          END;
          RAISE Wr.Failure (e);
      END;
    FINALLY
      LOCK c.srv.mu DO
        c.writeDeadline := 0.0D0;
      END;
    END;
  END Send;

PROCEDURE FlushOut (c: Conn) RAISES {Wr.Failure, Thread.Alerted} =
  VAR n := c.outN;
  BEGIN
    IF n > 0 THEN
      c.outN := 0;
      Send (c, SUBARRAY (c.out^, 0, n));
    END;
  END FlushOut;

PROCEDURE PutText (c: Conn; t: TEXT) RAISES {Wr.Failure, Thread.Alerted} =
  VAR n := Text.Length (t);  i := 0;  k: INTEGER;
  BEGIN
    WHILE i < n DO
      IF c.outN = NUMBER (c.out^) THEN FlushOut (c) END;
      k := MIN (n - i, NUMBER (c.out^) - c.outN);
      Text.SetChars (SUBARRAY (c.out^, c.outN, k), t, i);
      INC (c.outN, k);
      INC (i, k);
    END;
  END PutText;

PROCEDURE PutChars (c: Conn; READONLY a: ARRAY OF CHAR) RAISES {Wr.Failure, Thread.Alerted} =
  VAR n := NUMBER (a);  i := 0;  k: INTEGER;
  BEGIN
    IF n >= NUMBER (c.out^) THEN
      FlushOut (c);
      Send (c, a);
      RETURN;
    END;
    WHILE i < n DO
      IF c.outN = NUMBER (c.out^) THEN FlushOut (c) END;
      k := MIN (n - i, NUMBER (c.out^) - c.outN);
      SUBARRAY (c.out^, c.outN, k) := SUBARRAY (a, i, k);
      INC (c.outN, k);
      INC (i, k);
    END;
  END PutChars;

(* the input of a connection *)

(* Moves the unused bytes to the start of the buffer. *)
PROCEDURE Compact (c: Conn) =
  VAR n := c.end - c.pos;
  BEGIN
    IF c.pos > 0 THEN
      FOR k := 0 TO n - 1 DO c.in[k] := c.in[c.pos + k] END;
      c.pos := 0;
      c.end := n;
    END;
  END Compact;

(* Reads more input until "c.deadline". The result is 0 at the end of the input. *)
PROCEDURE Fill (c: Conn): CARDINAL RAISES {Reject, Rd.Failure, Thread.Alerted} =
  VAR wait := c.deadline - Time.Now ();  n: CARDINAL;
  BEGIN
    IF wait <= 0.0D0 THEN RAISE Reject (408) END;
    TRY
      n := c.fd.get (SUBARRAY (c.in^, c.end, NUMBER (c.in^) - c.end), wait);
    EXCEPT
    | ConnFD.TimedOut => RAISE Reject (408);
    END;
    INC (c.end, n);
    RETURN n;
  END Fill;

(* One line of at most "max" bytes without its CRLF. A line that is too long
   gets the status "tooLong". A bare LF is an error, and a bare CR is one
   that the callers find, because they reject control characters. *)
PROCEDURE GetLine (c: Conn; max, tooLong: INTEGER): TEXT
  RAISES {Reject, Rd.Failure, Thread.Alerted} =
  VAR i := c.pos;  len: INTEGER;  line: TEXT;
  BEGIN
    LOOP
      WHILE i < c.end DO
        IF c.in[i] = '\n' THEN
          len := i - c.pos;
          IF len = 0 OR c.in[i - 1] # '\r' THEN RAISE Reject (400) END;
          IF len - 1 > max THEN RAISE Reject (tooLong) END;
          line := Text.FromChars (SUBARRAY (c.in^, c.pos, len - 1));
          c.pos := i + 1;
          RETURN line;
        END;
        INC (i);
      END;
      IF i - c.pos > max + 1 THEN RAISE Reject (tooLong) END;
      DEC (i, c.pos);
      Compact (c);
      IF c.end = NUMBER (c.in^) THEN RAISE Reject (tooLong) END;
      IF Fill (c) = 0 THEN RAISE Reject (400) END;
    END;
  END GetLine;

(* A line of the head, charged to the budget of the head. *)
PROCEDURE HeadLine (c: Conn; tooLong: INTEGER): TEXT
  RAISES {Reject, Rd.Failure, Thread.Alerted} =
  VAR line := GetLine (c, c.srv.o.maxLineBytes, tooLong);
  BEGIN
    DEC (c.budget, Text.Length (line) + 2);
    IF c.budget < 0 THEN RAISE Reject (431) END;
    RETURN line;
  END HeadLine;

(* the head of a request *)

CONST
  OWS = SET OF CHAR {' ', '\t'};
  HostChars = SET OF CHAR {'A' .. 'Z', 'a' .. 'z', '0' .. '9', '-', '.', '_', '~', '%', '!',
                           '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=', ':', '[', ']'};

PROCEDURE Trim (t: TEXT): TEXT =
  VAR lo := 0;  hi := Text.Length (t);
  BEGIN
    WHILE lo < hi AND Text.GetChar (t, lo) IN OWS DO INC (lo) END;
    WHILE hi > lo AND Text.GetChar (t, hi - 1) IN OWS DO DEC (hi) END;
    RETURN Text.Sub (t, lo, hi - lo);
  END Trim;

(* Does a list field of this name, of any number of lines, contain the token? *)
PROCEDURE HasToken (h: HTTP.Headers; name, token: TEXT): BOOLEAN =
  VAR it := h.iterate ();  n, v: TEXT;  start, comma: INTEGER;
  BEGIN
    WHILE it.next (n, v) DO
      IF TextExtras.CIEqual (n, name) THEN
        start := 0;
        REPEAT
          comma := Text.FindChar (v, ',', start);
          IF comma < 0 THEN comma := Text.Length (v) END;
          IF TextExtras.CIEqual (Trim (Text.Sub (v, start, comma - start)), token) THEN
            RETURN TRUE;
          END;
          start := comma + 1;
        UNTIL start > Text.Length (v);
      END;
    END;
    RETURN FALSE;
  END HasToken;

PROCEDURE Count (h: HTTP.Headers; name: TEXT): INTEGER =
  VAR it := h.iterate ();  n, v: TEXT;  count := 0;
  BEGIN
    WHILE it.next (n, v) DO
      IF TextExtras.CIEqual (n, name) THEN INC (count) END;
    END;
    RETURN count;
  END Count;

PROCEDURE StartsWithNoCase (t, prefix: TEXT): BOOLEAN =
  BEGIN
    RETURN Text.Length (t) >= Text.Length (prefix)
             AND TextExtras.CIEqual (Text.Sub (t, 0, Text.Length (prefix)), prefix);
  END StartsWithNoCase;

(* Sets "version" and returns the bytes of a digit pair, or rejects. *)
PROCEDURE ParseVersion (v: TEXT) RAISES {Reject} =
  BEGIN
    IF Text.Length (v) # 8 OR NOT Text.Equal (Text.Sub (v, 0, 5), "HTTP/")
         OR Text.GetChar (v, 6) # '.'
         OR NOT Text.GetChar (v, 5) IN SET OF CHAR {'0' .. '9'}
         OR NOT Text.GetChar (v, 7) IN SET OF CHAR {'0' .. '9'} THEN
      RAISE Reject (400);
    END;
    IF NOT Text.Equal (v, "HTTP/1.1") AND NOT Text.Equal (v, "HTTP/1.0") THEN
      RAISE Reject (505);
    END;
  END ParseVersion;

PROCEDURE ParseTarget (req: Request) RAISES {Reject} =
  VAR t := req.target;  n := Text.Length (t);  start := 0;  mark, end: INTEGER;  ch: CHAR;
      raw: TEXT;
  BEGIN
    IF n = 0 OR Text.HasWideChars (t) THEN RAISE Reject (400) END;
    FOR i := 0 TO n - 1 DO
      ch := Text.GetChar (t, i);
      IF ch <= ' ' OR ch >= '\177' OR ch = '#' THEN RAISE Reject (400) END;
    END;
    IF Text.Equal (t, "*") THEN
      IF NOT Text.Equal (req.method, "OPTIONS") THEN RAISE Reject (400) END;
      req.path := "*";
      RETURN;
    END;
    IF StartsWithNoCase (t, "http://") OR StartsWithNoCase (t, "https://") THEN
      start := Text.FindChar (t, ':') + 3;
      end := start;
      WHILE end < n AND NOT Text.GetChar (t, end) IN SET OF CHAR {'/', '?'} DO INC (end) END;
      IF end = start THEN RAISE Reject (400) END;
      req.headers.set ("Host", Text.Sub (t, start, end - start));
      start := end;
    ELSIF Text.GetChar (t, 0) # '/' THEN
      RAISE Reject (400);
    END;
    mark := Text.FindChar (t, '?', start);
    IF mark < 0 THEN
      raw := Text.Sub (t, start);
    ELSE
      raw := Text.Sub (t, start, mark - start);
      req.query := Text.Sub (t, mark + 1);
    END;
    IF Text.Length (raw) = 0 THEN raw := "/" END;
    TRY
      req.path := HTTP.Unescape (raw);
    EXCEPT
    | HTTP.Error => RAISE Reject (400);
    END;
  END ParseTarget;

PROCEDURE ParseFields (c: Conn; h: HTTP.Headers)
  RAISES {Reject, Rd.Failure, Thread.Alerted} =
  VAR line, name, value: TEXT;  colon, count := 0;
  BEGIN
    LOOP
      line := HeadLine (c, 431);
      IF Text.Length (line) = 0 THEN RETURN END;
      IF Text.GetChar (line, 0) IN OWS THEN RAISE Reject (400) END;
      INC (count);
      IF count > c.srv.o.maxHeaders THEN RAISE Reject (431) END;
      colon := Text.FindChar (line, ':');
      IF colon <= 0 THEN RAISE Reject (400) END;
      name := Text.Sub (line, 0, colon);
      value := Trim (Text.Sub (line, colon + 1));
      IF NOT HTTP.ValidName (name) OR NOT HTTP.ValidValue (value) THEN RAISE Reject (400) END;
      h.add (name, value);
    END;
  END ParseFields;

PROCEDURE CheckHost (req: Request) RAISES {Reject} =
  VAR count := Count (req.headers, "Host");  host: TEXT;
  BEGIN
    IF count > 1 OR count = 0 AND Text.Equal (req.version, "HTTP/1.1") THEN
      RAISE Reject (400);
    END;
    IF count = 1 THEN
      host := req.headers.get ("Host");
      FOR i := 0 TO Text.Length (host) - 1 DO
        IF NOT Text.GetChar (host, i) IN HostChars THEN RAISE Reject (400) END;
      END;
    END;
  END CheckHost;

(* The length that a "Content-Length" states, or a rejection. *)
PROCEDURE ParseLength (t: TEXT; maxBytes: INTEGER): INTEGER RAISES {Reject} =
  VAR n := Text.Length (t);  v := 0;  ch: CHAR;  start := 0;
  BEGIN
    IF n = 0 THEN RAISE Reject (400) END;
    WHILE start < n - 1 AND Text.GetChar (t, start) = '0' DO INC (start) END;
    FOR i := 0 TO n - 1 DO
      ch := Text.GetChar (t, i);
      IF ch < '0' OR ch > '9' THEN RAISE Reject (400) END;
      IF i >= start THEN
        IF i - start >= MaxLengthDigits THEN RAISE Reject (413) END;
        v := v * 10 + ORD (ch) - ORD ('0');
      END;
    END;
    IF v > maxBytes THEN RAISE Reject (413) END;
    RETURN v;
  END ParseLength;

(* What comes after the head. A message whose framing is unclear must not be
   processed: the connection would no longer be in step with the client. *)
PROCEDURE ParseFraming (c: Conn; req: Request) RAISES {Reject} =
  VAR h := req.headers;  lengths := Count (h, "Content-Length");
      codings := Count (h, "Transfer-Encoding");  old := Text.Equal (req.version, "HTTP/1.0");
      body := NEW (BodyRd, c := c, buff := NIL, st := 0, lo := 0, hi := 0, cur := 0,
                   intermittent := TRUE, seekable := FALSE, closed := FALSE);
      expect := h.get ("Expect");
  BEGIN
    req.body := body;
    IF codings > 0 THEN
      IF lengths > 0 OR old THEN RAISE Reject (400) END;
      IF codings > 1 OR NOT TextExtras.CIEqual (h.get ("Transfer-Encoding"), "chunked") THEN
        RAISE Reject (501);
      END;
      body.chunked := TRUE;
    ELSIF lengths > 1 THEN
      RAISE Reject (400);
    ELSIF lengths = 1 THEN
      body.remaining := ParseLength (h.get ("Content-Length"), c.srv.o.maxBodyBytes);
      body.length_ := body.remaining;
      body.finished := body.remaining = 0;
    ELSE
      body.length_ := 0;
      body.finished := TRUE;
    END;
    IF expect # NIL AND NOT old THEN
      IF NOT TextExtras.CIEqual (expect, "100-continue") THEN RAISE Reject (417) END;
      c.expecting := NOT body.finished;
    END;
    c.keep := NOT old AND NOT HasToken (h, "Connection", "close");
  END ParseFraming;

(* Waits for the first byte of a request. *)
PROCEDURE AwaitRequest (c: Conn; wait: LONGREAL) RAISES {Gone, Rd.Failure, Thread.Alerted} =
  VAR n: CARDINAL;
  BEGIN
    IF c.pos < c.end THEN RETURN END;
    c.pos := 0;
    c.end := 0;
    LOCK c.srv.mu DO
      IF c.srv.stopping THEN RAISE Gone END;
      c.idle := TRUE;
    END;
    TRY
      TRY
        n := c.fd.get (c.in^, wait);
      EXCEPT
      | ConnFD.TimedOut => RAISE Gone;
      END;
      IF n = 0 THEN RAISE Gone END;
      c.end := n;
    FINALLY
      LOCK c.srv.mu DO
        c.idle := FALSE;
      END;
    END;
  END AwaitRequest;

PROCEDURE ReadHead (c: Conn): Request RAISES {Reject, Rd.Failure, Thread.Alerted} =
  VAR req := NEW (Request, headers := HTTP.NewHeaders (), peer := c.peer,
                  secure := c.ctx # NIL);
      line: TEXT;  first, last: INTEGER;
  BEGIN
    c.deadline := Time.Now () + c.srv.o.headerTimeout;
    c.budget := c.srv.o.maxHeaderBytes;
    c.headMethod := FALSE;
    REPEAT
      line := HeadLine (c, 414);
    UNTIL Text.Length (line) # 0;
    first := Text.FindChar (line, ' ');
    last := Text.FindCharR (line, ' ');
    IF first <= 0 OR first = last THEN RAISE Reject (400) END;
    req.method := Text.Sub (line, 0, first);
    c.headMethod := Text.Equal (req.method, "HEAD");
    req.target := Text.Sub (line, first + 1, last - first - 1);
    req.version := Text.Sub (line, last + 1);
    IF NOT HTTP.ValidName (req.method) THEN RAISE Reject (400) END;
    ParseVersion (req.version);
    IF Text.Equal (req.method, "CONNECT") THEN RAISE Reject (501) END;
    ParseFields (c, req.headers);
    CheckHost (req);       (* before an absolute target replaces the field *)
    ParseTarget (req);
    ParseFraming (c, req);
    RETURN req;
  END ReadHead;

(* the body of a request *)

PROCEDURE SendContinue (c: Conn) RAISES {Rd.Failure, Thread.Alerted} =
  BEGIN
    IF c.expecting THEN
      c.expecting := FALSE;
      IF NOT c.resp.headSent THEN
        TRY
          PutText (c, "HTTP/1.1 100 Continue\r\n\r\n");
          FlushOut (c);
          c.wrote := FALSE;
        EXCEPT
        | Wr.Failure (e) => RAISE Rd.Failure (e);
        END;
      END;
    END;
  END SendContinue;

(* Reads the line with the size of the next chunk, and the trailer after the
   last one. *)
PROCEDURE NextChunk (rd: BodyRd) RAISES {Reject, Rd.Failure, Thread.Alerted} =
  VAR c := rd.c;  line: TEXT;  size := 0;  digits := 0;  i := 0;  n: INTEGER;  ch: CHAR;
      digit: INTEGER;
  BEGIN
    c.deadline := Time.Now () + c.srv.o.readTimeout;
    IF rd.started THEN
      IF Text.Length (GetLine (c, 0, 400)) # 0 THEN RAISE Reject (400) END;
    END;
    rd.started := TRUE;
    line := GetLine (c, ChunkLineBytes, 400);
    n := Text.Length (line);
    IF Text.HasWideChars (line) THEN RAISE Reject (400) END;
    WHILE i < n DO
      ch := Text.GetChar (line, i);
      CASE ch OF
      | '0' .. '9' => digit := ORD (ch) - ORD ('0');
      | 'a' .. 'f' => digit := ORD (ch) - ORD ('a') + 10;
      | 'A' .. 'F' => digit := ORD (ch) - ORD ('A') + 10;
      ELSE EXIT;
      END;
      INC (digits);
      IF size > (LAST (INTEGER) - digit) DIV 16 THEN RAISE Reject (413) END;
      size := size * 16 + digit;
      INC (i);
    END;
    IF digits = 0 THEN RAISE Reject (400) END;
    IF i < n AND NOT Text.GetChar (line, i) IN SET OF CHAR {';', ' ', '\t'} THEN
      RAISE Reject (400);
    END;
    IF NOT HTTP.ValidValue (line) THEN RAISE Reject (400) END;
    IF size > c.srv.o.maxBodyBytes - rd.total THEN RAISE Reject (413) END;
    rd.remaining := size;
    IF size = 0 THEN
      (* the trailer *)
      c.budget := c.srv.o.maxHeaderBytes;
      WHILE Text.Length (HeadLine (c, 431)) # 0 DO END;
      rd.finished := TRUE;
    END;
  END NextChunk;

(* Makes the next piece of the body available as the buffer of "rd". *)
PROCEDURE Advance (rd: BodyRd; dontBlock: BOOLEAN): RdClass.SeekResult
  RAISES {Reject, Rd.Failure, Thread.Alerted} =
  VAR c := rd.c;  n: INTEGER;  wait: LONGREAL;  got: CARDINAL;
  BEGIN
    IF c = NIL OR rd.finished THEN RETURN RdClass.SeekResult.Eof END;
    SendContinue (c);
    IF rd.chunked AND rd.remaining = 0 THEN
      IF dontBlock THEN RETURN RdClass.SeekResult.WouldBlock END;
      NextChunk (rd);
      IF rd.finished THEN RETURN RdClass.SeekResult.Eof END;
    END;
    IF c.pos = c.end THEN
      c.pos := 0;
      c.end := 0;
      IF dontBlock THEN wait := 0.0D0 ELSE wait := c.srv.o.readTimeout END;
      TRY
        got := c.fd.get (c.in^, wait);
      EXCEPT
      | ConnFD.TimedOut =>
          IF dontBlock THEN RETURN RdClass.SeekResult.WouldBlock END;
          RAISE Reject (408);
      END;
      IF got = 0 THEN RAISE Reject (400) END;
      c.end := got;
    END;
    n := MIN (c.end - c.pos, rd.remaining);
    rd.buff := c.in;
    rd.st := c.pos;
    rd.lo := rd.cur;
    rd.hi := rd.cur + n;
    INC (c.pos, n);
    DEC (rd.remaining, n);
    INC (rd.total, n);
    IF NOT rd.chunked AND rd.remaining = 0 THEN rd.finished := TRUE END;
    RETURN RdClass.SeekResult.Ready;
  END Advance;

PROCEDURE BodySeek (rd: BodyRd; <*UNUSED*> pos: CARDINAL; dontBlock: BOOLEAN): RdClass.SeekResult
  RAISES {Rd.Failure, Thread.Alerted} =
  BEGIN
    TRY
      RETURN Advance (rd, dontBlock);
    EXCEPT
    | Reject (status) => RAISE Rd.Failure (StatusError (status));
    END;
  END BodySeek;

PROCEDURE BodyLength (rd: BodyRd): INTEGER =
  BEGIN
    RETURN rd.length_;
  END BodyLength;

(* Ends the use of the reader, which may still be held by a handler. *)
PROCEDURE Detach (rd: BodyRd) =
  BEGIN
    RdClass.Lock (rd);
    TRY
      rd.c := NIL;
      rd.hi := rd.cur;
      rd.lo := rd.cur;
    FINALLY
      RdClass.Unlock (rd);
    END;
  END Detach;

(* Reads the rest of the body, for the next request to find its start. *)
PROCEDURE Drain (rd: BodyRd): BOOLEAN RAISES {Thread.Alerted} =
  BEGIN
    TRY
      RdClass.Lock (rd);
      TRY
        WHILE Advance (rd, FALSE) = RdClass.SeekResult.Ready DO
          rd.cur := rd.hi;
        END;
      FINALLY
        RdClass.Unlock (rd);
      END;
      RETURN TRUE;
    EXCEPT
    | Reject, Rd.Failure => RETURN FALSE;
    END;
  END Drain;

(* the response *)

(* Is "t" a weight of RFC 9110 that is above zero? "t" follows the semicolon. *)
PROCEDURE PositiveWeight (t: TEXT): BOOLEAN =
  VAR s := Trim (t);  n := Text.Length (s);  first, ch: CHAR;  positive: BOOLEAN;
  BEGIN
    IF n < 3 OR n > 7 OR NOT Text.GetChar (s, 0) IN SET OF CHAR {'q', 'Q'}
         OR Text.GetChar (s, 1) # '=' THEN
      RETURN FALSE;
    END;
    first := Text.GetChar (s, 2);
    IF NOT first IN SET OF CHAR {'0', '1'} OR n > 3 AND Text.GetChar (s, 3) # '.' THEN
      RETURN FALSE;
    END;
    positive := first = '1';
    FOR i := 4 TO n - 1 DO
      ch := Text.GetChar (s, i);
      IF NOT ch IN SET OF CHAR {'0' .. '9'} THEN RETURN FALSE END;
      IF ch # '0' THEN
        IF first = '1' THEN RETURN FALSE END;
        positive := TRUE;
      END;
    END;
    RETURN positive;
  END PositiveWeight;

(* Does "Accept-Encoding" allow gzip? A name of gzip counts over "*", and where
   the fields are malformed or contradict each other the answer is no, which
   is always correct: the body then goes out as it is. *)
PROCEDURE AcceptsGzip (h: HTTP.Headers): BOOLEAN =
  VAR it := h.iterate ();  n, v, member, coding: TEXT;  start, comma, semi: INTEGER;
      allowed: BOOLEAN;  named, namedOff, star, starOff := FALSE;
  BEGIN
    WHILE it.next (n, v) DO
      IF TextExtras.CIEqual (n, "Accept-Encoding") THEN
        start := 0;
        REPEAT
          comma := Text.FindChar (v, ',', start);
          IF comma < 0 THEN comma := Text.Length (v) END;
          member := Text.Sub (v, start, comma - start);
          semi := Text.FindChar (member, ';');
          IF semi < 0 THEN
            coding := Trim (member);
            allowed := TRUE;
          ELSE
            coding := Trim (Text.Sub (member, 0, semi));
            allowed := PositiveWeight (Text.Sub (member, semi + 1));
          END;
          IF TextExtras.CIEqual (coding, "gzip") OR TextExtras.CIEqual (coding, "x-gzip") THEN
            named := TRUE;
            IF NOT allowed THEN namedOff := TRUE END;
          ELSIF Text.Equal (coding, "*") THEN
            star := TRUE;
            IF NOT allowed THEN starOff := TRUE END;
          END;
          start := comma + 1;
        UNTIL start > Text.Length (v);
      END;
    END;
    IF named THEN RETURN NOT namedOff END;
    RETURN star AND NOT starOff;
  END AcceptsGzip;

PROCEDURE MayCompress (r: Response): BOOLEAN =
  VAR st := r.status;
  BEGIN
    RETURN r.compression AND st # 204 AND st # 206 AND st # 304
             AND r.headers.get ("Content-Encoding") = NIL
             AND r.headers.get ("Content-Range") = NIL
             AND AcceptsGzip (r.req.headers);
  END MayCompress;

PROCEDURE ResponseCompress (r: Response) =
  BEGIN
    IF r.wr = NIL THEN r.compression := TRUE END;
  END ResponseCompress;

PROCEDURE ResponseBody (r: Response): Wr.T =
  BEGIN
    IF r.wr = NIL THEN
      r.wr := NEW (BodyWr, resp := r, buff := NEW (REF ARRAY OF CHAR, BodyBufferSize),
                   st := 0, lo := 0, cur := 0, hi := BodyBufferSize,
                   buffered := TRUE, seekable := FALSE, closed := FALSE);
      IF MayCompress (r) THEN r.gz := Deflate.NewWr (r.wr) END;
    END;
    IF r.gz # NIL THEN RETURN r.gz END;
    RETURN r.wr;
  END ResponseBody;

(* The fields that compression changes. This happens with the head, because a
   handler may set fields until then. *)
PROCEDURE CompressionFields (r: Response) =
  VAR h := r.headers;  tag: TEXT;
  BEGIN
    IF r.compression AND NOT HasToken (h, "Vary", "Accept-Encoding")
         AND NOT HasToken (h, "Vary", "*") THEN
      h.add ("Vary", "Accept-Encoding");
    END;
    IF r.gz # NIL AND r.status # 204 AND r.status # 304 THEN
      h.set ("Content-Encoding", "gzip");
      h.remove ("Content-Length");
      tag := h.get ("ETag");
      IF tag # NIL AND NOT Text.Equal (Text.Sub (tag, 0, 2), "W/") THEN
        h.set ("ETag", "W/" & tag);
      END;
    END;
  END CompressionFields;

PROCEDURE Refuse (what: TEXT) RAISES {Wr.Failure} =
  BEGIN
    RAISE Wr.Failure (ReadError (Invalid, what));
  END Refuse;

PROCEDURE Stopping (t: T): BOOLEAN =
  BEGIN
    LOCK t.mu DO
      RETURN t.stopping;
    END;
  END Stopping;

(* Sends the head. "length" is the length of the whole body if the handler
   has finished, otherwise -1. *)
PROCEDURE SendHead (r: Response; length: INTEGER) RAISES {Wr.Failure, Thread.Alerted} =
  VAR c := r.c;  h := r.headers;  st := r.status;  it: HTTP.Iterator;  name, value: TEXT;
      declared := -1;  head := Text.Equal (r.req.method, "HEAD");
      old := Text.Equal (r.req.version, "HTTP/1.0");  framing: TEXT := NIL;
  BEGIN
    IF st < 200 OR st > 599 THEN Refuse ("status of a response") END;
    CompressionFields (r);
    it := h.iterate ();
    WHILE it.next (name, value) DO
      IF NOT HTTP.ValidName (name) OR NOT HTTP.ValidValue (value) THEN
        Refuse ("field of a response");
      END;
      IF TextExtras.CIEqual (name, "Transfer-Encoding") THEN
        Refuse ("Transfer-Encoding in a response");
      ELSIF TextExtras.CIEqual (name, "Content-Length") THEN
        TRY
          declared := ParseLength (value, LAST (INTEGER));
        EXCEPT
        | Reject => Refuse ("Content-Length of a response");
        END;
      ELSIF TextExtras.CIEqual (name, "Connection") THEN
        IF HasToken (h, "Connection", "close") THEN c.keep := FALSE END;
      END;
    END;
    IF c.expecting THEN
      (* the client may or may not send the body now *)
      c.expecting := FALSE;
      c.keep := FALSE;
    END;
    IF Stopping (c.srv) THEN c.keep := FALSE END;

    r.noBody := head OR st = 204 OR st = 304;
    IF st = 204 THEN
      IF declared >= 0 THEN h.remove ("Content-Length") END;
    ELSIF st = 304 THEN
      (* a declared length describes the entity and stays *)
    ELSIF declared >= 0 THEN
      IF NOT head THEN r.remaining := declared END;
    ELSIF length >= 0 THEN
      framing := "Content-Length: " & Fmt.Int (length);
    ELSIF head THEN
      (* the length is unknown, and no body follows *)
    ELSIF old THEN
      c.keep := FALSE;
    ELSE
      framing := "Transfer-Encoding: chunked";
      r.chunked := TRUE;
    END;

    r.headSent := TRUE;
    PutText (c, "HTTP/1.1 " & Fmt.Int (st) & " " & HTTP.Reason (st) & "\r\n");
    IF h.get ("Date") = NIL THEN
      PutText (c, "Date: " & HTTP.FormatDate (Time.Now ()) & "\r\n");
    END;
    it := h.iterate ();
    WHILE it.next (name, value) DO
      PutText (c, name & ": " & value & "\r\n");
    END;
    IF framing # NIL THEN PutText (c, framing & "\r\n") END;
    IF NOT c.keep AND NOT HasToken (h, "Connection", "close") THEN
      PutText (c, "Connection: close\r\n");
    END;
    PutText (c, "\r\n");
  END SendHead;

PROCEDURE PutData (r: Response; READONLY a: ARRAY OF CHAR) RAISES {Wr.Failure, Thread.Alerted} =
  VAR c := r.c;  n := NUMBER (a);
  BEGIN
    IF n = 0 OR r.noBody THEN RETURN END;
    IF r.remaining >= 0 THEN
      IF n > r.remaining THEN
        RAISE Wr.Failure (ReadError (Invalid, "more bytes than the Content-Length"));
      END;
      DEC (r.remaining, n);
    END;
    IF r.chunked THEN
      PutText (c, Fmt.Int (n, 16) & "\r\n");
      PutChars (c, a);
      PutText (c, "\r\n");
    ELSE
      PutChars (c, a);
    END;
  END PutData;

PROCEDURE CheckOpen (r: Response) RAISES {Wr.Failure} =
  BEGIN
    IF r.done THEN
      RAISE Wr.Failure (ReadError (Invalid, "write after the end of a response"));
    END;
  END CheckOpen;

PROCEDURE Advanced (wr: BodyWr) RAISES {Wr.Failure, Thread.Alerted} =
  VAR r := wr.resp;  n := wr.cur - wr.lo;
  BEGIN
    CheckOpen (r);
    IF NOT r.headSent THEN SendHead (r, -1) END;
    PutData (r, SUBARRAY (wr.buff^, 0, n));
    wr.lo := wr.cur;
    wr.hi := wr.lo + NUMBER (wr.buff^);
  END Advanced;

PROCEDURE BodyWrSeek (wr: BodyWr; <*UNUSED*> n: CARDINAL) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    Advanced (wr);
  END BodyWrSeek;

PROCEDURE BodyWrFlush (wr: BodyWr) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    Advanced (wr);
    FlushOut (wr.resp.c);
  END BodyWrFlush;

(* Sends the rest of a response whose handler has returned. *)
PROCEDURE Finish (r: Response) RAISES {Wr.Failure, Thread.Alerted} =
  VAR c := r.c;  wr := r.wr;  n := 0;
  BEGIN
    IF r.gz # NIL THEN Deflate.Finish (r.gz) END;   (* into the buffer of "wr" *)
    IF wr # NIL THEN
      WrClass.Lock (wr);
    END;
    TRY
      IF wr # NIL AND NOT wr.closed THEN n := wr.cur - wr.lo END;
      IF NOT r.headSent THEN SendHead (r, n) END;
      IF n > 0 THEN PutData (r, SUBARRAY (wr.buff^, 0, n)) END;
      IF wr # NIL THEN
        wr.lo := wr.cur;
        wr.hi := wr.cur;
      END;
      r.done := TRUE;
      IF r.chunked THEN PutText (c, "0\r\n\r\n") END;
      IF r.remaining > 0 THEN c.keep := FALSE END;
      FlushOut (c);
    FINALLY
      IF wr # NIL THEN
        WrClass.Unlock (wr);
      END;
    END;
  END Finish;

(* An answer of the server itself; the connection ends after it. *)
PROCEDURE SendError (c: Conn; status: INTEGER; head: BOOLEAN) =
  VAR text := HTTP.Reason (status) & "\n";
  BEGIN
    TRY
      c.outN := 0;
      PutText (c, "HTTP/1.1 " & Fmt.Int (status) & " " & HTTP.Reason (status) & "\r\n"
                    & "Date: " & HTTP.FormatDate (Time.Now ()) & "\r\n"
                    & "Content-Type: text/plain\r\n"
                    & "Content-Length: " & Fmt.Int (Text.Length (text)) & "\r\n"
                    & "Connection: close\r\n\r\n");
      IF NOT head THEN PutText (c, text) END;
      FlushOut (c);
    EXCEPT
    | Wr.Failure, Thread.Alerted =>
    END;
  END SendError;

PROCEDURE Find (t: T; req: Request): Handler =
  VAR handlers: REF ARRAY OF Handler;
  BEGIN
    LOCK t.mu DO
      handlers := t.handlers;
    END;
    IF handlers = NIL THEN RETURN NIL END;
    FOR p := FIRST (Priority) TO LAST (Priority) DO
      FOR i := 0 TO LAST (handlers^) DO
        IF handlers[i].priority = p AND handlers[i].accept (req) THEN
          RETURN handlers[i];
        END;
      END;
    END;
    RETURN NIL;
  END Find;

PROCEDURE NotFound (resp: Response) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    resp.status := 404;
    resp.headers.set ("Content-Type", "text/plain");
    Wr.PutText (resp.body (), HTTP.Reason (404) & "\n");
  END NotFound;

TYPE Outcome = {Again, Linger, Abort};

(* The part of a failed response that the client can still see. *)
PROCEDURE AfterFailure (c: Conn; req: Request; status: INTEGER): Outcome =
  BEGIN
    c.keep := FALSE;
    IF c.wrote THEN RETURN Outcome.Abort END;
    SendError (c, status, Text.Equal (req.method, "HEAD"));
    RETURN Outcome.Linger;
  END AfterFailure;

PROCEDURE Respond (c: Conn; req: Request): Outcome =
  VAR resp := NEW (Response, c := c, req := req, headers := HTTP.NewHeaders ());
      handler: Handler;  body := NARROW (req.body, BodyRd);  result: Outcome;
  BEGIN
    c.resp := resp;
    c.wrote := FALSE;
    TRY
      TRY
        handler := Find (c.srv, req);
        IF handler = NIL THEN
          NotFound (resp);
        ELSE
          handler.request (req, resp);
        END;
        Finish (resp);
        resp.done := TRUE;
        IF c.keep AND NOT Drain (body) THEN c.keep := FALSE END;
        IF c.keep THEN result := Outcome.Again ELSE result := Outcome.Linger END;
      EXCEPT
      | Rd.Failure (e) => result := AfterFailure (c, req, StatusOf (e));
      | Wr.Failure (e) => result := AfterFailure (c, req, StatusOf (e));
      | Thread.Alerted => result := Outcome.Abort;
      ELSE
        Note (c.srv, "HTTPServer: a checked runtime error or an unlisted exception in a handler");
        result := AfterFailure (c, req, 500);
      END;
    FINALLY
      resp.done := TRUE;
      IF resp.gz # NIL THEN Deflate.Discard (resp.gz) END;
      Detach (body);
      c.resp := NIL;
    END;
    RETURN result;
  END Respond;

(* the connection *)

PROCEDURE SetCloseDeadline (c: Conn) =
  BEGIN
    LOCK c.srv.mu DO
      c.writeDeadline := Time.Now () + c.srv.o.writeTimeout;
    END;
  END SetCloseDeadline;

(* The output ends first, and the input is read for a while: a client that
   is still sending would otherwise get a reset, which can destroy the
   response before it has read that. *)
PROCEDURE Linger (c: Conn) =
  VAR buf: ARRAY [0 .. 4095] OF CHAR;  total := 0;  n: CARDINAL;
      deadline := Time.Now () + LingerTime;  wait: LONGREAL;
  BEGIN
    TRY
      SetCloseDeadline (c);
      c.fd.shutdownOut ();
      LOOP
        wait := deadline - Time.Now ();
        IF wait <= 0.0D0 OR total >= LingerBytes THEN EXIT END;
        n := c.fd.get (buf, wait);
        IF n = 0 THEN EXIT END;
        INC (total, n);
      END;
    EXCEPT
    | Rd.Failure, Wr.Failure, Thread.Alerted, ConnFD.TimedOut =>
    END;
  END Linger;

PROCEDURE Exchange (c: Conn): Outcome =
  VAR wait := c.srv.o.headerTimeout;  req: Request := NIL;  outcome: Outcome;
  BEGIN
    TRY
      LOOP
        AwaitRequest (c, wait);
        wait := c.srv.o.idleTimeout;
        req := ReadHead (c);
        outcome := Respond (c, req);
        IF outcome # Outcome.Again THEN EXIT END;
      END;
    EXCEPT
    | Reject (status) =>
        SendError (c, status, c.headMethod);
        outcome := Outcome.Linger;
    | Gone, Rd.Failure, Thread.Alerted => outcome := Outcome.Abort;
    ELSE
      Note (c.srv, "HTTPServer: a checked runtime error while reading a request");
      outcome := Outcome.Abort;
    END;
    RETURN outcome;
  END Exchange;

PROCEDURE Run (c: Conn): REFANY =
  VAR outcome := Outcome.Abort;  t := c.srv;  ready := TRUE;
  BEGIN
    IF c.ctx # NIL THEN
      TRY
        c.fd := TLS.Server (c.ctx, c.tcp, waitFor := t.o.handshakeTimeout);
      EXCEPT
      | IP.Error, Thread.Alerted => ready := FALSE;
      END;
    END;
    IF ready THEN
      outcome := Exchange (c);
      IF outcome = Outcome.Linger THEN Linger (c) END;
      SetCloseDeadline (c);
      IF outcome = Outcome.Abort THEN TCP.Close (c.tcp) END;
      c.fd.close ();
    END;
    LOCK t.mu DO
      c.writeDeadline := 0.0D0;
      IF c.prev # NIL THEN c.prev.next := c.next ELSE t.conns := c.next END;
      IF c.next # NIL THEN c.next.prev := c.prev END;
      DEC (t.active);
      Thread.Broadcast (t.changed);
    END;
    RETURN NIL;
  END Run;

PROCEDURE Start (t: T; tcp: TCP.T; READONLY peer: IP.Endpoint; ctx: TLS.Context) =
  VAR c := NEW (Conn, srv := t, tcp := tcp, fd := tcp, ctx := ctx, peer := peer,
                in := NEW (REF ARRAY OF CHAR, MAX (InputSize, t.o.maxLineBytes + 2)),
                out := NEW (REF ARRAY OF CHAR, OutputSize));
  BEGIN
    LOCK t.mu DO
      IF t.stopping THEN
        TCP.Close (tcp);
        RETURN;
      END;
      INC (t.active);
      c.next := t.conns;
      IF t.conns # NIL THEN t.conns.prev := c END;
      t.conns := c;
      c.th := Thread.Fork (c);
    END;
  END Start;

(* Blocked writes end when the connection below is closed; "TLS.T" would try
   to send a notice first, so the watchdog closes the "TCP.T". *)
PROCEDURE Watch (w: Watchdog): REFANY =
  VAR t := w.srv;  now: Time.T;  late, c: Conn;  victims: REF ARRAY OF Conn;  count: INTEGER;
  BEGIN
    LOOP
      Thread.Pause (1.0D0);
      count := 0;
      LOCK t.mu DO
        IF t.stopping AND t.active = 0 THEN RETURN NIL END;
        now := Time.Now ();
        victims := NEW (REF ARRAY OF Conn, t.active);
        c := t.conns;
        WHILE c # NIL DO
          late := c;
          c := c.next;
          IF late.writeDeadline # 0.0D0 AND now > late.writeDeadline THEN
            late.timedOut := TRUE;
            late.writeDeadline := 0.0D0;
            victims[count] := late;
            INC (count);
          END;
        END;
      END;
      FOR i := 0 TO count - 1 DO TCP.Close (victims[i].tcp) END;
    END;
  END Watch;

PROCEDURE AcceptAll (<*UNUSED*> h: Handler; <*UNUSED*> req: Request): BOOLEAN =
  BEGIN
    RETURN TRUE;
  END AcceptAll;

PROCEDURE New (READONLY o := Defaults): T =
  BEGIN
    RETURN NEW (T, o := o, mu := NEW (MUTEX), changed := NEW (Thread.Condition));
  END New;

PROCEDURE Register (t: T; h: Handler) =
  VAR new: REF ARRAY OF Handler;  n := 0;
  BEGIN
    LOCK t.mu DO
      IF t.handlers # NIL THEN n := NUMBER (t.handlers^) END;
      new := NEW (REF ARRAY OF Handler, n + 1);
      IF n > 0 THEN SUBARRAY (new^, 0, n) := t.handlers^ END;
      new[n] := h;
      t.handlers := new;
    END;
  END Register;

(* A program that ends when "serve" returns must not cut off the responses
   that "shutdown" lets finish. *)
PROCEDURE AwaitFinished (t: T) RAISES {Thread.Alerted} =
  BEGIN
    LOCK t.mu DO
      WHILE NOT t.finished DO
        Thread.AlertWait (t.mu, t.changed);
      END;
    END;
  END AwaitFinished;

PROCEDURE Serve (t: T; c: TCP.Connector; ctx: TLS.Context := NIL)
  RAISES {IP.Error, Thread.Alerted} =
  VAR peer: IP.Endpoint;  tcp: TCP.T;  delay := 0.01D0;  over := FALSE;
  BEGIN
    LOCK t.mu DO
      over := t.stopping;
    END;
    IF over THEN
      AwaitFinished (t);
      RETURN;
    END;
    LOCK t.mu DO
      t.connectors := NEW (Connectors, c := c, next := t.connectors);
      IF t.watchdog = NIL THEN
        t.watchdog := Thread.Fork (NEW (Watchdog, srv := t));
      END;
    END;
    LOOP
      LOCK t.mu DO
        WHILE t.active >= t.o.maxConnections AND NOT t.stopping DO
          Thread.AlertWait (t.mu, t.changed);
        END;
        over := t.stopping;
      END;
      IF over THEN EXIT END;
      tcp := NIL;
      TRY
        tcp := TCPMisc.AcceptFrom (c, peer);
        delay := 0.01D0;
      EXCEPT
      | IP.Error (e) =>
          over := Stopping (t);
          IF NOT over THEN
            IF e # NIL AND e.head = TCP.Closed THEN RAISE IP.Error (e) END;
            Note (t, "HTTPServer: accepting a connection failed");
            Thread.AlertPause (delay);
            delay := MIN (delay * 2.0D0, 1.0D0);
          END;
      END;
      IF over THEN EXIT END;
      IF tcp # NIL THEN Start (t, tcp, peer, ctx) END;
    END;
    AwaitFinished (t);
  END Serve;

PROCEDURE Active (t: T): INTEGER =
  BEGIN
    LOCK t.mu DO
      RETURN t.active;
    END;
  END Active;

PROCEDURE AwaitEnd (t: T; wait: LONGREAL) =
  VAR deadline := Time.Now () + wait;
  BEGIN
    WHILE Active (t) > 0 AND Time.Now () < deadline DO
      Thread.Pause (0.02D0);
    END;
  END AwaitEnd;

PROCEDURE Shutdown (t: T; waitFor := 10.0D0) =
  VAR connectors: Connectors;  c: Conn;  idle: REF ARRAY OF TCP.T;  n := 0;
  BEGIN
    LOCK t.mu DO
      t.stopping := TRUE;
      connectors := t.connectors;
      t.connectors := NIL;
      idle := NEW (REF ARRAY OF TCP.T, t.active);
      c := t.conns;
      WHILE c # NIL DO
        IF c.idle THEN
          idle[n] := c.tcp;
          INC (n);
        END;
        c := c.next;
      END;
      Thread.Broadcast (t.changed);
    END;
    WHILE connectors # NIL DO
      TCP.CloseConnector (connectors.c);
      connectors := connectors.next;
    END;
    FOR i := 0 TO n - 1 DO TCP.Close (idle[i]) END;
    AwaitEnd (t, waitFor);
    LOCK t.mu DO
      c := t.conns;
      idle := NEW (REF ARRAY OF TCP.T, t.active);
      n := 0;
      WHILE c # NIL DO
        Thread.Alert (c.th);
        idle[n] := c.tcp;
        INC (n);
        c := c.next;
      END;
    END;
    FOR i := 0 TO n - 1 DO TCP.Close (idle[i]) END;
    AwaitEnd (t, 2.0D0);
    LOCK t.mu DO
      t.finished := TRUE;
      Thread.Broadcast (t.changed);
    END;
  END Shutdown;

BEGIN
END HTTPServer.
