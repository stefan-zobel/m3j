(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The program of tools/crypto_test.py.

     batch IN OUT    carries out the lines of IN, one result line each in OUT

   Bytes in the lines are "x" and two digits each, text with characters above
   255 "w" and four digits for each, NIL "-". A "Crypto.Error" is "err" and
   the name of its first atom. Keys are kept under names, the public and the
   private ones apart. *)

MODULE Main;

IMPORT Aead, Atom, Crypto, Digest, FileRd, FileWr, Fmt, Kdf, Params, Rd, RdClass, Scan, Signature,
       Text, TextRd, TextRefTbl, TextWr, Thread, Time, UUID, Wr, X25519;

<*FATAL ANY*>

CONST Digits = "0123456789abcdef";

TYPE Tokens = REF ARRAY OF TEXT;

VAR
  publics := NEW (TextRefTbl.Default).init ();
  privates := NEW (TextRefTbl.Default).init ();

PROCEDURE Nibble (c: CHAR): INTEGER =
  BEGIN
    IF c >= 'a' THEN RETURN ORD (c) - ORD ('a') + 10 ELSE RETURN ORD (c) - ORD ('0') END;
  END Nibble;

(* The text of an argument: "x..." bytes, "w..." wide characters, "-" NIL. *)
PROCEDURE Arg (t: TEXT): TEXT =
  VAR n, v: INTEGER;
  BEGIN
    CASE Text.GetChar (t, 0) OF
    | '-' => RETURN NIL;
    | 'x' =>
        n := (Text.Length (t) - 1) DIV 2;
        WITH r = NEW (REF ARRAY OF CHAR, n) DO
          FOR i := 0 TO n - 1 DO
            r[i] := VAL (Nibble (Text.GetChar (t, 2 * i + 1)) * 16 + Nibble (Text.GetChar (t, 2 * i + 2)), CHAR);
          END;
          RETURN Text.FromChars (r^);
        END;
    | 'w' =>
        n := (Text.Length (t) - 1) DIV 4;
        WITH r = NEW (REF ARRAY OF WIDECHAR, n) DO
          FOR i := 0 TO n - 1 DO
            v := 0;
            FOR k := 1 TO 4 DO v := v * 16 + Nibble (Text.GetChar (t, 4 * i + k)) END;
            r[i] := VAL (v, WIDECHAR);
          END;
          RETURN Text.FromWideChars (r^);
        END;
    ELSE <*ASSERT FALSE*>
    END;
  END Arg;

PROCEDURE Show (t: TEXT): TEXT =
  VAR n: INTEGER;  r: REF ARRAY OF CHAR;  c: INTEGER;
  BEGIN
    IF t = NIL THEN RETURN "-" END;
    <*ASSERT NOT Text.HasWideChars (t)*>
    n := Text.Length (t);
    r := NEW (REF ARRAY OF CHAR, 2 * n + 1);
    r[0] := 'x';
    FOR i := 0 TO n - 1 DO
      c := ORD (Text.GetChar (t, i));
      r[2 * i + 1] := Text.GetChar (Digits, c DIV 16);
      r[2 * i + 2] := Text.GetChar (Digits, c MOD 16);
    END;
    RETURN Text.FromChars (r^);
  END Show;

PROCEDURE ShowArray (READONLY a: ARRAY OF CHAR): TEXT =
  BEGIN
    RETURN Show (Text.FromChars (a));
  END ShowArray;

PROCEDURE Bool (b: BOOLEAN): TEXT =
  BEGIN
    IF b THEN RETURN "yes" ELSE RETURN "no" END;
  END Bool;

PROCEDURE Split (line: TEXT): Tokens =
  VAR n := Text.Length (line);  count := 1;  r: Tokens;  start := 0;  stop: INTEGER;
  BEGIN
    FOR i := 0 TO n - 1 DO
      IF Text.GetChar (line, i) = ' ' THEN INC (count) END;
    END;
    r := NEW (Tokens, count);
    FOR i := 0 TO count - 1 DO
      stop := Text.FindChar (line, ' ', start);
      IF stop < 0 THEN stop := n END;
      r[i] := Text.Sub (line, start, stop - start);
      start := stop + 1;
    END;
    RETURN r;
  END Split;

PROCEDURE Int (t: TEXT): INTEGER =
  BEGIN
    RETURN Scan.Int (t);
  END Int;

PROCEDURE DigestAlg (t: TEXT): Digest.Algorithm =
  BEGIN
    RETURN VAL (Int (t), Digest.Algorithm);
  END DigestAlg;

PROCEDURE AeadAlg (t: TEXT): Aead.Algorithm =
  BEGIN
    RETURN VAL (Int (t), Aead.Algorithm);
  END AeadAlg;

PROCEDURE SigAlg (t: TEXT): Signature.Algorithm =
  BEGIN
    RETURN VAL (Int (t), Signature.Algorithm);
  END SigAlg;

PROCEDURE Public (name: TEXT): Signature.PublicKey =
  VAR r: REFANY;
  BEGIN
    <*ASSERT publics.get (name, r)*>
    RETURN r;
  END Public;

PROCEDURE Private (name: TEXT): Signature.PrivateKey =
  VAR r: REFANY;
  BEGIN
    <*ASSERT privates.get (name, r)*>
    RETURN r;
  END Private;

(* Readers *)

TYPE
  (* The bytes of another reader in pieces of "size", or of the lengths 1, 2,
     ... 17, 1, ... for a "size" of 0. *)
  ChunkRd = Rd.T OBJECT
    src: Rd.T;
    size: CARDINAL;
    next := 1;
  OVERRIDES
    seek := ChunkSeek;
    length := NoLength;
  END;

  (* The bytes of "data"; then a failure if "fail", or a wait for an alert. *)
  ScriptRd = Rd.T OBJECT
    data: TEXT;
    pos := 0;
    fail := FALSE;
  OVERRIDES
    seek := ScriptSeek;
    length := NoLength;
  END;

PROCEDURE ChunkSeek (r: ChunkRd; <*UNUSED*> pos: CARDINAL; <*UNUSED*> dontBlock: BOOLEAN): RdClass.SeekResult
  RAISES {Rd.Failure, Thread.Alerted} =
  VAR size := r.size;  n: CARDINAL;
  BEGIN
    IF size = 0 THEN
      size := r.next;
      r.next := r.next MOD 17 + 1;
    END;
    n := Rd.GetSub (r.src, SUBARRAY (r.buff^, 0, MIN (size, NUMBER (r.buff^))));
    IF n = 0 THEN RETURN RdClass.SeekResult.Eof END;
    r.st := 0;
    r.lo := r.cur;
    r.hi := r.cur + n;
    RETURN RdClass.SeekResult.Ready;
  END ChunkSeek;

PROCEDURE NoLength (<*UNUSED*> r: Rd.T): INTEGER =
  BEGIN
    RETURN -1;
  END NoLength;

PROCEDURE Chunked (src: Rd.T; size: INTEGER): Rd.T =
  BEGIN
    IF size < 0 THEN RETURN src END;
    RETURN NEW (ChunkRd, src := src, size := size, buff := NEW (REF ARRAY OF CHAR, 70000), st := 0,
                lo := 0, hi := 0, cur := 0, intermittent := TRUE, seekable := FALSE, closed := FALSE);
  END Chunked;

PROCEDURE ScriptSeek (r: ScriptRd; <*UNUSED*> p: CARDINAL; <*UNUSED*> dontBlock: BOOLEAN): RdClass.SeekResult
  RAISES {Rd.Failure, Thread.Alerted} =
  VAR n := MIN (NUMBER (r.buff^), Text.Length (r.data) - r.pos);  mu: MUTEX;  c: Thread.Condition;
  BEGIN
    IF n > 0 THEN
      Text.SetChars (r.buff^, Text.Sub (r.data, r.pos, n));
      INC (r.pos, n);
      r.st := 0;
      r.lo := r.cur;
      r.hi := r.cur + n;
      RETURN RdClass.SeekResult.Ready;
    END;
    IF r.fail THEN RAISE Rd.Failure (NIL) END;
    mu := NEW (MUTEX);
    c := NEW (Thread.Condition);
    LOCK mu DO Thread.AlertWait (mu, c) END;
    RETURN RdClass.SeekResult.Eof;
  END ScriptSeek;

TYPE
  Alerter = Thread.Closure OBJECT
    target: Thread.T;
  OVERRIDES
    apply := AlertLater;
  END;

PROCEDURE AlertLater (a: Alerter): REFANY =
  BEGIN
    Thread.Pause (0.3D0);
    Thread.Alert (a.target);
    RETURN NIL;
  END AlertLater;

PROCEDURE FromFailing (a: Digest.Algorithm; fail: BOOLEAN): TEXT =
  VAR src := NEW (ScriptRd, data := Text.FromChars (ARRAY [0 .. 999] OF CHAR {'a', ..}), fail := fail,
                  buff := NEW (REF ARRAY OF CHAR, 300), st := 0, lo := 0, hi := 0, cur := 0,
                  intermittent := TRUE, seekable := FALSE, closed := FALSE);
  BEGIN
    IF NOT fail THEN EVAL Thread.Fork (NEW (Alerter, target := Thread.Self ())) END;
    TRY
      RETURN Show (Digest.FromRd (a, src));
    EXCEPT
    | Rd.Failure => RETURN "failure";
    | Thread.Alerted => RETURN "alerted";
    END;
  END FromFailing;

(* Threads on one digest *)

TYPE
  Feeder = Thread.Closure OBJECT
    d: Digest.T;
    count, len: CARDINAL;
  OVERRIDES
    apply := Feed;
  END;

PROCEDURE Feed (f: Feeder): REFANY =
  VAR b := NEW (REF ARRAY OF CHAR, f.len);
  BEGIN
    FOR i := 0 TO f.len - 1 DO b[i] := 'a' END;
    FOR i := 1 TO f.count DO f.d.update (b^) END;
    RETURN NIL;
  END Feed;

PROCEDURE Threads (a: Digest.Algorithm; threads, count, len: CARDINAL): TEXT =
  VAR d := Digest.New (a);  ts := NEW (REF ARRAY OF Thread.T, threads);
  BEGIN
    FOR i := 0 TO threads - 1 DO
      ts[i] := Thread.Fork (NEW (Feeder, d := d, count := count, len := len));
    END;
    FOR i := 0 TO threads - 1 DO EVAL Thread.Join (ts[i]) END;
    RETURN Show (d.finish ());
  END Threads;

(* Chunks of an argument list into a digest, twice, to see that "finish"
   starts again. *)
PROCEDURE Chunks (d: Digest.T; READONLY a: ARRAY OF TEXT): TEXT =
  VAR r := "";
  BEGIN
    FOR round := 1 TO 2 DO
      FOR i := 0 TO LAST (a) DO
        WITH t = Arg (a[i]), b = NEW (REF ARRAY OF CHAR, Text.Length (t)) DO
          Text.SetChars (b^, t);
          d.update (b^);
        END;
      END;
      IF round = 2 THEN r := r & " " END;
      r := r & Show (d.finish ());
    END;
    RETURN r;
  END Chunks;

PROCEDURE Uuids (n: CARDINAL; v7: BOOLEAN): TEXT =
  VAR wr := TextWr.New ();  u: UUID.T;
  BEGIN
    FOR i := 1 TO n DO
      IF v7 THEN u := UUID.NewV7 () ELSE u := UUID.NewV4 () END;
      IF i > 1 THEN Wr.PutChar (wr, ',') END;
      Wr.PutText (wr, UUID.Format (u));
    END;
    RETURN TextWr.ToText (wr);
  END Uuids;

PROCEDURE ToUuid (t: TEXT): UUID.T =
  VAR u: UUID.T;
  BEGIN
    Text.SetChars (u, Arg (t));
    RETURN u;
  END ToUuid;

PROCEDURE KindText (k: Signature.Kind): TEXT =
  BEGIN
    RETURN "ok " & Fmt.Int (ORD (k));
  END KindText;

PROCEDURE Millis (start: Time.T): TEXT =
  BEGIN
    RETURN Fmt.Int (ROUND ((Time.Now () - start) * 1000.0D0));
  END Millis;

PROCEDURE Speed (READONLY a: ARRAY OF TEXT): TEXT RAISES {Crypto.Error} =
  VAR start: Time.T;  data := Text.FromChars (NEW (REF ARRAY OF CHAR, Int (a[2]))^);  n := Int (a[3]);
      key := Crypto.Random (32);  nonce := Crypto.Random (12);
  BEGIN
    start := Time.Now ();
    IF Text.Equal (a[0], "sd") THEN
      FOR i := 1 TO n DO EVAL Digest.Of (DigestAlg (a[1]), data) END;
    ELSIF Text.Equal (a[0], "sa") THEN
      FOR i := 1 TO n DO EVAL Aead.SealWith (AeadAlg (a[1]), key, nonce, data) END;
    ELSE
      WITH p = Signature.GenerateEc (Signature.Curve.P256), e = Signature.GenerateEd25519 () DO
        start := Time.Now ();
        FOR i := 1 TO n DO
          IF Int (a[1]) = 0 THEN
            EVAL Signature.Sign (Signature.Algorithm.Ed25519, e.private, data);
          ELSE
            EVAL Signature.Sign (Signature.Algorithm.EcdsaP256Sha256, p.private, data);
          END;
        END;
      END;
    END;
    RETURN Millis (start);
  END Speed;

PROCEDURE Do (line: TEXT): TEXT RAISES {Crypto.Error} =
  VAR a := Split (line);  op := a[0];  b: REF ARRAY OF CHAR;  x, y: TEXT;  u: UUID.T;
      pair: Signature.Pair;
  BEGIN
    IF Text.Equal (op, "rn") THEN
      RETURN Show (Crypto.Random (Int (a[1])));
    ELSIF Text.Equal (op, "rb") THEN
      b := NEW (REF ARRAY OF CHAR, Int (a[1]));
      Crypto.RandomBytes (b^);
      RETURN ShowArray (b^);
    ELSIF Text.Equal (op, "eq") THEN
      RETURN Bool (Crypto.Equal (Arg (a[1]), Arg (a[2])));

    ELSIF Text.Equal (op, "dg") THEN
      RETURN Show (Digest.Of (DigestAlg (a[1]), Arg (a[2])));
    ELSIF Text.Equal (op, "dc") THEN
      RETURN Chunks (Digest.New (DigestAlg (a[1])), SUBARRAY (a^, 2, NUMBER (a^) - 2));
    ELSIF Text.Equal (op, "dr") THEN
      RETURN Show (Digest.FromRd (DigestAlg (a[1]), Chunked (TextRd.New (Arg (a[2])), Int (a[3]))));
    ELSIF Text.Equal (op, "df") THEN
      RETURN FromFailing (DigestAlg (a[1]), Int (a[2]) = 1);
    ELSIF Text.Equal (op, "dt") THEN
      RETURN Threads (DigestAlg (a[1]), Int (a[2]), Int (a[3]), Int (a[4]));
    ELSIF Text.Equal (op, "hm") THEN
      RETURN Show (Digest.Hmac (DigestAlg (a[1]), Arg (a[2]), Arg (a[3])));
    ELSIF Text.Equal (op, "hc") THEN
      RETURN Chunks (Digest.NewHmac (DigestAlg (a[1]), Arg (a[2])), SUBARRAY (a^, 3, NUMBER (a^) - 3));

    ELSIF Text.Equal (op, "as") THEN
      RETURN Show (Aead.SealWith (AeadAlg (a[1]), Arg (a[2]), Arg (a[3]), Arg (a[4]), Arg (a[5])));
    ELSIF Text.Equal (op, "ao") THEN
      RETURN Show (Aead.OpenWith (AeadAlg (a[1]), Arg (a[2]), Arg (a[3]), Arg (a[4]), Arg (a[5])));
    ELSIF Text.Equal (op, "aS") THEN
      RETURN Show (Aead.Seal (AeadAlg (a[1]), Arg (a[2]), Arg (a[3]), Arg (a[4])));
    ELSIF Text.Equal (op, "aO") THEN
      RETURN Show (Aead.Open (AeadAlg (a[1]), Arg (a[2]), Arg (a[3]), Arg (a[4])));

    ELSIF Text.Equal (op, "pb") THEN
      RETURN Show (Kdf.Pbkdf2 (DigestAlg (a[1]), Arg (a[2]), Arg (a[3]), Int (a[4]), Int (a[5])));
    ELSIF Text.Equal (op, "hk") THEN
      RETURN Show (Kdf.Hkdf (DigestAlg (a[1]), Arg (a[2]), Arg (a[3]), Arg (a[4]), Int (a[5])));

    ELSIF Text.Equal (op, "ke") THEN
      EVAL publics.put (a[1], Signature.Ed25519Public (Arg (a[2])));
      RETURN KindText (Signature.PublicKind (Public (a[1])));
    ELSIF Text.Equal (op, "kr") THEN
      EVAL publics.put (a[1], Signature.RsaPublic (Arg (a[2]), Arg (a[3])));
      RETURN KindText (Signature.PublicKind (Public (a[1])));
    ELSIF Text.Equal (op, "kc") THEN
      EVAL publics.put (a[1], Signature.EcPublic (VAL (Int (a[2]), Signature.Curve), Arg (a[3]), Arg (a[4])));
      RETURN KindText (Signature.PublicKind (Public (a[1])));
    ELSIF Text.Equal (op, "kp") THEN
      EVAL publics.put (a[1], Signature.PublicFromPem (Arg (a[2])));
      RETURN KindText (Signature.PublicKind (Public (a[1])));
    ELSIF Text.Equal (op, "kq") THEN
      EVAL privates.put (a[1], Signature.PrivateFromPem (Arg (a[2])));
      RETURN KindText (Signature.PrivateKind (Private (a[1])));
    ELSIF Text.Equal (op, "kg") THEN
      CASE VAL (Int (a[2]), Signature.Kind) OF
      | Signature.Kind.Ed25519 => pair := Signature.GenerateEd25519 ();
      | Signature.Kind.Rsa => pair := Signature.GenerateRsa (Int (a[3]));
      | Signature.Kind.P256 => pair := Signature.GenerateEc (Signature.Curve.P256);
      | Signature.Kind.P384 => pair := Signature.GenerateEc (Signature.Curve.P384);
      | Signature.Kind.P521 => pair := Signature.GenerateEc (Signature.Curve.P521);
      END;
      EVAL publics.put (a[1], pair.public);
      EVAL privates.put (a[1], pair.private);
      RETURN KindText (Signature.PublicKind (pair.public)) & " " & Fmt.Int (ORD (Signature.PrivateKind (pair.private)));
    ELSIF Text.Equal (op, "kP") THEN
      RETURN Show (Signature.PublicToPem (Public (a[1])));
    ELSIF Text.Equal (op, "kQ") THEN
      RETURN Show (Signature.PrivateToPem (Private (a[1])));
    ELSIF Text.Equal (op, "kx") THEN
      Signature.PublicParts (Public (a[1]), x, y);
      RETURN Show (x) & " " & Show (y);
    ELSIF Text.Equal (op, "ks") THEN
      RETURN Show (Signature.Sign (SigAlg (a[1]), Private (a[2]), Arg (a[3])));
    ELSIF Text.Equal (op, "kv") THEN
      RETURN Bool (Signature.Verify (SigAlg (a[1]), Public (a[2]), Arg (a[3]), Arg (a[4])));

    ELSIF Text.Equal (op, "xn") THEN
      RETURN Show (X25519.NewPrivate ());
    ELSIF Text.Equal (op, "xp") THEN
      RETURN Show (X25519.Public (Arg (a[1])));
    ELSIF Text.Equal (op, "xa") THEN
      RETURN Show (X25519.Agree (Arg (a[1]), Arg (a[2])));
    ELSIF Text.Equal (op, "xi") THEN
      (* the iterations of section 5.2 of RFC 7748 *)
      x := Arg (a[2]);
      y := x;
      FOR i := 1 TO Int (a[1]) DO
        WITH r = X25519.Agree (x, y) DO
          y := x;
          x := r;
        END;
      END;
      RETURN Show (x);

    ELSIF Text.Equal (op, "u4") THEN
      RETURN Uuids (Int (a[1]), FALSE);
    ELSIF Text.Equal (op, "u7") THEN
      RETURN Uuids (Int (a[1]), TRUE);
    ELSIF Text.Equal (op, "uf") THEN
      RETURN UUID.Format (ToUuid (a[1]));
    ELSIF Text.Equal (op, "up") THEN
      IF NOT UUID.Parse (Arg (a[1]), u) THEN RETURN "no" END;
      RETURN ShowArray (u);
    ELSIF Text.Equal (op, "uv") THEN
      RETURN Fmt.Int (UUID.Version (ToUuid (a[1])));

    ELSIF Text.Equal (op, "sd") OR Text.Equal (op, "sa") OR Text.Equal (op, "ss") THEN
      RETURN Speed (a^);
    ELSE
      <*ASSERT FALSE*>
    END;
  END Do;

PROCEDURE Batch (in, out: TEXT) =
  VAR rd := FileRd.Open (in);  wr := FileWr.Open (out);  line, r: TEXT;
  BEGIN
    WHILE NOT Rd.EOF (rd) DO
      line := Rd.GetLine (rd);
      TRY
        r := Do (line);
      EXCEPT
      | Crypto.Error (e) => r := "err " & Atom.ToText (e.head);
      END;
      Wr.PutText (wr, r);
      Wr.PutChar (wr, '\n');
    END;
    Wr.Close (wr);
    Rd.Close (rd);
  END Batch;

BEGIN
  IF Params.Count = 4 AND Text.Equal (Params.Get (1), "batch") THEN
    Batch (Params.Get (2), Params.Get (3));
  ELSE
    <*ASSERT FALSE*>
  END;
END Main.
