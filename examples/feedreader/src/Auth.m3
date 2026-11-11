(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Auth;

IMPORT Base64, Crypto, Digest, Kdf, Store, Text, Thread, Time, Utf8;

<*FATAL Crypto.Error*>  (* all that goes in are bytes, within the limits *)

CONST
  SaltBytes = 16;
  HashBytes = 32;
  TokenBytes = 32;
  MaxName = 64;

PROCEDURE Pbkdf2 (password, salt: TEXT; iterations: INTEGER): TEXT =
  BEGIN
    RETURN Kdf.Pbkdf2 (Digest.Algorithm.SHA256, Utf8.Encode (password), salt, iterations, HashBytes);
  END Pbkdf2;

PROCEDURE Hash (token: TEXT): TEXT =
  BEGIN
    RETURN Digest.Of (Digest.Algorithm.SHA256, Utf8.Encode (token));
  END Hash;

PROCEDURE Token (): TEXT =
  BEGIN
    RETURN Base64.Encode (Crypto.Random (TokenBytes), Base64.Alphabet.Url, pad := FALSE);
  END Token;

PROCEDURE Equal (a, b: TEXT): BOOLEAN =
  BEGIN
    RETURN a # NIL AND b # NIL AND Crypto.Equal (Utf8.Encode (a), Utf8.Encode (b));
  END Equal;

PROCEDURE GoodName (name: TEXT): BOOLEAN =
  VAR n := Text.Length (name);
  BEGIN
    IF n = 0 OR n > MaxName THEN RETURN FALSE END;
    FOR i := 0 TO n - 1 DO
      IF Text.GetWideChar (name, i) <= W' ' THEN RETURN FALSE END;
    END;
    RETURN TRUE;
  END GoodName;

PROCEDURE AddUser (s: Store.T; name, password: TEXT): INTEGER RAISES {Error, Store.Error, Thread.Alerted} =
  VAR salt := Crypto.Random (SaltBytes);
  BEGIN
    IF NOT GoodName (name) THEN
      RAISE Error ("a name has 1 to 64 characters, without spaces");
    END;
    IF Text.Length (password) < MinPassword THEN
      RAISE Error ("a password has at least 8 characters");
    END;
    RETURN s.addUser (name, salt, Pbkdf2 (password, salt, Iterations), Iterations);
  END AddUser;

PROCEDURE Login (s: Store.T; name, password: TEXT): INTEGER RAISES {Store.Error, Thread.Alerted} =
  VAR id, iterations: INTEGER;  salt, hash: TEXT;
  BEGIN
    IF NOT s.user (name, id, salt, hash, iterations) THEN
      EVAL Pbkdf2 (password, "no such user", Iterations);  (* as long as for a user *)
      RETURN -1;
    END;
    IF Crypto.Equal (Pbkdf2 (password, salt, iterations), hash) THEN RETURN id END;
    RETURN -1;
  END Login;

PROCEDURE NewSession (s: Store.T; user: INTEGER; VAR csrf: TEXT): TEXT RAISES {Store.Error, Thread.Alerted} =
  VAR token := Token ();  now := Time.Now ();
  BEGIN
    csrf := Token ();
    s.removeExpired (now);
    s.addSession (Hash (token), user, csrf, now + SessionTime);
    RETURN token;
  END NewSession;

PROCEDURE Session (s: Store.T; token: TEXT; VAR user: INTEGER; VAR csrf: TEXT): BOOLEAN
  RAISES {Store.Error, Thread.Alerted} =
  BEGIN
    RETURN token # NIL AND s.session (Hash (token), Time.Now (), user, csrf);
  END Session;

PROCEDURE EndSession (s: Store.T; token: TEXT) RAISES {Store.Error, Thread.Alerted} =
  BEGIN
    IF token # NIL THEN s.removeSession (Hash (token)) END;
  END EndSession;

PROCEDURE NewApiToken (s: Store.T; user: INTEGER): TEXT RAISES {Store.Error, Thread.Alerted} =
  VAR token := Token ();
  BEGIN
    s.setApiToken (user, Hash (token));
    RETURN token;
  END NewApiToken;

PROCEDURE ApiUser (s: Store.T; token: TEXT): INTEGER RAISES {Store.Error, Thread.Alerted} =
  BEGIN
    IF token = NIL OR Text.Empty (token) THEN RETURN -1 END;
    RETURN s.userByApiToken (Hash (token));
  END ApiUser;

BEGIN
END Auth.
