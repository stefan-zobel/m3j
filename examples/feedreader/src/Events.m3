(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Events;

IMPORT Thread, Time;

CONST Step = 0.5D0;             (* seconds between two looks at the counter *)

VAR
  mu := NEW (MUTEX);
  count := 0;                   (* protected by "mu" *)

PROCEDURE Signal () =
  BEGIN
    LOCK mu DO INC (count) END;
  END Signal;

PROCEDURE Count (): INTEGER =
  BEGIN
    LOCK mu DO RETURN count END;
  END Count;

PROCEDURE Wait (seen: INTEGER; timeout: LONGREAL): INTEGER RAISES {Thread.Alerted} =
  VAR end := Time.Now () + timeout;  c: INTEGER;
  BEGIN
    LOOP
      c := Count ();
      IF c > seen OR Time.Now () >= end THEN RETURN c END;
      Thread.AlertPause (Step);
    END;
  END Wait;

BEGIN
END Events.
