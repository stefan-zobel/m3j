(* Rosetta Code, task Host-introspection: https://rosettacode.org/wiki/Host_introspection (GNU Free Documentation License 1.3) *)
MODULE Host EXPORTS Main;

IMPORT IO, Fmt, Word, Swap;

BEGIN
  IO.Put("Word Size: " & Fmt.Int(Word.Size) & "\n");
  IF Swap.endian = Swap.Endian.Big THEN
    IO.Put("Endianness: Big\n");
  ELSE
    IO.Put("Endianness: Little\n");
  END;
END Host.
