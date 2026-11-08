(* EXIT and RETURN out of the body of a TRY EXCEPT ELSE: the language
   defines them as exceptions, so the ELSE handler catches them, and the
   statement after the TRY runs next. *)

MODULE Main;
IMPORT IO;
VAR g := 1;

PROCEDURE T (t: TEXT) =
  BEGIN
    IO.Put(t & "\n");
  END T;

PROCEDURE A () =
  BEGIN
    LOOP
      TRY
        EXIT;
      EXCEPT
      ELSE
        T("a else");
      END;
      T("a after try");
      EXIT;
    END;
    T("a end");
  END A;

PROCEDURE B () =
  BEGIN
    TRY
      RETURN;
    EXCEPT
    ELSE
      T("b else");
    END;
    T("b after try");
  END B;

PROCEDURE C () =
  BEGIN
    LOOP
      TRY
        IF g = 1 THEN EXIT END;
      EXCEPT
      ELSE
        T("c else");
      END;
      T("c after try");
      EXIT;
    END;
    T("c end");
  END C;

PROCEDURE D () =
  BEGIN
    FOR i := 1 TO 2 DO
      TRY
        CASE g OF
        | 1 => IF g = 1 THEN EXIT END;
        ELSE
        END;
      EXCEPT
      ELSE
        T("d else");
      END;
      T("d after try");
    END;
    T("d end");
  END D;

BEGIN
  A(); B(); C(); D();
END Main.
