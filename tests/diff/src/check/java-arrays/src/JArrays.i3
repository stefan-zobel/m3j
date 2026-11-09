(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.Arrays":JAVA*> INTERFACE JArrays;

IMPORT Java;

<*EXTERNAL "sort([J)V"*>
PROCEDURE sort (VAR a: ARRAY OF INTEGER);

<*EXTERNAL "sort([J)V"*>
PROCEDURE sortThree (VAR a: ARRAY [0 .. 2] OF INTEGER);

<*EXTERNAL "sort([Ljava/lang/Object;)V"*>
PROCEDURE sortRefs (VAR a: ARRAY OF REFANY);

PROCEDURE fill (VAR a: ARRAY OF Java.Int; v: Java.Int);

<*EXTERNAL "toString([J)Ljava/lang/String;"*>
PROCEDURE longs (READONLY a: ARRAY OF INTEGER): TEXT;

<*EXTERNAL "toString([I)Ljava/lang/String;"*>
PROCEDURE ints (a: ARRAY OF Java.Int): TEXT;

<*EXTERNAL "toString([S)Ljava/lang/String;"*>
PROCEDURE shorts (READONLY a: ARRAY OF Java.Short): TEXT;

<*EXTERNAL "toString([B)Ljava/lang/String;"*>
PROCEDURE bytes (READONLY a: ARRAY OF Java.Byte): TEXT;

<*EXTERNAL "toString([B)Ljava/lang/String;"*>
PROCEDURE chars (READONLY a: ARRAY OF CHAR): TEXT;

<*EXTERNAL "toString([C)Ljava/lang/String;"*>
PROCEDURE wides (READONLY a: ARRAY OF Java.Char): TEXT;

<*EXTERNAL "toString([Z)Ljava/lang/String;"*>
PROCEDURE bools (READONLY a: ARRAY OF BOOLEAN): TEXT;

<*EXTERNAL "toString([F)Ljava/lang/String;"*>
PROCEDURE reals (READONLY a: ARRAY OF REAL): TEXT;

<*EXTERNAL "toString([D)Ljava/lang/String;"*>
PROCEDURE doubles (READONLY a: ARRAY OF LONGREAL): TEXT;

<*EXTERNAL "copyOf([JI)[J"*>
PROCEDURE copyOf (READONLY a: ARRAY OF INTEGER; n: Java.Int): REF ARRAY OF INTEGER;

END JArrays.
