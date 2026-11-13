(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j: "unit" is the pathname of the file of the unit;
   SameFile; TypeText. *)

INTERFACE Feature;

IMPORT M3Context, M3AST_AS;

PROCEDURE GetSymbolType(c: M3Context.T; unit : TEXT; line,col : INTEGER;
                        VAR startCol,len : INTEGER) : TEXT;
PROCEDURE GetDeclaration(c: M3Context.T; unit : TEXT; line,col : INTEGER;
                         VAR defLine,defCol,defLen : INTEGER) : TEXT;
PROCEDURE GetTypeDefinition(c: M3Context.T; unit : TEXT; line,col : INTEGER;
                            VAR defLine,defCol,defLen : INTEGER) : TEXT;

PROCEDURE TypeText(c: M3Context.T; type: M3AST_AS.M3TYPE) : TEXT;
(* the type as the hover shows it: by its name if it has one; NIL if
   type is NIL *)

PROCEDURE SameFile(a, b : TEXT) : BOOLEAN;
(* a and b are pathnames of the same file: equal, or on Windows equal
   but for case; FALSE if one of them is NIL *)

END Feature.
