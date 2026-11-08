(* TEXT: formatting, concatenation and scanning of characters. *)
MODULE Main;
IMPORT IO, Fmt, Text;

VAR sum := 0;  t: TEXT;

BEGIN
  FOR r := 1 TO 2000 DO
    t := "";
    FOR i := 1 TO 200 DO
      t := t & Fmt.Int (i * r) & ",";
    END;
    FOR i := 0 TO Text.Length (t) - 1 DO
      IF Text.GetChar (t, i) = ',' THEN
        INC (sum);
      ELSE
        INC (sum, ORD (Text.GetChar (t, i)) - ORD ('0'));
      END;
    END;
    INC (sum, Text.FindChar (t, '9'));
    IF Text.Equal (Text.Sub (t, 0, 3), "1,2") THEN INC (sum) END;
  END;
  IO.Put (Fmt.Int (sum) & "\n");
END Main.
