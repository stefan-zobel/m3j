(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.HashMap":JAVA*> INTERFACE JHashMap;

IMPORT JavaObject;

TYPE
  T <: JavaObject.T;
  Compute = PROCEDURE (key: REFANY): REFANY;

PROCEDURE New (): T;
PROCEDURE computeIfAbsent (self: T; key: REFANY; f: Compute): REFANY;
PROCEDURE size (self: T): INTEGER;

END JHashMap.
