(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.concurrent.ConcurrentHashMap":JAVA*> INTERFACE JMap;

IMPORT JavaObject;

TYPE
  T <: JavaObject.T;
  Merge = PROCEDURE (a, b: REFANY): REFANY;

PROCEDURE New (): T;

<*EXTERNAL "merge(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/BiFunction;)Ljava/lang/Object;"*>
PROCEDURE merge (self: T; key, value: REFANY; f: Merge): REFANY;

<*EXTERNAL "get(Ljava/lang/Object;)Ljava/lang/Object;"*>
PROCEDURE get (self: T; key: REFANY): REFANY;

END JMap.
