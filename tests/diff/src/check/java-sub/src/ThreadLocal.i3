(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.lang.ThreadLocal":JAVA*> INTERFACE ThreadLocal;

IMPORT JavaObject;

TYPE T <: JavaObject.T;

PROCEDURE get (self: T): REFANY;

END ThreadLocal.
