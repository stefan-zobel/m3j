(* Units of m3core and libm3 that the library of m3j brings for programs:
   the procedures of Boolean, Int32, Int64, WideChar and Unicode; FmtTime
   in UTC (of times made from dates); the geometry of Point, Rect,
   Interval, Region and Transform;
   reading and printing S-expressions (Sx); priority queues (IntPQ,
   TextPQ); properties by typecode (MProperty); NullRd, NullWr, RdCopy and
   RdUtils; RealFloatExtras and LongFloatExtras; Capability; and generic
   instances of tables, lists, sequences and sorts. *)

MODULE Main;

IMPORT Atom, AtomList, AtomSeq, Boolean, Capability, Date, FmtTime, Fmt, Int32, Int64,
       IntList, IntListSort, IntPQ, IntTextTbl, Interval, IO, LongFloatExtras,
       MProperty, MPropertyF, NullRd, NullWr, Point, Rd, RdCopy, RdUtils,
       RealFloatExtras, Rect, Region, SortedTextIntTbl, Sx, Text, TextArraySort,
       TextPQ, TextRd, TextTextTbl, TextWr, Thread, Transform, Unicode,
       WideChar, Wr;

<*FATAL Rd.Failure, Wr.Failure, Thread.Alerted*>

PROCEDURE Line (t: TEXT) =
  BEGIN
    IO.Put (t & "\n");
  END Line;

PROCEDURE B (b: BOOLEAN): TEXT =
  BEGIN
    RETURN Fmt.Bool (b);
  END B;

PROCEDURE I (i: INTEGER): TEXT =
  BEGIN
    RETURN Fmt.Int (i);
  END I;

PROCEDURE Types () =
  BEGIN
    Line ("Boolean: " & I (Boolean.Compare (FALSE, TRUE)) & " "
            & B (Boolean.Equal (TRUE, TRUE)) & " " & I (Boolean.Hash (TRUE)));
    Line ("Int32: " & I (Int32.Compare (-5, 3)) & " " & B (Int32.Equal (7, 7))
            & " " & I (Int32.Hash (-1)));
    Line ("Int64: " & I (Int64.Compare (5L, 3L)) & " " & B (Int64.Equal (7L, 8L))
            & " " & Fmt.LongInt (Int64.Hash (-1L)));
    Line ("WideChar: " & I (WideChar.Compare (W'a', W'b')) & " "
            & B (WideChar.Equal (W'\x1234', W'\x1234')) & " "
            & I (WideChar.Hash (W'\x1234')));
    VAR t := "";
    BEGIN
      FOR i := 0 TO 7 DO
        WITH c = ARRAY [0 .. 7] OF WIDECHAR {W'a', W'Z', W'5', W' ', W'\x00e9',
                                             W'\x03a9', W'\x0663', W'\x4e00'}[i] DO
          t := t & " " & Fmt.Int (ORD (c), 16) & ":"
                 & B (Unicode.IsLetter (c)) & "," & B (Unicode.IsDigit (c)) & ","
                 & B (Unicode.IsUpperCase (c)) & "," & B (Unicode.IsSpace (c)) & ","
                 & Fmt.Int (ORD (Unicode.ToUpperCase (c)), 16) & ","
                 & Fmt.Int (ORD (Unicode.ToLowerCase (c)), 16);
          IF Unicode.IsDigit (c) THEN
            t := t & "=" & I (Unicode.ToDigitValue (c));
          END;
        END;
      END;
      Line ("Unicode:" & t);
    END;
  END Types;

PROCEDURE Times () =
  VAR d: Date.T;
  BEGIN
    (* Times made from dates: the epoch of Time.T depends on the
       implementation (cm3 on Windows: 1601, m3j: 1970). *)
    <*FATAL Date.Error*>
    VAR t := Date.ToTime (Date.T {2000, Date.Month.Feb, 29, 0, 0, 0, 0, "UTC",
                                  Date.WeekDay.Sun});
    BEGIN
      Line ("FmtTime: " & FmtTime.Long (t, Date.UTC));
      Line ("FmtTime: " & FmtTime.Short (t + 86400.5D0 * 400.0D0, Date.UTC));
      Line ("FmtTime: " & FmtTime.Long (t - 3600.0D0 * 7.0D0, Date.UTC));
      d := Date.FromTime (t + 1.0D9, Date.UTC);
      Line ("FmtTime: " & FmtTime.DateLong (d) & " / " & FmtTime.DateShort (d));
    END;
  END Times;

PROCEDURE R (READONLY r: Rect.T): TEXT =
  BEGIN
    RETURN "[" & I (r.west) & "," & I (r.east) & "," & I (r.north) & ","
             & I (r.south) & "]";
  END R;

PROCEDURE P (READONLY p: Point.T): TEXT =
  BEGIN
    RETURN "(" & I (p.h) & "," & I (p.v) & ")";
  END P;

PROCEDURE Geometry () =
  VAR
    a := Rect.FromEdges (0, 10, 0, 5);
    b := Rect.FromEdges (5, 20, 2, 8);
    rg: Region.T;
    rects: REF ARRAY OF Rect.T;
    t: TEXT;
    tr: Transform.T;
  BEGIN
    Line ("Point: " & P (Point.Add (Point.T {3, 4}, Point.T {-1, 2})) & " "
            & P (Point.Mul (Point.T {3, 4}, 3)) & " "
            & I (Point.DistSquare (Point.T {0, 0}, Point.T {3, 4})));
    Line ("Rect: " & R (Rect.Meet (a, b)) & " " & R (Rect.Join (a, b)) & " "
            & I (Rect.HorSize (a)) & "x" & I (Rect.VerSize (a)) & " "
            & B (Rect.Member (Point.T {5, 3}, a)) & " " & B (Rect.Subset (a, b))
            & " " & P (Rect.Middle (b)) & " " & R (Rect.Inset (b, 1)));
    WITH iv = Interval.FromBounds (3, 9) DO
      Line ("Interval: " & I (iv.lo) & ".." & I (iv.hi) & " size "
              & I (Interval.Size (iv)) & " member "
              & B (Interval.Member (5, iv)) & B (Interval.Member (9, iv)));
    END;
    rg := Region.Join (Region.FromRect (a), Region.FromRect (b));
    rects := Region.ToRects (rg);
    t := "";
    FOR i := 0 TO LAST (rects^) DO t := t & " " & R (rects[i]) END;
    Line ("Region: " & I (NUMBER (rects^)) & t & " box " & R (Region.BoundingBox (rg))
            & " " & B (Region.Member (Point.T {15, 1}, rg))
            & B (Region.Member (Point.T {15, 3}, rg)));
    rg := Region.Difference (Region.FromRect (b), Region.FromRect (a));
    rects := Region.ToRects (rg);
    t := "";
    FOR i := 0 TO LAST (rects^) DO t := t & " " & R (rects[i]) END;
    Line ("Region difference:" & t);
    tr := Transform.Translate (2.0, 3.0, Transform.Scale (2.0, 0.5, Transform.Identity ()));
    (* The value of Hash differs: cm3 converts the REAL numbers to integers
       in its LOOPHOLE instead of taking their bits (check/runtime-hand
       has m3j's value). *)
    Line ("Transform: " & P (Transform.Apply (tr, Point.T {10, 10})) & " hash of equal "
            & B (Transform.Hash (tr) = Transform.Hash (Transform.Translate (2.0, 3.0,
                   Transform.Scale (2.0, 0.5, Transform.Identity ()))))
            & " " & B (Transform.Equal (tr, tr)));
  END Geometry;

PROCEDURE SxTest () =
  VAR rd := TextRd.New ("(define (f x) (* x 2.5)) \"str\" 42 (a . b) -17 sym (nested (list (3)))");
      wr: Wr.T;  e: Sx.T;
  BEGIN
    WHILE NOT Rd.EOF (rd) DO
      TRY
        e := Sx.Read (rd);
        wr := TextWr.New ();
        Sx.Print (wr, e);
        Line ("Sx: " & TextWr.ToText (wr));
      EXCEPT
      | Sx.ReadError (m) => Line ("Sx.ReadError " & m); EXIT;
      | Sx.PrintError => Line ("Sx.PrintError"); EXIT;
      | Rd.EndOfFile => EXIT;
      END;
    END;
  END SxTest;

PROCEDURE Queues () =
  VAR q := NEW (IntPQ.Default).init ();  t := "";
      tq := NEW (TextPQ.Default).init ();
      e3: IntPQ.Elt;
  BEGIN
    FOR i := 0 TO 9 DO
      WITH e = NEW (IntPQ.Elt, priority := (i * 7) MOD 10) DO
        q.insert (e);
        IF i = 3 THEN e3 := e END;
      END;
    END;
    <*FATAL IntPQ.Empty, IntPQ.NotInQueue, TextPQ.Empty*>
    BEGIN
      q.change (e3, -1);
      WHILE q.size () > 0 DO t := t & " " & I (q.deleteMin ().priority) END;
      Line ("IntPQ:" & t);
      FOR i := 0 TO 4 DO
        tq.insert (NEW (TextPQ.Elt,
                        priority := ARRAY [0 .. 4] OF TEXT {"pear", "apple", "fig", "kiwi", "date"}[i]));
      END;
      t := "";
      WHILE tq.size () > 0 DO t := t & " " & tq.deleteMin ().priority END;
      Line ("TextPQ:" & t);
    END;
  END Queues;

TYPE A = REF INTEGER;  B2 = REF TEXT;

PROCEDURE Properties () =
  VAR ps := MProperty.New ();  a := NEW (A);  b := NEW (B2);
  BEGIN
    a^ := 17;
    b^ := "seventeen";
    MPropertyF.Put (ps, a);
    MPropertyF.Put (ps, b);
    Line ("MProperty: " & I (NARROW (MPropertyF.Get (ps, TYPECODE (A)), A)^) & " "
            & NARROW (MPropertyF.Get (ps, TYPECODE (B2)), B2)^);
    MPropertyF.Remove (ps, TYPECODE (A));
    Line ("MProperty after Remove: " & B (MPropertyF.Get (ps, TYPECODE (A)) = NIL));
  END Properties;

PROCEDURE ReadersWriters () =
  VAR nr := NEW (NullRd.T).init ();  nw := NEW (NullWr.T).init ();
      wr := TextWr.New ();  rd: Rd.T;  n: INTEGER;
  BEGIN
    Line ("NullRd: eof " & B (Rd.EOF (nr)) & " length " & I (Rd.Length (nr)));
    Wr.PutText (nw, "nothing");
    Wr.Close (nw);
    Line ("NullWr: closed");
    rd := TextRd.New ("0123456789abcdefghij");
    n := RdCopy.ToWriter (rd, wr, 12);
    Line ("RdCopy: " & I (n) & " " & TextWr.ToText (wr) & " rest " & Rd.GetText (rd, 100));
    rd := TextRd.New ("Hello, wonderful World of Modula-3");
    Line ("RdUtils.Find: " & I (RdUtils.Find (rd, "World")) & " "
            & I (RdUtils.Find (rd, "nothing")));
    rd := TextRd.New ("Hello, wonderful World of Modula-3");
    Line ("RdUtils.Find canonical: "
            & I (RdUtils.Find (rd, "MODULA", RdUtils.ToUpperCaseASCII)));
    Line ("RdUtils.FailureText: "
            & RdUtils.FailureText (NIL) & " "
            & RdUtils.FailureText (AtomList.List2 (Atom.FromText ("disk"), Atom.FromText ("full"))));
  END ReadersWriters;

PROCEDURE Floats () =
  BEGIN
    Line ("RealFloatExtras.Round: " & I (RealFloatExtras.Round (2.5)) & " " & I (RealFloatExtras.Round (-3.7))
            & " LongFloatExtras.Round: " & I (LongFloatExtras.Round (3.5D0)) & " " & I (LongFloatExtras.Round (-0.4D0)));
  END Floats;

PROCEDURE Capabilities () =
  VAR c1 := Capability.New ();  c2 := Capability.New ();  h := Capability.Hash (c1);
  BEGIN
    Line ("Capability: " & B (Capability.Equal (c1, c1)) & " "
            & B (Capability.Equal (c1, c2)) & " hash in 32 bits "
            & B (FIRST (Int32.T) <= h AND h <= LAST (Int32.T)));
  END Capabilities;

PROCEDURE Instances () =
  VAR tt := NEW (TextTextTbl.Default).init ();
      st := NEW (SortedTextIntTbl.Default).init ();
      it := NEW (IntTextTbl.Default).init ();
      seq := NEW (AtomSeq.T).init ();
      words := ARRAY [0 .. 5] OF TEXT {"delta", "alpha", "echo", "charlie", "bravo", "foxtrot"};
      l: IntList.T := NIL;
      t := "";  k: TEXT;  v: INTEGER;  vt: TEXT;
  BEGIN
    FOR i := 0 TO LAST (words) DO
      EVAL tt.put (words[i], Text.Sub (words[i], 0, 2));
      EVAL st.put (words[i], Text.Length (words[i]));
      EVAL it.put (i * i, words[i]);
      seq.addhi (Atom.FromText (words[i]));
      l := IntList.Cons (Text.Length (words[i]) * 10 + i, l);
    END;
    EVAL tt.get ("echo", vt);
    Line ("TextTextTbl: " & I (tt.size ()) & " echo=" & vt);
    WITH iter = st.iterateOrdered () DO
      WHILE iter.next (k, v) DO t := t & " " & k & "=" & I (v) END;
    END;
    Line ("SortedTextIntTbl:" & t);
    EVAL it.get (16, vt);
    Line ("IntTextTbl: 16=" & vt);
    Line ("AtomSeq: " & I (seq.size ()) & " " & Atom.ToText (seq.get (2)));
    l := IntListSort.SortD (l);
    t := "";
    WHILE l # NIL DO t := t & " " & I (l.head); l := l.tail END;
    Line ("IntListSort:" & t);
    TextArraySort.Sort (words);
    t := "";
    FOR i := 0 TO LAST (words) DO t := t & " " & words[i] END;
    Line ("TextArraySort:" & t);
  END Instances;

BEGIN
  Types ();
  Times ();
  Geometry ();
  SxTest ();
  Queues ();
  Properties ();
  ReadersWriters ();
  Floats ();
  Capabilities ();
  Instances ();
END Main.
