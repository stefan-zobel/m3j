(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.List":JAVA*> INTERFACE JList;

IMPORT JavaObject;

TYPE T <: JavaObject.T;

<*EXTERNAL "of([Ljava/lang/Object;)Ljava/util/List;"*>
PROCEDURE ofArray (READONLY a: ARRAY OF REFANY): T;

END JList.
