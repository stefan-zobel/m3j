(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j: "unit" is the pathname of the file of the unit;
   SameFile. *)

INTERFACE Feature;

IMPORT M3Context;

PROCEDURE GetSymbolType(c: M3Context.T; unit : TEXT; line,col : INTEGER;
                        VAR startCol,len : INTEGER) : TEXT;
PROCEDURE GetDeclaration(c: M3Context.T; unit : TEXT; line,col : INTEGER;
                         VAR defLine,defCol,defLen : INTEGER) : TEXT;
PROCEDURE GetTypeDefinition(c: M3Context.T; unit : TEXT; line,col : INTEGER;
                            VAR defLine,defCol,defLen : INTEGER) : TEXT;

PROCEDURE SameFile(a, b : TEXT) : BOOLEAN;
(* a and b are pathnames of the same file: equal, or on Windows equal
   but for case; FALSE if one of them is NIL *)

END Feature.
