(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE SQL;

IMPORT M3jSql, Thread, Time;

REVEAL
  T = Public BRANDED "SQL.T" OBJECT
    h: REFANY;
  OVERRIDES
    prepare := Prepare;
    exec := Exec;
    query := Query;
    setAutoCommit := SetAutoCommit;
    commit := Commit;
    rollback := Rollback;
    close := Close;
  END;

  Stmt = StmtPublic BRANDED "SQL.Stmt" OBJECT
    h: REFANY;
  OVERRIDES
    bindNull := BindNull;
    bindBool := BindBool;
    bindInt := BindInt;
    bindReal := BindReal;
    bindDecimal := BindDecimal;
    bindText := BindText;
    bindBytes := BindBytes;
    bindDate := BindDate;
    bindTime := BindTime;
    bindTimestamp := BindTimestamp;
    bindInstant := BindInstant;
    exec := StmtExec;
    query := StmtQuery;
    add := Add;
    execBatch := ExecBatch;
    keys := Keys;
    close := StmtClose;
  END;

  Rows = RowsPublic BRANDED "SQL.Rows" OBJECT
    h: REFANY;
    n: CARDINAL;
  OVERRIDES
    columns := Columns;
    column := GetColumn;
    find := Find;
    next := Next;
    isNull := IsNull;
    bool := Bool;
    int := Int;
    real := Real;
    decimal := Decimal;
    text := Text;
    bytes := Bytes;
    date := Date;
    time := TimeOfDay;
    timestamp := GetTimestamp;
    instant := Instant;
    close := RowsClose;
  END;

(* ---------------------------------------------------------------- errors *)

PROCEDURE Problem (code: INTEGER; f: REFANY): Failure =
  VAR x := NEW (Failure);  state: TEXT := NIL;  n := 0;
  BEGIN
    IF code < 1 OR code > M3jSql.Other THEN code := M3jSql.Other END;
    x.kind := VAL (code - 1, Kind);
    IF f # NIL THEN
      x.text := M3jSql.Describe (f, state, n);
      x.state := state;
      x.code := n;
    END;
    IF x.text = NIL THEN x.text := "the operation failed" END;
    RETURN x;
  END Problem;

PROCEDURE Raise (code: INTEGER; f: REFANY) RAISES {Error, Thread.Alerted} =
  BEGIN
    IF code = M3jSql.Alerted THEN
      IF Thread.TestAlert () THEN RAISE Thread.Alerted END;
      RAISE Error (Problem (M3jSql.Other, NIL));
    END;
    RAISE Error (Problem (code, f));
  END Raise;

PROCEDURE Check (r: INTEGER; f: REFANY) RAISES {Error} =
  BEGIN
    IF r < 0 THEN RAISE Error (Problem (-r, f)) END;
  END Check;

(* ---------------------------------------------------------------- connections *)

PROCEDURE Connect (url: TEXT; READONLY o := Defaults): T RAISES {Error, Thread.Alerted} =
  VAR p := M3jSql.NewProperties ();  f: REFANY := NIL;  h: REFANY;
  BEGIN
    IF o.properties # NIL THEN
      FOR i := FIRST (o.properties^) TO LAST (o.properties^) DO
        M3jSql.SetProperty (p, o.properties[i].name, o.properties[i].value);
      END;
    END;
    M3jSql.SetProperty (p, "user", o.user);
    M3jSql.SetProperty (p, "password", o.password);
    h := M3jSql.Connect (url, p, o.driver, o.timeout, MAX (o.fetch, 1), f);
    IF h = NIL THEN Raise (M3jSql.Kind (f), f) END;
    RETURN NEW (T, h := h);
  END Connect;

PROCEDURE Prepare (t: T; sql: TEXT; keys: BOOLEAN): Stmt RAISES {Error, Thread.Alerted} =
  VAR s, f: REFANY := NIL;  r: INTEGER;
  BEGIN
    r := M3jSql.Prepare (t.h, sql, keys, s, f);
    IF r < 0 THEN Raise (-r, f) END;
    RETURN NEW (Stmt, h := s);
  END Prepare;

PROCEDURE Exec (t: T; sql: TEXT): INTEGER RAISES {Error, Thread.Alerted} =
  VAR n, r := 0;  f: REFANY := NIL;
  BEGIN
    r := M3jSql.Exec (t.h, sql, n, f);
    IF r < 0 THEN Raise (-r, f) END;
    RETURN n;
  END Exec;

PROCEDURE NewRows (h: REFANY): Rows =
  BEGIN
    RETURN NEW (Rows, h := h, n := M3jSql.Columns (h));
  END NewRows;

PROCEDURE Query (t: T; sql: TEXT): Rows RAISES {Error, Thread.Alerted} =
  VAR rows, f: REFANY := NIL;  r: INTEGER;
  BEGIN
    r := M3jSql.Query (t.h, sql, rows, f);
    IF r < 0 THEN Raise (-r, f) END;
    RETURN NewRows (rows);
  END Query;

PROCEDURE Transaction (t: T; op: INTEGER) RAISES {Error, Thread.Alerted} =
  VAR f: REFANY := NIL;  r: INTEGER;
  BEGIN
    r := M3jSql.Transaction (t.h, op, f);
    IF r < 0 THEN Raise (-r, f) END;
  END Transaction;

PROCEDURE SetAutoCommit (t: T; on: BOOLEAN) RAISES {Error, Thread.Alerted} =
  BEGIN
    IF on THEN
      Transaction (t, M3jSql.TxAutoCommitOn);
    ELSE
      Transaction (t, M3jSql.TxAutoCommitOff);
    END;
  END SetAutoCommit;

PROCEDURE Commit (t: T) RAISES {Error, Thread.Alerted} =
  BEGIN
    Transaction (t, M3jSql.TxCommit);
  END Commit;

PROCEDURE Rollback (t: T) RAISES {Error, Thread.Alerted} =
  BEGIN
    Transaction (t, M3jSql.TxRollback);
  END Rollback;

PROCEDURE Close (t: T) =
  BEGIN
    M3jSql.Close (t.h);
  END Close;

(* ---------------------------------------------------------------- statements *)

PROCEDURE Bind (s: Stmt; i: CARDINAL; type: Type; n: INTEGER; x: LONGREAL; t: TEXT) RAISES {Error} =
  VAR f: REFANY := NIL;
  BEGIN
    Check (M3jSql.Bind (s.h, i, ORD (type), n, x, t, f), f);
  END Bind;

PROCEDURE BindNull (s: Stmt; i: CARDINAL; type: Type) RAISES {Error} =
  BEGIN
    Bind (s, i, Type.Null, ORD (type), 0.0D0, NIL);
  END BindNull;

PROCEDURE BindBool (s: Stmt; i: CARDINAL; v: BOOLEAN) RAISES {Error} =
  BEGIN
    Bind (s, i, Type.Boolean, ORD (v), 0.0D0, NIL);
  END BindBool;

PROCEDURE BindInt (s: Stmt; i: CARDINAL; v: INTEGER) RAISES {Error} =
  BEGIN
    Bind (s, i, Type.Integer, v, 0.0D0, NIL);
  END BindInt;

PROCEDURE BindReal (s: Stmt; i: CARDINAL; v: LONGREAL) RAISES {Error} =
  BEGIN
    Bind (s, i, Type.Real, 0, v, NIL);
  END BindReal;

PROCEDURE BindDecimal (s: Stmt; i: CARDINAL; v: TEXT) RAISES {Error} =
  BEGIN
    Bind (s, i, Type.Decimal, 0, 0.0D0, v);
  END BindDecimal;

PROCEDURE BindText (s: Stmt; i: CARDINAL; v: TEXT) RAISES {Error} =
  BEGIN
    Bind (s, i, Type.Text, 0, 0.0D0, v);
  END BindText;

PROCEDURE BindBytes (s: Stmt; i: CARDINAL; v: TEXT) RAISES {Error} =
  BEGIN
    Bind (s, i, Type.Bytes, 0, 0.0D0, v);
  END BindBytes;

PROCEDURE BindInstant (s: Stmt; i: CARDINAL; v: Time.T) RAISES {Error} =
  BEGIN
    Bind (s, i, Type.TimestampZone, 0, v, NIL);
  END BindInstant;

PROCEDURE BindDateTime (s: Stmt; i: CARDINAL; type: Type; READONLY d: DateValue; READONLY t: TimeValue)
  RAISES {Error} =
  VAR f: REFANY := NIL;
  BEGIN
    Check (M3jSql.BindTime (s.h, i, ORD (type), d.year, d.month, d.day, t.hour, t.minute, t.second,
                            t.nanos, f), f);
  END BindDateTime;

CONST
  NoDate = DateValue {year := 1970, month := 1, day := 1};
  NoTime = TimeValue {hour := 0, minute := 0, second := 0};

PROCEDURE BindDate (s: Stmt; i: CARDINAL; READONLY v: DateValue) RAISES {Error} =
  BEGIN
    BindDateTime (s, i, Type.Date, v, NoTime);
  END BindDate;

PROCEDURE BindTime (s: Stmt; i: CARDINAL; READONLY v: TimeValue) RAISES {Error} =
  BEGIN
    BindDateTime (s, i, Type.Time, NoDate, v);
  END BindTime;

PROCEDURE BindTimestamp (s: Stmt; i: CARDINAL; READONLY v: Timestamp) RAISES {Error} =
  BEGIN
    BindDateTime (s, i, Type.Timestamp, v.date, v.time);
  END BindTimestamp;

PROCEDURE Execute (s: Stmt; op: INTEGER; VAR rows: REFANY): INTEGER RAISES {Error, Thread.Alerted} =
  VAR n, r := 0;  f: REFANY := NIL;
  BEGIN
    r := M3jSql.Execute (s.h, op, n, rows, f);
    IF r < 0 THEN Raise (-r, f) END;
    RETURN n;
  END Execute;

PROCEDURE StmtExec (s: Stmt): INTEGER RAISES {Error, Thread.Alerted} =
  VAR rows: REFANY := NIL;
  BEGIN
    RETURN Execute (s, M3jSql.XUpdate, rows);
  END StmtExec;

PROCEDURE StmtQuery (s: Stmt): Rows RAISES {Error, Thread.Alerted} =
  VAR rows: REFANY := NIL;
  BEGIN
    EVAL Execute (s, M3jSql.XQuery, rows);
    RETURN NewRows (rows);
  END StmtQuery;

PROCEDURE Add (s: Stmt) RAISES {Error} =
  VAR f: REFANY := NIL;
  BEGIN
    Check (M3jSql.Add (s.h, f), f);
  END Add;

PROCEDURE ExecBatch (s: Stmt): REF ARRAY OF INTEGER RAISES {Error, Thread.Alerted} =
  VAR rows: REFANY := NIL;  n: INTEGER;  a: REF ARRAY OF INTEGER;
  BEGIN
    n := Execute (s, M3jSql.XBatch, rows);
    a := NEW (REF ARRAY OF INTEGER, n);
    FOR i := 0 TO n - 1 DO a[i] := M3jSql.BatchCount (s.h, i) END;
    RETURN a;
  END ExecBatch;

PROCEDURE Keys (s: Stmt): Rows RAISES {Error, Thread.Alerted} =
  VAR rows: REFANY := NIL;
  BEGIN
    EVAL Execute (s, M3jSql.XKeys, rows);
    RETURN NewRows (rows);
  END Keys;

PROCEDURE StmtClose (s: Stmt) =
  BEGIN
    M3jSql.CloseStmt (s.h);
  END StmtClose;

(* ---------------------------------------------------------------- rows *)

PROCEDURE Columns (r: Rows): CARDINAL =
  BEGIN
    RETURN r.n;
  END Columns;

PROCEDURE GetColumn (r: Rows; i: CARDINAL): Column RAISES {Error} =
  VAR
    c: Column;
    name, typeName: TEXT := NIL;
    type, precision, scale, nullable := 0;
    f: REFANY := NIL;
  BEGIN
    Check (M3jSql.Column (r.h, i, name, typeName, type, precision, scale, nullable, f), f);
    c.name := name;
    c.typeName := typeName;
    c.type := VAL (type, Type);
    c.precision := precision;
    c.scale := scale;
    c.nullable := VAL (nullable, Nullable);
    RETURN c;
  END GetColumn;

PROCEDURE Find (r: Rows; name: TEXT): CARDINAL RAISES {Error} =
  VAR i: INTEGER;  x: Failure;
  BEGIN
    i := M3jSql.Find (r.h, name);
    IF i = 0 THEN
      x := Problem (M3jSql.Index, NIL);
      IF name = NIL THEN
        x.text := "no column of the name NIL";
      ELSE
        x.text := "no column " & name;
      END;
      RAISE Error (x);
    END;
    RETURN i;
  END Find;

PROCEDURE Next (r: Rows): BOOLEAN RAISES {Error, Thread.Alerted} =
  VAR f: REFANY := NIL;  x: INTEGER;
  BEGIN
    x := M3jSql.Next (r.h, f);
    IF x < 0 THEN Raise (-x, f) END;
    RETURN x = 1;
  END Next;

PROCEDURE Get (r: Rows; i: CARDINAL; type: Type; VAR n: INTEGER; VAR x: LONGREAL; VAR t: TEXT)
  RAISES {Error} =
  VAR f: REFANY := NIL;
  BEGIN
    Check (M3jSql.Get (r.h, i, ORD (type), n, x, t, f), f);
  END Get;

PROCEDURE IsNull (r: Rows; i: CARDINAL): BOOLEAN RAISES {Error} =
  VAR n := 0;  x := 0.0D0;  t: TEXT := NIL;
  BEGIN
    Get (r, i, Type.Null, n, x, t);
    RETURN n # 0;
  END IsNull;

PROCEDURE Bool (r: Rows; i: CARDINAL): BOOLEAN RAISES {Error} =
  VAR n := 0;  x := 0.0D0;  t: TEXT := NIL;
  BEGIN
    Get (r, i, Type.Boolean, n, x, t);
    RETURN n # 0;
  END Bool;

PROCEDURE Int (r: Rows; i: CARDINAL): INTEGER RAISES {Error} =
  VAR n := 0;  x := 0.0D0;  t: TEXT := NIL;
  BEGIN
    Get (r, i, Type.Integer, n, x, t);
    RETURN n;
  END Int;

PROCEDURE Real (r: Rows; i: CARDINAL): LONGREAL RAISES {Error} =
  VAR n := 0;  x := 0.0D0;  t: TEXT := NIL;
  BEGIN
    Get (r, i, Type.Real, n, x, t);
    RETURN x;
  END Real;

PROCEDURE Decimal (r: Rows; i: CARDINAL): TEXT RAISES {Error} =
  VAR n := 0;  x := 0.0D0;  t: TEXT := NIL;
  BEGIN
    Get (r, i, Type.Decimal, n, x, t);
    RETURN t;
  END Decimal;

PROCEDURE Text (r: Rows; i: CARDINAL): TEXT RAISES {Error} =
  VAR n := 0;  x := 0.0D0;  t: TEXT := NIL;
  BEGIN
    Get (r, i, Type.Text, n, x, t);
    RETURN t;
  END Text;

PROCEDURE Bytes (r: Rows; i: CARDINAL): TEXT RAISES {Error} =
  VAR n := 0;  x := 0.0D0;  t: TEXT := NIL;
  BEGIN
    Get (r, i, Type.Bytes, n, x, t);
    RETURN t;
  END Bytes;

PROCEDURE Instant (r: Rows; i: CARDINAL): Time.T RAISES {Error} =
  VAR n := 0;  x := 0.0D0;  t: TEXT := NIL;
  BEGIN
    Get (r, i, Type.TimestampZone, n, x, t);
    RETURN x;
  END Instant;

PROCEDURE GetDateTime (r: Rows; i: CARDINAL; type: Type; VAR d: DateValue; VAR t: TimeValue)
  RAISES {Error} =
  VAR
    year, month, day, hour, minute, second, nanos := 0;
    f: REFANY := NIL;
  BEGIN
    Check (M3jSql.GetTime (r.h, i, ORD (type), year, month, day, hour, minute, second, nanos, f), f);
    IF type # Type.Time THEN
      d.year := year;
      d.month := month;
      d.day := day;
    END;
    t.hour := hour;
    t.minute := minute;
    t.second := second;
    t.nanos := nanos;
  END GetDateTime;

PROCEDURE Date (r: Rows; i: CARDINAL): DateValue RAISES {Error} =
  VAR d := NoDate;  t := NoTime;
  BEGIN
    GetDateTime (r, i, Type.Date, d, t);
    RETURN d;
  END Date;

PROCEDURE TimeOfDay (r: Rows; i: CARDINAL): TimeValue RAISES {Error} =
  VAR d := NoDate;  t := NoTime;
  BEGIN
    GetDateTime (r, i, Type.Time, d, t);
    RETURN t;
  END TimeOfDay;

PROCEDURE GetTimestamp (r: Rows; i: CARDINAL): Timestamp RAISES {Error} =
  VAR x := Timestamp {date := NoDate, time := NoTime};
  BEGIN
    GetDateTime (r, i, Type.Timestamp, x.date, x.time);
    RETURN x;
  END GetTimestamp;

PROCEDURE RowsClose (r: Rows) =
  BEGIN
    M3jSql.CloseRows (r.h);
  END RowsClose;

BEGIN
END SQL.
