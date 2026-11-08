#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Random expression tests of the back end.

Usage:
  python3 tools/expr_fuzz.py [--seeds A-B] [--checks N] [--no-cm3] [--jobs N]
                             [--work DIR] [--keep] [--java JAVA] [--jar JAR]
                             [--timeout S] [-v]
  python3 tools/expr_fuzz.py --write DIR [--list FILE] [--seeds A-B] [--checks N]

For each seed, a Modula-3 program of N checks (default 400) is generated.
A check prints the value of a random expression over INTEGER, LONGINT,
BOOLEAN, REAL and LONGREAL: the arithmetic operators, DIV and MOD, ABS,
MIN and MAX, the procedures of Word and Long (unsigned arithmetic,
shifts, rotations, Extract and Insert), the comparisons, ORD, VAL,
FLOAT, TRUNC, ROUND, FLOOR and CEILING; the operands are global and local
variables, array elements, fields of a packed record and elements of a
packed array (BITS FOR subranges), results of procedure calls and
literals. Other checks store a value into a packed field, a subrange
variable or an element of a packed array, or change a variable with INC
and DEC, and print it.

A pair check prints an expression of literals twice: as it is (the front
end folds it to a constant) and with every literal passed through an
identity procedure (the compiled code computes it). The two lines must
be the same.

The generator evaluates every expression exactly and leaves out those
whose behavior is not defined in the same way by cm3 and m3j: overflow of
signed arithmetic (cm3's C back end makes it undefined behavior in C),
FIRST(INTEGER) DIV -1, conversions of floating-point values beyond the
range of INTEGER, results that are infinite or NaN, division by zero and
values outside the range of a variable. Errors of cm3 are avoided as
well, so that one of them does not change the values of the variables
for the rest of the program: its C back end writes the constant
FIRST(INTEGER) as -9223372036854775807-1 without parentheses (so that
x - FIRST(INTEGER) is off by 2), so that value is not a literal of
computed code; Insert with a width of 64 shifts by 64 in C, so that
width is not used; VAL(Long.RightShift(x, n), INTEGER) gets the sign of
x, so that expression is not used; MIN and MAX of a zero and a zero of
the other sign give either zero, depending on the code of the C
compiler, so these operands are not used; and the front end (cm3's as
well) fails an assertion when it folds Word.Divide or Word.Mod to an
unsigned value beyond LAST(INTEGER).

Each program is built and run with cm3 (on the PATH, with its C
compiler) and with m3j, and the outputs are compared line by line; with
--no-cm3 only m3j runs, and only the pair checks are compared. A
difference is printed with the check and its expression. For a value of
type INTEGER, LONGINT or BOOLEAN the generator knows the result: where m3j
gives that result and cm3 does not, the check is counted as an error of
cm3 (CM3-WRONG, such as the errors above), else as a failure of m3j
(FAIL). A program that both compilers reject is BOTH-FAILED.

--write DIR writes the programs as packages DIR/eSEED (src/m3makefile and
src/Main.m3) instead, for tools/diff_test.py; --list FILE writes the list
of those tests, named fuzz/eSEED. SEEDS is a number or a range A-B
(default 1-10). JAVA defaults to $JDK25/bin/java, else java from the PATH.
"""

import argparse
import concurrent.futures
import math
import os
import random
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import threading
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEP = ";" if os.name == "nt" else ":"
M64 = 1 << 64
IMIN = -(1 << 63)
IMAX = (1 << 63) - 1
# the limit of floating-point values converted to INTEGER (with a margin
# for the rounding of the model)
FMAX = float(1 << 62)


class Skip(Exception):
    """The expression has no defined value."""


def chk(v):
    if not IMIN <= v <= IMAX:
        raise Skip
    return v


def u(x):
    return x & (M64 - 1)


def s(x):
    x &= M64 - 1
    return x - M64 if x >> 63 else x


def f32(x):
    try:
        return struct.unpack("f", struct.pack("f", x))[0]
    except OverflowError:
        raise Skip


def fin(x):
    if math.isinf(x) or math.isnan(x):
        raise Skip
    return x


def int_to_f32(n):
    """n rounded to the nearest REAL (ties to even), exactly."""
    m = abs(n)
    bl = m.bit_length()
    if bl <= 24:
        r = float(m)
    else:
        sh = bl - 24
        q, rem = divmod(m, 1 << sh)
        half = 1 << (sh - 1)
        if rem > half or (rem == half and q & 1):
            q += 1
        r = float(q) * float(1 << sh)
    return -r if n < 0 else r


# the names of the variables and procedures of a program: an expression
# without them is a constant
VARIABLE = re.compile(r"\b(g[ILDRB]\d|gS|gC|arr|p\.|pa\[|lI\d|Id[ILBDR])")


def is_const(text):
    return VARIABLE.search(text) is None


def round_m3(x):
    # ties away from zero; a tie only matters for the range check
    return int(math.floor(x + 0.5)) if x >= 0 else -int(math.floor(-x + 0.5))


# ---------------------------------------------------------------- literals

def lit_int(v, t, rnd):
    suf = "L" if t == "L" else ""
    name = "LONGINT" if t == "L" else "INTEGER"
    if v == IMIN:
        return f"FIRST({name})"
    if v == IMAX and rnd.random() < 0.5:
        return f"LAST({name})"
    if rnd.random() < 0.2:
        # a based literal is the bit pattern of the value
        return f"16_{u(v):X}{suf}"
    if v < 0:
        return f"(-{-v}{suf})"
    return f"{v}{suf}"


def lit_float(x, t):
    e = "D" if t == "D" else "E"
    r = repr(abs(x))
    if "e" in r:
        mant, ex = r.split("e")
        ex = str(int(ex))
    else:
        mant, ex = r, "0"
    if "." not in mant:
        mant += ".0"
    text = f"{mant}{e}{ex}"
    return f"(-{text})" if math.copysign(1.0, x) < 0 else text


# ---------------------------------------------------------------- program state

PACKED = [("a", -4, 3, 3), ("b", 0, 31, 5), ("c", -4096, 4095, 13), ("e", -(1 << 39), (1 << 39) - 1, 40),
          ("f", 0, 3, 2)]
PA_LEN, PA_LO, PA_HI = 12, -16, 15
NG = {"I": 6, "L": 4, "D": 4, "R": 3, "B": 2}
NLOCAL = 3

INT_POOL = [0, 1, -1, 2, -2, 3, 5, 7, 10, -10, 100, -100, 255, 256, 1000, 65535, 65536,
            (1 << 31) - 1, -(1 << 31), 1 << 31, (1 << 32) - 1, 1 << 32, 1 << 40, 1 << 62,
            IMAX, IMIN, IMAX - 1, IMIN + 1, 123456789, -987654321]
FLOAT_POOL = [0.0, 1.0, -1.0, 0.5, -0.5, 1.5, 2.5, -2.5, 0.1, 0.3, 1e10, 1e-10, 3.141592653589793,
              2.0 ** 52, 2.0 ** 53, 1e15 + 0.5, 123.456, -7.75, 1e100, 1e-100, 4.9e-324]


class State:
    def __init__(self, rnd):
        self.g = {}
        for t, n in NG.items():
            for k in range(n):
                self.g[f"g{t}{k}"] = self.initial(t, rnd)
        for k in range(4):
            self.g[f"arr[{k}]"] = self.initial("I", rnd)
        for name, lo, hi, _ in PACKED:
            self.g["p." + name] = rnd.randint(lo, hi)
        self.g["p.d"] = rnd.random() < 0.5
        for k in range(PA_LEN):
            self.g[f"pa[{k}]"] = rnd.randint(PA_LO, PA_HI)
        self.g["gS"] = rnd.randint(-1000, 1000)
        self.g["gC"] = rnd.choice([0, 1, 7, 1 << 40, IMAX])
        # the values at the start of the program
        self.init = dict(self.g)
        self.local = {}

    @staticmethod
    def initial(t, rnd):
        if t in "IL":
            return rnd.choice(INT_POOL) if rnd.random() < 0.7 else rnd.randint(IMIN, IMAX)
        if t == "B":
            return rnd.random() < 0.5
        x = rnd.choice(FLOAT_POOL) if rnd.random() < 0.7 else rnd.uniform(-1e6, 1e6)
        if t == "R":
            return f32(x) if abs(x) < 1e38 else 0.25
        return x

    def value(self, name):
        return self.local[name] if name in self.local else self.g[name]


# ---------------------------------------------------------------- expressions

class Gen:
    """Generates expressions as (text, text with identity calls, value)."""

    def __init__(self, rnd, state):
        self.rnd = rnd
        self.st = state
        self.lit_mode = False
        self.in_proc = False

    def leaf(self, t):
        r = self.rnd
        if self.lit_mode or r.random() < 0.2:
            v = self.literal_value(t)
            if v == IMIN and not self.lit_mode:
                # not as a constant of computed code (see the comment at the top)
                v = IMIN + 1
            text = self.lit(v, t)
            return text, f"Id{t}({text})", v
        names = [f"g{t}{k}" for k in range(NG[t])]
        if t == "I":
            names += [f"arr[{k}]" for k in range(4)] + ["p." + n for n, *_ in PACKED]
            names += [f"pa[{r.randrange(PA_LEN)}]", "gS", "gC"]
            if self.in_proc:
                names += [f"lI{k}" for k in range(NLOCAL)]
        if t == "B":
            names += ["p.d"]
        name = r.choice(names)
        v = self.st.value(name)
        if t in "IL" and r.random() < 0.15:
            text = f"Id{t}({name})"
        else:
            text = name
        return text, text, v

    def literal_value(self, t):
        r = self.rnd
        if t in "IL":
            return r.choice(INT_POOL) if r.random() < 0.6 else r.randint(-1000, 1000)
        if t == "B":
            return r.random() < 0.5
        x = r.choice(FLOAT_POOL) if r.random() < 0.6 else round(r.uniform(-1000, 1000), r.randint(0, 6))
        if t == "R":
            return f32(x) if abs(x) < 1e38 else 0.75
        return x

    def lit(self, v, t):
        if t in "IL":
            return lit_int(v, t, self.rnd)
        if t == "B":
            return "TRUE" if v else "FALSE"
        return lit_float(v, t)

    def gen(self, t, d, top=False):
        for _ in range(20):
            try:
                if d <= 0 or (not top and self.rnd.random() < 0.25):
                    return self.leaf(t)
                return getattr(self, "gen_" + {"I": "int", "L": "int", "B": "bool", "D": "real",
                                               "R": "real"}[t])(t, d - 1)
            except Skip:
                continue
        return self.leaf(t)

    def gen_int(self, t, d):
        r = self.rnd
        W = "Word" if t == "I" else "Long"
        k = r.randrange(26)
        if k < 9:
            op = ["+", "-", "*", "DIV", "MOD", "+", "-", "*", "MOD"][k]
            a, a2, x = self.gen(t, d)
            b, b2, y = self.gen(t, d)
            if op == "+":
                v = chk(x + y)
            elif op == "-":
                v = chk(x - y)
            elif op == "*":
                v = chk(x * y)
            else:
                if y == 0 or (x == IMIN and y == -1):
                    raise Skip
                v = x // y if op == "DIV" else x % y
            return f"({a} {op} {b})", f"({a2} {op} {b2})", v
        if k < 11:
            a, a2, x = self.gen(t, d)
            if k == 9:
                return f"(-{a})", f"(-{a2})", chk(-x)
            return f"ABS({a})", f"ABS({a2})", chk(abs(x))
        if k < 13:
            op = "MIN" if k == 11 else "MAX"
            a, a2, x = self.gen(t, d)
            b, b2, y = self.gen(t, d)
            return f"{op}({a}, {b})", f"{op}({a2}, {b2})", (min if k == 11 else max)(x, y)
        if k < 16:
            op = r.choice(["Plus", "Minus", "Times", "Divide", "Mod", "And", "Or", "Xor"])
            a, a2, x = self.gen(t, d)
            b, b2, y = self.gen(t, d)
            ux, uy = u(x), u(y)
            if op in ("Divide", "Mod") and uy == 0:
                raise Skip
            if op in ("Divide", "Mod") and is_const(a) and is_const(b) and \
                    (ux // uy if op == "Divide" else ux % uy) > IMAX:
                # see the comment at the top
                raise Skip
            v = s({"Plus": ux + uy, "Minus": ux - uy, "Times": ux * uy,
                   "Divide": ux // uy if uy else 0, "Mod": ux % uy if uy else 0,
                   "And": ux & uy, "Or": ux | uy, "Xor": ux ^ uy}[op])
            return f"{W}.{op}({a}, {b})", f"{W}.{op}({a2}, {b2})", v
        if k == 16:
            a, a2, x = self.gen(t, d)
            return f"{W}.Not({a})", f"{W}.Not({a2})", s(~u(x))
        if k < 19:
            op = r.choice(["Shift", "Rotate", "LeftShift", "RightShift", "LeftRotate", "RightRotate"])
            a, a2, x = self.gen(t, d)
            if op in ("Shift", "Rotate"):
                n = r.randint(-70, 70)
            else:
                n = r.randint(0, 63)
            nt = lit_int(n, "I", r)
            nt2 = nt if not self.lit_mode else f"IdI({nt})"
            ux = u(x)
            if op == "Shift":
                v = 0 if abs(n) >= 64 else s(ux << n) if n >= 0 else s(ux >> -n)
            elif op == "LeftShift":
                v = s(ux << n)
            elif op == "RightShift":
                v = s(ux >> n)
            else:
                m = n % 64 if op in ("Rotate", "LeftRotate") else (-n) % 64
                v = s((ux << m) | (ux >> (64 - m))) if m else s(ux)
            return f"{W}.{op}({a}, {nt})", f"{W}.{op}({a2}, {nt2})", v
        if k == 19:
            a, a2, x = self.gen(t, d)
            n = r.choice([0, 1, 7, 8, 16, 31, 32, 33, 63, 64, r.randint(0, 64)])
            i = r.randint(0, 64 - n)
            v = s((u(x) >> i) & ((1 << n) - 1))
            return f"{W}.Extract({a}, {i}, {n})", f"{W}.Extract({a2}, {i}, {n})", v
        if k == 20:
            a, a2, x = self.gen(t, d)
            b, b2, y = self.gen(t, d)
            # not 64 (see the comment at the top)
            n = r.choice([0, 1, 7, 8, 16, 31, 32, 33, 63, r.randint(0, 63)])
            i = r.randint(0, 64 - n)
            mask = ((1 << n) - 1) << i
            v = s((u(x) & ~mask) | ((u(y) << i) & mask))
            return f"{W}.Insert({a}, {b}, {i}, {n})", f"{W}.Insert({a2}, {b2}, {i}, {n})", v
        if k == 21:
            a, a2, x = self.gen("B", d)
            if t == "L":
                return f"VAL(ORD({a}), LONGINT)", f"VAL(ORD({a2}), LONGINT)", int(x)
            return f"ORD({a})", f"ORD({a2})", int(x)
        if k == 22:
            o = "L" if t == "I" else "I"
            a, a2, x = self.gen(o, d)
            if a.startswith("Long.RightShift("):
                # see the comment at the top
                raise Skip
            name = "INTEGER" if t == "I" else "LONGINT"
            return f"VAL({a}, {name})", f"VAL({a2}, {name})", x
        if k < 25 and t == "I":
            op = r.choice(["TRUNC", "ROUND", "FLOOR", "CEILING"])
            ft = r.choice("DR")
            a, a2, x = self.gen(ft, d)
            if not -FMAX < x < FMAX:
                raise Skip
            v = {"TRUNC": math.trunc, "ROUND": round_m3, "FLOOR": math.floor, "CEILING": math.ceil}[op](x)
            return f"{op}({a})", f"{op}({a2})", int(v)
        if t == "I":
            a, a2, x = self.gen("I", d)
            return f"ORD(VAL({a} MOD 256, CHAR))", f"ORD(VAL({a2} MOD 256, CHAR))", x % 256
        return self.gen_int(t, d)

    def gen_bool(self, t, d):
        r = self.rnd
        k = r.randrange(10)
        if k < 5:
            ot = r.choice("IILDR")
            op = r.choice(["=", "#", "<", "<=", ">", ">="])
            a, a2, x = self.gen(ot, d)
            b, b2, y = self.gen(ot, d)
            v = {"=": x == y, "#": x != y, "<": x < y, "<=": x <= y, ">": x > y, ">=": x >= y}[op]
            return f"({a} {op} {b})", f"({a2} {op} {b2})", v
        if k < 7:
            ot = r.choice("IL")
            W = "Word" if ot == "I" else "Long"
            op = r.choice(["LT", "LE", "GT", "GE"])
            a, a2, x = self.gen(ot, d)
            b, b2, y = self.gen(ot, d)
            ux, uy = u(x), u(y)
            v = {"LT": ux < uy, "LE": ux <= uy, "GT": ux > uy, "GE": ux >= uy}[op]
            return f"{W}.{op}({a}, {b})", f"{W}.{op}({a2}, {b2})", v
        if k == 7:
            a, a2, x = self.gen("B", d)
            return f"(NOT {a})", f"(NOT {a2})", not x
        op = "AND" if k == 8 else "OR"
        a, a2, x = self.gen("B", d)
        b, b2, y = self.gen("B", d)
        return f"({a} {op} {b})", f"({a2} {op} {b2})", (x and y) if op == "AND" else (x or y)

    def gen_real(self, t, d):
        r = self.rnd
        rd = f32 if t == "R" else (lambda z: z)
        name = "REAL" if t == "R" else "LONGREAL"
        k = r.randrange(10)
        if k < 5:
            op = r.choice(["+", "-", "*", "/"])
            a, a2, x = self.gen(t, d)
            b, b2, y = self.gen(t, d)
            if op == "/" and y == 0:
                raise Skip
            v = fin(rd(fin({"+": lambda: x + y, "-": lambda: x - y, "*": lambda: x * y,
                            "/": lambda: x / y}[op]())))
            return f"({a} {op} {b})", f"({a2} {op} {b2})", v
        if k == 5:
            a, a2, x = self.gen(t, d)
            if r.random() < 0.5:
                return f"(-{a})", f"(-{a2})", -x
            return f"ABS({a})", f"ABS({a2})", abs(x)
        if k == 6:
            op = r.choice(["MIN", "MAX"])
            a, a2, x = self.gen(t, d)
            b, b2, y = self.gen(t, d)
            if x == y and math.copysign(1.0, x) != math.copysign(1.0, y):
                # see the comment at the top
                raise Skip
            return f"{op}({a}, {b})", f"{op}({a2}, {b2})", (min if op == "MIN" else max)(x, y)
        if k == 7:
            a, a2, x = self.gen("I", d)
            v = int_to_f32(x) if t == "R" else float(x)
            return f"FLOAT({a}, {name})", f"FLOAT({a2}, {name})", v
        o = "D" if t == "R" else "R"
        a, a2, x = self.gen(o, d)
        return f"FLOAT({a}, {name})", f"FLOAT({a2}, {name})", rd(x)


# ---------------------------------------------------------------- programs

PRINT = {"I": "PI", "L": "PL", "B": "PB", "D": "PD", "R": "PR"}
TYPE = {"I": "INTEGER", "L": "LONGINT", "B": "BOOLEAN", "D": "LONGREAL", "R": "REAL"}


def model_text(t, v):
    """The output of the value v of type t as the model computes it, or None
    for floating-point values (whose model is not exact in every case)."""
    if t in "IL":
        return str(v)
    if t == "B":
        return "TRUE" if v else "FALSE"
    return None


def generate(seed, checks):
    """The program of seed: (source text, {tag: (expression, value of the model)})."""
    rnd = random.Random(seed)
    st = State(rnd)
    g = Gen(rnd, st)
    exprs = {}
    procs = []
    body = []
    per_proc = 40
    pick = "IIIILLBBDDR"
    for n in range(checks):
        if n % per_proc == 0:
            if body:
                procs.append(body)
            body = []
            st.local = {f"lI{k}": st.g[f"gI{k}"] for k in range(NLOCAL)}
            g.in_proc = True
        tag = str(n + 1)
        kind = rnd.random()
        t = rnd.choice(pick)
        depth = rnd.randint(1, 4)
        if kind < 0.25:
            g.lit_mode = True
            a, a2, x = g.gen(t, depth, top=True)
            g.lit_mode = False
            body.append(f'    {PRINT[t]}("{tag}a", {a});')
            body.append(f'    {PRINT[t]}("{tag}b", {a2});')
            exprs[tag + "a"] = (a, model_text(t, x))
            exprs[tag + "b"] = (a2, model_text(t, x))
        elif kind < 0.35:
            # a store into a packed field, an element of the packed array or a subrange variable
            target, lo, hi = rnd.choice(
                [("p." + nm, lo, hi) for nm, lo, hi, _ in PACKED]
                + [(f"pa[{rnd.randrange(PA_LEN)}]", PA_LO, PA_HI), ("gS", -1000, 1000), ("gC", 0, IMAX)])
            a, _, x = g.gen("I", depth)
            if target == "gC":
                rhs, v = f"Word.And({a}, LAST(INTEGER))", u(x) & IMAX
            else:
                w = hi - lo + 1
                rhs, v = f"{a} MOD {w} + ({lo})", x % w + lo
            body.append(f"    {target} := {rhs};")
            body.append(f'    PI("{tag}", {target});')
            st.g[target] = v
            exprs[tag] = (f"{target} := {rhs}", model_text("I", v))
        elif kind < 0.42:
            # INC and DEC
            target = rnd.choice([f"gI{rnd.randrange(NG['I'])}", "p.c", "gS"])
            a, _, x = g.gen("I", min(depth, 2))
            op = rnd.choice(["INC", "DEC"])
            old = st.g[target]
            v = old + x if op == "INC" else old - x
            lo, hi = {"p.c": (-4096, 4095), "gS": (-1000, 1000)}.get(target, (IMIN, IMAX))
            if not lo <= v <= hi:
                body.append(f'    PI("{tag}", {target});')
                exprs[tag] = (target, model_text("I", old))
                continue
            body.append(f"    {op}({target}, {a});")
            body.append(f'    PI("{tag}", {target});')
            st.g[target] = v
            exprs[tag] = (f"{op}({target}, {a})", model_text("I", v))
        elif kind < 0.47:
            a, _, x = g.gen("B", depth)
            body.append(f"    p.d := {a};")
            body.append(f'    PB("{tag}", p.d);')
            st.g["p.d"] = x
            exprs[tag] = (f"p.d := {a}", model_text("B", x))
        else:
            a, _, x = g.gen(t, depth, top=True)
            body.append(f'    {PRINT[t]}("{tag}", {a});')
            exprs[tag] = (a, model_text(t, x))
    if body:
        procs.append(body)
    return program(seed, rnd, st, procs), exprs


def program(seed, rnd, st, procs):
    L = [f"(* Generated by tools/expr_fuzz.py, seed {seed}. *)", "", "MODULE Main;",
         "", "IMPORT IO, Fmt, Word, Long;", "",
         "TYPE",
         "  Packed = RECORD",
         ]
    fields = []
    for name, lo, hi, bits in PACKED:
        fields.append(f"    {name}: BITS {bits} FOR [{lo} .. {hi}];")
        if name == "c":
            fields.append("    d: BITS 1 FOR BOOLEAN;")
    L += fields
    L += ["  END;",
          f"  PArr = ARRAY [0 .. {PA_LEN - 1}] OF BITS 5 FOR [{PA_LO} .. {PA_HI}];",
          "", "VAR"]
    for t, n in NG.items():
        L.append("  " + ", ".join(f"g{t}{k}" for k in range(n)) + f": {TYPE[t]};")
    L += ["  arr: ARRAY [0 .. 3] OF INTEGER;",
          "  p: Packed;",
          "  pa: PArr;",
          "  gS: [-1000 .. 1000];",
          "  gC: CARDINAL;",
          ""]
    for t in "ILBDR":
        L += [f"PROCEDURE Id{t} (x: {TYPE[t]}): {TYPE[t]} =",
              "  BEGIN", "    RETURN x;", f"  END Id{t};", ""]
    fmt = {"I": "Fmt.Int(x)", "L": "Fmt.LongInt(x)", "B": "Fmt.Bool(x)",
           "D": "Fmt.LongReal(x, Fmt.Style.Sci, 16)", "R": "Fmt.Real(x, Fmt.Style.Sci, 8)"}
    for t in "ILBDR":
        L += [f"PROCEDURE {PRINT[t]} (tag: TEXT; x: {TYPE[t]}) =",
              "  BEGIN", f'    IO.Put(tag & " " & {fmt[t]} & "\\n");', f"  END {PRINT[t]};", ""]
    for i, body in enumerate(procs):
        L.append(f"PROCEDURE T{i} () =")
        L.append("  VAR " + "; ".join(f"lI{k} := gI{k}" for k in range(NLOCAL)) + ";")
        L.append("  BEGIN")
        L += body
        L.append(f"  END T{i};")
        L.append("")
    L.append("BEGIN")
    for name, v in st.init.items():
        L.append(f"  {name} := {init_text(name, v)};")
    for i in range(len(procs)):
        L.append(f"  T{i} ();")
    L.append("END Main.")
    return "\n".join(L) + "\n"


def init_text(name, v):
    t = name[1] if name.startswith("g") and name[1] in "ILDRB" and name[2:].isdigit() else "I"
    if t == "B" or name == "p.d":
        return "TRUE" if v else "FALSE"
    if t in "DR":
        return lit_float(v, t)
    return lit_int(v, t, random.Random(0))


# ---------------------------------------------------------------- running

M3MAKEFILE = """% Generated by tools/expr_fuzz.py.
import ("libm3")
implementation ("Main")
program ("{name}")
"""


def write_package(d, seed, checks):
    src = d / "src"
    src.mkdir(parents=True, exist_ok=True)
    text, exprs = generate(seed, checks)
    (src / "Main.m3").write_text(text, newline="\n")
    (src / "m3makefile").write_text(M3MAKEFILE.format(name=d.name), newline="\n")
    return exprs


def parse_lines(text):
    """{tag: value} of the output of a program."""
    out = {}
    for line in text.replace("\r\n", "\n").splitlines():
        tag, _, v = line.partition(" ")
        out[tag] = v
    return out


# builds of cm3 at the same time get in each other's way when linking
CM3_LOCK = threading.Lock()


def run_cm3(src, name, timeout):
    with CM3_LOCK:
        c = subprocess.run(["cm3", "-build"], cwd=src, capture_output=True, shell=os.name == "nt")
    exe = src.parent / "AMD64_NT" / (name + ".exe")
    if c.returncode != 0 or not exe.exists():
        return None, (c.stdout + c.stderr).decode("latin-1").strip().splitlines()[-3:]
    try:
        r = subprocess.run([str(exe)], cwd=src, capture_output=True, timeout=timeout)
    except OSError as e:
        # such as a virus scanner that takes the program for malware
        return None, [f"cannot run {exe.name}: {e}"]
    return r, None


def run_m3j(src, args, compile_args=(), files=("Main.m3",)):
    """Compiles the files in src with m3j (compile_args are more options of
    its JVM) and runs the program."""
    c = subprocess.run([args.java] + args.aot + list(compile_args) + ["-Xss64m", "-cp", args.jar, "m3j.jvm.Compile"]
                       + list(files) + ["-d", "classes"], cwd=src, capture_output=True, timeout=args.timeout)
    if c.returncode != 0:
        return None, (c.stdout + c.stderr).decode("latin-1").strip().splitlines()[-3:]
    r = subprocess.run([args.java] + args.aot + ["-cp", args.jar + SEP + "classes", "m3.Main"], cwd=src,
                       capture_output=True, timeout=args.timeout)
    return r, None


def check_seed(seed, args, work):
    name = f"e{seed}"
    d = work / name
    exprs = write_package(d, seed, args.checks)
    src = d / "src"
    problems = []
    cm3_wrong = []
    try:
        j, err = run_m3j(src, args)
    except subprocess.TimeoutExpired:
        return seed, "TIMEOUT", ["m3j"]
    if j is None:
        if not args.no_cm3:
            try:
                c, _ = run_cm3(src, name, args.timeout)
            except subprocess.TimeoutExpired:
                c = True
            if c is None:
                # an error of the front end that cm3 has as well
                return seed, "BOTH-FAILED", err
        return seed, "M3J-FAILED", err
    jout = parse_lines(j.stdout.decode("latin-1"))
    if j.returncode != 0:
        problems.append(f"m3j exit status {j.returncode}: " + j.stderr.decode("latin-1").strip()[:300])
    if args.no_cm3:
        for tag in exprs:
            if tag.endswith("a") and jout.get(tag) != jout.get(tag[:-1] + "b"):
                problems.append(f"{tag[:-1]}: folded {jout.get(tag)}, computed {jout.get(tag[:-1] + 'b')}\n"
                                f"    {exprs[tag][0]}")
    else:
        try:
            c, err = run_cm3(src, name, args.timeout)
        except subprocess.TimeoutExpired:
            return seed, "TIMEOUT", ["cm3"]
        if c is None:
            return seed, "CM3-FAILED", err
        cout = parse_lines(c.stdout.decode("latin-1"))
        if c.returncode != j.returncode:
            problems.append(f"exit status: cm3 {c.returncode}, m3j {j.returncode}")
        for tag, (text, model) in exprs.items():
            if cout.get(tag) != jout.get(tag):
                if model is not None and jout.get(tag) == model:
                    # an error of cm3 (see the comment at the top)
                    cm3_wrong.append(f"{tag}: cm3 {cout.get(tag)}, m3j and the model {model}\n    {text}")
                else:
                    problems.append(f"{tag}: cm3 {cout.get(tag)}, m3j {jout.get(tag)}, model {model}\n    {text}")
    if problems:
        return seed, "FAIL", problems + cm3_wrong
    return seed, ("CM3-WRONG" if cm3_wrong else "PASS"), cm3_wrong


def parse_seeds(text):
    a, _, b = text.partition("-")
    return list(range(int(a), int(b or a) + 1))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--seeds", default="1-10")
    ap.add_argument("--checks", type=int, default=400)
    ap.add_argument("--write", type=Path)
    ap.add_argument("--list", type=Path)
    ap.add_argument("--no-cm3", action="store_true")
    ap.add_argument("--jobs", type=int, default=max(1, (os.cpu_count() or 2) // 2))
    ap.add_argument("--work", type=Path)
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
    ap.add_argument("--timeout", type=int, default=300)
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()
    seeds = parse_seeds(args.seeds)
    if args.write:
        lines = []
        for seed in seeds:
            write_package(args.write / f"e{seed}", seed, args.checks)
            if args.list:
                rel = (args.write / f"e{seed}").resolve().relative_to(ROOT).as_posix()
                lines.append(f"fuzz/e{seed} {rel}")
        if args.list:
            args.list.write_text("# Random expression tests (written by tools/expr_fuzz.py --write)\n"
                                 + "\n".join(lines) + "\n", newline="\n")
        print(f"wrote {len(seeds)} programs to {args.write}")
        return
    if not args.java:
        jdk = os.environ.get("JDK25")
        args.java = str(Path(jdk) / "bin" / "java") if jdk else "java"
    aot = Path(args.jar).with_suffix(".aot")
    args.aot = ["-XX:AOTCache=" + str(aot)] if aot.exists() else []
    work = (args.work or Path(tempfile.mkdtemp(prefix="m3j-fuzz-"))).resolve()
    work.mkdir(parents=True, exist_ok=True)
    counts = {}
    try:
        with concurrent.futures.ThreadPoolExecutor(args.jobs) as ex:
            for seed, status, detail in ex.map(lambda sd: check_seed(sd, args, work), seeds):
                counts[status] = counts.get(status, 0) + 1
                print(f"{status:10s} seed {seed}", flush=True)
                for line in detail[:args.verbose and 1000 or 10]:
                    print("  " + line)
    finally:
        if not args.keep and not args.work:
            shutil.rmtree(work, ignore_errors=True)
    print("  ".join(f"{k} {v}" for k, v in sorted(counts.items())))
    sys.exit(0 if set(counts) <= {"PASS", "CM3-WRONG", "BOTH-FAILED"} else 1)


if __name__ == "__main__":
    main()
