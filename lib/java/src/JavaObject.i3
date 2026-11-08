(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The class java.lang.Object: the supertype of the types of the
   bindings of Java classes. *)

<*EXTERNAL "java.lang.Object":JAVA*> INTERFACE JavaObject;

TYPE T <: REFANY;

PROCEDURE equals (self: T; other: REFANY): BOOLEAN;
PROCEDURE hashCode (self: T): INTEGER;
PROCEDURE toString (self: T): TEXT;
PROCEDURE getClass (self: T): REFANY;

END JavaObject.
