(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Main;

IMPORT AbstractList, Bound, CharSequence, Collections, Comparator, Dimension, Fmt, IO, IntStream, Iterator, JArr, Java, JavaObject, JInteger,
       JMath, JThread, Elsewhere, Params, Point, RTType, Runnable, StringBuilder, Text, ThreadLocal, TimerTask, Workers;

EXCEPTION Boom (TEXT);

TYPE
  Worker = JThread.T OBJECT
    count := 0;
  METHODS
    run () := Run;
    report (): TEXT := Report;
  END;

  Twice = Worker OBJECT
    extra := 100;
  OVERRIDES
    run := RunTwice;
  END;

  Task = Runnable.T OBJECT
    n := 0;
  METHODS
    run () := Bump;
  END;

  Chars = CharSequence.T OBJECT
    s: TEXT;
  METHODS
    length (): Java.Int := Length;
    charAt (i: Java.Int): Java.Char RAISES {Boom} := CharAt;
    subSequence (a, b: Java.Int): CharSequence.T := Sub;
    toString (): TEXT := ToText;
  END;

  Upper = Chars OBJECT
  OVERRIDES
    charAt := UpperAt;
  END;

  Items = AbstractList.T OBJECT
    a, b, c: TEXT;
  METHODS
    get (i: Java.Int): REFANY := Get;
    size (): Java.Int := Size;
  END;

  Count = Iterator.T OBJECT
    i, n: INTEGER;
  METHODS
    hasNext (): BOOLEAN := HasNext;
    next (): REFANY := Next;
  END;

  ByLength = Comparator.T OBJECT
  METHODS
    compare (a, b: REFANY): Java.Int := Compare;
  END;

  Inside = Workers.Thr OBJECT
    seen := FALSE;
  OVERRIDES
    run := SeeSelf;
  END;

  Plain = JavaObject.T OBJECT
  METHODS
    toString (): TEXT := PlainText;
    other (): INTEGER := PlainOther;
  END;

  Tick = TimerTask.T OBJECT
    n := 0;
  METHODS
    run () := TickRun;
  END;

  Local = ThreadLocal.T OBJECT
  METHODS
    initialValue (): REFANY := Initial;
  END;

  Raiser = JThread.T OBJECT
  METHODS
    run () RAISES {Boom} := RaiseIt;
  END;

  Hashed = OBJECT
  METHODS
    hash (): Java.Int := Bound.Hash;
  END;

  MyPoint = Point.T OBJECT
    z := 0;
  END;

  Hidden <: JThread.T;

REVEAL
  Hidden = JThread.T BRANDED OBJECT
    seen := FALSE;
  METHODS
    run () := See;
  END;

PROCEDURE Run (self: Worker) =
  BEGIN
    INC (self.count);
  END Run;

PROCEDURE RunTwice (self: Twice) =
  BEGIN
    INC (self.count, 2);
    INC (self.extra);
  END RunTwice;

PROCEDURE Other (self: Worker) =
  BEGIN
    INC (self.count, 40);
  END Other;

PROCEDURE Report (self: Worker): TEXT =
  BEGIN
    RETURN "count " & Fmt.Int (self.count);
  END Report;

PROCEDURE Bump (self: Task) =
  BEGIN
    INC (self.n, 7);
  END Bump;

PROCEDURE Length (self: Chars): Java.Int =
  BEGIN
    RETURN Text.Length (self.s);
  END Length;

PROCEDURE CharAt (self: Chars; i: Java.Int): Java.Char RAISES {Boom} =
  VAR ch := Text.GetChar (self.s, i);
  BEGIN
    IF ch = '!' THEN RAISE Boom ("at " & Fmt.Int (i)) END;
    RETURN VAL (ORD (ch), WIDECHAR);
  END CharAt;

PROCEDURE UpperAt (self: Upper; i: Java.Int): Java.Char =
  VAR ch := Text.GetChar (self.s, i);
  BEGIN
    IF 'a' <= ch AND ch <= 'z' THEN ch := VAL (ORD (ch) - 32, CHAR) END;
    RETURN VAL (ORD (ch), WIDECHAR);
  END UpperAt;

PROCEDURE Sub (self: Chars; a, b: Java.Int): CharSequence.T =
  BEGIN
    RETURN NEW (Chars, s := Text.Sub (self.s, a, b - a));
  END Sub;

PROCEDURE ToText (self: Chars): TEXT =
  BEGIN
    RETURN self.s;
  END ToText;

PROCEDURE Get (self: Items; i: Java.Int): REFANY =
  BEGIN
    CASE i OF
    | 0 => RETURN self.a;
    | 1 => RETURN self.b;
    ELSE RETURN self.c;
    END;
  END Get;

PROCEDURE Size (<*UNUSED*> self: Items): Java.Int =
  BEGIN
    RETURN 3;
  END Size;

PROCEDURE HasNext (self: Count): BOOLEAN =
  BEGIN
    RETURN self.i < self.n;
  END HasNext;

PROCEDURE Next (self: Count): REFANY =
  BEGIN
    INC (self.i);
    RETURN "item" & Fmt.Int (self.i);
  END Next;

PROCEDURE Compare (<*UNUSED*> self: ByLength; a, b: REFANY): Java.Int =
  BEGIN
    RETURN Text.Length (NARROW (a, TEXT)) - Text.Length (NARROW (b, TEXT));
  END Compare;

PROCEDURE See (self: Hidden) =
  BEGIN
    self.seen := TRUE;
  END See;

PROCEDURE SeeSelf (self: Inside) =
  BEGIN
    self.seen := Workers.Current () = self;
  END SeeSelf;

PROCEDURE PlainText (<*UNUSED*> self: Plain): TEXT =
  BEGIN
    RETURN "plain";
  END PlainText;

PROCEDURE PlainOther (<*UNUSED*> self: Plain): INTEGER =
  BEGIN
    RETURN 5;
  END PlainOther;

PROCEDURE TickRun (self: Tick) =
  BEGIN
    INC (self.n);
  END TickRun;

PROCEDURE Initial (<*UNUSED*> self: Local): REFANY =
  BEGIN
    RETURN "initial";
  END Initial;

PROCEDURE RaiseIt (<*UNUSED*> self: Raiser) RAISES {Boom} =
  BEGIN
    RAISE Boom ("in a thread");
  END RaiseIt;

VAR visited := "";

PROCEDURE Visit (x: REFANY) =
  BEGIN
    visited := visited & NARROW (x, TEXT) & ";";
  END Visit;

PROCEDURE Check (name: TEXT; ok: BOOLEAN) =
  BEGIN
    IF ok THEN
      IO.Put (name & ": ok\n");
    ELSE
      IO.Put (name & ": FAILED\n");
    END;
  END Check;

PROCEDURE Threads () =
  VAR w := NEW (Worker);
  BEGIN
    JThread.start (w);
    JThread.join (w);
    Check ("thread subclass", w.count = 1);
    Check ("ordinary method", Text.Equal (w.report (), "count 1"));

    w := NEW (Worker, run := Other);
    JThread.start (w);
    JThread.join (w);
    Check ("override in NEW", w.count = 40);

    VAR t := NEW (Twice);
    BEGIN
      JThread.start (t);
      JThread.join (t);
      Check ("subtype of the subtype", t.count = 2 AND t.extra = 101);
    END;

    VAR task := NEW (Task); th := JThread.NewR (task);
    BEGIN
      JThread.start (th);
      JThread.join (th);
      Check ("interface passed to a constructor", task.n = 7);
      Runnable.run (task);
      Check ("interface method called from Modula-3", task.n = 14);
    END;

    VAR h := NEW (Hidden);
    BEGIN
      JThread.start (h);
      JThread.join (h);
      Check ("revealed subtype", h.seen);
    END;

    VAR i := NEW (Inside);
    BEGIN
      JThread.start (i);
      JThread.join (i);
      Check ("subtype from an interface, cast of a Java result", i.seen);
    END;
  END Threads;

PROCEDURE Sequences () =
  VAR sb := StringBuilder.New ();
  BEGIN
    EVAL StringBuilder.append (sb, NEW (Chars, s := "abc"));
    Check ("interface with several methods", Text.Equal (StringBuilder.toString (sb), "abc"));
    EVAL StringBuilder.append (sb, NEW (Upper, s := "def"));
    Check ("override reaches Java", Text.Equal (StringBuilder.toString (sb), "abcDEF"));
    Check ("toString", Text.Equal (JavaObject.toString (NEW (Chars, s := "xyz")), "xyz"));
    Check ("ToText", Text.Equal (Java.ToText (NEW (Chars, s := "uvw")), "uvw"));
    TRY
      EVAL StringBuilder.appendRaises (sb, NEW (Chars, s := "a!c"));
      Check ("exception through Java", FALSE);
    EXCEPT
    | Boom (msg) => Check ("exception through Java", Text.Equal (msg, "at 1"));
    ELSE Check ("exception through Java", FALSE);
    END;
  END Sequences;

PROCEDURE Collection () =
  VAR l := NEW (Items, a := "ab", b := "abcd", c := "abc");
  BEGIN
    Check ("abstract class", Text.Equal (JavaObject.toString (l), "[ab, abcd, abc]"));
    Check ("comparator", Text.Equal (NARROW (Collections.max (l, NEW (ByLength)), TEXT), "abcd"));
    Iterator.forEachRemaining (NEW (Count, i := 0, n := 3), Visit);
    Check ("default method of an interface", Text.Equal (visited, "item1;item2;item3;"));
    Check ("protected Java method", Text.Equal (NARROW (ThreadLocal.get (NEW (Local)), TEXT), "initial"));
    VAR p := NEW (Plain);
    BEGIN
      Check ("subtype of java.lang.Object", Text.Equal (JavaObject.toString (p), "plain") AND p.other () = 5);
    END;
    VAR t := NEW (Tick);
    BEGIN
      Check ("protected constructor", NOT TimerTask.cancel (t) AND t.n = 0);
    END;
  END Collection;

PROCEDURE Up (c: CharSequence.T): REFANY =
  BEGIN
    RETURN c;
  END Up;

PROCEDURE Tests () =
  VAR
    w: REFANY := NEW (Twice);
    c: REFANY := NEW (Chars, s := "x");
    u := NEW (Upper, s := "x");
    n := -1;
  BEGIN
    Check ("ISTYPE down", ISTYPE (w, Twice));
    Check ("ISTYPE up", ISTYPE (w, Worker) AND ISTYPE (w, JThread.T) AND ISTYPE (w, JavaObject.T));
    Check ("ISTYPE other", NOT ISTYPE (w, Chars) AND NOT ISTYPE (w, CharSequence.T));
    Check ("ISTYPE interface", ISTYPE (c, Chars) AND ISTYPE (c, CharSequence.T));
    Check ("NARROW", NARROW (w, Worker).count = 0);
    TYPECASE w OF
    | Chars => n := 0;
    | Twice => n := 1;
    | Worker => n := 2;
    ELSE n := 3;
    END;
    Check ("TYPECASE", n = 1);
    TYPECASE c OF
    | JThread.T => n := 0;
    | CharSequence.T => n := 1;
    ELSE n := 2;
    END;
    Check ("TYPECASE with a Java type", n = 1);
    Check ("assignment to the supertype", ISTYPE (Up (u), Chars));
    Check ("supertype of a subtype", RTType.Supertype (TYPECODE (Twice)) = TYPECODE (Worker));
    Check ("supertype of a subtype of Java", RTType.Supertype (TYPECODE (Worker)) = RTType.NoSuchType);
  END Tests;

PROCEDURE AddTo (VAR n: Java.Int; d: Java.Int) =
  BEGIN
    n := n + d;
  END AddTo;

PROCEDURE Fields () =
  VAR
    p := Point.New (3, 4);
    d := Dimension.New (5, 6);
    m := NEW (MyPoint, x := 7, z := 8);
  BEGIN
    Check ("read a field", p.x = 3 AND p.y = 4);
    p.x := 10;
    INC (p.y, 5);
    Check ("write a field", Point.getX (p) = 10.0d0 AND Point.getY (p) = 9.0d0);
    AddTo (p.x, 1);
    Check ("field as a VAR actual", p.x = 11);
    Check ("field of another class", d.width + d.height = 11);
    Check ("field in NEW of a subtype", m.x = 7 AND m.z = 8 AND Point.getX (m) = 7.0d0);
    WITH f = m.y DO
      f := 20;
    END;
    Check ("field through WITH", Point.getY (m) = 20.0d0);
  END Fields;

PROCEDURE Values () =
  VAR
    parse: Elsewhere.Parse := JInteger.parseInt;
    make: PROCEDURE (x, y: Java.Int): Point.T := Point.New;
    getX: PROCEDURE (self: Point.T): LONGREAL := Point.getX;
    start: PROCEDURE (self: JThread.T) := JThread.start;
    join: PROCEDURE (self: JThread.T) := JThread.join;
    abs: IntStream.Op := JMath.absInt;
    sort: PROCEDURE (VAR a: ARRAY OF INTEGER) := JArr.sort;
    map: PROCEDURE (self: IntStream.T; f: IntStream.Op): IntStream.T := IntStream.map;
    gc: PROCEDURE () := Bound.notifyAll;
    a := ARRAY [0 .. 2] OF INTEGER {3, 1, 2};
    h := NEW (Hashed);
    w := NEW (Worker);
  BEGIN
    Check ("value of a static method", parse ("42") = 42);
    Check ("value of a constructor", getX (make (6, 7)) = 6.0d0);
    start (w);
    join (w);
    Check ("value of an instance method", w.count = 1);
    Check ("equal in two units", parse = Elsewhere.Value () AND parse # NIL AND Elsewhere.Value () = JInteger.parseInt);
    Check ("not equal to another value", abs # NIL AND NOT getX = Point.getY);
    Check ("value passed as a callback", IntStream.sum (IntStream.map (IntStream.ofInts (ARRAY OF Java.Int {-1, -2}), abs)) = 3);
    Check ("value that takes a callback", IntStream.sum (map (IntStream.ofInts (ARRAY OF Java.Int {-4}), JMath.absInt)) = 4);
    sort (a);
    Check ("value with a VAR array", a[0] = 1 AND a[2] = 3);
    gc ();
    Check ("method default", h.hash () = Java.HashCode (h));
  END Values;

BEGIN
  IF Params.Count > 1 THEN
    (* ends the program: nothing handles the exception on the thread *)
    VAR r := NEW (Raiser);
    BEGIN
      JThread.start (r);
      JThread.join (r);
    END;
    IO.Put ("not reached\n");
  ELSE
    Threads ();
    Sequences ();
    Collection ();
    Tests ();
    Fields ();
    Values ();
  END;
END Main.
