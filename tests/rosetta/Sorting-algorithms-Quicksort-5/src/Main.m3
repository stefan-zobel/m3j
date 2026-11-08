(* Rosetta Code, task Sorting-algorithms-Quicksort: https://rosettacode.org/wiki/Sorting_algorithms_Quicksort (GNU Free Documentation License 1.3) *)
MODULE Main;

IMPORT IO, TextSort;

VAR arr := ARRAY [1..10] OF TEXT {"Foo", "bar", "!ooF", "Modula-3", "hickup",
                                 "baz", "quuz", "Zeepf", "woo", "Rosetta Code"};

BEGIN
  TextSort.Sort(arr);
  FOR i := FIRST(arr) TO LAST(arr) DO
    IO.Put(arr[i] & "\n");
  END;
END Main.
