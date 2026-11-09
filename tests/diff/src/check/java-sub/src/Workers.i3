(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

INTERFACE Workers;

IMPORT JThread;

TYPE Thr = JThread.T OBJECT METHODS run () END;

<*EXTERNAL "java.lang.Thread.currentThread":JAVA*>
PROCEDURE Current (): Thr;

END Workers.
