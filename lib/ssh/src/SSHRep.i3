(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* What the modules of the library ssh share, beyond the interface SSH. *)

INTERFACE SSHRep;

IMPORT AtomList, SSH, Thread;

TYPE
  Private = SSH.Public OBJECT
    h: REFANY;              (* the session of M3jSsh *)
    timeout: LONGREAL;
  END;

REVEAL SSH.T <: Private;

PROCEDURE Problem (code: INTEGER; err: TEXT): AtomList.T;
(* The list of "SSH.Error" for a code of M3jSsh, positive, with its text. *)

PROCEDURE Raise (code: INTEGER; err: TEXT) RAISES {SSH.Error, Thread.Alerted};
(* Raises "Thread.Alerted", taking the alert, if "code" is that of an alert
   and the thread has one; otherwise "SSH.Error" with "Problem". *)

END SSHRep.
