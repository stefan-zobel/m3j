(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.lang.Thread":JAVA*> INTERFACE JThread;

IMPORT JavaObject, Runnable;

TYPE T <: JavaObject.T;

<*EXTERNAL "<init>(Ljava/lang/Runnable;)V"*>
PROCEDURE NewR (r: Runnable.T): T;

PROCEDURE start (self: T);
PROCEDURE join (self: T);

END JThread.
