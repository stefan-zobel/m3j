(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.stream.IntStream":JAVA*> INTERFACE JIntStream;

IMPORT Java, JavaObject;

TYPE
  T <: JavaObject.T;
  Consumer = PROCEDURE (i: Java.Int) RAISES ANY;
  Op       = PROCEDURE (i: Java.Int): Java.Int;

<*EXTERNAL "range(II)Ljava/util/stream/IntStream;"*>
PROCEDURE range (from, to: Java.Int): T;

<*EXTERNAL "parallel()Ljava/util/stream/IntStream;"*>
PROCEDURE parallel (self: T): T;

<*EXTERNAL "forEach(Ljava/util/function/IntConsumer;)V"*>
PROCEDURE forEach (self: T; c: Consumer);

<*EXTERNAL "forEach(Ljava/util/function/IntConsumer;)V"*>
PROCEDURE forEachRaises (self: T; c: Consumer) RAISES ANY;

<*EXTERNAL "map(Ljava/util/function/IntUnaryOperator;)Ljava/util/stream/IntStream;"*>
PROCEDURE map (self: T; f: Op): T;

<*EXTERNAL "sum()I"*>
PROCEDURE sum (self: T): Java.Int;

END JIntStream.
