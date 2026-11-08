(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* A program on the interfaces that m3jbind writes for classes of a class
   path of their own. *)

MODULE Main;

IMPORT Dot, Dot_Kind, Shape, Java, JavaThrowable;
IMPORT IO, Fmt;

VAR
  p := Dot.New2 (3, 4);
  q := Dot.plus_int (p, 1);
  r := Dot.plus_Dot (p, Dot.ORIGIN);
  k: Dot_Kind.T := Dot.kind (p);

BEGIN
  IO.Put (Dot.toString (p) & " " & Dot.toString (q) & " " & Dot.toString (r) & "\n");
  IO.Put (Fmt.Int (Dot.getX (Dot.New ())) & "\n");

  (* the names that are changed *)
  IO.Put (Fmt.Int (Dot.END_) & " " & Fmt.Int (Dot.count_) & " " & Fmt.Int (Dot.count (p)) & "\n");
  IO.Put (Fmt.Int (Dot.New_ (p, 10)) & "\n");

  (* the other constants; the types of Java *)
  IO.Put (Fmt.Int (Dot.LIMIT) & " " & Dot.LABEL & "\n");
  IO.Put (Fmt.LongReal (Dot.half (p)) & "\n");
  IO.Put (Fmt.Int (ORD (Dot.tag (p, W'a'))) & "\n");
  IO.Put (Fmt.Int (Dot.compareTo (p, q)) & "\n");
  IO.Put (Dot.toString (Dot.parse ("8,9")) & "\n");

  (* a nested enum, and a class that the method of a static result has *)
  IO.Put (Fmt.Bool (k = Dot_Kind.NEAR) & " " & Fmt.Bool (k = Dot_Kind.FAR) & "\n");
  IO.Put (Fmt.Int (Shape.sides (Shape.triangle ())) & "\n");

  (* an array in a REFANY *)
  VAR a := ARRAY [0 .. 2] OF INTEGER {1, 20, 300};
  BEGIN
    IO.Put (Fmt.Int (Dot.sum (Java.NewLongs (a))) & "\n");
  END;

  TRY
    Dot.fail (p);
  EXCEPT
  | Java.Exception (t) => IO.Put (JavaThrowable.getMessage (t) & "\n");
  END;
END Main.
