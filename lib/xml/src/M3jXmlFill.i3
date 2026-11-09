(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The source of the bytes of a document, for the module XmlReader: its
   method "fill (max: Java.Int): REF ARRAY OF CHAR" returns at most "max"
   bytes, at least one, and NIL at the end of the input. *)

<*EXTERNAL "m3j.rt.Xml$Fill":JAVA*> INTERFACE M3jXmlFill;

IMPORT JavaObject;

TYPE T <: JavaObject.T;

END M3jXmlFill.
