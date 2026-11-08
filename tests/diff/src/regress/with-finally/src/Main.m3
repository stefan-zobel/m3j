(* WITH names bound to variables and used in a FINALLY clause (which the
   front end treats as a nested procedure): assignments through them must
   change the variables, also in recursive calls. *)

MODULE Main;
IMPORT IO, Fmt;
VAR g := 3;
    a := ARRAY [0 .. 2] OF INTEGER {1, 2, 3};

PROCEDURE Put (t: TEXT; v: INTEGER) =
  BEGIN
    IO.Put(t & " " & Fmt.Int(v) & "\n");
  END Put;

PROCEDURE P (d: INTEGER; VAR v: INTEGER) =
  BEGIN
    WITH w = g DO
      IF d > 0 THEN P(d - 1, w) END;
      TRY
        g := g + 10;
      FINALLY
        w := w + 1;
        Put("p", w);
      END;
    END;
    INC(v);
  END P;

PROCEDURE Q (VAR v: INTEGER) =
  VAR x := 5;
  BEGIN
    WITH w = x, u = v, e = a[1] DO
      TRY
        x := 7;
        v := 8;
        a[1] := 9;
      FINALLY
        Put("q", w);
        Put("q", u);
        Put("q", e);
        w := 20;
        u := 21;
        e := 22;
      END;
    END;
    Put("x", x);
    FOR i := 1 TO 2 DO
      WITH f = i DO
        TRY
        FINALLY
          Put("f", f);
        END;
      END;
    END;
  END Q;

VAR x := 0;
BEGIN
  P(2, x);
  Put("g", g);
  Put("x", x);
  Q(x);
  Put("x", x);
  Put("a", a[1]);
END Main.
