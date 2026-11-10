(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The program that tools/sql_test.py runs: it answers requests on the
   standard input, one per line, with one line each, "ok ..." or
   "error KIND STATE CODE TEXT" for an "SQL.Error" (STATE "-" if none).
   Texts in requests and answers are groups of four hexadecimal digits, the
   UTF-16 code units of their characters, or "-" for "NIL". Bytes are "-"
   (NIL) or "h" and their hexadecimal digits. C, S and R are numbers of
   connections, statements and rows, 0 to 63.

     connect C URL USER PASSWORD CONNECTTIMEOUT TIMEOUT FETCH DRIVER [NAME VALUE]...
     close C
     exec C SQL            ok COUNT
     query C R SQL         ok COLUMNS
     prepare C S KEYS SQL  KEYS 0 or 1
     autocommit C ON, commit C, rollback C
     bind S I KIND VALUE   KIND null (VALUE the ORD of the type), bool
                           (0, 1), int, real, dec, text, bytes, date
                           (Y:M:D), time (H:M:S:N), ts (Y:M:D:H:M:S:N),
                           instant (seconds since 1970)
     sexec S               ok COUNT
     squery S R            ok COLUMNS
     add S
     batch S               ok N COUNT...
     keys S R              ok COLUMNS
     sclose S
     next R                ok 1 or ok 0
     get R I KIND          the value as for "bind"; null gives 1 for NULL
     row R                 ok VALUE... of each column: "-" for NULL, bytes
                           for columns of the type Bytes and for bytes that
                           have no text, else "t" and the text
     count R               reads to the end: ok ROWS SUM (of column 1)
     cols R                ok NAME:TYPENAME:TYPE:PRECISION:SCALE:NULLABLE...
     find R NAME           ok I
     rclose R
     alert C SECONDS SQL   a thread runs the query and fetches its first row;
                           it is alerted after SECONDS: ok alerted MS (from
                           the alert to its end), or what it got
     closeduring C SECONDS SQL
                           a thread runs the query; C is closed after
                           SECONDS: what the thread got
     many C THREADS ROWS TABLE
                           THREADS threads insert ROWS rows each into TABLE
                           (n INTEGER) through C, each with a statement
     pause SECONDS *)

MODULE Main;

IMPORT FloatMode, Fmt, Lex, Rd, Scan, SQL, Stdio, Text, TextWr, Thread, Time, Word, Wr;

<*FATAL FloatMode.Trap, Lex.Error*>

CONST HexDigits = "0123456789abcdef";

CONST
  KindNames = ARRAY SQL.Kind OF TEXT {"NoDriver", "Connection", "Auth", "Syntax", "Constraint",
                                      "Rollback", "Data", "Timeout", "NotSupported", "Null", "Type",
                                      "Index", "Unbound", "Closed", "Other"};

VAR
  conns: ARRAY [0 .. 63] OF SQL.T;
  stmts: ARRAY [0 .. 63] OF SQL.Stmt;
  rows: ARRAY [0 .. 63] OF SQL.Rows;

(* texts *)

PROCEDURE Arg (t: TEXT): TEXT =
  VAR n: INTEGER;  r: REF ARRAY OF WIDECHAR;  v: INTEGER;
  BEGIN
    IF Text.Equal (t, "-") THEN RETURN NIL END;
    n := Text.Length (t) DIV 4;
    r := NEW (REF ARRAY OF WIDECHAR, n);
    FOR i := 0 TO n - 1 DO
      v := 0;
      FOR k := 4 * i TO 4 * i + 3 DO
        v := v * 16 + Text.FindChar (HexDigits, Text.GetChar (t, k));
      END;
      r[i] := VAL (v, WIDECHAR);
    END;
    RETURN Text.FromWideChars (r^);
  END Arg;

PROCEDURE Hex16 (t: TEXT): TEXT =
  VAR wr := TextWr.New ();  c: INTEGER;
  <*FATAL Wr.Failure, Thread.Alerted*>
  BEGIN
    IF t = NIL THEN RETURN "-" END;
    FOR i := 0 TO Text.Length (t) - 1 DO
      c := ORD (Text.GetWideChar (t, i));
      FOR k := 3 TO 0 BY -1 DO
        Wr.PutChar (wr, Text.GetChar (HexDigits, Word.And (Word.RightShift (c, 4 * k), 15)));
      END;
    END;
    RETURN TextWr.ToText (wr);
  END Hex16;

PROCEDURE Bytes (t: TEXT): TEXT =
  VAR n: INTEGER;  a: REF ARRAY OF CHAR;
  BEGIN
    IF Text.Equal (t, "-") THEN RETURN NIL END;
    n := (Text.Length (t) - 1) DIV 2;
    a := NEW (REF ARRAY OF CHAR, n);
    FOR i := 0 TO n - 1 DO
      a[i] := VAL (Text.FindChar (HexDigits, Text.GetChar (t, 1 + 2 * i)) * 16
                     + Text.FindChar (HexDigits, Text.GetChar (t, 2 + 2 * i)), CHAR);
    END;
    RETURN Text.FromChars (a^);
  END Bytes;

PROCEDURE HexBytes (t: TEXT): TEXT =
  VAR n: INTEGER;  r: REF ARRAY OF CHAR;  c: INTEGER;
  BEGIN
    IF t = NIL THEN RETURN "-" END;
    n := Text.Length (t);
    r := NEW (REF ARRAY OF CHAR, 2 * n + 1);
    r[0] := 'h';
    FOR i := 0 TO n - 1 DO
      c := ORD (Text.GetChar (t, i));
      r[2 * i + 1] := Text.GetChar (HexDigits, c DIV 16);
      r[2 * i + 2] := Text.GetChar (HexDigits, c MOD 16);
    END;
    RETURN Text.FromChars (r^);
  END HexBytes;

PROCEDURE Words (line: TEXT; sep := ' '): REF ARRAY OF TEXT =
  VAR n := 1;  r: REF ARRAY OF TEXT;  start := 0;  k := 0;  end: INTEGER;
  BEGIN
    FOR i := 0 TO Text.Length (line) - 1 DO
      IF Text.GetChar (line, i) = sep THEN INC (n) END;
    END;
    r := NEW (REF ARRAY OF TEXT, n);
    LOOP
      end := Text.FindChar (line, sep, start);
      IF end < 0 THEN r[k] := Text.Sub (line, start); EXIT END;
      r[k] := Text.Sub (line, start, end - start);
      INC (k);
      start := end + 1;
    END;
    RETURN r;
  END Words;

PROCEDURE Int (t: TEXT): INTEGER =
  BEGIN
    RETURN Scan.Int (t);
  END Int;

PROCEDURE Real (x: LONGREAL): TEXT =
  BEGIN
    RETURN Fmt.LongReal (x, Fmt.Style.Sci, 16);
  END Real;

PROCEDURE Describe (f: SQL.Failure): TEXT =
  VAR state := "-";
  BEGIN
    IF f.state # NIL THEN state := f.state END;
    RETURN "error " & KindNames[f.kind] & " " & state & " " & Fmt.Int (f.code) & " " & Hex16 (f.text);
  END Describe;

(* values *)

PROCEDURE DateText (READONLY d: SQL.DateValue): TEXT =
  BEGIN
    RETURN Fmt.Int (d.year) & ":" & Fmt.Int (d.month) & ":" & Fmt.Int (d.day);
  END DateText;

PROCEDURE TimeText (READONLY t: SQL.TimeValue): TEXT =
  BEGIN
    RETURN Fmt.Int (t.hour) & ":" & Fmt.Int (t.minute) & ":" & Fmt.Int (t.second) & ":"
             & Fmt.Int (t.nanos);
  END TimeText;

PROCEDURE ToDate (READONLY p: ARRAY OF TEXT; at: CARDINAL): SQL.DateValue =
  BEGIN
    RETURN SQL.DateValue {year := Int (p[at]), month := Int (p[at + 1]), day := Int (p[at + 2])};
  END ToDate;

PROCEDURE ToTime (READONLY p: ARRAY OF TEXT; at: CARDINAL): SQL.TimeValue =
  BEGIN
    RETURN SQL.TimeValue {hour := Int (p[at]), minute := Int (p[at + 1]), second := Int (p[at + 2]),
                          nanos := Int (p[at + 3])};
  END ToTime;

PROCEDURE Bind (s: SQL.Stmt; i: CARDINAL; kind, v: TEXT) RAISES {SQL.Error} =
  VAR p: REF ARRAY OF TEXT;
  BEGIN
    IF Text.Equal (kind, "null") THEN
      s.bindNull (i, VAL (Int (v), SQL.Type));
    ELSIF Text.Equal (kind, "bool") THEN
      s.bindBool (i, Text.Equal (v, "1"));
    ELSIF Text.Equal (kind, "int") THEN
      s.bindInt (i, Int (v));
    ELSIF Text.Equal (kind, "real") THEN
      s.bindReal (i, Scan.LongReal (v));
    ELSIF Text.Equal (kind, "dec") THEN
      s.bindDecimal (i, Arg (v));
    ELSIF Text.Equal (kind, "text") THEN
      s.bindText (i, Arg (v));
    ELSIF Text.Equal (kind, "bytes") THEN
      s.bindBytes (i, Bytes (v));
    ELSIF Text.Equal (kind, "date") THEN
      p := Words (v, ':');
      s.bindDate (i, ToDate (p^, 0));
    ELSIF Text.Equal (kind, "time") THEN
      p := Words (v, ':');
      s.bindTime (i, ToTime (p^, 0));
    ELSIF Text.Equal (kind, "ts") THEN
      p := Words (v, ':');
      s.bindTimestamp (i, SQL.Timestamp {date := ToDate (p^, 0), time := ToTime (p^, 3)});
    ELSIF Text.Equal (kind, "instant") THEN
      s.bindInstant (i, Scan.LongReal (v));
    END;
  END Bind;

PROCEDURE Get (r: SQL.Rows; i: CARDINAL; kind: TEXT): TEXT RAISES {SQL.Error} =
  BEGIN
    IF Text.Equal (kind, "null") THEN
      RETURN Fmt.Int (ORD (r.isNull (i)));
    ELSIF Text.Equal (kind, "bool") THEN
      RETURN Fmt.Int (ORD (r.bool (i)));
    ELSIF Text.Equal (kind, "int") THEN
      RETURN Fmt.Int (r.int (i));
    ELSIF Text.Equal (kind, "real") THEN
      RETURN Real (r.real (i));
    ELSIF Text.Equal (kind, "dec") THEN
      RETURN Hex16 (r.decimal (i));
    ELSIF Text.Equal (kind, "text") THEN
      RETURN Hex16 (r.text (i));
    ELSIF Text.Equal (kind, "bytes") THEN
      RETURN HexBytes (r.bytes (i));
    ELSIF Text.Equal (kind, "date") THEN
      RETURN DateText (r.date (i));
    ELSIF Text.Equal (kind, "time") THEN
      RETURN TimeText (r.time (i));
    ELSIF Text.Equal (kind, "ts") THEN
      WITH x = r.timestamp (i) DO RETURN DateText (x.date) & ":" & TimeText (x.time) END;
    ELSIF Text.Equal (kind, "instant") THEN
      RETURN Real (r.instant (i));
    ELSE
      RETURN "?";
    END;
  END Get;

PROCEDURE Row (r: SQL.Rows): TEXT RAISES {SQL.Error} =
  VAR out := "ok";
  BEGIN
    FOR i := 1 TO r.columns () DO
      IF r.isNull (i) THEN
        out := out & " -";
      ELSIF r.column (i).type = SQL.Type.Bytes THEN
        out := out & " " & HexBytes (r.bytes (i));
      ELSE
        TRY
          out := out & " t" & Hex16 (r.text (i));
        EXCEPT
        | SQL.Error (f) =>
            (* bytes in a column of another type, as SQLite has them *)
            IF f.kind # SQL.Kind.Type THEN RAISE SQL.Error (f) END;
            out := out & " " & HexBytes (r.bytes (i));
        END;
      END;
    END;
    RETURN out;
  END Row;

PROCEDURE Cols (r: SQL.Rows): TEXT RAISES {SQL.Error} =
  VAR out := "ok";  c: SQL.Column;
  BEGIN
    FOR i := 1 TO r.columns () DO
      c := r.column (i);
      out := out & " " & Hex16 (c.name) & ":" & Hex16 (c.typeName) & ":" & Fmt.Int (ORD (c.type)) & ":"
               & Fmt.Int (c.precision) & ":" & Fmt.Int (c.scale) & ":" & Fmt.Int (ORD (c.nullable));
    END;
    RETURN out;
  END Cols;

(* threads *)

TYPE
  Querier = Thread.Closure OBJECT
    c: SQL.T;
    sql: TEXT;
    result: TEXT := NIL;
  OVERRIDES
    apply := QueryApply;
  END;

PROCEDURE QueryApply (q: Querier): REFANY =
  VAR r: SQL.Rows;
  BEGIN
    TRY
      r := q.c.query (q.sql);
      EVAL r.next ();
      q.result := "ok finished";
    EXCEPT
    | SQL.Error (f) => q.result := Describe (f);
    | Thread.Alerted => q.result := "ok alerted";
    END;
    RETURN NIL;
  END QueryApply;

TYPE
  Inserter = Thread.Closure OBJECT
    c: SQL.T;
    table: TEXT;
    base, n: INTEGER;
    result: TEXT := NIL;
  OVERRIDES
    apply := InsertApply;
  END;

PROCEDURE InsertApply (x: Inserter): REFANY =
  VAR s: SQL.Stmt;
  BEGIN
    TRY
      s := x.c.prepare ("INSERT INTO " & x.table & " (n) VALUES (?)");
      FOR i := 0 TO x.n - 1 DO
        s.bindInt (1, x.base + i);
        IF s.exec () # 1 THEN x.result := "error count"; RETURN NIL END;
      END;
      s.close ();
      x.result := "ok";
    EXCEPT
    | SQL.Error (f) => x.result := Describe (f);
    | Thread.Alerted => x.result := "error alerted";
    END;
    RETURN NIL;
  END InsertApply;

(* requests *)

PROCEDURE Connect (READONLY w: ARRAY OF TEXT): TEXT RAISES {SQL.Error, Thread.Alerted} =
  VAR o := SQL.Defaults;  k := (NUMBER (w) - 9) DIV 2;
  BEGIN
    o.user := Arg (w[3]);
    o.password := Arg (w[4]);
    o.connectTimeout := Scan.LongReal (w[5]);
    o.timeout := Scan.LongReal (w[6]);
    o.fetch := Int (w[7]);
    o.driver := Arg (w[8]);
    IF k > 0 THEN
      o.properties := NEW (REF ARRAY OF SQL.Property, k);
      FOR i := 0 TO k - 1 DO
        o.properties[i] := SQL.Property {name := Arg (w[9 + 2 * i]), value := Arg (w[10 + 2 * i])};
      END;
    END;
    conns[Int (w[1])] := SQL.Connect (Arg (w[2]), o);
    RETURN "ok";
  END Connect;

PROCEDURE Alert (READONLY w: ARRAY OF TEXT): TEXT =
  VAR q := NEW (Querier, c := conns[Int (w[1])], sql := Arg (w[3]));  th := Thread.Fork (q);  t0: Time.T;
  BEGIN
    Thread.Pause (Scan.LongReal (w[2]));
    t0 := Time.Now ();
    Thread.Alert (th);
    EVAL Thread.Join (th);
    IF Text.Equal (q.result, "ok alerted") THEN
      RETURN q.result & " " & Fmt.Int (ROUND ((Time.Now () - t0) * 1000.0D0));
    END;
    RETURN q.result;
  END Alert;

PROCEDURE CloseDuring (READONLY w: ARRAY OF TEXT): TEXT =
  VAR c := conns[Int (w[1])];  q := NEW (Querier, c := c, sql := Arg (w[3]));  th := Thread.Fork (q);
  BEGIN
    Thread.Pause (Scan.LongReal (w[2]));
    c.close ();
    EVAL Thread.Join (th);
    RETURN q.result;
  END CloseDuring;

PROCEDURE Many (READONLY w: ARRAY OF TEXT): TEXT =
  VAR
    c := conns[Int (w[1])];
    k := Int (w[2]);
    n := Int (w[3]);
    x := NEW (REF ARRAY OF Inserter, k);
    t := NEW (REF ARRAY OF Thread.T, k);
  BEGIN
    FOR i := 0 TO k - 1 DO
      x[i] := NEW (Inserter, c := c, table := w[4], base := i * n, n := n);
      t[i] := Thread.Fork (x[i]);
    END;
    FOR i := 0 TO k - 1 DO EVAL Thread.Join (t[i]) END;
    FOR i := 0 TO k - 1 DO
      IF NOT Text.Equal (x[i].result, "ok") THEN RETURN x[i].result END;
    END;
    RETURN "ok";
  END Many;

PROCEDURE Count (r: SQL.Rows): TEXT RAISES {SQL.Error, Thread.Alerted} =
  VAR n, sum := 0;
  BEGIN
    WHILE r.next () DO
      INC (n);
      IF r.columns () > 0 AND NOT r.isNull (1) THEN INC (sum, r.int (1)) END;
    END;
    RETURN "ok " & Fmt.Int (n) & " " & Fmt.Int (sum);
  END Count;

PROCEDURE Batch (s: SQL.Stmt): TEXT RAISES {SQL.Error, Thread.Alerted} =
  VAR a := s.execBatch ();  out := "ok " & Fmt.Int (NUMBER (a^));
  BEGIN
    FOR i := 0 TO LAST (a^) DO out := out & " " & Fmt.Int (a[i]) END;
    RETURN out;
  END Batch;

PROCEDURE Do (READONLY w: ARRAY OF TEXT): TEXT RAISES {SQL.Error, Thread.Alerted} =
  VAR op := w[0];
  BEGIN
    IF Text.Equal (op, "connect") THEN
      RETURN Connect (w);
    ELSIF Text.Equal (op, "close") THEN
      conns[Int (w[1])].close ();
    ELSIF Text.Equal (op, "exec") THEN
      RETURN "ok " & Fmt.Int (conns[Int (w[1])].exec (Arg (w[2])));
    ELSIF Text.Equal (op, "query") THEN
      rows[Int (w[2])] := conns[Int (w[1])].query (Arg (w[3]));
      RETURN "ok " & Fmt.Int (rows[Int (w[2])].columns ());
    ELSIF Text.Equal (op, "prepare") THEN
      stmts[Int (w[2])] := conns[Int (w[1])].prepare (Arg (w[4]), Text.Equal (w[3], "1"));
    ELSIF Text.Equal (op, "autocommit") THEN
      conns[Int (w[1])].setAutoCommit (Text.Equal (w[2], "1"));
    ELSIF Text.Equal (op, "commit") THEN
      conns[Int (w[1])].commit ();
    ELSIF Text.Equal (op, "rollback") THEN
      conns[Int (w[1])].rollback ();
    ELSIF Text.Equal (op, "bind") THEN
      Bind (stmts[Int (w[1])], Int (w[2]), w[3], w[4]);
    ELSIF Text.Equal (op, "sexec") THEN
      RETURN "ok " & Fmt.Int (stmts[Int (w[1])].exec ());
    ELSIF Text.Equal (op, "squery") THEN
      rows[Int (w[2])] := stmts[Int (w[1])].query ();
      RETURN "ok " & Fmt.Int (rows[Int (w[2])].columns ());
    ELSIF Text.Equal (op, "add") THEN
      stmts[Int (w[1])].add ();
    ELSIF Text.Equal (op, "batch") THEN
      RETURN Batch (stmts[Int (w[1])]);
    ELSIF Text.Equal (op, "keys") THEN
      rows[Int (w[2])] := stmts[Int (w[1])].keys ();
      RETURN "ok " & Fmt.Int (rows[Int (w[2])].columns ());
    ELSIF Text.Equal (op, "sclose") THEN
      stmts[Int (w[1])].close ();
    ELSIF Text.Equal (op, "next") THEN
      RETURN "ok " & Fmt.Int (ORD (rows[Int (w[1])].next ()));
    ELSIF Text.Equal (op, "get") THEN
      RETURN "ok " & Get (rows[Int (w[1])], Int (w[2]), w[3]);
    ELSIF Text.Equal (op, "row") THEN
      RETURN Row (rows[Int (w[1])]);
    ELSIF Text.Equal (op, "count") THEN
      RETURN Count (rows[Int (w[1])]);
    ELSIF Text.Equal (op, "cols") THEN
      RETURN Cols (rows[Int (w[1])]);
    ELSIF Text.Equal (op, "find") THEN
      RETURN "ok " & Fmt.Int (rows[Int (w[1])].find (Arg (w[2])));
    ELSIF Text.Equal (op, "rclose") THEN
      rows[Int (w[1])].close ();
    ELSIF Text.Equal (op, "alert") THEN
      RETURN Alert (w);
    ELSIF Text.Equal (op, "closeduring") THEN
      RETURN CloseDuring (w);
    ELSIF Text.Equal (op, "many") THEN
      RETURN Many (w);
    ELSIF Text.Equal (op, "pause") THEN
      Thread.Pause (Scan.LongReal (w[1]));
    ELSE
      RETURN "error unknown request";
    END;
    RETURN "ok";
  END Do;

VAR line, answer: TEXT;

<*FATAL Rd.Failure, Wr.Failure, Thread.Alerted*>
BEGIN
  LOOP
    TRY
      line := Rd.GetLine (Stdio.stdin);
    EXCEPT
    | Rd.EndOfFile => EXIT;
    END;
    TRY
      answer := Do (Words (line)^);
    EXCEPT
    | SQL.Error (f) => answer := Describe (f);
    | Thread.Alerted => answer := "error alerted";
    END;
    Wr.PutText (Stdio.stdout, answer & "\n");
    Wr.Flush (Stdio.stdout);
  END;
END Main.
