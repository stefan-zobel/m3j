(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.Arrays":JAVA*> INTERFACE JArrays;

IMPORT Java;

TYPE IndexToLong = PROCEDURE (i: Java.Int): INTEGER;

<*EXTERNAL "sort([J)V"*>
PROCEDURE sortLongs (a: REFANY);

<*EXTERNAL "setAll([JLjava/util/function/IntToLongFunction;)V"*>
PROCEDURE setAllLongs (a: REFANY; f: IndexToLong);

<*EXTERNAL "toString([J)Ljava/lang/String;"*>
PROCEDURE longsToText (a: REFANY): TEXT;

<*EXTERNAL "toString([B)Ljava/lang/String;"*>
PROCEDURE bytesToText (a: REFANY): TEXT;

<*EXTERNAL "toString([D)Ljava/lang/String;"*>
PROCEDURE doublesToText (a: REFANY): TEXT;

<*EXTERNAL "toString([Ljava/lang/Object;)Ljava/lang/String;"*>
PROCEDURE objectsToText (a: REFANY): TEXT;

END JArrays.
