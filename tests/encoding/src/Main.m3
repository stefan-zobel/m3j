(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The program of tools/encoding_test.py.

     batch IN OUT    carries out the lines of IN, one result line each in OUT
     assert KIND     breaks a rule whose breach is a checked runtime error

   Bytes in the lines are "x" and two digits each, Unicode text "w" and four
   digits for each element, so that this program does not rely on what it
   tests. A missing part is "-". *)

MODULE Main;

IMPORT Base64, FileRd, FileWr, Fmt, Hex, Params, RFC3339, Rd, Scan, Text, TextWr, URI, Utf8, Wr;

<*FATAL ANY*>

CONST Digits = "0123456789abcdef";

TYPE Tokens = REF ARRAY OF TEXT;

PROCEDURE Nibble (c: CHAR): INTEGER =
  BEGIN
    IF c >= 'a' THEN RETURN ORD (c) - ORD ('a') + 10 ELSE RETURN ORD (c) - ORD ('0') END;
  END Nibble;

PROCEDURE Bytes (t: TEXT): TEXT =
  VAR n := (Text.Length (t) - 1) DIV 2;  r := NEW (REF ARRAY OF CHAR, n);
  BEGIN
    <*ASSERT Text.GetChar (t, 0) = 'x'*>
    FOR i := 0 TO n - 1 DO
      r[i] := VAL (Nibble (Text.GetChar (t, 2 * i + 1)) * 16 + Nibble (Text.GetChar (t, 2 * i + 2)), CHAR);
    END;
    RETURN Text.FromChars (r^);
  END Bytes;

PROCEDURE Wide (t: TEXT): TEXT =
  VAR n := (Text.Length (t) - 1) DIV 4;  r := NEW (REF ARRAY OF WIDECHAR, n);  v: INTEGER;
  BEGIN
    <*ASSERT Text.GetChar (t, 0) = 'w'*>
    FOR i := 0 TO n - 1 DO
      v := 0;
      FOR k := 1 TO 4 DO v := v * 16 + Nibble (Text.GetChar (t, 4 * i + k)) END;
      r[i] := VAL (v, WIDECHAR);
    END;
    RETURN Text.FromWideChars (r^);
  END Wide;

(* "wide" says that "t" is to be shown with four digits for each element. *)
PROCEDURE Show (t: TEXT; wide := FALSE): TEXT =
  VAR n := Text.Length (t);  wr := TextWr.New ();  c: INTEGER;
  BEGIN
    IF t = NIL THEN RETURN "-" END;
    IF wide THEN Wr.PutChar (wr, 'w') ELSE Wr.PutChar (wr, 'x') END;
    FOR i := 0 TO n - 1 DO
      c := ORD (Text.GetWideChar (t, i));
      IF wide THEN
        Wr.PutChar (wr, Text.GetChar (Digits, c DIV 4096));
        Wr.PutChar (wr, Text.GetChar (Digits, c DIV 256 MOD 16));
      END;
      Wr.PutChar (wr, Text.GetChar (Digits, c DIV 16 MOD 16));
      Wr.PutChar (wr, Text.GetChar (Digits, c MOD 16));
    END;
    RETURN TextWr.ToText (wr);
  END Show;

PROCEDURE ShowOpt (t: TEXT): TEXT =
  BEGIN
    IF t = NIL THEN RETURN "-" ELSE RETURN Show (t) END;
  END ShowOpt;

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

PROCEDURE Parsed (t: TEXT): URI.T =
  VAR u: URI.T;
  BEGIN
    <*ASSERT URI.Parse (Bytes (t), u)*>
    RETURN u;
  END Parsed;

PROCEDURE Mode (t: TEXT): URI.QueryMode =
  BEGIN
    IF Int (t) = 0 THEN RETURN URI.QueryMode.Raw ELSE RETURN URI.QueryMode.Form END;
  END Mode;

PROCEDURE Alphabet (t: TEXT): Base64.Alphabet =
  BEGIN
    RETURN VAL (Int (t), Base64.Alphabet);
  END Alphabet;

PROCEDURE Safe (t: TEXT): SET OF CHAR =
  BEGIN
    CASE Int (t) OF
    | 0 => RETURN URI.Unreserved;
    | 1 => RETURN URI.Unreserved + SET OF CHAR {'/'};
    | 2 => RETURN URI.Unreserved + SET OF CHAR {'%', '+', ' '};
    ELSE <*ASSERT FALSE*>
    END;
  END Safe;

PROCEDURE Do (a: Tokens): TEXT =
  VAR op := a[0];  r: TEXT;  u: URI.T;  p: URI.Pairs;  t: LONGREAL;  offset: INTEGER;  wr: TextWr.T;
  BEGIN
    IF Text.Equal (op, "he") THEN
      RETURN Show (Hex.Encode (Bytes (a[1])));
    ELSIF Text.Equal (op, "hd") THEN
      IF Hex.Decode (Bytes (a[1]), r) THEN RETURN Show (r) END;
    ELSIF Text.Equal (op, "hdw") THEN
      IF Hex.Decode (Wide (a[1]), r) THEN RETURN "accepted" END;
    ELSIF Text.Equal (op, "bdw") THEN
      IF Base64.Decode (Wide (a[1]), r) THEN RETURN "accepted" END;
    ELSIF Text.Equal (op, "qpw") THEN
      IF URI.ParseQuery (Wide (a[2]), p, Mode (a[1])) THEN RETURN "accepted" END;
    ELSIF Text.Equal (op, "rpw") THEN
      IF RFC3339.Parse (Wide (a[1]), t, offset) THEN RETURN "accepted" END;
    ELSIF Text.Equal (op, "ue") THEN
      RETURN Show (Utf8.Encode (Wide (a[1])));
    ELSIF Text.Equal (op, "ud") THEN
      IF Utf8.Decode (Bytes (a[1]), r) THEN RETURN Show (r, wide := TRUE) END;
    ELSIF Text.Equal (op, "udw") THEN
      IF Utf8.Decode (Wide (a[1]), r) THEN RETURN Show (r, wide := TRUE) END;
    ELSIF Text.Equal (op, "be") THEN
      RETURN Show (Base64.Encode (Bytes (a[3]), Alphabet (a[1]), Int (a[2]) # 0));
    ELSIF Text.Equal (op, "bd") THEN
      IF Base64.Decode (Bytes (a[4]), r, Alphabet (a[1]), VAL (Int (a[2]), Base64.Padding), Int (a[3]) # 0) THEN
        RETURN Show (r);
      END;
    ELSIF Text.Equal (op, "up") THEN
      IF URI.Parse (Bytes (a[1]), u) THEN
        RETURN ShowOpt (u.scheme) & " " & ShowOpt (u.userinfo) & " " & ShowOpt (u.host) & " " & Fmt.Int (u.port)
                 & " " & Show (u.path) & " " & ShowOpt (u.query) & " " & ShowOpt (u.fragment);
      END;
    ELSIF Text.Equal (op, "upw") THEN
      IF URI.Parse (Wide (a[1]), u) THEN RETURN "accepted" END;
    ELSIF Text.Equal (op, "uf") THEN
      IF URI.Parse (Bytes (a[1]), u) THEN RETURN Show (URI.Format (u)) END;
    ELSIF Text.Equal (op, "ur") THEN
      RETURN Show (URI.Format (URI.Resolve (Parsed (a[1]), Parsed (a[2]))));
    ELSIF Text.Equal (op, "un") THEN
      RETURN Show (URI.Format (URI.Normalize (Parsed (a[1]))));
    ELSIF Text.Equal (op, "qp") THEN
      IF URI.ParseQuery (Bytes (a[2]), p, Mode (a[1])) THEN
        wr := TextWr.New ();
        Wr.PutText (wr, "ok");
        FOR i := 0 TO LAST (p^) DO
          Wr.PutText (wr, " " & Show (p[i].name) & " " & Show (p[i].value));
        END;
        RETURN TextWr.ToText (wr);
      END;
    ELSIF Text.Equal (op, "qn") THEN
      IF URI.ParseQuery (NIL, p) THEN RETURN Fmt.Int (NUMBER (p^)) END;
    ELSIF Text.Equal (op, "qf") THEN
      p := NEW (URI.Pairs, (NUMBER (a^) - 2) DIV 2);
      FOR i := 0 TO LAST (p^) DO
        p[i] := URI.Pair {Bytes (a[2 * i + 2]), Bytes (a[2 * i + 3])};
      END;
      RETURN Show (URI.FormatQuery (p, Mode (a[1])));
    ELSIF Text.Equal (op, "ee") THEN
      RETURN Show (URI.Escape (Bytes (a[3]), Safe (a[1]), Int (a[2]) # 0));
    ELSIF Text.Equal (op, "eu") THEN
      IF URI.Unescape (Bytes (a[2]), r, Int (a[1]) # 0) THEN RETURN Show (r) END;
    ELSIF Text.Equal (op, "euw") THEN
      IF URI.Unescape (Wide (a[1]), r) THEN RETURN "accepted" END;
    ELSIF Text.Equal (op, "rf") THEN
      RETURN RFC3339.Format (Scan.LongReal (a[1]), Int (a[2]), Int (a[3]));
    ELSIF Text.Equal (op, "rp") THEN
      IF RFC3339.Parse (Bytes (a[1]), t, offset) THEN
        RETURN Fmt.LongReal (t, Fmt.Style.Fix, 6) & " " & Fmt.Int (offset);
      END;
    ELSE
      <*ASSERT FALSE*>
    END;
    RETURN "no";
  END Do;

PROCEDURE Batch (in, out: TEXT) =
  VAR rd := FileRd.Open (in);  wr := FileWr.Open (out);
  BEGIN
    WHILE NOT Rd.EOF (rd) DO
      Wr.PutText (wr, Do (Split (Rd.GetLine (rd))));
      Wr.PutChar (wr, '\n');
    END;
    Wr.Close (wr);
    Rd.Close (rd);
  END Batch;

PROCEDURE Breach (kind: TEXT) =
  VAR p := NEW (URI.Pairs, 1);  t: TEXT;  wide := W"\x4e2d";
  BEGIN
    p[0] := URI.Pair {wide, "b"};
    IF Text.Equal (kind, "hex") THEN
      t := Hex.Encode (wide);
    ELSIF Text.Equal (kind, "base64") THEN
      t := Base64.Encode (wide);
    ELSIF Text.Equal (kind, "escape") THEN
      t := URI.Escape (wide);
    ELSIF Text.Equal (kind, "query") THEN
      t := URI.FormatQuery (p);
    ELSIF Text.Equal (kind, "rawquery") THEN
      t := URI.FormatQuery (p, URI.QueryMode.Raw);
    ELSIF Text.Equal (kind, "digits") THEN
      t := RFC3339.Format (0.0D0, digits := 7);
    ELSIF Text.Equal (kind, "minutes") THEN
      t := RFC3339.Format (0.0D0, offset := 30);
    ELSIF Text.Equal (kind, "day") THEN
      t := RFC3339.Format (0.0D0, offset := 86400);
    ELSIF Text.Equal (kind, "early") THEN
      t := RFC3339.Format (-62167219201.0D0);
    ELSIF Text.Equal (kind, "late") THEN
      t := RFC3339.Format (253402300800.0D0);
    ELSIF Text.Equal (kind, "lateoffset") THEN
      t := RFC3339.Format (253402300799.0D0, offset := 3600);
    ELSE
      t := "unknown";
    END;
    Wr.PutText (FileWr.OpenAppend ("breach.out"), Fmt.Int (Text.Length (t)));
  END Breach;

BEGIN
  IF Text.Equal (Params.Get (1), "batch") THEN
    Batch (Params.Get (2), Params.Get (3));
  ELSE
    Breach (Params.Get (2));
  END;
END Main.
