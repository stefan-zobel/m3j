(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.lang.StringBuilder":JAVA*> INTERFACE JStringBuilder;

IMPORT Java, JavaObject;

TYPE T <: JavaObject.T;

PROCEDURE New (): T;

<*EXTERNAL "append(Ljava/lang/String;)Ljava/lang/StringBuilder;"*>
PROCEDURE append (self: T; s: TEXT): T;

PROCEDURE length (self: T): Java.Int;
PROCEDURE toString (self: T): TEXT;

END JStringBuilder.
