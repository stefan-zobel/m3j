(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.lang.StringBuilder":JAVA*> INTERFACE StringBuilder;

IMPORT JavaObject, CharSequence;

TYPE T <: JavaObject.T;

PROCEDURE New (): T;
PROCEDURE append (self: T; s: CharSequence.T): T;

<*EXTERNAL "append(Ljava/lang/CharSequence;)Ljava/lang/StringBuilder;"*>
PROCEDURE appendRaises (self: T; s: CharSequence.T): T RAISES ANY;

PROCEDURE toString (self: T): TEXT;

END StringBuilder.
