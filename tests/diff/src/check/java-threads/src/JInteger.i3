(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.lang.Integer":JAVA*> INTERFACE JInteger;

IMPORT Java;

<*EXTERNAL "parseInt(Ljava/lang/String;)I"*>
PROCEDURE parseInt (s: TEXT): Java.Int;

END JInteger.
