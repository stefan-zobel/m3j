INTERFACE Decls;

(* a comment with a ; and END Decls; that ends nothing *)

CONST
  Limit = 10;
  Name: TEXT = "a;b END";

TYPE
  Color = {Red, Green, Blue};
  Point = RECORD
    x, y: INTEGER;
    tag: CHAR := ';'
  END;
  Node = REF RECORD next: Node; value: INTEGER END;
  Handler = PROCEDURE (a: INTEGER; VAR b: TEXT): BOOLEAN;
  Range = [0 .. Limit];
  T <: Public;
  Public = OBJECT
    count: INTEGER;
  METHODS
    get (): INTEGER;
    put (x: INTEGER) := Put;
  END;

EXCEPTION Failed (TEXT);

VAR
  origin: Point;
  a, b := 1;

PROCEDURE Put (t: Public; x: INTEGER);

<*INLINE*> PROCEDURE New (): T;

END Decls.
