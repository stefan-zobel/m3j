(* Rosetta Code, task Odd-and-square-numbers: https://rosettacode.org/wiki/Odd_and_square_numbers (GNU Free Documentation License 1.3) *)
MODULE OddSquare EXPORTS Main;

IMPORT IO;

VAR N,Square:CARDINAL;

BEGIN
     N := 10;
     LOOP
          Square := N * N;
          IF Square > 1000 THEN EXIT END;
          IF Square MOD 2 = 1 THEN
               IO.PutInt(Square);
               IO.Put("\n");
          END;
          N := N + 1
     END
END OddSquare.
