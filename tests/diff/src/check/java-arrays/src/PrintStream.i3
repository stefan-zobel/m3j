(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.io.PrintStream":JAVA*> INTERFACE PrintStream;

IMPORT JOutputStream;

TYPE T <: JOutputStream.T;

<*EXTERNAL "<init>(Ljava/io/OutputStream;)V"*>
PROCEDURE New (o: JOutputStream.T): T;

<*EXTERNAL "print(Ljava/lang/String;)V"*>
PROCEDURE print (self: T; s: TEXT);

PROCEDURE flush (self: T);

END PrintStream.
