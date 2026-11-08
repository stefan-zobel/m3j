(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The class java.lang.Throwable, the argument of Java.Exception. *)

<*EXTERNAL "java.lang.Throwable":JAVA*> INTERFACE JavaThrowable;

IMPORT JavaObject;

TYPE T <: JavaObject.T;

PROCEDURE getMessage (self: T): TEXT;
PROCEDURE getCause (self: T): T;
PROCEDURE toString (self: T): TEXT;
PROCEDURE printStackTrace (self: T);

END JavaThrowable.
