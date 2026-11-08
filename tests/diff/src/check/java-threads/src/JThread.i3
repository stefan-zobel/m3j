(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.lang.Thread":JAVA*> INTERFACE JThread;

IMPORT JavaObject;

TYPE
  T <: JavaObject.T;
  Body = PROCEDURE ();

<*EXTERNAL "<init>(Ljava/lang/Runnable;)V"*>
PROCEDURE New (b: Body): T;

PROCEDURE start (self: T);
PROCEDURE join (self: T);

END JThread.
