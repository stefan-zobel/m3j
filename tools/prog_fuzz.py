#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Random program tests of the back end: statements, procedures and data.

Usage:
  python3 tools/prog_fuzz.py [--seeds A-B] [--focus LIST] [--avoid LIST] [--no-cm3]
                             [--split N] [--jobs N] [--work DIR] [--keep]
                             [--java JAVA] [--jar JAR] [--timeout S] [-v]
  python3 tools/prog_fuzz.py --show SEED [--focus LIST] [--avoid LIST]
  python3 tools/prog_fuzz.py --reduce SEED [--match TEXT] [--focus LIST] [--avoid LIST]
                             [--no-cm3] [--split N] [--work DIR] [-v]
  python3 tools/prog_fuzz.py --write DIR [--list FILE] [--seeds A-B] [--focus LIST]

For each seed, a Modula-3 program is generated that prints a trace of
what it does. Where expr_fuzz.py tests expressions, this tool tests how
the back end lowers statements, procedures and data:

  - control flow: EXIT and RETURN out of TRY FINALLY, LOCK and handlers,
    RAISE in handlers and FINALLY clauses, exception arguments, nested
    LOOP, WHILE, REPEAT and FOR (FOR at the limits of INTEGER and CHAR,
    with empty ranges, negative steps and enumerations), CASE with ranges
    and ELSE;
  - nested procedures that read and write the variables and formals of
    the procedures around them, recursive nested procedures and nested
    procedures passed as procedure parameters;
  - VAR and READONLY formals bound to overlapping places: the same
    variable twice, array elements whose indexes may be equal, a record
    and its field, WITH names, elements of open arrays, and globals that
    the procedure changes directly as well;
  - copies: assignment of records and arrays, value formals changed in
    the body, records and arrays as results, WITH bound to a value and
    to a variable, SUBARRAY assignments whose sides overlap, fields of
    packed records in the same word;
  - open arrays: NEW, SUBARRAY, NUMBER, LAST, passed by value, VAR and
    READONLY;
  - objects: a hierarchy of object types with fields that have default
    values, methods whose procedures are generated like the others, and
    overrides; calls of methods (on variables, elements of arrays, fields
    and results of NEW), of the method a procedure overrides (T.m) and of
    methods as procedures; NEW with fields and with methods; objects
    passed by value, VAR and READONLY, as results and as WITH names;
  - references: TYPECASE (with NULL, several types in a case and ELSE),
    ISTYPE, NARROW, = on references, a list (REF RECORD) that is extended,
    shortened and walked, REF INTEGER, REF Rec and REFANY;
  - sets: small sets (of an enumeration and of a subrange that starts
    below 0) and large ones (of more than 64 elements, and of CHAR), a
    set in a packed record, in an array and in an object; constructors
    with elements and ranges, +, -, *, /, IN, comparisons and loops over
    the elements, sets as formals and results;
  - texts and characters: &, Text.Sub, Cat, Length, GetChar, FindChar,
    Compare, Equal, Empty, FromChar, FromChars and SetChars, Fmt.Int,
    loops over the characters of a text, CASE on characters, texts as
    formals, results and in a TYPECASE on a REFANY;
  - several modules (half of the programs, all with --focus mod): an
    interface G with the types, exceptions and globals, which several
    modules export (G and those with method procedures), and modules
    M1 .. Mk with interfaces, between which the procedures are spread, so
    that calls, procedures as values, exceptions, methods and VAR formals
    cross modules; variables of interfaces with initial values, constants,
    variables of a module's own that its body sets, procedures that no
    interface declares, a module that exports an interface of another
    name, and imports in cycles; an opaque object type that a module
    reveals, a subtype of it in another interface that overrides a method
    and calls the one it overrides, and one more in Main.

Every program terminates: loops count with their own counters, a
recursive procedure gets a depth that decreases, and every procedure
spends one unit of a global fuel, so that calls through procedure
parameters end as well. Values stay small (every assignment is reduced
MOD 997, or into the range of its subrange), and indexes are reduced
MOD the length of the array, so that no program overflows or fails a
check; texts are cut to 16 characters when they are assigned. A function
call is a statement of its own, so that the order of
evaluation, which the language leaves open, does not matter; the result
of a call is not assigned to a place that a reference denotes, which the
call may change. A variable of an object type and REF INTEGER and REF
Rec are never NIL; the list, the REFANY and a field of an object may be,
and are tested before they are used. A TYPECASE that no case may match
has ELSE. The calls of the main body run three times, each time from the
state the last one left and with other values of some globals, so that
other branches are taken. A FOR loop
near the ends of INTEGER stops before its last increment would overflow
(the language defines FOR by INC, and cm3 loops forever there).

Errors of CM3's front end, which m3j shares, are avoided: a WITH name is
not bound to a field of a packed record (an internal error of the code
generator, "non-byte-aligned offset"), and a function does not return an
array constructor (inside TRY EXCEPT ELSE, "stack underflow"). So are
errors of cm3's C back end: it fails (M3C.last_param) on a FINALLY clause
that has a RETURN or EXIT and a TRY FINALLY or LOCK, so such a clause has
no RETURN or EXIT out of it; and the program it compiles crashes when
EXIT or RETURN leaves the body of a TRY EXCEPT that has named handlers
and ELSE (the language defines EXIT and RETURN as exceptions, which the
ELSE catches), so such a body has no EXIT or RETURN out of it. Of sets,
cm3's C back end gets ranges in constructors of small sets wrong unless
they are constant, swaps the operands of <, <=, > and >= on large sets,
and swaps = and # on large sets when they decide a branch (IF a = b
THEN), though not when their value is used (ORD (a = b)); so a range of
a small set is constant, and a comparison of large sets is = or # used
as a value. The C back end also takes the wrong arm of a CASE whose
selector has the absolute value of an expression that the front end
knows to be negative (CASE ABS (ORD (b) - 7) OF 5 => ... takes the arm
5, one less than the value 6), so there it is ABS (e) DIV 1. cm3's Text.SetChars copies the characters after the end of a
text made by Text.Sub into the rest of the array, so the text it gets is
as long as the array. CM3's front end, and so m3j's, refuses to pass an
operation on constant small sets, which it folds, to a READONLY formal
("non-byte-aligned value"), and fails (a subscript out of range in
SetExpr) when it passes such an operation whose elements may be
negative to a value formal; so such an actual is a constructor or a
variable; neither passes a field of a packed record by reference.

Each program is compiled by m3j twice, normally and with the system
property m3j.maxMethod set to a small limit N (default a random value
from 150 to 600 per seed), so that m3j splits even small procedures into
chunk methods; both are run, and the program is built and run with cm3
(on the PATH, with its C compiler). The status of a seed is PASS, FAIL
(the output of m3j differs from cm3's), SPLIT-FAIL (the output of the
split version differs from the normal one), VERIFY (a class does not
verify), M3J-FAILED, CM3-FAILED, BOTH-FAILED or TIMEOUT; for a difference,
the first differing line and the line of the program that printed it
are shown. With --no-cm3 only the two versions of m3j are compared.

--focus weights the constructs of one or more groups (flow, uplevel,
var, copy, open, obj, set, text, mod, comma-separated); --avoid leaves out constructs (see
FEATURES). --show prints the program of a seed; --write DIR writes the
programs as packages DIR/pSEED for tools/diff_test.py, and --list FILE
the list of those tests, named fuzz/pSEED. SEEDS is a number or a range
A-B (default 1-10). JAVA defaults to $JDK25/bin/java, else java from the
PATH.

--reduce SEED makes the program of a failed seed smaller while it keeps
its status (and, with --match, while a line of the details contains
TEXT): it removes procedures, statements, arms of CASE and handlers,
and replaces compound statements by their parts. For CM3-FAILED it runs
only cm3, for M3J-FAILED only m3j, for SPLIT-FAIL only the two versions
of m3j. It prints the result and writes it to DIR/pSEED-reduced.m3 for
--work DIR (a program of several modules to the package
DIR/pSEED-reduced).
"""

import argparse
import concurrent.futures
import copy
import os
import random
import re
import shutil
import sys
import tempfile
from pathlib import Path
import subprocess

sys.path.insert(0, str(Path(__file__).resolve().parent))
from expr_fuzz import ROOT, parse_seeds, run_cm3, run_m3j  # noqa: E402

FOCUS = ("flow", "uplevel", "var", "copy", "open", "obj", "set", "text", "mod")

# Constructs that --avoid can leave out.
FEATURES = {
    "finally-jump": "EXIT and RETURN in a FINALLY clause",
    "for-limits": "FOR loops at the limits of INTEGER and CHAR",
    "readonly-ref": "READONLY formals bound to variables",
    "proc-param": "procedures passed as parameters",
    "new-methods": "NEW of an object type that overrides methods",
    "modules": "programs of several modules",
}

# --------------------------------------------------------------- types

# The integer types and their ranges (None: INTEGER, values kept MOD 997).
RANGE = {"INTEGER": None, "Sub": (-5, 20), "PX": (0, 31), "PY": (-4, 3), "PZ": (0, 500),
         "BI": (0, 99)}
INTS = tuple(RANGE)
ORDS = ("Color", "PC", "Ch")
# types of FOR variables: INTEGER, Color, CHAR
LOOPV = ("FI", "FE", "FCh")
READ = INTS + ORDS + LOOPV
ARRAYS = ("Open", "Arr5", "Arr3")
COLORS = ["Color.Red", "Color.Green", "Color.Blue", "Color.Gray"]

TEXT = {"INTEGER": "INTEGER", "Sub": "Sub", "Color": "Color", "Rec": "Rec", "Arr5": "Arr5",
        "Arr3": "Arr3", "Open": "ARRAY OF INTEGER", "Fn": "Fn", "O": "O", "O1": "O1", "O2": "O2",
        "O3": "O3", "L": "L", "RI": "REF INTEGER", "RR": "REF Rec", "Any": "REFANY",
        "Ch": "CHAR", "TX": "TEXT", "CS": "CS", "SS": "SS", "BS": "BS", "CHS": "CHS", "Chars": "Chars"}
DEFAULT = {"INTEGER": "0", "Sub": "0", "Rec": "gr", "Arr5": "ga", "O": "gob", "TX": '""', "SS": "SS{}",
           "BS": "BS{}"}

# The set types and the types of their elements: CS and SS are small (SS
# starts at -5), BS and CHS large (more than 64 elements). cm3's C back end
# gets ranges in constructors of small sets wrong unless they are constant,
# swaps the operands of <, <=, > and >= on large sets, and swaps = and # on
# large sets that decide a branch (IF a = b), though not on their values.
SETS = {"CS": "Color", "SS": "Sub", "BS": "BI", "CHS": "Ch"}
BIG = ("BS", "CHS")
# an element of each set type, for the trace
PROBE = {"CS": "Color.Blue", "SS": "3", "BS": "7", "CHS": "'e'"}
TLITS = ['""', '"a"', '"ab"', '"xyz"', '"M3"', '"Hello"', '"a-b"', '"07"']

# The object types: O1 and O3 are subtypes of O, O2 of O1. A variable of
# an object type is never NIL (only the field nx of O2 may be).
OBJ = ("O", "O1", "O2", "O3")
PARENT = {"O1": "O", "O2": "O1", "O3": "O"}
SUBS = {"O": OBJ, "O1": ("O1", "O2"), "O2": ("O2",), "O3": ("O3",)}
FIELDS = {"O": [("a", "INTEGER"), ("c", "Color")]}
FIELDS["O1"] = FIELDS["O"] + [("b", "Sub"), ("r", "Rec")]
FIELDS["O2"] = FIELDS["O1"] + [("arr", "Arr3"), ("nx", "ONIL")]
FIELDS["O3"] = FIELDS["O"] + [("d", "INTEGER"), ("s", "CS")]
# the formals (name, type, mode) and result of the methods
MSIG = {"m": ([("x", "INTEGER", "")], "INTEGER"),
        "n": ([("v", "INTEGER", "VAR"), ("r", "Rec", "READONLY")], None),
        "k": ([("y", "INTEGER", "")], "O")}
# the procedure of each method of each type
BIND = {"O": {"m": "Om", "n": "On"}, "O1": {"m": "O1m", "n": "On", "k": "O1k"},
        "O2": {"m": "O1m", "n": "O2n", "k": "O2k"}, "O3": {"m": "O3m", "n": "O3n"}}
# the method procedures (name, method, type of self) in the order of their
# numbers: a call of a method may reach every procedure of the method, and
# a procedure calls the one it overrides (O1.k, O.m, O.n), so that calls
# go to higher numbers
IMPLS = [("O2k", "k", "O2"), ("O1k", "k", "O1"), ("O1m", "m", "O1"), ("O3m", "m", "O3"),
         ("Om", "m", "O"), ("O2n", "n", "O2"), ("O3n", "n", "O3"), ("On", "n", "O")]
# reference types other than objects: the list L (may be NIL), a node of
# it that is not NIL (LN), REF INTEGER, REF Rec (never NIL) and REFANY
REFS = ("L", "LN", "RI", "RR", "Any")
# the types of a TYPECASE on a REFANY, with the types of their names
ANYCASE = {"O": "O", "O1": "O1", "O2": "O2", "O3": "O3", "L": "LN", "REF INTEGER": "RI",
           "REF Rec": "RR", "TEXT": "TX"}


def isub(a, b):
    """True if the object type a is a subtype of b."""
    while a and a != b:
        a = PARENT.get(a)
    return a == b

# The designators of a variable of a type: (path, type of the designator);
# [] is an index, [r] a row and [r,c] an element of the two-dimensional array.
PATHS = {
    "INTEGER": [("", "INTEGER")], "Sub": [("", "Sub")], "Color": [("", "Color")],
    "PX": [("", "PX")], "PY": [("", "PY")], "PZ": [("", "PZ")], "PC": [("", "PC")],
    "FI": [("", "FI")], "FE": [("", "FE")], "FCh": [("", "FCh")],
    "Rec": [("", "Rec"), (".a", "INTEGER"), (".b", "Sub"), (".c", "Color"), (".arr", "Arr3"),
            (".arr[]", "INTEGER")],
    "Arr5": [("", "Arr5"), ("[]", "INTEGER")],
    "Arr3": [("", "Arr3"), ("[]", "INTEGER")],
    "Open": [("", "Open"), ("[]", "INTEGER")],
    "Ref": [("^", "Open"), ("[]", "INTEGER")],
    "Ref2": [("[r]", "Open"), ("[r,c]", "INTEGER")],
    "Pk": [(".x", "PX"), (".y", "PY"), (".c", "PC"), (".z", "PZ"), (".s", "CS")],
    "Fn": [("", "Fn")],
    "O": [("", "O"), (".a", "INTEGER"), (".c", "Color")],
    "O3": [("", "O3"), (".a", "INTEGER"), (".c", "Color"), (".d", "INTEGER"), (".s", "CS")],
    "AO": [("[]", "O"), ("[].a", "INTEGER"), ("[].c", "Color")],
    "L": [("", "L")],
    "LN": [("", "LN"), (".v", "INTEGER")],
    "RI": [("", "RI"), ("^", "INTEGER")],
    "RR": [("", "RR"), ("^", "Rec"), (".a", "INTEGER"), ("^.b", "Sub"), (".c", "Color"),
           (".arr[]", "INTEGER")],
    "Any": [("", "Any")],
    "Ch": [("", "Ch")], "TX": [("", "TX")], "CS": [("", "CS")], "SS": [("", "SS")],
    "BS": [("", "BS")], "CHS": [("", "CHS")], "Chars": [("", "Chars"), ("[]", "Ch")],
    "ASS": [("[]", "SS")],
    # the opaque type of the modules
    "QT": [(".a", "INTEGER")], "AQT": [("[].a", "INTEGER")],
}
PATHS["O1"] = [("", "O1"), (".a", "INTEGER"), (".c", "Color"), (".b", "Sub"), (".r", "Rec"),
               (".r.a", "INTEGER"), (".r.arr[]", "INTEGER")]
PATHS["O2"] = [("", "O2")] + PATHS["O1"][1:] + [(".arr", "Arr3"), (".arr[]", "INTEGER")]
REFTYPES = set(OBJ) | set(REFS) | {"Ref", "Ref2", "QT", "AQT"}


def via_ref(vt, path):
    """True if the designator path of a variable of type vt goes through a
    reference, so that it is writable even if the variable is not, and
    denotes another place when the variable changes."""
    return path != "" and (vt in REFTYPES or vt == "AO" and "." in path)


def norm(t, e):
    """The expression e of type INTEGER reduced to a value of type t."""
    if t == "Ch":
        return f"VAL(ORD('a') + ({e}) MOD 26, CHAR)"
    if t in ORDS:
        return f"VAL(({e}) MOD 4, Color)"
    r = RANGE[t]
    if r is None:
        return f"({e}) MOD 997"
    lo, hi = r
    v = f"({e}) MOD {hi - lo + 1}"
    return v if lo == 0 else f"{v} - {-lo}" if lo < 0 else f"{v} + {lo}"


def ival(text, t):
    """The value of a designator of type t as an INTEGER."""
    if t in INTS:
        return text
    if t == "FI":
        return f"({text} MOD 997)"
    return f"ORD({text})"


def lit(v):
    return f"({v})" if v < 0 else str(v)


class Var:
    """A variable, formal, WITH name, FOR variable or handler argument;
    owner is its procedure, None for a global and "stmt" for a name of a
    statement."""

    def __init__(self, name, type, owner=None, writable=True, mode="", init=None):
        self.name, self.type, self.owner, self.writable, self.mode, self.init = \
            name, type, owner, writable, mode, init


# ---------------------------------------------------------------- tree

class Leaf:
    """A statement of one line."""

    def __init__(self, text):
        self.text = text

    def render(self, ind, out):
        out.append("  " * ind + self.text + ";")


class Block:
    """A compound statement: lines before it, its parts (a header line, fixed
    lines and a list of statements or None) and its last line."""

    def __init__(self, parts, end="END", pre=()):
        self.parts, self.end, self.pre = parts, end, list(pre)

    def render(self, ind, out):
        sp = "  " * ind
        for p in self.pre:
            out.append(sp + p + ";")
        for head, fixed, body in self.parts:
            out.append(sp + head)
            for f in fixed:
                out.append(sp + "  " + f + ";")
            if body:
                render_list(body, ind + 1, out)
        out.append(sp + self.end + ";")


def has(stmts, pred):
    """True if pred holds for a statement of the list or inside one."""
    for st in stmts:
        if pred(st):
            return True
        if isinstance(st, Block) and any(body and has(body, pred) for _, _, body in st.parts):
            return True
    return False


def render_list(stmts, ind, out):
    for s in stmts:
        s.render(ind, out)


class Proc:
    def __init__(self, pid, parent):
        self.id, self.parent = pid, parent
        self.name = f"P{pid}"
        self.children = []
        self.formals = []
        self.locals = []
        self.counters = []
        self.result = None
        self.recursive = False
        self.depth = None
        self.fn = False
        self.body = []
        self.ret = None
        self.wrap = False
        self.removed = False
        self.bare = False
        # (method, type) for a method procedure
        self.impl = None
        # the variables of list walks
        self.walkers = []
        # the module of a procedure that is not nested (see Layout)
        self.module = "Main"
        # not declared in the interface of its module
        self.private = False

    def chain(self):
        """The procedures around this one, from the outermost, and itself."""
        c = []
        p = self
        while p:
            c.append(p)
            p = p.parent
        return c[::-1]

    def heading(self):
        fs = "; ".join(f"{f.mode + ' ' if f.mode else ''}{f.name}: {TEXT[f.type]}" for f in self.formals)
        res = f": {TEXT[self.result]}" if self.result else ""
        return f"PROCEDURE {self.name} ({fs}){res} RAISES {{E, F}} ="

    def decl(self):
        """The declaration of the procedure in an interface."""
        return self.heading()[:-2] + ";"

    def prologue(self):
        ret = f"RETURN {DEFAULT[self.result]}" if self.result else "RETURN"
        lines = ["LOCK m DO END", "DEC(fuel)", f"IF fuel < 0 THEN {ret} END"]
        if self.bare:
            return lines
        lines.append(f'Snap("{self.name}<")')
        for f in self.formals:
            tag = f'"{self.name}.{f.name}"'
            if f.type in READ:
                lines.append(f"T({tag}, {ival(f.name, f.type)})")
            elif f.type == "Rec":
                lines.append(f"T({tag}, {f.name}.a)")
            elif f.type in ("Arr5", "Open"):
                lines.append(f"T({tag}, {f.name}[LAST({f.name})])")
            elif f.type in OBJ:
                lines.append(f"T({tag}, {f.name}.a)")
            elif f.type == "TX":
                lines.append(f"PT({tag}, {f.name})")
            elif f.type in SETS:
                lines.append(f"T({tag}, ORD({PROBE[f.type]} IN {f.name}))")
        return lines

    def render(self, ind, out):
        sp = "  " * ind
        out.append(sp + self.heading())
        for v in self.locals:
            out.append(f"{sp}  VAR {v.name}: {TEXT[v.type]} := {v.init};")
        for c in self.counters:
            out.append(f"{sp}  VAR {c}: INTEGER := 0;")
        for q in self.walkers:
            out.append(f"{sp}  VAR {q}: L := NIL;")
        for c in self.children:
            if not c.removed:
                c.render(ind + 1, out)
        out.append(sp + "  BEGIN")
        b = "  " * (ind + 2)
        for line in self.prologue():
            out.append(b + line + ";")
        wrap = self.wrap and not self.bare
        inner = ind + 3 if wrap else ind + 2
        if wrap:
            out.append(b + "TRY")
        render_list(self.body, inner, out)
        if self.ret:
            out.append("  " * inner + self.ret + ";")
        if wrap:
            out.append(b + "FINALLY")
            out.append(b + f'  Snap("{self.name}>");')
            out.append(b + "END;")
        out.append(f"{sp}  END {self.name};")
        out.append("")


class Program:
    def __init__(self, seed, procs, init, main, round, layout=None):
        self.seed, self.procs, self.init, self.main, self.round = seed, procs, init, main, round
        self.layout = layout

    def text(self):
        return joined(self.files())

    def files(self):
        """The source files: a list of (name, text)."""
        if self.layout is None:
            L = [f"(* Generated by tools/prog_fuzz.py, seed {self.seed}. *)", "", "MODULE Main;", "",
                 "IMPORT IO, Fmt, Text;", ""] + HEADER.splitlines()
            self.render_procs("Main", L)
            self.body(L)
            return [("Main.m3", "\n".join(L) + "\n")]
        return self.layout.files(self)

    def render_procs(self, module, L):
        for p in self.procs:
            if p.parent is None and not p.removed and p.module == module:
                p.render(0, L)

    def body(self, L):
        L.append("BEGIN")
        L += ["  " + s + ";" for s in self.init]
        L.append('  Snap("init");')
        # the calls run several times, each time from the state the last
        # one left, so that other branches are taken
        L.append(f"  FOR round := 1 TO {ROUNDS} DO")
        L.append(f"    fuel := {FUEL};")
        L.append('    T("round", round);')
        L += ["    " + x + ";" for x in self.round]
        render_list(self.main, 2, L)
        L.append("  END;")
        L.append('  Snap("end");')
        L.append("END Main.")


def joined(files):
    """The text of a program of the source files (name, text)."""
    if len(files) == 1:
        return files[0][1]
    return "".join(f"(* ---------------- {n} *)\n{t}" for n, t in files)


HEADER = """\
TYPE
  Sub = [-5 .. 20];
  Color = {Red, Green, Blue, Gray};
  Arr3 = ARRAY [0 .. 2] OF INTEGER;
  Arr5 = ARRAY [0 .. 4] OF INTEGER;
  Rec = RECORD a: INTEGER; b: Sub; c: Color; arr: Arr3 END;
  CS = SET OF Color;
  SS = SET OF Sub;
  BS = SET OF [0 .. 99];
  CHS = SET OF CHAR;
  Chars = ARRAY [0 .. 5] OF CHAR;
  Pk = RECORD
    x: BITS 5 FOR [0 .. 31];
    y: BITS 3 FOR [-4 .. 3];
    c: BITS 2 FOR Color;
    z: BITS 9 FOR [0 .. 500];
    s: BITS 4 FOR CS
  END;
  Fn = PROCEDURE (x: INTEGER): INTEGER RAISES {E, F};
  O = OBJECT
    a: INTEGER := 1;
    c: Color := Color.Green;
  METHODS
    m (x: INTEGER): INTEGER RAISES {E, F} := Om;
    n (VAR v: INTEGER; READONLY r: Rec) RAISES {E, F} := On;
  END;
  O1 = O OBJECT
    b: Sub := 2;
    r: Rec := Rec{3, 4, Color.Blue, Arr3{5, 6, 7}};
  METHODS
    k (y: INTEGER): O RAISES {E, F} := O1k;
  OVERRIDES
    m := O1m;
  END;
  O2 = O1 OBJECT
    arr: Arr3 := Arr3{8, 9, 10};
    nx: O := NIL;
  OVERRIDES
    n := O2n;
    k := O2k;
  END;
  O3 = O OBJECT
    d: INTEGER := 11;
    s: CS := CS{Color.Red, Color.Blue};
  OVERRIDES
    m := O3m;
    n := O3n;
  END;
  L = REF RECORD v: INTEGER; next: L END;

EXCEPTION E; F (INTEGER);

VAR
  gi0, gi1, gi2, gi3: INTEGER;
  gs: Sub;
  gc: Color;
  ga: Arr5;
  gr, gr2: Rec;
  gp: Pk;
  go: REF ARRAY OF INTEGER;
  go2: REF ARRAY OF ARRAY OF INTEGER;
  m: MUTEX;
  fuel: INTEGER;
  gob: O;
  gob1: O1;
  gob2: O2;
  gob3: O3;
  goa: ARRAY [0 .. 2] OF O;
  gl: L;
  gri: REF INTEGER;
  grr: REF Rec;
  gany: REFANY;
  gcs: CS;
  gss: SS;
  gsa: ARRAY [0 .. 2] OF SS;
  gbs: BS;
  gchs: CHS;
  gt, gt2: TEXT;
  gch: CHAR;
  gca: Chars;

PROCEDURE T (tag: TEXT; v: INTEGER) =
  BEGIN
    IO.Put(tag & " " & Fmt.Int(v) & "\\n");
  END T;

PROCEDURE S (v: INTEGER) =
  BEGIN
    IO.Put(" " & Fmt.Int(v));
  END S;

PROCEDURE ST (t: TEXT) =
  VAR c: CHAR;
  BEGIN
    S(Text.Length(t));
    IO.Put(" ");
    FOR i := 0 TO Text.Length(t) - 1 DO
      c := Text.GetChar(t, i);
      IF c >= 'a' AND c <= 'z' OR c >= 'A' AND c <= 'Z' OR c >= '0' AND c <= '9' THEN
        IO.Put(Text.FromChar(c));
      ELSE
        IO.Put("<" & Fmt.Int(ORD(c)) & ">");
      END;
    END;
  END ST;

PROCEDURE PT (tag: TEXT; t: TEXT) =
  BEGIN
    IO.Put(tag);
    ST(t);
    IO.Put("\\n");
  END PT;

PROCEDURE SCS (s: CS) =
  BEGIN
    S(-100);
    FOR c := FIRST(Color) TO LAST(Color) DO IF c IN s THEN S(ORD(c)) END END;
  END SCS;

PROCEDURE SSS (READONLY s: SS) =
  BEGIN
    S(-100);
    FOR i := FIRST(Sub) TO LAST(Sub) DO IF i IN s THEN S(i) END END;
  END SSS;

PROCEDURE SBS (READONLY s: BS) =
  BEGIN
    S(-100);
    FOR i := 0 TO 99 DO IF i IN s THEN S(i) END END;
  END SBS;

PROCEDURE SCH (READONLY s: CHS) =
  BEGIN
    S(-100);
    FOR c := FIRST(CHAR) TO LAST(CHAR) DO IF c IN s THEN S(ORD(c)) END END;
  END SCH;

PROCEDURE SR (READONLY r: Rec) =
  BEGIN
    S(r.a); S(r.b); S(ORD(r.c));
    FOR i := 0 TO 2 DO S(r.arr[i]) END;
  END SR;

PROCEDURE SO (o: O) =
  BEGIN
    TYPECASE o OF
    | O2 (x) =>
        S(2); S(x.b); SR(x.r);
        FOR i := 0 TO 2 DO S(x.arr[i]) END;
        IF x.nx = NIL THEN S(-1) ELSE S(x.nx.a) END;
    | O1 (x) => S(1); S(x.b); SR(x.r);
    | O3 (x) => S(3); S(x.d); SCS(x.s);
    ELSE S(0);
    END;
    S(o.a); S(ORD(o.c));
  END SO;

PROCEDURE SL (l: L) =
  VAR n := 0;
  BEGIN
    WHILE l # NIL DO
      IF n < 8 THEN S(l.v) END;
      INC(n);
      l := l.next;
    END;
    S(1000 + n);
  END SL;

PROCEDURE SA (x: REFANY) =
  BEGIN
    TYPECASE x OF
    | NULL => S(-1);
    | O (o) => S(-2); S(o.a);
    | L (l) => S(-3); S(l.v);
    | REF INTEGER (r) => S(-4); S(r^);
    | REF Rec (r) => S(-5); S(r.a);
    | TEXT (t) => S(-7); ST(t);
    ELSE S(-6);
    END;
  END SA;

PROCEDURE Snap (tag: TEXT) =
  BEGIN
    IO.Put(tag);
    S(gi0); S(gi1); S(gi2); S(gi3); S(gs); S(ORD(gc));
    FOR i := 0 TO 4 DO S(ga[i]) END;
    SR(gr); SR(gr2);
    S(gp.x); S(gp.y); S(ORD(gp.c)); S(gp.z);
    FOR i := 0 TO LAST(go^) DO S(go[i]) END;
    FOR i := 0 TO LAST(go2^) DO
      FOR j := 0 TO LAST(go2[0]) DO S(go2[i, j]) END;
    END;
    IO.Put(" |");
    SO(gob); SO(gob1); SO(gob2); SO(gob3);
    FOR i := 0 TO 2 DO SO(goa[i]) END;
    S(ORD(gob = gob1)); S(ORD(gob = gob2)); S(ORD(gob1 = gob2)); S(ORD(gob = gob3));
    FOR i := 0 TO 2 DO S(ORD(goa[i] = gob)); S(ORD(goa[i] = gob1)) END;
    SL(gl); S(gri^); SR(grr^); SA(gany);
    S(ORD(gany = gob)); S(ORD(gany = gl));
    IO.Put(" |");
    SCS(gcs); SCS(gp.s); SSS(gss);
    FOR i := 0 TO 2 DO SSS(gsa[i]) END;
    SBS(gbs); SCH(gchs);
    ST(gt); ST(gt2); S(ORD(gch));
    FOR i := 0 TO 5 DO S(ORD(gca[i])) END;
    IO.Put("\\n");
  END Snap;

"""

GLOBALS = [("gi0", "INTEGER"), ("gi1", "INTEGER"), ("gi2", "INTEGER"), ("gi3", "INTEGER"),
           ("gs", "Sub"), ("gc", "Color"), ("ga", "Arr5"), ("gr", "Rec"), ("gr2", "Rec"),
           ("gp", "Pk"), ("go", "Ref"), ("go2", "Ref2"), ("gob", "O"), ("gob1", "O1"),
           ("gob2", "O2"), ("gob3", "O3"), ("goa", "AO"), ("gl", "L"), ("gri", "RI"),
           ("grr", "RR"), ("gany", "Any"), ("gcs", "CS"), ("gss", "SS"), ("gsa", "ASS"), ("gbs", "BS"),
           ("gchs", "CHS"), ("gt", "TX"), ("gt2", "TX"), ("gch", "Ch"), ("gca", "Chars")]

# ------------------------------------------------------------- modules

# the parts of the header: the types, the exceptions and variables, and the
# procedures
H_TYPES = HEADER[:HEADER.index("EXCEPTION E")].rstrip()
H_VARS = HEADER[HEADER.index("EXCEPTION E"):HEADER.index("PROCEDURE T ")].rstrip()
H_PROCS = HEADER[HEADER.index("PROCEDURE T "):]
H_TYPE_NAMES = re.findall(r"^  (\w+) =", H_TYPES, re.M)
H_VAR_NAMES = [n.strip() for line in re.findall(r"^  ([\w, ]+):", H_VARS, re.M) for n in line.split(",")]
H_DECLS = [line[:-2] + ";" for line in H_PROCS.splitlines() if line.startswith("PROCEDURE ")]
H_PROC_NAMES = [re.match(r"PROCEDURE (\w+)", d).group(1) for d in H_DECLS]
SNAP_END = '    IO.Put("\\n");\n  END Snap;'

# the procedures of the opaque type QT (module M1) and of its subtype QT2
QT_PROCS = """\
PROCEDURE QInc (self: QT; n: INTEGER): INTEGER RAISES {E, F} =
  BEGIN
    self.n := (self.n + n) MOD 997;
    self.h[self.n MOD 3] := self.a MOD 997;
    IF self.n MOD 11 = 5 THEN RAISE F(self.n) END;
    RETURN self.n;
  END QInc;

PROCEDURE QGet (self: QT): INTEGER =
  BEGIN
    RETURN (self.n * 7 + self.a + self.h[0] + self.h[1] + self.h[2]) MOD 997;
  END QGet;

PROCEDURE NewQ (a: INTEGER): QT =
  BEGIN
    RETURN NEW(QT, a := a);
  END NewQ;

PROCEDURE SQ (q: QT) =
  BEGIN
    S(q.a); S(q.n); S(q.get());
    FOR j := 0 TO 2 DO S(q.h[j]) END;
    TYPECASE q OF
    | QT2 (q2) => S(q2.w);
    ELSE S(-1);
    END;
  END SQ;
"""

QT2_PROCS = """\
PROCEDURE Q2Inc (self: QT2; n: INTEGER): INTEGER RAISES {E, F} =
  BEGIN
    self.w := (self.w * 3 + n) MOD 997;
    IF self.w MOD 13 = 4 THEN RAISE E END;
    RETURN QT.inc(self, n + self.w) + 1;
  END Q2Inc;

PROCEDURE NewQ2 (a: INTEGER): QT =
  BEGIN
    RETURN NEW(QT2, a := a, w := a MOD 5);
  END NewQ2;
"""


# a subtype of QT2 that only Main declares
QT3_DECL = """\
TYPE
  QT3 = QT2 OBJECT
    v: INTEGER := 9;
  OVERRIDES
    get := Get3;
  END;

PROCEDURE Get3 (self: QT3): INTEGER =
  BEGIN
    RETURN (QT2.get(self) + self.v * 3) MOD 997;
  END Get3;
"""


def from_import(intf, names):
    """FROM intf IMPORT names, in lines of at most about 100 characters."""
    lines, cur = [], f"FROM {intf} IMPORT "
    for k, n in enumerate(names):
        piece = n + (", " if k < len(names) - 1 else ";")
        if len(cur) + len(piece) > 100:
            lines.append(cur.rstrip())
            cur = "  "
        cur += piece
    lines.append(cur)
    return "\n".join(lines)


class Layout:
    """How a program is split into modules. The interface G has the types,
    exceptions and variables of the header and declares its procedures,
    which the module G implements, and the method procedures, which G or
    the modules Mi implement (so that several modules export G). The other
    procedures that are not nested are in Main or in a module Mi, whose
    interface declares them. The interface Mi also has a constant ki, the
    variables xi and ri with initial values and the procedure Dump; the
    module Mi has variables yi and zi of its own, which its body sets.
    The interface M1 declares the opaque object type QT, a subtype of QPub,
    which the module M1 reveals, and the variables gq and gqa; the last
    interface Mk declares QT2, a subtype of QT that overrides a method and
    calls the method it overrides. The module of Mi is Mi or MiImpl (which
    exports Mi). Every unit imports the names of the others with FROM
    IMPORT; Snap in G prints the variables of the modules (and calls their
    Dump) with qualified names."""

    def __init__(self, r):
        self.rnd = r
        self.k = r.randint(1, 3)
        self.mods = []
        for i in range(1, self.k + 1):
            self.mods.append({
                "i": i, "name": f"M{i}", "impl": f"M{i}Impl" if r.random() < 0.3 else f"M{i}",
                "k": r.randint(1, 50), "x": lit(r.randint(-20, 300)), "y": lit(r.randint(-20, 300)),
                "r": f"Rec{{{r.randint(0, 99)}, {lit(r.randint(-5, 20))}, {r.choice(COLORS)}, "
                     f"Arr3{{{r.randint(0, 9)}, {r.randint(0, 9)}, {r.randint(0, 9)}}}}}",
                "z": "Arr5{" + ", ".join(lit(r.randint(-20, 300)) for _ in range(5)) + "}",
                "q": [r.randint(0, 99) for _ in range(2)]})
        # the modules of the procedures that are not nested
        self.homes = ["Main"] + [m["name"] for m in self.mods]
        self.impl_homes = ["G"] + [m["name"] for m in self.mods]

    def globals(self):
        out = []
        for m in self.mods:
            i = m["i"]
            out += [Var(f"x{i}", "INTEGER"), Var(f"r{i}", "Rec"), Var(f"k{i}", "INTEGER", writable=False),
                    Var(f"f{i}", "Fn")]
        return out + [Var("gq", "QT"), Var("gqa", "AQT")]

    def private(self, module):
        """The variables of the module itself."""
        for m in self.mods:
            if m["name"] == module:
                return [Var(f"y{m['i']}", "INTEGER"), Var(f"z{m['i']}", "Arr5")]
        return []

    @staticmethod
    def top(prog, module, impl, private=True):
        return [p for p in prog.procs
                if p.parent is None and not p.removed and p.module == module and bool(p.impl) == impl
                and (private or not p.private)]

    def exports(self, m, prog):
        """The names that the interface of m declares."""
        i = m["i"]
        names = [p.name for p in self.top(prog, m["name"], False, False)] + [f"x{i}", f"r{i}", f"k{i}", f"f{i}"]
        if i == 1:
            names += ["QPub", "QT", "gq", "gqa", "NewQ"]
        if i == self.k:
            names += ["QT2", "NewQ2"]
        return names

    @staticmethod
    def g_names(prog):
        return (H_TYPE_NAMES + ["E", "F"] + H_VAR_NAMES + H_PROC_NAMES
                + [p.name for p in prog.procs if p.impl and not p.removed])

    def imports(self, prog, skip=None, g=True, qualified=()):
        L = ["IMPORT " + ", ".join(["IO", "Fmt", "Text"] + list(qualified)) + ";"]
        if g:
            L.append(from_import("G", self.g_names(prog)))
        for m in self.mods:
            if m["name"] != skip:
                L.append(from_import(m["name"], self.exports(m, prog)))
        return L + [""]

    def files(self, prog):
        hdr = f"(* Generated by tools/prog_fuzz.py, seed {prog.seed}. *)"
        out = [("G.i3", self.g_interface(prog, hdr)), ("G.m3", self.g_module(prog, hdr))]
        for m in self.mods:
            out.append((m["name"] + ".i3", self.interface(m, prog, hdr)))
            out.append((m["impl"] + ".m3", self.module(m, prog, hdr)))
        L = [hdr, "", "MODULE Main;", ""] + self.imports(prog) + QT3_DECL.splitlines() + [""]
        prog.render_procs("Main", L)
        prog.body(L)
        out.append(("Main.m3", "\n".join(L) + "\n"))
        return out

    def g_interface(self, prog, hdr):
        L = [hdr, "", "INTERFACE G;", "", H_TYPES, "", H_VARS, ""] + H_DECLS
        L += [p.decl() for p in prog.procs if p.impl and not p.removed]
        return "\n".join(L + ["", "END G.", ""])

    def g_module(self, prog, hdr):
        L = [hdr, "", "MODULE G;", ""] + self.imports(prog, g=False, qualified=[m["name"] for m in self.mods])
        extra = '    IO.Put(" |");\n' + "".join(
            f"    S({m['name']}.x{m['i']}); SR({m['name']}.r{m['i']}); {m['name']}.Dump();\n" for m in self.mods)
        assert H_PROCS.count(SNAP_END) == 1
        L += H_PROCS.replace(SNAP_END, extra + SNAP_END).splitlines()
        prog.render_procs("G", L)
        return "\n".join(L + ["BEGIN", "END G.", ""])

    def interface(self, m, prog, hdr):
        i, k = m["i"], self.k
        L = [hdr, "", f"INTERFACE M{i};", "", from_import("G", H_TYPE_NAMES + ["E", "F"])]
        if i == k and k > 1:
            L.append("FROM M1 IMPORT QT;")
        L += ["", f"CONST k{i} = {m['k']};", ""]
        if i == 1:
            L += ["TYPE", "  QPub = OBJECT", "    a: INTEGER := 0;", "  METHODS",
                  "    inc (n: INTEGER): INTEGER RAISES {E, F};", "    get (): INTEGER;", "  END;",
                  "  QT <: QPub;", ""]
        if i == k:
            L += ["TYPE", "  QT2 = QT OBJECT", "    w: INTEGER := 3;", "  OVERRIDES", "    inc := Q2Inc;",
                  "  END;", ""]
        L += ["VAR", f"  x{i}: INTEGER := {m['x']};", f"  r{i} := {m['r']};", f"  f{i}: Fn;"]
        if i == 1:
            L += ["  gq: QT;", "  gqa: ARRAY [0 .. 1] OF QT;"]
        L.append("")
        L += [p.decl() for p in self.top(prog, m["name"], False, False)]
        if i == 1:
            L.append("PROCEDURE NewQ (a: INTEGER): QT;")
        if i == k:
            L.append("PROCEDURE NewQ2 (a: INTEGER): QT;")
            L.append("PROCEDURE Q2Inc (self: QT2; n: INTEGER): INTEGER RAISES {E, F};")
        L += ["PROCEDURE Dump ();", "", f"END M{i}.", ""]
        return "\n".join(L)

    def module(self, m, prog, hdr):
        i, k = m["i"], self.k
        g = bool(self.top(prog, m["name"], True))
        head = f"MODULE {m['impl']}"
        if g or m["impl"] != m["name"]:
            head += " EXPORTS " + ", ".join([m["name"]] + (["G"] if g else []))
        L = [hdr, "", head + ";", ""] + self.imports(prog, skip=m["name"], g=not g)
        if i == 1:
            L += ["REVEAL", "  QT = QPub BRANDED OBJECT", "    n: INTEGER := 7;", "    h: Arr3 := Arr3{1, 2, 3};",
                  "  OVERRIDES", "    inc := QInc;", "    get := QGet;", "  END;", ""]
        L += [f"VAR y{i}: INTEGER := {m['y']};", f"VAR z{i}: Arr5;", ""]
        if i == 1:
            L += QT_PROCS.splitlines() + [""]
        if i == k:
            L += QT2_PROCS.splitlines() + [""]
        L += ["PROCEDURE Dump () =", "  BEGIN", f"    S(y{i});", f"    FOR j := 0 TO 4 DO S(z{i}[j]) END;"]
        if i == 1:
            L += ["    SQ(gq);", "    FOR j := 0 TO 1 DO SQ(gqa[j]) END;",
                  "    S(ORD(gq = gqa[0])); S(ORD(gq = gqa[1])); S(ORD(gqa[0] = gqa[1]));"]
        L += ["  END Dump;", "",
              f"PROCEDURE Id{i} (x: INTEGER): INTEGER RAISES {{E, F}} =", "  BEGIN",
              f"    RETURN (x * k{i} + y{i}) MOD 997;", f"  END Id{i};", ""]
        prog.render_procs(m["name"], L)
        L += ["BEGIN", f"  z{i} := {m['z']};", f"  y{i} := (y{i} + k{i}) MOD 997;", f"  f{i} := {m['f']};"]
        if i == 1:
            a, b = m["q"]
            L += [f"  gq := NEW(QT, a := {a});", f"  gqa[0] := NewQ({b});", "  gqa[1] := gq;"]
        return "\n".join(L + [f"END {m['impl']}.", ""])


# ----------------------------------------------------------- generator

BASE = {"assign": 6, "print": 4, "inc": 2, "if": 4, "loop": 3, "for": 3, "case": 3, "exit": 3,
        "return": 1.5, "tryf": 3, "trye": 3, "raise": 2.5, "lock": 2, "with": 3, "call": 6,
        "copy": 3, "sub": 2, "mcall": 3, "oassign": 2, "tcase": 2, "narrow": 1.5, "list": 2,
        "walk": 1.5, "sassign": 3, "sfor": 1.5, "tassign": 3, "tloop": 1.5, "qcall": 3}
GROUPS = {"mod": ["qcall", "call"],"flow": ["if", "loop", "for", "case", "exit", "return", "tryf", "trye", "raise", "lock"],
          "uplevel": ["call"], "var": ["call", "with"], "copy": ["copy", "call", "with"],
          "open": ["sub", "call"], "obj": ["mcall", "oassign", "tcase", "narrow", "list", "walk"],
          "set": ["sassign", "sfor"], "text": ["tassign", "tloop"]}
COMPOUND = {"if", "loop", "for", "case", "tryf", "trye", "lock", "with", "sub", "tcase", "narrow",
            "walk", "sfor", "tloop"}
MAXDEPTH = 3
FUEL = 300
ROUNDS = 3

# FOR loops near the ends of INTEGER whose last increment does not overflow
# (the language defines FOR by INC, so that cm3 loops forever there)
FOR_LIMITS = [("LAST(INTEGER)", "LAST(INTEGER) - 2", "-1"), ("LAST(INTEGER) - 10", "LAST(INTEGER) - 5", "2"),
              ("LAST(INTEGER) - 1", "LAST(INTEGER) - 3", "-1"), ("FIRST(INTEGER)", "FIRST(INTEGER) + 2", None),
              ("FIRST(INTEGER) + 9", "FIRST(INTEGER) + 4", "-2"), ("FIRST(INTEGER) + 1", "FIRST(INTEGER) + 3", None),
              ("LAST(INTEGER) - 3", "LAST(INTEGER) - 1", None), ("FIRST(INTEGER) + 3", "FIRST(INTEGER) + 1", "-1"),
              ("LAST(INTEGER)", "FIRST(INTEGER)", None), ("FIRST(INTEGER)", "LAST(INTEGER)", "-1"),
              ("5", "2", None), ("2", "5", "-1"), ("-2147483648", "-2147483646", None),
              ("2147483647", "2147483645", "-1"), ("2147483645", "2147483647", "2")]


class Ctx:
    """Where a statement is generated: its procedure (None in the main
    body), the visible variables, whether it is in a loop (EXIT), in a
    LOCK (no calls and locks), where EXIT to the loop around it or RETURN
    is not allowed, and the depth of statements around it."""

    def __init__(self, proc, scope):
        self.proc, self.scope = proc, scope
        self.loop = self.lock = self.noexit = self.noreturn = False
        self.depth = 0

    def but(self, **kw):
        c = copy.copy(self)
        c.depth = self.depth + 1
        for k, v in kw.items():
            setattr(c, k, v)
        return c

    def adding(self, *vs):
        c = copy.copy(self)
        c.scope = list(vs) + self.scope
        return c


class Gen:
    def __init__(self, seed, focus=(), avoid=()):
        self.seed = seed
        self.rnd = random.Random(seed)
        self.focus = set(focus)
        self.avoid = set(avoid)
        self.n = 0
        self.weights = dict(BASE)
        for g in self.focus:
            for k in GROUPS[g]:
                self.weights[k] *= 3
        self.globals = [Var(n, t) for n, t in GLOBALS]
        self.procs = []
        # several modules (a random number of its own, so that the programs
        # of one module stay as they were)
        self.layout = None
        lr = random.Random(seed * 7907 + 3)
        if "modules" not in self.avoid and ("mod" in self.focus or lr.random() < 0.5):
            self.layout = Layout(lr)
            self.globals += self.layout.globals()
        # integer leaves and conditions on sets and texts (not inside one)
        self.dnest = 0
        self.dprob = 0.15 if self.focus & {"set", "text"} else 0.05
        # in the selector of a CASE that is not reduced MOD k
        self.case_sel = False

    def fresh(self, prefix):
        self.n += 1
        return f"{prefix}{self.n}"

    def tag(self):
        return self.fresh("t")

    def chance(self, p, group=None):
        if group in self.focus:
            p = min(0.9, p * 2)
        return self.rnd.random() < p

    # ---------------------------------------------------- designators

    def weight(self, ctx, v):
        if v.owner is None:
            return 1
        if v.owner == "stmt" or v.owner is ctx.proc:
            return 2
        return 5 if "uplevel" in self.focus else 2

    def pick(self, ctx, types, writable=False, simple=False, packed=True):
        """A random designator (text, type, writable) of one of the types, or None."""
        # simple: no index and no reference (the target of a call, which may
        # change the reference); packed: also fields of packed records
        cands, ws = [], []
        for v in ctx.scope:
            if not packed and v.type == "Pk":
                continue
            for path, pt in PATHS[v.type]:
                ref = via_ref(v.type, path)
                if writable and not (v.writable or ref):
                    continue
                if pt in types and not (simple and ("[" in path or ref)):
                    cands.append((v, path, pt))
                    ws.append(self.weight(ctx, v))
        if not cands:
            return None
        v, path, pt = self.rnd.choices(cands, ws)[0]
        return self.path_text(ctx, v, path), pt, v.writable or via_ref(v.type, path)

    def index(self, ctx, n):
        return f"({self.iexpr(ctx, 1, simple=True)}) MOD {n}"

    def path_text(self, ctx, v, path):
        b = v.name
        if path == "[r]":
            return f"{b}[{self.index(ctx, f'NUMBER({b}^)')}]"
        if path == "[r,c]":
            return f"{b}[{self.index(ctx, f'NUMBER({b}^)')}, {self.index(ctx, f'NUMBER({b}[0])')}]"
        out = b
        rest = path
        while rest:
            if rest.startswith("[]"):
                if out.endswith(".arr"):
                    n = "3"
                else:
                    n = {"Arr5": "5", "Arr3": "3", "AO": "3", "Open": f"NUMBER({b})",
                         "Ref": f"NUMBER({b}^)", "Chars": "6", "ASS": "3", "AQT": "2"}[v.type]
                out += f"[{self.index(ctx, n)}]"
                rest = rest[2:]
            else:
                k = 1
                while k < len(rest) and rest[k] not in ".[^":
                    k += 1
                out += rest[:k]
                rest = rest[k:]
        return out

    # ---------------------------------------------------- expressions

    def literal(self):
        return lit(self.rnd.choice([0, 1, 2, 3, 4, 5, 7, 10, 42, 100, 255, 996, -1, -2, -7, -100,
                                    self.rnd.randint(-20, 300)]))

    def ileaf(self, ctx, simple):
        if self.dnest == 0 and self.rnd.random() < self.dprob:
            x = self.dleaf(ctx)
            if x:
                return x
        r = self.rnd.random()
        if r < 0.3:
            return self.literal()
        if r < 0.9:
            d = self.pick(ctx, READ, simple=simple)
            if d:
                return ival(d[0], d[1])
        else:
            d = self.pick(ctx, ARRAYS, simple=True)
            if d:
                return f"{self.rnd.choice(['NUMBER', 'LAST'])}({d[0]})"
        return self.literal()

    def iexpr(self, ctx, d, simple=False):
        """An INTEGER expression of depth d without calls."""
        if d <= 0 or self.rnd.random() < 0.25:
            return self.ileaf(ctx, simple)
        op = self.rnd.choice(["+", "-", "+", "-", "*", "DIV", "MOD", "MIN", "MAX", "ABS"])
        a = self.iexpr(ctx, d - 1, simple)
        if op in ("+", "-"):
            return f"({a} {op} {self.iexpr(ctx, d - 1, simple)})"
        if op == "*":
            return f"({a} * {lit(self.rnd.randint(-3, 7))})"
        if op in ("DIV", "MOD"):
            return f"({a} {op} {self.rnd.randint(1, 9)})"
        if op == "ABS":
            # in the selector of a CASE, the absolute value as a DIV (see the top)
            return f"(ABS({a}) DIV 1)" if self.case_sel else f"ABS({a})"
        return f"{op}({a}, {self.iexpr(ctx, d - 1, simple)})"

    def cond(self, ctx, d=1):
        if self.dnest == 0 and self.rnd.random() < self.dprob * 1.5:
            c = self.data_cond(ctx)
            if c:
                return c
        if self.chance(0.08, "obj"):
            c = self.obj_cond(ctx)
            if c:
                return c
        r = self.rnd.random()
        if r < 0.35 or d <= 0:
            k = self.rnd.randint(2, 4)
            return f"({self.iexpr(ctx, 1)}) MOD {k} = {self.rnd.randrange(k)}"
        if r < 0.6:
            op = self.rnd.choice(["<", "<=", ">", ">=", "=", "#"])
            return f"{self.iexpr(ctx, 1)} {op} {self.iexpr(ctx, 1)}"
        if r < 0.67:
            return f"({self.iexpr(ctx, 1)}) MOD 2 # 0"
        if r < 0.72:
            t = self.rnd.choice(["Rec", "Arr5", "Arr3"])
            a, b = self.pick(ctx, (t,)), self.pick(ctx, (t,))
            if a and b:
                return f"{a[0]} {self.rnd.choice(['=', '#'])} {b[0]}"
            return "TRUE"
        if r < 0.82:
            return f"({self.cond(ctx, d - 1)}) AND ({self.cond(ctx, d - 1)})"
        if r < 0.92:
            return f"({self.cond(ctx, d - 1)}) OR ({self.cond(ctx, d - 1)})"
        return f"NOT ({self.cond(ctx, d - 1)})"

    # ------------------------------------------------- sets and texts

    def dleaf(self, ctx):
        """An INTEGER expression on a text or a set, or None."""
        self.dnest += 1
        try:
            if self.rnd.random() < 0.55:
                d = self.pick(ctx, ("TX",))
                if not d:
                    return None
                t, s = d[0], self.rnd.random()
                if s < 0.35:
                    return f"Text.Length({t})"
                if s < 0.6:
                    return f'ORD(Text.GetChar({t} & "#", ({self.iexpr(ctx, 1, True)}) MOD (Text.Length({t}) + 1)))'
                if s < 0.8:
                    return f"Text.FindChar({t}, {self.cexpr(ctx)})"
                return f"Text.Compare({t}, {self.texpr(ctx, 1)})"
            d = self.pick(ctx, tuple(SETS))
            if not d:
                return None
            return f"ORD({self.value(ctx, SETS[d[1]])} IN {d[0]})"
        finally:
            self.dnest -= 1

    def data_cond(self, ctx):
        """A condition on sets, texts or characters, or None."""
        self.dnest += 1
        try:
            r = self.rnd.random()
            if r < 0.4:
                d = self.pick(ctx, tuple(SETS))
                if not d:
                    return None
                t = d[1]
                if self.rnd.random() < 0.5:
                    return f"{self.value(ctx, SETS[t])} IN {self.sexpr(ctx, t, 1)}"
                if t in BIG:
                    # = and # of large sets as values (see SETS), not <, <=, >, >=
                    return f"ORD({self.sexpr(ctx, t, 1)} {self.rnd.choice(['=', '#'])} {self.sexpr(ctx, t, 1)}) = 1"
                ops = ["=", "#", "<", "<=", ">", ">="]
                return f"{self.sexpr(ctx, t, 1)} {self.rnd.choice(ops)} {self.sexpr(ctx, t, 1)}"
            if r < 0.75:
                d = self.pick(ctx, ("TX",))
                if not d:
                    return None
                s = self.rnd.random()
                if s < 0.5:
                    return f"Text.Equal({d[0]}, {self.texpr(ctx, 1)})"
                if s < 0.65:
                    return f"Text.Empty({d[0]})"
                return f"Text.Length({self.texpr(ctx, 1)}) {self.rnd.choice(['<', '>=', '='])} {self.rnd.randint(0, 6)}"
            d = self.pick(ctx, ("Ch", "FCh"))
            if not d:
                return None
            return f"{d[0]} {self.rnd.choice(['<', '<=', '>', '>=', '=', '#'])} {self.cexpr(ctx)}"
        finally:
            self.dnest -= 1

    def cexpr(self, ctx):
        """An expression of type CHAR without calls."""
        r = self.rnd.random()
        if r < 0.4:
            d = self.pick(ctx, ("Ch", "FCh"))
            if d:
                return d[0]
        if r < 0.55:
            d = self.pick(ctx, ("TX",))
            if d:
                return f'Text.GetChar({d[0]} & "#", ({self.iexpr(ctx, 1, True)}) MOD (Text.Length({d[0]}) + 1))'
        if r < 0.65:
            return self.rnd.choice(["'a'", "'z'", "'0'", "'-'", "VAL(200, CHAR)", "FIRST(CHAR)", "LAST(CHAR)"])
        return norm("Ch", self.iexpr(ctx, self.rnd.randint(0, 2)))

    def texpr(self, ctx, d):
        """An expression of type TEXT without calls of procedures of the program."""
        if d > 0 and self.rnd.random() < 0.5:
            r = self.rnd.random()
            if r < 0.5:
                return f"({self.texpr(ctx, d - 1)} & {self.texpr(ctx, d - 1)})"
            if r < 0.8:
                return (f"Text.Sub({self.texpr(ctx, d - 1)}, ({self.iexpr(ctx, 1)}) MOD 5, "
                        f"({self.iexpr(ctx, 1)}) MOD 8)")
            return f"Text.Cat({self.texpr(ctx, d - 1)}, {self.texpr(ctx, d - 1)})"
        r = self.rnd.random()
        if r < 0.6:
            d = self.pick(ctx, ("TX",))
            if d and r >= 0.2:
                return d[0]
            return self.rnd.choice(TLITS)
        if r < 0.75:
            return f"Fmt.Int({self.iexpr(ctx, 1)})"
        if r < 0.9:
            return f"Text.FromChar({self.cexpr(ctx)})"
        d = self.pick(ctx, ("Chars",))
        if not d:
            return self.rnd.choice(TLITS)
        if self.rnd.random() < 0.5:
            return f"Text.FromChars({d[0]})"
        k = self.rnd.randint(0, 5)
        return f"Text.FromChars(SUBARRAY({d[0]}, {k}, {self.rnd.randint(0, 6 - k)}))"

    def sexpr(self, ctx, t, d):
        """An expression of the set type t without calls."""
        if d > 0 and self.rnd.random() < 0.4:
            op = self.rnd.choice(["+", "-", "*", "/"])
            return f"({self.sexpr(ctx, t, d - 1)} {op} {self.sexpr(ctx, t, d - 1)})"
        if self.rnd.random() < 0.55:
            v = self.pick(ctx, (t,))
            if v:
                return v[0]
        et = SETS[t]
        items = []
        for _ in range(self.rnd.randint(0, 3)):
            if self.rnd.random() < 0.3:
                if t in BIG:
                    # ranges of large sets may be computed (see SETS)
                    items.append(f"{self.value(ctx, et)} .. {self.value(ctx, et)}")
                else:
                    a, b = self.rnd.randrange(4), self.rnd.randrange(4)
                    if t == "CS":
                        items.append(f"{COLORS[a]} .. {COLORS[b]}")
                    else:
                        # both bounds in the domain, also of an empty range
                        a = self.rnd.randint(-4, 18)
                        items.append(f"{lit(a)} .. {lit(a + b - 1)}")
            elif t == "CHS" and self.rnd.random() < 0.3:
                items.append(self.cexpr(ctx))
            else:
                items.append(self.value(ctx, et))
        return f"{t}{{{', '.join(items)}}}"

    def obj_cond(self, ctx):
        """A condition on references, or None."""
        r = self.rnd.random()
        if r < 0.35:
            a = self.pick(ctx, OBJ)
            if not a:
                return None
            b = self.pick(ctx, tuple(t for t in OBJ if isub(t, a[1]) or isub(a[1], t)))
            return f"{a[0]} {self.rnd.choice(['=', '#'])} {b[0]}"
        if r < 0.65:
            a = self.pick(ctx, OBJ)
            if not a:
                return None
            return f"ISTYPE({a[0]}, {self.rnd.choice(SUBS[a[1]])})"
        if r < 0.8:
            a = self.pick(ctx, ("L", "Any"))
            return a and f"{a[0]} {self.rnd.choice(['=', '#'])} NIL"
        a = self.pick(ctx, ("Any",))
        if not a:
            return None
        if self.rnd.random() < 0.3:
            b = self.pick(ctx, OBJ + REFS)
            return f"{a[0]} {self.rnd.choice(['=', '#'])} {b[0]}"
        return f"ISTYPE({a[0]}, {self.rnd.choice(list(ANYCASE))})"

    def obj_value(self, ctx, t, ctor=True, meths=True):
        """An object of type t (not NIL) without calls (NEW only if ctor,
        with methods only if meths: its type is then a new subtype, which
        TYPECASE, ISTYPE and NARROW cannot test); self.vtype is set to its
        static type."""
        if not ctor or self.rnd.random() < 0.55:
            d = self.pick(ctx, SUBS[t])
            if d:
                self.vtype = d[1]
                return d[0]
        t2 = self.rnd.choice(SUBS[t])
        inits = []
        for f, ft in FIELDS[t2]:
            if self.rnd.random() < 0.3:
                if ft == "ONIL":
                    v = "NIL" if self.rnd.random() < 0.3 else self.obj_value(ctx, "O", False)
                else:
                    v = self.value(ctx, ft)
                inits.append(f"{f} := {v}")
        if meths and "new-methods" not in self.avoid:
            for meth, impl in BIND[t2].items():
                if self.chance(0.12, "obj"):
                    # a procedure of the method whose self is a supertype of t2
                    cands = [n for n, mt, st in IMPLS if mt == meth and isub(t2, st)]
                    inits.append(f"{meth} := {self.rnd.choice(cands)}")
        self.rnd.shuffle(inits)
        self.vtype = t2
        return f"NEW({t2}{''.join(', ' + i for i in inits)})"

    def value(self, ctx, t, ctor=True):
        """An expression of type t without calls (a constructor only if ctor)."""
        if t in INTS or t in ORDS:
            return norm(t, self.iexpr(ctx, self.rnd.randint(0, 2)))
        if t == "Open":
            return self.open_actual(ctx, False)
        if t in OBJ:
            return self.obj_value(ctx, t, ctor)
        if t == "TX":
            return self.texpr(ctx, self.rnd.randint(0, 2))
        if t in SETS:
            return self.sexpr(ctx, t, self.rnd.randint(0, 1))
        if t in REFS:
            d = self.pick(ctx, ("L", "LN") if t == "L" else (t,))
            return d[0] if d else "NIL"
        if self.rnd.random() < 0.6 or not ctor:
            d = self.pick(ctx, (t,))
            if d:
                return d[0]
        if t == "Rec":
            return ("Rec{a := " + self.value(ctx, "INTEGER") + ", b := " + self.value(ctx, "Sub")
                    + ", c := " + self.value(ctx, "Color") + ", arr := " + self.value(ctx, "Arr3") + "}")
        n = 5 if t == "Arr5" else 3
        return t + "{" + ", ".join(self.value(ctx, "INTEGER") for _ in range(n)) + "}"

    def open_actual(self, ctx, writable):
        """An array for a formal ARRAY OF INTEGER, not empty."""
        d = self.pick(ctx, ARRAYS, writable)
        if not d:
            return None
        a = d[0]
        if self.chance(0.3, "open"):
            k = self.index(ctx, f"NUMBER({a})")
            a = f"SUBARRAY({a}, {k}, 1 + ({self.iexpr(ctx, 1, True)}) MOD (NUMBER({a}) - {k}))"
        return a

    def retval(self, ctx):
        # not an array constructor (see the top)
        return self.value(ctx, ctx.proc.result, ctx.proc.result != "Arr5")

    def return_text(self, ctx):
        return f"RETURN {self.retval(ctx)}" if ctx.proc.result else "RETURN"

    def raise_text(self, ctx):
        if self.rnd.random() < 0.4:
            return "RAISE E"
        return f"RAISE F({self.value(ctx, 'INTEGER')})"

    # ------------------------------------------------------ statements

    def block(self, ctx, n):
        out = []
        for _ in range(n):
            s = self.stmt(ctx)
            if s:
                out.append(s)
        if ctx.depth > 0:
            r = self.rnd.random()
            if r < 0.06 and ctx.loop and not ctx.noexit:
                out.append(Leaf("EXIT"))
            elif r < 0.09 and ctx.proc and not ctx.noreturn:
                out.append(Leaf(self.return_text(ctx)))
            elif r < 0.12:
                out.append(Leaf(self.raise_text(ctx)))
        return out

    def inner(self, ctx, lo=1, hi=3):
        return self.block(ctx, self.rnd.randint(lo, hi))

    def stmt(self, ctx):
        kinds, ws = [], []
        for k, w in self.weights.items():
            if k in COMPOUND and ctx.depth >= MAXDEPTH:
                continue
            if k == "exit" and not (ctx.loop and not ctx.noexit):
                continue
            if k == "return" and (ctx.proc is None or ctx.noreturn):
                continue
            if k in ("lock", "call", "mcall") and ctx.lock:
                continue
            if k == "qcall" and not self.layout:
                continue
            kinds.append(k)
            ws.append(w)
        for _ in range(5):
            k = self.rnd.choices(kinds, ws)[0]
            s = getattr(self, "s_" + k)(ctx)
            if s:
                return s
        return None

    def s_assign(self, ctx):
        d = self.pick(ctx, INTS + ORDS, writable=True)
        if not d:
            return None
        return Leaf(f"{d[0]} := {self.value(ctx, d[1])}")

    def s_print(self, ctx):
        return Leaf(f'T("{self.tag()}", {self.iexpr(ctx, self.rnd.randint(0, 3))})')

    def s_inc(self, ctx):
        d = self.pick(ctx, ("INTEGER",), writable=True)
        if not d:
            return None
        op = self.rnd.choice(["INC", "DEC"])
        return Leaf(f"{op}({d[0]})" if self.rnd.random() < 0.3 else f"{op}({d[0]}, {self.rnd.randint(1, 5)})")

    def s_if(self, ctx):
        c = ctx.but()
        parts = [(f"IF {self.cond(ctx)} THEN", (), self.inner(c))]
        while self.rnd.random() < 0.3:
            parts.append((f"ELSIF {self.cond(ctx)} THEN", (), self.inner(c)))
        if self.rnd.random() < 0.5:
            parts.append(("ELSE", (), self.inner(c)))
        return Block(parts)

    def counter(self, ctx):
        c = self.fresh("c")
        ctx.proc.counters.append(c)
        return c

    def s_loop(self, ctx):
        if ctx.proc is None:
            return None
        c = self.counter(ctx)
        k = self.rnd.randint(0, 4)
        body = self.inner(ctx.but(loop=True, noexit=False))
        kind = self.rnd.choice(["LOOP", "WHILE", "REPEAT"])
        if kind == "LOOP":
            return Block([("LOOP", (f"IF {c} >= {k} THEN EXIT END", f"INC({c})"), body)], pre=[f"{c} := 0"])
        if kind == "WHILE":
            return Block([(f"WHILE {c} < {k} DO", (f"INC({c})",), body)], pre=[f"{c} := 0"])
        return Block([("REPEAT", (f"INC({c})",), body)], end=f"UNTIL {c} >= {k}", pre=[f"{c} := 0"])

    def s_for(self, ctx):
        i = self.fresh("i")
        r = self.rnd.random()
        step = None
        if r < 0.25 and "for-limits" not in self.avoid:
            lo, hi, step = self.rnd.choice(FOR_LIMITS)
            t = "FI"
        elif r < 0.4:
            lo, hi, step = self.rnd.choice([("FIRST(Color)", "LAST(Color)", None), ("Color.Gray", "Color.Red", "-1"),
                                            ("Color.Green", "Color.Blue", None), ("Color.Blue", "Color.Green", None),
                                            ("LAST(Color)", "FIRST(Color)", "-2")])
            t = "FE"
        elif r < 0.5:
            choices = [("'a'", "'c'", None), ("'z'", "'x'", "-1")]
            if "for-limits" not in self.avoid:
                choices += [("VAL(253, CHAR)", "LAST(CHAR)", None), ("VAL(2, CHAR)", "FIRST(CHAR)", "-1")]
            lo, hi, step = self.rnd.choice(choices)
            t = "FCh"
        elif r < 0.6:
            lo, hi = "0", f"({self.iexpr(ctx, 1)}) MOD 4"
            t = "FI"
        else:
            e = self.iexpr(ctx, 1)
            k = self.rnd.randint(-1, 4)
            s = self.rnd.choice([1, 1, 2, -1, -2])
            lo, hi = e, f"{e} + {k}" if s > 0 else f"{e} - {k}"
            step = None if s == 1 else lit(s)
            t = "FI"
        v = Var(i, t, "stmt", writable=False)
        by = f" BY {step}" if step else ""
        body = self.inner(ctx.adding(v).but(loop=True, noexit=False), 0, 3)
        return Block([(f"FOR {i} := {lo} TO {hi}{by} DO", (f'T("{self.tag()}", {ival(i, t)})',), body)])

    def s_case(self, ctx):
        c = ctx.but()
        has_else = self.rnd.random() < 0.6
        extreme = False
        if self.rnd.random() < 0.7:
            k = self.rnd.randint(2, 8)
            extreme = self.rnd.random() < 0.25
            self.case_sel = extreme
            e = self.iexpr(ctx, 2)
            self.case_sel = False
            sel = f"{e} + 0" if extreme else f"({e}) MOD {k}"
            values = [str(x) for x in range(k)]
        else:
            d = self.pick(ctx, ORDS + ("FCh",))
            if not d:
                return None
            if d[1] in ("Ch", "FCh"):
                sel, values, has_else = d[0], [f"'{x}'" for x in "abcdefgh"], True
            else:
                sel, values = d[0], COLORS
        groups = []
        i = 0
        while i < len(values):
            n = self.rnd.randint(1, 3)
            groups.append(values[i:i + n])
            i += n
        arms = []
        for g in groups:
            if has_else and self.rnd.random() < 0.3:
                continue
            if len(g) > 1 and self.rnd.random() < 0.5:
                arms.append(f"{g[0]} .. {g[-1]}")
            else:
                arms.append(", ".join(g))
        if extreme:
            arms.append("FIRST(INTEGER) .. -1")
            if not has_else:
                arms.append(f"{len(values)} .. LAST(INTEGER)")
        self.rnd.shuffle(arms)
        if not arms:
            arms = [values[0]]
        parts = [(f"CASE {sel} OF", (), None)]
        for a in arms:
            parts.append((f"| {a} =>", (f'T("{self.tag()}", 0)',), self.inner(c, 0, 2)))
        if has_else:
            parts.append(("ELSE", (f'T("{self.tag()}", 0)',), self.inner(c, 0, 2)))
        return Block(parts)

    def s_exit(self, ctx):
        return Leaf(f"IF {self.cond(ctx)} THEN EXIT END")

    def s_return(self, ctx):
        return Leaf(f"IF {self.cond(ctx)} THEN {self.return_text(ctx)} END")

    def s_raise(self, ctx):
        return Leaf(f"IF {self.cond(ctx)} THEN {self.raise_text(ctx)} END")

    def s_tryf(self, ctx):
        body = self.inner(ctx.but())
        no = "finally-jump" in self.avoid
        c = ctx.but(noexit=ctx.noexit or no, noreturn=ctx.noreturn or no)
        fin = self.inner(c, 0, 2)
        if has(fin, lambda st: isinstance(st, Leaf) and ("RETURN" in st.text or "EXIT" in st.text)) \
           and has(fin, lambda st: isinstance(st, Block) and st.parts[0][0] in ("TRY", "LOCK m DO")
                   and any(h == "FINALLY" for h, _, _ in st.parts)
                   or isinstance(st, Block) and st.parts[0][0] == "LOCK m DO"):
            # cm3's C back end fails (M3C.last_param) on a FINALLY clause with
            # a RETURN or EXIT and a TRY FINALLY or LOCK
            fin = self.inner(c.but(noexit=True, noreturn=True), 0, 2)
        mark = f'Snap("{self.tag()}")' if self.rnd.random() < 0.3 else f'T("{self.tag()}", 0)'
        return Block([("TRY", (), body), ("FINALLY", (mark,), fin)])

    def s_trye(self, ctx):
        pattern = self.rnd.choice(["E", "F", "EF", "E,F", "", "E", "F", "EF"])
        has_else = not pattern or self.rnd.random() < 0.3
        # the ELSE catches EXIT and RETURN out of the body; cm3's program
        # crashes when the TRY has named handlers as well
        no = bool(pattern) and has_else
        body = self.inner(ctx.but(noexit=ctx.noexit or no, noreturn=ctx.noreturn or no))
        if self.rnd.random() < 0.8:
            # mostly an exception that a handler of this TRY catches
            r = self.rnd.random()
            if r < 0.4 and "E" in pattern:
                x = "RAISE E"
            elif r < 0.8 and "F" in pattern:
                x = f"RAISE F({self.value(ctx, 'INTEGER')})"
            else:
                x = self.raise_text(ctx)
            body.append(Leaf(f"IF {self.cond(ctx)} THEN {x} END"))
        c = ctx.but()
        parts = [("TRY", (), body), ("EXCEPT", (), None)]
        if "E" in pattern and "," not in pattern:
            parts.append(("| E =>", (f'T("{self.tag()}", 0)',), self.inner(c, 0, 2)))
        if "F" in pattern and "," not in pattern:
            e = self.fresh("e")
            parts.append((f"| F ({e}) =>", (f'T("{self.tag()}", {e})',),
                          self.inner(c.adding(Var(e, "INTEGER", "stmt", writable=False)), 0, 2)))
        if pattern == "E,F":
            parts.append(("| E, F =>", (f'T("{self.tag()}", 0)',), self.inner(c, 0, 2)))
        if has_else:
            mark = f'Snap("{self.tag()}")' if self.rnd.random() < 0.3 else f'T("{self.tag()}", 0)'
            parts.append(("ELSE", (mark,), self.inner(c, 0, 2)))
        return Block(parts)

    def s_lock(self, ctx):
        return Block([("LOCK m DO", (), self.inner(ctx.but(lock=True)))])

    def s_with(self, ctx):
        names = []
        bindings = []
        for _ in range(2 if self.chance(0.25, "var") else 1):
            w = self.fresh("w")
            r = self.rnd.random()
            if r < 0.6:
                # not fields of a packed record (see the top)
                d = self.pick(ctx, ("INTEGER", "Sub", "Color", "Rec", "Arr5", "Arr3", "Open", "O", "O1", "TX",
                                    "CS", "SS", "BS", "Ch"), packed=False)
                if not d:
                    return None
                bindings.append(f"{w} = {d[0]}")
                names.append(Var(w, d[1], "stmt", d[2]))
            elif r < 0.8 or ctx.lock:
                t = self.rnd.choice(["INTEGER", "INTEGER", "Rec", "Arr5", "O", "TX", "SS"])
                v = self.obj_value(ctx, t, meths=False) if t == "O" else self.value(ctx, t)
                bindings.append(f"{w} = {v}")
                names.append(Var(w, self.vtype if t == "O" else t, "stmt", False))
            else:
                call = self.call_expr(ctx, ("INTEGER", "Sub", "Rec", "Arr5", "O"))
                if not call:
                    return None
                bindings.append(f"{w} = {call[0]}")
                names.append(Var(w, call[1], "stmt", False))
        return Block([(f"WITH {', '.join(bindings)} DO", (), self.inner(ctx.adding(*names)))])

    def s_copy(self, ctx):
        t = self.rnd.choice(["Rec", "Arr5", "Arr3"])
        d = self.pick(ctx, (t,), writable=True)
        if not d:
            return None
        return Leaf(f"{d[0]} := {self.value(ctx, t)}")

    def s_sub(self, ctx):
        a = self.pick(ctx, ARRAYS, writable=True)
        if not a:
            return None
        a = a[0]
        b = a if self.chance(0.4, "open") else (self.pick(ctx, ARRAYS) or (a,))[0]
        n, k1, k2 = self.fresh("w"), self.fresh("w"), self.fresh("w")
        return Block([(f"WITH {n} = 1 + ({self.iexpr(ctx, 1)}) MOD MIN(NUMBER({a}), NUMBER({b})), "
                       f"{k1} = ({self.iexpr(ctx, 1)}) MOD (NUMBER({a}) - {n} + 1), "
                       f"{k2} = ({self.iexpr(ctx, 1)}) MOD (NUMBER({b}) - {n} + 1) DO",
                       (f"SUBARRAY({a}, {k1}, {n}) := SUBARRAY({b}, {k2}, {n})",), [])])

    # ----------------------------------------------------------- calls

    def module_of(self, ctx):
        return "Main" if ctx.proc is None else ctx.proc.chain()[0].module

    def visible(self, ctx, p):
        if ctx.proc is None:
            ok = p.parent is None
        else:
            ok = p.parent is None or p.parent in ctx.proc.chain()
        # Main has no interface, and a private procedure is not in one
        if ok and p.parent is None and (p.module == "Main" or p.private) and self.module_of(ctx) != p.module:
            return False
        return ok

    def callables(self, ctx):
        """The procedures that the code of ctx may call (to a higher number,
        so that the calls do not form cycles)."""
        here = -1 if ctx.proc is None else ctx.proc.id
        return [p for p in self.procs if p.id > here and self.visible(ctx, p)]

    def fn_actuals(self, ctx):
        if "proc-param" in self.avoid:
            return []
        return ([p.name for p in self.procs if p.fn and self.visible(ctx, p)]
                + [v.name for v in ctx.scope if v.type == "Fn"])

    def args(self, ctx, p, self_call=False, skip=0):
        out = []
        chosen = []
        for f in p.formals[skip:]:
            if f is p.depth:
                out.append(f"{f.name} - 1" if self_call else str(self.rnd.randint(0, 2)))
                continue
            if f.type == "Fn":
                fs = self.fn_actuals(ctx)
                if not fs:
                    return None
                out.append(self.rnd.choice(fs))
                continue
            a = None
            if f.mode == "VAR":
                if chosen and self.chance(0.35, "var"):
                    a = self.related(ctx, f.type, chosen)
                if a is None:
                    if f.type == "Open":
                        a = self.open_actual(ctx, True)
                    else:
                        d = self.pick(ctx, (f.type,), writable=True, packed=False)
                        a = d and d[0]
                if a:
                    chosen.append((a, f.type))
            elif f.mode == "READONLY" and "readonly-ref" not in self.avoid and self.rnd.random() < 0.6:
                if chosen and self.chance(0.3, "var"):
                    a = self.related(ctx, f.type, chosen)
                if a is None and f.type != "Open":
                    # a field of a packed record cannot be passed by reference
                    d = self.pick(ctx, (f.type,), packed=False)
                    a = d and d[0]
            if a is None and f.type in SETS and f.type not in BIG and (f.mode == "READONLY" or f.type == "SS"):
                # not an operation on small sets, which the front end may fold
                # (see the top)
                a = self.sexpr(ctx, f.type, 0)
            if a is None:
                a = self.value(ctx, f.type)
            if a is None:
                return None
            out.append(a)
        return ", ".join(out)

    def related(self, ctx, t, chosen):
        """A designator of type t that overlaps one passed before."""
        cands = []
        for a, at in chosen:
            if at == t and t != "Open":
                cands.append(a)
            if at == "Rec":
                cands += {"INTEGER": [f"{a}.a", f"{a}.arr[{self.index(ctx, 3)}]"], "Sub": [f"{a}.b"],
                          "Open": [f"{a}.arr"]}.get(t, [])
            if at in ("Arr5", "Open") and t == "INTEGER":
                n = "5" if at == "Arr5" else f"NUMBER({a})"
                cands.append(f"{a}[{self.index(ctx, n)}]")
            if at in ("Arr5", "Open") and t == "Open":
                cands.append(a)
            if at in OBJ:
                cands += {"INTEGER": [f"{a}.a"], "Color": [f"{a}.c"]}.get(t, [])
                if at != "O" and at != "O3":
                    cands += {"Sub": [f"{a}.b"], "Rec": [f"{a}.r"], "INTEGER": [f"{a}.r.a"]}.get(t, [])
        return self.rnd.choice(cands) if cands else None

    def call_expr(self, ctx, results):
        """A call of a function with one of the results: (text, result type), or None."""
        cands = [p for p in self.callables(ctx) if p.result in results]
        fns = [v.name for v in ctx.scope if v.type == "Fn"] if "INTEGER" in results else []
        if fns and (not cands or self.rnd.random() < 0.3):
            return f"{self.rnd.choice(fns)}({self.value(ctx, 'INTEGER')})", "INTEGER"
        if not cands:
            return None
        p = self.rnd.choice(cands)
        a = self.args(ctx, p)
        if a is None:
            return None
        return f"{p.name}({a})", p.result

    def s_call(self, ctx):
        cands = self.callables(ctx)
        fns = [v.name for v in ctx.scope if v.type == "Fn"]
        me = ctx.proc
        if me is not None and me.recursive and self.rnd.random() < 0.3:
            a = self.args(ctx, me, self_call=True)
            if a is None:
                return None
            call = f"{me.name}({a})"
            if me.result:
                call = f"EVAL {call}"
            return Leaf(f"IF {me.depth.name} > 0 THEN {call} END")
        if fns and (not cands or self.rnd.random() < 0.25):
            call, result = f"{self.rnd.choice(fns)}({self.value(ctx, 'INTEGER')})", "INTEGER"
        else:
            if not cands:
                return None
            p = self.rnd.choice(cands)
            a = self.args(ctx, p)
            if a is None:
                return None
            call, result = f"{p.name}({a})", p.result
        return self.use_call(ctx, call, result)

    def use_call(self, ctx, call, result):
        """A statement with the call: the call itself, an assignment of its
        result, a trace of it or EVAL."""
        if result is None:
            return Leaf(call)
        r = self.rnd.random()
        if r < 0.5:
            d = self.pick(ctx, ("INTEGER",) if result == "INTEGER" else ("INTEGER", "Sub") if result == "Sub"
                          else (result,), writable=True, simple=True)
            if d:
                if result == "TX":
                    # texts stay short
                    return Leaf(f"{d[0]} := Text.Sub({call}, 0, 16)")
                return Leaf(f"{d[0]} := {call}")
        if r < 0.8:
            return Leaf(self.trace_of(call, result))
        return Leaf(f"EVAL {call}")

    def trace_of(self, call, result):
        """A statement that prints the result of a call."""
        if result == "TX":
            return f'PT("{self.tag()}", {call})'
        if result in SETS:
            return f'T("{self.tag()}", ORD({PROBE[result]} IN {call}))'
        sel = {"INTEGER": "", "Sub": "", "Rec": ".a", "Arr5": f"[{self.rnd.randrange(5)}]",
               "O": ".a"}[result]
        return f'T("{self.tag()}", {call}{sel})'

    def s_qcall(self, ctx):
        """A statement on the opaque type QT of the modules."""
        r = self.rnd.random()
        q = "gq" if self.rnd.random() < 0.6 else f"gqa[{self.index(ctx, 2)}]"
        if r < 0.3:
            return Leaf(f'T("{self.tag()}", {q}.inc({self.value(ctx, "INTEGER")}))')
        if r < 0.4:
            d = self.pick(ctx, ("INTEGER",), writable=True, simple=True)
            return d and Leaf(f"{d[0]} := {q}.inc({self.value(ctx, 'INTEGER')})")
        if r < 0.5:
            return Leaf(f'T("{self.tag()}", {q}.get())')
        if r < 0.7:
            src = self.rnd.choice(["NewQ", "NewQ2", "NEW(QT, a := ", "NEW(QT2, w := ", "gq", "gqa[0]", "gqa[1]"]
                                  + ["NEW(QT3, v := "] * 2 * (self.module_of(ctx) == "Main"))
            if src.startswith("N"):
                src += ("(" if src.startswith("NewQ") else "") + self.value(ctx, "INTEGER") + ")"
            return Leaf(f"{q} := {src}")
        if r < 0.85:
            x = self.fresh("q")
            return Leaf(f'TYPECASE {q} OF QT2 ({x}) => T("{self.tag()}", {x}.w); '
                        f'{x}.w := ({x}.w + {self.value(ctx, "INTEGER")}) MOD 997 '
                        f'| QT ({x}) => T("{self.tag()}", {x}.a) END')
        if r < 0.93:
            return Leaf(f'T("{self.tag()}", ORD(ISTYPE({q}, QT2)))')
        return Leaf(f'IF ISTYPE({q}, QT2) THEN T("{self.tag()}", NARROW({q}, QT2).w) END')

    # --------------------------------------------------- objects, references

    def impl_of(self, ctx):
        """The method procedure around the code of ctx, or None."""
        if ctx.proc is None:
            return None
        return next((p for p in reversed(ctx.proc.chain()) if p.impl), None)

    def s_mcall(self, ctx):
        here = -1 if ctx.proc is None else ctx.proc.id
        impl = self.impl_of(ctx)
        r = self.rnd.random()
        if impl and r < 0.3:
            # a call of the method that this one overrides
            meth, t = impl.impl
            par = PARENT.get(t)
            if par and meth in BIND[par]:
                target = self.byname[BIND[par][meth]]
                if target.id > here:
                    a = self.args(ctx, target, skip=1)
                    if a is None:
                        return None
                    return self.use_call(ctx, f"{par}.{meth}(self{', ' + a if a else ''})", MSIG[meth][1])
        meths = [mt for mt in MSIG if all(self.byname[n].id > here for n, m2, _ in IMPLS if m2 == mt)]
        if not meths:
            return None
        meth = self.rnd.choice(meths)
        impl0 = self.byname[next(n for n, m2, _ in IMPLS if m2 == meth)]
        a = self.args(ctx, impl0, skip=1)
        if a is None:
            return None
        types = tuple(t for t in OBJ if meth in BIND[t])
        if r < 0.45:
            # T.m: the procedure of the method of type T
            t = self.rnd.choice(types)
            recv = self.obj_value(ctx, t, self.rnd.random() < 0.2)
            return self.use_call(ctx, f"{t}.{meth}({recv}{', ' + a if a else ''})", MSIG[meth][1])
        if r < 0.55 and meth != "k":
            d = self.pick(ctx, ("O2",))
            if d:
                s = self.use_call(ctx, f"{d[0]}.nx.{meth}({a})", MSIG[meth][1])
                return Leaf(f"IF {d[0]}.nx # NIL THEN {s.text} END")
        if r < 0.6:
            recv = f"NEW({self.rnd.choice(types)})"
        else:
            d = self.pick(ctx, types)
            if not d:
                return None
            recv = d[0]
        return self.use_call(ctx, f"{recv}.{meth}({a})", MSIG[meth][1])

    def s_oassign(self, ctx):
        r = self.rnd.random()
        if r < 0.55:
            d = self.pick(ctx, OBJ, writable=True)
            if not d:
                return None
            return Leaf(f"{d[0]} := {self.obj_value(ctx, d[1])}")
        if r < 0.65:
            d = self.pick(ctx, ("O2",))
            if not d:
                return None
            v = "NIL" if self.rnd.random() < 0.3 else self.obj_value(ctx, "O")
            return Leaf(f"{d[0]}.nx := {v}")
        if r < 0.8:
            t = self.rnd.choice(["RI", "RR"])
            d = self.pick(ctx, (t,), writable=True)
            if not d:
                return None
            if self.rnd.random() < 0.4:
                return Leaf(f"{d[0]} := {self.pick(ctx, (t,))[0]}")
            w = self.fresh("w")
            it = "INTEGER" if t == "RI" else "Rec"
            return Block([(f"WITH {w} = NEW({TEXT[t]}) DO", (f"{w}^ := {self.value(ctx, it)}", f"{d[0]} := {w}"),
                           [])])
        d = self.pick(ctx, ("Any",), writable=True)
        if not d:
            return None
        s = self.rnd.random()
        if s < 0.15:
            v = "NIL"
        elif s < 0.3:
            v = self.obj_value(ctx, self.rnd.choice(OBJ))
        else:
            v = self.pick(ctx, OBJ + REFS + ("TX",))[0]
        return Leaf(f"{d[0]} := {v}")

    def s_list(self, ctx):
        r = self.rnd.random()
        d = self.pick(ctx, ("L",), writable=True)
        if r < 0.3 and d:
            return Leaf(f"{d[0]} := NEW(L, v := {self.value(ctx, 'INTEGER')}, next := {d[0]})")
        if r < 0.45 and d:
            return Leaf(f"IF {d[0]} # NIL THEN {d[0]} := {d[0]}.next END")
        if r < 0.55 and d:
            return Leaf(f"IF {d[0]} # NIL THEN {d[0]}.v := {self.value(ctx, 'INTEGER')} END")
        if r < 0.75:
            q = self.pick(ctx, ("LN",))
            if q:
                if self.rnd.random() < 0.5:
                    return Leaf(f"{q[0]}.next := NEW(L, v := {self.value(ctx, 'INTEGER')}, next := {q[0]}.next)")
                return Leaf(f"IF {q[0]}.next # NIL THEN {q[0]}.next := {q[0]}.next.next END")
        if not d:
            return None
        if r < 0.9:
            s = self.pick(ctx, ("L", "LN"))
            return Leaf(f"{d[0]} := {s[0] if s and self.rnd.random() < 0.8 else 'NIL'}")
        a = self.pick(ctx, ("Any",))
        return a and Leaf(f"IF ISTYPE({a[0]}, L) THEN {d[0]} := NARROW({a[0]}, L) END")

    def s_walk(self, ctx):
        """A walk over at most a few nodes of a list: q is the next node, p
        the current one."""
        if ctx.proc is None:
            return None
        s = self.pick(ctx, ("L", "LN"))
        if not s:
            return None
        p, q = self.fresh("q"), self.fresh("q")
        ctx.proc.walkers += [p, q]
        c = self.counter(ctx)
        body = self.inner(ctx.adding(Var(p, "LN", "stmt", False)).but(loop=True, noexit=False), 0, 3)
        return Block([(f"WHILE {q} # NIL AND {c} < {self.rnd.randint(1, 6)} DO",
                       (f"INC({c})", f"{p} := {q}", f"{q} := {q}.next", f'T("{self.tag()}", {p}.v)'), body)],
                     pre=[f"{c} := 0", f"{q} := {s[0]}"])

    def s_tcase(self, ctx):
        c = ctx.but()
        if self.rnd.random() < 0.4:
            d = self.pick(ctx, ("Any",))
            if not d:
                return None
            subj, cases, st = d[0], dict(ANYCASE), None
            guarded = wrap = self.rnd.random() < 0.4
        else:
            subj = self.obj_value(ctx, "O", self.rnd.random() < 0.15, meths=False)
            st = self.vtype
            cases = {t: t for t in SUBS[st]}
            guarded, wrap = True, False
        types = list(cases)
        self.rnd.shuffle(types)
        types = types[:self.rnd.randint(1, len(types))]
        arms = []
        i = 0
        while i < len(types):
            n = 2 if i + 1 < len(types) and self.rnd.random() < 0.25 else 1
            arms.append(types[i:i + n])
            i += n
        if not guarded:
            # NIL is in the first case whose type contains it
            if self.rnd.random() < 0.7:
                arms.insert(0, ["NULL"])
            elif len(arms[0]) == 1:
                arms[0] = arms[0] + ["NULL"]
        parts = [(f"TYPECASE {subj} OF", (), None)]
        for a in arms:
            names = []
            if len(a) == 1 and a[0] != "NULL" and self.rnd.random() < 0.7:
                x = self.fresh("x")
                names = [Var(x, cases[a[0]], "stmt", False)]
                head = f"| {a[0]} ({x}) =>"
            else:
                head = f"| {', '.join(a)} =>"
            parts.append((head, (f'T("{self.tag()}", 0)',), self.inner(c.adding(*names), 0, 2)))
        # a TYPECASE that no case matches is an error unless it has ELSE
        if self.rnd.random() < 0.5 or st not in types:
            parts.append(("ELSE", (f'T("{self.tag()}", 0)',), self.inner(c, 0, 2)))
        b = Block(parts)
        if wrap:
            return Block([(f"IF {subj} # NIL THEN", (), [b])])
        return b

    def s_narrow(self, ctx):
        if self.rnd.random() < 0.35:
            d = self.pick(ctx, ("Any",))
            if not d:
                return None
            subj = d[0]
            t = self.rnd.choice(list(ANYCASE))
            tt = ANYCASE[t]
            guard = f"{subj} # NIL AND ISTYPE({subj}, {t})"
        else:
            d = self.pick(ctx, ("O", "O1"))
            if not d:
                return None
            subj = d[0]
            t = tt = self.rnd.choice(SUBS[d[1]][1:])
            guard = f"ISTYPE({subj}, {t})"
        if self.rnd.random() < 0.3:
            # a field of the narrowed reference
            if tt in OBJ:
                f, ft = self.rnd.choice([x for x in FIELDS[tt] if x[1] in ("INTEGER", "Sub")])
                d2 = self.pick(ctx, ("INTEGER",), writable=True, simple=True)
                if d2:
                    return Leaf(f"IF {guard} THEN {d2[0]} := NARROW({subj}, {t}).{f} END")
        w = self.fresh("w")
        body = self.inner(ctx.but().adding(Var(w, tt, "stmt", False)))
        return Block([(f"IF {guard} THEN", (), [Block([(f"WITH {w} = NARROW({subj}, {t}) DO", (), body)])])])

    def s_sassign(self, ctx):
        d = self.pick(ctx, tuple(SETS), writable=True)
        if not d:
            return None
        t = d[1]
        if self.rnd.random() < 0.4:
            return Leaf(f"{d[0]} := {d[0]} {self.rnd.choice(['+', '-'])} {t}{{{self.value(ctx, SETS[t])}}}")
        return Leaf(f"{d[0]} := {self.sexpr(ctx, t, 2)}")

    def s_sfor(self, ctx):
        """A loop over the elements of a set."""
        d = self.pick(ctx, tuple(SETS))
        if not d:
            return None
        t = d[1]
        lo, hi, vt = {"CS": ("FIRST(Color)", "LAST(Color)", "FE"), "SS": ("FIRST(Sub)", "LAST(Sub)", "FI"),
                      "BS": ("0", "99", "FI"), "CHS": ("'a'", "'z'", "FCh")}[t]
        if t == "CHS" and self.rnd.random() < 0.3:
            lo, hi = "FIRST(CHAR)", "LAST(CHAR)"
        i = self.fresh("i")
        body = self.inner(ctx.adding(Var(i, vt, "stmt", False)).but(loop=True, noexit=False), 0, 2)
        test = Block([(f"IF {i} IN {d[0]} THEN", (f'T("{self.tag()}", {ival(i, vt)})',), body)])
        return Block([(f"FOR {i} := {lo} TO {hi} DO", (), [test])])

    def s_tassign(self, ctx):
        r = self.rnd.random()
        if r < 0.5:
            d = self.pick(ctx, ("TX",), writable=True)
            if not d:
                return None
            e = f"{d[0]} & {self.texpr(ctx, 1)}" if self.rnd.random() < 0.3 else self.texpr(ctx, 2)
            # texts stay short
            return Leaf(f"{d[0]} := Text.Sub({e}, {self.rnd.choice([0, 0, 0, 1, 2])}, 16)")
        if r < 0.65:
            d = self.pick(ctx, ("Ch",), writable=True)
            return d and Leaf(f"{d[0]} := {self.cexpr(ctx)}")
        if r < 0.8:
            return Leaf(f'PT("{self.tag()}", {self.texpr(ctx, 2)})')
        d = self.pick(ctx, ("Chars",), writable=True)
        if not d:
            return None
        if r < 0.9:
            # cm3 copies the characters after the end of a text made by
            # Text.Sub into the rest of the array; the text is long enough
            return Leaf(f'Text.SetChars({d[0]}, {self.texpr(ctx, 1)} & "uvwxyz")')
        return Leaf(f"{d[0]}[{self.index(ctx, 6)}] := {self.cexpr(ctx)}")

    def s_tloop(self, ctx):
        """A loop over the characters of a text."""
        i, w = self.fresh("i"), self.fresh("w")
        # bound to a value: the body may change the variables of the text
        e = f'{self.texpr(ctx, 1)} & ""'
        down = self.rnd.random() < 0.3
        rng = f"Text.Length({w}) - 1 TO 0 BY -1" if down else f"0 TO Text.Length({w}) - 1"
        body = self.inner(ctx.adding(Var(i, "FI", "stmt", False), Var(w, "TX", "stmt", False))
                          .but(loop=True, noexit=False), 0, 2)
        loop = Block([(f"FOR {i} := {rng} DO", (f'T("{self.tag()}", ORD(Text.GetChar({w}, {i})))',), body)])
        return Block([(f"WITH {w} = {e} DO", (), [loop])])

    # --------------------------------------------------------- program

    def scope(self, p):
        seen = set()
        res = []
        for a in reversed(p.chain()):
            for v in a.formals + a.locals:
                if v.name not in seen:
                    seen.add(v.name)
                    res.append(v)
        res += self.globals
        if self.layout:
            res += self.layout.private(p.chain()[0].module)
        return res

    def structure(self):
        nest = (0.6, 0.25) if "uplevel" in self.focus else (0.4, 0.1)

        def make(parent, level, impl=None):
            p = Proc(len(self.procs), parent)
            self.procs.append(p)
            if parent:
                parent.children.append(p)
            if impl:
                p.name, meth, t = impl
                p.impl = (meth, t)
            if level < 3:
                r = self.rnd.random()
                for _ in range(2 if r < nest[1] else 1 if r < nest[0] else 0):
                    make(p, level + 1)

        # the method procedures come between the other procedures, so that
        # those before them call methods and those after them are called
        # by them
        n = self.rnd.randint(3, 6)
        k = self.rnd.randint(1, n)
        for i in range(n):
            if i == k:
                for impl in IMPLS:
                    make(None, 2, impl)
            make(None, 1)
        if k == n:
            for impl in IMPLS:
                make(None, 2, impl)
        self.byname = {p.name: p for p in self.procs}
        if self.layout:
            L = self.layout
            for p in self.procs:
                if p.parent is None:
                    p.module = L.rnd.choice(L.impl_homes if p.impl else L.homes)
                    p.private = not p.impl and p.module != "Main" and L.rnd.random() < 0.25
                else:
                    p.module = p.chain()[0].module
        for p in self.procs:
            if p.impl:
                meth, t = p.impl
                p.formals = [Var("self", t, p, False)] + [Var(f, ft, p, mode != "READONLY", mode)
                                                          for f, ft, mode in MSIG[meth][0]]
                p.result = MSIG[meth][1]
                continue
            p.fn = self.rnd.random() < 0.2
        if "proc-param" in self.avoid or not any(p.fn and p.parent is None for p in self.procs):
            fn_ok = False
        else:
            fn_ok = True
        for p in self.procs:
            if p.impl:
                pass
            elif p.fn:
                p.formals = [Var("x", "INTEGER", p, True)]
                p.result = "INTEGER"
            else:
                p.recursive = self.rnd.random() < 0.2
                if p.recursive:
                    p.depth = Var(self.fresh("d"), "INTEGER", p, False)
                    p.formals.append(p.depth)
                types = ["INTEGER"] * 4 + ["Sub", "Rec", "Rec", "Arr5", "Open", "Open"]
                if "open" in self.focus:
                    types += ["Open"] * 4
                if "copy" in self.focus:
                    types += ["Rec", "Arr5"] * 2
                if fn_ok:
                    types += ["Fn"] * (3 if "uplevel" in self.focus else 1)
                types += ["O", "O1", "O2"] * (2 if "obj" in self.focus else 1)
                types += ["CS", "SS", "BS", "CHS"] * (2 if "set" in self.focus else 1)
                types += ["TX", "TX", "Ch"] * (2 if "text" in self.focus else 1)
                for _ in range(self.rnd.randint(0, 4)):
                    t = self.rnd.choice(types)
                    mode = "" if t == "Fn" else self.rnd.choices(["", "VAR", "READONLY"], [45, 35, 20])[0]
                    p.formals.append(Var(self.fresh("v"), t, p, mode != "READONLY", mode))
                p.result = self.rnd.choice([None, None, None, "INTEGER", "Sub", "Rec", "Arr5", "O", "TX",
                                            "SS", "BS"])
            self.add_locals(p)
        if self.layout:
            # the procedures that the variables fi of the modules get
            for m in self.layout.mods:
                fns = [p.name for p in self.procs if p.parent is None and p.fn and p.module != "Main"
                       and (not p.private or p.module == m["name"])]
                m["f"] = self.layout.rnd.choice([f"Id{m['i']}"] * 2 + fns)

    def add_locals(self, p):
        for _ in range(self.rnd.randint(0, 3)):
            t = self.rnd.choice(["INTEGER", "INTEGER", "Sub", "Color", "Rec", "Arr5", "O", "O1", "L", "TX",
                                 "CS", "SS", "Ch"])
            init = {"INTEGER": self.literal(), "Sub": lit(self.rnd.randint(-5, 20)),
                    "Color": self.rnd.choice(COLORS), "Rec": self.rnd.choice(["gr", "gr2"]),
                    "Arr5": "ga", "O": self.rnd.choice(["gob", "gob1", "gob2", "gob3", "goa[1]"]),
                    "O1": self.rnd.choice(["gob1", "gob2", "NEW(O1)"]),
                    "L": self.rnd.choice(["gl", "NIL", "NEW(L, v := 5, next := gl)"]),
                    "TX": self.rnd.choice(TLITS + ["gt", "gt2"]),
                    "CS": self.rnd.choice(["gcs", "CS{}", "CS{Color.Green .. Color.Gray}"]),
                    "SS": self.rnd.choice(["gss", "SS{-5, 0 .. 3, 20}", "gsa[2]"]),
                    "Ch": self.rnd.choice(["'q'", "gch", "gca[0]"])}[t]
            p.locals.append(Var(self.fresh("v"), t, p, True, init=init))

    def generate(self):
        self.structure()
        for p in self.procs:
            ctx = Ctx(p, self.scope(p))
            p.body = self.block(ctx, self.rnd.randint(1, 4) if p.impl else self.rnd.randint(3, 8))
            if p.result:
                p.ret = "RETURN " + self.retval(ctx)
            p.wrap = self.rnd.random() < 0.4
        init = self.init()
        ctx = Ctx(None, list(self.globals))
        main = []
        for p in self.procs:
            if not self.visible(ctx, p):
                continue
            for _ in range(self.rnd.randint(0, 1) if p.impl else self.rnd.randint(1, 2)):
                a = self.args(ctx, p)
                if a is None:
                    continue
                call = f"{p.name}({a})"
                if p.result:
                    call = self.trace_of(call, p.result)
                main.append(self.guarded(Leaf(call)))
        # calls of methods
        for _ in range(self.rnd.randint(1, 4 if "obj" in self.focus else 2)):
            st = self.s_mcall(ctx)
            if st:
                main.append(self.guarded(st))
        # other values for each round
        r = self.rnd
        rnd = [f"gi{k} := (gi{k} + round * {r.randint(1, 50)}) MOD 997" for k in range(4)]
        rnd.append(f"gs := (gs + round * {r.randint(1, 7)}) MOD 26 - 5")
        rnd.append("gc := VAL((ORD(gc) + round) MOD 4, Color)")
        anys = ["NIL", "gob", "gob1", "gob2", "gob3", "gl", "gri", "grr", "goa[round MOD 3]", "gt"]
        r.shuffle(anys)
        rnd.append("CASE round MOD 3 OF " + " | ".join(f"{k} => gany := {anys[k]}" for k in range(3)) + " ELSE END")
        if r.random() < 0.5:
            rnd.append("goa[round MOD 3] := goa[(round + 1) MOD 3]")
        rnd.append(f"gt := Text.Sub(gt & Fmt.Int(round * {r.randint(1, 9)}), {r.randint(0, 2)}, 16)")
        rnd.append(f"gch := VAL(ORD('a') + (ORD(gch) + round * {r.randint(1, 5)}) MOD 26, CHAR)")
        rnd.append(f"gss := gss / SS{{round, round * {r.randint(2, 6)}}}")
        rnd.append(f"gbs := gbs + BS{{round * {r.randint(5, 30)} .. round * {r.randint(20, 33)}}}")
        if self.layout:
            rnd.append("IF round = 2 THEN gqa[1] := NEW(QT3, a := gi0, v := gi1) END")
        return Program(self.seed, self.procs, init, main, rnd, self.layout)

    def guarded(self, st):
        """The statement st of the main body in a TRY that handles E and F."""
        e = self.fresh("e")
        return Block([("TRY", (), [st]), ("EXCEPT", (), None),
                      ("| E =>", (f'T("{self.tag()}", 0)',), []),
                      (f"| F ({e}) =>", (f'T("{self.tag()}", {e})',), [])],
                     pre=[] if self.rnd.random() < 0.7 else [f'Snap("{self.tag()}")'])

    def init(self):
        r = self.rnd
        L = [f"gi{k} := {self.literal()}" for k in range(4)]
        L.append(f"gs := {lit(r.randint(-5, 20))}")
        L.append(f"gc := {r.choice(COLORS)}")
        L.append("ga := Arr5{" + ", ".join(self.literal() for _ in range(5)) + "}")
        for g in ("gr", "gr2"):
            L.append(f"{g} := Rec{{a := {self.literal()}, b := {lit(r.randint(-5, 20))}, c := {r.choice(COLORS)}, "
                     f"arr := Arr3{{{self.literal()}, {self.literal()}, {self.literal()}}}}}")
        L.append(f"gp := Pk{{x := {r.randint(0, 31)}, y := {lit(r.randint(-4, 3))}, c := {r.choice(COLORS)}, "
                 f"z := {r.randint(0, 500)}, s := CS{{{r.choice(COLORS)}}}}}")
        n = r.randint(1, 7)
        L.append(f"go := NEW(REF ARRAY OF INTEGER, {n})")
        L += [f"go[{k}] := {self.literal()}" for k in range(n)]
        rows, cols = r.randint(1, 3), r.randint(1, 4)
        L.append(f"go2 := NEW(REF ARRAY OF ARRAY OF INTEGER, {rows}, {cols})")
        L += [f"go2[{i}, {j}] := {self.literal()}" for i in range(rows) for j in range(cols)]
        L.append("m := NEW(MUTEX)")
        L.append(f"fuel := {FUEL}")
        # objects (never NIL) and references
        objs = []
        for g, t in (("gob", "O"), ("gob1", "O1"), ("gob2", "O2"), ("gob3", "O3")):
            t2 = r.choice(SUBS[t])
            inits = []
            for f, ft in FIELDS[t2]:
                if ft in ("INTEGER", "Sub") and r.random() < 0.4:
                    inits.append(f"{f} := {lit(r.randint(-5, 20))}")
                elif ft == "ONIL" and objs and r.random() < 0.5:
                    inits.append(f"{f} := {r.choice(objs)}")
            if "new-methods" not in self.avoid and r.random() < 0.25:
                meth = r.choice(list(BIND[t2]))
                inits.append(f"{meth} := {r.choice([n for n, mt, st in IMPLS if mt == meth and isub(t2, st)])}")
            L.append(f"{g} := NEW({t2}{''.join(', ' + i for i in inits)})")
            objs.append(g)
        for k in range(3):
            L.append(f"goa[{k}] := {r.choice(objs + ['NEW(O)', 'NEW(O3, d := 77)'])}")
        L.append("gl := NIL")
        for _ in range(r.randint(0, 4)):
            L.append(f"gl := NEW(L, v := {self.literal()}, next := gl)")
        L.append("gri := NEW(REF INTEGER)")
        L.append(f"gri^ := {self.literal()}")
        L.append("grr := NEW(REF Rec)")
        L.append(f"grr^ := {r.choice(['gr', 'gr2'])}")
        L.append(f"gany := {r.choice(['NIL', 'gob', 'gob1', 'gob2', 'gl', 'gri', 'grr', 'gt'])}")
        # sets and texts
        L.append("gcs := CS{" + ", ".join(c for c in COLORS if r.random() < 0.5) + "}")
        for g in ("gss", "gsa[0]", "gsa[1]", "gsa[2]"):
            L.append(f"{g} := SS{{" + ", ".join(lit(r.randint(-5, 20)) for _ in range(r.randint(0, 4))) + "}")
        a = r.randint(0, 90)
        L.append(f"gbs := BS{{{a} .. {a + r.randint(0, 40) if a < 60 else 99}, {r.randint(0, 99)}}}")
        L.append(f"gchs := CHS{{'a' .. '{r.choice('cfk')}', '{r.choice('xyz')}', VAL({r.randint(0, 255)}, CHAR)}}")
        L.append(f"gt := {r.choice(TLITS)}")
        L.append(f"gt2 := {r.choice(TLITS)}")
        L.append(f"gch := '{r.choice('abcdefgh')}'")
        L.append("gca := Chars{" + ", ".join(f"'{r.choice('abcxyz09')}'" for _ in range(6)) + "}")
        return L


def generate(seed, focus=(), avoid=()):
    return Gen(seed, focus, avoid).generate()


# ---------------------------------------------------------------- running

def makefile(files, name):
    names = [n for n, _ in files]
    L = ["% Generated by tools/prog_fuzz.py.", 'import ("libm3")']
    for n in names:
        b, ext = n.rsplit(".", 1)
        if ext == "i3":
            L.append(f'module ("{b}")' if b + ".m3" in names else f'interface ("{b}")')
        elif b + ".i3" not in names:
            L.append(f'implementation ("{b}")')
    L.append(f'program ("{name}")')
    return "\n".join(L) + "\n"


def write_files(d, files):
    d.mkdir(parents=True, exist_ok=True)
    for n, t in files:
        (d / n).write_text(t, newline="\n")


def write_package(d, files):
    src = d / "src"
    write_files(src, files)
    (src / "m3makefile").write_text(makefile(files, d.name), newline="\n")
    return src


def modules(files):
    """The modules of the files."""
    return [n for n, _ in files if n.endswith(".m3")]


def split_limit(seed, args):
    return args.split or random.Random(seed * 7919 + 17).randint(150, 600)


def lines_of(r):
    return r.stdout.decode("latin-1").replace("\r\n", "\n").splitlines()


def first_diff(a, b, text, names):
    """A description of the first difference of the outputs a and b."""
    n = next((i for i in range(min(len(a), len(b))) if a[i] != b[i]), min(len(a), len(b)))
    la = a[n] if n < len(a) else "(end)"
    lb = b[n] if n < len(b) else "(end)"
    out = [f"line {n + 1}: {names[0]} {la!r}", f"        {names[1]} {lb!r}"]
    for line in (la, lb):
        tag = line.split(" ")[0]
        if tag and tag != "(end)":
            for k, s in enumerate(text.splitlines()):
                if f'"{tag}"' in s:
                    out.append(f"  printed by line {k + 1}: {s.strip()}")
                    return out
    return out


def run(files, name, limit, args, work):
    """Compiles and runs the program of the source files in the package
    work/name, with m3j (normally and split at limit) and with cm3:
    (status, details)."""
    text = joined(files)
    src = write_package(work / name, files)
    sdir = work / name / "split"
    write_files(sdir, files)
    try:
        j, err = run_m3j(src, args, files=modules(files))
    except subprocess.TimeoutExpired:
        return "TIMEOUT", ["m3j"]
    if j is None:
        if not args.no_cm3:
            try:
                c, _ = run_cm3(src, name, args.timeout)
            except subprocess.TimeoutExpired:
                c = True
            if c is None:
                return "BOTH-FAILED", err
        return "M3J-FAILED", err
    try:
        s, serr = run_m3j(sdir, args, [f"-Dm3j.maxMethod={limit}"], modules(files))
    except subprocess.TimeoutExpired:
        return "TIMEOUT", [f"m3j, split at {limit}"]
    for r in (j, s):
        if r is not None and b"VerifyError" in r.stderr:
            return "VERIFY", r.stderr.decode("latin-1").strip().splitlines()[:5]
    problems = []
    status = None
    if not args.no_cm3:
        try:
            c, cerr = run_cm3(src, name, args.timeout)
        except subprocess.TimeoutExpired:
            return "TIMEOUT", ["cm3"]
        if c is None:
            return "CM3-FAILED", cerr
        if lines_of(c) != lines_of(j) or c.returncode != j.returncode:
            status = "FAIL"
            problems += [f"exit status: cm3 {c.returncode}, m3j {j.returncode}"] if c.returncode != j.returncode else []
            problems += first_diff(lines_of(c), lines_of(j), text, ("cm3", "m3j"))
            if j.stderr:
                problems.append("m3j: " + j.stderr.decode("latin-1").strip()[:300])
    if s is None:
        status = status or "SPLIT-FAIL"
        problems += [f"split at {limit}: compile failed"] + serr
    elif lines_of(s) != lines_of(j) or s.returncode != j.returncode:
        status = status or "SPLIT-FAIL"
        problems.append(f"split at {limit}:")
        problems += first_diff(lines_of(j), lines_of(s), text, ("normal", "split "))
        if s.stderr:
            problems.append("split: " + s.stderr.decode("latin-1").strip()[:300])
    elif j.returncode != 0 or j.stderr:
        problems.append(f"m3j exit status {j.returncode}: " + j.stderr.decode("latin-1").strip()[:300])
        status = status or ("PASS" if not args.no_cm3 else "M3J-FAILED")
    return status or "PASS", problems


def check_seed(seed, args, work):
    files = generate(seed, args.focus, args.avoid).files()
    status, detail = run(files, f"p{seed}", split_limit(seed, args), args, work)
    return seed, status, detail


# ------------------------------------------------------------- reducer

class Reducer:
    """Makes the program of a seed smaller while test(text) holds: removes
    procedures, statements, arms of CASE and handlers, replaces compound
    statements by one of their statement lists and conditional statements
    by their statement, and leaves out the trace of procedure entries."""

    def __init__(self, prog, test, verbose=False):
        self.prog, self.test, self.verbose = prog, test, verbose
        self.cache = {}
        self.tries = 0

    def ok(self):
        text = self.prog.text()
        if text not in self.cache:
            self.tries += 1
            self.cache[text] = self.test(self.prog.files())
            if self.verbose:
                print(f"  try {self.tries}: {len(text.splitlines())} lines, "
                      f"{'kept' if self.cache[text] else 'rejected'}", flush=True)
        return self.cache[text]

    def lists(self):
        out = [self.prog.main]

        def walk(stmts):
            for st in stmts:
                if isinstance(st, Block):
                    for _, _, body in st.parts:
                        if body is not None:
                            out.append(body)
                            walk(body)
        walk(self.prog.main)
        for p in self.prog.procs:
            if not any(a.removed for a in p.chain()):
                out.append(p.body)
                walk(p.body)
        return out

    def reduce_list(self, L):
        chunk = max(1, len(L) // 2)
        while chunk >= 1:
            i = 0
            while i < len(L):
                saved = L[i:i + chunk]
                del L[i:i + chunk]
                if not self.ok():
                    L[i:i] = saved
                    i += chunk
            chunk //= 2

    def simplify(self, L):
        i = 0
        while i < len(L):
            st = L[i]
            if isinstance(st, Leaf):
                t = st.text
                if t.startswith("IF ") and t.endswith(" END") and " THEN " in t:
                    L[i] = Leaf(t[t.index(" THEN ") + 6:-4])
                    if not self.ok():
                        L[i] = st
                i += 1
                continue
            done = False
            for _, _, body in list(st.parts):
                if body is not None:
                    L[i:i + 1] = body
                    if self.ok():
                        done = True
                        break
                    L[i:i + len(body)] = [st]
            if done:
                continue
            k = 0
            while k < len(st.parts):
                head, fixed, body = st.parts[k]
                if head.startswith(("|", "ELSE", "ELSIF")):
                    del st.parts[k]
                    if self.ok():
                        continue
                    st.parts.insert(k, (head, fixed, body))
                if fixed and all(f.startswith(("T(", "Snap(")) for f in fixed):
                    st.parts[k] = (head, (), body)
                    if not self.ok():
                        st.parts[k] = (head, fixed, body)
                k += 1
            if st.pre and all(x.startswith("Snap(") for x in st.pre):
                pre, st.pre = st.pre, []
                if not self.ok():
                    st.pre = pre
            i += 1

    def reduce(self):
        if not self.ok():
            raise SystemExit("the program does not show the difference")
        while True:
            before = self.prog.text()
            for p in reversed(self.prog.procs):
                if not p.removed:
                    p.removed = True
                    if not self.ok():
                        p.removed = False
            for L in self.lists():
                self.reduce_list(L)
            for L in self.lists():
                self.simplify(L)
            for p in self.prog.procs:
                if not p.removed and not p.bare:
                    p.bare = True
                    if not self.ok():
                        p.bare = False
            if self.prog.text() == before:
                return before


def reduce_seed(seed, args, work):
    prog = generate(seed, args.focus, args.avoid)
    limit = split_limit(seed, args)
    status, detail = run(prog.files(), f"p{seed}", limit, args, work)
    print(f"{status} seed {seed}")
    for line in detail[:12]:
        print("  " + line)
    if status in ("PASS", "BOTH-FAILED"):
        raise SystemExit("nothing to reduce")
    if args.match and not any(args.match in d for d in detail):
        raise SystemExit(f"{args.match!r} is not in the details")
    if status == "SPLIT-FAIL":
        args.no_cm3 = True
    n = [0]

    def test(files):
        n[0] += 1
        name = f"p{seed}r{n[0]}"
        # only what the status needs
        if status == "CM3-FAILED":
            try:
                c, det = run_cm3(write_package(work / name, files), name, args.timeout)
            except subprocess.TimeoutExpired:
                return False
            st = "CM3-FAILED" if c is None else "PASS"
        elif status == "M3J-FAILED":
            try:
                j, det = run_m3j(write_package(work / name, files), args, files=modules(files))
            except subprocess.TimeoutExpired:
                return False
            if j is not None and j.returncode != 0:
                # an error at run time
                j, det = None, j.stderr.decode("latin-1").strip().splitlines()
            st = "M3J-FAILED" if j is None else "PASS"
        else:
            st, det = run(files, name, limit, args, work)
        return st == status and (not args.match or any(args.match in d for d in det))

    r = Reducer(prog, test, args.verbose)
    text = r.reduce()
    print(f"reduced in {r.tries} runs to {len(text.splitlines())} lines:")
    print(text)
    if args.work:
        files = prog.files()
        if len(files) == 1:
            out = args.work / f"p{seed}-reduced.m3"
            out.write_text(text, newline="\n")
        else:
            out = write_package(args.work / f"p{seed}-reduced", files)
        print(f"written to {out}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--seeds", default="1-10")
    ap.add_argument("--focus", default="")
    ap.add_argument("--avoid", default="")
    ap.add_argument("--show", type=int)
    ap.add_argument("--reduce", type=int)
    ap.add_argument("--match")
    ap.add_argument("--write", type=Path)
    ap.add_argument("--list", type=Path)
    ap.add_argument("--no-cm3", action="store_true")
    ap.add_argument("--split", type=int)
    ap.add_argument("--jobs", type=int, default=max(1, (os.cpu_count() or 2) // 2))
    ap.add_argument("--work", type=Path)
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
    ap.add_argument("--timeout", type=int, default=300)
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()
    args.focus = [f for f in args.focus.split(",") if f]
    args.avoid = [f for f in args.avoid.split(",") if f]
    for f in args.focus:
        if f not in FOCUS:
            ap.error(f"unknown focus {f} (one of {', '.join(FOCUS)})")
    for f in args.avoid:
        if f not in FEATURES:
            ap.error(f"unknown construct {f} (one of {', '.join(FEATURES)})")
    if args.show is not None:
        sys.stdout.write(generate(args.show, args.focus, args.avoid).text())
        return
    seeds = parse_seeds(args.seeds)
    if args.write:
        lines = []
        for seed in seeds:
            d = args.write / f"p{seed}"
            write_package(d, generate(seed, args.focus, args.avoid).files())
            if args.list:
                rel = d.resolve().relative_to(ROOT).as_posix()
                lines.append(f"fuzz/p{seed} {rel}")
        if args.list:
            args.list.write_text("# Random program tests (written by tools/prog_fuzz.py --write)\n"
                                 + "\n".join(lines) + "\n", newline="\n")
        print(f"wrote {len(seeds)} programs to {args.write}")
        return
    if not args.java:
        jdk = os.environ.get("JDK25")
        args.java = str(Path(jdk) / "bin" / "java") if jdk else "java"
    aot = Path(args.jar).with_suffix(".aot")
    args.aot = ["-XX:AOTCache=" + str(aot)] if aot.exists() else []
    work = (args.work or Path(tempfile.mkdtemp(prefix="m3j-pfuzz-"))).resolve()
    work.mkdir(parents=True, exist_ok=True)
    if args.reduce is not None:
        try:
            reduce_seed(args.reduce, args, work)
        finally:
            if not args.keep and not args.work:
                shutil.rmtree(work, ignore_errors=True)
        return
    counts = {}
    try:
        with concurrent.futures.ThreadPoolExecutor(args.jobs) as ex:
            for seed, status, detail in ex.map(lambda sd: check_seed(sd, args, work), seeds):
                counts[status] = counts.get(status, 0) + 1
                print(f"{status:11s} seed {seed}", flush=True)
                for line in detail[:args.verbose and 1000 or 12]:
                    print("  " + line)
    finally:
        if not args.keep and not args.work:
            shutil.rmtree(work, ignore_errors=True)
    print("  ".join(f"{k} {v}" for k, v in sorted(counts.items())))
    sys.exit(0 if set(counts) <= {"PASS", "BOTH-FAILED"} else 1)


if __name__ == "__main__":
    main()
