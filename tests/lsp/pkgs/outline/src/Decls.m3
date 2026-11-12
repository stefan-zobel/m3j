MODULE Decls;

REVEAL
  T = Public BRANDED "Decls.T" OBJECT
    hidden: TEXT;
  OVERRIDES
    get := Get;
  END;

VAR counter := 0;

PROCEDURE Get (t: T): INTEGER =
  BEGIN
    RETURN t.count;
  END Get;

PROCEDURE Put (t: Public; x: INTEGER) =
  VAR local := x;               (* not in the outline *)

  PROCEDURE Put (y: INTEGER) =  (* nested, with the same name *)
    BEGIN
      t.count := y;
    END Put;

  PROCEDURE Twice (y: INTEGER): INTEGER =
    PROCEDURE Inner (): INTEGER =
      BEGIN
        RETURN y;
      END Inner;
    BEGIN
      RETURN 2 * Inner ();
    END Twice;

  BEGIN
    Put (Twice (local));
  END Put;

PROCEDURE New (): T =
  BEGIN
    INC (counter);
    RETURN NEW (T, hidden := "END New;");
  END New;

BEGIN
END Decls.
