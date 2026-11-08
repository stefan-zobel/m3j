(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

<*EXTERNAL "java.util.Collections":JAVA*> INTERFACE JCollections;

IMPORT Java, JList;

TYPE
  Compare     = PROCEDURE (a, b: REFANY): Java.Int;
  CompareText = PROCEDURE (a, b: TEXT): Java.Int;
  CompareWide = PROCEDURE (a, b: REFANY): INTEGER;
  CompareAny  = PROCEDURE (a, b: REFANY): Java.Int RAISES ANY;

PROCEDURE sort (l: JList.T; c: Compare);

<*EXTERNAL "sort"*>
PROCEDURE sortText (l: JList.T; c: CompareText);

<*EXTERNAL "sort"*>
PROCEDURE sortWide (l: JList.T; c: CompareWide);

<*EXTERNAL "sort"*>
PROCEDURE sortAny (l: JList.T; c: CompareAny) RAISES ANY;

END JCollections.
