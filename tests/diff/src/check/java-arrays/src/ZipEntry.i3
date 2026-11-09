(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.zip.ZipEntry":JAVA*> INTERFACE ZipEntry;

IMPORT JavaObject;

TYPE T <: JavaObject.T;

<*EXTERNAL "<init>(Ljava/lang/String;)V"*>
PROCEDURE New (name: TEXT): T;

PROCEDURE getExtra (self: T): REF ARRAY OF CHAR;
PROCEDURE setExtra (self: T; READONLY b: ARRAY OF CHAR);

END ZipEntry.
