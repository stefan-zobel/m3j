(* The default values of the fields of records that are elements of
   arrays allocated with NEW (open and fixed), and fields of objects. *)

MODULE Main;

IMPORT IO, Fmt;

TYPE
  R  = RECORD a: INTEGER := 7;  b: INTEGER := 1;  c: INTEGER END;
  F  = ARRAY [0..1] OF R;
  S  = RECORD f: F END;
  O  = OBJECT x: F;  r: R  END;
  P  = O OBJECT q: ARRAY [0..2] OF S;  s := 3  END;

PROCEDURE Show (what: TEXT;  READONLY r: R) =
  BEGIN
    IO.Put (what & " " & Fmt.Int (r.a) & " " & Fmt.Int (r.b) & " "
              & Fmt.Int (r.c) & "\n");
  END Show;

PROCEDURE Test (n: INTEGER) =
  VAR
    open  := NEW (REF ARRAY OF R, n);
    open2 := NEW (REF ARRAY OF ARRAY OF R, n, 2);
    openf := NEW (REF ARRAY OF F, n);
    opens := NEW (REF ARRAY OF S, n);
    fixed := NEW (REF F);
    o     := NEW (O);
    p     := NEW (P, s := 5);
  BEGIN
    Show ("open", open[n - 1]);
    Show ("open2", open2[n - 1][1]);
    Show ("openf", openf[n - 1][1]);
    Show ("opens", opens[n - 1].f[1]);
    Show ("fixed", fixed[1]);
    Show ("o.x", o.x[1]);
    Show ("o.r", o.r);
    Show ("p.q", p.q[2].f[1]);
    Show ("p.r", p.r);
    IO.Put ("p.s " & Fmt.Int (p.s) & "\n");
  END Test;

BEGIN
  Test (3);
END Main.
