(* WITH binds a name to a packed element whose bit offset is known only at
   run time: a restriction that the compiler reports (a packed field and an
   element at a constant index are bound). *)

MODULE PackedWith EXPORTS Main;

TYPE A = ARRAY [0..9] OF BITS 3 FOR [0..7];

VAR a: A; i := 3;

BEGIN
  WITH e = a[i] DO e := 1 END;
END PackedWith.
