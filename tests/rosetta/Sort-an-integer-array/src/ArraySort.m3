(* Rosetta Code, task Sort-an-integer-array: https://rosettacode.org/wiki/Sort_an_integer_array (GNU Free Documentation License 1.3) *)
MODULE ArraySort EXPORTS Main;

IMPORT IntArraySort;

VAR arr := ARRAY [1..10] OF INTEGER{3, 6, 1, 2, 10, 7, 9, 4, 8, 5};

BEGIN
  IntArraySort.Sort(arr);
END ArraySort.
