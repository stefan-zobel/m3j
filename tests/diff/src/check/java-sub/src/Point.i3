(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.awt.Point":JAVA*> INTERFACE Point;

IMPORT Java, JavaObject;

TYPE T <: JavaObject.T OBJECT x, y: Java.Int END;

PROCEDURE New (x, y: Java.Int): T;

PROCEDURE getX (self: T): LONGREAL;
PROCEDURE getY (self: T): LONGREAL;

END Point.
