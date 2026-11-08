(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.concurrent.Future":JAVA*> INTERFACE JFuture;

IMPORT JavaObject;

TYPE T <: JavaObject.T;

<*EXTERNAL "get()Ljava/lang/Object;"*>
PROCEDURE get (self: T): REFANY;

END JFuture.
