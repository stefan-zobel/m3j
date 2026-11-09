(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE HTTPClient;

IMPORT Atom, AtomList, HTTP, M3jHttp, Rd, RdClass, Text, TextExtras, TextWr, Thread, TLS, Wr;

CONST
  BodyBufferSize = 16384;

REVEAL
  T = ROOT BRANDED "HTTPClient.T" OBJECT
    h: REFANY;
  END;

  Response = ResponsePublic BRANDED "HTTPClient.Response" OBJECT
    x: REFANY;
  OVERRIDES
    close := ResponseClose;
  END;

TYPE
  BodyRd = Rd.T OBJECT
    x: REFANY;
    timeout: LONGREAL;
  OVERRIDES
    seek := BodySeek;
    length := BodyLength;
    close := BodyClose;
  END;

  Uploader = Thread.Closure OBJECT
    x: REFANY;
    rd: Rd.T;
    failed := FALSE;
  OVERRIDES
    apply := Upload;
  END;

PROCEDURE Fail (atom: Atom.T; text: TEXT): AtomList.T =
  BEGIN
    RETURN AtomList.List2 (atom, Atom.FromText (text));
  END Fail;

PROCEDURE AtomOf (code: INTEGER): Atom.T =
  BEGIN
    CASE code OF
    | M3jHttp.Timeout => RETURN HTTP.Timeout;
    | M3jHttp.BadMessage => RETURN HTTP.BadMessage;
    | M3jHttp.BadUrl => RETURN HTTP.BadUrl;
    | M3jHttp.BadCertificate => RETURN TLS.BadCertificate;
    | M3jHttp.HandshakeFailed => RETURN TLS.HandshakeFailed;
    ELSE RETURN HTTP.Unreachable;
    END;
  END AtomOf;

PROCEDURE New (READONLY o := Defaults): T RAISES {HTTP.Error} =
  VAR err: TEXT := NIL;  context: REFANY := NIL;  h: REFANY;
  BEGIN
    IF o.ctx # NIL THEN context := TLS.Platform (o.ctx) END;
    h := M3jHttp.NewClient (o.connectTimeout, o.followRedirects, o.proxy, context, err);
    IF h = NIL THEN RAISE HTTP.Error (Fail (HTTP.BadUrl, err)) END;
    RETURN NEW (T, h := h);
  END New;

PROCEDURE Close (c: T) =
  BEGIN
    M3jHttp.CloseClient (c.h);
  END Close;

PROCEDURE Restricted (name: TEXT): BOOLEAN =
  BEGIN
    RETURN TextExtras.CIEqual (name, "Host")
             OR TextExtras.CIEqual (name, "Connection")
             OR TextExtras.CIEqual (name, "Content-Length")
             OR TextExtras.CIEqual (name, "Expect")
             OR TextExtras.CIEqual (name, "Upgrade")
             OR TextExtras.CIEqual (name, "Transfer-Encoding");
  END Restricted;

PROCEDURE Ascii (t: TEXT): BOOLEAN =
  BEGIN
    IF Text.HasWideChars (t) THEN RETURN FALSE END;
    FOR i := 0 TO Text.Length (t) - 1 DO
      IF Text.GetChar (t, i) > '\176' THEN RETURN FALSE END;
    END;
    RETURN TRUE;
  END Ascii;

(* The fields as the platform takes them. *)
PROCEDURE FieldLines (h: HTTP.Headers): TEXT RAISES {HTTP.Error} =
  <*FATAL Wr.Failure, Thread.Alerted*>
  VAR wr := TextWr.New ();  it: HTTP.Iterator;  name, value: TEXT;
  BEGIN
    IF h = NIL THEN RETURN "" END;
    it := h.iterate ();
    WHILE it.next (name, value) DO
      IF Restricted (name) THEN
        RAISE HTTP.Error (Fail (HTTP.BadMessage, "a field that the client sets itself"));
      END;
      IF NOT HTTP.ValidName (name) OR NOT HTTP.ValidValue (value) OR NOT Ascii (value) THEN
        RAISE HTTP.Error (Fail (HTTP.BadMessage, "a field that cannot be sent"));
      END;
      Wr.PutText (wr, name & ": " & value & "\n");
    END;
    RETURN TextWr.ToText (wr);
  END FieldLines;

PROCEDURE ParseFields (t: TEXT): HTTP.Headers =
  VAR h := HTTP.NewHeaders ();  n := Text.Length (t);  start := 0;  end, colon: INTEGER;
  BEGIN
    WHILE start < n DO
      end := Text.FindChar (t, '\n', start);
      IF end < 0 THEN end := n END;
      colon := Text.FindChar (t, ':', start);
      IF colon >= 0 AND colon < end THEN
        h.add (Text.Sub (t, start, colon - start), Text.Sub (t, colon + 2, end - colon - 2));
      END;
      start := end + 1;
    END;
    RETURN h;
  END ParseFields;

PROCEDURE Upload (u: Uploader): REFANY =
  VAR buf := NEW (REF ARRAY OF CHAR, BodyBufferSize);  n: CARDINAL;
  BEGIN
    TRY
      LOOP
        n := Rd.GetSub (u.rd, buf^);
        IF n = 0 THEN
          M3jHttp.FinishUpload (u.x, FALSE);
          EXIT;
        END;
        IF M3jHttp.Write (u.x, SUBARRAY (buf^, 0, n)) < 0 THEN EXIT END;
      END;
    EXCEPT
    | Rd.Failure, Thread.Alerted =>
        u.failed := TRUE;
        M3jHttp.FinishUpload (u.x, TRUE);
    END;
    RETURN NIL;
  END Upload;

PROCEDURE Exchange (c: T; r: Request; text: TEXT): Response RAISES {HTTP.Error, Thread.Alerted} =
  VAR
    code: INTEGER;  err, version, fields: TEXT := NIL;  status := 0;  x: REFANY;
    upload := r.body # NIL AND text = NIL;  u: Uploader := NIL;  th: Thread.T := NIL;
    lines: TEXT;
  BEGIN
    IF r.url = NIL THEN RAISE HTTP.Error (Fail (HTTP.BadUrl, "no URL")) END;
    IF NOT HTTP.ValidName (r.method) THEN
      RAISE HTTP.Error (Fail (HTTP.BadMessage, "a method that cannot be sent"));
    END;
    lines := FieldLines (r.headers);
    x := M3jHttp.Start (c.h, r.method, r.url, lines, text, upload, r.length, r.timeout, code, err);
    IF x = NIL THEN RAISE HTTP.Error (Fail (AtomOf (code), err)) END;
    IF upload THEN
      u := NEW (Uploader, x := x, rd := r.body);
      th := Thread.Fork (u);
    END;
    code := M3jHttp.AwaitResponse (x, status, version, fields, err);
    IF code = -M3jHttp.Alerted THEN
      M3jHttp.Abort (x);
      IF th # NIL THEN Thread.Alert (th) END;
      IF Thread.TestAlert () THEN RAISE Thread.Alerted END;
      RAISE HTTP.Error (Fail (HTTP.Unreachable, "the request was given up"));
    END;
    IF code < 0 THEN
      M3jHttp.Abort (x);
      IF u # NIL AND u.failed THEN
        RAISE HTTP.Error (Fail (HTTP.BadMessage, "reading the body of the request failed"));
      END;
      RAISE HTTP.Error (Fail (AtomOf (-code), err));
    END;
    RETURN NEW (Response, status := status, version := version, headers := ParseFields (fields),
                x := x, body := NEW (BodyRd, x := x, timeout := NormalTimeout (r.timeout),
                                     buff := NEW (REF ARRAY OF CHAR, BodyBufferSize), st := 0,
                                     lo := 0, hi := 0, cur := 0, intermittent := TRUE,
                                     seekable := FALSE, closed := FALSE));
  END Exchange;

(* A timeout that is not positive means no limit, for "Read" a negative one. *)
PROCEDURE NormalTimeout (t: LONGREAL): LONGREAL =
  BEGIN
    IF t > 0.0D0 THEN RETURN t ELSE RETURN -1.0D0 END;
  END NormalTimeout;

PROCEDURE Send (c: T; r: Request): Response RAISES {HTTP.Error, Thread.Alerted} =
  BEGIN
    RETURN Exchange (c, r, NIL);
  END Send;

PROCEDURE Get (c: T; url: TEXT): Response RAISES {HTTP.Error, Thread.Alerted} =
  BEGIN
    RETURN Send (c, NEW (Request, url := url));
  END Get;

PROCEDURE GetText (c: T; url: TEXT): TEXT RAISES {HTTP.Error, Thread.Alerted} =
  <*FATAL Wr.Failure*>
  VAR resp := Get (c, url);  wr := TextWr.New ();  buf: ARRAY [0 .. 8191] OF CHAR;  n: CARDINAL;
  BEGIN
    TRY
      IF resp.status < 200 OR resp.status > 299 THEN
        RAISE HTTP.Error (Fail (HTTP.BadMessage, "a status outside 200 to 299"));
      END;
      TRY
        LOOP
          n := Rd.GetSub (resp.body, buf);
          IF n = 0 THEN EXIT END;
          Wr.PutString (wr, SUBARRAY (buf, 0, n));
        END;
      EXCEPT
      | Rd.Failure (e) => RAISE HTTP.Error (e);
      END;
      RETURN TextWr.ToText (wr);
    FINALLY
      resp.close ();
    END;
  END GetText;

PROCEDURE Post (c: T; url, contentType, body: TEXT): Response
  RAISES {HTTP.Error, Thread.Alerted} =
  VAR h := HTTP.NewHeaders ();
  BEGIN
    h.set ("Content-Type", contentType);
    RETURN Exchange (c, NEW (Request, method := "POST", url := url, headers := h), body);
  END Post;

PROCEDURE ResponseClose (r: Response) =
  BEGIN
    M3jHttp.Abort (r.x);
  END ResponseClose;

PROCEDURE BodySeek (rd: BodyRd; <*UNUSED*> pos: CARDINAL; dontBlock: BOOLEAN): RdClass.SeekResult
  RAISES {Rd.Failure, Thread.Alerted} =
  VAR n: INTEGER;  err: TEXT := NIL;  wait := rd.timeout;
  BEGIN
    IF dontBlock THEN wait := 0.0D0 END;
    n := M3jHttp.Read (rd.x, rd.buff^, wait, err);
    IF n > 0 THEN
      rd.st := 0;
      rd.lo := rd.cur;
      rd.hi := rd.cur + n;
      RETURN RdClass.SeekResult.Ready;
    ELSIF n = 0 THEN
      RETURN RdClass.SeekResult.Eof;
    ELSIF n = -M3jHttp.Alerted THEN
      IF Thread.TestAlert () THEN RAISE Thread.Alerted END;
      RETURN RdClass.SeekResult.WouldBlock;
    ELSIF n = -M3jHttp.Timeout AND dontBlock THEN
      RETURN RdClass.SeekResult.WouldBlock;
    ELSE
      RAISE Rd.Failure (Fail (AtomOf (-n), err));
    END;
  END BodySeek;

PROCEDURE BodyLength (<*UNUSED*> rd: BodyRd): INTEGER =
  BEGIN
    RETURN -1;
  END BodyLength;

PROCEDURE BodyClose (rd: BodyRd) =
  BEGIN
    M3jHttp.Abort (rd.x);
  END BodyClose;

BEGIN
END HTTPClient.
