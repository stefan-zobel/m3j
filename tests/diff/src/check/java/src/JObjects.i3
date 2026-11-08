(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.Objects":JAVA*> INTERFACE JObjects;

PROCEDURE requireNonNull (x: REFANY): REFANY;
PROCEDURE isNull (x: REFANY): BOOLEAN;

END JObjects.
