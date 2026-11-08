(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Calls of Java from Modula-3: static, instance, interface and constructor
   calls, static fields, Java exceptions, procedures passed to Java, and
   the helpers of the interface Java. With an argument, the program ends
   with the error that the argument names: range, index, nil or uncaught. *)

MODULE Main;

IMPORT Fmt, IO, Params, RuntimeError, Text, Thread;
IMPORT Java, JavaObject, JavaThrowable;
IMPORT JArrayList, JArrays, JCollections, JHashMap, JInteger, JList, JMath,
       JObjects, JString, JStringBuilder, JThread;

EXCEPTION Stop (INTEGER);

TYPE Own = OBJECT n: INTEGER END;

PROCEDURE Line (a, b: TEXT) =
  BEGIN
    IO.Put (a & ": " & b & "\n");
  END Line;

PROCEDURE Int (name: TEXT; i: INTEGER) =
  BEGIN
    Line (name, Fmt.Int (i));
  END Int;

PROCEDURE Bool (name: TEXT; b: BOOLEAN) =
  BEGIN
    Line (name, Fmt.Bool (b));
  END Bool;

PROCEDURE Statics () =
  BEGIN
    Int ("max", JMath.Max (FIRST (INTEGER), 7));
    Int ("abs", JMath.AbsInt (-12));
    Line ("sqrt", Fmt.LongReal (JMath.Sqrt (2.25D0)));
    Line ("pi", Fmt.LongReal (JMath.Pi));
    Int ("MAX_VALUE", JInteger.MAX_VALUE);
    Int ("parseInt", JInteger.parseInt ("-4711"));
    Line ("toString", JInteger.ToText (255));
  END Statics;

PROCEDURE Strings () =
  VAR s := "hello, java";
  BEGIN
    Int ("length", JString.length (s));
    Int ("charAt", ORD (JString.charAt (s, 1)));
    Line ("substring", JString.substring (s, 7, 11));
    Line ("toUpperCase", JString.toUpperCase (s));
    WITH b = JStringBuilder.append (JStringBuilder.append (JStringBuilder.New (), "ab"), "cde") DO
      Int ("builder length", JStringBuilder.length (b));
      Line ("builder", JStringBuilder.toString (b));
    END;
  END Strings;

PROCEDURE Lists () =
  VAR
    a := JArrayList.New ();
    l: JList.T := a;
    x: REFANY := a;
  BEGIN
    Bool ("add", JList.add (l, "one"));
    EVAL JList.add (a, Java.FromInt (2));
    EVAL JList.add (l, NIL);
    Int ("size", JList.size (a));
    Line ("get 0", NARROW (JList.get (l, 0), TEXT));
    Int ("get 1", Java.ToInt (JList.get (l, 1)));
    Bool ("get 2 is NIL", JList.get (a, 2) = NIL);
    Bool ("ISTYPE JList", ISTYPE (x, JList.T));
    Bool ("ISTYPE JArrayList", ISTYPE (x, JArrayList.T));
    Bool ("ISTYPE JStringBuilder", ISTYPE (x, JStringBuilder.T));
    Bool ("ISTYPE ROOT", ISTYPE (x, ROOT));
    a := NARROW (x, JArrayList.T);
    TYPECASE x OF
    | JStringBuilder.T => Line ("TYPECASE", "builder");
    | JList.T (y) => Int ("TYPECASE list of", JList.size (y));
    ELSE Line ("TYPECASE", "else");
    END;
    TRY
      WITH b = NARROW (x, JStringBuilder.T) DO EVAL JStringBuilder.length (b) END;
    EXCEPT
    | RuntimeError.E (e) => Line ("NARROW", RuntimeError.Tag (e));
    END;
    Line ("toString", JavaObject.toString (a));
  END Lists;

PROCEDURE Message (t: JavaThrowable.T): TEXT =
  BEGIN
    RETURN JavaThrowable.getMessage (t);
  END Message;

PROCEDURE Exceptions () RAISES {} =
  BEGIN
    TRY
      EVAL JInteger.parseInt ("x");
      Line ("parseInt", "no exception");
    EXCEPT
    | Java.Exception (t) =>
        Line ("caught", Message (t));
        Line ("class", Java.ClassName (t));
    END;
    TRY
      TRY
        EVAL JInteger.parseInt ("y");
      FINALLY
        Line ("finally", "ran");
      END;
    EXCEPT
    | Java.Exception (t) => Line ("after finally", Message (t));
    END;
    TRY
      EVAL JObjects.requireNonNull (NIL);
    EXCEPT
    | RuntimeError.E (e) => Line ("NIL to Java", RuntimeError.Tag (e));
    END;
    TRY
      EVAL JList.get (JArrayList.New (), 3);
    EXCEPT
    | RuntimeError.E (e) => Line ("index", RuntimeError.Tag (e));
    END;
    Bool ("isNull NIL", JObjects.isNull (NIL));
  END Exceptions;

PROCEDURE ByText (a, b: REFANY): Java.Int =
  BEGIN
    RETURN Text.Compare (NARROW (a, TEXT), NARROW (b, TEXT));
  END ByText;

PROCEDURE ByLength (a, b: TEXT): Java.Int =
  BEGIN
    RETURN Text.Length (a) - Text.Length (b);
  END ByLength;

PROCEDURE TooWide (<*UNUSED*> a, b: REFANY): INTEGER =
  BEGIN
    RETURN LAST (INTEGER);
  END TooWide;

PROCEDURE Square (i: Java.Int): INTEGER =
  BEGIN
    RETURN i * i;
  END Square;

PROCEDURE Parses (<*UNUSED*> a, b: REFANY): Java.Int =
  BEGIN
    RETURN JInteger.parseInt ("not a number");
  END Parses;

VAR ran := 0;
    mu := NEW (MUTEX);

PROCEDURE Body () =
  BEGIN
    LOCK mu DO INC (ran) END;
    Bool ("Thread.Self in a Java thread", Thread.Self () # NIL);
  END Body;

PROCEDURE Upper (key: REFANY): REFANY =
  BEGIN
    RETURN JString.toUpperCase (NARROW (key, TEXT));
  END Upper;

PROCEDURE Show (l: JList.T) =
  VAR all := "";
  PROCEDURE Add (x: REFANY) =
    BEGIN
      all := all & NARROW (x, TEXT) & " ";
    END Add;
  BEGIN
    JList.forEach (l, Add);
    Line ("list", all);
  END Show;

PROCEDURE Callbacks () =
  VAR
    l := JArrayList.New ();
    cmp: JCollections.Compare := ByText;
    t: JThread.T;
    m := JHashMap.New ();
    a := Java.NewLongs (ARRAY OF INTEGER {0, 0, 0, 0});
  BEGIN
    EVAL JList.add (l, "pear");
    EVAL JList.add (l, "fig");
    EVAL JList.add (l, "banana");
    JCollections.sort (l, cmp);
    Show (l);
    JCollections.sortText (l, ByLength);
    Show (l);
    TRY
      JCollections.sortWide (l, TooWide);
    EXCEPT
    | RuntimeError.E (e) => Line ("result out of range", RuntimeError.Tag (e));
    END;
    TRY
      JCollections.sort (l, Parses);
    EXCEPT
    | Java.Exception (t) => Line ("through Java", Message (t));
    END;
    t := JThread.New (Body);
    JThread.start (t);
    JThread.join (t);
    Int ("thread ran", ran);
    t := JThread.New (NIL);
    JThread.start (t);
    JThread.join (t);
    Line ("NIL procedure", "ok");
    Line ("computeIfAbsent", NARROW (JHashMap.computeIfAbsent (m, "abc", Upper), TEXT));
    Int ("map size", JHashMap.size (m));
    JArrays.setAllLongs (a, Square);
    Line ("setAll", JArrays.longsToText (a));
  END Callbacks;

<*FATAL ANY*>
PROCEDURE RaiseThrough () =
  VAR l := JArrayList.New ();
  PROCEDURE Stops (<*UNUSED*> a, b: REFANY): Java.Int RAISES {Stop} =
    BEGIN
      RAISE Stop (7);
    END Stops;
  BEGIN
    EVAL JList.add (l, "a");
    EVAL JList.add (l, "b");
    TRY
      JCollections.sortAny (l, Stops);
    EXCEPT
    | Stop (n) => Int ("Modula-3 exception through Java", n);
    END;
  END RaiseThrough;

PROCEDURE Helpers () =
  VAR
    own := NEW (Own, n := 1);
    longs := ARRAY [0 .. 2] OF INTEGER {9, 7, 8};
    chars := ARRAY [0 .. 1] OF CHAR {'A', 'B'};
    doubles := ARRAY [0 .. 1] OF LONGREAL {0.5D0, 1.5D0};
    texts := ARRAY [0 .. 1] OF TEXT {"x", "y"};
    two: ARRAY [0 .. 1] OF INTEGER;
    four: ARRAY [0 .. 3] OF INTEGER;
    outc: ARRAY [0 .. 1] OF CHAR;
    outd: ARRAY [0 .. 1] OF LONGREAL;
    outt: ARRAY [0 .. 2] OF TEXT;
    a: REFANY;
  BEGIN
    Line ("ToText", Java.ToText (JArrayList.New ()));
    Line ("ToText NIL", Java.ToText (NIL));
    Bool ("Equals", Java.Equals ("a" & "b", "ab"));
    Bool ("Equals NIL", Java.Equals (NIL, NIL));
    Int ("HashCode", Java.HashCode ("ab"));
    Int ("HashCode NIL", Java.HashCode (NIL));
    Line ("ClassName", Java.ClassName (JStringBuilder.New ()));
    Bool ("IsInstance List", Java.IsInstance (JArrayList.New (), "java.util.List"));
    Bool ("IsInstance Object", Java.IsInstance (JArrayList.New (), "java.lang.Object"));
    Bool ("IsInstance other", Java.IsInstance (JArrayList.New (), "no.such.Class"));
    Bool ("IsInstance NIL", Java.IsInstance (NIL, "java.lang.Object"));
    Bool ("IsInstance own", Java.IsInstance (own, "java.lang.Object"));
    Int ("FromInt", Java.ToInt (Java.FromInt (-5)));
    Int ("FromLong", Java.ToInt (Java.FromLong (FIRST (INTEGER))));
    Line ("box class", Java.ClassName (Java.FromInt (1)));
    Line ("box class", Java.ClassName (Java.FromLong (1)));
    TRY
      EVAL Java.ToInt ("text");
    EXCEPT
    | RuntimeError.E (e) => Line ("ToInt", RuntimeError.Tag (e));
    END;
    a := Java.NewLongs (longs);
    Int ("Length", Java.Length (a));
    JArrays.sortLongs (a);
    Line ("sorted copy", JArrays.longsToText (a));
    Int ("original", longs [0]);
    Int ("GetLongs short", Java.GetLongs (a, two));
    Int ("GetLongs short 1", two [1]);
    Int ("GetLongs long", Java.GetLongs (a, four));
    Int ("GetLongs long 2", four [2]);
    a := Java.NewBytes (SUBARRAY (chars, 1, 1));
    Line ("bytes", JArrays.bytesToText (a));
    Int ("GetBytes", Java.GetBytes (a, outc));
    Int ("GetBytes 0", ORD (outc [0]));
    a := Java.NewDoubles (doubles);
    Line ("doubles", JArrays.doublesToText (a));
    Int ("GetDoubles", Java.GetDoubles (a, outd));
    Line ("GetDoubles 1", Fmt.LongReal (outd [1]));
    a := Java.NewStrings (texts);
    Line ("strings", JArrays.objectsToText (a));
    Int ("GetStrings", Java.GetStrings (a, outt));
    Line ("GetStrings 1", outt [1]);
    TRY
      EVAL Java.GetLongs (Java.NewBytes (chars), two);
    EXCEPT
    | RuntimeError.E (e) => Line ("wrong array", RuntimeError.Tag (e));
    END;
    TRY
      EVAL Java.Length ("text");
    EXCEPT
    | RuntimeError.E (e) => Line ("not an array", RuntimeError.Tag (e));
    END;
    TRY
      JArrays.sortLongs ("text");
    EXCEPT
    | RuntimeError.E (e) => Line ("REFANY of the wrong class", RuntimeError.Tag (e));
    END;
  END Helpers;

BEGIN
  IF Params.Count > 1 THEN
    WITH mode = Params.Get (1) DO
      IF Text.Equal (mode, "range") THEN
        Int ("abs", JMath.AbsInt (LAST (INTEGER)));
      ELSIF Text.Equal (mode, "index") THEN
        EVAL JList.get (JArrayList.New (), 5);
      ELSIF Text.Equal (mode, "nil") THEN
        Int ("size", JList.size (NIL));
      ELSIF Text.Equal (mode, "uncaught") THEN
        Int ("parse", JInteger.parseInt ("nope"));
      END;
    END;
  ELSE
    Statics ();
    Strings ();
    Lists ();
    Exceptions ();
    Callbacks ();
    RaiseThrough ();
    Helpers ();
  END;
END Main.
