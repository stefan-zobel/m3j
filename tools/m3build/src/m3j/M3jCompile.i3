(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The compiler of m3j, for m3build: the runtime of m3j implements this
   interface in Java (the class m3.i.M3jCompile), with the compiler of
   the jar m3j.jar that m3build runs with. *)

INTERFACE M3jCompile;

PROCEDURE Run (args: TEXT; nargs: INTEGER): INTEGER;
(* Runs the compiler of m3j (m3j.jvm.Compile) in this program with the
   nargs arguments separated by NUL in args, as on its command line:
   its exit code (0 if all went well). Its messages go to the standard
   error output. Relative pathnames are relative to the working directory
   of the program. *)

PROCEDURE Home (): TEXT;
(* The directory of m3j: the directory of the jar of the compiler, where
   the configuration file m3j.cfg, the jars of m3j and its library (lib)
   are. *)

PROCEDURE Java (): TEXT;
(* The pathname of the java program that runs this program. *)

PROCEDURE Version (): TEXT;
(* The version of m3j, as in the manifest of its jar ("unknown" if it
   has none). *)

END M3jCompile.
