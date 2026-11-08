MODULE Misc;

(* FOR loops whose step has a known sign, the LINE pragma, the built-in
   procedures of the Compiler interface at run time, set constructors with
   ranges that span words, and run-time type descriptions with bounds
   beyond 32 bits. *)

IMPORT Compiler;

TYPE
  Pos = [1 .. 10];
  Neg = [-10 .. -1];
  Huge = [-16_7FFFFFFFFF .. 16_7FFFFFFFFFF];
  HugeRec = RECORD
    h: Huge;
    w: [0 .. 16_FFFFFFFF];
    m: [FIRST (INTEGER) .. 0];
    l: [-16_80000000L .. 16_7FFFFFFFFFFFL];
  END;
  HugeRef = REF HugeRec;
  HugeArr = REF ARRAY OF Huge;
  Big = SET OF [0 .. 300];

VAR
  pos: Pos := 2;
  neg: Neg := -3;
  step := 1;
  path, file: TEXT;
  line: INTEGER;
  hr := NEW (HugeRef);
  ha := NEW (HugeArr, 3);
  big: Big;

<* UNKNOWN_PRAGMA <* nested *> still inside *>

PROCEDURE Loops (): INTEGER =
  VAR n := 0;
  BEGIN
    FOR i := 0 TO 100 BY pos DO INC (n) END;
    FOR i := 100 TO 0 BY neg DO INC (n) END;
    FOR i := 0 TO 100 BY step DO INC (n) END;
    FOR i := pos TO 10 BY 3 DO INC (n) END;
    FOR i := LAST (INTEGER) - 5 TO LAST (INTEGER) BY pos DO INC (n) END;
    FOR i := FIRST (INTEGER) + 5 TO FIRST (INTEGER) BY neg DO INC (n) END;
    FOR i := 0 TO n BY pos DO INC (n) END;
    FOR i := n TO 0 BY neg DO INC (n) END;
    FOR c := 'a' TO 'z' BY pos DO INC (n) END;
    FOR l := 0L TO 10L BY VAL (pos, LONGINT) DO INC (n) END;
    RETURN n;
  END Loops;

<*LINE 1000 "Generated.m3" *>
PROCEDURE Where (): INTEGER =
  BEGIN
    path := Compiler.ThisPath ();
    file := Compiler.ThisFile ();
    line := Compiler.ThisLine ();
    RETURN line;
  END Where;
<*LINE 77 *>
PROCEDURE Sets (k: INTEGER): INTEGER =
  VAR n := 0;
  BEGIN
    big := Big {1, 60 .. 70, 100 .. 250, k};
    big := big + Big {k .. k + 130, 0 .. 63, 64 .. 127, 128, 255 .. 300};
    big := Big {k, 2 .. 3, 62 .. 65, 126 .. 129, 190 .. 193, 254 .. 257};
    FOR i := 0 TO 300 DO
      IF i IN big THEN INC (n) END;
    END;
    RETURN n;
  END Sets;

PROCEDURE Run (): INTEGER =
  BEGIN
    hr.h := LAST (Huge);
    hr.w := 16_FFFFFFFF;
    hr.l := 16_7FFFFFFFFFFFL;
    ha[1] := FIRST (Huge);
    RETURN Loops () + Where () + Sets (3) + Compiler.ThisLine ();
  END Run;

BEGIN
END Misc.
