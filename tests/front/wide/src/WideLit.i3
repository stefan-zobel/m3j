INTERFACE WideLit;

CONST
  Greeting = W"Gr\x00fc\x00dfe, \x20ac and \U01F600";
  Plain = W"plain";
  Euro = W'\x20ac';
  Mixed = "narrow " & W"and wide";
  Empty = W"";

TYPE
  T <: ROOT;
  Branded = BRANDED W"WideLit.Branded" REF INTEGER;
  Special = BRANDED W"Br\x00e4nd \t\n\r\f\\\"\'\x0001\x007f\x0080\x00ff\x20ac" OBJECT
    s: TEXT := W"\x20ac\t\x0000\\";
  METHODS
    m (t: TEXT := W"\x00e4\"\n";  c: WIDECHAR := W'\x0007') := Method;
  END;
  Proc = PROCEDURE (t: TEXT := W"\x4e2d\x6587";  c := W'\'');

PROCEDURE Run (): TEXT;

PROCEDURE Method (self: Special;  t: TEXT := W"\x00e4\"\n";  c: WIDECHAR := W'\x0007');

END WideLit.
