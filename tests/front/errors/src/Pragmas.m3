MODULE Pragmas EXPORTS Main;

VAR v := 1;

<*LINE x *>
<*LINE 99999999999999999999 *>
<*LINE 20 *>

PROCEDURE P () =
  VAR unused: INTEGER;
  BEGIN
    <*NOWARN*> v := 2;
    <*NOWARN junk *>
    v := 3;
  END P;

<*LINE 5 "named.m3" junk *>
PROCEDURE Q () =
  BEGIN
    v := 4;
  END Q;

BEGIN
  P ();
  Q ();
END Pragmas.
