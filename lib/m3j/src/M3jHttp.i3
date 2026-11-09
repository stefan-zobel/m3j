(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The client of HTTP of the Java platform, for the module HTTPClient of
   m3j: the procedures are methods of the class m3j.rt.Http (through
   m3j.rt.Externals). A result less than 0 is one of the numbers below,
   negated. *)

INTERFACE M3jHttp;

CONST
  Timeout = 1;
  Alerted = 2;
  Unreachable = 3;
  BadMessage = 4;
  BadUrl = 5;
  BadCertificate = 6;
  HandshakeFailed = 7;
  Closed = 8;

<*EXTERNAL "M3jHttp__NewClient"*>
PROCEDURE NewClient (connectTimeout: LONGREAL; follow: BOOLEAN; proxy: TEXT;
                     context: REFANY; VAR err: TEXT): REFANY;
(* A client: "connectTimeout" in seconds if positive, "proxy" a "host:port"
   or NIL, "context" that of the platform or NIL. NIL and err set if
   "proxy" is malformed. *)

<*EXTERNAL "M3jHttp__CloseClient"*>
PROCEDURE CloseClient (client: REFANY);
(* Ends the exchanges of the client and frees its resources. *)

<*EXTERNAL "M3jHttp__Start"*>
PROCEDURE Start (client: REFANY; method, url, headers, text: TEXT; upload: BOOLEAN;
                 length: INTEGER; timeout: LONGREAL; VAR code: INTEGER; VAR err: TEXT): REFANY;
(* Sends a request and returns the exchange, or NIL with code and err set.
   "headers" has a line "name: value" for each field. The body is "text"
   if that is not NIL; with "upload" it comes from "Write" and "Finish",
   with "length" bytes, or an unknown number if that is -1. "timeout", if
   positive, limits the seconds until the head of the response. *)

<*EXTERNAL "M3jHttp__AwaitResponse"*>
PROCEDURE AwaitResponse (x: REFANY; VAR status: INTEGER; VAR version, headers, err: TEXT): INTEGER;
(* Waits for the head of the response: 0, or an error with err set. *)

<*EXTERNAL "M3jHttp__Read"*>
PROCEDURE Read (x: REFANY; VAR b: ARRAY OF CHAR; timeout: LONGREAL; VAR err: TEXT): INTEGER;
(* The number of bytes of the body put into b, 0 at the end, or an error.
   A negative "timeout" means no limit. *)

<*EXTERNAL "M3jHttp__Write"*>
PROCEDURE Write (x: REFANY; READONLY b: ARRAY OF CHAR): INTEGER;
(* Adds to the body of the request: 0, or an error if the request has ended. *)

<*EXTERNAL "M3jHttp__FinishUpload"*>
PROCEDURE FinishUpload (x: REFANY; failed: BOOLEAN);
(* Ends the body of the request; with "failed", the request fails. *)

<*EXTERNAL "M3jHttp__Abort"*>
PROCEDURE Abort (x: REFANY);
(* Ends the exchange, closing its connection if the response is not read. *)

END M3jHttp.
