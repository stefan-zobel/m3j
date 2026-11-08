(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Weak references of the Java platform, for the module WeakRef of m3j:
   the procedures are methods of the class m3j.rt.Weak (through
   m3j.rt.Externals). A weak reference is a number greater than 0, never
   used again in the run of the program. *)

INTERFACE M3jWeak;

<*EXTERNAL "M3jWeak__New"*>
PROCEDURE New (r: REFANY; cleanup: BOOLEAN): INTEGER;
(* A new weak reference to r, which is not NIL, which is cleaned up when
   it dies if cleanup; 0 if cleanup and r has such a weak reference
   already. *)

<*EXTERNAL "M3jWeak__Get"*>
PROCEDURE Get (w: INTEGER): REFANY;
(* The node of w, or NIL if w is dead or not a weak reference. *)

<*EXTERNAL "M3jWeak__Next"*>
PROCEDURE Next (): INTEGER;
(* Waits until a weak reference that is cleaned up has died, and returns
   it. *)

<*EXTERNAL "M3jWeak__Take"*>
PROCEDURE Take (w: INTEGER): REFANY;
(* The node of w, which Next has returned. *)

END M3jWeak.
