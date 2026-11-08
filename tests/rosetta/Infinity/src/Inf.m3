(* Rosetta Code, task Infinity: https://rosettacode.org/wiki/Infinity (GNU Free Documentation License 1.3) *)
MODULE Inf EXPORTS Main;

IMPORT IO, IEEESpecial;

BEGIN
  IO.PutReal(IEEESpecial.RealPosInf);
  IO.Put("\n");
END Inf.
