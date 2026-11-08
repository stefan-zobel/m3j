(* Rosetta Code, task Towers-of-Hanoi: https://rosettacode.org/wiki/Towers_of_Hanoi (GNU Free Documentation License 1.3) *)
MODULE Hanoi EXPORTS Main;

FROM IO IMPORT Put;
FROM Fmt IMPORT Int;

PROCEDURE doHanoi(n, from, to, using: INTEGER) =
  BEGIN
    IF n > 0 THEN
      doHanoi(n - 1, from, using, to);
      Put("move " & Int(from) & " --> " & Int(to) & "\n");
      doHanoi(n - 1, using, to, from);
    END;
  END doHanoi;

BEGIN
  doHanoi(4, 1, 2, 3);
END Hanoi.
