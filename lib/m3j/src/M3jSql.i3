(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Databases through JDBC, for the library sql of m3j: the procedures are
   methods of the class m3j.rt.Sql (through m3j.rt.Externals). A result
   less than 0 is one of the kinds below, negated; "fail" then describes
   the error (see "Kind" and "Describe"). Parameters and columns count
   from 1. *)

INTERFACE M3jSql;

CONST
  (* the kinds of errors: ORD (SQL.Kind) + 1 *)
  NoDriver = 1;
  Connection = 2;
  Auth = 3;
  Syntax = 4;
  Constraint = 5;
  Rollback = 6;
  Data = 7;
  Timeout = 8;
  NotSupported = 9;
  Null = 10;
  Type = 11;
  Index = 12;
  Unbound = 13;
  Closed = 14;
  Other = 15;
  Alerted = 16;

  (* operations of "Execute" *)
  XUpdate = 0;
  XQuery = 1;
  XBatch = 2;
  XKeys = 3;

  (* operations of "Transaction" *)
  TxAutoCommitOff = 0;
  TxAutoCommitOn = 1;
  TxCommit = 2;
  TxRollback = 3;

(* The types of values are ORD (SQL.Type). *)

<*EXTERNAL "M3jSql__NewProperties"*>
PROCEDURE NewProperties (): REFANY;
(* An empty set of properties for "Connect". *)

<*EXTERNAL "M3jSql__SetProperty"*>
PROCEDURE SetProperty (p: REFANY; name, value: TEXT);

<*EXTERNAL "M3jSql__Connect"*>
PROCEDURE Connect (url: TEXT; props: REFANY; driver: TEXT; timeout: LONGREAL; fetch: INTEGER;
                   VAR fail: REFANY): REFANY;
(* A connection, or NIL with "fail" set. "driver" NIL looks for a driver
   that accepts the URL. *)

<*EXTERNAL "M3jSql__Close"*>
PROCEDURE Close (c: REFANY);

<*EXTERNAL "M3jSql__Exec"*>
PROCEDURE Exec (c: REFANY; sql: TEXT; VAR count: INTEGER; VAR fail: REFANY): INTEGER;

<*EXTERNAL "M3jSql__Query"*>
PROCEDURE Query (c: REFANY; sql: TEXT; VAR rows, fail: REFANY): INTEGER;

<*EXTERNAL "M3jSql__Transaction"*>
PROCEDURE Transaction (c: REFANY; op: INTEGER; VAR fail: REFANY): INTEGER;

<*EXTERNAL "M3jSql__Prepare"*>
PROCEDURE Prepare (c: REFANY; sql: TEXT; keys: BOOLEAN; VAR stmt, fail: REFANY): INTEGER;

<*EXTERNAL "M3jSql__Bind"*>
PROCEDURE Bind (s: REFANY; i, type, n: INTEGER; x: LONGREAL; t: TEXT; VAR fail: REFANY): INTEGER;
(* The value of parameter "i": for the type Null, NULL with the type
   ORD (SQL.Type) "n"; Boolean and Integer in "n"; Real and TimestampZone
   (seconds since 1970) in "x"; Decimal, Text and Bytes in "t" (NIL: NULL). *)

<*EXTERNAL "M3jSql__BindTime"*>
PROCEDURE BindTime (s: REFANY; i, type, year, month, day, hour, minute, second, nanos: INTEGER;
                    VAR fail: REFANY): INTEGER;
(* A date, a time of day or a timestamp for parameter "i". *)

<*EXTERNAL "M3jSql__Add"*>
PROCEDURE Add (s: REFANY; VAR fail: REFANY): INTEGER;
(* The values of the parameters into the batch. *)

<*EXTERNAL "M3jSql__Execute"*>
PROCEDURE Execute (s: REFANY; op: INTEGER; VAR count: INTEGER; VAR rows, fail: REFANY): INTEGER;
(* XUpdate: the count of rows; XQuery and XKeys: rows; XBatch: the number
   of counts, which "BatchCount" gives. *)

<*EXTERNAL "M3jSql__BatchCount"*>
PROCEDURE BatchCount (s: REFANY; i: INTEGER): INTEGER;
(* Count "i" (from 0) of the last batch; -1 if it is unknown. *)

<*EXTERNAL "M3jSql__CloseStmt"*>
PROCEDURE CloseStmt (s: REFANY);

<*EXTERNAL "M3jSql__Columns"*>
PROCEDURE Columns (r: REFANY): INTEGER;

<*EXTERNAL "M3jSql__Column"*>
PROCEDURE Column (r: REFANY; i: INTEGER; VAR name, typeName: TEXT;
                  VAR type, precision, scale, nullable: INTEGER; VAR fail: REFANY): INTEGER;
(* "nullable": 0 no, 1 yes, 2 unknown. *)

<*EXTERNAL "M3jSql__Find"*>
PROCEDURE Find (r: REFANY; name: TEXT): INTEGER;
(* The first column with the name, in any case; 0 if none has it. *)

<*EXTERNAL "M3jSql__Next"*>
PROCEDURE Next (r: REFANY; VAR fail: REFANY): INTEGER;
(* 1 with the next row current, 0 at the end. *)

<*EXTERNAL "M3jSql__Get"*>
PROCEDURE Get (r: REFANY; i, type: INTEGER; VAR n: INTEGER; VAR x: LONGREAL; VAR t: TEXT;
               VAR fail: REFANY): INTEGER;
(* Column "i" of the current row as the type: Null gives "n" 1 for NULL,
   else 0; the others as for "Bind". *)

<*EXTERNAL "M3jSql__GetTime"*>
PROCEDURE GetTime (r: REFANY; i, type: INTEGER; VAR year, month, day, hour, minute, second, nanos: INTEGER;
                   VAR fail: REFANY): INTEGER;

<*EXTERNAL "M3jSql__CloseRows"*>
PROCEDURE CloseRows (r: REFANY);

<*EXTERNAL "M3jSql__Kind"*>
PROCEDURE Kind (f: REFANY): INTEGER;
(* The kind of the failure "f", one of the constants above. *)

<*EXTERNAL "M3jSql__Describe"*>
PROCEDURE Describe (f: REFANY; VAR state: TEXT; VAR code: INTEGER): TEXT;
(* The text of the failure "f", its SQLSTATE (or NIL) and the code of the
   database. *)

END M3jSql.
