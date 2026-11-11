(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Regular expressions with the syntax and meaning of those of the Java
   platform (java.util.regex.Pattern): groups, named groups "(?<name>...)",
   alternation, greedy, lazy and possessive quantifiers, classes, anchors,
   back references, lookahead and lookbehind, and the flags "(?i)" and the
   like inside a pattern.

|    re := Regexp.Compile ("(?<key>\\w+)\\s*=\\s*(?<value>[^;]+)");
|    m := Regexp.Find (re, "a = 1; b = two");
|    WHILE m # NIL DO
|      IO.Put (m.group (re.groupNumber ("key")) & " is " & m.group (2) & "\n");
|      m := Regexp.Next (m);
|    END;

   The engine backtracks, and for some patterns its time grows
   exponentially with the text: bounded repetitions of a group, nested
   ".*", back references. So each search has a budget: it may read
   characters of the text "limit" times the characters it searches plus
   100_000 times; a search over its budget, or one that overflows the stack
   (a group with alternatives repeated some 100_000 times), raises
   "TooComplex". "Replace" and "Split" have one budget for the whole text.
   With "DefaultLimit" a search takes at most some 100 milliseconds on a
   short text and about a second on a text of a million characters, while
   ordinary patterns read each character fewer than ten times. A limit of
   0 means no limit. A search is not stopped by "Thread.Alert".

   Indices count the "WIDECHAR"s of a text. A character above 16_FFFF is
   two of them, a surrogate pair, and a pattern takes such a pair as one
   character.

   A "T" and a "Match" may be shared by threads. *)

INTERFACE Regexp;

EXCEPTION
  Error (TEXT);  (* a bad pattern or replacement; the text names the index *)
  TooComplex;    (* a search went over its budget or the stack *)

TYPE
  Option = {IgnoreCase, Unicode, Multiline, DotAll, Literal, Comments};
  Options = SET OF Option;

  (* "IgnoreCase" compares the letters of ASCII without case, and with
     "Unicode" all letters; "Unicode" also takes "\w", "\d", "\s", "\b" and
     the classes "\p{Alpha}" and the like for all of Unicode, not just
     ASCII. "Multiline": "^" and "$" match at the end of each line too.
     "DotAll": "." matches the end of a line too. "Literal": the pattern is
     a text to look for. "Comments": white space and comments from "#" to
     the end of the line are left out of the pattern. *)

CONST DefaultLimit = 100;

TYPE
  T <: Public;   (* a compiled pattern *)
  Public = OBJECT METHODS
    pattern (): TEXT;
    options (): Options;
    limit (): CARDINAL;
    groups (): CARDINAL;                (* the capturing groups *)
    groupNumber (name: TEXT): INTEGER;  (* -1 if there is no such group *)
  END;

  Match <: MatchPublic;
  MatchPublic = OBJECT METHODS
    text (): TEXT;                      (* the text it was found in *)
    start (n: CARDINAL := 0): INTEGER;  (* -1 if group n took no part *)
    stop (n: CARDINAL := 0): INTEGER;   (* the index after it, or -1 *)
    group (n: CARDINAL := 0): TEXT;     (* NIL if group n took no part *)
  END;
  (* Group 0 is the whole match. A group "n" above "groups ()" is a checked
     runtime error. *)

  Replacer = OBJECT METHODS
    apply (m: Match): TEXT RAISES ANY;
  END;

PROCEDURE Compile (pattern: TEXT; options := Options{};
                   limit: CARDINAL := DefaultLimit): T RAISES {Error};

PROCEDURE Quote (text: TEXT): TEXT;
(* A pattern that matches "text" and nothing else. *)

PROCEDURE QuoteReplacement (text: TEXT): TEXT;
(* A replacement that stands for "text" and nothing else. *)

PROCEDURE Matches (re: T; text: TEXT): BOOLEAN RAISES {TooComplex};
(* Whether the whole text matches. *)

PROCEDURE FullMatch (re: T; text: TEXT): Match RAISES {TooComplex};
(* The match of the whole text, or NIL. "Next" of it is NIL. *)

PROCEDURE Find (re: T; text: TEXT; start: CARDINAL := 0): Match
  RAISES {TooComplex};
(* The first match that starts at "start" or later, NIL if there is none or
   "start" is beyond the end. Lookbehind and "\b" see the text before
   "start"; "^" and "\A" match at index 0 only, and "\G" at "start". *)

PROCEDURE Next (m: Match): Match RAISES {TooComplex};
(* The match after "m", NIL if there is none. The search starts at the end
   of "m", or one later if "m" is empty, and "\G" matches at the end of
   "m". *)

PROCEDURE Replace (re: T; text, replacement: TEXT; all := TRUE): TEXT
  RAISES {Error, TooComplex};
(* "text" with the matches replaced, or only the first if "all" is FALSE.
   In "replacement", "$n" stands for group "n" (as many digits as name a
   group), "${name}" for the group of that name, and "\c" for the character
   "c"; a group that took no part stands for the empty text. A reference to
   a group that the pattern does not have is an "Error", even if nothing
   matches. *)

PROCEDURE ReplaceWith (re: T; text: TEXT; r: Replacer; all := TRUE): TEXT
  RAISES ANY;
(* "text" with each match "m" replaced by "r.apply (m)". *)

PROCEDURE Split (re: T; text: TEXT; limit: INTEGER := 0): REF ARRAY OF TEXT
  RAISES {TooComplex};
(* The parts of "text" between the matches. An empty match at index 0 makes
   no empty first part. With "limit" > 0 there are at most "limit" parts,
   the last with the rest of the text; with "limit" = 0 empty parts at the
   end are left out; with "limit" < 0 they stay. The empty text is one
   empty part. *)

END Regexp.
