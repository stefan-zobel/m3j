(* A module of Two in another directory, which the compiler does not take
   (with it, B would be exported twice). *)

MODULE TwoOther EXPORTS Two;

PROCEDURE B (): TEXT =
  BEGIN
    RETURN "other";
  END B;

BEGIN
END TwoOther.
