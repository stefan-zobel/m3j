MODULE WideErrors EXPORTS Main;

CONST
  C1 = W'\x41';
  C2 = W'\101';
  C3 = W'\U110000';
  C4 = W'\U10FFFF';
  C5 = W'';
  C6 = W'\q';
  C7 = W'ab';
  C8 = W'\777777';
  C9 = '\777';
  C10 = '\x4';
  C11 = W'\x4g';
  C12 = W'\U12';
  T1 = W"\x41 and \101";
  T2 = W"\U110000\U01F600\q";
  T3 = W"\000101\x0041\x00";
  T4 = W"unterminated
  ;
  T5 = W"\777777";

BEGIN
END WideErrors.
