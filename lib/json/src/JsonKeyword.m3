(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j from the UNSAFE module of cm3: the characters are
   taken from x, with the checks of its subscripts, instead of through
   an untraced pointer to its first one. So the module is safe. *)

MODULE JsonKeyword;

FROM JsonScanner IMPORT TK, TK_Ident, TK_Null, TK_True, TK_False;

PROCEDURE Classify (READONLY x: ARRAY OF CHAR): TK =
  VAR n := NUMBER (x);  class := TK_Ident;
  BEGIN
    IF n = 0 THEN RETURN class; END;
    CASE x[0] OF
    | 'n' =>
              IF    (n = 4)
                AND (x[1] = 'u')
                AND (x[2] = 'l')
                AND (x[3] = 'l') THEN class := TK_Null;
              END;
    | 't' =>
              IF    (n = 4)
                AND (x[1] = 'r')
                AND (x[2] = 'u')
                AND (x[3] = 'e') THEN class := TK_True;
              END;
   | 'f' =>
              IF    (n = 5)
                AND (x[1] = 'a')
                AND (x[2] = 'l')
                AND (x[3] = 's')
                AND (x[4] = 'e') THEN class := TK_False;
              END;
    ELSE (* class := TK_Ident *)
    END;
    RETURN class;
  END Classify;

BEGIN
END JsonKeyword.
