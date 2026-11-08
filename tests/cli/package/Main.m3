(* Uses the interfaces of the source package pkg (-pkg pkg): the module of
   Pub and Priv is Impl.m3, which the compiler finds by its EXPORTS; Vars
   needs no module, and the compiler does not warn about it. *)

MODULE Main;

IMPORT IO, Fmt, Pub, Priv, Vars;

BEGIN
  IO.Put(Pub.Name() & " " & Fmt.Char(Priv.Sep) & " " & Fmt.Int(Vars.V) & "\n");
END Main.
