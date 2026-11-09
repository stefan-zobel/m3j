(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* What the bindings of Java classes share: the Java types as Modula-3
   types, and the exception that stands for a Java exception. *)

INTERFACE Java;

IMPORT JavaThrowable;

TYPE
  Long    = INTEGER;
  Int     = [-16_7FFFFFFF - 1 .. 16_7FFFFFFF];
  Short   = [-16_8000 .. 16_7FFF];
  Byte    = [-16_80 .. 16_7F];
  Char    = WIDECHAR;
  Boolean = BOOLEAN;
  Float   = REAL;
  Double  = LONGREAL;

<*IMPLICIT*>
EXCEPTION Exception (JavaThrowable.T);
(* Raised by a call of a Java method that ends with a Java exception. As
   it is implicit, it need not be listed in RAISES clauses. Exceptions
   that stand for checked runtime errors (an index out of bounds, a
   reference to NIL, ...) are that runtime error instead. *)

(* The methods that every Java object has, for any reference. *)

PROCEDURE ToText (x: REFANY): TEXT;
(* x.toString (), or "null" if x is NIL. *)

PROCEDURE Equals (a, b: REFANY): BOOLEAN;
(* a.equals (b), or TRUE if both are NIL. *)

PROCEDURE HashCode (x: REFANY): INTEGER;
(* x.hashCode (), or 0 if x is NIL. *)

PROCEDURE ClassName (x: REFANY): TEXT;
(* The name of the class of the object x. *)

PROCEDURE IsInstance (x: REFANY; class: TEXT): BOOLEAN;
(* TRUE if x is an instance of the class or interface with that name.
   Never an error: a name that no class has is simply not x's. *)

(* Java has no references to scalars: a collection holds boxes. *)

PROCEDURE FromInt (i: Int): REFANY;
(* A java.lang.Integer. *)

PROCEDURE FromLong (i: INTEGER): REFANY;
(* A java.lang.Long. *)

PROCEDURE ToInt (x: REFANY): INTEGER;
(* The value of the java.lang.Number x; a checked runtime error if x is
   anything else. *)

(* A binding can have Modula-3 arrays as parameters and results, which are
   copied at the call. An array that fits none of them (of several
   dimensions, or of a class without a binding) is held in a REFANY: a
   binding can pass it for a parameter of an array type, and it is copied
   from and to Modula-3 arrays here. *)

PROCEDURE NewLongs (READONLY a: ARRAY OF INTEGER): REFANY;
PROCEDURE NewBytes (READONLY a: ARRAY OF CHAR): REFANY;
PROCEDURE NewDoubles (READONLY a: ARRAY OF LONGREAL): REFANY;
PROCEDURE NewStrings (READONLY a: ARRAY OF TEXT): REFANY;
(* A new long[], byte[], double[] or String[] with the elements of a. *)

PROCEDURE Length (array: REFANY): CARDINAL;
(* The length of the Java array; a checked runtime error if array is
   anything else. *)

PROCEDURE GetLongs (array: REFANY; VAR a: ARRAY OF INTEGER): CARDINAL;
PROCEDURE GetBytes (array: REFANY; VAR a: ARRAY OF CHAR): CARDINAL;
PROCEDURE GetDoubles (array: REFANY; VAR a: ARRAY OF LONGREAL): CARDINAL;
PROCEDURE GetStrings (array: REFANY; VAR a: ARRAY OF TEXT): CARDINAL;
(* Copies the elements of the Java array to the start of a, as many as
   both have, and returns their number; a checked runtime error if array
   is not a Java array of that type. *)

END Java.
