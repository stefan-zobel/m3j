(* Checked runtime errors: each case below fails one check of the language
   (range, subscript, shape, NARROW, CASE, TYPECASE, missing RETURN,
   ASSERT, NIL method). Without arguments, the program runs every case in
   a handler for RuntimeError.E and prints the error it caught; with the
   number of a case as its argument, it runs that case without a handler,
   so the error ends the program. Further arguments name errors that the
   runtime reports (a thread joined twice, a mutex locked twice or
   released while unlocked, Process.Crash, an error not handled in a
   thread).

   The argument "traps" runs the errors that cm3 on AMD64_NT detects by a
   trap of the processor (division by zero, NIL dereference, stack
   overflow) and that its handlers cannot catch, and the shape checks of
   assignments from open arrays, which cm3 does not make. *)

MODULE Main;

IMPORT IO, Fmt, Params, Scan, RuntimeError, Word, Thread, Process, Lex,
       FloatMode, Text;

TYPE
  Digit = [0 .. 9];
  Wide = [0 .. 100000];
  Huge = [-1 .. 16_7FFFFFFFFF];
  LDigit = [0L .. 9L];
  Color = {Red, Green, Blue};
  Digits = SET OF Digit;
  Big = SET OF [0 .. 199];
  Arr = ARRAY [0 .. 4] OF INTEGER;
  Grid = ARRAY [0 .. 2], [0 .. 2] OF Digit;
  Open = REF ARRAY OF INTEGER;
  Open2 = REF ARRAY OF ARRAY OF INTEGER;
  Rec = RECORD x, y: INTEGER END;
  RRec = REF Rec;
  RInt = REF INTEGER;
  T0 = OBJECT a: INTEGER METHODS m (): INTEGER := M0 END;
  T1 = T0 OBJECT b: INTEGER END;
  T2 = T0 OBJECT c: INTEGER END;
  U = OBJECT METHODS u (): INTEGER END;
  Packed = RECORD d: BITS 4 FOR Digit;  w: BITS 20 FOR Wide END;

VAR
  sink: INTEGER;
  lsink: LONGINT;
  dsink: Digit;
  wsink: Wide;
  hsink: Huge;
  csink: Color;
  chsink: CHAR;
  arr: Arr;
  grid: Grid;
  packed: Packed;
  digits: Digits;
  big: Big;
  t0: T0;
  t1: T1;
  ref: REFANY;

PROCEDURE M0 (self: T0): INTEGER =
  BEGIN
    RETURN self.a;
  END M0;

(* The value n, which the compiler cannot know. *)
PROCEDURE Id (n: INTEGER): INTEGER =
  BEGIN
    IF Params.Count > 1000 THEN RETURN 0 END;
    RETURN n;
  END Id;

PROCEDURE LId (n: LONGINT): LONGINT =
  BEGIN
    IF Params.Count > 1000 THEN RETURN 0L END;
    RETURN n;
  END LId;

PROCEDURE WId (n: Wide): Wide =
  BEGIN
    IF Params.Count > 1000 THEN RETURN 0 END;
    RETURN n;
  END WId;

PROCEDURE Nil (): REFANY =
  BEGIN
    IF Params.Count > 1000 THEN RETURN NEW(RRec) END;
    RETURN NIL;
  END Nil;

PROCEDURE TakeDigit (d: Digit): INTEGER =
  BEGIN
    RETURN d;
  END TakeDigit;

PROCEDURE GiveDigit (n: INTEGER): Digit =
  BEGIN
    RETURN n;
  END GiveDigit;

PROCEDURE NoReturn (n: INTEGER): INTEGER =
  BEGIN
    IF n > 100 THEN RETURN n END;
  END NoReturn;

(* Recursion without an end, until the stack overflows. *)
PROCEDURE Deep (n: INTEGER): INTEGER =
  BEGIN
    RETURN Deep(n + 1) + 1;
  END Deep;

PROCEDURE Sum (READONLY a: ARRAY OF INTEGER): INTEGER =
  VAR s := 0;
  BEGIN
    FOR i := 0 TO LAST(a) DO INC(s, a[i]) END;
    RETURN s;
  END Sum;

PROCEDURE Show (READONLY a: Arr): INTEGER =
  BEGIN
    RETURN a[4];
  END Show;

PROCEDURE Fill (VAR a: Arr) =
  BEGIN
    FOR i := 0 TO LAST(a) DO a[i] := i END;
  END Fill;

CONST Cases = 24;

PROCEDURE Name (i: INTEGER): TEXT =
  BEGIN
    CASE i OF
    | 0 => RETURN "INTEGER to [0..9]";
    | 1 => RETURN "[0..100000] to [0..9]";
    | 2 => RETURN "INTEGER to [-1..2^39-1]";
    | 3 => RETURN "VAL to an enumeration";
    | 4 => RETURN "VAL to CHAR";
    | 5 => RETURN "LONGINT to [0L..9L]";
    | 6 => RETURN "parameter and result";
    | 7 => RETURN "packed field";
    | 8 => RETURN "set element";
    | 9 => RETURN "subscript of a fixed array";
    | 10 => RETURN "subscript of an open array";
    | 11 => RETURN "SUBARRAY";
    | 12 => RETURN "open array to a fixed READONLY parameter";
    | 13 => RETURN "open array to a fixed VAR parameter";
    | 14 => RETURN "array constructor to an open array";
    | 15 => RETURN "NEW with a negative size";
    | 16 => RETURN "NARROW";
    | 17 => RETURN "implicit NARROW";
    | 18 => RETURN "CASE without a match";
    | 19 => RETURN "TYPECASE without a match";
    | 20 => RETURN "function without RETURN";
    | 21 => RETURN "ASSERT";
    | 22 => RETURN "method that is NIL";
    | 23 => RETURN "subscript of a two-dimensional open array";
    | 100 => RETURN "DIV by zero";
    | 101 => RETURN "MOD by zero";
    | 102 => RETURN "Word.Divide by zero";
    | 103 => RETURN "Word.Mod by zero";
    | 104 => RETURN "NIL^";
    | 105 => RETURN "field of a NIL object";
    | 106 => RETURN "method of a NIL object";
    | 107 => RETURN "open array to an open array";
    | 108 => RETURN "open array to an open array of two dimensions";
    | 109 => RETURN "open array to a fixed array";
    | 110 => RETURN "stack overflow";
    ELSE RETURN "?";
    END;
  END Name;

(* Runs case i: its last statement fails a check. *)
PROCEDURE Fail (i: INTEGER) =
  VAR n := Id(i + 1000);
  BEGIN
    CASE i OF
    | 0 =>
        dsink := Id(7);
        dsink := Id(10);
    | 1 =>
        wsink := WId(99999);
        dsink := wsink;
    | 2 =>
        hsink := Id(16_7FFFFFFFFF);
        hsink := Id(-2);
    | 3 =>
        csink := VAL(Id(2), Color);
        csink := VAL(Id(3), Color);
    | 4 =>
        chsink := VAL(Id(255), CHAR);
        chsink := VAL(Id(256), CHAR);
    | 5 =>
        lsink := VAL(LId(9L), LDigit);
        lsink := VAL(LId(-1L), LDigit);
    | 6 =>
        sink := TakeDigit(Id(9));
        sink := GiveDigit(Id(-1));
        sink := TakeDigit(Id(12));
    | 7 =>
        packed.w := Id(100000);
        packed.d := packed.w;
    | 8 =>
        digits := Digits {Id(3), Id(9)};
        big := Big {Id(3) .. Id(199)};
        digits := Digits {Id(3) .. Id(10)};
    | 9 =>
        arr[Id(4)] := 1;
        grid[Id(2), Id(1)] := 3;
        arr[Id(5)] := 1;
    | 10 =>
        WITH o = NEW(Open, Id(3)) DO
          o[Id(2)] := 1;
          sink := o[Id(3)];
        END;
    | 11 =>
        sink := Sum(SUBARRAY(arr, Id(2), Id(3)));
        sink := Sum(SUBARRAY(arr, Id(3), Id(3)));
    | 12 =>
        VAR o := NEW(Open, Id(5));
        BEGIN
          sink := Show(o^);
          o := NEW(Open, Id(4));
          sink := Show(o^);
        END;
    | 13 =>
        VAR o := NEW(Open, Id(5));
        BEGIN
          Fill(o^);
          o := NEW(Open, Id(6));
          Fill(o^);
        END;
    | 14 =>
        VAR o := NEW(Open, Id(3));
        BEGIN
          o^ := ARRAY OF INTEGER {1, 2, 3};
          o := NEW(Open, Id(2));
          o^ := ARRAY OF INTEGER {1, 2, 3};
        END;
    | 15 =>
        ref := NEW(Open, Id(0));
        ref := NEW(Open, Id(-1));
    | 16 =>
        t0 := NEW(T2, a := 5);
        ref := t0;
        t0 := NARROW(ref, T0);
        t1 := NARROW(ref, T1);
    | 17 =>
        t0 := NEW(T1, a := 6);
        t1 := t0;
        t0 := NEW(T2, a := 7);
        t1 := t0;
    | 18 =>
        CASE n OF
        | 0 .. 999 => sink := 1;
        | 1019 .. 1030 => sink := 2;
        END;
    | 19 =>
        ref := NEW(T2);
        TYPECASE ref OF
        | NULL => sink := 0;
        | T1 => sink := 1;
        | RInt => sink := 2;
        END;
    | 20 =>
        sink := NoReturn(Id(101));
        sink := NoReturn(Id(5));
    | 21 =>
        <* ASSERT n = Id(1021) *>
        <* ASSERT n < Id(1021) *>
    | 22 =>
        WITH u = NEW(U) DO
          sink := u.u();
        END;
    | 23 =>
        WITH o = NEW(Open2, Id(2), Id(3)) DO
          o[Id(1), Id(2)] := 4;
          o[Id(1), Id(3)] := 4;
        END;
    | 100 =>
        sink := Id(7) DIV Id(-2);
        sink := Id(7) DIV Id(0);
    | 101 =>
        lsink := LId(7L) MOD LId(-2L);
        lsink := LId(7L) MOD LId(0L);
    | 102 =>
        sink := Word.Divide(Id(-1), Id(3));
        sink := Word.Divide(Id(-1), Id(0));
    | 103 =>
        sink := Word.Mod(Id(-1), Id(3));
        sink := Word.Mod(Id(-1), Id(0));
    | 104 =>
        WITH r = NARROW(Nil(), RRec) DO
          sink := r.x;
        END;
    | 105 =>
        t0 := Nil();
        sink := t0.a;
    | 106 =>
        t0 := Nil();
        sink := t0.m();
    | 107 =>
        WITH a = NEW(Open, Id(3)), b = NEW(Open, Id(4)) DO
          a^ := SUBARRAY(b^, 0, 3);
          a^ := b^;
        END;
    | 109 =>
        VAR o := NEW(Open, Id(5));
        BEGIN
          arr := o^;
          o := NEW(Open, Id(4));
          arr := o^;
        END;
    | 110 =>
        sink := Deep(Id(1));
    | 108 =>
        WITH a = NEW(Open2, Id(2), Id(3)), b = NEW(Open2, Id(2), Id(4)) DO
          a^ := b^;
        END;
    ELSE
    END;
  END Fail;

PROCEDURE Run (from, to: INTEGER) =
  BEGIN
    FOR i := from TO to DO
      TRY
        Fail(i);
        IO.Put(Fmt.Int(i) & " " & Name(i) & ": no error\n");
      EXCEPT
        RuntimeError.E (e) =>
          IO.Put(Fmt.Int(i) & " " & Name(i) & ": " & RuntimeError.Tag(e) & "\n");
      END;
    END;
    IO.Put("sink " & Fmt.Int(sink) & " " & Fmt.LongInt(lsink) & " "
             & Fmt.Int(dsink) & " " & Fmt.Int(ORD(csink)) & " "
             & Fmt.Int(ORD(chsink)) & " " & Fmt.Int(hsink) & " "
             & Fmt.Int(arr[4]) & "\n");
  END Run;

(* Errors that the runtime reports and that end the program. *)

TYPE Closure = Thread.Closure OBJECT n: INTEGER OVERRIDES apply := Apply END;

PROCEDURE Apply (cl: Closure): REFANY =
  BEGIN
    dsink := Id(cl.n);
    RETURN NIL;
  END Apply;

PROCEDURE Nothing (<*UNUSED*> cl: Thread.Closure): REFANY =
  BEGIN
    RETURN NIL;
  END Nothing;

PROCEDURE Ends (what: TEXT) =
  VAR m := NEW(MUTEX);
  BEGIN
    IF Text.Equal(what, "traps") THEN
      Run(100, 110);
      RETURN;
    ELSIF Text.Equal(what, "join") THEN
      WITH t = Thread.Fork(NEW(Thread.Closure, apply := Nothing)) DO
        EVAL Thread.Join(t);
        EVAL Thread.Join(t);
      END;
    ELSIF Text.Equal(what, "lock") THEN
      LOCK m DO LOCK m DO sink := 1 END END;
    ELSIF Text.Equal(what, "release") THEN
      Thread.Release(m);
    ELSIF Text.Equal(what, "crash") THEN
      IO.Put("before the crash\n");
      Process.Crash("the program gives up");
    ELSIF Text.Equal(what, "thread") THEN
      EVAL Thread.Join(Thread.Fork(NEW(Closure, n := 3)));
      IO.Put("in range\n");
      EVAL Thread.Join(Thread.Fork(NEW(Closure, n := 30)));
    END;
    IO.Put("not reached\n");
  END Ends;

BEGIN
  IF Params.Count > 1 THEN
    WITH arg = Params.Get(1) DO
      TRY
        Fail(Scan.Int(arg));
        IO.Put("not reached\n");
      EXCEPT
        Lex.Error, FloatMode.Trap => Ends(arg);
      END;
    END;
  ELSE
    Run(0, Cases - 1);
  END;
END Main.
