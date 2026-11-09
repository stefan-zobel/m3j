(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.lang.Thread":JAVA*> INTERFACE JThread;

IMPORT Java, JavaObject;

TYPE T <: JavaObject.T;

PROCEDURE enumerate (VAR a: ARRAY OF T): Java.Int;

END JThread.
