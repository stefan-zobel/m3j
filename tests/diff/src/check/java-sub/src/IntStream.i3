(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.stream.IntStream":JAVA*> INTERFACE IntStream;

IMPORT Java, JavaObject;

TYPE
  T <: JavaObject.T;
  Op = PROCEDURE (x: Java.Int): Java.Int;

<*EXTERNAL "of([I)Ljava/util/stream/IntStream;"*>
PROCEDURE ofInts (READONLY a: ARRAY OF Java.Int): T;

PROCEDURE map (self: T; f: Op): T;
PROCEDURE sum (self: T): Java.Int;

END IntStream.
