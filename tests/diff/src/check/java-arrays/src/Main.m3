(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Main;

IMPORT ByteIn, IO, Java, JArrays, JFile, JInputStream, JList, JOutputStream, JString, JThread, PrintStream, Text,
       ZipEntry;

TYPE
  Sink0 = JOutputStream.T OBJECT
    text := "";
    ones := 0;
  METHODS
    write (b: Java.Int) := WriteOne;
  END;

  (* the overloads of a Java method are methods of types one below the other *)
  Sink = Sink0 OBJECT
  METHODS
    write (READONLY b: ARRAY OF CHAR; off, len: Java.Int) := WriteAll;
  END;

  Src = JInputStream.T OBJECT
    give := TRUE;
  METHODS
    read (): Java.Int := ReadOne;
    readAllBytes (): REF ARRAY OF CHAR := ReadAll;
  END;

PROCEDURE WriteOne (self: Sink0; <*UNUSED*> b: Java.Int) =
  BEGIN
    INC (self.ones);
  END WriteOne;

PROCEDURE WriteAll (self: Sink; READONLY b: ARRAY OF CHAR; off, len: Java.Int) =
  BEGIN
    self.text := self.text & Text.FromChars (SUBARRAY (b, off, len));
  END WriteAll;

PROCEDURE ReadOne (<*UNUSED*> self: Src): Java.Int =
  BEGIN
    RETURN -1;
  END ReadOne;

PROCEDURE ReadAll (self: Src): REF ARRAY OF CHAR =
  VAR r: REF ARRAY OF CHAR := NIL;
  BEGIN
    IF self.give THEN
      r := NEW (REF ARRAY OF CHAR, 2);
      r[0] := 'x';
      r[1] := 'y';
    END;
    RETURN r;
  END ReadAll;

PROCEDURE Check (name: TEXT; ok: BOOLEAN) =
  BEGIN
    IF ok THEN
      IO.Put (name & ": ok\n");
    ELSE
      IO.Put (name & ": FAILED\n");
    END;
  END Check;

PROCEDURE Is (a, b: TEXT): BOOLEAN =
  BEGIN
    RETURN Text.Equal (a, b);
  END Is;

PROCEDURE Parameters () =
  VAR
    a := ARRAY [0 .. 4] OF INTEGER {5, 3, 9, 1, 7};
    three := ARRAY [0 .. 2] OF INTEGER {3, 2, 1};
    ints: ARRAY [0 .. 2] OF Java.Int;
    open := NEW (REF ARRAY OF INTEGER, 3);
    refs := ARRAY [0 .. 2] OF REFANY {"c", "a", "b"};
  BEGIN
    JArrays.sort (SUBARRAY (a, 1, 3));
    Check ("VAR SUBARRAY writes back its part", a[0] = 5 AND a[1] = 1 AND a[2] = 3 AND a[3] = 9 AND a[4] = 7);
    JArrays.sort (a);
    Check ("VAR fixed array to an open formal", a[0] = 1 AND a[4] = 9);
    JArrays.sortThree (three);
    Check ("VAR fixed formal", three[0] = 1 AND three[2] = 3);
    JArrays.fill (ints, 8);
    Check ("VAR ARRAY OF Java.Int", ints[0] = 8 AND ints[2] = 8);
    open[0] := 3;
    open[1] := 2;
    open[2] := 1;
    JArrays.sort (open^);
    Check ("VAR open array", open[0] = 1 AND open[2] = 3);
    Check ("READONLY constructor", Is (JArrays.longs (ARRAY OF INTEGER {1, 2}), "[1, 2]"));
    Check ("VALUE", Is (JArrays.ints (ints), "[8, 8, 8]"));
    Check ("empty", Is (JArrays.longs (SUBARRAY (a, 0, 0)), "[]"));
    Check ("short", Is (JArrays.shorts (ARRAY OF Java.Short {-300, 2}), "[-300, 2]"));
    Check ("byte", Is (JArrays.bytes (ARRAY OF Java.Byte {-1, 2}), "[-1, 2]"));
    Check ("CHAR is a byte", Is (JArrays.chars (ARRAY OF CHAR {'\377', 'A'}), "[-1, 65]"));
    Check ("WIDECHAR", Is (JArrays.wides (ARRAY OF Java.Char {W'a', W'b'}), "[a, b]"));
    Check ("BOOLEAN", Is (JArrays.bools (ARRAY OF BOOLEAN {TRUE, FALSE}), "[true, false]"));
    Check ("REAL", Is (JArrays.reals (ARRAY OF REAL {1.5}), "[1.5]"));
    Check ("LONGREAL", Is (JArrays.doubles (ARRAY OF LONGREAL {2.5d0}), "[2.5]"));
    Check ("ofChars", Is (JString.ofChars (ARRAY OF WIDECHAR {W'h', W'i'}), "hi"));
    Check ("Object array", Is (Java.ToText (JList.ofArray (refs)), "[c, a, b]"));
    JArrays.sortRefs (refs);
    Check ("VAR Object array", Is (NARROW (refs[0], TEXT), "a") AND Is (NARROW (refs[2], TEXT), "c"));
    VAR t: ARRAY [0 .. 63] OF JThread.T;
    BEGIN
      Check ("VAR array of a Java type", JThread.enumerate (t) >= 1 AND t[0] # NIL);
    END;
  END Parameters;

PROCEDURE Results () =
  VAR
    a := ARRAY [0 .. 2] OF INTEGER {4, 5, 6};
    r := JArrays.copyOf (a, 2);
    w := JString.toCharArray ("ab");
    b := JString.getBytes ("hi");
    s := JString.split ("x,y,z", ",");
    z := ZipEntry.New ("e");
  BEGIN
    Check ("REF ARRAY result", NUMBER (r^) = 2 AND r[1] = 5);
    r[0] := 99;
    Check ("a result is a copy", a[0] = 4);
    Check ("REF ARRAY OF WIDECHAR", NUMBER (w^) = 2 AND w[1] = W'b');
    Check ("REF ARRAY OF CHAR", NUMBER (b^) = 2 AND b[0] = 'h');
    Check ("REF ARRAY OF TEXT", NUMBER (s^) = 3 AND Is (s[2], "z"));
    Check ("null is NIL", ZipEntry.getExtra (z) = NIL);
    ZipEntry.setExtra (z, ARRAY OF CHAR {'\167', '\167', '\000', '\000'});
    Check ("result after a parameter", NUMBER (ZipEntry.getExtra (z)^) = 4);
    Check ("roots of a file system", NUMBER (JFile.listRoots ()^) >= 1);
  END Results;

PROCEDURE Streams () =
  VAR
    in := ByteIn.New (ARRAY OF CHAR {'a', 'b', 'c'});
    buf := ARRAY [0 .. 4] OF CHAR {'.', ..};
    n := JInputStream.readInto (in, buf);
    sink := NEW (Sink);
    out := PrintStream.New (sink);
    src := NEW (Src);
  BEGIN
    Check ("VAR ARRAY OF CHAR", n = 3 AND buf[0] = 'a' AND buf[2] = 'c' AND buf[3] = '.');
    PrintStream.print (out, "hello");
    PrintStream.flush (out);
    Check ("Java passes an array to a method", Is (sink.text, "hello"));
    VAR t := JInputStream.readAllBytes (src);
    BEGIN
      Check ("a method returns an array to Java", NUMBER (t^) = 2 AND t[1] = 'y');
    END;
    src.give := FALSE;
    Check ("a method returns NIL to Java", JInputStream.readAllBytes (src) = NIL);
  END Streams;

BEGIN
  Parameters ();
  Results ();
  Streams ();
END Main.
