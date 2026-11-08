(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.ArrayList":JAVA*> INTERFACE JArrayList;

IMPORT JList;

TYPE T <: JList.T;

PROCEDURE New (): T;

END JArrayList.
