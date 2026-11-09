(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* A client for HTTP, on the client of the Java platform: it speaks HTTP/2
   where a server offers it by ALPN and HTTP/1.1 otherwise, keeps a pool of
   connections, follows redirects and uses the TLS of the platform.

|    c := HTTPClient.New ();
|    page := HTTPClient.GetText (c, "https://www.example.org/");

   Several threads may use one client at the same time. *)

INTERFACE HTTPClient;

IMPORT HTTP, Rd, Thread, TLS;

TYPE
  Options = RECORD
    connectTimeout := 30.0D0;
    followRedirects := TRUE;
    proxy: TEXT := NIL;
    ctx: TLS.Context := NIL;
  END;

(* "connectTimeout" limits the seconds to connect, if it is positive.

   "followRedirects" follows redirects, except from "https" to "http".

   "proxy", in the form "host:port", is used instead of what the platform
   finds; "NIL" leaves that to the platform.

   "ctx", if not "NIL", decides which certificates are trusted and which
   certificate the client presents; the default is that of the platform. *)

CONST Defaults = Options {};

TYPE
  Request = OBJECT
    method := "GET";
    url: TEXT;
    headers: HTTP.Headers := NIL;
    body: Rd.T := NIL;
    length := -1;
    timeout := 60.0D0;
  END;

(* "headers" may not contain "Host", "Connection", "Content-Length",
   "Expect", "Upgrade" or "Transfer-Encoding", which the client sets
   itself, nor characters outside of ASCII in a value, which the platform
   cannot send without changing them.

   "body" is the body of the request, read to its end by a thread of the
   client; "NIL" means no body. "length" is the number of bytes in it, -1
   if unknown; then the request uses the "chunked" coding, or DATA frames
   under HTTP/2. A redirect that needs the body a second time fails.

   "timeout" limits the seconds until the head of the response and then
   each read of its body, if it is positive. *)

  Response <: ResponsePublic;
  ResponsePublic = OBJECT
    status: INTEGER;
    version: TEXT;
    headers: HTTP.Headers;
    body: Rd.T;
  METHODS
    close ();
  END;

(* "version" is "HTTP/1.1" or "HTTP/2". The names in "headers" are in lower
   case and ordered by name, and repeated fields keep their order.

   "body" reads the body: "Rd.Failure" with "HTTP.Timeout" after "timeout"
   and with "HTTP.BadMessage" if the response is incomplete. A body that is
   read to its end returns the connection to the pool. "close" gives up the
   rest of the response, and its connection if that is needed. *)

  T <: ROOT;

PROCEDURE New (READONLY o := Defaults): T RAISES {HTTP.Error};
(* A new client. Raises "HTTP.Error" with "HTTP.BadUrl" if "proxy" is
   malformed. *)

(* The procedures below raise "HTTP.Error" with one of these atoms:

   "HTTP.BadUrl" for a malformed URL, or a scheme other than "http" and
   "https".

   "HTTP.BadMessage" for a request that may not be sent, or a response that
   is not HTTP.

   "HTTP.Unreachable" if no connection could be made or it was lost.

   "HTTP.Timeout" after "connectTimeout" or "timeout".

   "TLS.BadCertificate" if the certificate of the server is not accepted,
   and "TLS.HandshakeFailed" for other errors of TLS. *)

PROCEDURE Send (c: T; r: Request): Response RAISES {HTTP.Error, Thread.Alerted};
(* Sends "r" and returns the response when its head has arrived. A status
   of an error such as 404 is an ordinary response. *)

PROCEDURE Get (c: T; url: TEXT): Response RAISES {HTTP.Error, Thread.Alerted};
(* "Send" with the method "GET". *)

PROCEDURE GetText (c: T; url: TEXT): TEXT RAISES {HTTP.Error, Thread.Alerted};
(* The body of the response to a "GET". Raises "HTTP.Error" with
   "HTTP.BadMessage" if the status is not in the range from 200 to 299. *)

PROCEDURE Post (c: T; url, contentType, body: TEXT): Response
  RAISES {HTTP.Error, Thread.Alerted};
(* "Send" with the method "POST", "body" as the body. Characters above 255
   in "body" are sent in UTF-8. *)

PROCEDURE Close (c: T);
(* Ends the exchanges under way and frees the resources of "c". *)

END HTTPClient.
