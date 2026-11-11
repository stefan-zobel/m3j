(* Copyright 1996-2000 Critical Mass, Inc. All rights reserved.    *)
(* See file COPYRIGHT-CMASS for details. *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j from the interface of cm3: the builder of m3j
   compiles a whole program from the sources of its units with the
   compiler of m3j (M3jCompile), so the state of a compilation and the
   items of the configuration that the back ends of cm3 read (State,
   GetConfigItem) are left out. *)

INTERFACE Builder;

IMPORT Arg, M3Unit, Quake, QMachine;

PROCEDURE BuildPgm (prog: TEXT;  READONLY units: M3Unit.Set;
                    sys_libs: Arg.List;  shared: BOOLEAN;  m: Quake.Machine);
(* Build a new program named "prog" from the sources in "units" *)

PROCEDURE BuildLib (lib: TEXT;  READONLY units: M3Unit.Set;
                    sys_libs: Arg.List;  shared: BOOLEAN;  m: Quake.Machine);
(* Build a new library named "lib" from the sources in "units" *)

PROCEDURE BuildCPgm (prog: TEXT;  READONLY units: M3Unit.Set;
                     sys_libs: Arg.List;  shared: BOOLEAN;  m: Quake.Machine);
(* Build a new C program named "prog" from the C sources in "units" *)

PROCEDURE AddClassPath (m: Quake.Machine;  path: TEXT) RAISES {Quake.Error};
(* Adds the jar or directory path to the Java classes of the program or
   library that the machine builds. *)

PROCEDURE AddJavaOption (m: Quake.Machine;  option: TEXT) RAISES {Quake.Error};
(* m3j: adds option to the java command of the scripts that run the
   program that the machine builds. *)

PROCEDURE CleanUp ();
(* Last chance to dump state and clean up *)

PROCEDURE EmitPkgImports (READONLY units: M3Unit.Set);
(* Output all imported packages *)

PROCEDURE SetupNamingConventions (mach : QMachine.T);

PROCEDURE ProgramFiles (prog: TEXT): ARRAY [0..2] OF TEXT;
(* m3j: the files of the program "prog" in the build directory: its jar
   and the scripts that run it on POSIX and on Windows. *)

END Builder.
