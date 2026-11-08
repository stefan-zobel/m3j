(* Rosetta Code, task Loop-over-multiple-arrays-simultaneously: https://rosettacode.org/wiki/Loop_over_multiple_arrays_simultaneously (GNU Free Documentation License 1.3) *)
MODULE MultiArray EXPORTS Main;

IMPORT IO, Fmt;

TYPE ArrIdx = [1..3];

VAR
  arr1 := ARRAY ArrIdx OF CHAR {'a', 'b', 'c'};
  arr2 := ARRAY ArrIdx OF CHAR {'A', 'B', 'C'};
  arr3 := ARRAY ArrIdx OF INTEGER {1, 2, 3};

BEGIN
  FOR i := FIRST(ArrIdx) TO LAST(ArrIdx) DO
    IO.Put(Fmt.Char(arr1[i]) & Fmt.Char(arr2[i]) &
Fmt.Int(arr3[i]) & "\n");
  END;
END MultiArray.
