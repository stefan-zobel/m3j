(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.io.File":JAVA*> INTERFACE JFile;

IMPORT JavaObject;

TYPE T <: JavaObject.T;

PROCEDURE listRoots (): REF ARRAY OF T;

END JFile.
