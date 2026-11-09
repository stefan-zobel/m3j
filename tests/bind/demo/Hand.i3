(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* What m3jbind does not write: a procedure for a functional interface. *)

<*EXTERNAL "demo.Dot":JAVA*> INTERFACE Hand;

IMPORT Java;

TYPE F = PROCEDURE (READONLY b: ARRAY OF CHAR): Java.Int;

PROCEDURE applyTo (f: F; READONLY b: ARRAY OF CHAR): Java.Int;

END Hand.
