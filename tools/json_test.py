#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Tests of JsonWriter and JsonReader of the library json of m3j, through
tests/jsonwriter/src/Main.m3, which carries out scripts of calls, and
tests/jsonreader/src/Main.m3, which reads documents. They need no connection
to the internet.

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

The cases of the reader; the json module of Python, made strict about what it
lets through, says what a document holds and whether it is one:

  rfixed   documents with their pieces, checked by hand
  rerrors  what must be refused, with the place of the error, however it comes
  rrandom  random documents against their model, read in every way, and
           written again by JsonWriter
  rfuzz    documents with random damage, accepted exactly if Python accepts
  rchunks  every document in pieces of any size gives the same result
  rchars   each character in a value and in a key, raw and as an escape;
           surrogates without a partner; pairs on the edge of a chunk of a text
  rutf8    all short sequences of bytes, refused or replaced like Python does
  rreals   random numbers from their bits and hard cases, to the bit
  rints    the limits of INTEGER, and what is not an integer
  rnumbers numbers as texts against the grammar of RFC 8259, long numbers
  rdepth   the limit of nesting; a million levels without a limit
  rlimits  the limits of strings and numbers, to the character
  rapi     calls one by one: what takes nothing, what ends the reader, positions
  rfeed    readers that fail or block, Open and close (expected output in
           tests/jsonreader/expected)
  rbig     a generated document under a small heap, checked record by record

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
READER = ROOT / "tests" / "jsonreader"
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
        self.reader_classes = work / "reader_classes"
        self.batches = 0

    def compile(self, tests=TESTS, classes=None):
        cmd = [self.java, "-Xss64m", "-cp", self.args.jar, "m3j.jvm.Compile", str(tests / "src" / "Main.m3"),
               "-d", str(classes or self.classes)]
        r = subprocess.run(cmd, cwd=self.work, capture_output=True, text=True, stdin=subprocess.DEVNULL)
        if r.returncode:
            raise Failed("compile:\n" + r.stdout + r.stderr)

    def rrun(self, *a, heap=None):
        return self.run(*a, heap=heap, cp=str(self.reader_classes) + os.pathsep + self.args.jar)

    def reads(self, docs):
        """Reads each Read; gives the lines of the result of each."""
        self.batches += 1
        name = "r%d" % self.batches
        (self.work / name).write_text("".join("%s %s %s\n" % (d.head(), d.how, d.hex()) for d in docs), newline="\n",
                                      encoding="ascii")
        self.rrun("batch", name, name + ".out", heap="2g")
        out = (self.work / (name + ".out")).read_text(encoding="ascii").split(".\n")
        (self.work / name).unlink()
        (self.work / (name + ".out")).unlink()
        return [b.splitlines() for b in out[:len(docs)]]

    def run(self, *a, heap=None, cp=None):
        opts = ["-Xmx" + heap] if heap else []
        r = subprocess.run([self.java, *opts, "-Xss64m", "-cp", cp or self.cp, "m3.Main", *a], cwd=self.work, capture_output=True,
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


# ---------------------------------------------------------------- the reader

WHOLE = 1 << 30
PIECES = -1                     # delivered in pieces of random sizes
FROM_TEXT = -2                  # the document is a text
BOM = b"\xef\xbb\xbf"
MAX_STRING = 64 * 1024 * 1024
MAX_NUMBER = 64 * 1024
LONE = re.compile("[\ud800-\udfff]")


class Read:
    """A document for the reader, and how it is read: its pieces, the options,
    and e for all the pieces, s to skip it, w to write it again."""

    def __init__(self, data, chunk=WHOLE, replace=False, depth=1000, string=MAX_STRING, number=MAX_NUMBER, how="e"):
        self.data = data
        self.chunk = chunk
        self.replace = replace
        self.depth = depth
        self.string = string
        self.number = number
        self.how = how

    def head(self):
        return "%d %d %d %d %d" % (self.chunk, self.replace, self.depth, self.string, self.number)

    def hex(self):
        if self.chunk == FROM_TEXT:
            return self.data.encode("utf-16-be", "surrogatepass").hex()
        return self.data.hex()


class Num(str):
    pass


class Pairs(list):
    pass


def units(s):
    return s.encode("utf-16-be", "surrogatepass").hex()


def events(v, fix=False):
    """The pieces of a value, as the test program prints them."""
    out = []

    def text(s):
        return units(LONE.sub("\ufffd", s) if fix else s)

    def go(x):
        if isinstance(x, Pairs):
            out.append("{")
            for k, y in x:
                out.append("K " + text(k))
                go(y)
            out.append("}")
        elif isinstance(x, list):
            out.append("[")
            for y in x:
                go(y)
            out.append("]")
        elif isinstance(x, Num):
            out.append("N " + x)
        elif isinstance(x, str):
            out.append("T " + text(x))
        elif x is None:
            out.append("Z")
        else:
            out.append("B " + ("TRUE" if x else "FALSE"))

    go(v)
    return out


def no_constant(c):
    raise ValueError(c)


def strict_loads(text):
    return json.loads(text, parse_constant=no_constant, parse_int=Num, parse_float=Num, object_pairs_hook=Pairs)


def has_lone(v):
    if isinstance(v, Pairs):
        return any(has_lone(k) or has_lone(y) for k, y in v)
    if isinstance(v, list):
        return any(has_lone(y) for y in v)
    return isinstance(v, str) and not isinstance(v, Num) and LONE.search(v) is not None


def oracle_text(text, fix=False):
    """The pieces of a document that RFC 8259 allows, otherwise None."""
    try:
        v = strict_loads(text)
    except (ValueError, RecursionError):
        return None
    if has_lone(v) and not fix:
        return None
    return events(v, fix)


def oracle(data, fix=False):
    if data.startswith(BOM):
        data = data[3:]
    try:
        return oracle_text(data.decode("utf-8", "replace" if fix else "strict"), fix)
    except UnicodeDecodeError:
        return None


def agree(label, doc, lines, expected):
    """expected is the list of pieces, or None if the document must be refused."""
    if expected is None:
        if len(lines) < 2 or not lines[-2].startswith("ERROR ") or lines[-1] != "AFTER Closed":
            raise Failed("%s: accepted %r: %s" % (label, doc[:200], lines[-3:]))
    elif lines != expected + ["OK"]:
        raise Failed("%s: %r:\n  expected %s\n  got      %s" % (label, doc[:200], expected[:40], lines[:40]))


def rand_string(r):
    alpha = ["a", "z", "0", " ", "/", "\\", '"', "\b", "\f", "\n", "\r", "\t", "\x00", "\x1f", "\x7f", "\x80",
             "\u00e4", "\u07ff", "\u0800", "\u20ac", "\uffff", "\U00010000", "\U0001f600", "\U0010ffff"]
    return "".join(r.choice(alpha) for _ in range(r.randrange(r.choice([1, 3, 8, 40]))))


def rand_number(r):
    i = r.choice(["0", "1", "9", "10", "123456789", "9223372036854775807", "340282366920938463463374607431768211456"])
    return Num(r.choice(["", "-"]) + i + r.choice(["", "", ".0", ".5", ".000123"]) +
               r.choice(["", "", "e0", "E5", "e+12", "e-7", "E-0"]))


def rand_value(r, depth):
    k = r.randrange(8 if depth < 6 else 5)
    if k == 0:
        return rand_string(r)
    if k == 1:
        return rand_number(r)
    if k == 2:
        return r.choice([True, False])
    if k == 3:
        return None
    if k == 4:
        return r.choice(["", Num("0"), Pairs(), []])
    if k in (5, 6):
        return [rand_value(r, depth + 1) for _ in range(r.randrange(5))]
    keys = [rand_string(r) for _ in range(r.randrange(5))]
    if keys and r.randrange(4) == 0:
        keys.append(keys[0])
    return Pairs((key, rand_value(r, depth + 1)) for key in keys)


def write_string(r, s):
    short = {'"': '\\"', "\\": "\\\\", "\b": "\\b", "\f": "\\f", "\n": "\\n", "\r": "\\r", "\t": "\\t"}

    def u(c):
        h = units(c)
        return "".join("\\u" + (h[i:i + 4].upper() if r.randrange(2) else h[i:i + 4]) for i in range(0, len(h), 4))

    out = ['"']
    for c in s:
        if c in short:
            out.append(short[c] if r.randrange(3) else u(c))
        elif c < " ":
            out.append(u(c))
        elif c == "/" and r.randrange(2):
            out.append("\\/")
        elif r.randrange(4) == 0:
            out.append(u(c))
        else:
            out.append(c)
    out.append('"')
    return "".join(out)


def write_value(r, v):
    """v as a document, with white space and escapes chosen at random."""

    def ws():
        return r.choice(["", "", "", " ", "\n", "\t ", "\r\n  "])

    def go(x):
        if isinstance(x, Pairs):
            if not x:
                return "{" + ws() + "}"
            return "{" + ",".join(ws() + write_string(r, k) + ws() + ":" + ws() + go(y) + ws() for k, y in x) + "}"
        if isinstance(x, list):
            if not x:
                return "[" + ws() + "]"
            return "[" + ",".join(ws() + go(y) + ws() for y in x) + "]"
        if isinstance(x, Num):
            return str(x)
        if isinstance(x, str):
            return write_string(r, x)
        if x is None:
            return "null"
        return "true" if x else "false"

    return ws() + go(v) + ws()


def compact(v):
    if isinstance(v, Pairs):
        return "{" + ",".join(json.dumps(k, ensure_ascii=False) + ":" + compact(y) for k, y in v) + "}"
    if isinstance(v, list):
        return "[" + ",".join(compact(y) for y in v) + "]"
    if isinstance(v, Num):
        return str(v)
    return json.dumps(v, ensure_ascii=False)


def rand_docs(env, count, seed):
    r = random.Random(seed)
    docs = []
    for _ in range(count):
        v = rand_value(r, 0)
        docs.append((v, write_value(r, v).encode("utf-8")))
    return docs


FIXED_READS = [
    (b'{"a":1}', ["{", "K 0061", "N 1", "}"]),
    (b"[]", ["[", "]"]),
    (b"{}", ["{", "}"]),
    (b" \t\r\n[ 1 , 2 ]\n", ["[", "N 1", "N 2", "]"]),
    (b'[[],{},[{}]]', ["[", "[", "]", "{", "}", "[", "{", "}", "]", "]"]),
    (b'"x"', ["T 0078"]),
    (b'""', ["T "]),
    (b"true", ["B TRUE"]),
    (b"false", ["B FALSE"]),
    (b"null", ["Z"]),
    (b"-0", ["N -0"]),
    (b"0.5e+3", ["N 0.5e+3"]),
    (b"1E2", ["N 1E2"]),
    (BOM + b"[]", ["[", "]"]),
    (b'"\\"\\\\\\/\\b\\f\\n\\r\\t\\u0041"', ["T 0022005c002f0008000c000a000d00090041"]),
    ('"\u00e4\u00f6\u20ac\U0001f600"'.encode("utf-8"), ["T 00e400f620acd83dde00"]),
    (b'"\\uD83D\\uDE00"', ["T d83dde00"]),
    (b'"\\ud83d\\ude00x"', ["T d83dde000078"]),
    (b'"\\u0000"', ["T 0000"]),
    (b'"\x7f"', ["T 007f"]),
    (b'"\xef\xbf\xbe\xef\xbf\xbf"', ["T fffeffff"]),
    (b'{"a":1,"a":2}', ["{", "K 0061", "N 1", "K 0061", "N 2", "}"]),
    (b'{"":null}', ["{", "K ", "Z", "}"]),
    (b'{"a":{"b":[true]}}', ["{", "K 0061", "{", "K 0062", "[", "B TRUE", "]", "}", "}"]),
]

# What takes the place of something invalid stands where that was.
REPLACED_READS = [
    (b'"\\ud800\\n"', "fffd000a"),
    (b'"\\ud800a"', "fffd0061"),
    ('"\\ud800\u00e4"'.encode("utf-8"), "fffd00e4"),
    (b'"\\ud800\\u0041"', "fffd0041"),
    (b'"\\ud800\\ud800\\udc00"', "fffdd800dc00"),
    (b'"\\udc00\\ud800"', "fffdfffd"),
    (b'"a\\ud800"', "0061fffd"),
    (b'"\\ud83d\\ude00\\ud83d"', "d83dde00fffd"),
    (b'"\xffa"', "fffd0061"),
    (b'"\xe2\x82a"', "fffd0061"),
    (b'"\xf0\x9f\x98\\n"', "fffd000a"),
    (b'"\xed\xa0\x80"', "fffdfffdfffd"),
]

# What must be refused; @@ marks the place of the error. Encoding where it says
# so, else Malformed.
M, E = "Malformed", "Encoding"
REFUSED = [
    (b"@@", M), (b"  \n @@", M), (b"@@x", M), (b"@@True", M), (b"tru@@", M), (b"tru@@x", M), (b"nul@@L", M),
    (b"fals@@", M), (b"f@@", M), (b"@@NaN", M), (b"@@Infinity", M), (b"-@@Infinity", M), (b"@@+1", M), (b"@@.5", M),
    (b"1.@@", M), (b"1.@@e5", M), (b"1e@@", M), (b"1e+@@", M), (b"1e@@x", M), (b"-@@", M), (b"0@@1", M), (b"-0@@1", M),
    (b"1.5@@.5", M), (b"0@@x10", M), (b"1 @@2", M), (b'"a"@@"b"', M), (b"1@@\x00", M), (b"[1]@@]", M), (b"{}@@{}", M),
    (b"null@@x", M), (b"true@@1", M),
    (b"[@@", M), (b"[1@@", M), (b"[1,@@", M), (b"[1,@@]", M), (b"[@@,1]", M), (b"[1 @@2]", M), (b"[1,@@,2]", M),
    (b"[1@@:2]", M), (b"[@@}", M), (b"[1@@}", M), (b"[[1]@@", M), (b"[01@@", M) if False else (b"[0@@1]", M),
    (b"{@@", M), (b"{@@1:2}", M), (b'{"a"@@', M), (b'{"a" @@1}', M), (b'{"a":@@', M), (b'{"a":@@}', M),
    (b'{"a":1@@', M), (b'{"a":1,@@', M), (b'{"a":1,@@}', M), (b'{"a":1 @@"b":2}', M), (b'{"a":1,@@2:3}', M),
    (b"{@@,}", M), (b"{@@]", M), (b'{"a":1@@]', M), (b'{"a"@@,1}', M), (b'{"a"::@@1}'.replace(b"::@@", b":@@:"), M),
    (b'{@@a:1}', M), (b"{@@'a':1}", M), (b"@@'a'", M), (b"[@@'x']", M), (b"[1@@/*c*/]", M), (b"@@//c\n1", M),
    (b"[@@\x0b1]", M), (b"[@@\x0c1]", M), (b"[@@\xa01]", M),
    (b'"@@', M), (b'"abc@@', M), (b'"a\\@@', M), (b'"@@\\q"', M), (b'"a@@\\x41"', M), (b'"\\u12@@G4"', M),
    (b'"\\u12@@', M), (b'"\\u@@', M), (b'"\\u@@"', M), (b'"\\uD83D\\u@@', M),
    (b'"a@@\x01b"', M), (b'"@@\n"', M), (b'"@@\t"', M), (b'"@@\x00"', M), (b'"@@\x1f"', M),
    (b'"a@@\xffb"', E), (b'"@@\xc0\x80"', E), (b'"@@\xc1\xbf"', E), (b'"@@\xe0\x80\x80"', E), (b'"@@\xed\xa0\x80"', E),
    (b'"@@\xed\xbf\xbf"', E), (b'"@@\xf4\x90\x80\x80"', E), (b'"@@\xf5\x80\x80\x80"', E), (b'"@@\xf0\x80\x80\x80"', E),
    (b'"@@\x80"', E), (b'"@@\xbf"', E), (b'"@@\xc3"', E), (b'"@@\xe2\x82"', E), (b'"@@\xf0\x9f\x98"', E),
    (b'"\xc3\xa4@@\xc3', E),
    (b'"@@\\ud800"', E), (b'"@@\\udc00"', E), (b'"@@\\ud800\\u0041"', E), (b'"@@\\ud83d x"', E), (b'"@@\\ud800\\n"', E),
    (b'"a@@\\ud800"', E), (b'"\\ud83d\\ude00@@\\ud83d"', E), (b'"@@\\ud800\\ud800\\udc00"', E),
    (b'"@@\\udc00\\ud800"', E), (b'"@@\\ud800\xc3\xa4"', E),
    (b'{"@@\xff":1}', E), (b'{"@@\\ud800":1}', E), (b"@@\xc3\xa4", M), (b"[@@\xff]", M),
    (b"@@\xef\xbb", M), (b"@@\xef\xbbx", M), (b"@@\xef", M), (BOM + b"@@", M), (BOM + b"@@" + BOM + b"1", M),
    (b"[1,\n 2,\n@@x]", M), (b'{\r\n"a":1,\r\n@@}', M), (BOM + b"[@@x]", M), (BOM + b"\n@@x", M),
]


def marked(entry):
    """The bytes, and the failure to expect: problem, line, column, offset."""
    doc, problem = entry
    at = doc.index(b"@@")
    data = doc[:at] + doc[at + 2:]
    before = data[:at]
    line = before.count(b"\n") + 1
    start = before.rfind(b"\n") + 1
    if data.startswith(BOM) and line == 1 and at >= 3:
        start = 3
    return data, "ERROR %s %d %d %d" % (problem, line, at - start + 1, at)


def case_rfixed(env):
    docs = [Read(d) for d, _ in FIXED_READS]
    for (d, want), lines in zip(FIXED_READS, env.reads(docs)):
        agree("fixed", d, lines, want)
        if oracle(d) != want:
            raise Failed("the expectation disagrees with Python: %r" % d)
    for chunk in (WHOLE, 1, 3):
        docs = [Read(d, chunk=chunk, replace=True) for d, _ in REPLACED_READS]
        for (d, want), lines in zip(REPLACED_READS, env.reads(docs)):
            agree("replaced", d, lines, ["T " + want])
            if oracle(d, fix=True) != ["T " + want]:
                raise Failed("the expectation disagrees with Python: %r" % d)


def case_rerrors(env):
    for how, chunk in (("e", WHOLE), ("s", WHOLE), ("e", 1), ("s", 1), ("e", 2), ("e", PIECES)):
        cases = [marked(c) for c in REFUSED]
        res = env.reads([Read(d, chunk=chunk, how=how) for d, _ in cases])
        for (d, want), lines in zip(cases, res):
            if oracle(d) is not None:
                raise Failed("Python accepts what should be refused: %r" % d)
            if lines[-2:] != [want, "AFTER Closed"]:
                raise Failed("%s %d: %r: expected %s, got %s" % (how, chunk, d, want, lines[-3:]))


def case_rrandom(env):
    docs = rand_docs(env, env.args.count, SEED + 1)
    res = env.reads([Read(d) for _, d in docs])
    for (v, d), lines in zip(docs, res):
        want = oracle(d)
        if want != events(v):
            raise Failed("the document does not say what the model says: %r" % d[:300])
        agree("random", d, lines, want)
    res = env.reads([Read(d, how="s") for _, d in docs])
    for (v, d), lines in zip(docs, res):
        agree("skip", d, lines, [])
    res = env.reads([Read(d, how="w", depth=0) for _, d in docs])
    for (v, d), lines in zip(docs, res):
        agree("again", d, lines, ["W " + compact(v).encode("utf-8").hex()])


def mutate(r, d):
    d = bytearray(d)
    for _ in range(r.choice([1, 1, 1, 2, 3])):
        k = r.randrange(4)
        at = r.randrange(len(d) + 1)
        if k == 0 and at < len(d):
            d[at] = r.choice([r.randrange(256), r.choice(b'"\\{}[],:0-.eEtfnu \n\x00\x80\xc3\xed\xf0\xff')])
        elif k == 1 and at < len(d):
            del d[at]
        elif k == 2:
            d.insert(at, r.choice([r.randrange(256), r.choice(b'"\\{}[],:0-.eEtfnu \n\x00\x80\xc3\xed\xf0\xff')]))
        else:
            del d[at:]
    return bytes(d)


def case_rfuzz(env):
    r = random.Random(SEED + 2)
    docs = rand_docs(env, env.args.count, SEED + 3)
    bad = [mutate(r, docs[r.randrange(len(docs))][1]) for _ in range(env.args.count * 15)]
    accepted = 0
    res = env.reads([Read(d) for d in bad])
    for d, lines in zip(bad, res):
        want = oracle(d)
        accepted += want is not None
        agree("mutated", d, lines, want)
    if not accepted or accepted == len(bad):
        raise Failed("the mutations prove nothing: %d of %d accepted" % (accepted, len(bad)))
    res = env.reads([Read(d, replace=True) for d in bad])
    for d, lines in zip(bad, res):
        agree("replaced", d, lines, oracle(d, fix=True))


def case_rchunks(env):
    docs = [d for _, d in rand_docs(env, env.args.count, SEED + 4)] + [d for d, _ in map(marked, REFUSED)]
    whole = {how: env.reads([Read(d, how=how) for d in docs]) for how in "es"}
    for chunk in (1, 2, 3, 5, PIECES):
        for how in "es":
            res = env.reads([Read(d, chunk=chunk, how=how) for d in docs])
            for d, a, b in zip(docs, whole[how], res):
                if a != b:
                    raise Failed("pieces of %d: %r:\n  %s\n  %s" % (chunk, d[:200], a[-4:], b[-4:]))


def code_points():
    for c in range(0x10000):
        if not 0xd800 <= c <= 0xdfff:
            yield c
    r = random.Random(SEED + 5)
    for c in [0x10000, 0x10001, 0x1ffff, 0x20000, 0xfffff, 0x100000, 0x10fffe, 0x10ffff]:
        yield c
    for _ in range(5000):
        yield r.randrange(0x10000, 0x110000)


def case_rchars(env):
    cps = list(code_points())
    docs, wants = [], []
    for g in range(0, len(cps), 200):
        group = cps[g:g + 200]
        strs = [chr(c) for c in group]
        raw = [s for s in strs if s >= " " and s not in '"\\']

        def esc(s):
            h = units(s)
            return "".join("\\u" + h[i:i + 4] for i in range(0, len(h), 4))

        docs.append(("[" + ",".join('"%s"' % s for s in raw) + "]").encode("utf-8"))
        wants.append(["["] + ["T " + units(s) for s in raw] + ["]"])
        docs.append(("{" + ",".join('"%s":null' % s for s in raw) + "}").encode("utf-8"))
        wants.append(["{"] + [x for s in raw for x in ("K " + units(s), "Z")] + ["}"])
        docs.append(("[" + ",".join('"%s"' % esc(s) for s in strs) + "]").encode("ascii"))
        wants.append(["["] + ["T " + units(s) for s in strs] + ["]"])
    for d, want, lines in zip(docs, wants, env.reads([Read(d) for d in docs])):
        agree("characters", d, lines, want)
    for chunk in (FROM_TEXT,):
        ds = [d.decode("utf-8") for d in docs]
        for d, want, lines in zip(ds, wants, env.reads([Read(d, chunk=chunk) for d in ds])):
            agree("text", d.encode("utf-8"), lines, want)
    # every surrogate alone, and refused or replaced
    alone = [b'"\\u%04x"' % c for c in range(0xd800, 0xe000)]
    for d, lines in zip(alone, env.reads([Read(d) for d in alone])):
        if lines[-2] != "ERROR Encoding 1 2 1":
            raise Failed("%r: %s" % (d, lines))
    for d, lines in zip(alone, env.reads([Read(d, replace=True) for d in alone])):
        agree("replaced", d, lines, ["T fffd"])
    # a pair on the edge of a piece of a text
    for n in (16382, 16383, 16384, 32766, 32767, 32768):
        s = '"' + "a" * n + "\U0001f600" + '"'
        agree("edge", s[:9].encode("ascii"), env.reads([Read(s, chunk=FROM_TEXT)])[0],
              ["T " + units("a" * n + "\U0001f600")])
        s = '"' + "\u00e4" * n + "\U0001f600\u20ac" + '"'
        agree("edge", s[:9].encode("utf-8"), env.reads([Read(s, chunk=FROM_TEXT)])[0],
              ["T " + units("\u00e4" * n + "\U0001f600\u20ac")])
    # a text with a surrogate alone
    res = env.reads([Read('"a\ud800b"', chunk=FROM_TEXT), Read('"a\ud800b"', chunk=FROM_TEXT, replace=True)])
    if res[0][-2] != "ERROR Encoding 1 3 2":
        raise Failed("a surrogate alone in a text: %s" % res[0])
    agree("alone in a text", b"", res[1], ["T 0061fffd0062"])


def case_rutf8(env):
    edge = [0x00, 0x7f, 0x80, 0x8f, 0x90, 0x9f, 0xa0, 0xbf, 0xc0, 0xc1, 0xc2, 0xdf, 0xe0, 0xe1, 0xec, 0xed, 0xee, 0xef,
            0xf0, 0xf1, 0xf3, 0xf4, 0xf5, 0xff]
    r = random.Random(SEED + 6)
    seqs = [bytes([a]) for a in range(256)]
    seqs += [bytes([a, b]) for a in range(0x80, 0x100) for b in range(256)]
    seqs += [bytes([a, b, c]) for a in edge for b in edge for c in edge]
    seqs += [bytes(r.choice(edge) for _ in range(4)) for _ in range(30000)]
    docs = [b'"' + s + b'x"' for s in seqs]
    for d, lines in zip(docs, env.reads([Read(d) for d in docs])):
        agree("strict", d, lines, oracle(d))
    for d, lines in zip(docs, env.reads([Read(d, replace=True) for d in docs])):
        agree("replaced", d, lines, oracle(d, fix=True))
    keys = [b'{"' + s + b'x":0}' for s in seqs[:3000]]
    for d, lines in zip(keys, env.reads([Read(d, chunk=1) for d in keys])):
        agree("key", d, lines, oracle(d))


def number_results(env, texts):
    env.batches += 1
    name = "n%d" % env.batches
    (env.work / name).write_text("".join(t + "\n" for t in texts), newline="\n", encoding="ascii")
    env.rrun("numbers", name, name + ".out")
    return (env.work / (name + ".out")).read_text(encoding="ascii").splitlines()


def real_of(text):
    return float(text)


def case_rreals(env):
    r = random.Random(SEED + 7)
    texts = []
    while len(texts) < 200000:
        b = r.getrandbits(64)
        if r.randrange(4) == 0:
            b &= ~(0x7ff << 52)    # subnormal
        x = struct.unpack("<d", struct.pack("<Q", b))[0]
        if math.isfinite(x):
            texts.append(r.choice([repr(x), "%.17e" % x, "%.3e" % x, "%.40e" % x]))
    texts += ["5e-324", "2.4703282292062328e-324", "2.4703282292062327e-324", "2.2250738585072014e-308",
              "2.2250738585072011e-308", "1.7976931348623157e308", "1.7976931348623158e308", "1.7976931348623159e308",
              "1e308", "1e309", "-1e309", "1e-400", "-1e-400", "0e999999999999999999999", "1e-99999999999999999999",
              "1e99999999999999999999", "0.0", "-0.0", "-0", "9007199254740993", "9007199254740992.5", "1e23",
              "8.41e21", "2.0e-322", "0.000000000000000000000000000000000000000000000001",
              "1" + "0" * 400 + "e-400", "0." + "0" * 400 + "1e401", "179769313486231580793728971405303415079934132710"
              "037826936173778980444968292764750946649017977587207096330286416692887910946555547851940402630657488671"
              "505820681908902000708383676273854845817711531764475730270069855571366959622842914819860834936475292719"
              "074600444e0"]
    res = number_results(env, texts)
    for t, line in zip(texts, res):
        f = line.split(" ")
        want = real_of(t)
        if not math.isfinite(want):
            if f[3] != "BadValue":
                raise Failed("%s: %s" % (t, line))
            continue
        got = float(f[3])
        if struct.pack("<d", got) != struct.pack("<d", want):
            raise Failed("%s: expected %r: %s" % (t, want, line))
        if f[5] != t:
            raise Failed("%s: number gives %s" % (t, line))


def case_rints(env):
    r = random.Random(SEED + 8)
    good = [FIRST_INT, FIRST_INT + 1, LAST_INT, LAST_INT - 1, 0, 1, -1, 10, -10, 922337203685477580, -922337203685477580,
            922337203685477581, 9223372036854775799, -9223372036854775799]
    bad = [FIRST_INT - 1, LAST_INT + 1, 2 ** 64, -2 ** 64, 92233720368547758070, 10 ** 30, 9223372036854775810]
    good += [r.randrange(FIRST_INT, LAST_INT) >> r.randrange(64) for _ in range(20000)]
    texts = [str(n) for n in good + bad] + ["-0", "1.0", "1e2", "1E0", "0.0", "-0.0", "12e-1"]
    res = number_results(env, texts)
    for i, (t, line) in enumerate(zip(texts, res)):
        f = line.split(" ")
        if i < len(good):
            if f[1] != str(good[i]):
                raise Failed("%s: %s" % (t, line))
        elif i < len(good) + len(bad) or t != "-0":
            if f[1] != "BadValue":
                raise Failed("%s: %s" % (t, line))
        elif f[1] != "0":
            raise Failed("%s: %s" % (t, line))


def case_rnumbers(env):
    r = random.Random(SEED + 9)
    texts = ["", "-", "00", "01", "-01", "1.", ".1", "1e", "1e+", "+1", "1.e1", "0e", "1.5e", "--1", "1-", "1e1e1", "e1"]
    while len(texts) < 60000:
        texts.append("".join(r.choice("-+.eE0123456789") for _ in range(r.randrange(1, 9))))
    for t, line in zip(texts, number_results(env, texts)):
        f = line.split(" ")
        if NUMBER.fullmatch(t):
            if f[5] != t:
                raise Failed("%r: %s" % (t, line))
        elif f[5] != "Malformed":
            raise Failed("%r is not a number: %s" % (t, line))
    long_digits = b"1" * 70000
    res = env.reads([Read(long_digits), Read(long_digits, number=0), Read(long_digits, number=0, chunk=7),
                     Read(b"[0." + long_digits + b"]", number=0)])
    if res[0][-2] != "ERROR Limit 1 65537 65536":
        raise Failed("a long number: %s" % res[0])
    agree("long", b"", res[1], ["N " + long_digits.decode()])
    agree("long", b"", res[2], ["N " + long_digits.decode()])
    agree("long", b"", res[3], ["[", "N 0." + long_digits.decode(), "]"])


def nest(n, kind):
    if kind == "a":
        return b"[" * n + b"]" * n
    if kind == "o":
        return b'{"k":' * n + b"1" + b"}" * n
    return b'[{"k":' * (n // 2) + b"1" + b"}]" * (n // 2)


def case_rdepth(env):
    for kind in "aom":
        ok = nest(1000, kind)
        over = nest(1002, kind) if kind == "m" else nest(1001, kind)
        where = {"a": 1000, "o": 5 * 1000, "m": 501 * 6 - 6 + 1 + 5 - 1 - 5}[kind]
        for how in "es":
            res = env.reads([Read(ok, how=how), Read(over, how=how)])
            if res[0][-1] != "OK" or res[1][-2:] != ["ERROR Limit 1 %d %d" % (where + 1, where), "AFTER Closed"]:
                raise Failed("%s %s: %s %s" % (kind, how, res[0][-2:], res[1][-2:]))
    shallow = env.reads([Read(b"[]", depth=1), Read(b"[[]]", depth=1), Read(b'{"a":[]}', depth=1), Read(b"1", depth=1)])
    if shallow[0][-1] != "OK" or shallow[3][-1] != "OK" or shallow[1][-2] != "ERROR Limit 1 2 1" \
            or shallow[2][-2] != "ERROR Limit 1 6 5":
        raise Failed("a limit of 1: %s" % shallow)
    deep = nest(1000000, "a")
    res = env.reads([Read(deep, depth=0, how="s"), Read(deep, depth=0)])
    if res[0][-1] != "OK" or res[1] != ["["] * 1000000 + ["]"] * 1000000 + ["OK"]:
        raise Failed("a million levels: %s" % res[0][-2:])


LIMITS = [
    # (document with @@ where it is too long, maximum in WIDECHARs)
    (b'"aaa@@a"', 3), (b'"aaaaaaaaaa@@aaaaaaaaaaaaaa"', 10), (b'"aa\\n@@a"', 3), (b'"aaa\\n@@"', 3), (b'"aa\xc3\xa4@@a"', 3),
    (b'"aaa\xc3\xa4@@"', 3), (b'"\xf0\x9f\x98\x80@@a"', 2), (b'"a\xf0\x9f\x98\x80@@"', 2),
    (b'"\\ud83d\\ude00@@a"', 2), (b'{"aaa@@a":1}', 3), (b'["aaa","aaa@@a"]', 3), (b'"a@@"', 0) if False else (b'"a@@b"', 1),
]


def case_rlimits(env):
    for doc, m in LIMITS:
        data, want = marked((doc, "Limit"))
        for chunk in (WHOLE, 1, 3):
            for how in "es":
                lines = env.reads([Read(data, chunk=chunk, string=m, how=how)])[0]
                if lines[-2:] != [want, "AFTER Closed"]:
                    raise Failed("%r with %d: expected %s, got %s" % (data, m, want, lines[-2:]))
    exact = [(b'"aaa"', 3), (b'"aa\\n"', 3), (b'"\xf0\x9f\x98\x80"', 2), (b'{"aaa":"bbb"}', 3), (b'""', 1)]
    for data, m in exact:
        for chunk in (WHOLE, 1):
            agree("exact", data, env.reads([Read(data, chunk=chunk, string=m)])[0], oracle(data))
    agree("no limit", b"", env.reads([Read(b'"' + b"a" * 200000 + b'"', string=0)])[0], ["T " + "0061" * 200000])
    res = env.reads([Read(b"123", number=3), Read(b"1234", number=3), Read(b"-1.5e+3", number=7), Read(b"-1.5e+3", number=6),
                     Read(b"[12,12345]", number=4)])
    if res[0][-1] != "OK" or res[1][-2] != "ERROR Limit 1 4 3" or res[2][-1] != "OK" or res[3][-2] != "ERROR Limit 1 7 6" \
            or res[4][-2] != "ERROR Limit 1 9 8":
        raise Failed("the limit of numbers: %s" % res)
    # a long string, longer than any buffer, in several ways
    long = "x\u00e4\\\n" * 100000
    d = json.dumps(long, ensure_ascii=False).encode("utf-8")
    agree("long", b"", env.reads([Read(d, chunk=4099)])[0], ["T " + units(long)])
    long_key = "k" * 70000
    d = json.dumps({long_key: "v" * 70000}).encode("ascii")
    agree("long key", b"", env.reads([Read(d)])[0], ["{", "K " + units(long_key), "T " + units("v" * 70000), "}"])


def hexed(key):
    return units(key)


APIS = [
    # how a program reads a known format
    (b'{"books":[{"id":7,"title":"x","more":[1,{"a":2}]}],"other":true}',
     [("object", "ok"), ("member", "TRUE"), ("is " + hexed("books"), "TRUE"), ("is " + hexed("book"), "FALSE"),
      ("name", hexed("books")), ("array", "ok"), ("element", "TRUE"), ("object", "ok"), ("member", "TRUE"),
      ("is " + hexed("id"), "TRUE"), ("int", "7"), ("member", "TRUE"), ("is " + hexed("title"), "TRUE"),
      ("text", hexed("x")), ("member", "TRUE"), ("skip", "ok"), ("member", "FALSE"), ("element", "FALSE"),
      ("member", "TRUE"), ("depth", "1"), ("skip", "ok"), ("member", "FALSE"), ("depth", "0"), ("peek", "EOF"),
      ("finish", "ok"), ("peek", "ERROR Closed 1 65 64")]),
    # the wrong kind takes nothing
    (b'[1,"a",true,null]',
     [("text", "ERROR Unexpected 1 1 0"), ("object", "ERROR Unexpected 1 1 0"), ("array", "ok"),
      ("text", "ERROR Unexpected 1 2 1"), ("bool", "ERROR Unexpected 1 2 1"), ("null", "ERROR Unexpected 1 2 1"),
      ("member", "ERROR Unexpected 1 2 1"), ("int", "1"), ("int", "ERROR Unexpected 1 4 3"), ("text", hexed("a")),
      ("text", "ERROR Unexpected 1 8 7"), ("bool", "TRUE"), ("null", "ok"), ("peek", "ArrayEnd"),
      ("element", "FALSE"), ("finish", "ok")]),
    # a number that does not fit takes nothing either
    (b'[1.5,99999999999999999999,1e999,1e2,-0]',
     [("array", "ok"), ("int", "ERROR BadValue 1 2 1"), ("peek", "Number"), ("real", "1.5"),
      ("int", "ERROR BadValue 1 6 5"), ("int", "ERROR BadValue 1 6 5"), ("number", "99999999999999999999"),
      ("real", "ERROR BadValue 1 27 26"), ("skip", "ok"), ("int", "ERROR BadValue 1 33 32"), ("real", "100"),
      ("int", "0"), ("element", "FALSE"), ("finish", "ok")]),
    # element only in an array, and not twice for one value
    (b"[1,2]", [("array", "ok"), ("element", "TRUE"), ("element", "ERROR Unexpected 1 2 1"), ("int", "1"),
                ("element", "TRUE"), ("skip", "ok"), ("element", "FALSE"), ("finish", "ok")]),
    (b'[[1],[]]', [("array", "ok"), ("element", "TRUE"), ("array", "ok"), ("element", "TRUE"), ("int", "1"),
                   ("element", "FALSE"), ("element", "TRUE"), ("array", "ok"), ("element", "FALSE"),
                   ("element", "FALSE"), ("finish", "ok")]),
    (b'{"a":1}', [("object", "ok"), ("element", "ERROR Unexpected 1 2 1"), ("member", "TRUE"),
                  ("element", "ERROR Unexpected 1 6 5"), ("int", "1"), ("element", "ERROR Unexpected 1 7 6"),
                  ("member", "FALSE")]),
    (b"1", [("element", "ERROR Unexpected 1 1 0"), ("member", "ERROR Unexpected 1 1 0"), ("end", "ERROR Unexpected 1 1 0"),
            ("int", "1"), ("end", "ERROR Unexpected 1 2 1"), ("skip", "ERROR Unexpected 1 2 1"), ("finish", "ok")]),
    # end leaves what is left
    (b'{"a":[1,2,{"b":3}],"c":4}',
     [("object", "ok"), ("member", "TRUE"), ("array", "ok"), ("element", "TRUE"), ("int", "1"), ("end", "ok"),
      ("depth", "1"), ("member", "TRUE"), ("is " + hexed("c"), "TRUE"), ("end", "ok"), ("depth", "0"), ("finish", "ok")]),
    (b'[{"x":[1]},2]', [("array", "ok"), ("element", "TRUE"), ("object", "ok"), ("member", "TRUE"), ("end", "ok"),
                        ("depth", "1"), ("element", "TRUE"), ("int", "2"), ("end", "ok"), ("depth", "0"), ("finish", "ok")]),
    (b'[1,[2]', [("array", "ok"), ("end", "ERROR Malformed 1 7 6"), ("peek", "ERROR Closed 1 7 6")]),
    # a key lasts until its value is taken
    (b'{"a":1,"b":[]}',
     [("name", ""), ("object", "ok"), ("is " + hexed("a"), "FALSE"), ("member", "TRUE"), ("name", hexed("a")),
      ("peek", "Number"), ("name", hexed("a")), ("is " + hexed("a"), "TRUE"), ("int", "1"), ("name", ""),
      ("is " + hexed("a"), "FALSE"), ("member", "TRUE"), ("name", hexed("b")), ("array", "ok"), ("name", ""),
      ("element", "FALSE"), ("member", "FALSE")]),
    # keys that need decoding, and that a long value does not disturb
    ('{"\u00e4\\n\U0001f600":"%s"}' % ("\u20ac" * 300), [
        ("object", "ok"), ("member", "TRUE"), ("is " + hexed("\u00e4\n\U0001f600"), "TRUE"),
        ("is " + hexed("\u00e4\n\U0001f601"), "FALSE"), ("is " + hexed("\u00e4\n"), "FALSE"),
        ("text", hexed("\u20ac" * 300)), ("member", "FALSE")]),
    ('{"%s":"%s"}' % ("k" * 1000, "\u20ac" * 1000), [
        ("object", "ok"), ("member", "TRUE"), ("is " + hexed("k" * 1000), "TRUE"),
        ("peek", "Text"), ("name", hexed("k" * 1000)), ("text", hexed("\u20ac" * 1000)), ("member", "FALSE")]),
    # a value that fails halfway leaves the key alone
    (b'{"abc":"\\u00e4x\\q"}', [
        ("object", "ok"), ("member", "TRUE"), ("text", "ERROR Malformed 1 16 15"), ("name", hexed("abc")),
        ("is " + hexed("abc"), "TRUE")]),
    # the wrong order at the end
    (b"[1]", [("finish", "ERROR Unexpected 1 1 0"), ("array", "ok"), ("finish", "ERROR Unexpected 1 2 1"), ("int", "1"),
              ("finish", "ERROR Unexpected 1 3 2"), ("element", "FALSE"), ("finish", "ok"), ("close", "ok"),
              ("peek", "ERROR Closed 1 4 3")]),
    (b"[]", [("array", "ok"), ("skip", "ERROR Unexpected 1 2 1"), ("member", "ERROR Unexpected 1 2 1"),
             ("peek", "ArrayEnd"), ("peek", "ArrayEnd"), ("element", "FALSE"), ("skip", "ERROR Unexpected 1 3 2"),
             ("peek", "EOF"), ("peek", "EOF"), ("finish", "ok")]),
    # close at any time
    (b"[1]", [("array", "ok"), ("close", "ok"), ("close", "ok"), ("peek", "ERROR Closed 1 2 1"),
              ("int", "ERROR Closed 1 2 1"), ("depth", "1")]),
    # errors of the document are final
    (b'[tru]', [("array", "ok"), ("element", "TRUE"), ("peek", "Bool"), ("bool", "ERROR Malformed 1 5 4"),
                ("peek", "ERROR Closed 1 5 4"), ("bool", "ERROR Closed 1 5 4"), ("close", "ok")]),
    (b'[1.]', [("array", "ok"), ("peek", "Number"), ("int", "ERROR Malformed 1 4 3"), ("skip", "ERROR Closed 1 4 3")]),
    (b'["\\q"]', [("array", "ok"), ("skip", "ERROR Malformed 1 3 2"), ("peek", "ERROR Closed 1 5 4")]),
    # where the reader is
    (b'[1,\n  2]', [("pos", "1 1 0"), ("array", "ok"), ("pos", "1 2 1"), ("element", "TRUE"), ("pos", "1 2 1"), ("int", "1"),
                    ("pos", "1 3 2"), ("element", "TRUE"), ("pos", "2 3 6"), ("int", "2"), ("pos", "2 4 7"),
                    ("element", "FALSE"), ("pos", "2 5 8")]),
]


def case_rapi(env):
    for chunk in (WHOLE, 1, 3):
        env.batches += 1
        name = "a%d" % env.batches
        script = []
        for doc, ops in APIS:
            head = "DOC %d 0 1000 %d %d " % (chunk, MAX_STRING, MAX_NUMBER)
            d = doc if isinstance(doc, bytes) else doc.encode("utf-8")
            script.append(head + d.hex())
            script += [op for op, _ in ops]
            script.append("END")
        (env.work / name).write_text("\n".join(script) + "\n", newline="\n", encoding="ascii")
        out = env.rrun("api", name).split(".\n")
        for (doc, ops), block in zip(APIS, out):
            lines = block.splitlines()
            want = [w for _, w in ops]
            if lines != want:
                for (op, w), got in zip(ops, lines):
                    if w != got:
                        raise Failed("pieces of %d: %r: %s: expected %s, got %s" % (chunk, doc[:60], op[:40], w, got))
                raise Failed("%r: %s" % (doc[:60], lines))


def case_rfeed(env):
    path = env.work / "feed.json"
    path.write_bytes(b'{"a":[1,2,3]}')
    got = env.rrun("feed", path.name).replace("\r\n", "\n")
    want = (READER / "expected" / "feed.txt").read_text(encoding="ascii")
    if got != want:
        raise Failed("feed:\n%s" % got)


def write_big(path, mb):
    n = size = 0
    with open(path, "wb", buffering=1 << 20) as f:
        f.write(b"[")
        while size < mb * 1000000:
            rec = ('%s{"id":%d,"title":"book %d \u00e4\u00f6","ratio":%d.5,"tags":["x\\n\\"y","x\\n\\"y"],"flag":true,'
                   '"none":null,"rest":{"x":[1,2,3],"y":"zzzzzzzz"}}' % ("," if n else "", n, n, n)).encode("utf-8")
            f.write(rec)
            size += len(rec)
            n += 1
        f.write(b"]\n")
    return n


def case_rbig(env):
    path = env.work / "big.json"
    n = write_big(path, 200)
    f = env.rrun("big", path.name, heap="64m").split()
    if f[0] != str(n) or f[1] != "0" or float(f[2]) != n * n / 2:
        raise Failed("%s records, the result is %s" % (n, f))
    path.unlink()


def speed_reader(env):
    path = env.work / "speed.json"
    write_big(path, 300)
    for line in env.rrun("speed", path.name).splitlines():
        print("SPEED     " + line, flush=True)
    path.unlink()


READER_CASES = {
    "rfixed": case_rfixed,
    "rerrors": case_rerrors,
    "rrandom": case_rrandom,
    "rfuzz": case_rfuzz,
    "rchunks": case_rchunks,
    "rchars": case_rchars,
    "rutf8": case_rutf8,
    "rreals": case_rreals,
    "rints": case_rints,
    "rnumbers": case_rnumbers,
    "rdepth": case_rdepth,
    "rlimits": case_rlimits,
    "rapi": case_rapi,
    "rfeed": case_rfeed,
    "rbig": case_rbig,
}

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
    **READER_CASES,
}


def main():
    ap = argparse.ArgumentParser(description="tests of JsonWriter and JsonReader")
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
        if args.speed or any(n not in READER_CASES for n in names):
            env.compile()
        if args.speed or any(n in READER_CASES for n in names):
            env.compile(READER, env.reader_classes)
        for name in names:
            try:
                CASES[name](env)
                print("PASS      " + name, flush=True)
            except (Failed, subprocess.TimeoutExpired, OSError, ValueError) as e:
                failed += 1
                print("FAIL      %s: %s" % (name, e), flush=True)
        if args.speed:
            speed(env)
            speed_reader(env)
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
