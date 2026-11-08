(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

INTERFACE JMath;

IMPORT Java;

<*EXTERNAL "java.lang.Math.max(JJ)J":JAVA*>
PROCEDURE Max (a, b: INTEGER): INTEGER;

<*EXTERNAL "java.lang.Math.abs(I)I":JAVA*>
PROCEDURE AbsInt (a: INTEGER): Java.Int;

<*EXTERNAL "java.lang.Math.sqrt":JAVA*>
PROCEDURE Sqrt (x: LONGREAL): LONGREAL;

<*EXTERNAL "java.lang.Math.PI":JAVA*>
VAR Pi: LONGREAL;

END JMath.
