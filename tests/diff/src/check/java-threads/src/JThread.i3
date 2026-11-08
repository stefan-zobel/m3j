(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.lang.Thread":JAVA*> INTERFACE JThread;

IMPORT JavaObject;

TYPE
  T <: JavaObject.T;
  Body = PROCEDURE ();
  BodyRaises = PROCEDURE () RAISES ANY;

<*EXTERNAL "<init>(Ljava/lang/Runnable;)V"*>
PROCEDURE New (b: Body): T;

<*EXTERNAL "<init>(Ljava/lang/Runnable;)V"*>
PROCEDURE NewRaises (b: BodyRaises): T;

PROCEDURE start (self: T);
PROCEDURE join (self: T);

END JThread.
