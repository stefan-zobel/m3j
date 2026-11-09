(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.io.ByteArrayInputStream":JAVA*> INTERFACE ByteIn;

IMPORT JInputStream;

TYPE T <: JInputStream.T;

<*EXTERNAL "<init>([B)V"*>
PROCEDURE New (READONLY a: ARRAY OF CHAR): T;

END ByteIn.
