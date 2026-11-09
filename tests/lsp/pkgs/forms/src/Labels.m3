MODULE Labels;

(* The same value written in several ways: exactly these labels overlap
   if the value of a wide literal is read correctly. *)

VAR
  c: WIDECHAR := W'A';
  n := 0;

BEGIN
  CASE c OF
  | W'\x0041' => n := 1;
  | W'A' => n := 2;
  | W'\000101' => n := 3;
  | W'\x0043' => n := 4;
  | W'\n' => n := 5;
  | W'\x000A' => n := 6;
  ELSE
  END;
END Labels.
