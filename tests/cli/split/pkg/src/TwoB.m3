(* A second module of Two, in the same directory: B. *)

MODULE TwoB EXPORTS Two;

PROCEDURE B (): TEXT =
  BEGIN
    RETURN "B";
  END B;

BEGIN
END TwoB.
