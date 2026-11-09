(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.lang.Math":JAVA*> INTERFACE JMath;

IMPORT Java;

<*EXTERNAL "abs(I)I"*>
PROCEDURE absInt (x: Java.Int): Java.Int;

END JMath.
