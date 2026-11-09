(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.TimerTask":JAVA*> INTERFACE TimerTask;

IMPORT JavaObject;

TYPE T <: JavaObject.T;

PROCEDURE cancel (self: T): BOOLEAN;

END TimerTask.
