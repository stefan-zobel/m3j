(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* A server for HTTP/1.1 over TCP, or over TLS with a "TLS.Context". The
   program registers handlers and calls "serve":

|    TYPE Hello = HTTPServer.Handler OBJECT OVERRIDES request := Greet END;
|
|    PROCEDURE Greet (h: Hello; req: HTTPServer.Request; resp: HTTPServer.Response)
|      RAISES {Wr.Failure, Thread.Alerted} =
|      BEGIN
|        resp.headers.set ("Content-Type", "text/plain");
|        Wr.PutText (resp.body (), "Hello\n");
|      END Greet;
|
|    server := HTTPServer.New ();
|    server.register (NEW (Hello));
|    server.serve (TCP.NewConnector (IP.Endpoint {IP.NullAddress, 8080}));

   One thread serves one connection, so a handler may block. Handlers run
   in several threads at once.

   The server checks each request against RFC 9110 and RFC 9112 and answers
   a malformed one itself, with a status of 400 and upward, before any
   handler sees it. *)

INTERFACE HTTPServer;

IMPORT HTTP, IP, Rd, TCP, Thread, TLS, Wr;

TYPE
  Request = OBJECT
    method, target, version: TEXT;
    path, query: TEXT;
    headers: HTTP.Headers;
    body: Rd.T;
    peer: IP.Endpoint;
    secure: BOOLEAN;
  END;

(* A "Request" is filled in by the server. "method" is in the case that
   the client sent, as methods are case-sensitive; "version" is "HTTP/1.0"
   or "HTTP/1.1".

   "target" is the raw text of the request line. "path" is its path with
   the percent escapes decoded and "query" its query, not decoded, or "NIL"
   if there is none. The path of a request for the whole server is "*".
   The server does not normalize a path: a handler that maps paths to files
   must itself reject ".." and decoded slashes, NUL and so on.

   "body" reads the body of the request and is empty if there is none. It
   does the decoding of "chunked", and it raises "Rd.Failure" with
   "HTTP.TooLarge" beyond "maxBodyBytes", with "HTTP.Timeout" after
   "readTimeout" and with "HTTP.BadMessage" on a malformed or incomplete
   body. It is valid only until the handler returns.

   "peer" is the address of the client and "secure" tells whether the
   connection uses TLS. *)

  Response <: ResponsePublic;
  ResponsePublic = OBJECT
    status := 200;
    headers: HTTP.Headers;
  METHODS
    body (): Wr.T;
  END;

(* A handler sets "status" and "headers" and then writes to the writer that
   "body" returns; a response without a body need not call it. The head of
   the response goes out at the first flush of the writer, or when the
   writer's buffer overflows, or at the end of the handler, so it can no
   longer be changed after that. Closing the writer is not needed.

   If "headers" has a "Content-Length", the body must have exactly that
   length: more bytes raise "Wr.Failure" and fewer end the connection, so
   that the client notices. Without that field, a body that fits into the
   buffer of 8 KB and is not flushed gets a "Content-Length", any other
   uses "chunked", or under HTTP/1.0 the end of the connection. Writing to a
   response without a body, to a request of "HEAD" or with status 204 or 304,
   discards the bytes.

   The server sets "Date" unless the handler did. A status outside 200 to
   599, a "Transfer-Encoding" field or an invalid name or value of a field
   turns the response into a 500. *)

  Priority = {High, Normal, Low};

  Handler = OBJECT
    priority := Priority.Normal;
  METHODS
    accept (req: Request): BOOLEAN := AcceptAll;
    request (req: Request; resp: Response) RAISES {Rd.Failure, Wr.Failure, Thread.Alerted};
  END;

(* The server takes the first handler, in order of "priority" and then in
   the order of registration, whose "accept" returns "TRUE"; without one it
   answers 404.

   If "request" raises an exception or a checked runtime error before the
   head of the response has gone out, the server answers with a status
   that fits the error (408, 413, 400 or else 500) and closes the
   connection. After that it breaks off the connection, so that the client
   notices that the response is incomplete. *)

PROCEDURE AcceptAll (h: Handler; req: Request): BOOLEAN;
(* Returns "TRUE". *)

TYPE
  Log = OBJECT
  METHODS
    log (msg: TEXT);
  END;

  Options = RECORD
    maxConnections := 256;
    maxLineBytes := 8192;
    maxHeaderBytes := 65536;
    maxHeaders := 100;
    maxBodyBytes := 16_1000000;
    handshakeTimeout := 10.0D0;
    headerTimeout := 10.0D0;
    readTimeout := 30.0D0;
    writeTimeout := 30.0D0;
    idleTimeout := 60.0D0;
    log: Log := NIL;
  END;

(* "maxConnections" limits the connections that are served at one time; the
   operating system holds further ones until one ends.

   "maxLineBytes" limits a line of the head and "maxHeaderBytes" the whole
   head; "maxHeaders" limits the number of fields. "maxBodyBytes" limits
   the body that the server reads.

   The times are in seconds and must be positive. "handshakeTimeout" limits
   the handshake of TLS and "headerTimeout" the arrival of the head of a
   request. "readTimeout" limits each read of a body, and "writeTimeout"
   each write to the connection. After a response "idleTimeout" limits the
   wait for the next request.

   "log", if not "NIL", is told of errors that the program should know
   about, such as a checked runtime error in a handler; it may be called
   from several threads at once. *)

CONST Defaults = Options {};

TYPE
  T <: Public;
  Public = OBJECT
  METHODS
    register (h: Handler);
    serve (c: TCP.Connector; ctx: TLS.Context := NIL) RAISES {IP.Error, Thread.Alerted};
    shutdown (waitFor := 10.0D0);
  END;

(* "s.register(h)" adds a handler, which may also happen while the server
   is running.

   "s.serve(c, ctx)" accepts connections on "c" until "shutdown" and
   returns when that is done, so that a program that ends after "serve"
   does not cut off responses. With a "ctx" that has a key, the connections use TLS. A thread
   may call "serve" for each of several connectors. An error of the
   connector other than its being closed is passed to "log" and retried.

   "s.shutdown(waitFor)" stops all "serve" calls of "s", closes their
   connectors and the connections that wait for a request, lets the others
   finish their response for up to "waitFor" seconds, and then alerts their
   threads and closes their connections. *)

PROCEDURE New (READONLY o := Defaults): T;
(* A new server with the options "o". *)

END HTTPServer.
