(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE URI;

IMPORT Text, TextWr, Thread, Wr;

<*FATAL Wr.Failure, Thread.Alerted*>

TYPE Chars = SET OF CHAR;

CONST
  Alpha = Chars {'a' .. 'z', 'A' .. 'Z'};
  Digit = Chars {'0' .. '9'};
  HexDigit = Digit + Chars {'a' .. 'f', 'A' .. 'F'};
  SubDelims = Chars {'!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '='};
  SchemeChars = Alpha + Digit + Chars {'+', '-', '.'};
  UserChars = Unreserved + SubDelims + Chars {':'};
  HostChars = Unreserved + SubDelims;
  PathChars = Unreserved + SubDelims + Chars {':', '@', '/'};
  QueryChars = PathChars + Chars {'?'};
  FutureChars = Unreserved + SubDelims + Chars {':'};

  HexUpper = ARRAY [0 .. 15] OF CHAR {'0', '1', '2', '3', '4', '5', '6', '7',
                                      '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

PROCEDURE ToChars (t: TEXT): REF ARRAY OF CHAR =
  VAR b := NEW (REF ARRAY OF CHAR, Text.Length (t));
  BEGIN
    Text.SetChars (b^, t);
    RETURN b;
  END ToChars;

PROCEDURE Sub (READONLY b: ARRAY OF CHAR; from, to: INTEGER): TEXT =
  BEGIN
    RETURN Text.FromChars (SUBARRAY (b, from, to - from));
  END Sub;

(* Are b[from] to b[to - 1] characters of "allowed" and well formed escapes? *)
PROCEDURE Valid (READONLY b: ARRAY OF CHAR; from, to: INTEGER; READONLY allowed: Chars): BOOLEAN =
  VAR i := from;
  BEGIN
    WHILE i < to DO
      IF b[i] = '%' THEN
        IF i + 2 >= to OR NOT b[i + 1] IN HexDigit OR NOT b[i + 2] IN HexDigit THEN
          RETURN FALSE;
        END;
        INC (i, 3);
      ELSIF b[i] IN allowed THEN
        INC (i);
      ELSE
        RETURN FALSE;
      END;
    END;
    RETURN TRUE;
  END Valid;

PROCEDURE ValidIPv4 (READONLY b: ARRAY OF CHAR; from, to: INTEGER): BOOLEAN =
  VAR i := from;  start, v: INTEGER;
  BEGIN
    FOR octet := 1 TO 4 DO
      start := i;
      v := 0;
      WHILE i < to AND b[i] IN Digit AND i - start < 3 DO
        v := v * 10 + ORD (b[i]) - ORD ('0');
        INC (i);
      END;
      IF i = start OR v > 255 OR (i - start > 1 AND b[start] = '0') THEN RETURN FALSE END;
      IF octet < 4 THEN
        IF i >= to OR b[i] # '.' THEN RETURN FALSE END;
        INC (i);
      END;
    END;
    RETURN i = to;
  END ValidIPv4;

PROCEDURE ValidIPv6 (READONLY b: ARRAY OF CHAR; from, to: INTEGER): BOOLEAN =
  VAR i := from;  j: INTEGER;  count := 0;  double := FALSE;
  BEGIN
    IF to - from >= 2 AND b[i] = ':' AND b[i + 1] = ':' THEN
      double := TRUE;
      INC (i, 2);
    END;
    WHILE i < to DO
      j := i;
      WHILE j < to AND b[j] IN HexDigit DO INC (j) END;
      IF j < to AND b[j] = '.' THEN
        IF NOT ValidIPv4 (b, i, to) THEN RETURN FALSE END;
        INC (count, 2);
        i := to;
      ELSE
        IF j = i OR j - i > 4 THEN RETURN FALSE END;
        INC (count);
        i := j;
        IF i < to THEN
          IF b[i] # ':' THEN RETURN FALSE END;
          IF i + 1 < to AND b[i + 1] = ':' THEN
            IF double THEN RETURN FALSE END;
            double := TRUE;
            INC (i, 2);
          ELSE
            INC (i);
            IF i = to THEN RETURN FALSE END;
          END;
        END;
      END;
    END;
    IF double THEN RETURN count <= 7 ELSE RETURN count = 8 END;
  END ValidIPv6;

(* The inside of the brackets of an IP literal. *)
PROCEDURE ValidLiteral (READONLY b: ARRAY OF CHAR; from, to: INTEGER): BOOLEAN =
  VAR i: INTEGER;
  BEGIN
    IF from < to AND b[from] IN Chars {'v', 'V'} THEN
      i := from + 1;
      WHILE i < to AND b[i] IN HexDigit DO INC (i) END;
      IF i = from + 1 OR i >= to OR b[i] # '.' OR i + 1 >= to THEN RETURN FALSE END;
      FOR k := i + 1 TO to - 1 DO
        IF NOT b[k] IN FutureChars THEN RETURN FALSE END;
      END;
      RETURN TRUE;
    END;
    RETURN ValidIPv6 (b, from, to);
  END ValidLiteral;

PROCEDURE ParseAuthority (READONLY b: ARRAY OF CHAR; from, to: INTEGER; VAR u: T): BOOLEAN =
  VAR i := from;  start := from;  v: INTEGER;
  BEGIN
    WHILE i < to AND b[i] # '@' DO INC (i) END;
    IF i < to THEN
      IF NOT Valid (b, from, i, UserChars) THEN RETURN FALSE END;
      u.userinfo := Sub (b, from, i);
      start := i + 1;
    END;
    i := start;
    IF i < to AND b[i] = '[' THEN
      WHILE i < to AND b[i] # ']' DO INC (i) END;
      IF i = to OR NOT ValidLiteral (b, start + 1, i) THEN RETURN FALSE END;
      INC (i);
    ELSE
      WHILE i < to AND b[i] # ':' DO INC (i) END;
      IF NOT Valid (b, start, i, HostChars) THEN RETURN FALSE END;
    END;
    u.host := Sub (b, start, i);
    IF i < to THEN
      IF b[i] # ':' THEN RETURN FALSE END;
      INC (i);
      v := 0;
      FOR k := i TO to - 1 DO
        IF NOT b[k] IN Digit THEN RETURN FALSE END;
        v := v * 10 + ORD (b[k]) - ORD ('0');
        IF v > 65535 THEN RETURN FALSE END;
      END;
      IF i < to THEN u.port := v END;
    END;
    RETURN TRUE;
  END ParseAuthority;

PROCEDURE Parse (t: TEXT; VAR u: T): BOOLEAN =
  VAR n := Text.Length (t);  b: REF ARRAY OF CHAR;  r: T;  i := 0;  e := 0;
  BEGIN
    IF Text.HasWideChars (t) THEN RETURN FALSE END;
    b := ToChars (t);
    WHILE e < n AND NOT b[e] IN Chars {':', '/', '?', '#'} DO INC (e) END;
    IF e < n AND b[e] = ':' THEN
      IF e = 0 OR NOT b[0] IN Alpha THEN RETURN FALSE END;
      FOR k := 1 TO e - 1 DO
        IF NOT b[k] IN SchemeChars THEN RETURN FALSE END;
      END;
      r.scheme := Sub (b^, 0, e);
      i := e + 1;
    END;

    IF i + 1 < n AND b[i] = '/' AND b[i + 1] = '/' THEN
      e := i + 2;
      WHILE e < n AND NOT b[e] IN Chars {'/', '?', '#'} DO INC (e) END;
      IF NOT ParseAuthority (b^, i + 2, e, r) THEN RETURN FALSE END;
      i := e;
    END;

    e := i;
    WHILE e < n AND NOT b[e] IN Chars {'?', '#'} DO INC (e) END;
    IF NOT Valid (b^, i, e, PathChars) THEN RETURN FALSE END;
    r.path := Sub (b^, i, e);
    i := e;

    IF i < n AND b[i] = '?' THEN
      e := i + 1;
      WHILE e < n AND b[e] # '#' DO INC (e) END;
      IF NOT Valid (b^, i + 1, e, QueryChars) THEN RETURN FALSE END;
      r.query := Sub (b^, i + 1, e);
      i := e;
    END;

    IF i < n THEN
      IF NOT Valid (b^, i + 1, n, QueryChars) THEN RETURN FALSE END;
      r.fragment := Sub (b^, i + 1, n);
    END;

    u := r;
    RETURN TRUE;
  END Parse;

PROCEDURE Format (READONLY u: T): TEXT =
  VAR wr := TextWr.New ();
  BEGIN
    IF u.scheme # NIL THEN
      Wr.PutText (wr, u.scheme);
      Wr.PutChar (wr, ':');
    END;
    IF u.host # NIL THEN
      Wr.PutText (wr, "//");
      IF u.userinfo # NIL THEN
        Wr.PutText (wr, u.userinfo);
        Wr.PutChar (wr, '@');
      END;
      Wr.PutText (wr, u.host);
      IF u.port >= 0 THEN
        Wr.PutChar (wr, ':');
        PutInt (wr, u.port);
      END;
    END;
    Wr.PutText (wr, u.path);
    IF u.query # NIL THEN
      Wr.PutChar (wr, '?');
      Wr.PutText (wr, u.query);
    END;
    IF u.fragment # NIL THEN
      Wr.PutChar (wr, '#');
      Wr.PutText (wr, u.fragment);
    END;
    RETURN TextWr.ToText (wr);
  END Format;

PROCEDURE PutInt (wr: Wr.T; v: CARDINAL) =
  BEGIN
    IF v >= 10 THEN PutInt (wr, v DIV 10) END;
    Wr.PutChar (wr, VAL (ORD ('0') + v MOD 10, CHAR));
  END PutInt;

(* Section 5.2.4 of RFC 3986. *)
PROCEDURE RemoveDots (p: TEXT): TEXT =
  VAR
    n := Text.Length (p);
    b := ToChars (p);
    o := NEW (REF ARRAY OF CHAR, n);
    i, m := 0;

  PROCEDURE At (k: INTEGER; c: CHAR): BOOLEAN =
    BEGIN
      RETURN k < n AND b[k] = c;
    END At;

  PROCEDURE DropLast () =
    BEGIN
      WHILE m > 0 AND o[m - 1] # '/' DO DEC (m) END;
      IF m > 0 THEN DEC (m) END;
    END DropLast;

  BEGIN
    WHILE i < n DO
      IF At (i, '.') AND At (i + 1, '.') AND At (i + 2, '/') THEN
        INC (i, 3);
      ELSIF At (i, '.') AND At (i + 1, '/') THEN
        INC (i, 2);
      ELSIF At (i, '/') AND At (i + 1, '.') AND At (i + 2, '/') THEN
        INC (i, 2);
      ELSIF At (i, '/') AND At (i + 1, '.') AND i + 2 = n THEN
        b[i + 1] := '/';
        INC (i);
      ELSIF At (i, '/') AND At (i + 1, '.') AND At (i + 2, '.') AND At (i + 3, '/') THEN
        INC (i, 3);
        DropLast ();
      ELSIF At (i, '/') AND At (i + 1, '.') AND At (i + 2, '.') AND i + 3 = n THEN
        b[i + 2] := '/';
        INC (i, 2);
        DropLast ();
      ELSIF At (i, '.') AND (i + 1 = n OR At (i + 1, '.') AND i + 2 = n) THEN
        i := n;
      ELSE
        REPEAT
          o[m] := b[i];
          INC (m);
          INC (i);
        UNTIL i >= n OR b[i] = '/';
      END;
    END;
    RETURN Text.FromChars (SUBARRAY (o^, 0, m));
  END RemoveDots;

PROCEDURE Merge (READONLY base: T; path: TEXT): TEXT =
  VAR slash: INTEGER;
  BEGIN
    IF base.host # NIL AND Text.Empty (base.path) THEN RETURN "/" & path END;
    slash := Text.FindCharR (base.path, '/');
    IF slash < 0 THEN RETURN path END;
    RETURN Text.Sub (base.path, 0, slash + 1) & path;
  END Merge;

PROCEDURE Resolve (READONLY base, ref: T): T =
  VAR r: T;
  BEGIN
    IF ref.scheme # NIL THEN
      r := ref;
      r.path := RemoveDots (ref.path);
    ELSE
      IF ref.host # NIL THEN
        r := ref;
        r.path := RemoveDots (ref.path);
      ELSE
        r.userinfo := base.userinfo;
        r.host := base.host;
        r.port := base.port;
        IF Text.Empty (ref.path) THEN
          r.path := base.path;
          IF ref.query # NIL THEN r.query := ref.query ELSE r.query := base.query END;
        ELSE
          IF Text.GetChar (ref.path, 0) = '/' THEN
            r.path := RemoveDots (ref.path);
          ELSE
            r.path := RemoveDots (Merge (base, ref.path));
          END;
          r.query := ref.query;
        END;
      END;
      r.scheme := base.scheme;
    END;
    r.fragment := ref.fragment;
    RETURN r;
  END Resolve;

PROCEDURE HexValue (c: CHAR): INTEGER =
  BEGIN
    CASE c OF
    | '0' .. '9' => RETURN ORD (c) - ORD ('0');
    | 'A' .. 'F' => RETURN ORD (c) - ORD ('A') + 10;
    | 'a' .. 'f' => RETURN ORD (c) - ORD ('a') + 10;
    ELSE RETURN -1;
    END;
  END HexValue;

(* "t" with the escapes of section 6.2.2 of RFC 3986, and with its other
   characters in lower case if "lower". *)
PROCEDURE NormalizeText (t: TEXT; lower: BOOLEAN): TEXT =
  VAR
    n: INTEGER;
    b, o: REF ARRAY OF CHAR;
    i, m := 0;
    high, low: INTEGER;
    c: CHAR;

  PROCEDURE Put (x: CHAR) =
    BEGIN
      IF lower AND x >= 'A' AND x <= 'Z' THEN x := VAL (ORD (x) + 32, CHAR) END;
      o[m] := x;
      INC (m);
    END Put;

  BEGIN
    IF t = NIL THEN RETURN NIL END;
    n := Text.Length (t);
    b := ToChars (t);
    o := NEW (REF ARRAY OF CHAR, n);
    WHILE i < n DO
      c := b[i];
      high := -1;
      low := -1;
      IF c = '%' AND i + 2 < n THEN
        high := HexValue (b[i + 1]);
        low := HexValue (b[i + 2]);
      END;
      IF high >= 0 AND low >= 0 THEN
        c := VAL (high * 16 + low, CHAR);
        IF c IN Unreserved THEN
          Put (c);
        ELSE
          o[m] := '%';
          o[m + 1] := HexUpper[high];
          o[m + 2] := HexUpper[low];
          INC (m, 3);
        END;
        INC (i, 3);
      ELSE
        Put (c);
        INC (i);
      END;
    END;
    RETURN Text.FromChars (SUBARRAY (o^, 0, m));
  END NormalizeText;

PROCEDURE Normalize (READONLY u: T): T =
  VAR r := u;
  BEGIN
    r.scheme := NormalizeText (u.scheme, TRUE);
    r.userinfo := NormalizeText (u.userinfo, FALSE);
    r.host := NormalizeText (u.host, TRUE);
    r.path := NormalizeText (u.path, FALSE);
    IF NOT Text.Empty (r.path) AND Text.GetChar (r.path, 0) = '/' THEN
      r.path := RemoveDots (r.path);
    END;
    r.query := NormalizeText (u.query, FALSE);
    r.fragment := NormalizeText (u.fragment, FALSE);
    RETURN r;
  END Normalize;

PROCEDURE ParseQuery (q: TEXT; VAR r: Pairs; mode := QueryMode.Form): BOOLEAN =
  VAR n, count, start, stop, eq, k: INTEGER;  p: Pairs;  name, value: TEXT;
  BEGIN
    IF q = NIL THEN
      r := NEW (Pairs, 0);
      RETURN TRUE;
    END;
    IF Text.HasWideChars (q) THEN RETURN FALSE END;
    n := Text.Length (q);
    count := 1;
    start := Text.FindChar (q, '&');
    WHILE start >= 0 DO
      INC (count);
      start := Text.FindChar (q, '&', start + 1);
    END;
    p := NEW (Pairs, count);
    k := 0;
    start := 0;
    WHILE start <= n DO
      stop := Text.FindChar (q, '&', start);
      IF stop < 0 THEN stop := n END;
      IF stop > start THEN
        eq := Text.FindChar (q, '=', start);
        IF eq < 0 OR eq > stop THEN eq := stop END;
        name := Text.Sub (q, start, eq - start);
        IF eq < stop THEN value := Text.Sub (q, eq + 1, stop - eq - 1) ELSE value := "" END;
        IF mode = QueryMode.Form THEN
          IF NOT Unescape (name, name, form := TRUE)
               OR NOT Unescape (value, value, form := TRUE) THEN
            RETURN FALSE;
          END;
        END;
        p[k] := Pair {name, value};
        INC (k);
      END;
      start := stop + 1;
    END;
    r := NEW (Pairs, k);
    r^ := SUBARRAY (p^, 0, k);
    RETURN TRUE;
  END ParseQuery;

PROCEDURE FormatQuery (p: Pairs; mode := QueryMode.Form): TEXT =
  VAR wr := TextWr.New ();

  PROCEDURE Put (t: TEXT) =
    BEGIN
      IF mode = QueryMode.Form THEN
        Wr.PutText (wr, Escape (t, form := TRUE));
      ELSE
        <*ASSERT NOT Text.HasWideChars (t)*>
        Wr.PutText (wr, t);
      END;
    END Put;

  BEGIN
    FOR i := 0 TO LAST (p^) DO
      IF i > 0 THEN Wr.PutChar (wr, '&') END;
      Put (p[i].name);
      Wr.PutChar (wr, '=');
      Put (p[i].value);
    END;
    RETURN TextWr.ToText (wr);
  END FormatQuery;

PROCEDURE Escape (t: TEXT; READONLY safe := Unreserved; form := FALSE): TEXT =
  VAR
    n := Text.Length (t);
    b: REF ARRAY OF CHAR;
    o := NEW (REF ARRAY OF CHAR, 3 * n);
    m := 0;
    c: CHAR;
  BEGIN
    <*ASSERT NOT Text.HasWideChars (t)*>
    b := ToChars (t);
    FOR i := 0 TO n - 1 DO
      c := b[i];
      IF form AND c = ' ' THEN
        o[m] := '+';
        INC (m);
      ELSIF c IN safe AND c # '%' AND NOT (form AND c = '+') THEN
        o[m] := c;
        INC (m);
      ELSE
        o[m] := '%';
        o[m + 1] := HexUpper[ORD (c) DIV 16];
        o[m + 2] := HexUpper[ORD (c) MOD 16];
        INC (m, 3);
      END;
    END;
    RETURN Text.FromChars (SUBARRAY (o^, 0, m));
  END Escape;

PROCEDURE Unescape (t: TEXT; VAR r: TEXT; form := FALSE): BOOLEAN =
  VAR
    n := Text.Length (t);
    b: REF ARRAY OF CHAR;
    o := NEW (REF ARRAY OF CHAR, n);
    i, m := 0;
    high, low: INTEGER;
    c: CHAR;
  BEGIN
    IF Text.HasWideChars (t) THEN RETURN FALSE END;
    b := ToChars (t);
    WHILE i < n DO
      c := b[i];
      INC (i);
      IF c = '%' THEN
        IF i + 2 > n THEN RETURN FALSE END;
        high := HexValue (b[i]);
        low := HexValue (b[i + 1]);
        IF high < 0 OR low < 0 THEN RETURN FALSE END;
        c := VAL (high * 16 + low, CHAR);
        INC (i, 2);
      ELSIF form AND c = '+' THEN
        c := ' ';
      END;
      o[m] := c;
      INC (m);
    END;
    r := Text.FromChars (SUBARRAY (o^, 0, m));
    RETURN TRUE;
  END Unescape;

BEGIN
END URI.
