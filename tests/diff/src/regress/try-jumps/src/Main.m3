(* EXIT and RETURN out of nested TRY statements whose outer FINALLY part
   raises an exception: the FINALLY parts run once each, and the handlers
   of the TRY statements that the jump leaves do not catch the exception. *)

MODULE Main;
IMPORT IO, Fmt;
EXCEPTION F (INTEGER);
VAR g := 1;

PROCEDURE T (t: TEXT) =
  BEGIN
    IO.Put(t & "\n");
  END T;

PROCEDURE A (): INTEGER RAISES {F} =
  BEGIN
    TRY
      TRY
        RETURN 5;
      FINALLY
        T("a inner");
      END;
    FINALLY
      IF g # 0 THEN RAISE F(2) END;
    END;
  END A;

PROCEDURE B (): INTEGER RAISES {F} =
  BEGIN
    TRY
      TRY
        RETURN 5;
      EXCEPT
      | F (v) => T("b caught " & Fmt.Int(v));
      END;
    FINALLY
      IF g # 0 THEN RAISE F(3) END;
    END;
    RETURN 6;
  END B;

PROCEDURE C () RAISES {F} =
  BEGIN
    LOOP
      TRY
        TRY
          TRY
            EXIT;
          EXCEPT
          | F (v) => T("c caught " & Fmt.Int(v));
          END;
        FINALLY
          T("c inner");
        END;
      FINALLY
        IF g # 0 THEN RAISE F(4) END;
      END;
    END;
    T("c after");
  END C;

PROCEDURE D (): INTEGER =
  BEGIN
    TRY
      TRY
        RETURN 7;
      EXCEPT
      | F => T("d caught");
      END;
    FINALLY
      T("d fin");
    END;
    RETURN 8;
  END D;

BEGIN
  TRY EVAL A() EXCEPT F (v) => T("A raised " & Fmt.Int(v)) END;
  TRY EVAL B() EXCEPT F (v) => T("B raised " & Fmt.Int(v)) END;
  TRY C() EXCEPT F (v) => T("C raised " & Fmt.Int(v)) END;
  T("D " & Fmt.Int(D()));
END Main.
