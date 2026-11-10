(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Bodies of the type "multipart" of RFC 2046, in the form that HTML forms
   send as "multipart/form-data" of RFC 7578: a reader that takes the parts
   one after the other as a stream, and a writer. A handler of "HTTPServer"
   reads a form like this:

|    r := Multipart.NewReader (req.body,
|                              Multipart.Boundary (req.headers.get ("Content-Type")));
|    WHILE r.next (p) DO
|      IF p.name # NIL AND Text.Equal (p.name, "upload") THEN Store (p.body) END;
|    END;

   and a client builds one like this:

|    w := Multipart.NewWriter (wr);
|    Wr.PutText (w.part ("title"), "Holiday");
|    Wr.PutText (w.part ("upload", "beach.jpg", "image/jpeg"), photo);
|    w.finish ();

   with "w.contentType ()" as the "Content-Type" of the request.

   Names, file names and bodies are strings of bytes. Browsers send names
   and file names in UTF-8, and they send a double quote in them as "%22",
   a CR as "%0D" and an LF as "%0A"; the reader does not undo that.

   A file name comes from the client: it may contain "..", slashes,
   backslashes, a drive or a device name. Never use it as the name of a
   file without checking it. *)

INTERFACE Multipart;

IMPORT HTTP, Rd, Thread, Wr;

TYPE
  Limits = RECORD
    maxParts := 1000;
    maxHeaderBytes := 16384;
    maxHeaders := 32;
    maxPreamble := 65536;
    maxPartBytes := 0;
    maxTotalBytes := 0;
  END;

(* "maxParts" limits the number of parts, "maxHeaderBytes" the bytes of the
   head of a part, with the line ends and the empty line that ends it, and
   "maxHeaders" the number of fields in it. "maxPreamble" limits the bytes
   before the first boundary. "maxPartBytes" limits the body of each part
   and "maxTotalBytes" all the bytes from the start up to the end of the
   last boundary; 0 means no limit for these two. The limits are exact:
   a body with exactly the bytes that a limit allows is accepted.

   The reader holds a buffer of 16 KB, or "maxHeaderBytes" if that is
   larger. *)

CONST Defaults = Limits {};

TYPE
  Part = RECORD
    name, filename, contentType: TEXT;
    headers: HTTP.Headers;
    body: Rd.T;
  END;

(* "name" and "filename" are the parameters of the "Content-Disposition"
   field of the part, "NIL" if it has no such field or the field lacks the
   parameter; "contentType" is its "Content-Type" field or "NIL", which
   means "text/plain". A parameter "filename*" is ignored, as RFC 7578
   asks. "headers" has all the fields of the head of the part.

   "body" reads the body of the part. It is valid until the next call of
   "next"; a read after that raises "Rd.Failure", unless an earlier read
   had found the end of the body. A body need not be read: "next" skips
   what is left of it. *)

  Reader <: ReaderPublic;
  ReaderPublic = OBJECT
  METHODS
    next (VAR p: Part): BOOLEAN RAISES {Rd.Failure, Thread.Alerted};
  END;

(* "r.next(p)" sets "p" to the next part and returns "TRUE", or returns
   "FALSE" after the last part. The reader stops at the end of the last
   boundary and does not read the epilogue that may follow it.

   "next" and the reads of a body raise "Rd.Failure" with "HTTP.BadMessage"
   and an atom that describes the error if the body is malformed, and with
   "HTTP.TooLarge" if it breaks one of the limits; a handler of
   "HTTPServer" that lets the exception pass answers 400 or 413. A body that
   ends before the last boundary is malformed, never an orderly end. A
   failure of the underlying reader is passed on as it is. After a failure,
   each further call raises the same one.

   The reader is strict where readers are known to differ: each line ends
   with CR LF, a boundary line has only spaces and tabs after the boundary,
   a field of a head may not be continued on the next line, a head may not
   have two fields "Content-Disposition" or "Content-Type", and a
   "Content-Disposition" may not have two parameters "name" or "filename".
   A body that starts with the boundary right after the empty line, without
   a line end of its own, is malformed too.

   Within a quoted parameter, a backslash quotes a double quote or a
   backslash and is kept before any other character, since browsers send
   the backslashes of Windows paths as they are.

   A "Reader" is not monitored: a client must serialize the calls of
   "next". *)

  Writer <: WriterPublic;
  WriterPublic = OBJECT
  METHODS
    contentType (): TEXT;
    part (name: TEXT; filename, contentType: TEXT := NIL): Wr.T
      RAISES {HTTP.Error, Wr.Failure, Thread.Alerted};
    finish () RAISES {Wr.Failure, Thread.Alerted};
  END;

(* "w.contentType()" is the value of the "Content-Type" field for the body,
   "multipart/form-data" with the boundary.

   "w.part(name, filename, contentType)" ends the previous part, writes the
   head of a new one and returns the underlying writer, on which the program
   then writes the body of the part. A part with a "filename" and without a
   "contentType" gets "application/octet-stream". "part" raises
   "HTTP.Error" with "HTTP.BadMessage", and writes nothing, if "name" or
   "filename" contains a control character, a double quote or a character
   above 255, or "contentType" is not a valid value of a field.

   "w.finish()" ends the body with the last boundary. It neither flushes
   nor closes the underlying writer. It is a checked runtime error to call
   "part" or "finish" after "finish". *)

PROCEDURE Boundary (contentType: TEXT): TEXT;
(* The parameter "boundary" of the value "contentType" of a "Content-Type"
   field, if the type is "multipart" and the boundary is valid; else
   "NIL", also for "NIL". *)

PROCEDURE ValidBoundary (b: TEXT): BOOLEAN;
(* Is "b" a boundary as RFC 2046 defines it: 1 to 70 characters from
   letters, digits, the space and "'()+_,-./:=?", the last of them not a
   space? *)

PROCEDURE NewReader (rd: Rd.T; boundary: TEXT; READONLY limits := Defaults): Reader;
(* A reader of the multipart body in "rd", whose parts are separated by
   "boundary". If "boundary" is "NIL" or not valid, the first call of
   "next" raises "Rd.Failure" with "HTTP.BadMessage". *)

PROCEDURE NewWriter (wr: Wr.T; boundary: TEXT := NIL): Writer;
(* A writer of a multipart body to "wr". "NIL" means a boundary of 128
   random bits, which no content will contain by chance. A boundary of the
   caller must be valid and must not occur in the content; it is a checked
   runtime error if it is not valid. *)

END Multipart.
