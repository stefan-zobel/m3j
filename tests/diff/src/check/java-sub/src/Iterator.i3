(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.Iterator":JAVA*> INTERFACE Iterator;

IMPORT JavaObject;

TYPE
  T <: JavaObject.T;
  Visit = PROCEDURE (x: REFANY);

PROCEDURE forEachRemaining (self: T; v: Visit);

END Iterator.
