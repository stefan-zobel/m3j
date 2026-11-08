(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* The procedures of Pathname for the pathnames of Windows (the module
   PathnameWin32 of libm3, changed to export this interface), which the
   module Pathname of m3j calls when the program runs on Windows. *)

INTERFACE PathnameWin32;

FROM Pathname IMPORT T, Arcs, Invalid;

PROCEDURE Valid(pn: T): BOOLEAN;
PROCEDURE Decompose(pn: T): Arcs RAISES {Invalid};
PROCEDURE Compose(a: Arcs): T RAISES {Invalid};
PROCEDURE Absolute(pn: T): BOOLEAN;
PROCEDURE Prefix(pn: T): T;
PROCEDURE Last(pn: T): T;
PROCEDURE Base(pn: T): T;
PROCEDURE Join(pn, base: T; ext: TEXT := NIL): T;
PROCEDURE LastBase(pn: T): T;
PROCEDURE LastExt(pn: T): TEXT;
PROCEDURE ReplaceExt(pn: T; ext: TEXT): T;

END PathnameWin32.
