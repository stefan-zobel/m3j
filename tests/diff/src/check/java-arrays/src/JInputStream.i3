(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.io.InputStream":JAVA*> INTERFACE JInputStream;

IMPORT Java, JavaObject;

TYPE T <: JavaObject.T;

<*EXTERNAL "read([B)I"*>
PROCEDURE readInto (self: T; VAR b: ARRAY OF CHAR): Java.Int;

PROCEDURE readAllBytes (self: T): REF ARRAY OF CHAR;

END JInputStream.
