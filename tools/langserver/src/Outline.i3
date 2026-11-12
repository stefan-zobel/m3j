(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The outline of a file (textDocument/documentSymbol): the declarations
   of its unit, as m3tk parsed them, with those of the types and the
   nested procedures inside them. *)

INTERFACE Outline;

IMPORT M3Context, M3AST_AS;

CONST
  (* the kinds of symbols of the protocol (SymbolKind) that are used *)
  Class      = 5;
  Method     = 6;
  Field      = 8;
  Enum       = 10;
  Function   = 12;
  Variable   = 13;
  Constant   = 14;
  EnumMember = 22;
  Struct     = 23;
  Event      = 24;
  TypeParam  = 26;

TYPE
  (* a place in the text: a line from 0 and a column in bytes, as m3tk
     counts them *)
  Pos = RECORD line, col: INTEGER := 0; END;
  Span = RECORD start, end: Pos; END;

  Symbol = REF RECORD
    name     : TEXT;                    (* bytes of UTF-8 *)
    detail   : TEXT    := NIL;          (* bytes of UTF-8, or NIL *)
    kind     : INTEGER;
    range    : Span;                    (* the whole declaration *)
    selection: Span;                    (* its identifier *)
    children : Symbols := NIL;
  END;

  Symbols = REF ARRAY OF Symbol;

PROCEDURE Get (c: M3Context.T; file, text: TEXT): Symbols;
(* The symbols of the unit of file (its pathname) in the context c, in
   the order of the text, or NIL if c has no unit of file. text is the
   text that m3tk compiled; the ends of the declarations, which m3tk
   does not keep, are found in it. *)

PROCEDURE Unit (c: M3Context.T; file: TEXT): M3AST_AS.Compilation_Unit;
(* The unit of file in the context c, or NIL. *)

PROCEDURE OfUnit (cu: M3AST_AS.Compilation_Unit; text: TEXT): Symbols;
(* The symbols of the unit cu, whose text is text (as for Get); an empty
   array if it has none. *)

PROCEDURE InCode (text: TEXT; offset: INTEGER): BOOLEAN;
(* FALSE if the place before the character at offset of text is in a
   comment, a pragma, a text or a character literal (also one that is
   not closed), else TRUE. *)

END Outline.
