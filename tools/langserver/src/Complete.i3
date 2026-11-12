(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The completion of an identifier (textDocument/completion): after
   "I." the declarations of the interface that the unit imports as I;
   else the identifiers of the unit and of the interfaces it exports,
   the interfaces it imports and the names of its FROM imports, the
   predeclared identifiers and the reserved words. *)

INTERFACE Complete;

IMPORT M3Context;

CONST
  (* the kinds of items of the protocol (CompletionItemKind) that are
     used *)
  TextItem      = 1;
  Method        = 2;
  Function      = 3;
  Field         = 5;
  Variable      = 6;
  Class         = 7;
  Module        = 9;
  Enum          = 13;
  Keyword       = 14;
  EnumMember    = 20;
  Constant      = 21;
  Struct        = 22;
  Event         = 23;
  TypeParameter = 25;

TYPE
  Item = RECORD
    label : TEXT;                       (* bytes of UTF-8 *)
    detail: TEXT    := NIL;             (* bytes of UTF-8, or NIL *)
    kind  : INTEGER;
    sort  : TEXT;                       (* the group, "0" to "3" *)
  END;

  Items = REF ARRAY OF Item;

  (* the text of a file that is open in the editor, or NIL *)
  Texts = OBJECT METHODS get (file: TEXT): TEXT; END;

PROCEDURE Get (c    : M3Context.T;
               file : TEXT;
               text : TEXT;
               line : INTEGER;
               col  : INTEGER;
               texts: Texts        ): Items;
(* The items at line and col (from 0, col in bytes) of text, the text of
   file (its pathname) that m3tk compiled in the context c; NIL if c has
   no unit of file, an empty array if there is nothing to complete there
   (in a comment or a text, or after a "." that does not follow the name
   of an imported interface). The client filters the items by what is
   typed. The texts of other units are those of texts, else those of
   their files. Not for several threads at once: the caller holds the
   lock of m3tk. *)

PROCEDURE Parsed (c: M3Context.T; file, text: TEXT);
(* After m3tk compiled text, the text of file, in the context c: if it
   parsed it without errors, its declarations are noted for Get, which
   adds them while the unit has errors of the parser (whose recovery
   drops the declarations after an error, as long as one types). *)

END Complete.
