MODULE Asserted;

(* Names that are used only in an ASSERT or DEBUG pragma, none of which may
   get the message that it is not used. *)

IMPORT Fmt, Text;

EXCEPTION Failed (INTEGER);

TYPE
  A = REF RECORD x: INTEGER END;
  B = REF RECORD y: INTEGER END;

PROCEDURE Early (): BOOLEAN =
  BEGIN
    <*ASSERT Late > 0*>
    RETURN TRUE;
  END Early;

CONST Late = 1;

PROCEDURE Bound (s: TEXT) =
  BEGIN
    WITH c = Text.GetChar (s, 0) DO
      <*ASSERT c = ' ' OR c = '\t'*>
    END;
  END Bound;

PROCEDURE Local () =
  VAR outer := 1;
  BEGIN
    VAR inner := outer;
    BEGIN
      <*ASSERT inner > 0*>
    END;
  END Local;

PROCEDURE Arms (r: REFANY) =
  BEGIN
    TYPECASE r OF
    | NULL =>
    | A (v) => <*ASSERT v.x = 0*> EVAL r;
    | B (v) =>
        <*ASSERT(v.y = 0)*>
    ELSE
    END;
  END Arms;

PROCEDURE Handled () =
  BEGIN
    TRY
      RAISE Failed (1);
    EXCEPT
    | Failed (code) => <*DEBUG code > 0, "failed"*>
    END;
  END Handled;

PROCEDURE Loop () =
  BEGIN
    FOR i := 1 TO 3 DO
      <*ASSERT i > 0*>
    END;
  END Loop;

PROCEDURE Formals (a, b: INTEGER) =
  BEGIN
    WITH p = a, q = p + 1 DO
      <*ASSERT q > b*>
    END;
  END Formals;

PROCEDURE Imported () =
  BEGIN
    <*ASSERT Fmt.Int (0) # NIL*>
  END Imported;

VAR final := 0;

BEGIN
  EVAL Early ();
  Bound (" ");
  Local ();
  Arms (NIL);
  Handled ();
  Loop ();
  Formals (1, 0);
  Imported ();
  <*ASSERT final = 0*>
END Asserted.
