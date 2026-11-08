(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.concurrent.Executors":JAVA*> INTERFACE JExecutors;

IMPORT Java, JExecutor;

<*EXTERNAL "newFixedThreadPool(I)Ljava/util/concurrent/ExecutorService;"*>
PROCEDURE newFixedThreadPool (n: Java.Int): JExecutor.T;

END JExecutors.
