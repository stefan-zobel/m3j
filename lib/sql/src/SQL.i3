(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Relational databases through JDBC, the interface of the Java platform
   to their drivers. m3j brings no driver: a program names the jar of its
   driver as Java classes, with "java_classpath" in its m3makefile or with
   "-cp" of the compiler, and the driver that accepts the URL is found
   there.

|    db := SQL.Connect ("jdbc:postgresql://db.example.org/shop",
|                       SQL.Options {user := "shop", password := pw});
|    s := db.prepare ("SELECT name, price FROM item WHERE price < ?");
|    s.bindDecimal (1, "20.00");
|    rows := s.query ();
|    WHILE rows.next () DO
|      Wr.PutText (wr, rows.text (1) & " " & rows.decimal (2) & "\n");
|    END;
|    db.close ();

   Values go to the database as parameters ("?"), never as part of the
   text of a statement. Parameters and columns count from 1, as in SQL.
   Texts of statements and values are characters; "bytes" are one "CHAR"
   for each byte.

   All calls of a connection run one after the other, on a thread of its
   own; a "T" may be used by several threads, but a "Stmt" with its values
   and a "Rows" are for one thread at a time. A call that waits for the
   database ends with "Thread.Alerted" when the thread is alerted, and with
   "Kind.Timeout" when it takes longer than the "timeout" of the options:
   its statement is then cancelled; a driver that does not end it within a
   few seconds loses its connection ("Kind.Closed" afterwards).

   Rows are fetched "fetch" at a time and kept in memory, each value whole,
   also large ones ("CLOB", "BLOB"). Close what is no longer needed:
   "close" of a "T" closes its statements and rows too. *)

INTERFACE SQL;

IMPORT Thread, Time;

TYPE
  Kind = {NoDriver, Connection, Auth, Syntax, Constraint, Rollback, Data, Timeout, NotSupported, Null,
          Type, Index, Unbound, Closed, Other};

(* "NoDriver": no driver accepts the URL. "Connection": the connection
   failed or broke. "Auth": the user or the password was not accepted.
   "Syntax": the statement is not valid, or names a table or column that
   does not exist. "Constraint": a constraint such as a unique key or NOT
   NULL was violated. "Rollback": the database rolled the transaction back,
   as after a deadlock; it may succeed if repeated. "Data": a value is not
   valid for its type or out of range. "Timeout": the call took too long,
   or a lock could not be had in time. "NotSupported": the driver does not
   support the operation. "Null": a value that is NULL was read as a type
   that has no NULL. "Type": a value cannot be read as the type asked for.
   "Index": no parameter or column of that number or name. "Unbound": a
   parameter has no value. "Closed": the connection, the statement or the
   rows are closed. "Other": anything else.

   The kind comes from the class of the Java exception of the driver, else
   from the class of its SQLSTATE, else, for SQLite, which has no SQLSTATE,
   from its result code. *)

  Failure = REF RECORD
    kind: Kind;
    state: TEXT;     (* the SQLSTATE of the driver, five characters, or NIL *)
    code: INTEGER;   (* the code of the error of the database, 0 if none *)
    text: TEXT;      (* the text of the driver, or one of m3j *)
  END;

EXCEPTION Error (Failure);

TYPE
  Property = RECORD name, value: TEXT END;

  Options = RECORD
    user, password: TEXT := NIL;
    properties: REF ARRAY OF Property := NIL;  (* further properties of the driver *)
    driver: TEXT := NIL;     (* the Java class of the driver, for drivers that do not
                                register as a service *)
    timeout := 30.0D0;       (* seconds, for connecting and for each call; <= 0: none *)
    fetch: CARDINAL := 256;  (* rows fetched at a time *)
  END;

CONST Defaults = Options {};

TYPE
  Type = {Null, Boolean, Integer, Real, Decimal, Text, Bytes, Date, Time, Timestamp, TimestampZone,
          Other};
  (* The types of columns and of NULL values. "Integer" covers the SQL types
     from TINYINT to BIGINT, "Real" REAL, FLOAT and DOUBLE, "Decimal" NUMERIC
     and DECIMAL, "Text" the character types and CLOB, "Bytes" the binary
     types and BLOB, "Timestamp" TIMESTAMP without time zone, "TimestampZone"
     TIMESTAMP WITH TIME ZONE. *)

  Nullable = {No, Yes, Unknown};

  Column = RECORD
    name: TEXT;       (* the label of the column *)
    typeName: TEXT;   (* the name of its type in the database *)
    type: Type;
    precision, scale: INTEGER;
    nullable: Nullable;
  END;

  DateValue = RECORD year: INTEGER; month: [1 .. 12]; day: [1 .. 31] END;
  TimeValue = RECORD
    hour: [0 .. 23];
    minute, second: [0 .. 59];
    nanos: [0 .. 999999999] := 0;
  END;
  Timestamp = RECORD date: DateValue; time: TimeValue END;
  (* Dates and times without a time zone, as the database has them: no
     conversion to or from the time zone of the program. *)

  T <: Public;
  Public = OBJECT
  METHODS
    prepare (sql: TEXT; keys := FALSE): Stmt RAISES {Error, Thread.Alerted};
    (* A statement with parameters "?". With "keys", the keys that the
       database generates for its rows are kept for "Stmt.keys". *)

    exec (sql: TEXT): INTEGER RAISES {Error, Thread.Alerted};
    (* Executes a statement without parameters that returns no rows: the
       number of rows it changed (0 for others, such as CREATE). *)

    query (sql: TEXT): Rows RAISES {Error, Thread.Alerted};
    (* Executes a query without parameters. *)

    setAutoCommit (on: BOOLEAN) RAISES {Error, Thread.Alerted};
    (* At first each statement is a transaction of its own; after
       "setAutoCommit (FALSE)", a transaction lasts until "commit" or
       "rollback". *)

    commit () RAISES {Error, Thread.Alerted};
    rollback () RAISES {Error, Thread.Alerted};

    close ();
    (* Closes the connection with its statements and rows. *)
  END;

  Stmt <: StmtPublic;
  StmtPublic = OBJECT
  METHODS
    bindNull (i: CARDINAL; type := Type.Null) RAISES {Error};
    bindBool (i: CARDINAL; v: BOOLEAN) RAISES {Error};
    bindInt (i: CARDINAL; v: INTEGER) RAISES {Error};
    bindReal (i: CARDINAL; v: LONGREAL) RAISES {Error};
    bindDecimal (i: CARDINAL; v: TEXT) RAISES {Error};   (* such as "-12.50"; NIL: NULL *)
    bindText (i: CARDINAL; v: TEXT) RAISES {Error};      (* NIL: NULL *)
    bindBytes (i: CARDINAL; v: TEXT) RAISES {Error};     (* NIL: NULL *)
    bindDate (i: CARDINAL; READONLY v: DateValue) RAISES {Error};
    bindTime (i: CARDINAL; READONLY v: TimeValue) RAISES {Error};
    bindTimestamp (i: CARDINAL; READONLY v: Timestamp) RAISES {Error};
    bindInstant (i: CARDINAL; v: Time.T) RAISES {Error};
    (* A moment for a TIMESTAMP WITH TIME ZONE, given in UTC. *)
    (* Each sets the value of parameter "i" until it is set again; all
       parameters need a value before the statement is executed. *)

    exec (): INTEGER RAISES {Error, Thread.Alerted};
    (* Executes the statement: the number of rows it changed. *)

    query (): Rows RAISES {Error, Thread.Alerted};
    (* Executes the query; the rows of an earlier execution are closed. *)

    add () RAISES {Error};
    (* Adds the values of the parameters to the batch. *)

    execBatch (): REF ARRAY OF INTEGER RAISES {Error, Thread.Alerted};
    (* Executes the statement with each set of values of the batch, which is
       then empty: the numbers of rows changed, -1 where the driver does
       not tell. *)

    keys (): Rows RAISES {Error, Thread.Alerted};
    (* The keys generated by the last execution, if prepared with "keys". *)

    close ();
  END;

  Rows <: RowsPublic;
  RowsPublic = OBJECT
  METHODS
    columns (): CARDINAL;
    column (i: CARDINAL): Column RAISES {Error};
    find (name: TEXT): CARDINAL RAISES {Error};
    (* The number of the first column with the name, in any case. *)

    next (): BOOLEAN RAISES {Error, Thread.Alerted};
    (* Makes the next row the current one; FALSE at the end. *)

    (* The getters read a column of the current row. A NULL is "Kind.Null",
       except for "decimal", "text" and "bytes", which return NIL. *)
    isNull (i: CARDINAL): BOOLEAN RAISES {Error};
    bool (i: CARDINAL): BOOLEAN RAISES {Error};
    (* A BOOLEAN, or an integer 0 or 1 (SQLite keeps booleans so). *)
    int (i: CARDINAL): INTEGER RAISES {Error};
    (* An integer, or a decimal number without a fraction in range. *)
    real (i: CARDINAL): LONGREAL RAISES {Error};
    (* Any number. *)
    decimal (i: CARDINAL): TEXT RAISES {Error};
    (* Any number, in decimal digits without an exponent. *)
    text (i: CARDINAL): TEXT RAISES {Error};
    (* Any value but bytes: numbers as by "decimal" (reals in the form of
       Java's Double.toString), booleans as TRUE and FALSE, dates and times
       in the form of ISO 8601, such as 2026-11-10T18:32:33.5. *)
    bytes (i: CARDINAL): TEXT RAISES {Error};
    date (i: CARDINAL): DateValue RAISES {Error};
    (* A DATE, or the date of a timestamp. *)
    time (i: CARDINAL): TimeValue RAISES {Error};
    (* A TIME, or the time of day of a timestamp. *)
    timestamp (i: CARDINAL): Timestamp RAISES {Error};
    (* A TIMESTAMP, a DATE at midnight, or the local date and time of a
       TIMESTAMP WITH TIME ZONE. *)
    instant (i: CARDINAL): Time.T RAISES {Error};
    (* A TIMESTAMP WITH TIME ZONE. *)
    (* Dates and times that a database keeps as texts in the form of ISO
       8601, as SQLite does, are read as such. *)

    close ();
  END;

PROCEDURE Connect (url: TEXT; READONLY o := Defaults): T RAISES {Error, Thread.Alerted};
(* Connects to the database of the JDBC URL, such as
   "jdbc:postgresql://host/db", "jdbc:h2:./data" or "jdbc:sqlite:file.db". *)

END SQL.
