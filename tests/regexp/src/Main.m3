(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The program of tools/regexp_test.py.

     batch IN OUT        carries out the lines of IN, one result line each in OUT
     fixed               calls with readable output, checked by hand
     deep N main|thread  "(a|b)*c" on N letters, on the main thread or another
     threads N           N threads search with one pattern; the results agree
     speed               matches per second, with and without a limit

   Text in the lines is "w" and four digits for each element, so that this
   program does not rely on what it tests. Options are a number, the bits of
   "Regexp.Option" in order. *)

MODULE Main;

IMPORT FileRd, FileWr, Fmt, IO, Params, Rd, Regexp, Scan, Text, TextWr, Thread, Time, Wr, Word;

<*FATAL ANY*>

CONST Digits = "0123456789abcdef";

TYPE Tokens = REF ARRAY OF TEXT;

PROCEDURE Nibble (c: CHAR): INTEGER =
  BEGIN
    IF c >= 'a' THEN RETURN ORD (c) - ORD ('a') + 10 ELSE RETURN ORD (c) - ORD ('0') END;
  END Nibble;

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

PROCEDURE Show (t: TEXT): TEXT =
  VAR n := Text.Length (t);  wr := TextWr.New ();  c: INTEGER;
  BEGIN
    IF t = NIL THEN RETURN "-" END;
    Wr.PutChar (wr, 'w');
    FOR i := 0 TO n - 1 DO
      c := ORD (Text.GetWideChar (t, i));
      Wr.PutChar (wr, Text.GetChar (Digits, c DIV 4096));
      Wr.PutChar (wr, Text.GetChar (Digits, c DIV 256 MOD 16));
      Wr.PutChar (wr, Text.GetChar (Digits, c DIV 16 MOD 16));
      Wr.PutChar (wr, Text.GetChar (Digits, c MOD 16));
    END;
    RETURN TextWr.ToText (wr);
  END Show;

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

PROCEDURE Opts (t: TEXT): Regexp.Options =
  VAR bits := Int (t);  r := Regexp.Options{};
  BEGIN
    FOR o := FIRST (Regexp.Option) TO LAST (Regexp.Option) DO
      IF Word.And (bits, Word.LeftShift (1, ORD (o))) # 0 THEN r := r + Regexp.Options{o} END;
    END;
    RETURN r;
  END Opts;

(* The bounds of all groups of "m": "a:b" each, separated by commas. *)
PROCEDURE Bounds (re: Regexp.T; m: Regexp.Match): TEXT =
  VAR wr := TextWr.New ();
  BEGIN
    FOR g := 0 TO re.groups () DO
      IF g > 0 THEN Wr.PutChar (wr, ',') END;
      Wr.PutText (wr, Fmt.Int (m.start (g)) & ":" & Fmt.Int (m.stop (g)));
      IF m.group (g) = NIL THEN
        <*ASSERT m.start (g) = -1 AND m.stop (g) = -1*>
      ELSE
        <*ASSERT Text.Equal (m.group (g), Text.Sub (m.text (), m.start (g), m.stop (g) - m.start (g)))*>
      END;
    END;
    RETURN TextWr.ToText (wr);
  END Bounds;

PROCEDURE Same (re: Regexp.T; a, b: Regexp.Match): BOOLEAN =
  BEGIN
    IF a = NIL OR b = NIL THEN RETURN a = b END;
    FOR g := 0 TO re.groups () DO
      IF a.start (g) # b.start (g) OR a.stop (g) # b.stop (g) THEN RETURN FALSE END;
    END;
    RETURN TRUE;
  END Same;

(* All matches from "start", and Next asked again of each earlier one. *)
PROCEDURE FindAll (re: Regexp.T; text: TEXT; start: CARDINAL): TEXT =
  VAR wr := TextWr.New ();  m: Regexp.Match;  all := NEW (REF ARRAY OF Regexp.Match, 16);  n := 0;
      more: REF ARRAY OF Regexp.Match;
  BEGIN
    TRY
      m := Regexp.Find (re, text, start);
      IF m = NIL THEN RETURN "none" END;
      WHILE m # NIL DO
        IF n > 0 THEN Wr.PutChar (wr, ';') END;
        Wr.PutText (wr, Bounds (re, m));
        IF n = NUMBER (all^) THEN
          more := NEW (REF ARRAY OF Regexp.Match, 2 * n);
          SUBARRAY (more^, 0, n) := all^;
          all := more;
        END;
        all[n] := m;
        INC (n);
        m := Regexp.Next (m);
      END;
      IF Regexp.Next (all[n - 1]) # NIL THEN Wr.PutText (wr, " last-again") END;
      FOR i := n - 2 TO 0 BY -1 DO
        IF NOT Same (re, Regexp.Next (all[i]), all[i + 1]) THEN Wr.PutText (wr, " again" & Fmt.Int (i)) END;
      END;
    EXCEPT
      Regexp.TooComplex => Wr.PutText (wr, " TooComplex");
    END;
    RETURN TextWr.ToText (wr);
  END FindAll;

TYPE Angle = Regexp.Replacer OBJECT OVERRIDES apply := ApplyAngle END;

PROCEDURE ApplyAngle (<*UNUSED*> self: Angle; m: Regexp.Match): TEXT =
  BEGIN
    RETURN "<" & m.group () & ">";
  END ApplyAngle;

PROCEDURE Do (a: Tokens): TEXT =
  VAR op := a[0];  re: Regexp.T;  m: Regexp.Match;  parts: REF ARRAY OF TEXT;  wr: TextWr.T;
  BEGIN
    IF Text.Equal (op, "q") THEN
      RETURN Show (Regexp.Quote (Wide (a[1]))) & " " & Show (Regexp.QuoteReplacement (Wide (a[1])));
    END;
    TRY
      re := Regexp.Compile (Wide (a[3]), Opts (a[1]), Int (a[2]));
    EXCEPT
      Regexp.Error (e) => RETURN "error " & Show (e);
    END;
    TRY
      IF Text.Equal (op, "c") THEN
        RETURN "ok " & Fmt.Int (re.groups ());
      ELSIF Text.Equal (op, "g") THEN
        RETURN Fmt.Int (re.groupNumber (Wide (a[4])));
      ELSIF Text.Equal (op, "f") THEN
        RETURN FindAll (re, Wide (a[4]), Int (a[5]));
      ELSIF Text.Equal (op, "m") THEN
        m := Regexp.FullMatch (re, Wide (a[4]));
        IF m = NIL THEN
          <*ASSERT NOT Regexp.Matches (re, Wide (a[4]))*>
          RETURN "none";
        END;
        <*ASSERT Regexp.Matches (re, Wide (a[4])) AND Regexp.Next (m) = NIL*>
        RETURN Bounds (re, m);
      ELSIF Text.Equal (op, "r") THEN
        TRY
          RETURN Show (Regexp.Replace (re, Wide (a[4]), Wide (a[5]), Int (a[6]) # 0));
        EXCEPT
          Regexp.Error (e) => RETURN "error " & Show (e);
        END;
      ELSIF Text.Equal (op, "w") THEN
        RETURN Show (Regexp.ReplaceWith (re, Wide (a[4]), NEW (Angle), Int (a[5]) # 0));
      ELSIF Text.Equal (op, "s") THEN
        parts := Regexp.Split (re, Wide (a[4]), Int (a[5]));
        wr := TextWr.New ();
        Wr.PutText (wr, Fmt.Int (NUMBER (parts^)));
        FOR i := 0 TO LAST (parts^) DO Wr.PutText (wr, " " & Show (parts[i])) END;
        RETURN TextWr.ToText (wr);
      END;
    EXCEPT
      Regexp.TooComplex => RETURN "TooComplex";
    END;
    RETURN "bad command";
  END Do;

PROCEDURE Batch (in, out: TEXT) =
  VAR rd := FileRd.Open (in);  wr := FileWr.Open (out);
  BEGIN
    WHILE NOT Rd.EOF (rd) DO
      Wr.PutText (wr, Do (Split (Rd.GetLine (rd))) & "\n");
    END;
    Rd.Close (rd);
    Wr.Close (wr);
  END Batch;

(* Fixed calls, with readable output. *)

PROCEDURE Esc (t: TEXT): TEXT =
  VAR wr := TextWr.New ();  c: WIDECHAR;
  BEGIN
    IF t = NIL THEN RETURN "NIL" END;
    Wr.PutChar (wr, '"');
    FOR i := 0 TO Text.Length (t) - 1 DO
      c := Text.GetWideChar (t, i);
      IF c = W'\n' THEN
        Wr.PutText (wr, "\\n");
      ELSIF c = W'"' OR c = W'\\' THEN
        Wr.PutChar (wr, '\\');
        Wr.PutChar (wr, VAL (ORD (c), CHAR));
      ELSIF ORD (c) >= 32 AND ORD (c) < 127 THEN
        Wr.PutChar (wr, VAL (ORD (c), CHAR));
      ELSE
        Wr.PutText (wr, "\\u" & Fmt.Pad (Fmt.Int (ORD (c), 16), 4, '0'));
      END;
    END;
    Wr.PutChar (wr, '"');
    RETURN TextWr.ToText (wr);
  END Esc;

PROCEDURE Out (t: TEXT) =
  BEGIN
    IO.Put (t & "\n");
  END Out;

PROCEDURE Re (p: TEXT; o := Regexp.Options{}): Regexp.T =
  BEGIN
    TRY
      RETURN Regexp.Compile (p, o);
    EXCEPT
      Regexp.Error (e) => Out ("  Compile " & Esc (p) & ": Error " & Esc (e)); RETURN NIL;
    END;
  END Re;

PROCEDURE ShowMatch (re: Regexp.T; m: Regexp.Match): TEXT =
  VAR wr := TextWr.New ();
  BEGIN
    IF m = NIL THEN RETURN "NIL" END;
    FOR g := 0 TO re.groups () DO
      IF g > 0 THEN Wr.PutText (wr, " ") END;
      Wr.PutText (wr, Fmt.Int (g) & "=" & Fmt.Int (m.start (g)) & ".." & Fmt.Int (m.stop (g)) & " "
                        & Esc (m.group (g)));
    END;
    RETURN TextWr.ToText (wr);
  END ShowMatch;

PROCEDURE All (p, t: TEXT; o := Regexp.Options{}; start := 0) =
  VAR re := Re (p, o);  m: Regexp.Match;
  BEGIN
    IF re = NIL THEN RETURN END;
    Out ("  " & Esc (p) & " in " & Esc (t) & " from " & Fmt.Int (start) & ":");
    m := Regexp.Find (re, t, start);
    IF m = NIL THEN Out ("    none") END;
    WHILE m # NIL DO
      Out ("    " & ShowMatch (re, m));
      m := Regexp.Next (m);
    END;
  END All;

PROCEDURE Rep (p, t, r: TEXT; all := TRUE) =
  VAR re := Re (p);
  BEGIN
    IF re = NIL THEN RETURN END;
    TRY
      Out ("  Replace " & Esc (p) & " in " & Esc (t) & " by " & Esc (r) & " all " & Fmt.Bool (all) & ": "
             & Esc (Regexp.Replace (re, t, r, all)));
    EXCEPT
      Regexp.Error (e) => Out ("  Replace " & Esc (p) & " by " & Esc (r) & ": Error " & Esc (e));
    END;
  END Rep;

PROCEDURE Spl (p, t: TEXT; limit := 0) =
  VAR re := Re (p);  parts: REF ARRAY OF TEXT;  wr := TextWr.New ();
  BEGIN
    IF re = NIL THEN RETURN END;
    parts := Regexp.Split (re, t, limit);
    FOR i := 0 TO LAST (parts^) DO Wr.PutText (wr, " " & Esc (parts[i])) END;
    Out ("  Split " & Esc (p) & " " & Esc (t) & " limit " & Fmt.Int (limit) & ": " & Fmt.Int (NUMBER (parts^))
           & TextWr.ToText (wr));
  END Spl;

TYPE Upper = Regexp.Replacer OBJECT OVERRIDES apply := ApplyUpper END;

PROCEDURE ApplyUpper (<*UNUSED*> self: Upper; m: Regexp.Match): TEXT =
  VAR t := m.group (1);  r := NEW (REF ARRAY OF CHAR, Text.Length (t));
  BEGIN
    FOR i := 0 TO LAST (r^) DO
      r[i] := Text.GetChar (t, i);
      IF 'a' <= r[i] AND r[i] <= 'z' THEN r[i] := VAL (ORD (r[i]) - 32, CHAR) END;
    END;
    RETURN Text.FromChars (r^) & "!";
  END ApplyUpper;

EXCEPTION Stop;

TYPE Stopper = Regexp.Replacer OBJECT OVERRIDES apply := ApplyStop END;

PROCEDURE ApplyStop (<*UNUSED*> self: Stopper; <*UNUSED*> m: Regexp.Match): TEXT RAISES {Stop} =
  BEGIN
    RAISE Stop;
  END ApplyStop;

PROCEDURE Fixed () =
  VAR re: Regexp.T;  m: Regexp.Match;
  BEGIN
    Out ("groups");
    All ("(\\d+)-(\\d+)", "tel 030-1234, 040-99");
    All ("(a)|(b)", "abc");
    All ("(?<year>\\d{4})-(?<month>\\d\\d)", "on 2026-11 and 1999-01");
    re := Re ("(?<year>\\d{4})-(?<month>\\d\\d)");
    Out ("  groups " & Fmt.Int (re.groups ()) & ", year " & Fmt.Int (re.groupNumber ("year")) & ", month "
           & Fmt.Int (re.groupNumber ("month")) & ", day " & Fmt.Int (re.groupNumber ("day")));
    Out ("  pattern " & Esc (re.pattern ()) & ", limit " & Fmt.Int (re.limit ()));
    All ("(a*)+b", "aab");
    All ("((a)|b)+", "ab");
    m := Regexp.Find (Re ("x"), "x");
    Out ("  text of a match " & Esc (m.text ()));

    Out ("empty matches");
    All ("a*", "baab");
    All ("", "ab");
    All ("\\b", "ab cd");
    All ("x*", "");

    Out ("start");
    All ("\\w+", "one two", start := 2);
    All ("(?<=o)\\w", "one two", start := 1);
    All ("^\\w", "one two", start := 1);
    All ("\\G\\w", "one two", start := 4);
    All ("\\Ga", "aab");
    All ("\\Ga*", "baab");
    All ("a", "a", start := 1);
    All ("$", "a", start := 1);
    Out ("  start beyond: " & Fmt.Bool (Regexp.Find (Re ("a"), "a", 2) = NIL));

    Out ("options");
    All ("abc", "ABC aBc", Regexp.Options{Regexp.Option.IgnoreCase});
    All (W"\x00e9", W"\x00c9", Regexp.Options{Regexp.Option.IgnoreCase});
    All (W"\x00e9", W"\x00c9", Regexp.Options{Regexp.Option.IgnoreCase, Regexp.Option.Unicode});
    All ("\\w+", W"na\x00efve");
    All ("\\w+", W"na\x00efve", Regexp.Options{Regexp.Option.Unicode});
    All ("^\\w", "a\nb");
    All ("^\\w", "a\nb", Regexp.Options{Regexp.Option.Multiline});
    All ("a.b", "a\nb");
    All ("a.b", "a\nb", Regexp.Options{Regexp.Option.DotAll});
    All ("a.b(", "xa.b(", Regexp.Options{Regexp.Option.Literal});
    All ("a b # the letters\n c", "abc", Regexp.Options{Regexp.Option.Comments});
    All ("(?i)x", "X");
    re := Re ("x", Regexp.Options{Regexp.Option.DotAll, Regexp.Option.Comments});
    Out ("  options DotAll, Comments: " & Fmt.Bool (re.options () = Regexp.Options{Regexp.Option.DotAll,
                                                                                 Regexp.Option.Comments}));

    Out ("whole text");
    re := Re ("(\\d+)-(\\d+)");
    Out ("  Matches \"12-34\" " & Fmt.Bool (Regexp.Matches (re, "12-34")) & ", \"12-34x\" "
           & Fmt.Bool (Regexp.Matches (re, "12-34x")));
    Out ("  FullMatch \"12-34\": " & ShowMatch (re, Regexp.FullMatch (re, "12-34")));
    Out ("  FullMatch \"12-34x\": " & ShowMatch (re, Regexp.FullMatch (re, "12-34x")));

    Out ("wide characters");
    All (".", W"a\x00e9\x4e2d\xd83d\xde00b");
    All ("[^a]", W"a\xd83d\xde00");
    All ("\\x{1F600}+", W"\xd83d\xde00\xd83d\xde00");
    All ("\\x{DE00}", W"\xd83d\xde00\xde00");
    All (W"\x4e2d(.)", W"\x4e2d\x6587");

    Out ("replace");
    Rep ("(\\w+)@(\\w+)", "ann@home, bob@work", "$2:$1");
    Rep ("(?<user>\\w+)@(?<host>\\w+)", "ann@home", "${host}/${user}");
    Rep ("a", "banana", "o", all := FALSE);
    Rep ("a", "banana", "\\$");
    Rep ("(a)|b", "ab", "[$1]");
    Rep ("x*", "abc", "-");
    Rep ("(a)", "a", "$11");
    Rep ("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(k)", "abcdefghijk", "$11-$12");
    Rep ("a", "b", "$1");
    Rep ("(a)", "b", "$2");
    Rep ("a", "b", "${x}");
    Rep ("a", "b", "${1x}");
    Rep ("a", "b", "${}");
    Rep ("a", "b", "${x");
    Rep ("a", "b", "x$");
    Rep ("a", "b", "x\\");
    Rep ("a", "b", "$x");
    Rep ("a", "a", Regexp.QuoteReplacement ("$1\\"));
    Out ("  ReplaceWith: " & Esc (Regexp.ReplaceWith (Re ("\\b(\\w)"), "one two three", NEW (Upper))));
    Out ("  ReplaceWith first: " & Esc (Regexp.ReplaceWith (Re ("\\b(\\w)"), "one two", NEW (Upper), all := FALSE)));
    Out ("  ReplaceWith none: " & Esc (Regexp.ReplaceWith (Re ("\\d"), "one", NEW (Upper))));
    TRY
      EVAL Regexp.ReplaceWith (Re ("o"), "one", NEW (Stopper));
      Out ("  ReplaceWith raising: no exception");
    EXCEPT
      Stop => Out ("  ReplaceWith raising: Stop passes through");
    END;

    Out ("split");
    Spl (",", "a,b,,c,,");
    Spl (",", "a,b,,c,,", -1);
    Spl (",", "a,b,,c,,", 2);
    Spl (",", "a,b,,c,,", 1);
    Spl (",", "");
    Spl (",", ",");
    Spl ("", "abc");
    Spl ("(?=b)", "abab");
    Spl ("\\s*;\\s*", "x ; y;z ;");

    Out ("quote");
    re := Re (Regexp.Quote ("a.b*c\\E(d"));
    Out ("  Quote: " & Esc (re.pattern ()) & " matches itself " & Fmt.Bool (Regexp.Matches (re, "a.b*c\\E(d"))
           & ", \"aXbbc\\\\E(d\" " & Fmt.Bool (Regexp.Matches (re, "aXbbc\\E(d")));

    Out ("bad patterns");
    EVAL Re ("(a");
    EVAL Re ("a)");
    EVAL Re ("[a");
    EVAL Re ("a{2,1}");
    EVAL Re ("*a");
    EVAL Re ("\\k<x>");
    EVAL Re ("(?<1x>a)");
    EVAL Re ("\\p{Nope}");
    EVAL Re ("a\\");
    EVAL Re ("(?<n>a)(?<n>b)");
  END Fixed;

(* The stack: "(a|b)*c" on n letters. *)

TYPE DeepClosure = Thread.Closure OBJECT n: INTEGER; r: TEXT OVERRIDES apply := DeepApply END;

PROCEDURE Deep (n: INTEGER): TEXT =
  VAR re: Regexp.T;  t := NEW (REF ARRAY OF CHAR, n);  start := Time.Now ();  r: TEXT;
  BEGIN
    FOR i := 0 TO n - 1 DO t[i] := 'a' END;
    re := Regexp.Compile ("(a|b)*c", limit := 0);
    TRY
      IF Regexp.Find (re, Text.FromChars (t^)) = NIL THEN r := "none" ELSE r := "found" END;
    EXCEPT
      Regexp.TooComplex => r := "TooComplex";
    END;
    (* the pattern works again *)
    <*ASSERT Regexp.Find (re, "ababc").stop () = 5*>
    RETURN r & " " & Fmt.LongReal (Time.Now () - start, Fmt.Style.Fix, 2);
  END Deep;

PROCEDURE DeepApply (self: DeepClosure): REFANY =
  BEGIN
    self.r := Deep (self.n);
    RETURN NIL;
  END DeepApply;

(* Threads that search with one pattern. *)

TYPE Worker = Thread.Closure OBJECT re: Regexp.T; seed: INTEGER; r: TEXT OVERRIDES apply := Work END;

PROCEDURE Texts (seed: INTEGER; i: INTEGER): TEXT =
  CONST Words = ARRAY OF TEXT {"key=1", "x = yes", "a=b=c", "  ", "z= ", "k=v;"};
  VAR wr := TextWr.New ();
  BEGIN
    FOR k := 0 TO 20 DO
      Wr.PutText (wr, Words[(seed * 7 + i * 13 + k * k) MOD NUMBER (Words)]);
      Wr.PutText (wr, "; ");
    END;
    RETURN TextWr.ToText (wr);
  END Texts;

PROCEDURE Work (self: Worker): REFANY =
  VAR wr := TextWr.New ();  m: Regexp.Match;
  BEGIN
    FOR i := 0 TO 2000 DO
      m := Regexp.Find (self.re, Texts (self.seed, i));
      WHILE m # NIL DO
        Wr.PutText (wr, m.group (1) & "=" & m.group (2) & ";");
        m := Regexp.Next (m);
      END;
      Wr.PutText (wr, Regexp.Replace (self.re, Texts (self.seed, i), "$2=$1"));
    END;
    self.r := TextWr.ToText (wr);
    RETURN NIL;
  END Work;

PROCEDURE Threads (n: INTEGER) =
  VAR re := Regexp.Compile ("(\\w+)\\s*=\\s*([^;]*)");  w := NEW (REF ARRAY OF Worker, n);
      t := NEW (REF ARRAY OF Thread.T, n);  alone: Worker;  ok := TRUE;
  BEGIN
    FOR i := 0 TO n - 1 DO
      w[i] := NEW (Worker, re := re, seed := i MOD 3);
      t[i] := Thread.Fork (w[i]);
    END;
    FOR i := 0 TO n - 1 DO EVAL Thread.Join (t[i]) END;
    FOR s := 0 TO 2 DO
      alone := NEW (Worker, re := re, seed := s);
      EVAL Work (alone);
      FOR i := 0 TO n - 1 DO
        IF w[i].seed = s AND NOT Text.Equal (w[i].r, alone.r) THEN ok := FALSE END;
      END;
    END;
    IF ok THEN Out ("threads agree " & Fmt.Int (n)) ELSE Out ("threads differ") END;
  END Threads;

PROCEDURE Speed () =
  VAR wr := TextWr.New ();  text: TEXT;  start: Time.T;  count: INTEGER;  m: Regexp.Match;
  BEGIN
    FOR i := 0 TO 20000 DO Wr.PutText (wr, "name" & Fmt.Int (i) & " = value " & Fmt.Int (i * 7) & "; ") END;
    text := TextWr.ToText (wr);
    FOR limit := 0 TO 1 DO
      FOR round := 1 TO 3 DO
        start := Time.Now ();
        count := 0;
        WITH re = Regexp.Compile ("(\\w+)\\s*=\\s*([^;]*)", limit := limit * Regexp.DefaultLimit) DO
          FOR k := 1 TO 10 DO
            m := Regexp.Find (re, text);
            WHILE m # NIL DO INC (count); m := Regexp.Next (m) END;
          END;
        END;
        IF round = 3 THEN
          Out ("limit " & Fmt.Int (limit * Regexp.DefaultLimit) & ": "
                 & Fmt.Int (ROUND (FLOAT (count, LONGREAL) / (Time.Now () - start))) & " matches per second, "
                 & Fmt.Int (ROUND (FLOAT (count, LONGREAL) * 33.0d0 / (Time.Now () - start) / 1.0d6))
                 & " MB of text per second");
        END;
      END;
    END;
  END Speed;

VAR closure: DeepClosure;

BEGIN
  IF Text.Equal (Params.Get (1), "batch") THEN
    Batch (Params.Get (2), Params.Get (3));
  ELSIF Text.Equal (Params.Get (1), "fixed") THEN
    Fixed ();
  ELSIF Text.Equal (Params.Get (1), "deep") THEN
    IF Text.Equal (Params.Get (3), "main") THEN
      Out (Deep (Int (Params.Get (2))));
    ELSE
      closure := NEW (DeepClosure, n := Int (Params.Get (2)));
      EVAL Thread.Join (Thread.Fork (closure));
      Out (closure.r);
    END;
  ELSIF Text.Equal (Params.Get (1), "threads") THEN
    Threads (Int (Params.Get (2)));
  ELSIF Text.Equal (Params.Get (1), "speed") THEN
    Speed ();
  END;
END Main.
