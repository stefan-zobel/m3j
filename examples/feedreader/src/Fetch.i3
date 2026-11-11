(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Fetching a feed over HTTP: with the validators of the last fetch, so
   that a feed that has not changed costs a "304 Not Modified", with gzip,
   and with limits, since a feed comes from a server that is not trusted. *)

INTERFACE Fetch;

IMPORT HTTPClient, Thread;

CONST
  MaxBytes = 32 * 1024 * 1024;  (* of a feed, after gzip is undone *)
  Agent = "feedreader/1.0 (Modula-3)";

TYPE
  Result = RECORD
    status: INTEGER;
    body: TEXT := NIL;          (* the bytes, if the status is 2xx *)
    etag, lastModified: TEXT := NIL;
    contentType: TEXT := NIL;
  END;

EXCEPTION Error (TEXT);         (* no answer, or a body that breaks a limit *)

PROCEDURE NewClient (): HTTPClient.T;
(* A client for "Get", which may be shared by threads. *)

PROCEDURE Get (c: HTTPClient.T; url: TEXT; etag, lastModified: TEXT := NIL;
               timeout := 30.0D0): Result RAISES {Error, Thread.Alerted};
(* Fetches "url", following redirects. "etag" and "lastModified" are those
   of the last fetch, NIL if there was none. A status other than 2xx is a
   result without a body, 304 among them. *)

END Fetch.
