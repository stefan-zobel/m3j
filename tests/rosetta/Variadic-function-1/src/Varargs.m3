(* Rosetta Code, task Variadic-function: https://rosettacode.org/wiki/Variadic_function (GNU Free Documentation License 1.3) *)
MODULE Varargs EXPORTS Main;

IMPORT IO;

VAR strings := ARRAY [1..5] OF TEXT {"foo", "bar", "baz", "quux", "zeepf"};

PROCEDURE Variable(VAR arr: ARRAY OF TEXT) =
  BEGIN
    FOR i := FIRST(arr) TO LAST(arr) DO
      IO.Put(arr[i] & "\n");
    END;
  END Variable;

BEGIN
  Variable(strings);
END Varargs.
