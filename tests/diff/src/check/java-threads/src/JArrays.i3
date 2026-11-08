(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.Arrays":JAVA*> INTERFACE JArrays;

IMPORT Java;

TYPE CompareText = PROCEDURE (a, b: TEXT): Java.Int;

<*EXTERNAL "parallelSort([Ljava/lang/Object;Ljava/util/Comparator;)V"*>
PROCEDURE parallelSort (a: REFANY; c: CompareText);

END JArrays.
