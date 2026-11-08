(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* The module TimeStamp of m3j. In cm3 it is UNSAFE: it fills a
   TimeStampRep.T and converts it with LOOPHOLE. Here the same 16 bytes
   are set one by one: the time in seconds since the epoch (big endian),
   the 1/256 of the second, the counter within it, the process ID (little
   endian, high and low half) and the machine ID (zero: m3j has none). *)

MODULE TimeStamp;

IMPORT Date, Thread, Time, Word, Process;

TYPE
  Counter = MUTEX OBJECT
    time        : INTEGER := 0;
    fineTime    : [0..255] := 0;
    fineCounter : [0..255] := 0;
  END;

VAR
  counter   := NEW (Counter);
  init_done := FALSE;
  myPID     : INTEGER;
  epoch     : Time.T;

PROCEDURE Init () =
  <*FATAL Date.Error*>
  CONST
    epochDate = Date.T{1970, Date.Month.Jan, 2, 0,0,0,0, "", Date.WeekDay.Sun};
    oneDay    = 24.0d0 * 3600.0d0;
  BEGIN
    myPID     := Process.GetMyID ();
    epoch     := Date.ToTime (epochDate) - oneDay;
    init_done := TRUE;
  END Init;

PROCEDURE New (): T =
  VAR
    fineTime   : INTEGER;
    fineCounter: [0 .. 255];
    time       : INTEGER;
    now        : Time.T;
    t          : T;
  BEGIN
    LOCK counter DO
      IF NOT init_done THEN Init () END;
      LOOP
        now := Time.Now () - epoch;
        time := TRUNC (now);
        fineTime := ROUND ((now - FLOAT (time, LONGREAL)) * 256.0d0);
        IF fineTime > 255 THEN fineTime := 0; INC (time); END;
        IF counter.time # time OR counter.fineTime # fineTime THEN
          fineCounter := 0;
          counter.fineCounter := 0;
          counter.time := time;
          counter.fineTime := fineTime;
          EXIT
        ELSIF counter.fineCounter < 255 THEN
          INC (counter.fineCounter);
          fineCounter := counter.fineCounter;
          EXIT
        ELSE
          Thread.Release (counter);
          TRY
            Thread.Pause (1.0D0 / 256.0D0);
          FINALLY
            Thread.Acquire (counter);
          END;
        END;
      END;
    END;
    FOR i := 0 TO 3 DO
      t.r[i] := Word.Extract (time, 8 * (3 - i), 8);
    END;
    t.r[4] := fineTime;
    t.r[5] := fineCounter;
    t.r[6] := Word.Extract (myPID, 0, 8);
    t.r[7] := Word.Extract (myPID, 8, 8);
    t.r[8] := Word.Extract (myPID, 16, 8);
    t.r[9] := Word.Extract (myPID, 24, 8);
    FOR i := 10 TO 15 DO t.r[i] := 0 END;
    RETURN t;
  END New;

PROCEDURE Compare (READONLY t1, t2: T): [-1 .. 1] =
  BEGIN
    FOR i := FIRST (t1.r) TO LAST (t1.r) DO
      IF t1.r[i] < t2.r[i] THEN RETURN -1
      ELSIF t1.r[i] > t2.r[i] THEN RETURN 1
      END;
    END;
    RETURN 0;
  END Compare;

PROCEDURE Equal (READONLY t1, t2: T): BOOLEAN =
  BEGIN
    RETURN t1 = t2;
  END Equal;

PROCEDURE Max (READONLY t1, t2: T): T =
  BEGIN
    IF Compare (t1, t2) = 1 THEN RETURN t2 ELSE RETURN t1; END;
  END Max;

PROCEDURE Min (READONLY t1, t2: T): T =
  BEGIN
    IF Compare (t1, t2) = 1 THEN RETURN t1 ELSE RETURN t2; END;
  END Min;

(* The 32-bit integer (sign extended) of bytes 4k .. 4k+3, little endian. *)
PROCEDURE Int32 (READONLY t: T; k: [0 .. 3]): INTEGER =
  VAR v := 0;
  BEGIN
    FOR i := 3 TO 0 BY -1 DO v := Word.Or (Word.LeftShift (v, 8), t.r[4 * k + i]) END;
    IF v >= 16_80000000 THEN DEC (v, 16_100000000) END;
    RETURN v;
  END Int32;

PROCEDURE Hash (READONLY t: T): INTEGER =
  BEGIN
    RETURN Word.Xor (Word.Xor (Int32 (t, 0), Int32 (t, 1)), Word.Xor (Int32 (t, 2), Int32 (t, 3)));
  END Hash;

PROCEDURE ToTime (READONLY t: T): Time.T =
  VAR
    frac := FLOAT (t.r[4], LONGREAL) / 256.0D0;
    time := 0;
  BEGIN
    IF NOT init_done THEN Init () END;
    FOR i := 0 TO 3 DO time := time * 256 + t.r[i] END;
    IF time >= 16_80000000 THEN DEC (time, 16_100000000) END;
    RETURN FLOAT (time, LONGREAL) + epoch + frac;
  END ToTime;

BEGIN
END TimeStamp.
