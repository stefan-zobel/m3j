(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE JsonReader;

IMPORT FileRd, FloatMode, Fmt, LongFloat, OSError, Rd, Text, Thread;

CONST
  BufSize = 64 * 1024;
  Chunk = BufSize DIV 4;        (* units of a text that fit as UTF-8 *)
  FirstStore = 256;
  Replacement = 16_FFFD;
  Lone = "a surrogate has no partner";
  HighFirst = 16_D800;
  LowFirst = 16_DC00;
  LowLast = 16_DFFF;
  ExponentCap = 1000000000000;  (* more than the digits of any number *)
  IntLimit = -(LAST (INTEGER) DIV 10);
  IntLimitDigit = LAST (INTEGER) MOD 10 + 1;

TYPE
  State = {Root, After, FirstMember, NextMember, Colon, FirstElement, NextElement};
  Store = REF ARRAY OF WIDECHAR;

REVEAL
  T = Public BRANDED "JsonReader.T" OBJECT
    o: Options;
    rd: Rd.T := NIL;            (* NIL if the input is a text *)
    file: Rd.T := NIL;          (* what close closes *)
    src: TEXT := NIL;
    srcPos, srcLen: CARDINAL := 0;
    chunk: Store := NIL;

    buf: REF ARRAY OF CHAR;
    i, n: CARDINAL := 0;        (* the cursor and the end, in buf *)
    base := 0;                  (* the offset of buf[0] in the input *)
    eof := FALSE;
    closed := FALSE;
    fresh := TRUE;              (* a byte order mark can come *)
    ln := 1;
    lineStart := 0;             (* the offset where the line begins *)

    s := State.Root;
    stack: REF ARRAY OF BOOLEAN;        (* TRUE for an object *)
    d: CARDINAL := 0;

    ready := FALSE;             (* k holds what comes next *)
    k := Kind.EOF;
    tokenAt := 0;               (* the offset of what comes next *)
    held := FALSE;              (* element said that a value comes *)

    out: Store;                 (* the string being decoded *)
    len: CARDINAL := 0;
    key: Store;                 (* the key that member took *)
    keyLen: CARDINAL := 0;
    hasKey := FALSE;
    high := -1;                 (* a high surrogate, waiting for its partner *)
    highAt := 0;

    num: REF ARRAY OF CHAR;     (* the number at the cursor, once scanned *)
    numLen: CARDINAL := 0;
    scanned := FALSE;           (* the number is in num, and is past the cursor *)
    integral := FALSE;
    digits: REF ARRAY OF [0 .. 9] := NIL;
  OVERRIDES
    peek := Peek;
    object := Object;
    array := Array;
    member := Member;
    element := Element;
    end := End;
    name := Name;
    is := Is;
    text := GetText;
    int := Int;
    real := Real;
    number := Number;
    bool := Bool;
    null := Null;
    skip := Skip;
    finish := Finish;
    close := Close;
    depth := Depth;
    line := Line;
    column := Column;
    offset := Offset;
  END;

VAR
  (* The bytes that stand for themselves in a string. *)
  Plain := ARRAY CHAR OF BOOLEAN {FALSE, ..};
  KindNames := ARRAY Kind OF TEXT {"an object", "the end of an object", "an array",
                                   "the end of an array", "a key", "a string", "a number",
                                   "true or false", "null", "the end of the document"};

PROCEDURE New (READONLY o: Options; size: CARDINAL): T =
  BEGIN
    RETURN NEW (T, o := o, buf := NEW (REF ARRAY OF CHAR, size),
                stack := NEW (REF ARRAY OF BOOLEAN, 64),
                out := NEW (Store, FirstStore), key := NEW (Store, FirstStore),
                num := NEW (REF ARRAY OF CHAR, FirstStore));
  END New;

PROCEDURE FromRd (rd: Rd.T; READONLY o := Defaults): T =
  VAR t := New (o, BufSize);
  BEGIN
    t.rd := rd;
    RETURN t;
  END FromRd;

PROCEDURE FromText (t: TEXT; READONLY o := Defaults): T =
  VAR
    len := Text.Length (t);
    units := MIN (len, Chunk);
    r := New (o, 3 * units);    (* the most that they can be in UTF-8 *)
  BEGIN
    r.src := t;
    r.srcLen := len;
    r.chunk := NEW (Store, units);
    RETURN r;
  END FromText;

PROCEDURE Open (path: TEXT; READONLY o := Defaults): T RAISES {OSError.E} =
  VAR rd := FileRd.Open (path); t := FromRd (rd, o);
  BEGIN
    t.file := rd;
    RETURN t;
  END Open;

PROCEDURE Format (f: Failure): TEXT =
  BEGIN
    RETURN Fmt.Int (f.line) & ":" & Fmt.Int (f.column) & ": " & f.message;
  END Format;

(* ---------------------------------------------------------------- failures *)

PROCEDURE At (t: T; p: Problem; message: TEXT; offset: INTEGER): Failure =
  BEGIN
    RETURN NEW (Failure, problem := p, line := t.ln,
                column := offset - t.lineStart + 1, offset := offset, message := message);
  END At;

(* An error of the document, at the place given. *)
PROCEDURE FatalAt (t: T; p: Problem; message: TEXT; offset: INTEGER): Failure =
  BEGIN
    t.closed := TRUE;
    RETURN At (t, p, message, offset);
  END FatalAt;

PROCEDURE Fatal (t: T; p: Problem; message: TEXT): Failure =
  BEGIN
    RETURN FatalAt (t, p, message, t.base + t.i);
  END Fatal;

PROCEDURE Malformed (t: T; message: TEXT): Failure =
  BEGIN
    RETURN Fatal (t, Problem.Malformed, message);
  END Malformed;

PROCEDURE Guard (t: T) RAISES {Error} =
  BEGIN
    IF t.closed THEN
      RAISE Error (At (t, Problem.Closed, "the reader has ended", t.base + t.i));
    END;
  END Guard;

PROCEDURE Unexpected (t: T; message: TEXT): Failure =
  BEGIN
    RETURN At (t, Problem.Unexpected, message, t.tokenAt);
  END Unexpected;

PROCEDURE BadValue (t: T; message: TEXT): Failure =
  BEGIN
    RETURN At (t, Problem.BadValue, message, t.tokenAt);
  END BadValue;

(* ------------------------------------------------------------------- input *)

(* Makes the next text of the input into bytes. A pair stays in one piece, so
   that the decoder sees it whole; a surrogate without a partner becomes bytes
   that are not UTF-8, unless it is to be replaced anyway. *)
PROCEDURE FillText (t: T) =
  VAR
    cnt := MIN (t.srcLen - t.srcPos, Chunk);
    c, m := 0;
    j := 0;
  BEGIN
    Text.SetWideChars (t.chunk^, t.src, t.srcPos);
    IF t.srcPos + cnt < t.srcLen
         AND ORD (t.chunk[cnt - 1]) >= HighFirst AND ORD (t.chunk[cnt - 1]) < LowFirst THEN
      DEC (cnt);
    END;
    WHILE j < cnt DO
      c := ORD (t.chunk[j]);
      INC (j);
      IF c >= HighFirst AND c < LowFirst AND j < cnt
           AND ORD (t.chunk[j]) >= LowFirst AND ORD (t.chunk[j]) <= LowLast THEN
        c := 16_10000 + (c - HighFirst) * 1024 + ORD (t.chunk[j]) - LowFirst;
        INC (j);
      ELSIF c >= HighFirst AND c <= LowLast AND t.o.replaceInvalid THEN
        c := Replacement;
      END;
      IF c < 16_80 THEN
        t.buf[m] := VAL (c, CHAR);
        INC (m);
      ELSIF c < 16_800 THEN
        t.buf[m] := VAL (16_C0 + c DIV 64, CHAR);
        t.buf[m + 1] := VAL (16_80 + c MOD 64, CHAR);
        INC (m, 2);
      ELSIF c < 16_10000 THEN
        t.buf[m] := VAL (16_E0 + c DIV 4096, CHAR);
        t.buf[m + 1] := VAL (16_80 + c DIV 64 MOD 64, CHAR);
        t.buf[m + 2] := VAL (16_80 + c MOD 64, CHAR);
        INC (m, 3);
      ELSE
        t.buf[m] := VAL (16_F0 + c DIV 262144, CHAR);
        t.buf[m + 1] := VAL (16_80 + c DIV 4096 MOD 64, CHAR);
        t.buf[m + 2] := VAL (16_80 + c DIV 64 MOD 64, CHAR);
        t.buf[m + 3] := VAL (16_80 + c MOD 64, CHAR);
        INC (m, 4);
      END;
    END;
    INC (t.srcPos, cnt);
    t.n := m;
  END FillText;

(* The cursor is at the end of the buffer. TRUE if there is more input. A
   failure of rd leaves "closed" set, because the assignment after the calls is
   not reached. *)
PROCEDURE Refill (t: T): BOOLEAN RAISES {Rd.Failure, Thread.Alerted} =
  BEGIN
    IF t.eof THEN RETURN FALSE END;
    INC (t.base, t.n);
    t.i := 0;
    t.n := 0;
    IF t.rd = NIL THEN
      IF t.srcPos < t.srcLen THEN FillText (t) END;
    ELSE
      t.closed := TRUE;
      IF NOT Rd.EOF (t.rd) THEN
        t.n := Rd.GetSub (t.rd, SUBARRAY (t.buf^, 0, MIN (BufSize, MAX (1, Rd.CharsReady (t.rd)))));
      END;
      t.closed := FALSE;
    END;
    t.eof := t.n = 0;
    RETURN NOT t.eof;
  END Refill;

(* The byte at the cursor, or -1 at the end of the input. *)
PROCEDURE PeekByte (t: T): INTEGER RAISES {Rd.Failure, Thread.Alerted} =
  BEGIN
    IF t.i = t.n AND NOT Refill (t) THEN RETURN -1 END;
    RETURN ORD (t.buf[t.i]);
  END PeekByte;

(* Goes over white space; the byte after it, or -1. Only here can a line end,
   as a string has no raw line ends. *)
PROCEDURE Space (t: T): INTEGER RAISES {Rd.Failure, Thread.Alerted} =
  VAR j, n: CARDINAL; b := t.buf; c: CHAR;
  BEGIN
    LOOP
      IF t.i = t.n AND NOT Refill (t) THEN RETURN -1 END;
      j := t.i;
      n := t.n;
      WHILE j < n DO
        c := b[j];
        IF c = ' ' OR c = '\t' OR c = '\r' THEN
          INC (j);
        ELSIF c = '\n' THEN
          INC (j);
          INC (t.ln);
          t.lineStart := t.base + j;
        ELSE
          t.i := j;
          RETURN ORD (c);
        END;
      END;
      t.i := j;
    END;
  END Space;

PROCEDURE ByteOrderMark (t: T) RAISES {Error, Rd.Failure, Thread.Alerted} =
  BEGIN
    IF PeekByte (t) # 16_EF THEN RETURN END;
    INC (t.i);
    IF PeekByte (t) = 16_BB THEN
      INC (t.i);
      IF PeekByte (t) = 16_BF THEN
        INC (t.i);
        t.lineStart := 3;
        RETURN;
      END;
    END;
    RAISE Error (FatalAt (t, Problem.Malformed, "a value is expected", 0));
  END ByteOrderMark;

(* --------------------------------------------------------------- structure *)

PROCEDURE ValueKind (t: T; c: INTEGER): Kind RAISES {Error} =
  BEGIN
    IF c < 0 THEN RAISE Error (Malformed (t, "the document ends too early")) END;
    CASE VAL (c, CHAR) OF
    | '{' => RETURN Kind.Object;
    | '[' => RETURN Kind.Array;
    | '"' => RETURN Kind.Text;
    | '-', '0' .. '9' => RETURN Kind.Number;
    | 't', 'f' => RETURN Kind.Bool;
    | 'n' => RETURN Kind.Null;
    ELSE
      RAISE Error (Malformed (t, "a value is expected"));
    END;
  END ValueKind;

(* Goes over what separates the pieces, up to the first byte of the next. *)
PROCEDURE Scan (t: T): Kind RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR c: INTEGER; k: Kind;
  BEGIN
    IF t.fresh THEN
      t.fresh := FALSE;
      ByteOrderMark (t);
    END;
    c := Space (t);
    CASE t.s OF
    | State.Root =>
        k := ValueKind (t, c);
    | State.After =>
        IF c >= 0 THEN RAISE Error (Malformed (t, "text follows the document")) END;
        k := Kind.EOF;
    | State.FirstMember =>
        IF c = ORD ('}') THEN
          k := Kind.ObjectEnd;
        ELSIF c = ORD ('"') THEN
          k := Kind.Key;
        ELSIF c < 0 THEN
          RAISE Error (Malformed (t, "the document ends too early"));
        ELSE
          RAISE Error (Malformed (t, "a key or the end of the object is expected"));
        END;
    | State.NextMember =>
        IF c = ORD ('}') THEN
          k := Kind.ObjectEnd;
        ELSIF c = ORD (',') THEN
          INC (t.i);
          c := Space (t);
          IF c < 0 THEN RAISE Error (Malformed (t, "the document ends too early")) END;
          IF c # ORD ('"') THEN RAISE Error (Malformed (t, "a key is expected")) END;
          k := Kind.Key;
        ELSIF c < 0 THEN
          RAISE Error (Malformed (t, "the document ends too early"));
        ELSE
          RAISE Error (Malformed (t, "a comma or the end of the object is expected"));
        END;
    | State.Colon =>
        IF c < 0 THEN RAISE Error (Malformed (t, "the document ends too early")) END;
        IF c # ORD (':') THEN RAISE Error (Malformed (t, "a colon is expected")) END;
        INC (t.i);
        k := ValueKind (t, Space (t));
    | State.FirstElement =>
        IF c = ORD (']') THEN k := Kind.ArrayEnd; ELSE k := ValueKind (t, c); END;
    | State.NextElement =>
        IF c = ORD (']') THEN
          k := Kind.ArrayEnd;
        ELSIF c = ORD (',') THEN
          INC (t.i);
          k := ValueKind (t, Space (t));
        ELSIF c < 0 THEN
          RAISE Error (Malformed (t, "the document ends too early"));
        ELSE
          RAISE Error (Malformed (t, "a comma or the end of the array is expected"));
        END;
    END;
    t.k := k;
    t.ready := TRUE;
    t.tokenAt := t.base + t.i;
    RETURN k;
  END Scan;

PROCEDURE Peek (t: T): Kind RAISES {Error, Rd.Failure, Thread.Alerted} =
  BEGIN
    Guard (t);
    IF t.ready THEN RETURN t.k END;
    RETURN Scan (t);
  END Peek;

PROCEDURE Want (t: T; k: Kind) RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR found := Peek (t);
  BEGIN
    IF found # k THEN
      RAISE Error (Unexpected (t, KindNames[k] & " is expected, not " & KindNames[found]));
    END;
  END Want;

(* A value has been taken, or the end of an object or array. *)
PROCEDURE Taken (t: T) =
  BEGIN
    t.ready := FALSE;
    t.scanned := FALSE;
    t.hasKey := FALSE;
    t.held := FALSE;
    IF t.d = 0 THEN
      t.s := State.After;
    ELSIF t.stack[t.d - 1] THEN
      t.s := State.NextMember;
    ELSE
      t.s := State.NextElement;
    END;
  END Taken;

PROCEDURE Push (t: T; isObject: BOOLEAN) RAISES {Error} =
  BEGIN
    IF t.o.maxDepth # 0 AND t.d = t.o.maxDepth THEN
      RAISE Error (Fatal (t, Problem.Limit, "the nesting is too deep"));
    END;
    IF t.d = NUMBER (t.stack^) THEN
      WITH new = NEW (REF ARRAY OF BOOLEAN, 2 * t.d) DO
        SUBARRAY (new^, 0, t.d) := t.stack^;
        t.stack := new;
      END;
    END;
    t.stack[t.d] := isObject;
    INC (t.d);
    INC (t.i);
    t.ready := FALSE;
    t.hasKey := FALSE;
    t.held := FALSE;
    IF isObject THEN t.s := State.FirstMember; ELSE t.s := State.FirstElement; END;
  END Push;

PROCEDURE Pop (t: T) =
  BEGIN
    INC (t.i);
    DEC (t.d);
    Taken (t);
  END Pop;

PROCEDURE Object (t: T) RAISES {Error, Rd.Failure, Thread.Alerted} =
  BEGIN
    Want (t, Kind.Object);
    Push (t, TRUE);
  END Object;

PROCEDURE Array (t: T) RAISES {Error, Rd.Failure, Thread.Alerted} =
  BEGIN
    Want (t, Kind.Array);
    Push (t, FALSE);
  END Array;

PROCEDURE Member (t: T): BOOLEAN RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR k := Peek (t); store: Store;
  BEGIN
    IF k = Kind.ObjectEnd THEN
      Pop (t);
      RETURN FALSE;
    ELSIF k # Kind.Key THEN
      RAISE Error (Unexpected (t, "a key or the end of an object is expected, not " & KindNames[k]));
    END;
    INC (t.i);
    t.len := 0;
    Decode (t, TRUE);
    store := t.key;
    t.key := t.out;
    t.out := store;
    t.keyLen := t.len;
    t.hasKey := TRUE;
    t.ready := FALSE;
    t.s := State.Colon;
    RETURN TRUE;
  END Member;

PROCEDURE Element (t: T): BOOLEAN RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR k := Peek (t);
  BEGIN
    IF t.held THEN
      RAISE Error (Unexpected (t, "the element before is not taken yet"));
    END;
    IF k = Kind.ArrayEnd THEN
      Pop (t);
      RETURN FALSE;
    END;
    IF k = Kind.Key OR k = Kind.ObjectEnd OR k = Kind.EOF
         OR t.d = 0 OR t.stack[t.d - 1] THEN
      RAISE Error (Unexpected (t, "an element is expected, not " & KindNames[k]));
    END;
    t.held := TRUE;
    RETURN TRUE;
  END Element;

(* Takes what is left until only "depth" objects and arrays are open. It holds
   no stack but that of the reader, so it can go as deep as the reader. *)
PROCEDURE Run (t: T; depth: CARDINAL) RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR k: Kind;
  BEGIN
    WHILE t.d > depth DO
      k := Peek (t);
      CASE k OF
      | Kind.Key =>
          INC (t.i);
          t.len := 0;
          Decode (t, FALSE);
          t.ready := FALSE;
          t.s := State.Colon;
      | Kind.ObjectEnd, Kind.ArrayEnd => Pop (t);
      | Kind.Object => Push (t, TRUE);
      | Kind.Array => Push (t, FALSE);
      | Kind.EOF => <*ASSERT FALSE*>
      ELSE
        Scalar (t, k);
      END;
    END;
  END Run;

PROCEDURE End (t: T) RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR k := Peek (t);
  BEGIN
    IF t.d = 0 THEN
      RAISE Error (Unexpected (t, "no object or array is open, the next is " & KindNames[k]));
    END;
    Run (t, t.d - 1);
  END End;

PROCEDURE Skip (t: T) RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR k := Peek (t);
  BEGIN
    CASE k OF
    | Kind.Key, Kind.ObjectEnd, Kind.ArrayEnd, Kind.EOF =>
        RAISE Error (Unexpected (t, "a value is expected, not " & KindNames[k]));
    | Kind.Object =>
        Push (t, TRUE);
        Run (t, t.d - 1);
    | Kind.Array =>
        Push (t, FALSE);
        Run (t, t.d - 1);
    ELSE
      Scalar (t, k);
    END;
  END Skip;

(* Takes a string, number, true, false or null, and only checks it. *)
PROCEDURE Scalar (t: T; k: Kind) RAISES {Error, Rd.Failure, Thread.Alerted} =
  BEGIN
    CASE k OF
    | Kind.Text =>
        INC (t.i);
        t.len := 0;
        Decode (t, FALSE);
    | Kind.Number => ScanNumber (t);
    | Kind.Bool =>
        IF PeekByte (t) = ORD ('t') THEN Literal (t, "true"); ELSE Literal (t, "false"); END;
    | Kind.Null => Literal (t, "null");
    ELSE
      <*ASSERT FALSE*>
    END;
    Taken (t);
  END Scalar;

PROCEDURE Literal (t: T; word: TEXT) RAISES {Error, Rd.Failure, Thread.Alerted} =
  BEGIN
    FOR j := 0 TO Text.Length (word) - 1 DO
      IF PeekByte (t) # ORD (Text.GetChar (word, j)) THEN
        RAISE Error (Malformed (t, "a value is expected"));
      END;
      INC (t.i);
    END;
  END Literal;

PROCEDURE Finish (t: T) RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR k := Peek (t);
  BEGIN
    IF k # Kind.EOF THEN
      RAISE Error (Unexpected (t, "the document is not read to its end, the next is "
                                    & KindNames[k]));
    END;
    t.closed := TRUE;
  END Finish;

PROCEDURE Close (t: T) =
  BEGIN
    t.closed := TRUE;
    IF t.file # NIL THEN
      TRY
        Rd.Close (t.file);
      EXCEPT
      | Rd.Failure, Thread.Alerted =>   (* nothing can be done about it *)
      END;
      t.file := NIL;
    END;
  END Close;

PROCEDURE Depth (t: T): CARDINAL =
  BEGIN
    RETURN t.d;
  END Depth;

PROCEDURE Line (t: T): INTEGER =
  BEGIN
    RETURN t.ln;
  END Line;

PROCEDURE Column (t: T): INTEGER =
  BEGIN
    RETURN t.base + t.i - t.lineStart + 1;
  END Column;

PROCEDURE Offset (t: T): INTEGER =
  BEGIN
    RETURN t.base + t.i;
  END Offset;

PROCEDURE Name (t: T): TEXT =
  BEGIN
    IF NOT t.hasKey THEN RETURN "" END;
    RETURN Text.FromWideChars (SUBARRAY (t.key^, 0, t.keyLen));
  END Name;

PROCEDURE Is (t: T; key: TEXT): BOOLEAN =
  BEGIN
    IF NOT t.hasKey OR Text.Length (key) # t.keyLen THEN RETURN FALSE END;
    FOR j := 0 TO t.keyLen - 1 DO
      IF Text.GetWideChar (key, j) # t.key[j] THEN RETURN FALSE END;
    END;
    RETURN TRUE;
  END Is;

(* ----------------------------------------------------------------- strings *)

(* Makes place for k more units of the string; the limit holds also for one
   that is not kept, so that a document is refused whatever reads it. *)
PROCEDURE Room (t: T; k: CARDINAL; store: BOOLEAN) RAISES {Error} =
  BEGIN
    IF t.o.maxStringChars # 0 AND t.len + k > t.o.maxStringChars THEN
      RAISE Error (Fatal (t, Problem.Limit, "a string is too long"));
    END;
    IF store AND t.len + k > NUMBER (t.out^) THEN
      WITH new = NEW (Store, MAX (2 * NUMBER (t.out^), t.len + k)) DO
        SUBARRAY (new^, 0, t.len) := SUBARRAY (t.out^, 0, t.len);
        t.out := new;
      END;
    END;
  END Room;

PROCEDURE Put (t: T; c: INTEGER; store: BOOLEAN) RAISES {Error} =
  BEGIN
    Room (t, 1, store);
    IF store THEN t.out[t.len] := VAL (c, WIDECHAR) END;
    INC (t.len);
  END Put;

(* Something at "at" is not a character. *)
PROCEDURE Invalid (t: T; at: INTEGER; message: TEXT; store: BOOLEAN) RAISES {Error} =
  BEGIN
    IF NOT t.o.replaceInvalid THEN
      RAISE Error (FatalAt (t, Problem.Encoding, message, at));
    END;
    Put (t, Replacement, store);
  END Invalid;

(* The next bytes are plain, so they are the units of the string. *)
PROCEDURE PutRun (t: T; to: CARDINAL; store: BOOLEAN) RAISES {Error} =
  VAR k := to - t.i; m := t.o.maxStringChars;
  BEGIN
    IF m # 0 AND t.len + k > m THEN
      INC (t.i, m - t.len);     (* at the first one that is too many *)
    END;
    Room (t, k, store);
    IF store THEN
      WITH o = t.out, b = t.buf, from = t.i, len = t.len DO
        FOR j := 0 TO k - 1 DO
          o[len + j] := VAL (ORD (b[from + j]), WIDECHAR);
        END;
      END;
    END;
    INC (t.len, k);
    t.i := to;
  END PutRun;

(* A character of UTF-8 is at the cursor. What may follow its first byte is
   what makes a shortest form that is not a surrogate and not above U+10FFFF
   (table 3-7 of the Unicode standard). *)
PROCEDURE Utf8 (t: T; store: BOOLEAN) RAISES {Error, Rd.Failure, Thread.Alerted} =
  CONST Message = "the input is not UTF-8";
  VAR
    at := t.base + t.i;
    c := ORD (t.buf[t.i]);
    need := 0;
    lo := 16_80;
    hi := 16_BF;
    b: INTEGER;
  BEGIN
    INC (t.i);
    IF c >= 16_C2 AND c <= 16_DF THEN
      need := 1;
      c := c - 16_C0;
    ELSIF c >= 16_E0 AND c <= 16_EF THEN
      need := 2;
      IF c = 16_E0 THEN lo := 16_A0; ELSIF c = 16_ED THEN hi := 16_9F; END;
      c := c - 16_E0;
    ELSIF c >= 16_F0 AND c <= 16_F4 THEN
      need := 3;
      IF c = 16_F0 THEN lo := 16_90; ELSIF c = 16_F4 THEN hi := 16_8F; END;
      c := c - 16_F0;
    ELSE
      Invalid (t, at, Message, store);
      RETURN;
    END;
    FOR j := 1 TO need DO
      b := PeekByte (t);
      IF b < lo OR b > hi THEN
        Invalid (t, at, Message, store);
        RETURN;
      END;
      INC (t.i);
      c := c * 64 + b - 16_80;
      lo := 16_80;
      hi := 16_BF;
    END;
    IF c >= 16_10000 THEN
      DEC (c, 16_10000);
      Put (t, HighFirst + c DIV 1024, store);
      Put (t, LowFirst + c MOD 1024, store);
    ELSE
      Put (t, c, store);
    END;
  END Utf8;

(* The four digits after "\u", which is taken. *)
PROCEDURE Hex4 (t: T): INTEGER RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR v := 0; b, d: INTEGER;
  BEGIN
    FOR j := 1 TO 4 DO
      b := PeekByte (t);
      IF b >= ORD ('0') AND b <= ORD ('9') THEN
        d := b - ORD ('0');
      ELSIF b >= ORD ('a') AND b <= ORD ('f') THEN
        d := b - ORD ('a') + 10;
      ELSIF b >= ORD ('A') AND b <= ORD ('F') THEN
        d := b - ORD ('A') + 10;
      ELSIF b < 0 THEN
        RAISE Error (Malformed (t, "the document ends in a string"));
      ELSE
        RAISE Error (Malformed (t, "a hexadecimal digit is expected"));
      END;
      INC (t.i);
      v := v * 16 + d;
    END;
    RETURN v;
  END Hex4;

(* A high surrogate from an escape has no partner, if it still waits now. *)
PROCEDURE Settle (t: T; store: BOOLEAN) RAISES {Error} =
  BEGIN
    IF t.high >= 0 THEN
      t.high := -1;
      Invalid (t, t.highAt, Lone, store);
    END;
  END Settle;

(* Reads a string whose first quote is taken, up to the closing one, and
   decodes it to t.out if "store". Everything is taken from the buffer as it
   comes, so that any place may be the end of a buffer. A high surrogate from
   an escape waits in t.high for its partner. *)
PROCEDURE Decode (t: T; store: BOOLEAN) RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR
    b := t.buf;
    j, n: CARDINAL;
    c: CHAR;
    e, v: INTEGER;
    at := 0;
  BEGIN
    t.high := -1;
    LOOP
      IF t.i = t.n AND NOT Refill (t) THEN
        RAISE Error (Malformed (t, "the document ends in a string"));
      END;
      j := t.i;
      n := t.n;
      WHILE j < n AND Plain[b[j]] DO INC (j) END;
      IF j > t.i THEN
        Settle (t, store);
        PutRun (t, j, store);
      END;
      IF j < n THEN
        c := b[j];
        IF c = '"' THEN
          Settle (t, store);
          t.i := j + 1;
          RETURN;
        ELSIF c = '\\' THEN
          at := t.base + j;
          t.i := j + 1;
          e := PeekByte (t);
          IF e < 0 THEN RAISE Error (Malformed (t, "the document ends in a string")) END;
          IF e # ORD ('u') THEN Settle (t, store) END;
          INC (t.i);
          CASE VAL (e, CHAR) OF
          | '"', '\\', '/' => Put (t, e, store);
          | 'b' => Put (t, 8, store);
          | 'f' => Put (t, 12, store);
          | 'n' => Put (t, 10, store);
          | 'r' => Put (t, 13, store);
          | 't' => Put (t, 9, store);
          | 'u' =>
              v := Hex4 (t);
              IF v >= LowFirst AND v <= LowLast THEN
                IF t.high >= 0 THEN
                  Put (t, t.high, store);
                  Put (t, v, store);
                  t.high := -1;
                ELSE
                  Invalid (t, at, Lone, store);
                END;
              ELSE
                Settle (t, store);
                IF v >= HighFirst AND v < LowFirst THEN
                  t.high := v;
                  t.highAt := at;
                ELSE
                  Put (t, v, store);
                END;
              END;
          ELSE
            RAISE Error (FatalAt (t, Problem.Malformed, "an unknown escape", at));
          END;
        ELSIF ORD (c) < 16_20 THEN
          t.i := j;
          RAISE Error (Malformed (t, "a control character in a string"));
        ELSE
          t.i := j;
          Settle (t, store);
          Utf8 (t, store);
        END;
      END;
    END;
  END Decode;

PROCEDURE GetText (t: T): TEXT RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR b := t.buf; i, j, n: CARDINAL; r: TEXT; m: INTEGER;
  BEGIN
    Want (t, Kind.Text);
    i := t.i + 1;
    j := i;
    n := t.n;
    m := t.o.maxStringChars;
    WHILE j < n AND Plain[b[j]] DO INC (j) END;
    IF j < n AND b[j] = '"' AND (m = 0 OR j - i <= m) THEN
      r := Text.FromChars (SUBARRAY (b^, i, j - i));
      t.i := j + 1;
    ELSE
      t.i := i;
      t.len := 0;
      Decode (t, TRUE);
      r := Text.FromWideChars (SUBARRAY (t.out^, 0, t.len));
    END;
    Taken (t);
    RETURN r;
  END GetText;

(* ----------------------------------------------------------------- numbers *)

PROCEDURE NumRoom (t: T; k: CARDINAL) RAISES {Error} =
  BEGIN
    IF t.o.maxNumberChars # 0 AND t.numLen + k > t.o.maxNumberChars THEN
      INC (t.i, t.o.maxNumberChars - t.numLen);
      RAISE Error (Fatal (t, Problem.Limit, "a number is too long"));
    END;
    IF t.numLen + k > NUMBER (t.num^) THEN
      WITH new = NEW (REF ARRAY OF CHAR, MAX (2 * NUMBER (t.num^), t.numLen + k)) DO
        SUBARRAY (new^, 0, t.numLen) := SUBARRAY (t.num^, 0, t.numLen);
        t.num := new;
      END;
    END;
  END NumRoom;

(* Takes the byte at the cursor into the number. *)
PROCEDURE Keep (t: T) RAISES {Error} =
  BEGIN
    NumRoom (t, 1);
    t.num[t.numLen] := t.buf[t.i];
    INC (t.numLen);
    INC (t.i);
  END Keep;

(* Takes digits into the number; how many there are. *)
PROCEDURE Digits (t: T): CARDINAL RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR b := t.buf; j, n, k: CARDINAL; count := 0;
  BEGIN
    LOOP
      IF t.i = t.n AND NOT Refill (t) THEN RETURN count END;
      j := t.i;
      n := t.n;
      WHILE j < n AND b[j] >= '0' AND b[j] <= '9' DO INC (j) END;
      k := j - t.i;
      IF k > 0 THEN
        NumRoom (t, k);
        SUBARRAY (t.num^, t.numLen, k) := SUBARRAY (b^, t.i, k);
        INC (t.numLen, k);
        INC (count, k);
        t.i := j;
      END;
      IF j < n THEN RETURN count END;
    END;
  END Digits;

(* Checks the number at the cursor against the grammar, and holds it in num.
   It stays what comes next, until it is taken. *)
PROCEDURE ScanNumber (t: T) RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR c: INTEGER;
  BEGIN
    IF t.scanned THEN RETURN END;
    t.numLen := 0;
    t.integral := TRUE;
    IF PeekByte (t) = ORD ('-') THEN Keep (t) END;
    c := PeekByte (t);
    IF c = ORD ('0') THEN
      Keep (t);
    ELSIF c < ORD ('1') OR c > ORD ('9') THEN
      RAISE Error (Malformed (t, "a digit is expected in the number"));
    ELSE
      EVAL Digits (t);
    END;
    IF PeekByte (t) = ORD ('.') THEN
      t.integral := FALSE;
      Keep (t);
      IF Digits (t) = 0 THEN
        RAISE Error (Malformed (t, "a digit is expected after the point"));
      END;
    END;
    c := PeekByte (t);
    IF c = ORD ('e') OR c = ORD ('E') THEN
      t.integral := FALSE;
      Keep (t);
      c := PeekByte (t);
      IF c = ORD ('+') OR c = ORD ('-') THEN Keep (t) END;
      IF Digits (t) = 0 THEN
        RAISE Error (Malformed (t, "a digit is expected in the exponent"));
      END;
    END;
    t.scanned := TRUE;
  END ScanNumber;

PROCEDURE Int (t: T): INTEGER RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR j := 0; v := 0; d: INTEGER; negative: BOOLEAN;
  BEGIN
    Want (t, Kind.Number);
    ScanNumber (t);
    IF NOT t.integral THEN
      RAISE Error (BadValue (t, "the number is not an integer"));
    END;
    negative := t.num[0] = '-';
    IF negative THEN j := 1 END;
    (* counts downward, as the most negative value has no positive *)
    WHILE j < t.numLen DO
      d := ORD (t.num[j]) - ORD ('0');
      IF v < IntLimit OR v = IntLimit AND d > IntLimitDigit THEN
        RAISE Error (BadValue (t, "the number is too large for an INTEGER"));
      END;
      v := v * 10 - d;
      INC (j);
    END;
    IF NOT negative THEN
      IF v = FIRST (INTEGER) THEN
        RAISE Error (BadValue (t, "the number is too large for an INTEGER"));
      END;
      v := -v;
    END;
    Taken (t);
    RETURN v;
  END Int;

PROCEDURE Real (t: T): LONGREAL RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR
    j := 0;
    count := 0;
    exp := 0;
    whole := 0;
    point := FALSE;
    sign: [0 .. 1] := 0;
    c: CHAR;
    r: LONGREAL;
  BEGIN
    Want (t, Kind.Number);
    ScanNumber (t);
    IF t.digits = NIL OR NUMBER (t.digits^) < t.numLen THEN
      t.digits := NEW (REF ARRAY OF [0 .. 9], MAX (t.numLen, FirstStore));
    END;
    IF t.num[0] = '-' THEN
      sign := 1;
      j := 1;
    END;
    WHILE j < t.numLen DO
      c := t.num[j];
      INC (j);
      IF c = '.' THEN
        point := TRUE;
      ELSIF c = 'e' OR c = 'E' THEN
        exp := Exponent (t, j);
        j := t.numLen;
      ELSE
        t.digits[count] := ORD (c) - ORD ('0');
        INC (count);
        IF NOT point THEN INC (whole) END;
      END;
    END;
    TRY
      r := LongFloat.FromDecimal (sign, SUBARRAY (t.digits^, 0, count), whole - 1 + exp);
    EXCEPT
    | FloatMode.Trap =>
        RAISE Error (BadValue (t, "the number does not fit in a LONGREAL"));
    END;
    IF NOT LongFloat.Finite (r) THEN
      RAISE Error (BadValue (t, "the number does not fit in a LONGREAL"));
    END;
    Taken (t);
    RETURN r;
  END Real;

(* The exponent in num from "j" on, held at a value that is out of reach of
   any number. *)
PROCEDURE Exponent (t: T; j: CARDINAL): INTEGER =
  VAR v := 0; negative := FALSE; c: CHAR;
  BEGIN
    c := t.num[j];
    IF c = '-' OR c = '+' THEN
      negative := c = '-';
      INC (j);
    END;
    WHILE j < t.numLen DO
      IF v < ExponentCap THEN v := v * 10 + ORD (t.num[j]) - ORD ('0') END;
      INC (j);
    END;
    IF negative THEN RETURN -v END;
    RETURN v;
  END Exponent;

PROCEDURE Number (t: T): TEXT RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR r: TEXT;
  BEGIN
    Want (t, Kind.Number);
    ScanNumber (t);
    r := Text.FromChars (SUBARRAY (t.num^, 0, t.numLen));
    Taken (t);
    RETURN r;
  END Number;

PROCEDURE Bool (t: T): BOOLEAN RAISES {Error, Rd.Failure, Thread.Alerted} =
  VAR r: BOOLEAN;
  BEGIN
    Want (t, Kind.Bool);
    r := PeekByte (t) = ORD ('t');
    Scalar (t, Kind.Bool);
    RETURN r;
  END Bool;

PROCEDURE Null (t: T) RAISES {Error, Rd.Failure, Thread.Alerted} =
  BEGIN
    Want (t, Kind.Null);
    Scalar (t, Kind.Null);
  END Null;

BEGIN
  FOR c := ' ' TO '\177' DO
    Plain[c] := c # '"' AND c # '\\';
  END;
END JsonReader.
