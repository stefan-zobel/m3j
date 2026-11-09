(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.awt.Dimension":JAVA*> INTERFACE Dimension;

IMPORT Java, JavaObject;

TYPE T <: JavaObject.T OBJECT width, height: Java.Int END;

PROCEDURE New (w, h: Java.Int): T;

END Dimension.
