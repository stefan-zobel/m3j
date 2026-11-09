MODULE Main;

(* Wide literals, and an object type made from a name in NEW. *)

IMPORT Thread;

CONST
  Plain = W'a';
  Newline = W'\n';
  Hex = W'\x00E4';
  Octal = W'\000344';
  Message = W"wide \x00E4 text";

VAR
  mutex := NEW (Thread.Mutex OBJECT count: INTEGER := 0; END);
  c: WIDECHAR := Hex;
  t: TEXT := Message;

BEGIN
  IF c = Plain OR c = Newline OR c = Octal OR t = NIL THEN
    INC (mutex.count);
  END;
END Main.
