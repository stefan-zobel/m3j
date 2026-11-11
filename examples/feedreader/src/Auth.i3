(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Who the user is: passwords, kept as a hash of PBKDF2 with a salt of its
   own; and random tokens for sessions, forms and the API, of which the
   database keeps only a hash, so that a copy of it lets no one in. *)

INTERFACE Auth;

IMPORT Store, Thread;

CONST
  Iterations = 600000;          (* of PBKDF2-HMAC-SHA256 *)
  MinPassword = 8;              (* characters *)
  SessionTime = 30.0D0 * 24.0D0 * 3600.0D0;

EXCEPTION Error (TEXT);

PROCEDURE AddUser (s: Store.T; name, password: TEXT): INTEGER RAISES {Error, Store.Error, Thread.Alerted};
(* A new user; "Error" if the name or the password will not do. *)

PROCEDURE Login (s: Store.T; name, password: TEXT): INTEGER RAISES {Store.Error, Thread.Alerted};
(* The id of the user, -1 if the name or the password is wrong. *)

PROCEDURE NewSession (s: Store.T; user: INTEGER; VAR csrf: TEXT): TEXT RAISES {Store.Error, Thread.Alerted};
(* The token of a new session of "user", for a cookie, which lasts
   "SessionTime"; "csrf" is the token that its forms must carry. *)

PROCEDURE Session (s: Store.T; token: TEXT; VAR user: INTEGER; VAR csrf: TEXT): BOOLEAN
  RAISES {Store.Error, Thread.Alerted};
(* Whether "token" is that of a session that has not expired. *)

PROCEDURE EndSession (s: Store.T; token: TEXT) RAISES {Store.Error, Thread.Alerted};

PROCEDURE NewApiToken (s: Store.T; user: INTEGER): TEXT RAISES {Store.Error, Thread.Alerted};
(* A new token for the API of "user"; the one before is no longer valid. *)

PROCEDURE ApiUser (s: Store.T; token: TEXT): INTEGER RAISES {Store.Error, Thread.Alerted};
(* The user of the token, -1 if there is none. *)

PROCEDURE Token (): TEXT;
(* A random token: 32 bytes in Base64 for URLs. *)

PROCEDURE Equal (a, b: TEXT): BOOLEAN;
(* Whether the tokens are equal, in a time that does not tell where they
   differ. *)

END Auth.
