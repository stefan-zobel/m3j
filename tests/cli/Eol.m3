(* Wr.EOL, OSConfig.LineSep and the constants made of them: their values
   are those of the system that the program runs on, not those of the
   system that compiled it. Each text is shown with \r and \n. *)

MODULE Eol EXPORTS Main;

IMPORT Wr, IO, Text, Fmt, OSConfig;

TYPE R = RECORD a: TEXT := Wr.EOL; b: INTEGER := 1 END;

CONST
  X = "a" & Wr.EOL & "b";
  Y = Wr.EOL & Wr.EOL;
  A = ARRAY OF TEXT {Wr.EOL, "z" & Wr.EOL, "plain"};
  RC = R {a := "r" & Wr.EOL, b := 2};

PROCEDURE Show (t: TEXT): TEXT =
  VAR r := "";
  BEGIN
    FOR i := 0 TO Text.Length (t) - 1 DO
      WITH c = Text.GetChar (t, i) DO
        IF c = '\r' THEN r := r & "\\r"
        ELSIF c = '\n' THEN r := r & "\\n"
        ELSE r := r & Text.FromChar (c)
        END;
      END;
    END;
    RETURN "[" & r & "]";
  END Show;

PROCEDURE P (t := Wr.EOL): TEXT =
  BEGIN
    RETURN t;
  END P;

VAR r: R;

BEGIN
  IO.Put ("EOL " & Show (Wr.EOL) & " LineSep " & Show (OSConfig.LineSep) & "\n");
  IO.Put ("X " & Show (X) & " Y " & Show (Y) & "\n");
  IO.Put ("A " & Show (A[0]) & Show (A[1]) & Show (A[2]) & " " & Fmt.Int (NUMBER (A)) & "\n");
  IO.Put ("default " & Show (P ()) & " field " & Show (r.a) & " RC " & Show (RC.a) & "\n");
  IO.Put ("equal " & Fmt.Bool (Text.Equal (X, "a" & Wr.EOL & "b")) & "\n");
END Eol.
