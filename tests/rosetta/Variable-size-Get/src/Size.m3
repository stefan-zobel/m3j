(* Rosetta Code, task Variable-size-Get: https://rosettacode.org/wiki/Variable_size_Get (GNU Free Documentation License 1.3) *)
MODULE Size EXPORTS Main;

FROM IO IMPORT Put;
FROM Fmt IMPORT Int;

BEGIN
  Put("Integer in bits: " & Int(BITSIZE(INTEGER)) & "\n");
  Put("Integer in bytes: " & Int(BYTESIZE(INTEGER)) & "\n");
END Size.
