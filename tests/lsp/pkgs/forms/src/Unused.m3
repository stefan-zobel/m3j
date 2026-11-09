MODULE Unused;

(* Names that an ASSERT does not use, each of which still gets the message
   that it is not used, and one that is used although it says it is not. *)

IMPORT Fmt;

TYPE O = OBJECT field: INTEGER END;

VAR
  o := NEW (O);
  late := 0;

PROCEDURE Texts () =
  VAR inText, c, inComment, field, W := 0;
  BEGIN
    <*ASSERT "inText Fmt" # NIL*>
    <*ASSERT 'c' # 'd'*>
    <*ASSERT TRUE (* inComment *)*>
    <*ASSERT o.field = 0*>
    <*ASSERT W"x" # NIL*>
  END Texts;

PROCEDURE Other () =
  VAR late := 0;
  BEGIN
  END Other;

PROCEDURE Shadowed () =
  VAR v := 0;
  BEGIN
    WITH v = 1 DO
      <*ASSERT v = 1*>
    END;
    <*ASSERT late = 0*>
  END Shadowed;

PROCEDURE Says (<*UNUSED*> u: INTEGER) =
  BEGIN
    <*ASSERT u = 0*>
  END Says;

BEGIN
  Texts ();
  Other ();
  Shadowed ();
  Says (0);
END Unused.
