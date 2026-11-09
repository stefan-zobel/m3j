(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

INTERFACE Bound;

IMPORT Java;

<*EXTERNAL "java.util.Objects.hashCode":JAVA*>
PROCEDURE Hash (o: REFANY): Java.Int;

(* a method of java.lang.Object is final: this static method has its name and descriptor *)
<*EXTERNAL "java.lang.System.gc":JAVA*>
PROCEDURE notifyAll ();

END Bound.
