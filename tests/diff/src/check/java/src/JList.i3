(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.List":JAVA*> INTERFACE JList;

IMPORT Java, JavaObject;

TYPE
  T <: JavaObject.T;
  Each = PROCEDURE (x: REFANY);

PROCEDURE add (self: T; e: REFANY): BOOLEAN;
PROCEDURE get (self: T; i: Java.Int): REFANY;
PROCEDURE size (self: T): Java.Int;
PROCEDURE forEach (self: T; f: Each);

END JList.
