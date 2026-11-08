(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.lang.String":JAVA*> INTERFACE JString;

IMPORT Java;

PROCEDURE length (self: TEXT): Java.Int;
PROCEDURE charAt (self: TEXT; i: Java.Int): Java.Char;
PROCEDURE substring (self: TEXT; from, to: Java.Int): TEXT;
PROCEDURE toUpperCase (self: TEXT): TEXT;

END JString.
