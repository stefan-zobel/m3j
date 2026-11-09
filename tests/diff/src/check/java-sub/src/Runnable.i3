(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.lang.Runnable":JAVA*> INTERFACE Runnable;

IMPORT JavaObject;

TYPE T <: JavaObject.T;

PROCEDURE run (self: T);

END Runnable.
