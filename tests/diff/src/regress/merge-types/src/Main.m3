(* A variable of type REFANY or ROOT that is assigned references of
   different types on different paths, of types that this module is the
   first to use (REF CHAR, REF INTEGER, a REF of a record, new object
   types and open arrays): the paths of the code join with the variable
   holding either of them. *)

MODULE Main;
IMPORT IO, Fmt;

TYPE
  L = REF RECORD a: L; n: INTEGER END;
  M = REF RECORD c: CHAR END;
  A = OBJECT x := 1 END;
  B = OBJECT y := 2 END;
  C = A OBJECT z := 3 END;

PROCEDURE Show (r: REFANY): TEXT =
  BEGIN
    TYPECASE r OF
    | NULL => RETURN "nil"
    | REF CHAR (c) => RETURN "char " & Fmt.Char(c^)
    | REF INTEGER (i) => RETURN "integer " & Fmt.Int(i^)
    | L (l) => RETURN "list " & Fmt.Int(l.n)
    | M (m) => RETURN "m " & Fmt.Char(m.c)
    | C (c) => RETURN "c " & Fmt.Int(c.x + c.z)
    | A (a) => RETURN "a " & Fmt.Int(a.x)
    | B (b) => RETURN "b " & Fmt.Int(b.y)
    | REF ARRAY OF INTEGER (v) => RETURN "array " & Fmt.Int(NUMBER(v^))
    ELSE RETURN "other"
    END;
  END Show;

PROCEDURE Two (k: INTEGER): REFANY =
  VAR r: REFANY;
  BEGIN
    IF k = 0 THEN
      VAR c := NEW(REF CHAR); BEGIN c^ := 'q'; r := c END;
    ELSE
      VAR i := NEW(REF INTEGER); BEGIN i^ := k; r := i END;
    END;
    RETURN r;
  END Two;

PROCEDURE Many (k: INTEGER): REFANY =
  VAR r: REFANY := NIL;
  BEGIN
    CASE k OF
    | 0 => r := NEW(L, n := 7);
    | 1 => r := NEW(M, c := 'm');
    | 2 => r := NEW(A);
    | 3 => r := NEW(B);
    | 4 => r := NEW(C);
    | 5 => r := NEW(REF ARRAY OF INTEGER, 4);
    ELSE
    END;
    RETURN r;
  END Many;

PROCEDURE Objects (k: INTEGER): ROOT =
  VAR o: ROOT;
  BEGIN
    IF k MOD 2 = 0 THEN o := NEW(B) ELSE o := NEW(C) END;
    RETURN o;
  END Objects;

PROCEDURE Loop (): TEXT =
  VAR r: REFANY := NEW(REF CHAR); t := "";
  BEGIN
    FOR i := 1 TO 3 DO
      t := t & Show(r) & "; ";
      IF i = 1 THEN r := NEW(L, n := i) ELSE r := NEW(B) END;
    END;
    RETURN t & Show(r);
  END Loop;

BEGIN
  IO.Put(Show(Two(0)) & "\n");
  IO.Put(Show(Two(5)) & "\n");
  FOR k := 0 TO 6 DO
    IO.Put(Show(Many(k)) & "\n");
  END;
  IO.Put(Show(Objects(0)) & " " & Show(Objects(1)) & "\n");
  IO.Put(Loop() & "\n");
END Main.
