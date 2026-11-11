(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Html;

IMPORT Text, TextIntTbl;

CONST
  (* The named references of HTML 4 for the characters 160 to 255, in
     order. *)
  Latin1 = "nbsp iexcl cent pound curren yen brvbar sect uml copy ordf laquo not shy reg macr "
             & "deg plusmn sup2 sup3 acute micro para middot cedil sup1 ordm raquo frac14 frac12 frac34 iquest "
             & "Agrave Aacute Acirc Atilde Auml Aring AElig Ccedil Egrave Eacute Ecirc Euml Igrave Iacute Icirc Iuml "
             & "ETH Ntilde Ograve Oacute Ocirc Otilde Ouml times Oslash Ugrave Uacute Ucirc Uuml Yacute THORN szlig "
             & "agrave aacute acirc atilde auml aring aelig ccedil egrave eacute ecirc euml igrave iacute icirc iuml "
             & "eth ntilde ograve oacute ocirc otilde ouml divide oslash ugrave uacute ucirc uuml yacute thorn yuml";

  (* The others, each with its code. *)
  Others = "quot 34 amp 38 apos 39 lt 60 gt 62 OElig 338 oelig 339 Scaron 352 scaron 353 Yuml 376 fnof 402 "
             & "circ 710 tilde 732 "
             & "Alpha 913 Beta 914 Gamma 915 Delta 916 Epsilon 917 Zeta 918 Eta 919 Theta 920 Iota 921 "
             & "Kappa 922 Lambda 923 Mu 924 Nu 925 Xi 926 Omicron 927 Pi 928 Rho 929 Sigma 931 Tau 932 "
             & "Upsilon 933 Phi 934 Chi 935 Psi 936 Omega 937 "
             & "alpha 945 beta 946 gamma 947 delta 948 epsilon 949 zeta 950 eta 951 theta 952 iota 953 "
             & "kappa 954 lambda 955 mu 956 nu 957 xi 958 omicron 959 pi 960 rho 961 sigmaf 962 sigma 963 "
             & "tau 964 upsilon 965 phi 966 chi 967 psi 968 omega 969 thetasym 977 upsih 978 piv 982 "
             & "ensp 8194 emsp 8195 thinsp 8201 zwnj 8204 zwj 8205 lrm 8206 rlm 8207 ndash 8211 mdash 8212 "
             & "lsquo 8216 rsquo 8217 sbquo 8218 ldquo 8220 rdquo 8221 bdquo 8222 dagger 8224 Dagger 8225 "
             & "bull 8226 hellip 8230 permil 8240 prime 8242 Prime 8243 lsaquo 8249 rsaquo 8250 oline 8254 "
             & "frasl 8260 euro 8364 image 8465 weierp 8472 real 8476 trade 8482 alefsym 8501 "
             & "larr 8592 uarr 8593 rarr 8594 darr 8595 harr 8596 crarr 8629 "
             & "lArr 8656 uArr 8657 rArr 8658 dArr 8659 hArr 8660 "
             & "forall 8704 part 8706 exist 8707 empty 8709 nabla 8711 isin 8712 notin 8713 ni 8715 "
             & "prod 8719 sum 8721 minus 8722 lowast 8727 radic 8730 prop 8733 infin 8734 ang 8736 "
             & "and 8743 or 8744 cap 8745 cup 8746 int 8747 there4 8756 sim 8764 cong 8773 asymp 8776 "
             & "ne 8800 equiv 8801 le 8804 ge 8805 sub 8834 sup 8835 nsub 8836 sube 8838 supe 8839 "
             & "oplus 8853 otimes 8855 perp 8869 sdot 8901 lceil 8968 rceil 8969 lfloor 8970 rfloor 8971 "
             & "lang 9001 rang 9002 loz 9674 spades 9824 clubs 9827 hearts 9829 diams 9830";

  (* What a number from 16_80 to 16_9F stands for: the character of
     windows-1252, or the number itself where that has none. *)
  Windows1252 = ARRAY [16_80 .. 16_9F] OF INTEGER {
    16_20AC, 16_81, 16_201A, 16_0192, 16_201E, 16_2026, 16_2020, 16_2021,
    16_02C6, 16_2030, 16_0160, 16_2039, 16_0152, 16_8D, 16_017D, 16_8F,
    16_90, 16_2018, 16_2019, 16_201C, 16_201D, 16_2022, 16_2013, 16_2014,
    16_02DC, 16_2122, 16_0161, 16_203A, 16_0153, 16_9D, 16_017E, 16_0178};

  MaxName = 10;                 (* longer than any name *)
  MaxDigits = 8;

VAR names: TextIntTbl.T;        (* written once, by the main body *)

PROCEDURE Put (VAR a: ARRAY OF WIDECHAR; VAR j: INTEGER; s: TEXT) =
  BEGIN
    FOR i := 0 TO Text.Length (s) - 1 DO
      a[j] := Text.GetWideChar (s, i);
      INC (j);
    END;
  END Put;

PROCEDURE Escape (t: TEXT): TEXT =
  VAR n := Text.Length (t);  extra := 0;  src, r: REF ARRAY OF WIDECHAR;  j := 0;  c: WIDECHAR;
  BEGIN
    src := NEW (REF ARRAY OF WIDECHAR, n);
    Text.SetWideChars (src^, t);
    FOR i := 0 TO n - 1 DO
      c := src[i];
      IF c = W'&' OR c = W'\'' THEN INC (extra, 4)
      ELSIF c = W'<' OR c = W'>' THEN INC (extra, 3)
      ELSIF c = W'"' THEN INC (extra, 5)
      END;
    END;
    IF extra = 0 THEN RETURN t END;
    r := NEW (REF ARRAY OF WIDECHAR, n + extra);
    FOR i := 0 TO n - 1 DO
      c := src[i];
      IF c = W'&' THEN Put (r^, j, "&amp;")
      ELSIF c = W'<' THEN Put (r^, j, "&lt;")
      ELSIF c = W'>' THEN Put (r^, j, "&gt;")
      ELSIF c = W'"' THEN Put (r^, j, "&quot;")
      ELSIF c = W'\'' THEN Put (r^, j, "&#39;")
      ELSE
        r[j] := c;
        INC (j);
      END;
    END;
    RETURN Text.FromWideChars (r^);
  END Escape;

(* The value of the digit "c" in base 16 or 10, or -1. *)
PROCEDURE Digit (c: WIDECHAR; hex: BOOLEAN): INTEGER =
  VAR v := ORD (c);
  BEGIN
    IF v >= ORD ('0') AND v <= ORD ('9') THEN RETURN v - ORD ('0') END;
    IF hex THEN
      IF v >= ORD ('a') AND v <= ORD ('f') THEN RETURN v - ORD ('a') + 10 END;
      IF v >= ORD ('A') AND v <= ORD ('F') THEN RETURN v - ORD ('A') + 10 END;
    END;
    RETURN -1;
  END Digit;

PROCEDURE IsAlnum (c: WIDECHAR): BOOLEAN =
  VAR v := ORD (c);
  BEGIN
    RETURN v >= ORD ('a') AND v <= ORD ('z') OR v >= ORD ('A') AND v <= ORD ('Z')
             OR v >= ORD ('0') AND v <= ORD ('9');
  END IsAlnum;

(* The character that the number "v" of a reference stands for. *)
PROCEDURE Fix (v: INTEGER): INTEGER =
  BEGIN
    IF v = 0 OR v >= 16_D800 AND v <= 16_DFFF OR v > 16_10FFFF THEN RETURN 16_FFFD END;
    IF v >= 16_80 AND v <= 16_9F THEN RETURN Windows1252[v] END;
    RETURN v;
  END Fix;

(* If a reference begins at "a[start]", just after its "&": sets "v" to its
   character and returns the index after its ";". Otherwise returns -1. *)
PROCEDURE Reference (READONLY a: ARRAY OF WIDECHAR; start: INTEGER; VAR v: INTEGER): INTEGER =
  VAR n := NUMBER (a);  i := start;  first, d: INTEGER;  hex := FALSE;
  BEGIN
    IF i < n AND a[i] = W'#' THEN
      INC (i);
      IF i < n AND (a[i] = W'x' OR a[i] = W'X') THEN
        hex := TRUE;
        INC (i);
      END;
      first := i;
      v := 0;
      WHILE i < n AND i - first < MaxDigits DO
        d := Digit (a[i], hex);
        IF d < 0 THEN EXIT END;
        IF hex THEN v := v * 16 + d ELSE v := v * 10 + d END;
        INC (i);
      END;
      IF i = first OR i >= n OR a[i] # W';' THEN RETURN -1 END;
      v := Fix (v);
      RETURN i + 1;
    END;
    first := i;
    WHILE i < n AND i - first <= MaxName AND IsAlnum (a[i]) DO INC (i) END;
    IF i = first OR i >= n OR a[i] # W';' THEN RETURN -1 END;
    IF NOT names.get (Text.FromWideChars (SUBARRAY (a, first, i - first)), v) THEN RETURN -1 END;
    RETURN i + 1;
  END Reference;

PROCEDURE Unescape (t: TEXT): TEXT =
  VAR n, i, j, k, v: INTEGER;  src, r: REF ARRAY OF WIDECHAR;
  BEGIN
    IF Text.FindChar (t, '&') < 0 THEN RETURN t END;
    n := Text.Length (t);
    src := NEW (REF ARRAY OF WIDECHAR, n);
    Text.SetWideChars (src^, t);
    r := NEW (REF ARRAY OF WIDECHAR, n);  (* a reference is never shorter than what it stands for *)
    i := 0;
    j := 0;
    WHILE i < n DO
      k := -1;
      IF src[i] = W'&' THEN k := Reference (src^, i + 1, v) END;
      IF k < 0 THEN
        r[j] := src[i];
        INC (j);
        INC (i);
      ELSE
        IF v > 16_FFFF THEN
          DEC (v, 16_10000);
          r[j] := VAL (16_D800 + v DIV 16_400, WIDECHAR);
          r[j + 1] := VAL (16_DC00 + v MOD 16_400, WIDECHAR);
          INC (j, 2);
        ELSE
          r[j] := VAL (v, WIDECHAR);
          INC (j);
        END;
        i := k;
      END;
    END;
    RETURN Text.FromWideChars (SUBARRAY (r^, 0, j));
  END Unescape;

PROCEDURE Lookup (name: TEXT): INTEGER =
  VAR v: INTEGER;
  BEGIN
    IF names.get (name, v) THEN RETURN v END;
    RETURN -1;
  END Lookup;

(* Fills "names" from "Latin1" and "Others", whose words are separated by
   single spaces. *)
PROCEDURE Fill () =
  VAR i, stop, code: INTEGER;  name: TEXT;

  PROCEDURE Word (t: TEXT): TEXT =
    VAR w: TEXT;
    BEGIN
      stop := Text.FindChar (t, ' ', i);
      IF stop < 0 THEN stop := Text.Length (t) END;
      w := Text.Sub (t, i, stop - i);
      i := stop + 1;
      RETURN w;
    END Word;

  PROCEDURE Number (w: TEXT): INTEGER =
    VAR v := 0;
    BEGIN
      FOR k := 0 TO Text.Length (w) - 1 DO
        v := v * 10 + ORD (Text.GetChar (w, k)) - ORD ('0');
      END;
      RETURN v;
    END Number;

  BEGIN
    names := NEW (TextIntTbl.Default).init (300);
    i := 0;
    code := 160;
    WHILE i < Text.Length (Latin1) DO
      EVAL names.put (Word (Latin1), code);
      INC (code);
    END;
    <*ASSERT code = 256*>
    i := 0;
    WHILE i < Text.Length (Others) DO
      name := Word (Others);
      EVAL names.put (name, Number (Word (Others)));
    END;
  END Fill;

BEGIN
  Fill ();
END Html.
