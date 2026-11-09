(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE JsonWriter;

IMPORT FileWr, Fmt, LongFloat, OSError, Text, TextIntTbl, Thread, Wr;

CONST
  BufSize = 8192;
  Chunk = 4096;
  Room = 16;                    (* the most that one code unit can add *)
  TableFrom = 64;               (* keys before a table is worth it *)
  HexDigits = "0123456789abcdef";

TYPE
  Frame = RECORD
    isObject := FALSE;
    count: CARDINAL := 0;       (* of keys or of elements, so far *)
    keys: REF ARRAY OF TEXT := NIL;     (* the first TableFrom keys *)
    nk: CARDINAL := 0;
    seen: TextIntTbl.T := NIL;  (* of the keys, from TableFrom on *)
  END;

REVEAL
  T = Public BRANDED "JsonWriter.T" OBJECT
    wr: Wr.T;                   (* NIL after close *)
    file: Wr.T := NIL;          (* what close closes *)
    o: Options;
    buf: REF ARRAY OF CHAR;
    chunk: REF ARRAY OF WIDECHAR;
    n: CARDINAL := 0;
    closed := FALSE;            (* nothing more is accepted *)
    broken := FALSE;            (* and wr failed, so it is left alone *)
    rooted := FALSE;            (* the root value has begun *)
    keyed := FALSE;             (* a key is written, and waits for its value *)
    frames: REF ARRAY OF Frame;
    d: CARDINAL := 0;
  OVERRIDES
    object := Object;
    array := Array;
    end := End;
    key := Key;
    text := PutText;
    int := Int;
    real := Real;
    bool := Bool;
    null := Null;
    number := Number;
    depth := Depth;
    flush := Flush;
    finish := Finish;
    close := Close;
  END;

PROCEDURE ToWr (wr: Wr.T; READONLY o := Defaults): T =
  VAR t := NEW (T, wr := wr, o := o, buf := NEW (REF ARRAY OF CHAR, BufSize),
                chunk := NEW (REF ARRAY OF WIDECHAR, Chunk),
                frames := NEW (REF ARRAY OF Frame, 16));
  BEGIN
    IF o.indent # NIL THEN
      FOR i := 0 TO Text.Length (o.indent) - 1 DO
        WITH c = Text.GetChar (o.indent, i) DO
          <*ASSERT c = ' ' OR c = '\t'*>
        END;
      END;
    END;
    RETURN t;
  END ToWr;

PROCEDURE Open (path: TEXT; READONLY o := Defaults): T RAISES {OSError.E} =
  VAR wr := FileWr.Open (path); t := ToWr (wr, o);
  BEGIN
    t.file := wr;
    RETURN t;
  END Open;

PROCEDURE Format (f: Failure): TEXT =
  BEGIN
    RETURN f.message;
  END Format;

PROCEDURE Fail (t: T; p: Problem; message: TEXT): Failure =
  BEGIN
    t.closed := TRUE;
    RETURN NEW (Failure, problem := p, message := message);
  END Fail;

PROCEDURE Misplaced (t: T; message: TEXT): Failure =
  BEGIN
    RETURN Fail (t, Problem.Structure, message);
  END Misplaced;

PROCEDURE Guard (t: T) RAISES {Error} =
  BEGIN
    IF t.closed THEN
      RAISE Error (NEW (Failure, problem := Problem.Closed,
                        message := "the writer has ended"));
    END;
  END Guard;

(* A failure of wr leaves "broken" set, because the assignment after the call
   is not reached. *)
PROCEDURE Drain (t: T) RAISES {Wr.Failure, Thread.Alerted} =
  VAR n := t.n; was := t.closed;
  BEGIN
    IF n > 0 THEN
      t.closed := TRUE;
      t.broken := TRUE;
      t.n := 0;
      Wr.PutString (t.wr, SUBARRAY (t.buf^, 0, n));
      t.broken := FALSE;
      t.closed := was;
    END;
  END Drain;

(* Writes s, which is ASCII, as it is. *)
PROCEDURE Lit (t: T; s: TEXT) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    FOR i := 0 TO Text.Length (s) - 1 DO
      IF t.n = BufSize THEN Drain (t) END;
      t.buf[t.n] := Text.GetChar (s, i);
      INC (t.n);
    END;
  END Lit;

PROCEDURE Bad (t: T; c: INTEGER) RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    IF NOT t.o.replaceInvalid THEN
      RAISE Error (Fail (t, Problem.Content,
                         "the surrogate U+" & Fmt.Pad (Fmt.Int (c, 16), 4, '0')
                           & " has no partner"));
    END;
    Lit (t, "\357\277\275");
  END Bad;

(* Writes s as a string, in UTF-8. A surrogate may be the last of a chunk, so
   it is remembered across chunks. *)
PROCEDURE Put (t: T; s: TEXT) RAISES {Error, Wr.Failure, Thread.Alerted} =
  VAR
    len := Text.Length (s);
    pos := 0;
    cnt, c, v: INTEGER;
    high := -1;                 (* a high surrogate waiting for its partner *)
  BEGIN
    Lit (t, "\"");
    WHILE pos < len DO
      cnt := MIN (len - pos, Chunk);
      Text.SetWideChars (t.chunk^, s, pos);
      FOR j := 0 TO cnt - 1 DO
        c := ORD (t.chunk[j]);
        IF t.n > BufSize - Room THEN Drain (t) END;
        IF high >= 0 THEN
          IF c >= 16_DC00 AND c <= 16_DFFF THEN
            v := 16_10000 + (high - 16_D800) * 1024 + (c - 16_DC00);
            high := -1;
            WITH b = t.buf^ DO
              b[t.n] := VAL (16_F0 + v DIV 262144, CHAR);
              b[t.n + 1] := VAL (16_80 + v DIV 4096 MOD 64, CHAR);
              b[t.n + 2] := VAL (16_80 + v DIV 64 MOD 64, CHAR);
              b[t.n + 3] := VAL (16_80 + v MOD 64, CHAR);
            END;
            INC (t.n, 4);
            c := -1;
          ELSE
            Bad (t, high);
            high := -1;
          END;
        END;
        IF c >= 16_80 THEN
          IF c < 16_800 THEN
            t.buf[t.n] := VAL (16_C0 + c DIV 64, CHAR);
            t.buf[t.n + 1] := VAL (16_80 + c MOD 64, CHAR);
            INC (t.n, 2);
          ELSIF c >= 16_D800 AND c <= 16_DBFF THEN
            high := c;
          ELSIF c >= 16_DC00 AND c <= 16_DFFF THEN
            Bad (t, c);
          ELSE
            t.buf[t.n] := VAL (16_E0 + c DIV 4096, CHAR);
            t.buf[t.n + 1] := VAL (16_80 + c DIV 64 MOD 64, CHAR);
            t.buf[t.n + 2] := VAL (16_80 + c MOD 64, CHAR);
            INC (t.n, 3);
          END;
        ELSIF c >= 16_20 AND c # ORD ('"') AND c # ORD ('\\') THEN
          t.buf[t.n] := VAL (c, CHAR);
          INC (t.n);
        ELSIF c >= 0 THEN
          t.buf[t.n] := '\\';
          INC (t.n);
          CASE c OF
          | ORD ('"') => t.buf[t.n] := '"';
          | ORD ('\\') => t.buf[t.n] := '\\';
          | 16_08 => t.buf[t.n] := 'b';
          | 16_09 => t.buf[t.n] := 't';
          | 16_0A => t.buf[t.n] := 'n';
          | 16_0C => t.buf[t.n] := 'f';
          | 16_0D => t.buf[t.n] := 'r';
          ELSE
            t.buf[t.n] := 'u';
            t.buf[t.n + 1] := '0';
            t.buf[t.n + 2] := '0';
            t.buf[t.n + 3] := Text.GetChar (HexDigits, c DIV 16);
            t.buf[t.n + 4] := Text.GetChar (HexDigits, c MOD 16);
            INC (t.n, 4);
          END;
          INC (t.n);
        END;
      END;
      INC (pos, cnt);
    END;
    IF high >= 0 THEN Bad (t, high) END;
    Lit (t, "\"");
  END Put;

PROCEDURE Indent (t: T; level: CARDINAL) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    Lit (t, "\n");
    FOR i := 1 TO level DO Lit (t, t.o.indent) END;
  END Indent;

(* What comes before the next member of the innermost object or array. *)
PROCEDURE Separate (t: T) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    WITH f = t.frames[t.d - 1] DO
      IF f.count > 0 THEN Lit (t, ",") END;
      INC (f.count);
    END;
    IF t.o.indent # NIL THEN Indent (t, t.d) END;
  END Separate;

(* Whether the object has this key already; if not, it has it from now on. *)
PROCEDURE Duplicate (VAR f: Frame; k: TEXT): BOOLEAN =
  BEGIN
    IF f.seen = NIL AND f.nk < TableFrom THEN
      FOR i := 0 TO f.nk - 1 DO
        IF Text.Equal (f.keys[i], k) THEN RETURN TRUE END;
      END;
      IF f.keys = NIL THEN f.keys := NEW (REF ARRAY OF TEXT, TableFrom) END;
      f.keys[f.nk] := k;
      INC (f.nk);
      RETURN FALSE;
    END;
    IF f.seen = NIL THEN
      f.seen := NEW (TextIntTbl.Default).init (2 * f.nk);
      FOR i := 0 TO f.nk - 1 DO EVAL f.seen.put (f.keys[i], 0) END;
    END;
    RETURN f.seen.put (k, 0);
  END Duplicate;

PROCEDURE Key (t: T; k: TEXT) RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    Guard (t);
    IF t.d = 0 OR NOT t.frames[t.d - 1].isObject THEN
      RAISE Error (Misplaced (t, "only a member of an object has a key"));
    END;
    IF t.keyed THEN
      RAISE Error (Misplaced (t, "a key needs its value first"));
    END;
    IF t.o.uniqueKeys AND Duplicate (t.frames[t.d - 1], k) THEN
      RAISE Error (Misplaced (t, "the object has this key already"));
    END;
    Separate (t);
    Put (t, k);
    IF t.o.indent # NIL THEN Lit (t, ": ") ELSE Lit (t, ":") END;
    t.keyed := TRUE;
  END Key;

(* What every value does first. *)
PROCEDURE Before (t: T; key: TEXT) RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    Guard (t);
    IF key # NIL THEN Key (t, key) END;
    IF t.d = 0 THEN
      IF t.rooted THEN
        RAISE Error (Misplaced (t, "a document has one root value"));
      END;
      t.rooted := TRUE;
    ELSIF NOT t.frames[t.d - 1].isObject THEN
      Separate (t);
    ELSIF t.keyed THEN
      t.keyed := FALSE;
    ELSE
      RAISE Error (Misplaced (t, "a member of an object needs a key"));
    END;
  END Before;

PROCEDURE Begin (t: T; key: TEXT; isObject: BOOLEAN)
  RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    Before (t, key);
    IF t.o.maxDepth # 0 AND t.d >= t.o.maxDepth THEN
      RAISE Error (Misplaced (t, "the document is nested too deeply"));
    END;
    IF t.d = NUMBER (t.frames^) THEN
      VAR f := NEW (REF ARRAY OF Frame, 2 * t.d);
      BEGIN
        SUBARRAY (f^, 0, t.d) := t.frames^;
        t.frames := f;
      END;
    END;
    WITH f = t.frames[t.d] DO
      f.isObject := isObject;
      f.count := 0;
      f.nk := 0;
    END;
    INC (t.d);
    IF isObject THEN Lit (t, "{") ELSE Lit (t, "[") END;
  END Begin;

PROCEDURE Object (t: T; key: TEXT) RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    Begin (t, key, isObject := TRUE);
  END Object;

PROCEDURE Array (t: T; key: TEXT) RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    Begin (t, key, isObject := FALSE);
  END Array;

PROCEDURE End (t: T) RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    Guard (t);
    IF t.d = 0 THEN
      RAISE Error (Misplaced (t, "no object or array is open"));
    END;
    IF t.keyed THEN
      RAISE Error (Misplaced (t, "a key needs its value first"));
    END;
    WITH f = t.frames[t.d - 1] DO
      IF t.o.indent # NIL AND f.count > 0 THEN Indent (t, t.d - 1) END;
      IF f.isObject THEN Lit (t, "}") ELSE Lit (t, "]") END;
      f.seen := NIL;
    END;
    DEC (t.d);
  END End;

PROCEDURE PutText (t: T; s, key: TEXT) RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    Before (t, key);
    Put (t, s);
  END PutText;

PROCEDURE Int (t: T; i: INTEGER; key: TEXT)
  RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    Before (t, key);
    Lit (t, Fmt.Int (i));
  END Int;

PROCEDURE Real (t: T; r: LONGREAL; key: TEXT)
  RAISES {Error, Wr.Failure, Thread.Alerted} =
  VAR s: TEXT;
  BEGIN
    Before (t, key);
    IF LongFloat.IsNaN (r) OR NOT LongFloat.Finite (r) THEN
      IF NOT t.o.replaceInvalid THEN
        RAISE Error (Fail (t, Problem.Content, "JSON has only finite numbers"));
      END;
      Lit (t, "null");
    ELSE
      s := Fmt.LongReal (r);
      Lit (t, s);
      IF Text.FindChar (s, '.') < 0 AND Text.FindChar (s, 'e') < 0 THEN
        Lit (t, ".0");
      END;
    END;
  END Real;

PROCEDURE Bool (t: T; b: BOOLEAN; key: TEXT)
  RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    Before (t, key);
    IF b THEN Lit (t, "true") ELSE Lit (t, "false") END;
  END Bool;

PROCEDURE Null (t: T; key: TEXT) RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    Before (t, key);
    Lit (t, "null");
  END Null;

PROCEDURE IsNumber (s: TEXT): BOOLEAN =
  VAR i := 0; n := Text.Length (s);

  PROCEDURE Current (): INTEGER =
    BEGIN
      IF i < n THEN RETURN ORD (Text.GetWideChar (s, i)) ELSE RETURN -1 END;
    END Current;

  PROCEDURE Digits (): BOOLEAN =
    VAR from := i;
    BEGIN
      WHILE Current () >= ORD ('0') AND Current () <= ORD ('9') DO INC (i) END;
      RETURN i > from;
    END Digits;

  BEGIN
    IF Current () = ORD ('-') THEN INC (i) END;
    IF Current () = ORD ('0') THEN
      INC (i);
    ELSIF NOT Digits () THEN
      RETURN FALSE;
    END;
    IF Current () = ORD ('.') THEN
      INC (i);
      IF NOT Digits () THEN RETURN FALSE END;
    END;
    IF Current () = ORD ('e') OR Current () = ORD ('E') THEN
      INC (i);
      IF Current () = ORD ('+') OR Current () = ORD ('-') THEN INC (i) END;
      IF NOT Digits () THEN RETURN FALSE END;
    END;
    RETURN i = n;
  END IsNumber;

PROCEDURE Number (t: T; s, key: TEXT) RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    Before (t, key);
    IF NOT IsNumber (s) THEN
      RAISE Error (Fail (t, Problem.Content, "not a number of JSON"));
    END;
    Lit (t, s);
  END Number;

PROCEDURE Depth (t: T): CARDINAL =
  BEGIN
    RETURN t.d;
  END Depth;

PROCEDURE FlushWr (t: T) RAISES {Wr.Failure, Thread.Alerted} =
  VAR was := t.closed;
  BEGIN
    Drain (t);
    t.closed := TRUE;
    t.broken := TRUE;
    Wr.Flush (t.wr);
    t.broken := FALSE;
    t.closed := was;
  END FlushWr;

PROCEDURE Flush (t: T) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    IF t.wr # NIL AND NOT t.broken THEN FlushWr (t) END;
  END Flush;

PROCEDURE Finish (t: T) RAISES {Error, Wr.Failure, Thread.Alerted} =
  BEGIN
    Guard (t);
    IF NOT t.rooted THEN
      RAISE Error (Misplaced (t, "the document has no value"));
    END;
    IF t.d > 0 THEN
      RAISE Error (Misplaced (t, "an object or array is still open"));
    END;
    IF t.o.indent # NIL THEN Lit (t, "\n") END;
    FlushWr (t);
    t.closed := TRUE;
  END Finish;

PROCEDURE Close (t: T) RAISES {Wr.Failure, Thread.Alerted} =
  VAR f := t.file;
  BEGIN
    t.closed := TRUE;
    t.wr := NIL;
    t.file := NIL;
    IF f # NIL THEN Wr.Close (f) END;
  END Close;

BEGIN
END JsonWriter.
