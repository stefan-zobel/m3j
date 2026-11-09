(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The layer on the parser of the Java platform, for the module XmlReader.
   "next" returns the number of a "XmlReader.Kind", or -1 after an error,
   which "problem" (the number of a "XmlReader.Problem"), "errorLine",
   "errorColumn" and "errorMessage" describe. "local", "uri", "prefix" and
   "lookup" are for the tag that "next" has just returned, the attributes
   for a start tag. A limit of 0 means no limit. *)

<*EXTERNAL "m3j.rt.Xml":JAVA*> INTERFACE M3jXml;

IMPORT Java, JavaObject, M3jXmlFill;

TYPE T <: JavaObject.T;

PROCEDURE fromFill (fill: M3jXmlFill.T;
                    comments, instructions, whitespace, rejectDoctype: BOOLEAN;
                    maxDepth, maxAttributes, maxNameChars, maxTokenChars: INTEGER): T;

PROCEDURE fromText (text: TEXT;
                    comments, instructions, whitespace, rejectDoctype: BOOLEAN;
                    maxDepth, maxAttributes, maxNameChars, maxTokenChars: INTEGER): T;

PROCEDURE next (self: T): Java.Int;
PROCEDURE close (self: T);

PROCEDURE problem (self: T): Java.Int;
PROCEDURE errorLine (self: T): Java.Int;
PROCEDURE errorColumn (self: T): Java.Int;
PROCEDURE errorMessage (self: T): TEXT;

PROCEDURE value (self: T): TEXT;
PROCEDURE target (self: T): TEXT;
PROCEDURE version (self: T): TEXT;
PROCEDURE encoding (self: T): TEXT;

PROCEDURE line (self: T): Java.Int;
PROCEDURE column (self: T): Java.Int;
PROCEDURE offset (self: T): Java.Int;

PROCEDURE local (self: T): TEXT;
PROCEDURE uri (self: T): TEXT;
PROCEDURE prefix (self: T): TEXT;
PROCEDURE lookup (self: T; prefix: TEXT): TEXT;

PROCEDURE attrCount (self: T): Java.Int;
PROCEDURE attrLocal (self: T; i: Java.Int): TEXT;
PROCEDURE attrUri (self: T; i: Java.Int): TEXT;
PROCEDURE attrPrefix (self: T; i: Java.Int): TEXT;
PROCEDURE attrValue (self: T; i: Java.Int): TEXT;

END M3jXml.
