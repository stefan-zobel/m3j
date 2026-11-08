(* Uses two interfaces that have no module of their name. The module of
   Foo is FooWin32.m3, which exports it, as modules of cm3 for Windows are
   named; BarWin32.m3 does not export Bar and must not be compiled (Bar
   needs no module). *)

MODULE Main;

IMPORT IO, Foo, Bar;

BEGIN
  IO.Put(Foo.Name() & " " & Bar.Name & "\n");
END Main.
