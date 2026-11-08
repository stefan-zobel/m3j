MODULE RaiseArgs;

(* RAISE with arguments of every size and kind. *)

TYPE
  Rec = RECORD a, b, c: INTEGER;  t: TEXT END;
  Arr = ARRAY [0 .. 9] OF CHAR;
  Big = SET OF [0 .. 200];
  Small = SET OF [0 .. 7];
  Pair = RECORD x, y: [0 .. 255] END;

EXCEPTION
  ERec (Rec);
  EArr (Arr);
  EBig (Big);
  EReal (LONGREAL);
  EShort (REAL);
  ESmall (Small);
  EText (TEXT);
  EInt ([0 .. 100]);
  EPair (Pair);
  ELong (LONGINT);
  EChar (CHAR);
  EPlain;

PROCEDURE Raise (k: INTEGER) RAISES ANY =
  VAR r := Rec {1, 2, 3, "x"};  a: Arr;  s := Big {1, 100 .. 150, k};
  BEGIN
    a[0] := 'x';
    CASE k OF
    | 0 => RAISE ERec (r);
    | 1 => RAISE EArr (a);
    | 2 => RAISE EBig (s);
    | 3 => RAISE EReal (1.5d0);
    | 4 => RAISE ESmall (Small {1, 3});
    | 5 => RAISE EText ("t");
    | 6 => RAISE EInt (k);
    | 7 => RAISE ERec (Rec {k, k, k, NIL});
    | 8 => RAISE EPair (Pair {k, 2 * k});
    | 9 => RAISE EShort (FLOAT (k));
    | 10 => RAISE ELong (VAL (k, LONGINT));
    | 11 => RAISE EChar (VAL (k, CHAR));
    | 12 => RAISE EBig (Big {k .. 3 * k, 2 * k .. 199});
    | 13 => RAISE EPlain;
    ELSE
    END;
  END Raise;

PROCEDURE Run (): INTEGER =
  VAR n := 0;
  BEGIN
    FOR k := 0 TO 14 DO
      TRY
        Raise (k);
      EXCEPT
      | ERec (r) => INC (n, r.a);
      | EArr (a) => IF a[0] = 'x' THEN INC (n) END;
      | EBig (s) => IF 120 IN s THEN INC (n) END;
      | EReal (x) => IF x > 1.0d0 THEN INC (n) END;
      | EShort (x) => IF x > 1.0 THEN INC (n) END;
      | ESmall (s) => IF 3 IN s THEN INC (n) END;
      | EText (t) => IF t # NIL THEN INC (n) END;
      | EInt (i) => INC (n, i);
      | EPair (p) => INC (n, p.y);
      | ELong (l) => INC (n, VAL (l, INTEGER));
      | EChar (c) => INC (n, ORD (c));
      | EPlain => INC (n);
      END;
    END;
    RETURN n;
  END Run;

BEGIN
END RaiseArgs.
