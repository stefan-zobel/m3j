(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.Collections":JAVA*> INTERFACE Collections;

IMPORT AbstractList, Comparator;

PROCEDURE max (c: AbstractList.T; cmp: Comparator.T): REFANY;

END Collections.
