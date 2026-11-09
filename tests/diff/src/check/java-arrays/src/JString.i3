(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.lang.String":JAVA*> INTERFACE JString;

PROCEDURE toCharArray (self: TEXT): REF ARRAY OF WIDECHAR;

<*EXTERNAL "getBytes()[B"*>
PROCEDURE getBytes (self: TEXT): REF ARRAY OF CHAR;

<*EXTERNAL "split(Ljava/lang/String;)[Ljava/lang/String;"*>
PROCEDURE split (self: TEXT; regex: TEXT): REF ARRAY OF TEXT;

<*EXTERNAL "valueOf([C)Ljava/lang/String;"*>
PROCEDURE ofChars (READONLY a: ARRAY OF WIDECHAR): TEXT;

END JString.
