(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.lang.Integer":JAVA*> INTERFACE JInteger;

IMPORT Java, JavaObject;

TYPE T <: JavaObject.T;

<*EXTERNAL*> VAR MAX_VALUE: Java.Int;

PROCEDURE parseInt (s: TEXT): Java.Int;

<*EXTERNAL "toString(I)Ljava/lang/String;"*>
PROCEDURE ToText (i: Java.Int): TEXT;

END JInteger.
