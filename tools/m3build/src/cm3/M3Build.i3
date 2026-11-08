(* Copyright 1996-2000 Critical Mass, Inc. All rights reserved.    *)
(* See file COPYRIGHT-CMASS for details. *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j from the interface of cm3: noM3ShipResolution is TRUE. *)

INTERFACE M3Build;

IMPORT Quake, Thread;

TYPE T <: Quake.Machine;

PROCEDURE NewMachine (): T;
(* Return a freshly allocated and initialized interpreter. *)

PROCEDURE SetUp (t: T;  pkg, to_pkg, build_dir: TEXT)  RAISES {Quake.Error};
(* Initialize internal state to start building "pkg" at "to_pkg" *)

PROCEDURE Run (t: T;  makefile: TEXT) RAISES {Quake.Error, Thread.Alerted};
(* Evaluate the specified makefile... *)

PROCEDURE RealClean ();
(* Remove the complete target directory for derived files *)

VAR
  done := FALSE;
  noM3ShipResolution := TRUE;
  (* m3j: .M3SHIP names the places of the installation by the quake
     variables of the configuration, so that -ship installs where -root or
     M3J_ROOT names when it runs *)
  groupWritable := FALSE;
END M3Build.

