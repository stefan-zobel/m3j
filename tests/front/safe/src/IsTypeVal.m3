MODULE IsTypeVal;

(* ISTYPE as a value, not as the condition of a branch. *)

TYPE
  A = OBJECT n: INTEGER END;
  B = A OBJECT m: INTEGER END;
  C = B OBJECT END;
  R = REF INTEGER;
  RB = BRANDED "IsTypeVal.RB" REF INTEGER;
  U <: A;

REVEAL
  U = A BRANDED "IsTypeVal.U" OBJECT k := 0 END;

VAR
  a: A := NEW (B);
  r: REFANY := NEW (R);
  n: REFANY := NIL;
  flags: ARRAY [0 .. 10] OF BOOLEAN;

PROCEDURE Run (): INTEGER =
  VAR k := 0;  x: ROOT := NEW (C);  b: BOOLEAN;
  BEGIN
    flags[0] := ISTYPE (a, A);
    flags[1] := ISTYPE (a, B);
    flags[2] := ISTYPE (a, C);
    flags[3] := ISTYPE (r, R);
    flags[4] := ISTYPE (r, NULL);
    flags[5] := ISTYPE (n, RB);
    flags[6] := ISTYPE (r, A);
    flags[7] := ISTYPE (x, U);
    flags[8] := NOT ISTYPE (x, B) OR ISTYPE (a, U);
    flags[9] := ISTYPE (NARROW (r, R), R);
    b := ISTYPE (x, ROOT);
    flags[10] := b AND ISTYPE (NEW (C), B);
    IF ISTYPE (r, R) THEN INC (k) END;
    IF NOT ISTYPE (n, RB) THEN INC (k) END;
    IF ISTYPE (r, RB) OR ISTYPE (n, R) THEN INC (k) END;
    FOR i := FIRST (flags) TO LAST (flags) DO
      IF flags[i] THEN INC (k) END;
    END;
    RETURN k;
  END Run;

BEGIN
END IsTypeVal.
