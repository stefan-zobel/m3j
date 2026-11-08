MODULE WideLit;

IMPORT Text;

REVEAL
  T = ROOT BRANDED W"WideLit.T" OBJECT
        c: WIDECHAR := W'\177777';
        s: TEXT := W"\t\n\r\f\\\"\'\000101\x0041\U000042";
      END;

CONST
  Pieces = ARRAY [0 .. 4] OF TEXT {Empty, W"a", W"\x0101\x0102", Plain & Plain,
                                   Mixed & "!"};
  Chars = ARRAY [0 .. 5] OF WIDECHAR {W'a', W'\n', W'\'', W'\x00e4',
                                      W'\U00FFFD', W'\000377'};
  First = Greeting & Plain;
  Long = W"012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789N2de87N2de87N2de87N2de87N2de87N2de87";

VAR
  count := 0;
  wc: WIDECHAR := Euro;
  t: TEXT := Greeting;
  o := NEW (T);
  b := NEW (Branded);
  narrow: CHAR := 'x';

PROCEDURE Method (self: Special;  t: TEXT;  c: WIDECHAR) =
  BEGIN
    self.s := t & Text.FromWideChar (c);
  END Method;

PROCEDURE Run (): TEXT =
  VAR r := Mixed & First;
  BEGIN
    FOR i := FIRST (Pieces) TO LAST (Pieces) DO
      r := r & Pieces[i];
    END;
    FOR i := FIRST (Chars) TO LAST (Chars) DO
      IF Chars[i] = wc THEN INC (count) END;
      r := r & Text.FromWideChar (Chars[i]);
    END;
    IF Text.GetWideChar (t, 2) = W'\x00fc' THEN INC (count) END;
    CASE wc OF
    | W'a' .. W'z' => INC (count);
    | W'\x20ac' => INC (count, 2);
    | W'\U00FFFF' => INC (count, 3);
    ELSE
    END;
    wc := VAL (ORD (narrow), WIDECHAR);
    o.c := VAL (ORD (o.c) - 1, WIDECHAR);
    b^ := count;
    WITH sp = NEW (Special) DO
      sp.m ();
      sp.m (W"x");
      r := r & sp.s;
    END;
    RETURN r & Long & o.s & Text.FromWideChar (o.c) & Text.FromWideChar (wc);
  END Run;

BEGIN
END WideLit.
