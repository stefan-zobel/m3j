#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Tests of the library sql of m3j (tests/sql/src/Main.m3) against real
drivers of JDBC, which m3j does not bring: H2, a database in Java, and
SQLite (sqlite-jdbc, with the native SQLite in its jar), both in the process
of the program, on files in the work directory. They need no server and no
connection to the internet.

  python3 tools/sql_test.py [CASE]... [--java JAVA] [--jar JAR]
                            [--runtime JAR] [--h2 JAR] [--sqlite JAR]
                            [--work DIR] [--keep] [--timeout SECONDS]

The cases:

  types    each type of value bound as a parameter and read back with
           each getter that accepts it, NULL, limits of INTEGER, texts
           beyond the BMP, all bytes, large values, decimal numbers with a
           large scale, dates and times with nanoseconds; the columns; the
           errors Null, Type, Index, Unbound and Data
  stmt     exec and query with and without parameters, statements executed
           again with new values, batches, generated keys, find
  rows     results of 0 to 10000 rows around the size of a block, rows
           closed in the middle, the end
  tx       auto-commit off and on, commit and rollback, seen through a
           second connection
  errors   the kinds of the errors of the driver (syntax, constraints,
           conversions, password, locks) and of m3j (no driver, closed)
  alert    an alert and a timeout during a long query and during a fetch;
           the connection afterwards; a close by another thread
  threads  several threads on one connection
  lite     the same with SQLite: its dynamic types, its errors without
           SQLSTATE (by its result codes), a busy database, properties,
           alerts after INSERTs and with rows left open
  cross    SQLite through m3j against the module sqlite3 of Python on the
           same file: Python writes and m3j reads, and the other way
  inmemory the program compiled and run in memory (-run) with the drivers
           given with -cp of the compiler

The cases up to threads use H2. Without arguments all cases run. JAVA
defaults to $JDK25/bin/java, else java from the PATH; JAR to m3j.jar and
the runtime to m3j-runtime.jar in the root of m3j; the jars of the drivers
to H2 2.4.240 and sqlite-jdbc 3.53.4.0 in the local repository of Maven
(~/.m2). Their SHA-256 is checked.
"""

import argparse
import hashlib
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests" / "sql"
M2 = Path.home() / ".m2" / "repository"
H2_JAR = M2 / "com" / "h2database" / "h2" / "2.4.240" / "h2-2.4.240.jar"
H2_SHA256 = "29b70e427cc1c40cdc376283adbb0cc62853073797bb5fe5761f81fe73d57ce0"
SQLITE_JAR = M2 / "org" / "xerial" / "sqlite-jdbc" / "3.53.4.0" / "sqlite-jdbc-3.53.4.0.jar"
SQLITE_SHA256 = "bcb1f51e36f940867e83342f9efbf5968ac44a6bef4d397bb4af7b17b45cd2fb"

# sqlite-jdbc loads its native library; without this the JDK warns on stderr
NATIVE = "--enable-native-access=ALL-UNNAMED"

USER = "sa"
PASSWORD = "secret pw"

# the types of SQL.Type
T_NULL, T_BOOLEAN, T_INTEGER, T_REAL, T_DECIMAL, T_TEXT, T_BYTES, T_DATE, T_TIME, T_TIMESTAMP, T_ZONE, T_OTHER = range(12)


def h16(t):
    return "-" if t is None else t.encode("utf-16-be").hex()


def from_h16(t):
    return None if t == "-" else bytes.fromhex(t).decode("utf-16-be")


def hb(b):
    return "-" if b is None else "h" + b.hex()


def from_hb(t):
    return None if t == "-" else bytes.fromhex(t[1:])


def real(t):
    return float(t.replace("D", "e").replace("d", "e"))


class Failed(Exception):
    pass


def check(what, got, want):
    if got != want:
        raise Failed("%s:\n  got  %r\n  want %r" % (what, got, want))


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


class Program:
    """The test program, which answers each request with a line."""

    def __init__(self, env, cmd):
        self.env = env
        self.p = subprocess.Popen(cmd, cwd=env.work, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                  stderr=subprocess.PIPE, text=True, encoding="ascii")
        self.timer = threading.Timer(env.args.timeout, self.p.kill)
        self.timer.start()

    def ask(self, line):
        self.p.stdin.write(line + "\n")
        self.p.stdin.flush()
        answer = self.p.stdout.readline()
        if not answer:
            raise Failed("the program ended at %r: %s" % (line[:200], self.p.stderr.read()[-2000:]))
        return answer.rstrip("\n")

    def ok(self, line, want=None):
        got = self.ask(line)
        if not got.startswith("ok") or want is not None and got != "ok" + ("" if want == "" else " " + want):
            raise Failed("%s:\n  got  %s\n  want ok %s" % (line[:200], got[:400], want if want is not None else "..."))
        return got[3:]

    def error(self, line, kind, state=None):
        """The error of a request: its words KIND STATE CODE TEXT, with the text decoded."""
        got = self.ask(line)
        w = got.split(" ")
        if w[0] != "error" or len(w) != 5 or w[1] != kind or state is not None and w[2] != state:
            raise Failed("%s:\n  got  %s\n  want error %s %s" % (line[:200], got[:400], kind, state or ""))
        return w[1], w[2], int(w[3]), from_h16(w[4])

    def connect(self, c, url, user=USER, password=PASSWORD, timeout=0.0, fetch=256, driver=None, props=(),
                connect_timeout=30.0):
        line = "connect %d %s %s %s %.3f %.3f %d %s" % (c, h16(url), h16(user), h16(password), connect_timeout, timeout,
                                                        fetch, h16(driver))
        for name, value in props:
            line += " %s %s" % (h16(name), h16(value))
        return line

    def exec(self, c, sql):
        return int(self.ok("exec %d %s" % (c, h16(sql))))

    def query(self, c, r, sql):
        return int(self.ok("query %d %d %s" % (c, r, h16(sql))))

    def prepare(self, c, s, sql, keys=False):
        self.ok("prepare %d %d %d %s" % (c, s, 1 if keys else 0, h16(sql)))

    def row(self, r):
        """The values of the current row: None, bytes or a text each."""
        out = []
        for t in self.ok("row %d" % r).split(" "):
            if t:
                out.append(None if t == "-" else from_hb(t) if t.startswith("h") else from_h16(t[1:]))
        return out

    def close(self):
        self.p.stdin.close()
        rest = self.p.stdout.read()
        code = self.p.wait()
        self.timer.cancel()
        err = self.p.stderr.read()
        if code or rest or err.strip():
            raise Failed("the program: exit %d, %r, %s" % (code, rest[:200], err[-2000:]))


class Env:
    def __init__(self, args, work):
        self.args = args
        self.work = work
        self.classes = work / "classes"
        self.drivers = os.pathsep.join([args.h2, args.sqlite])
        self.cp = os.pathsep.join([str(self.classes), args.runtime, self.drivers])
        self.compiled = False
        self.n = 0

    def program(self, in_memory=False):
        if in_memory:
            return Program(self, [self.args.java, "-Xss64m", NATIVE, "-cp", self.args.jar, "m3j.jvm.Compile", "-cp",
                                  self.drivers, str(TESTS / "src" / "Main.m3"), "-run"])
        if not self.compiled:
            cmd = [self.args.java, "-Xss64m", "-cp", self.args.jar, "m3j.jvm.Compile", str(TESTS / "src" / "Main.m3"),
                   "-d", str(self.classes)]
            r = subprocess.run(cmd, cwd=self.work, capture_output=True, text=True, stdin=subprocess.DEVNULL)
            if r.returncode or r.stdout.strip() or r.stderr.strip():
                raise Failed("compile:\n" + r.stdout + r.stderr)
            self.compiled = True
        return Program(self, [self.args.java, NATIVE, "-cp", self.cp, "m3.Main"])

    def h2(self, name=None):
        """The URL of a new database of H2 in the work directory."""
        if name is None:
            self.n += 1
            name = "db%d" % self.n
        return "jdbc:h2:" + str(self.work / name).replace("\\", "/")

    def lite(self):
        """The URL of a new database of SQLite in the work directory."""
        self.n += 1
        return "jdbc:sqlite:" + str(self.work / ("lite%d.db" % self.n)).replace("\\", "/")


# ---------------------------------------------------------------- types

TYPES_TABLE = ("CREATE TABLE t (id INTEGER PRIMARY KEY, b BOOLEAN, i BIGINT, r DOUBLE PRECISION, n NUMERIC(40,12), "
               "v VARCHAR(200), c CLOB, vb VARBINARY(300), bl BLOB, d DATE, tm TIME(9), ts TIMESTAMP(9), "
               "tz TIMESTAMP(9) WITH TIME ZONE)")

BIG_TEXT = "".join(chr(0x20 + i % 0x5000) for i in range(100000))
BIG_BYTES = bytes((i * 7 + i // 256) % 256 for i in range(70000))
ALL_BYTES = bytes(range(256))


def case_types(env):
    p = env.program()
    p.ok(p.connect(0, env.h2()))
    p.exec(0, TYPES_TABLE)
    p.prepare(0, 0, "INSERT INTO t VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
    rows = [
        [("int", "1"), ("bool", "1"), ("int", "9223372036854775807"), ("real", "0.1"),
         ("dec", h16("-12345678901234567890.012345678901")), ("text", h16("\u00e4\u00f6\u20ac\U0001F600 ok")),
         ("text", h16(BIG_TEXT)), ("bytes", hb(ALL_BYTES)), ("bytes", hb(BIG_BYTES)), ("date", "2026:11:10"),
         ("time", "18:32:33:123456789"), ("ts", "2026:11:10:18:32:33:123456789"), ("instant", "1234567890.25")],
        [("int", "2"), ("bool", "0"), ("int", "-9223372036854775808"), ("real", "-1e300"),
         ("dec", h16("0.000000000001")), ("text", h16("")), ("text", h16("")), ("bytes", "h"), ("bytes", "h"),
         ("date", "1:1:1"), ("time", "0:0:0:0"), ("ts", "1970:1:1:0:0:0:0"), ("instant", "-1.5")],
        [("int", "3"), ("null", str(T_BOOLEAN)), ("null", str(T_INTEGER)), ("null", str(T_REAL)), ("dec", "-"),
         ("text", "-"), ("null", str(T_TEXT)), ("bytes", "-"), ("null", str(T_BYTES)), ("null", str(T_DATE)),
         ("null", str(T_TIME)), ("null", str(T_TIMESTAMP)), ("null", str(T_ZONE))],
        [("int", "4")] + [("null", str(T_NULL))] * 12,
    ]
    for values in rows:
        for i, (kind, v) in enumerate(values):
            p.ok("bind 0 %d %s %s" % (i + 1, kind, v))
        check("insert", p.ok("sexec 0"), "1")

    check("columns", p.query(0, 0, "SELECT * FROM t ORDER BY id"), 13)
    cols = p.ok("cols 0").split(" ")
    want = [("ID", "INTEGER", T_INTEGER, 0), ("B", "BOOLEAN", T_BOOLEAN, 1), ("I", "BIGINT", T_INTEGER, 1),
            ("R", "DOUBLE PRECISION", T_REAL, 1), ("N", "NUMERIC", T_DECIMAL, 1),
            ("V", "CHARACTER VARYING", T_TEXT, 1), ("C", "CHARACTER LARGE OBJECT", T_TEXT, 1),
            ("VB", "BINARY VARYING", T_BYTES, 1), ("BL", "BINARY LARGE OBJECT", T_BYTES, 1),
            ("D", "DATE", T_DATE, 1), ("TM", "TIME", T_TIME, 1), ("TS", "TIMESTAMP", T_TIMESTAMP, 1),
            ("TZ", "TIMESTAMP WITH TIME ZONE", T_ZONE, 1)]
    for c, (name, tname, t, nullable) in zip(cols, want):
        f = c.split(":")
        check("column %s" % name, (from_h16(f[0]), from_h16(f[1]), int(f[2]), int(f[5])), (name, tname, t, nullable))
    check("precision and scale of N", cols[4].split(":")[3:5], ["40", "12"])

    p.error("get 0 1 int", "Other")  # before the first row
    p.ok("next 0", "1")
    check("row 1", p.row(0), ["1", "TRUE", "9223372036854775807", "0.1", "-12345678901234567890.012345678901",
                              "\u00e4\u00f6\u20ac\U0001F600 ok", BIG_TEXT, ALL_BYTES, BIG_BYTES, "2026-11-10",
                              "18:32:33.123456789", "2026-11-10T18:32:33.123456789", "2009-02-13T23:31:30.250Z"])
    check("bool", p.ok("get 0 2 bool"), "1")
    check("int", p.ok("get 0 3 int"), "9223372036854775807")
    check("real", real(p.ok("get 0 4 real")), 0.1)
    check("real of an integer", real(p.ok("get 0 3 real")), 9.223372036854776e18)
    check("real of a decimal", real(p.ok("get 0 5 real")), -1.2345678901234567e19)
    check("decimal of an integer", from_h16(p.ok("get 0 3 dec")), "9223372036854775807")
    check("decimal of a real", from_h16(p.ok("get 0 4 dec")), "0.1")
    check("date", p.ok("get 0 10 date"), "2026:11:10")
    check("time", p.ok("get 0 11 time"), "18:32:33:123456789")
    check("timestamp", p.ok("get 0 12 ts"), "2026:11:10:18:32:33:123456789")
    check("date of a timestamp", p.ok("get 0 12 date"), "2026:11:10")
    check("time of a timestamp", p.ok("get 0 12 time"), "18:32:33:123456789")
    check("instant", real(p.ok("get 0 13 instant")), 1234567890.25)
    check("timestamp of a time with zone", p.ok("get 0 13 ts"), "2009:2:13:23:31:30:250000000")
    check("null", p.ok("get 0 2 null"), "0")
    p.error("get 0 6 int", "Type")
    p.error("get 0 8 text", "Type")
    p.error("get 0 3 bytes", "Type")
    p.error("get 0 5 int", "Type")       # a fraction
    p.error("get 0 12 instant", "Type")  # no time zone
    p.error("get 0 11 date", "Type")
    p.error("get 0 2 int", "Type")
    p.error("get 0 0 int", "Index")
    p.error("get 0 14 int", "Index")

    p.ok("next 0", "1")
    check("row 2", p.row(0), ["2", "FALSE", "-9223372036854775808", "-1.0E300", "0.000000000001", "", "", b"", b"",
                              "0001-01-01", "00:00", "1970-01-01T00:00", "1969-12-31T23:59:58.500Z"])
    check("time 0", p.ok("get 0 11 time"), "0:0:0:0")
    check("instant before 1970", real(p.ok("get 0 13 instant")), -1.5)
    check("decimal 1e-12", from_h16(p.ok("get 0 5 dec")), "0.000000000001")

    for n in (3, 4):
        p.ok("next 0", "1")
        check("row %d" % n, p.row(0), [str(n)] + [None] * 12)
        for i, kind in enumerate(["bool", "int", "real", None, None, None, None, None, "date", "time", "ts",
                                  "instant"]):
            if kind:
                p.error("get 0 %d %s" % (i + 2, kind), "Null")
        check("null text", p.ok("get 0 6 text"), "-")
        check("null decimal", p.ok("get 0 5 dec"), "-")
        check("null bytes", p.ok("get 0 9 bytes"), "-")
        check("is null", p.ok("get 0 7 null"), "1")
    p.ok("next 0", "0")
    p.error("get 0 1 int", "Other")  # after the last row
    p.ok("rclose 0")
    p.error("next 0", "Closed")

    # integers from decimals
    p.query(0, 1, "SELECT CAST(12 AS NUMERIC(10,2)), CAST(1E30 AS NUMERIC(40,0)), CAST(-7 AS DECIMAL(5,0))")
    p.ok("next 1", "1")
    check("int of 12.00", p.ok("get 1 1 int"), "12")
    p.error("get 1 2 int", "Type")
    check("int of -7", p.ok("get 1 3 int"), "-7")
    check("decimal 1E30", from_h16(p.ok("get 1 2 dec")), "1" + "0" * 30)

    # errors of binding
    p.error("bind 0 0 int 1", "Index")
    p.error("bind 0 14 int 1", "Index")
    p.error("bind 0 1 dec %s" % h16("12a"), "Data")
    p.error("bind 0 10 date 2026:2:30", "Data")
    p.error("bind 0 12 ts 2026:2:29:12:0:0:0", "Data")
    p.prepare(0, 1, "INSERT INTO t (id, v) VALUES (?, ?)")
    p.ok("bind 1 1 int 10")
    p.error("sexec 1", "Unbound")
    p.ok("bind 1 2 text %s" % h16("x"))
    check("insert after binding", p.ok("sexec 1"), "1")
    p.ok("close 0")
    p.close()


# ---------------------------------------------------------------- statements

def case_stmt(env):
    p = env.program()
    p.ok(p.connect(0, env.h2()))
    check("create", p.exec(0, "CREATE TABLE s (id INTEGER PRIMARY KEY, name VARCHAR(20), qty INTEGER)"), 0)
    check("insert", p.exec(0, "INSERT INTO s VALUES (1, 'one', 10), (2, 'two', 20), (3, 'three', 30)"), 3)
    check("update", p.exec(0, "UPDATE s SET qty = qty + 1 WHERE id >= 2"), 2)
    check("update none", p.exec(0, "UPDATE s SET qty = 0 WHERE id > 99"), 0)

    p.prepare(0, 0, "SELECT name, qty FROM s WHERE qty > ? ORDER BY id")
    for limit, names in ((0, ["one", "two", "three"]), (15, ["two", "three"]), (100, [])):
        p.ok("bind 0 1 int %d" % limit)
        p.ok("squery 0 0", "2")
        got = []
        while p.ok("next 0") == "1":
            got.append(from_h16(p.ok("get 0 1 text")))
        check("query with %d" % limit, got, names)
    check("find", p.ok("find 0 %s" % h16("QTY")), "2")
    check("find in another case", p.ok("find 0 %s" % h16("Name")), "1")
    p.error("find 0 %s" % h16("price"), "Index")

    # a new execution closes the rows of the last one
    p.ok("bind 0 1 int 0")
    p.ok("squery 0 1")
    p.ok("squery 0 2")
    p.error("next 1", "Closed")
    p.ok("next 2", "1")
    p.ok("rclose 2")

    # the values stay until they are set again
    p.prepare(0, 1, "INSERT INTO s VALUES (?, ?, ?)")
    p.ok("bind 1 1 int 4")
    p.ok("bind 1 2 text %s" % h16("four"))
    p.ok("bind 1 3 int 40")
    check("insert 4", p.ok("sexec 1"), "1")
    p.error("sexec 1", "Constraint")
    p.ok("bind 1 1 int 5")
    check("insert 5", p.ok("sexec 1"), "1")

    # batches
    for k in range(6, 9):
        p.ok("bind 1 1 int %d" % k)
        p.ok("add 1")
    check("batch", p.ok("batch 1"), "3 1 1 1")
    check("empty batch", p.ok("batch 1"), "0")
    p.ok("bind 1 1 int 9")
    p.ok("add 1")
    p.ok("bind 1 1 int 1")
    p.ok("add 1")
    p.error("batch 1", "Constraint")
    check("empty after a failure", p.ok("batch 1"), "0")
    p.query(0, 3, "SELECT id FROM s")
    check("rows after the batches", p.ok("count 3"), "9 45")
    p.prepare(0, 5, "UPDATE s SET qty = qty WHERE id <= ?")
    for k in (2, 0, 5):
        p.ok("bind 5 1 int %d" % k)
        p.ok("add 5")
    check("counts of a batch", p.ok("batch 5"), "3 2 0 5")

    # generated keys
    p.exec(0, "CREATE TABLE k (id BIGINT AUTO_INCREMENT PRIMARY KEY, v VARCHAR(10))")
    p.prepare(0, 2, "INSERT INTO k (v) VALUES (?)", keys=True)
    p.error("keys 2 4", "Other")  # not executed yet
    for n in (1, 2):
        p.ok("bind 2 1 text %s" % h16("v%d" % n))
        p.ok("sexec 2")
        check("key columns", p.ok("keys 2 4"), "1")
        p.ok("next 4", "1")
        check("key %d" % n, p.ok("get 4 1 int"), str(n))
        p.ok("next 4", "0")
    p.error("keys 1 4", "Other")  # not prepared for keys
    p.error("sexec 0", "Other")   # a query is no update
    p.ok("sclose 2")
    p.error("bind 2 1 text -", "Closed")
    p.error("sexec 2", "Closed")
    p.ok("close 0")
    p.close()


# ---------------------------------------------------------------- rows

def case_rows(env):
    p = env.program()
    url = env.h2()
    p.ok(p.connect(0, url))
    p.exec(0, "CREATE TABLE big (n INTEGER PRIMARY KEY)")
    check("insert", p.exec(0, "INSERT INTO big SELECT X FROM SYSTEM_RANGE(1, 10000)"), 10000)
    for c, fetch in ((1, 1), (2, 3), (3, 256)):
        p.ok(p.connect(c, url, fetch=fetch))
        for n in sorted({0, 1, fetch - 1, fetch, fetch + 1, 2 * fetch + 1, 10000}):
            if n < 0:
                continue
            p.query(c, 0, "SELECT n FROM big WHERE n <= %d ORDER BY n" % n)
            check("fetch %d, %d rows" % (fetch, n), p.ok("count 0"), "%d %d" % (n, n * (n + 1) // 2))
            p.ok("next 0", "0")  # stays at the end
            p.ok("rclose 0")
        # closed in the middle
        p.query(c, 1, "SELECT n FROM big ORDER BY n")
        for k in range(1, 2 * fetch + 2):
            p.ok("next 1", "1")
            check("row %d" % k, p.ok("get 1 1 int"), str(k))
        p.ok("rclose 1")
        p.error("next 1", "Closed")
        p.error("get 1 1 int", "Closed")
        p.query(c, 2, "SELECT COUNT(*) FROM big")
        p.ok("next 2", "1")
        check("the connection after a close", p.ok("get 2 1 int"), "10000")
        p.ok("close %d" % c)
    p.query(0, 3, "SELECT n FROM big WHERE n < 0")
    check("no columns lost", p.ok("cols 3").count(":"), 5)
    p.ok("next 3", "0")
    p.ok("close 0")
    p.error("next 3", "Closed")
    p.close()


# ---------------------------------------------------------------- transactions

def count(p, c, r):
    p.query(c, r, "SELECT COUNT(*) FROM x")
    p.ok("next %d" % r, "1")
    n = int(p.ok("get %d 1 int" % r))
    p.ok("rclose %d" % r)
    return n


def case_tx(env):
    p = env.program()
    url = env.h2()
    p.ok(p.connect(0, url))
    p.ok(p.connect(1, url))
    p.exec(0, "CREATE TABLE x (n INTEGER)")
    p.ok("autocommit 0 0")
    p.exec(0, "INSERT INTO x VALUES (1)")
    check("own uncommitted row", count(p, 0, 0), 1)
    check("uncommitted row elsewhere", count(p, 1, 1), 0)
    p.ok("commit 0")
    check("committed row", count(p, 1, 1), 1)
    p.exec(0, "INSERT INTO x VALUES (2)")
    p.ok("rollback 0")
    check("after rollback", count(p, 0, 0), 1)
    check("after rollback elsewhere", count(p, 1, 1), 1)
    p.exec(0, "INSERT INTO x VALUES (3)")
    p.ok("autocommit 0 1")  # commits
    check("auto-commit on commits", count(p, 1, 1), 2)
    p.exec(0, "INSERT INTO x VALUES (4)")
    check("auto-commit", count(p, 1, 1), 3)
    p.ok("close 0")
    p.ok("close 1")
    p.close()


# ---------------------------------------------------------------- errors

def case_errors(env):
    p = env.program()
    url = env.h2()
    p.ok(p.connect(0, url))
    p.exec(0, "CREATE TABLE e (id INTEGER PRIMARY KEY, n INTEGER NOT NULL, x INTEGER)")
    p.exec(0, "INSERT INTO e VALUES (1, 1, 1)")
    k, state, code, text = p.error("exec 0 %s" % h16("SELEC 1"), "Syntax", "42001")
    check("code of the syntax error", code, 42001)
    p.error("query 0 0 %s" % h16("SELECT * FROM nope"), "Syntax", "42S02")
    p.error("query 0 0 %s" % h16("SELECT nope FROM e"), "Syntax", "42S22")
    p.error("exec 0 %s" % h16("INSERT INTO e VALUES (1, 2, 2)"), "Constraint", "23505")
    p.error("exec 0 %s" % h16("INSERT INTO e VALUES (2, NULL, 2)"), "Constraint", "23502")
    p.error("exec 0 %s" % h16("INSERT INTO e VALUES (3, 'abc', 2)"), "Data", "22018")
    p.error("query 0 0 %s" % h16("SELECT 1/0"), "Data", "22012")
    p.error("exec 0 -", "Syntax")

    # the password
    k, state, code, text = p.error(p.connect(1, url, password="wrong password"), "Auth", "28000")
    if "wrong password" in text:
        raise Failed("the password is in the text: %r" % text)
    p.error(p.connect(1, url, user="nobody"), "Auth", "28000")

    # locks: a lock timeout is a Timeout
    p.ok(p.connect(1, url))
    p.exec(1, "SET LOCK_TIMEOUT 300")
    p.ok("autocommit 0 0")
    p.exec(0, "UPDATE e SET x = 5 WHERE id = 1")
    t = time.time()
    p.error("exec 1 %s" % h16("UPDATE e SET x = 6 WHERE id = 1"), "Timeout", "HYT00")
    if time.time() - t > 5:
        raise Failed("the lock timeout took %.1f s" % (time.time() - t))
    p.ok("rollback 0")
    p.ok("autocommit 0 1")
    check("after the lock", p.exec(1, "UPDATE e SET x = 6 WHERE id = 1"), 1)

    # no driver
    p.error(p.connect(2, "jdbc:none:x;password=secret"), "NoDriver")
    k, state, code, text = p.error(p.connect(2, "jdbc:none:x;password=secret"), "NoDriver")
    if "secret" in text:
        raise Failed("the URL is in the text: %r" % text)
    p.error(p.connect(2, "not a url"), "NoDriver")
    p.error(p.connect(2, url, driver="no.such.Driver"), "NoDriver")
    p.ok(p.connect(2, url, driver="org.h2.Driver"))
    p.ok("close 2")

    # closed
    p.prepare(1, 0, "SELECT id FROM e WHERE id > ?")
    p.ok("bind 0 1 int 0")
    p.ok("squery 0 0")
    p.ok("next 0", "1")
    p.ok("close 1")
    p.ok("close 1")  # again
    p.error("exec 1 %s" % h16("SELECT 1"), "Closed")
    p.error("next 0", "Closed")
    p.error("get 0 1 int", "Closed")
    p.error("bind 0 1 int 0", "Closed")
    p.error("sexec 0", "Closed")
    p.error("commit 1", "Closed")
    p.ok("sclose 0")
    p.ok("rclose 0")
    p.ok("close 0")
    p.close()


# ---------------------------------------------------------------- alerts and timeouts

LONG = "SELECT SUM(X) FROM SYSTEM_RANGE(1, 100000000000)"


def case_alert(env):
    p = env.program()
    url = env.h2()
    p.ok(p.connect(0, url, timeout=0))
    p.exec(0, "CREATE TABLE a (n INTEGER)")
    for _ in range(3):
        got = p.ok("alert 0 0.5 %s" % h16(LONG))
        if not got.startswith("alerted "):
            raise Failed("alert: %s" % got)
        if int(got.split(" ")[1]) > 3000:
            raise Failed("the alert took %s ms" % got.split(" ")[1])
        check("the connection after an alert", p.exec(0, "INSERT INTO a VALUES (1)"), 1)
    # a timeout
    p.ok(p.connect(1, url, timeout=1.0))
    t = time.time()
    p.error("query 1 0 %s" % h16(LONG), "Timeout")
    if time.time() - t > 4:
        raise Failed("the timeout took %.1f s" % (time.time() - t))
    check("the connection after a timeout", p.exec(1, "INSERT INTO a VALUES (2)"), 1)
    # a timeout while rows are fetched (H2 computes the rows of a query in
    # advance, unless it is lazy: the property also shows that properties
    # reach the driver)
    p.ok(p.connect(2, url, timeout=1.0, fetch=1, props=[("LAZY_QUERY_EXECUTION", "TRUE")]))
    p.query(2, 1, "SELECT X FROM SYSTEM_RANGE(1, 100000000000) WHERE X = 1 OR X > 99999999999")
    p.ok("next 1", "1")
    t = time.time()
    p.error("next 1", "Timeout")
    if time.time() - t > 4:
        raise Failed("the timeout of a fetch took %.1f s" % (time.time() - t))
    check("the connection after a timeout of a fetch", p.exec(2, "INSERT INTO a VALUES (3)"), 1)
    # closed by another thread during a query
    p.ok(p.connect(3, url, timeout=0))
    t = time.time()
    p.error("closeduring 3 0.5 %s" % h16(LONG), "Closed")
    if time.time() - t > 4:
        raise Failed("the close took %.1f s" % (time.time() - t))
    p.error("exec 3 %s" % h16("INSERT INTO a VALUES (4)"), "Closed")
    for c in (0, 1, 2):
        p.ok("close %d" % c)
    p.close()


# ---------------------------------------------------------------- threads

def case_threads(env):
    p = env.program()
    p.ok(p.connect(0, env.h2()))
    p.exec(0, "CREATE TABLE m (n INTEGER PRIMARY KEY)")
    p.ok("many 0 8 300 m")
    p.query(0, 0, "SELECT n FROM m")
    n = 8 * 300
    check("rows of the threads", p.ok("count 0"), "%d %d" % (n, n * (n - 1) // 2))
    p.ok("close 0")
    p.close()


# ---------------------------------------------------------------- SQLite

LITE_TABLE = ("CREATE TABLE t (id INTEGER PRIMARY KEY, b BOOLEAN, i BIGINT, r DOUBLE PRECISION, n NUMERIC(40,12), "
              "v VARCHAR(200), c CLOB, vb VARBINARY(300), bl BLOB, d DATE, tm TIME, ts TIMESTAMP, "
              "tz TIMESTAMP WITH TIME ZONE)")

FOREVER = "WITH RECURSIVE f(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM f) "


def case_lite(env):
    """What the other cases test, against SQLite: its dynamic types, its errors without SQLSTATE."""
    p = env.program()
    url = env.lite()
    p.ok(p.connect(0, url))
    p.exec(0, LITE_TABLE)
    p.prepare(0, 0, "INSERT INTO t VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
    rows = [
        [("int", "1"), ("bool", "1"), ("int", "9223372036854775807"), ("real", "0.1"), ("dec", h16("12.5")),
         ("text", h16("\u00e4\u00f6\u20ac\U0001F600 ok")), ("text", h16(BIG_TEXT)), ("bytes", hb(ALL_BYTES)),
         ("bytes", hb(BIG_BYTES)), ("date", "2026:11:10"), ("time", "18:32:33:123456789"),
         ("ts", "2026:11:10:18:32:33:123456789"), ("instant", "1234567890.25")],
        [("int", "2"), ("bool", "0"), ("int", "-9223372036854775808"), ("real", "-1e300"), ("dec", h16("-7")),
         ("text", h16("")), ("text", h16("")), ("bytes", "h"), ("bytes", "h"), ("date", "1:1:1"),
         ("time", "0:0:0:0"), ("ts", "1970:1:1:0:0:0:0"), ("instant", "-1.5")],
        [("int", "3"), ("null", str(T_BOOLEAN)), ("null", str(T_INTEGER)), ("null", str(T_REAL)), ("dec", "-"),
         ("text", "-"), ("null", str(T_TEXT)), ("bytes", "-"), ("null", str(T_BYTES)), ("null", str(T_DATE)),
         ("null", str(T_TIME)), ("null", str(T_TIMESTAMP)), ("null", str(T_ZONE))],
    ]
    for values in rows:
        for i, (kind, v) in enumerate(values):
            p.ok("bind 0 %d %s %s" % (i + 1, kind, v))
        check("insert", p.ok("sexec 0"), "1")

    check("columns", p.query(0, 0, "SELECT * FROM t ORDER BY id"), 13)
    p.ok("next 0", "1")
    # SQLite keeps booleans as integers, NUMERIC as a real number here, and
    # dates and times as texts in the form of ISO 8601
    check("row 1", p.row(0), ["1", "1", "9223372036854775807", "0.1", "12.5", "\u00e4\u00f6\u20ac\U0001F600 ok",
                              BIG_TEXT, ALL_BYTES, BIG_BYTES, "2026-11-10", "18:32:33.123456789",
                              "2026-11-10T18:32:33.123456789", "2009-02-13T23:31:30.250Z"])
    check("bool", p.ok("get 0 2 bool"), "1")
    check("int", p.ok("get 0 3 int"), "9223372036854775807")
    check("real", real(p.ok("get 0 4 real")), 0.1)
    check("decimal", from_h16(p.ok("get 0 5 dec")), "12.5")
    check("date", p.ok("get 0 10 date"), "2026:11:10")
    check("time", p.ok("get 0 11 time"), "18:32:33:123456789")
    check("timestamp", p.ok("get 0 12 ts"), "2026:11:10:18:32:33:123456789")
    check("instant", real(p.ok("get 0 13 instant")), 1234567890.25)
    check("bytes", from_hb(p.ok("get 0 8 bytes")), ALL_BYTES)
    p.error("get 0 6 int", "Type")
    p.error("get 0 3 bool", "Type")
    p.error("get 0 12 instant", "Type")
    p.ok("next 0", "1")
    check("bool 0", p.ok("get 0 2 bool"), "0")
    check("int", p.ok("get 0 3 int"), "-9223372036854775808")
    check("decimal -7", from_h16(p.ok("get 0 5 dec")), "-7")
    check("empty text", from_h16(p.ok("get 0 6 text")), "")
    check("empty bytes", from_hb(p.ok("get 0 8 bytes")), b"")
    check("time 0", p.ok("get 0 11 time"), "0:0:0:0")
    check("instant before 1970", real(p.ok("get 0 13 instant")), -1.5)
    p.ok("next 0", "1")
    check("row 3", p.row(0), ["3"] + [None] * 12)
    p.error("get 0 3 int", "Null")
    p.ok("next 0", "0")

    # statements, batches, keys
    p.exec(0, "CREATE TABLE k (id INTEGER PRIMARY KEY AUTOINCREMENT, v TEXT UNIQUE)")
    p.prepare(0, 1, "INSERT INTO k (v) VALUES (?)", keys=True)
    for n in (1, 2):
        p.ok("bind 1 1 text %s" % h16("v%d" % n))
        check("insert", p.ok("sexec 1"), "1")
        check("key columns", p.ok("keys 1 1"), "1")
        p.ok("next 1", "1")
        check("key %d" % n, p.ok("get 1 1 int"), str(n))
        p.ok("rclose 1")
    for n in (3, 4, 5):
        p.ok("bind 1 1 text %s" % h16("v%d" % n))
        p.ok("add 1")
    check("batch", p.ok("batch 1"), "3 1 1 1")
    k, state, code, text = p.error("sexec 1", "Constraint", "-")   # v5 again
    check("the result code of SQLite", code, 19)
    p.error("exec 0 %s" % h16("SELEC 1"), "Other", "-")
    p.error("query 0 2 %s" % h16("SELECT * FROM nope"), "Other", "-")

    # rows in blocks
    p.exec(0, "CREATE TABLE big (n INTEGER PRIMARY KEY)")
    upto = FOREVER.replace("FROM f)", "FROM f WHERE n < 3000)")
    check("insert", p.exec(0, "INSERT INTO big " + upto + "SELECT n FROM f"), 3000)
    for c, fetch in ((1, 1), (2, 7)):
        p.ok(p.connect(c, url, fetch=fetch))
        for n in (0, 1, fetch, fetch + 1, 3000):
            p.query(c, 3, "SELECT n FROM big WHERE n <= %d ORDER BY n" % n)
            check("fetch %d, %d rows" % (fetch, n), p.ok("count 3"), "%d %d" % (n, n * (n + 1) // 2))
        p.ok("close %d" % c)

    # a transaction seen from a second connection; a busy database is a Timeout
    p.ok(p.connect(1, url, props=[("busy_timeout", "300")]))
    p.exec(0, "CREATE TABLE x (n INTEGER)")
    p.ok("autocommit 0 0")
    p.exec(0, "INSERT INTO x VALUES (1)")
    p.query(1, 4, "SELECT COUNT(*) FROM x")
    p.ok("next 4", "1")
    check("uncommitted row elsewhere", p.ok("get 4 1 int"), "0")
    p.ok("rclose 4")
    t = time.time()
    k, state, code, text = p.error("exec 1 %s" % h16("INSERT INTO x VALUES (2)"), "Timeout", "-")
    check("the result code of a busy database", code, 5)
    if time.time() - t > 5:
        raise Failed("busy took %.1f s" % (time.time() - t))
    p.ok("commit 0")
    p.ok("autocommit 0 1")
    p.query(1, 4, "SELECT COUNT(*) FROM x")
    p.ok("next 4", "1")
    check("committed row", p.ok("get 4 1 int"), "1")
    p.ok("rclose 4")

    # alerts and timeouts: SQLite runs a query step by step
    for _ in range(2):
        got = p.ok("alert 0 0.5 %s" % h16(FOREVER + "SELECT COUNT(*) FROM f"))
        if not got.startswith("alerted ") or int(got.split(" ")[1]) > 3000:
            raise Failed("alert: %s" % got)
    # rows left open keep the cancel of SQLite (sqlite3_interrupt) in effect
    # until they are closed
    p.query(0, 7, "SELECT n FROM big")
    p.ok("next 7", "1")
    got = p.ok("alert 0 0.5 %s" % h16(FOREVER + "SELECT COUNT(*) FROM f"))
    if not got.startswith("alerted "):
        raise Failed("alert with open rows: %s" % got)
    k, state, code, text = p.error("exec 0 %s" % h16("INSERT INTO x VALUES (9)"), "Other", "-")
    check("SQLITE_INTERRUPT", code, 9)
    p.ok("rclose 7")
    check("after the rows are closed", p.exec(0, "INSERT INTO x VALUES (9)"), 1)
    p.ok(p.connect(2, url, timeout=1.0, fetch=1))
    p.query(2, 5, FOREVER + "SELECT n FROM f WHERE n = 1 OR n < 0")
    p.ok("next 5", "1")
    t = time.time()
    p.error("next 5", "Timeout")
    if time.time() - t > 4:
        raise Failed("the timeout took %.1f s" % (time.time() - t))
    check("the connection after a timeout", p.exec(2, "INSERT INTO x VALUES (3)"), 1)

    # threads
    p.exec(0, "CREATE TABLE m (n INTEGER PRIMARY KEY)")
    p.ok("many 0 4 200 m")
    p.query(0, 6, "SELECT n FROM m")
    check("rows of the threads", p.ok("count 6"), "800 %d" % (800 * 799 // 2))
    for c in (0, 1, 2):
        p.ok("close %d" % c)
    p.close()


def case_cross(env):
    """SQLite through m3j against the sqlite3 of Python on the same file, both ways."""
    import sqlite3
    path = env.work / "cross.db"
    db = sqlite3.connect(str(path))
    db.execute("CREATE TABLE c (id INTEGER PRIMARY KEY, i INTEGER, r REAL, t TEXT, b BLOB, d DATE, ts TIMESTAMP, "
               "flag BOOLEAN, n NUMERIC)")
    py_rows = [
        (1, 2 ** 63 - 1, 0.1, "\u00e4\u00f6\u20ac\U0001F600", ALL_BYTES, "2026-11-10", "2026-11-10 18:32:33", 1, 12.5),
        (2, -2 ** 63, -1e-300, "", b"", "1999-12-31", "1999-12-31 23:59:59.999", 0, 3),
        (3, None, None, None, None, None, None, None, None),
        (4, 0, 1.7976931348623157e308, "a\nb\tc", bytes(70000), "0001-01-01", "2000-02-29T00:00:00", 1, -0.5),
    ]
    db.executemany("INSERT INTO c VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", py_rows)
    db.commit()
    db.close()

    p = env.program()
    p.ok(p.connect(0, "jdbc:sqlite:" + str(path).replace("\\", "/")))
    p.query(0, 0, "SELECT * FROM c ORDER BY id")
    for row in py_rows:
        p.ok("next 0", "1")
        what = "row %d" % row[0]
        if row[1] is None:
            for i in range(2, 10):
                check(what + " null %d" % i, p.ok("get 0 %d null" % i), "1")
            p.error("get 0 2 int", "Null")
            check(what + " text", p.ok("get 0 4 text"), "-")
            continue
        check(what + " int", p.ok("get 0 2 int"), str(row[1]))
        check(what + " real", real(p.ok("get 0 3 real")), row[2])
        check(what + " text", from_h16(p.ok("get 0 4 text")), row[3])
        check(what + " bytes", from_hb(p.ok("get 0 5 bytes")), row[4])
        y, m, d = row[5].split("-")
        check(what + " date", p.ok("get 0 6 date"), "%d:%d:%d" % (int(y), int(m), int(d)))
        ts = row[6].replace("T", " ")
        date, clock = ts.split(" ")
        y, m, d = date.split("-")
        hh, mm, ss = clock.split(":")
        nanos = round(float("0." + ss.split(".")[1]) * 1e9) if "." in ss else 0
        check(what + " timestamp", p.ok("get 0 7 ts"),
              "%d:%d:%d:%d:%d:%d:%d" % (int(y), int(m), int(d), int(hh), int(mm), int(ss.split(".")[0]), nanos))
        check(what + " bool", p.ok("get 0 8 bool"), str(row[7]))
        check(what + " decimal", real(from_h16(p.ok("get 0 9 dec"))), float(row[8]))
    p.ok("next 0", "0")

    # the other way
    p.exec(0, "CREATE TABLE w (id INTEGER PRIMARY KEY, i INTEGER, r REAL, t TEXT, b BLOB, d DATE, flag BOOLEAN, "
              "n NUMERIC)")
    p.prepare(0, 0, "INSERT INTO w VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
    m3_rows = [
        [("int", "1"), ("int", "-9223372036854775808"), ("real", "2.5e-310"), ("text", h16("\U0001F600\u0000x")),
         ("bytes", hb(ALL_BYTES)), ("date", "2026:11:10"), ("bool", "1"), ("dec", h16("12.25"))],
        [("int", "2"), ("null", str(T_INTEGER)), ("null", str(T_REAL)), ("text", "-"), ("bytes", "-"),
         ("null", str(T_DATE)), ("null", str(T_BOOLEAN)), ("dec", "-")],
    ]
    for values in m3_rows:
        for i, (kind, v) in enumerate(values):
            p.ok("bind 0 %d %s %s" % (i + 1, kind, v))
        p.ok("sexec 0", "1")
    p.ok("close 0")
    p.close()
    db = sqlite3.connect(str(path))
    got = db.execute("SELECT * FROM w ORDER BY id").fetchall()
    db.close()
    check("written by m3j", got, [(1, -2 ** 63, 2.5e-310, "\U0001F600\u0000x", ALL_BYTES, "2026-11-10", 1, 12.25),
                                  (2, None, None, None, None, None, None, None)])


def case_inmemory(env):
    """The program compiled and run in memory (-run), its drivers given with -cp of the compiler."""
    p = env.program(in_memory=True)
    for c, url in ((0, env.h2()), (1, env.lite())):
        p.ok(p.connect(c, url))
        p.exec(c, "CREATE TABLE r (n INTEGER)")
        check("insert", p.exec(c, "INSERT INTO r VALUES (1), (2), (3)"), 3)
        p.query(c, c, "SELECT n FROM r")
        check("rows", p.ok("count %d" % c), "3 6")
        p.ok("close %d" % c)
    p.error(p.connect(2, "jdbc:none:x"), "NoDriver")
    p.close()


CASES = {"types": case_types, "stmt": case_stmt, "rows": case_rows, "tx": case_tx, "errors": case_errors,
         "alert": case_alert, "threads": case_threads, "lite": case_lite, "cross": case_cross,
         "inmemory": case_inmemory}


def main():
    jdk = os.environ.get("JDK25")
    ap = argparse.ArgumentParser()
    ap.add_argument("cases", nargs="*")
    ap.add_argument("--java", default=str(Path(jdk) / "bin" / "java") if jdk else "java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
    ap.add_argument("--runtime", default=str(ROOT / "m3j-runtime.jar"))
    ap.add_argument("--h2", default=str(H2_JAR))
    ap.add_argument("--sqlite", default=str(SQLITE_JAR))
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--timeout", type=int, default=300)
    args = ap.parse_args()
    if sys.platform == "win32" and not args.java.endswith(".exe") and Path(args.java + ".exe").exists():
        args.java += ".exe"
    for name in args.cases:
        if name not in CASES:
            print("FAIL      setup  no case %s" % name)
            return 1
    for jar, sha, name, option in ((args.h2, H2_SHA256, "H2 2.4.240", "--h2"),
                                   (args.sqlite, SQLITE_SHA256, "sqlite-jdbc 3.53.4.0", "--sqlite")):
        if not Path(jar).is_file():
            print("FAIL      setup  no jar of %s at %s (%s)" % (name, jar, option))
            return 1
        if sha256(jar) != sha:
            print("FAIL      setup  %s is not the jar of %s" % (jar, name))
            return 1
    names = args.cases or list(CASES)
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="sql_"))
    work.mkdir(parents=True, exist_ok=True)
    failed = 0
    t0 = time.time()
    env = Env(args, work)
    try:
        for name in names:
            t = time.time()
            try:
                CASES[name](env)
                print("PASS      %-8s %.0fs" % (name, time.time() - t), flush=True)
            except (Failed, subprocess.TimeoutExpired, OSError) as e:
                failed += 1
                print("FAIL      %s  %s" % (name, e), flush=True)
    finally:
        if args.keep or args.work:
            print("work: %s" % work)
        else:
            shutil.rmtree(work, ignore_errors=True)
    print("failed %d, %.0fs" % (failed, time.time() - t0))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
