(* The modules G and B, which export G, import A, which imports G, and
   AImpl, which exports A, imports G: a cycle of imports. The interface A
   must be initialized (its variables allocated) before AImpl, which sets
   them, and IO before AImpl, which calls G.Put, although AImpl is in the
   cycle and IO is not. *)

MODULE Main;

IMPORT G, A;

BEGIN
  G.Dump();
  INC(A.arr[1], A.x);
  G.Dump();
  G.Put("sum", G.Sum());
END Main.
