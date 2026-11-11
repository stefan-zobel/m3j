(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Messages of the program for its operator, on the standard error, each
   with the time in UTC. *)

INTERFACE Log;

VAR quiet := FALSE;             (* no messages *)

PROCEDURE Put (message: TEXT);

END Log.
