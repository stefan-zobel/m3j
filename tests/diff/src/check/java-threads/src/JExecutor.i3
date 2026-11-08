(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.concurrent.ExecutorService":JAVA*> INTERFACE JExecutor;

IMPORT JavaObject, JFuture;

TYPE
  T <: JavaObject.T;
  Task = PROCEDURE () RAISES ANY;

<*EXTERNAL "submit(Ljava/lang/Runnable;)Ljava/util/concurrent/Future;"*>
PROCEDURE submit (self: T; t: Task): JFuture.T;

PROCEDURE shutdown (self: T);

END JExecutor.
