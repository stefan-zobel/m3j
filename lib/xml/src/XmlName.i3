(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The names of XML without a colon, as namespaces want them. The characters
   are those of XML 1.0 up to its fourth edition (appendix B). The fifth edition
   allows many more, but parsers still in use refuse those, among them the
   parser of the Java platform and expat, so a document that uses them cannot
   be relied on. Characters above 16_FFFF are not in the list. *)

INTERFACE XmlName;

PROCEDURE IsStart (ch: WIDECHAR): BOOLEAN;
(* Whether a name can begin with "ch". *)

PROCEDURE IsName (ch: WIDECHAR): BOOLEAN;
(* Whether a name can have "ch" after its first character. *)

PROCEDURE Valid (t: TEXT): BOOLEAN;
(* Whether "t" is a name. *)

END XmlName.
