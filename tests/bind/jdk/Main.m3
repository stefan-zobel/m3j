(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* A program on the interfaces that m3jbind writes for classes of the JDK. *)

MODULE Main;

IMPORT ArrayList, List, StringBuilder, JavaObject;
IMPORT IO, Fmt;

VAR
  a := ArrayList.New ();
  l: List.T := a;
  b := StringBuilder.New ();

BEGIN
  (* a method that the class declares, and one of its supertype *)
  EVAL ArrayList.add_Object (a, "one");
  EVAL List.add_Object (l, "two");
  List.add_int_Object (l, 0, "zero");
  IO.Put (Fmt.Int (ArrayList.size (a)) & "\n");
  IO.Put (NARROW (ArrayList.get (a, 1), TEXT) & "\n");
  IO.Put (JavaObject.toString (a) & "\n");
  EVAL ArrayList.remove_int (a, 1);
  IO.Put (Fmt.Int (List.size (a)) & "\n");
  IO.Put (Fmt.Bool (ISTYPE (l, ArrayList.T)) & "\n");

  (* results that are the object itself; a method that exists as a bridge *)
  EVAL StringBuilder.append_int (StringBuilder.append_String (b, "x="), 42);
  EVAL StringBuilder.insert_int_String (b, 0, ">");
  IO.Put (StringBuilder.toString (b) & "\n");
  IO.Put (Fmt.Int (StringBuilder.length (b)) & "\n");
  IO.Put (StringBuilder.toString (StringBuilder.reverse (b)) & "\n");
END Main.
