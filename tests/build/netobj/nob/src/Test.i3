(* The network objects of tests/net/netobj: T has stubs of version 1 of
   the protocol (its referents are pickled by Pickle), U of version 2
   (Pickle2). *)

INTERFACE Test;

IMPORT NetObj, Thread, Rd, Wr;

TYPE
  Color = {Red, Green, Blue};
  Rec = RECORD
    i: INTEGER;
    r: REAL;
    l: LONGREAL;
    b: BOOLEAN;
    c: CHAR;
    w: WIDECHAR;
    s: [-3..1000];
    e: Color;
    set: SET OF [0..40];
  END;
  List = REF RECORD head: INTEGER; name: TEXT; tail: List END;

  T = NetObj.T OBJECT METHODS
    add (a, b: INTEGER): INTEGER RAISES {NetObj.Error, Thread.Alerted};
    echo (t: TEXT): TEXT RAISES {NetObj.Error, Thread.Alerted};
    rec (READONLY r: Rec; VAR out: Rec) RAISES {NetObj.Error, Thread.Alerted};
    list (n: INTEGER): List RAISES {NetObj.Error, Thread.Alerted};
    sum (l: List): INTEGER RAISES {NetObj.Error, Thread.Alerted};
    arr (VAR a: ARRAY OF INTEGER) RAISES {NetObj.Error, Thread.Alerted};
    chars (READONLY a: ARRAY OF CHAR; VAR w: ARRAY OF WIDECHAR): TEXT
      RAISES {NetObj.Error, Thread.Alerted};
    texts (READONLY a: ARRAY OF TEXT): TEXT RAISES {NetObj.Error, Thread.Alerted};
    fail (code: INTEGER) RAISES {NetObj.Error, Thread.Alerted, Oops};
    callback (cb: T; n: INTEGER): INTEGER RAISES {NetObj.Error, Thread.Alerted};
    other (): U RAISES {NetObj.Error, Thread.Alerted};
    reader (rd: Rd.T): TEXT RAISES {NetObj.Error, Thread.Alerted};
    writer (wr: Wr.T; t: TEXT) RAISES {NetObj.Error, Thread.Alerted};
    quit () RAISES {NetObj.Error, Thread.Alerted};
  END;

  U = NetObj.T OBJECT METHODS
    list (n: INTEGER): List RAISES {NetObj.Error, Thread.Alerted};
    sum (l: List): INTEGER RAISES {NetObj.Error, Thread.Alerted};
    rec (READONLY r: Rec): Rec RAISES {NetObj.Error, Thread.Alerted};
    same (t: T): BOOLEAN RAISES {NetObj.Error, Thread.Alerted};
  END;

EXCEPTION Oops (TEXT);

END Test.
