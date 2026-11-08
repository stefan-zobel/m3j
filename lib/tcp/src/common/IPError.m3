(* Changed for m3j: with RaiseOS, which cm3 has in a module for each
   system (IPErrorWin32.m3, IPErrorPosix.m3). The Java platform has no
   number of the last error, so RaiseOS raises the atom alone. *)

(* Copyright 1996-2000, Critical Mass, Inc.  All rights reserved. *)
(* See file COPYRIGHT-CMASS for details. *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

MODULE IPError;

IMPORT Atom, AtomList, Fmt, IP, TCP;

PROCEDURE Raise (a: Atom.T;  info: INTEGER := 0) RAISES {IP.Error} =
  BEGIN
    IF (info = 0) THEN
      RAISE IP.Error (AtomList.List1 (a));
    ELSE
      RAISE IP.Error (AtomList.List2 (a, Atom.FromText (Fmt.Int (info))));
    END;
  END Raise;

PROCEDURE RaiseOS (a: Atom.T) RAISES {IP.Error} =
  BEGIN
    Raise (a);
  END RaiseOS;

PROCEDURE RaiseUnexpected () RAISES {IP.Error} =
  BEGIN
    RaiseOS (Unexpected);
  END RaiseUnexpected;

EXCEPTION FatalError;

PROCEDURE Die () =
  <*FATAL FatalError*>
  BEGIN
    RAISE FatalError;
  END Die;

BEGIN
  IP.LookupFailure := Atom.FromText("IP.LookupFailure");
  IP.Unreachable := Atom.FromText("IP.Unreachable");
  IP.PortBusy := Atom.FromText("IP.PortBusy");
  IP.NoResources := Atom.FromText("IP.NoResources");

  TCP.Refused := Atom.FromText("TCP.Refused");
  TCP.Closed := Atom.FromText("TCP.Closed");
  TCP.Timeout := Atom.FromText("TCP.Timeout");
  TCP.ConnLost := Atom.FromText("TCP.ConnLost");

  Unexpected := Atom.FromText("TCP.Unexpected");
  ClosedErr := AtomList.List1(TCP.Closed);
END IPError.
