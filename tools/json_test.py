#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Tests of JsonWriter of the library json of m3j, through
tests/jsonwriter/src/Main.m3, which carries out scripts of calls. They need
no connection to the internet.

  python3 tools/json_test.py [CASE]... [--java JAVA] [--jar JAR] [--work DIR]
                             [--keep] [--timeout SECONDS] [--count N] [--speed]

The cases:

  fixed    documents with their expected bytes, checked by hand
  errors   calls that must be refused: the kind of problem, the writer is
           closed afterwards, and what was written is the beginning of a
           document that the json module of Python accepts
  random   random documents against the model they were made from: the bytes
           are those of json.dumps, and Python and Json read the model back
  chars    each character in a value and in a key; surrogates without their
           partner are refused or replaced; pairs on the edge of a chunk
  reals    random numbers from their bits, read by Python and by Json, as
           floating-point numbers
  ints     the limits of INTEGER and random ones
  numbers  numbers as texts against the grammar of RFC 8259
  depth    the limit of nesting, just below and just above; no limit
  dups     the same key once more, with few and many keys
  sink     writers that fail or block, flush, Open and close (expected output
           in tests/jsonwriter/expected)
  big      a generated document under a small heap, compared by hash

--speed measures the rate, for information.
"""

import argparse
import hashlib
import json
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
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests" / "jsonwriter"
SEED = 20261109
FIRST_INT = -2 ** 63
LAST_INT = 2 ** 63 - 1
NUMBER = re.compile(r"-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?")


class Failed(Exception):
    pass


class Options:
    def __init__(self, indent=None, replace=False, unique=True, depth=1000, tree=True):
        self.indent = indent
        self.replace = replace
        self.unique = unique
        self.depth = depth
        self.tree = tree


class Env:
    def __init__(self, args, work):
        self.args = args
        self.work = work
        self.java = args.java
        self.classes = work / "classes"
        self.cp = str(self.classes) + os.pathsep + args.jar
        self.batches = 0

    def compile(self):
        cmd = [self.java, "-Xss64m", "-cp", self.args.jar, "m3j.jvm.Compile", str(TESTS / "src" / "Main.m3"),
               "-d", str(self.classes)]
        r = subprocess.run(cmd, cwd=self.work, capture_output=True, text=True, stdin=subprocess.DEVNULL)
        if r.returncode:
            raise Failed("compile:\n" + r.stdout + r.stderr)

    def run(self, *a, heap=None):
        opts = ["-Xmx" + heap] if heap else []
        r = subprocess.run([self.java, *opts, "-cp", self.cp, "m3.Main", *a], cwd=self.work, capture_output=True,
                           timeout=self.args.timeout)
        if r.returncode:
            raise Failed("exit %d: %s" % (r.returncode, (r.stdout + r.stderr).decode("latin-1")[:2000]))
        if r.stderr:
            raise Failed("output on standard error: %s" % r.stderr.decode("latin-1")[:500])
        return r.stdout.decode("latin-1")

    def scripts(self, docs):
        """Carries out each (options, calls); gives (bytes, lines of the result, tree or None)."""
        self.batches += 1
        d = "w%d" % self.batches
        (self.work / d).mkdir()
        lines = []
        for i, (opts, ops) in enumerate(docs):
            name = "%s/%05d" % (d, i)
            (self.work / (name + ".s")).write_text(script(opts, ops), newline="\n", encoding="ascii")
            lines.append("%s.s %s.json" % (name, name))
        (self.work / (d + ".list")).write_text("\n".join(lines) + "\n", newline="\n")
        self.run("batch", d + ".list")
        out = []
        for i in range(len(docs)):
            name = self.work / ("%s/%05d.json" % (d, i))
            tree = name.with_name(name.name + ".tree")
            out.append((name.read_bytes(),
                        name.with_name(name.name + ".res").read_text(encoding="ascii").splitlines(),
                        tree.read_text(encoding="ascii").splitlines() if tree.exists() else None))
        return out

    def one(self, opts, ops):
        return self.scripts([(opts, ops)])[0]


# ---------------------------------------------------------------- scripts

def arg(s):
    if s is None:
        return "-"
    if s == "":
        return "="
    return s.encode("utf-16-be", "surrogatepass").hex()


def script(o, ops):
    head = "options %s %d %d %d %d" % (arg(o.indent), o.replace, o.unique, o.depth, o.tree)
    return "\n".join([head, *ops]) + "\n"


def O(key=None):
    return "object " + arg(key)


def A(key=None):
    return "array " + arg(key)


END = "end"
FINISH = "finish"


def K(k):
    return "key " + arg(k)


def T(s, key=None):
    return "text %s %s" % (arg(s), arg(key))


def I(n, key=None):
    return "int %d %s" % (n, arg(key))


def R(x, key=None):
    return "real %s %s" % (x if isinstance(x, str) else repr(x), arg(key))


def B(b, key=None):
    return "bool %d %s" % (b, arg(key))


def N(key=None):
    return "null " + arg(key)


def NUM(s, key=None):
    return "number %s %s" % (arg(s), arg(key))


def calls(v, key=None, rng=None):
    """The calls that write the value v, whose key goes by either way."""
    if rng is not None and key is not None and rng.random() < 0.3:
        return [K(key)] + calls(v, None, rng)
    if isinstance(v, dict):
        out = [O(key)]
        for k, x in v.items():
            out += calls(x, k, rng)
        return out + [END]
    if isinstance(v, list):
        out = [A(key)]
        for x in v:
            out += calls(x, None, rng)
        return out + [END]
    if v is None:
        return [N(key)]
    if v is True or v is False:
        return [B(v, key)]
    if isinstance(v, int):
        return [I(v, key)]
    if isinstance(v, float):
        return [R(v, key)]
    return [T(v, key)]


# ----------------------------------------------------- reading the output

class Obj(list):
    """An object as its pairs, in order."""


def refuse_constant(c):
    raise Failed("a constant that JSON does not have: " + c)


def read(data):
    return json.loads(data.decode("utf-8"), object_pairs_hook=Obj, parse_constant=refuse_constant)


def canon(v):
    if isinstance(v, dict):
        return Obj((k, canon(x)) for k, x in v.items())
    if isinstance(v, list):
        return [canon(x) for x in v]
    return v


def same(a, b):
    if type(a) is not type(b):
        return False
    if isinstance(a, float):
        return a == b and math.copysign(1, a) == math.copysign(1, b)
    if isinstance(a, Obj):
        return len(a) == len(b) and all(x[0] == y[0] and same(x[1], y[1]) for x, y in zip(a, b))
    if isinstance(a, list):
        return len(a) == len(b) and all(same(x, y) for x, y in zip(a, b))
    return a == b


def raw_text(h):
    """A string as Json gives it: the bytes of the source, with their escapes."""
    b = b"" if h == "=" else bytes.fromhex(h)
    return json.loads('"' + b.decode("utf-8") + '"')


def parse_tree(lines):
    """What Json has read, from the lines of Dump: Python values, objects as dicts."""
    pos = [0]

    def value():
        f = lines[pos[0]].split(" ")
        pos[0] += 1
        if f[0] == "{":
            d = {}
            for _ in range(int(f[1])):
                k = lines[pos[0]].split(" ")
                pos[0] += 1
                if k[0] != "k":
                    raise Failed("a key was expected: " + lines[pos[0] - 1])
                name = raw_text(k[1])
                d[name] = value()
            return d
        if f[0] == "[":
            return [value() for _ in range(int(f[1]))]
        if f[0] == "s":
            return raw_text(f[1])
        if f[0] == "i":
            if int(f[1]) != int(f[2]):
                raise Failed("Json: %s reads as %s" % (f[1], f[2]))
            return int(f[1])
        if f[0] == "f":
            if not same(float(f[1]), float(f[2])):
                raise Failed("Json: %s reads as %s" % (f[1], f[2]))
            return float(f[1])
        if f[0] == "b":
            return f[1] == "true"
        if f[0] == "n":
            return None
        raise Failed("in the tree: " + lines[pos[0] - 1])

    v = value()
    if pos[0] != len(lines):
        raise Failed("the tree goes on after its value")
    return v


def same_tree(model, tree):
    if type(model) is not type(tree):
        return False
    if isinstance(model, dict):
        return model.keys() == tree.keys() and all(same_tree(model[k], tree[k]) for k in model)
    if isinstance(model, list):
        return len(model) == len(tree) and all(same_tree(x, y) for x, y in zip(model, tree))
    return same(model, tree)


def check_read(model, data, tree, label):
    if not same(canon(model), read(data)):
        raise Failed("%s: Python reads something else:\n  %r" % (label, data[:300]))
    if tree is not None and not same_tree(model, parse_tree(tree)):
        raise Failed("%s: Json reads something else:\n  %r" % (label, data[:300]))


def complete(data):
    """Whether data is the beginning of a document: closes what is open and parses it."""
    s = data.decode("utf-8")
    stack = []
    in_string = False
    escaped = False
    for c in s:
        if in_string:
            if escaped:
                escaped = False
            elif c == "\\":
                escaped = True
            elif c == '"':
                in_string = False
        elif c == '"':
            in_string = True
        elif c in "{[":
            stack.append(c)
        elif c in "}]":
            stack.pop()
    if escaped:
        return False
    if in_string:
        s += '"'
    closers = "".join("}" if c == "{" else "]" for c in reversed(stack))
    for middle in ("", "null", ":null"):
        try:
            read((s + middle + closers).encode("utf-8"))
            return True
        except ValueError:
            pass
    return False


def expect_ok(label, res):
    if res != ["OK 0"]:
        raise Failed("%s: the result is %r" % (label, res))


# ------------------------------------------------------------------ cases

D = Options(tree=False)

FIXED = [
    ("number", D, [I(42), FINISH], b"42"),
    ("string", D, [T("a"), FINISH], b'"a"'),
    ("bool", D, [B(True), FINISH], b"true"),
    ("null", D, [N(), FINISH], b"null"),
    ("empty object", D, [O(), END, FINISH], b"{}"),
    ("empty array", D, [A(), END, FINISH], b"[]"),
    ("members", D, [O(), I(1, "a"), T("x", "b"), B(False, "c"), N("d"), END, FINISH],
     b'{"a":1,"b":"x","c":false,"d":null}'),
    ("nesting", D, [A(), O(), A("k"), END, END, A(), END, END, FINISH], b'[{"k":[]},[]]'),
    ("key by call", D, [O(), K("a"), A(), I(1), END, K("b"), I(2), END, FINISH], b'{"a":[1],"b":2}'),
    ("empty key and text", D, [O(), T("", ""), END, FINISH], b'{"":""}'),
    ("escapes", D, [T('"\\/\b\f\n\r\t\x00\x1f\x7f'), FINISH],
     b'"\\"\\\\/\\b\\f\\n\\r\\t\\u0000\\u001f\x7f"'),
    ("utf-8", D, [T("\u00e4\u20ac\U0001F600\ufffe\uffff"), FINISH],
     '"\u00e4\u20ac\U0001F600\ufffe\uffff"'.encode("utf-8")),
    ("reals", D, [A(), R(100000.0), R(-0.0), R(1e6), R(0.1), R(1.5e-7), R(0.0), R(1.0), END, FINISH],
     b"[100000.0,-0.0,1e6,0.1,1.5e-7,0.0,1.0]"),
    ("replaced real", Options(replace=True, tree=False), [A(), R("inf"), R("-inf"), R("nan"), END, FINISH],
     b"[null,null,null]"),
    ("replaced characters", Options(replace=True, tree=False), [T("a\ud800b\udc00"), FINISH],
     'a\ufffdb\ufffd'.join('""').encode("utf-8")),
    ("big number", D, [NUM("123456789012345678901234567890.25e-7"), FINISH],
     b"123456789012345678901234567890.25e-7"),
    ("indented", Options(indent="  ", tree=False),
     [O(), A("a"), I(1), O(), END, A(), END, END, O("b"), N("c"), END, END, FINISH],
     b'{\n  "a": [\n    1,\n    {},\n    []\n  ],\n  "b": {\n    "c": null\n  }\n}\n'),
    ("tabs", Options(indent="\t", tree=False), [A(), I(1), END, FINISH], b"[\n\t1\n]\n"),
    ("indented root value", Options(indent=" ", tree=False), [I(1), FINISH], b"1\n"),
]


def case_fixed(env):
    got = env.scripts([(o, ops) for _, o, ops, _ in FIXED])
    for (name, _, _, want), (data, res, _) in zip(FIXED, got):
        expect_ok(name, res)
        if data != want:
            raise Failed("%s:\n  got  %r\n  want %r" % (name, data, want))


S, C, X = "Structure", "Content", "Closed"
OK_ROOT = [I(1)]

ERRORS = [
    ("value without a key", [O(), I(1)], S),
    ("text without a key", [O(), T("x")], S),
    ("object without a key", [O(), O()], S),
    ("array without a key", [O(), A()], S),
    ("after a member, no key", [O(), I(1, "a"), I(2)], S),
    ("key in an array", [A(), K("a")], S),
    ("key at the root", [K("a")], S),
    ("key of a root value", [T("x", "k")], S),
    ("key of an element", [A(), I(1, "k")], S),
    ("key of an object in an array", [A(), O("k")], S),
    ("two keys", [O(), K("a"), K("b")], S),
    ("key and argument", [O(), K("a"), I(1, "b")], S),
    ("end of nothing", [END], S),
    ("end too often", [A(), END, END], S),
    ("end after a key", [O(), K("a"), END], S),
    ("second root", [I(1), I(2)], S),
    ("second root object", [O(), END, O()], S),
    ("key after the root", [O(), END, K("a")], S),
    ("finish without a value", [FINISH], S),
    ("finish in an object", [O(), FINISH], S),
    ("finish in an array", [A(), I(1), FINISH], S),
    ("finish after a key", [O(), K("a"), FINISH], S),
    ("duplicate", [O(), I(1, "a"), I(2, "a")], S),
    ("duplicate by call", [O(), K("a"), I(1), K("a")], S),
    ("high surrogate alone", [T("\ud800")], C),
    ("low surrogate alone", [T("\udc00")], C),
    ("high surrogate before a letter", [T("\ud800a")], C),
    ("surrogates reversed", [T("\udc00\ud800")], C),
    ("surrogate in a key", [O(), I(1, "a\ud800")], C),
    ("surrogate in a key by call", [O(), K("\udbff")], C),
    ("infinity", [A(), R("inf")], C),
    ("minus infinity", [R("-inf")], C),
    ("nan", [O(), R("nan", "k")], C),
    ("bad number", [A(), NUM("01")], C),
    ("closed by finish", [I(1), FINISH, I(2)], X),
    ("closed by finish, end", [I(1), FINISH, END], X),
    ("closed by finish, key", [O(), END, FINISH, K("a")], X),
    ("finished twice", [I(1), FINISH, FINISH], X),
]


def case_errors(env):
    docs = [(D, ops) for _, ops, _ in ERRORS]
    deep = Options(depth=2, tree=False)
    table = list(ERRORS)
    table.append(("too deep", None, S))
    docs.append((deep, [A(), A(), A()]))
    table.append(("too deep, objects", None, S))
    docs.append((deep, [O(), O("a"), O("b")]))
    got = env.scripts(docs)
    for (name, _, problem), (data, res, _) in zip(table, got):
        if res != ["ERROR " + problem, "AFTER Closed"]:
            raise Failed("%s: the result is %r" % (name, res))
        if not complete(data):
            raise Failed("%s: not the beginning of a document: %r" % (name, data))


class Gen:
    """Random documents, whose strings have all that needs care."""
    POOL = 'ab z"\\/\b\f\n\r\t\x00\x01\x1f\x7f\x80\xe4\xff\u0100\u07ff\u0800\u2028\u20ac\ud7ff\ue000\ufffd\ufffe\uffff\U00010000\U0001F600\U0010FFFF'

    def __init__(self, rng, reals):
        self.rng = rng
        self.reals = reals

    def string(self):
        return "".join(self.rng.choice(self.POOL) for _ in range(self.rng.randrange(0, 12)))

    def real(self):
        r = self.rng
        return r.choice([0.0, -0.0, 1.0, -1.5, 1e5, 1e21, 1e-7, r.random(), r.uniform(-1e9, 1e9),
                         r.random() * 10.0 ** r.randrange(-300, 300)])

    def scalar(self):
        r = self.rng
        k = r.randrange(5 + self.reals)
        if k == 0:
            return None
        if k == 1:
            return r.random() < 0.5
        if k == 2:
            return r.choice([0, 1, -1, r.randrange(FIRST_INT, LAST_INT)])
        if k <= 4:
            return self.string()
        return self.real()

    def value(self, depth):
        r = self.rng
        k = r.random()
        if depth <= 0 or k < 0.4:
            return self.scalar()
        n = r.randrange(0, 7)
        if k < 0.7:
            return [self.value(depth - 1) for _ in range(n)]
        d = {}
        for _ in range(n):
            d[self.string()] = self.value(depth - 1)
        return d


def dumped(v, indent):
    if indent is None:
        return json.dumps(v, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    return (json.dumps(v, ensure_ascii=False, indent=indent) + "\n").encode("utf-8")


def case_random(env):
    rng = random.Random(SEED)
    docs = []
    models = []
    for i in range(env.args.count):
        reals = i % 2
        v = Gen(rng, reals).value(rng.randrange(0, 7))
        indent = None if reals else rng.choice([None, None, "", " ", "  ", "\t"])
        models.append((v, indent, bool(reals)))
        docs.append((Options(indent=indent), calls(v, None, rng) + [FINISH]))
    for i, ((v, indent, reals), (data, res, tree)) in enumerate(zip(models, env.scripts(docs))):
        label = "document %d" % i
        expect_ok(label, res)
        if not reals and data != dumped(v, indent):
            raise Failed("%s:\n  got  %r\n  want %r" % (label, data[:400], dumped(v, indent)[:400]))
        if tree is None:
            raise Failed(label + ": no tree")
        check_read(v, data, tree, label)


def bmp():
    return [chr(c) for c in range(0x10000) if not 0xD800 <= c <= 0xDFFF]


def case_chars(env):
    chars = bmp()
    rng = random.Random(SEED)
    astral = [chr(rng.randrange(0x10000, 0x110000)) for _ in range(3000)] + ["\U00010000", "\U0010FFFF"]
    whole = "".join(chars + astral)
    keys = {c: i for i, c in enumerate(chars + astral)}
    docs = [(D, [A()] + [T(c) for c in chars + astral] + [END, FINISH]),
            (Options(), [T(whole), FINISH]),
            (Options(), [O()] + [I(i, c) for c, i in keys.items()] + [END, FINISH])]
    want = [chars + astral, whole, keys]
    for i, (v, (data, res, tree)) in enumerate(zip(want, env.scripts(docs))):
        expect_ok("characters %d" % i, res)
        if data != dumped(v, None):
            raise Failed("characters %d: the bytes differ from those of json.dumps" % i)
        check_read(v, data, tree, "characters %d" % i)

    # a text goes through in chunks of 4096 code units
    pair = "\U0001F600"
    edge = []
    for n in (4094, 4095, 4096, 8190, 8191, 8192):
        edge.append("a" * n + pair + "b")
        edge.append("\u20ac" * n + pair + "\n")
    got = env.scripts([(Options(), [T(s), FINISH]) for s in edge])
    for s, (data, res, tree) in zip(edge, got):
        expect_ok("a pair on the edge", res)
        if data != dumped(s, None):
            raise Failed("a pair on the edge: the bytes differ")
        check_read(s, data, tree, "a pair on the edge")

    # surrogates without a partner, also on the edge
    bad = ["\ud800", "\udbff", "\udc00", "\udfff", "a\ud83d", "\ud83db", "\ude00\ud83d",
           "\ud83d\ud83d\ude00", "a" * 4095 + "\ud83d" + "b", "a" * 4095 + "\ud83d", "a" * 4096 + "\ude00"]
    refused = env.scripts([(D, [T(s), FINISH]) for s in bad])
    replace = Options(replace=True, tree=False)
    replaced = env.scripts([(replace, [T(s), FINISH]) for s in bad])
    lone = re.compile("[\ud800-\udfff]")
    for s, r1, r2 in zip(bad, refused, replaced):
        if r1[1] != ["ERROR Content", "AFTER Closed"]:
            raise Failed("%r: the result is %r" % (s[-8:], r1[1]))
        if not complete(r1[0]):
            raise Failed("%r: not the beginning of a document" % s[-8:])
        expect_ok("replaced", r2[1])
        want = lone.sub("\ufffd", s.encode("utf-16", "surrogatepass").decode("utf-16", "surrogatepass"))
        if read(r2[0]) != want:
            raise Failed("%r: replaced as %r" % (s[-8:], read(r2[0])[-8:]))


def case_reals(env):
    rng = random.Random(SEED)

    def bits():
        while True:
            x = struct.unpack("<d", struct.pack("<Q", rng.getrandbits(64)))[0]
            if math.isfinite(x):
                return x

    special = [0.0, -0.0, 1.0, -1.0, 0.1, 1 / 3, 1e5, 99999.0, 100000.5, 1e6, 1e21, 1e22, 1e23, 5e-324, -5e-324,
               2.2250738585072014e-308, 2.225073858507201e-308, 1.7976931348623157e308, -1.7976931348623157e308,
               4503599627370496.0, 9007199254740993.0, 123456.0, 1234567.0]
    small = [special] + [[bits() for _ in range(50)] for _ in range(400)]
    docs = [(Options(), [A()] + [R(x) for x in v] + [END, FINISH]) for v in small]
    many = [bits() for _ in range(200000)]
    docs.append((D, [A()] + [R(x) for x in many] + [END, FINISH]))
    got = env.scripts(docs)
    for i, (v, (data, res, tree)) in enumerate(zip(small + [many], got)):
        expect_ok("reals %d" % i, res)
        check_read(v, data, tree, "reals %d" % i)
    for x, (data, res, _) in zip(special, env.scripts([(D, [R(x), FINISH]) for x in special])):
        s = data.decode("ascii")
        if not NUMBER.fullmatch(s) or not ("." in s or "e" in s):
            raise Failed("%r is written as %s" % (x, s))


def case_ints(env):
    rng = random.Random(SEED)
    v = [0, 1, -1, 9, 10, LAST_INT, FIRST_INT, LAST_INT - 1, FIRST_INT + 1, 2 ** 53, 2 ** 53 + 1]
    v += [rng.randrange(FIRST_INT, LAST_INT) for _ in range(200)]
    for name, ops in (("array", [A()] + [I(x) for x in v] + [END]),):
        data, res, tree = env.one(Options(), ops + [FINISH])
        expect_ok(name, res)
        if data != dumped(v, None):
            raise Failed("%s: the bytes differ" % name)
        check_read(v, data, tree, name)


def case_numbers(env):
    good = ["0", "-0", "1", "-1", "10", "0.0", "0.5", "-0.5", "1e5", "1E5", "1e+5", "1E-5", "1.5e10", "0e0", "0E+0",
            "-0.0e-0", "123456789012345678901234567890", "1" + "0" * 10000, "0." + "1" * 10000]
    bad = ["", "+1", "01", "-01", "00", "1.", ".5", "-.5", "-", "e5", "1e", "1e+", "1E-", "1.e5", "1.5.5", "1e5.5",
           "1e5e5", "--1", "NaN", "Infinity", "-Infinity", "0x10", " 1", "1 ", "1\n", "1,2", "\u0663", "1\u0663",
           "\uff11", "1\x00"]
    rng = random.Random(SEED)
    rand = ["".join(rng.choice("0123456789+-.eE") for _ in range(rng.randrange(1, 9))) for _ in range(3000)]
    table = [(s, True) for s in good] + [(s, False) for s in bad] + [(s, NUMBER.fullmatch(s) is not None) for s in rand]
    if sum(1 for s, ok in table if ok) < 60:
        raise Failed("too few of the random texts are numbers")
    got = env.scripts([(D, [NUM(s), FINISH]) for s, _ in table])
    for (s, ok), (data, res, _) in zip(table, got):
        if ok:
            expect_ok(repr(s[:40]), res)
            if data != s.encode("ascii"):
                raise Failed("%r is written as %r" % (s[:40], data[:40]))
        elif res != ["ERROR Content", "AFTER Closed"]:
            raise Failed("%r: the result is %r" % (s[:40], res))


def nested(n, kind):
    v = 1
    for _ in range(n):
        v = [v] if kind == 0 else {"a": v}
    return v


def case_depth(env):
    def ops(n, kind):
        if kind == 0:
            return [A()] * n + [I(1)] + [END] * n + [FINISH]
        return [O()] + [O("a")] * (n - 1) + [I(1, "a")] + [END] * n + [FINISH]

    got = env.scripts([(Options(), ops(1000, 0)), (Options(), ops(1000, 1))])
    for kind, (data, res, tree) in enumerate(got):
        expect_ok("depth 1000", res)
        if tree is None:
            raise Failed("depth 1000: no tree")
        w = "[" * 1000 + "1" + "]" * 1000 if kind == 0 else '{"a":' * 1000 + "1" + "}" * 1000
        if data != w.encode("ascii"):
            raise Failed("depth 1000: the bytes differ")
        t = tree
        if t.count("[ 1") + t.count("{ 1") != 1000:
            raise Failed("depth 1000: Json has %d levels" % (t.count("[ 1") + t.count("{ 1")))

    for kind in (0, 1):
        n = 1001
        data, res, _ = env.one(D, ops(n, kind))
        if res != ["ERROR Structure", "AFTER Closed"]:
            raise Failed("depth 1001: the result is %r" % res)
        if not data.endswith(b"[" * 1000 if kind == 0 else b'{"a":' * 999 + b'{"a":') or len(data) > 6000:
            raise Failed("depth 1001: wrote %r" % data[-40:])

    n = 50000
    data, res, _ = env.one(Options(depth=0, tree=False), ops(n, 0))
    expect_ok("no limit", res)
    if data != b"[" * n + b"1" + b"]" * n:
        raise Failed("no limit: the bytes differ")
    data, res, _ = env.one(Options(depth=2, tree=False), [A(), A(), END, A(), END, END, FINISH])
    expect_ok("depth 2", res)
    if data != b"[[],[]]":
        raise Failed("depth 2: %r" % data)


def case_dups(env):
    docs = []
    want = []
    for n in (1, 2, 63, 64, 65, 66, 200, 3000):
        keys = ["k%d" % i for i in range(n)]
        ok = [O()] + [I(i, k) for i, k in enumerate(keys)]
        docs.append((Options(), ok + [END, FINISH]))
        want.append(("OK", {k: i for i, k in enumerate(keys)}))
        for k in {keys[0], keys[n // 2], keys[-1]}:
            docs.append((D, ok + [I(0, k)] + [END, FINISH]))
            want.append(("Structure", None))
            docs.append((D, ok + [K(k)] + [I(0)] + [END, FINISH]))
            want.append(("Structure", None))
    # what is open is kept apart, and an object that ended is forgotten
    v = {"a": {"a": {"a": 1}, "b": {"a": 2}}, "b": {"a": 3}, "c": [{"a": 4}, {"a": 5}]}
    docs.append((Options(), calls(v) + [FINISH]))
    want.append(("OK", v))
    big_key = "x" * 70
    many = {"k%d" % i: {big_key: i, "k%d" % i: i} for i in range(100)}
    docs.append((Options(), calls(many) + [FINISH]))
    want.append(("OK", many))
    docs.append((D, [O(), O("a"), I(1, "x"), END, I(2, "a"), END, FINISH]))
    want.append(("Structure", None))
    # after a deep object has used the table, its frame serves another
    wide = {"k%d" % i: i for i in range(100)}
    v = [wide, dict(wide), {"k1": 1}]
    docs.append((Options(), calls(v) + [FINISH]))
    want.append(("OK", v))
    # switched off
    off = Options(unique=False, tree=False)
    docs.append((off, [O(), I(1, "a"), I(2, "a"), END, FINISH]))
    want.append(("BYTES", b'{"a":1,"a":2}'))
    docs.append((off, [O()] + [I(i, "same") for i in range(200)] + [END, FINISH]))
    want.append(("BYTES", ("{" + ",".join('"same":%d' % i for i in range(200)) + "}").encode("ascii")))
    for i, ((kind, v), (data, res, tree)) in enumerate(zip(want, env.scripts(docs))):
        label = "document %d" % i
        if kind == "Structure":
            if res != ["ERROR Structure", "AFTER Closed"]:
                raise Failed("%s: the result is %r" % (label, res))
            if not complete(data):
                raise Failed("%s: not the beginning of a document" % label)
        elif kind == "BYTES":
            expect_ok(label, res)
            if data != v:
                raise Failed("%s: %r" % (label, data[:100]))
        else:
            expect_ok(label, res)
            check_read(v, data, tree, label)


def case_sink(env):
    got = env.run("sink").replace("\r\n", "\n")
    want = (TESTS / "expected" / "sink.txt").read_text(encoding="ascii").replace("\r\n", "\n")
    if got != want:
        (env.work / "sink.got").write_text(got, newline="\n")
        a = got.splitlines()
        b = want.splitlines()
        first = next((i for i, (x, y) in enumerate(zip(a, b)) if x != y), min(len(a), len(b)))
        raise Failed("line %d:\n  got  %s\n  want %s" % (first + 1, a[first:first + 1], b[first:first + 1]))


def big_hash(mb):
    """The hash and length of the document that the mode big writes."""
    h = hashlib.sha256()
    left = mb * 1000 * 1000
    n = 0
    size = 0
    part = ["["]
    first = True
    while left > 0:
        n += 1
        line = '%s{"id":%d,"t":"text %d"}' % ("" if first else ",", n, n)
        first = False
        left -= 20 + 2 * len(str(n))
        part.append(line)
        if len(part) >= 50000:
            b = "".join(part).encode("ascii")
            h.update(b)
            size += len(b)
            part.clear()
    part.append("]")
    b = "".join(part).encode("ascii")
    h.update(b)
    return h.hexdigest(), size + len(b)


def case_big(env):
    mb = 200
    want = big_hash(mb)
    p = subprocess.Popen([env.java, "-Xmx64m", "-cp", env.cp, "m3.Main", "big", str(mb)], cwd=env.work,
                         stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    err = []
    drain = threading.Thread(target=lambda: err.append(p.stderr.read()), daemon=True)
    drain.start()
    h = hashlib.sha256()
    size = 0
    while True:
        b = p.stdout.read(1 << 20)
        if not b:
            break
        h.update(b)
        size += len(b)
    code = p.wait(timeout=env.args.timeout)
    drain.join(10)
    if code or err[0]:
        raise Failed("exit %d: %s" % (code, err[0][:500]))
    if (h.hexdigest(), size) != want:
        raise Failed("got %d bytes %s, want %d bytes %s" % (size, h.hexdigest(), want[1], want[0]))


def speed(env):
    print("SPEED     " + env.run("speed", "1000").strip(), flush=True)


CASES = {
    "fixed": case_fixed,
    "errors": case_errors,
    "random": case_random,
    "chars": case_chars,
    "reals": case_reals,
    "ints": case_ints,
    "numbers": case_numbers,
    "depth": case_depth,
    "dups": case_dups,
    "sink": case_sink,
    "big": case_big,
}


def main():
    ap = argparse.ArgumentParser(description="tests of JsonWriter")
    ap.add_argument("cases", nargs="*")
    ap.add_argument("--java", default=str(Path(os.environ.get("JDK25", "")) / "bin" / "java") if os.environ.get("JDK25") else "java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--timeout", type=float, default=240)
    ap.add_argument("--count", type=int, default=1500, help="random documents")
    ap.add_argument("--speed", action="store_true")
    args = ap.parse_args()
    if sys.platform == "win32" and not args.java.endswith(".exe") and Path(args.java + ".exe").exists():
        args.java += ".exe"

    names = args.cases or list(CASES)
    for n in names:
        if n not in CASES:
            ap.error("unknown case " + n)
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="m3j_json_"))
    work.mkdir(parents=True, exist_ok=True)
    env = Env(args, work)
    failed = 0
    start = time.time()
    try:
        env.compile()
        for name in names:
            try:
                CASES[name](env)
                print("PASS      " + name, flush=True)
            except (Failed, subprocess.TimeoutExpired, OSError, ValueError) as e:
                failed += 1
                print("FAIL      %s: %s" % (name, e), flush=True)
        if args.speed:
            speed(env)
    except Failed as e:
        failed += 1
        print("FAIL      %s" % e, flush=True)
    finally:
        if not args.keep and not args.work:
            shutil.rmtree(work, ignore_errors=True)
    print("failed %d, %.0fs" % (failed, time.time() - start))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
