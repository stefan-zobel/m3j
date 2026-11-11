(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Fetch;

IMPORT Atom, AtomList, Deflate, Fmt, HTTP, HTTPClient, Rd, Text, Thread;

CONST Accept = "application/rss+xml, application/atom+xml, application/rdf+xml;q=0.9, "
                 & "application/xml;q=0.9, text/xml;q=0.9, */*;q=0.5";

(* The text of a failure: its last atom, which describes it. *)
PROCEDURE Describe (al: AtomList.T): TEXT =
  BEGIN
    IF al = NIL THEN RETURN "failed" END;
    WHILE al.tail # NIL DO al := al.tail END;
    RETURN Atom.ToText (al.head);
  END Describe;

PROCEDURE NewClient (): HTTPClient.T =
  <*FATAL HTTP.Error*>
  BEGIN
    RETURN HTTPClient.New (HTTPClient.Options {connectTimeout := 15.0D0, decompress := FALSE});
  END NewClient;

PROCEDURE Same (a, b: TEXT): BOOLEAN =
  BEGIN
    RETURN a # NIL AND Text.Equal (a, b);
  END Same;

PROCEDURE Get (c: HTTPClient.T; url: TEXT; etag, lastModified: TEXT := NIL; timeout := 30.0D0): Result
  RAISES {Error, Thread.Alerted} =
  VAR
    h := HTTP.NewHeaders ();
    resp: HTTPClient.Response;
    r: Result;
    rd: Rd.T;
  BEGIN
    h.set ("User-Agent", Agent);
    h.set ("Accept", Accept);
    h.set ("Accept-Encoding", "gzip");
    IF etag # NIL AND HTTP.ValidValue (etag) THEN h.set ("If-None-Match", etag) END;
    IF lastModified # NIL AND HTTP.ValidValue (lastModified) THEN h.set ("If-Modified-Since", lastModified) END;
    TRY
      resp := HTTPClient.Send (c, NEW (HTTPClient.Request, url := url, headers := h, timeout := timeout));
    EXCEPT
    | HTTP.Error (al) => RAISE Error (Describe (al));
    END;
    TRY
      r.status := resp.status;
      r.etag := resp.headers.get ("etag");
      r.lastModified := resp.headers.get ("last-modified");
      r.contentType := resp.headers.get ("content-type");
      IF r.status < 200 OR r.status > 299 THEN RETURN r END;
      rd := resp.body;
      IF Same (resp.headers.get ("content-encoding"), "gzip") THEN
        rd := Deflate.NewRd (rd, o := Deflate.ReadOptions {maxBytes := MaxBytes});
      END;
      TRY
        r.body := Rd.GetText (rd, MaxBytes + 1);
      EXCEPT
      | Rd.Failure (al) =>
          IF al # NIL AND al.head = Deflate.TooLarge THEN
            RAISE Error ("larger than " & Fmt.Int (MaxBytes DIV 1024 DIV 1024) & " MB");
          END;
          RAISE Error (Describe (al));
      END;
      IF Text.Length (r.body) > MaxBytes THEN
        RAISE Error ("larger than " & Fmt.Int (MaxBytes DIV 1024 DIV 1024) & " MB");
      END;
      RETURN r;
    FINALLY
      resp.close ();
    END;
  END Get;

BEGIN
END Fetch.
