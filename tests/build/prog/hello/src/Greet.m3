MODULE Greet;

PROCEDURE Hello (who: TEXT): TEXT =
  BEGIN
    RETURN "hello, " & who;
  END Hello;

BEGIN
END Greet.
