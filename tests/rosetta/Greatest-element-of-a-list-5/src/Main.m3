(* Rosetta Code, task Greatest-element-of-a-list: https://rosettacode.org/wiki/Greatest_element_of_a_list (GNU Free Documentation License 1.3) *)
MODULE Main;

IMPORT RealMax, IO, Fmt;

VAR realarr := ARRAY [1..5] OF REAL {1.1, 1.0, 0.0, 2.4, 3.3};

BEGIN
  IO.Put(Fmt.Real(RealMax.Max(realarr)) & "\n");
END Main.
