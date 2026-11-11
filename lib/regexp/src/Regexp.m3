(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Regexp;

IMPORT Java, M3jRegexp, M3jRegexpScan, Text, Word;

TYPE Bounds = REF ARRAY OF Java.Int;

REVEAL
  T = Public BRANDED "Regexp.T" OBJECT
    text   : TEXT;
    opts   : Options;
    lim    : CARDINAL;
    count  : CARDINAL;
    re     : M3jRegexp.T;
  OVERRIDES
    pattern     := Pattern;
    options     := GetOptions;
    limit       := GetLimit;
    groups      := Groups;
    groupNumber := GroupNumber;
  END;

  Match = MatchPublic BRANDED "Regexp.Match" OBJECT
    s     : TEXT;
    b     : Bounds;
    scan  : M3jRegexpScan.T;    (* NIL for a match of the whole text *)
    index : CARDINAL;           (* the matches of "scan" up to this one *)
  OVERRIDES
    text  := MatchText;
    start := Start;
    stop  := Stop;
    group := Group;
  END;

PROCEDURE Compile (pattern: TEXT; options := Options{};
                   limit: CARDINAL := DefaultLimit): T RAISES {Error} =
  VAR bits := 0;  re: M3jRegexp.T;  err: TEXT;
  BEGIN
    FOR o := FIRST (Option) TO LAST (Option) DO
      IF o IN options THEN bits := Word.Or (bits, Word.LeftShift (1, ORD (o))) END;
    END;
    re := M3jRegexp.compile (pattern, bits, limit);
    err := M3jRegexp.error (re);
    IF err # NIL THEN RAISE Error (err) END;
    RETURN NEW (T, text := pattern, opts := options, lim := limit,
                count := M3jRegexp.groups (re), re := re);
  END Compile;

PROCEDURE Pattern (self: T): TEXT =
  BEGIN
    RETURN self.text;
  END Pattern;

PROCEDURE GetOptions (self: T): Options =
  BEGIN
    RETURN self.opts;
  END GetOptions;

PROCEDURE GetLimit (self: T): CARDINAL =
  BEGIN
    RETURN self.lim;
  END GetLimit;

PROCEDURE Groups (self: T): CARDINAL =
  BEGIN
    RETURN self.count;
  END Groups;

PROCEDURE GroupNumber (self: T; name: TEXT): INTEGER =
  BEGIN
    RETURN M3jRegexp.groupNumber (self.re, name);
  END GroupNumber;

PROCEDURE Quote (text: TEXT): TEXT =
  BEGIN
    RETURN M3jRegexp.quote (text);
  END Quote;

PROCEDURE QuoteReplacement (text: TEXT): TEXT =
  BEGIN
    RETURN M3jRegexp.quoteReplacement (text);
  END QuoteReplacement;

PROCEDURE MatchText (self: Match): TEXT =
  BEGIN
    RETURN self.s;
  END MatchText;

PROCEDURE Start (self: Match; n: CARDINAL := 0): INTEGER =
  BEGIN
    RETURN self.b[2 * n];
  END Start;

PROCEDURE Stop (self: Match; n: CARDINAL := 0): INTEGER =
  BEGIN
    RETURN self.b[2 * n + 1];
  END Stop;

PROCEDURE Group (self: Match; n: CARDINAL := 0): TEXT =
  VAR a := self.b[2 * n];
  BEGIN
    IF a < 0 THEN RETURN NIL END;
    RETURN Text.Sub (self.s, a, self.b[2 * n + 1] - a);
  END Group;

(* A match of the bounds "b", NIL for NIL. *)
PROCEDURE Make (text: TEXT; b: Bounds; scan: M3jRegexpScan.T; index: CARDINAL): Match
  RAISES {TooComplex} =
  BEGIN
    IF b = NIL THEN RETURN NIL END;
    IF NUMBER (b^) = 0 THEN RAISE TooComplex END;
    RETURN NEW (Match, s := text, b := b, scan := scan, index := index);
  END Make;

PROCEDURE Matches (re: T; text: TEXT): BOOLEAN RAISES {TooComplex} =
  BEGIN
    RETURN FullMatch (re, text) # NIL;
  END Matches;

PROCEDURE FullMatch (re: T; text: TEXT): Match RAISES {TooComplex} =
  BEGIN
    RETURN Make (text, M3jRegexp.fullMatch (re.re, text), NIL, 0);
  END FullMatch;

PROCEDURE Find (re: T; text: TEXT; start: CARDINAL := 0): Match
  RAISES {TooComplex} =
  VAR scan: M3jRegexpScan.T;
  BEGIN
    IF start > Text.Length (text) THEN RETURN NIL END;
    scan := M3jRegexp.scan (re.re, text, start);
    RETURN Make (text, M3jRegexpScan.next (scan, 0), scan, 1);
  END Find;

PROCEDURE Next (m: Match): Match RAISES {TooComplex} =
  BEGIN
    IF m.scan = NIL THEN RETURN NIL END;
    RETURN Make (m.s, M3jRegexpScan.next (m.scan, m.index), m.scan, m.index + 1);
  END Next;

PROCEDURE Replace (re: T; text, replacement: TEXT; all := TRUE): TEXT
  RAISES {Error, TooComplex} =
  VAR err := M3jRegexp.checkReplacement (re.re, replacement);  r: TEXT;
  BEGIN
    IF err # NIL THEN RAISE Error (err) END;
    r := M3jRegexp.replace (re.re, text, replacement, all);
    IF r = NIL THEN RAISE TooComplex END;
    RETURN r;
  END Replace;

PROCEDURE ReplaceWith (re: T; text: TEXT; r: Replacer; all := TRUE): TEXT
  RAISES ANY =
  VAR m := Find (re, text);  p := NEW (REF ARRAY OF TEXT, 16);  n: CARDINAL := 0;  last := 0;
  BEGIN
    IF m = NIL THEN RETURN text END;
    REPEAT
      Put (p, n, Text.Sub (text, last, m.start () - last));
      Put (p, n, r.apply (m));
      last := m.stop ();
      IF all THEN m := Next (m) ELSE m := NIL END;
    UNTIL m = NIL;
    Put (p, n, Text.Sub (text, last));
    RETURN M3jRegexp.concat (SUBARRAY (p^, 0, n));
  END ReplaceWith;

PROCEDURE Put (VAR p: REF ARRAY OF TEXT; VAR n: CARDINAL; t: TEXT) =
  VAR q: REF ARRAY OF TEXT;
  BEGIN
    IF n = NUMBER (p^) THEN
      q := NEW (REF ARRAY OF TEXT, 2 * n);
      SUBARRAY (q^, 0, n) := p^;
      p := q;
    END;
    p[n] := t;
    INC (n);
  END Put;

PROCEDURE Split (re: T; text: TEXT; limit: INTEGER := 0): REF ARRAY OF TEXT
  RAISES {TooComplex} =
  VAR r := M3jRegexp.split (re.re, text, MAX (MIN (limit, LAST (Java.Int)), FIRST (Java.Int)));
  BEGIN
    IF r = NIL THEN RAISE TooComplex END;
    RETURN r;
  END Split;

BEGIN
END Regexp.
