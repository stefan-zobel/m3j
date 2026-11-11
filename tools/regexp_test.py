#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Tests of the library regexp of m3j, through tests/regexp/src/Main.m3,
against tests/regexp/Ref.java, which does the same with java.util.regex
directly. They need no connection to the internet.

  python3 tools/regexp_test.py [CASE]... [--java JAVA] [--jar JAR] [--work DIR]
                               [--keep] [--timeout SECONDS] [--count N] [--speed]

The cases:

  fixed         calls with readable output, checked by hand
                (tests/regexp/expected/fixed.txt)
  differential  random patterns and texts under random options: all matches
                from a random start with all groups, the match of the whole
                text, Split, Replace and ReplaceWith, against Ref.java
  errors        bad patterns, fixed and made by damaging good ones, give the
                message of Java; bad replacements are an Error even when
                nothing matches
  limit         searches that take exponential time end with TooComplex; a
                limit just below and just above what a search reads, for
                Find, Next, FullMatch, Split and Replace; limit 0 is none
  deep          a group repeated until the stack overflows gives TooComplex,
                on the main thread and on another, and the pattern works on
  threads       threads that search with one pattern agree

--speed measures matches per second with and without the limit, for
information.
"""

import argparse
import os
import random
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests" / "regexp"
BASE = 100000
DEFAULT_LIMIT = 100


class Failed(Exception):
    pass


def w(s):
    """A text as "w" and four hex digits for each UTF-16 element."""
    d = s.encode("utf-16-be", "surrogatepass")
    return "w" + "".join("%02x%02x" % (d[i], d[i + 1]) for i in range(0, len(d), 2))


def unw(t):
    return bytes.fromhex(t[1:]).decode("utf-16-be", "surrogatepass")


def units(s):
    return len(s.encode("utf-16-be", "surrogatepass")) // 2


class Env:
    def __init__(self, args, work):
        self.args = args
        self.work = work
        self.java = args.java
        self.cp = str(work / "classes") + os.pathsep + args.jar
        self.batches = 0

    def compile(self):
        cmd = [self.java, "-Xss64m", "-cp", self.args.jar, "m3j.jvm.Compile", str(TESTS / "src" / "Main.m3"),
               "-d", str(self.work / "classes")]
        r = subprocess.run(cmd, cwd=self.work, capture_output=True, text=True, stdin=subprocess.DEVNULL)
        if r.returncode or "no module" in r.stdout + r.stderr or "warning" in r.stdout + r.stderr:
            raise Failed("compile:\n" + r.stdout + r.stderr)
        javac = str(Path(self.java).with_name("javac"))
        r = subprocess.run([javac, "-d", str(self.work / "ref"), str(TESTS / "Ref.java")], capture_output=True,
                           text=True)
        if r.returncode:
            raise Failed("javac:\n" + r.stdout + r.stderr)

    def command(self, *a, timeout=None):
        return subprocess.run([self.java, "-cp", self.cp, "m3.Main", *a], cwd=self.work, capture_output=True,
                              timeout=timeout or self.args.timeout)

    def run(self, *a):
        r = self.command(*a)
        if r.returncode or r.stderr:
            raise Failed("exit %d: %s" % (r.returncode, (r.stdout + r.stderr).decode("latin-1")[:2000]))
        return r.stdout.decode("ascii").replace("\r\n", "\n")

    def batch(self, lines, ref=False):
        """The result line of each of the lines, of Main.m3 or of Ref.java."""
        self.batches += 1
        name = "b%d" % self.batches
        (self.work / (name + ".in")).write_text("\n".join(lines) + "\n", newline="\n")
        if ref:
            cmd = [self.java, "-cp", str(self.work / "ref"), "Ref", name + ".in", name + ".out"]
            r = subprocess.run(cmd, cwd=self.work, capture_output=True, timeout=self.args.timeout)
        else:
            r = self.command("batch", name + ".in", name + ".out")
        if r.returncode or r.stderr:
            raise Failed("exit %d: %s" % (r.returncode, (r.stdout + r.stderr).decode("latin-1")[:2000]))
        out = (self.work / (name + ".out")).read_text().splitlines()
        if len(out) != len(lines):
            raise Failed("%d results for %d lines" % (len(out), len(lines)))
        return out


def readable(line):
    """A line of a batch with its texts decoded, for a message."""
    return " ".join(repr(unw(t)) if t.startswith("w") else t for t in line.split(" "))


def agree(got, want, line):
    """Whether the result of Main.m3 is that of Ref.java."""
    if got == want:
        return True
    if line.startswith("r ") and want.startswith("nomatch "):
        # Java finds a bad replacement only at a match
        return got == want[len("nomatch "):] or got.startswith("error ")
    if line.startswith("r ") and want == "error":
        return got.startswith("error ")
    return False


def compare(env, lines):
    got = env.batch(lines)
    want = env.batch(lines, ref=True)
    bad = [i for i in range(len(lines)) if not agree(got[i], want[i], lines[i])]
    if bad:
        shown = ["%s\n      got  %s\n      want %s" % (readable(lines[i])[:300], got[i][:300], want[i][:300])
                 for i in bad[:5]]
        raise Failed("%d of %d differ:\n    %s" % (len(bad), len(lines), "\n    ".join(shown)))
    return got


def case_fixed(env):
    got = env.run("fixed")
    want = (TESTS / "expected" / "fixed.txt").read_text(encoding="ascii").replace("\r\n", "\n")
    if got != want:
        g, h = got.splitlines(), want.splitlines()
        for i in range(max(len(g), len(h))):
            a = g[i] if i < len(g) else "<end>"
            b = h[i] if i < len(h) else "<end>"
            if a != b:
                raise Failed("line %d: got %r, want %r" % (i + 1, a, b))


ATOMS = ["a", "b", "c", "a", "b", ".", "[ab]", "[^a]", "[a-c]", "[^\\s]", "\\w", "\\W", "\\d", "\\s", "\\b", "\\B",
         "^", "$", "\\A", "\\z", "\\Z", "\\G", "x", "\u00e9", "\u00c9", "\\x{1F600}", "[\\x{1F600}a]", "\\n", "\\.",
         "\\p{L}", "\\p{Lu}", "[a&&[^b]]", "\\R", "K", "\u212a", " ", "\\Q.a\\E"]
QUANTS = ["*", "+", "?", "{0,2}", "{1,3}", "{2}", "{1,}"]
CHARS = ["a", "b", "c", "a", "b", "x", " ", "\n", "\r\n", "\u00e9", "\u00c9", "\U0001F600", "k", "K", "\u212a", "1",
         ".", "\ud800", "\udc00"]
REPLACEMENTS = ["", "-", "$0", "[$1]", "${n1}", "\\$", "$2$1", "x$10y", "\\\\", "$1$1", "<${n2}>"]


class Gen:
    def __init__(self, rng):
        self.rng = rng
        self.groups = 0
        self.names = 0

    def pattern(self, depth=0):
        r = self.rng
        k = r.random()
        if depth > 3 or k < 0.3:
            return r.choice(ATOMS)
        if k < 0.5:
            return "".join(self.pattern(depth + 1) for _ in range(r.randint(2, 4)))
        if k < 0.6:
            return self.pattern(depth + 1) + "|" + self.pattern(depth + 1)
        if k < 0.85:
            inner = self.pattern(depth + 1)
            if inner[-1] in "*+?}" and not inner.endswith("\\" + inner[-1]):
                inner = "(?:" + inner + ")"
            return inner + r.choice(QUANTS) + r.choice(["", "", "?", "+"])
        if k < 0.92:
            self.groups += 1
            if r.random() < 0.3:
                self.names += 1
                return "(?<n%d>%s)" % (self.names, self.pattern(depth + 1))
            return "(" + self.pattern(depth + 1) + ")"
        if k < 0.95:
            return "(?" + r.choice(["=", "!"]) + self.pattern(depth + 1) + ")"
        if k < 0.97:
            return "(?" + r.choice(["<=", "<!"]) + r.choice(["a", "b", "ab", "a|bc", "[ab]", "\\w"]) + ")"
        if k < 0.99 and self.groups:
            return "\\%d" % r.randint(1, self.groups)
        return "(?" + r.choice(["i", "-i", "s", "m", "x"]) + ")"


def gen_pattern(rng):
    g = Gen(rng)
    p = "".join(g.pattern() for _ in range(rng.randint(1, 3)))
    return p


def gen_text(rng):
    return "".join(rng.choice(CHARS) for _ in range(rng.randint(0, 12)))


def gen_options(rng):
    bits = 0
    for bit, p in ((1, 0.25), (2, 0.25), (4, 0.25), (8, 0.25), (16, 0.03), (32, 0.03)):
        if rng.random() < p:
            bits |= bit
    return bits


def case_differential(env):
    rng = random.Random(20261111)
    lines = []
    for _ in range(env.args.count):
        p, o = gen_pattern(rng), gen_options(rng)
        head = "%d %d %s" % (o, DEFAULT_LIMIT, w(p))
        lines.append("c " + head)
        for _ in range(3):
            t = gen_text(rng)
            start = rng.choice([0, 0, rng.randint(0, units(t) + 1)])
            lines.append("f %s %s %d" % (head, w(t), start))
            lines.append("m %s %s" % (head, w(t)))
            lines.append("s %s %s %d" % (head, w(t), rng.choice([-1, 0, 0, 1, 2, 3])))
            lines.append("r %s %s %s %d" % (head, w(t), w(rng.choice(REPLACEMENTS)), rng.randint(0, 1)))
            lines.append("w %s %s %d" % (head, w(t), rng.randint(0, 1)))
        if rng.random() < 0.2:
            lines.append("g %s %s" % (head, w(rng.choice(["n1", "n2", "x"]))))
    for t in ["", "a.b", "\\E", "a\\Eb\\Q", "$1\\", "\U0001F600"]:
        lines.append("q " + w(t))
    got = compare(env, lines)
    compiled = sum(1 for i, l in enumerate(lines) if l.startswith("c ") and got[i].startswith("ok"))
    matches = sum(1 for i, l in enumerate(lines) if l.startswith("f ") and got[i] != "none" and "error" not in got[i])
    if compiled < len([l for l in lines if l.startswith("c ")]) * 0.8 or matches < env.args.count:
        raise Failed("too few patterns compiled (%d) or matched (%d)" % (compiled, matches))


BAD_PATTERNS = ["(a", "a)", "[a", "a{2,1}", "*a", "a**", "\\k<x>", "(?<1x>a)", "\\p{Nope}", "a\\", "(?<n>a)(?<n>b)",
                "[z-a]", "a{", "(?<", "(?", "\\x{110000}", "\\u12", "[a-\\d]", "\\c", "\\N{nope}",
                "(?P<n>a)", "x{2147483648}", "[[a]", "(?i", "a|*"]


def case_errors(env):
    rng = random.Random(7)
    lines = ["c 0 %d %s" % (DEFAULT_LIMIT, w(p)) for p in BAD_PATTERNS]
    for _ in range(3000):
        p = gen_pattern(rng)
        i = rng.randint(0, len(p))
        p = p[:i] + rng.choice("()[]{}*+?\\|^$<>=!,-") + p[i + rng.randint(0, 1):]
        lines.append("c %d %d %s" % (gen_options(rng), DEFAULT_LIMIT, w(p)))
    got = compare(env, lines)
    if sum(1 for g in got if g.startswith("error ")) < 1000:
        raise Failed("too few bad patterns")
    for i, p in enumerate(BAD_PATTERNS):
        if not got[i].startswith("error ") or "index" not in unw(got[i][6:]) and "nested" not in unw(got[i][6:]):
            raise Failed("%r: %s" % (p, got[i]))
    # bad replacements, with a match and without
    cases = [("a", "$1"), ("(a)", "$2"), ("a", "${x}"), ("a", "${1x}"), ("a", "${}"), ("a", "${x"), ("a", "x$"),
             ("a", "x\\"), ("a", "$x"), ("(?<n>a)", "${m}"), ("(a)", "$"), ("(a)", "\\")]
    lines = []
    for p, r in cases:
        for t in ["a", "b"]:
            lines.append("r 0 %d %s %s %s 1" % (DEFAULT_LIMIT, w(p), w(t), w(r)))
    for i, g in enumerate(env.batch(lines)):
        if not g.startswith("error ") or "index" not in unw(g[6:]):
            raise Failed("%s: %s" % (readable(lines[i]), g))
    # good ones that look odd
    good = [("(a)", "$10", "a0"), ("(a)", "$01", "a"), ("(a)", "\\$1", "$1"), ("(?<n>a)", "${n}$1", "aa"),
            ("(a)|b", "[$1]", "[]")]
    lines = ["r 0 %d %s %s %s 1" % (DEFAULT_LIMIT, w(p), w("a" if "b" not in p else "b"), w(r)) for p, r, _ in good]
    for i, g in enumerate(env.batch(lines)):
        if g != w(good[i][2]):
            raise Failed("%s: %s" % (readable(lines[i]), g))


def case_limit(env):
    # exponential: over the default budget in bounded time
    t0 = time.time()
    lines = ["f 0 %d %s %s 0" % (DEFAULT_LIMIT, w("(a{1,2}){1,40}b"), w("a" * 40)),
             "f 0 %d %s %s 0" % (DEFAULT_LIMIT, w("(.*,){11}P"), w("x," * 40)),
             "m 0 %d %s %s" % (DEFAULT_LIMIT, w("^(a+)+\\1b"), w("a" * 40)),
             "s 0 %d %s %s 0" % (DEFAULT_LIMIT, w("(a{1,2}){1,40}b"), w("a" * 40)),
             "r 0 %d %s %s %s 1" % (DEFAULT_LIMIT, w("(a{1,2}){1,40}b"), w("a" * 40), w("x")),
             "w 0 %d %s %s 1" % (DEFAULT_LIMIT, w("(a{1,2}){1,40}b"), w("a" * 40))]
    got = env.batch(lines)
    for i, g in enumerate(got):
        if not g.endswith("TooComplex"):
            raise Failed("%s: %s" % (readable(lines[i]), g))
    if time.time() - t0 > 20:
        raise Failed("TooComplex took %.1f s" % (time.time() - t0))
    # limit 0: no limit
    p, t = w("(a{1,2}){1,40}b"), w("a" * 32)
    got = env.batch(["f 0 0 %s %s 0" % (p, t), "f 0 %d %s %s 0" % (DEFAULT_LIMIT, p, t)])
    if got != ["none", " TooComplex"]:
        raise Failed("limit 0 and default: %s" % got)
    # just below and just above: the reads, by Ref.java, give the limit
    probes = [  # kind, pattern, text, start or max or all
        ("f", "(a{1,2}){1,40}b", "a" * 24, 0),
        ("f", "(a{1,2}){1,40}b", "xyz" + "a" * 24, 2),
        ("f", "(.*,){11}P", "x," * 14, 0),
        ("f2", "b|(a{1,2}){1,40}c", "b" + "a" * 24, 0),
        ("m", "(a{1,2}){1,40}", "a" * 23 + "b", 0),
        ("s", "(a{1,2}){1,40}b", "a" * 24, 0),
        ("r", "(a{1,2}){1,40}b", "a" * 24, 1),
    ]
    ref = []
    for kind, p, t, n in probes:
        if kind in ("f", "f2"):
            ref.append("cnt %d 0 0 %s %s %d" % (1 if kind == "f2" else 0, w(p), w(t), n))
        elif kind == "m":
            ref.append("cntm 0 0 %s %s" % (w(p), w(t)))
        elif kind == "s":
            ref.append("cnts 0 0 %s %s %d" % (w(p), w(t), n))
        else:
            ref.append("cntr 0 0 %s %s %d" % (w(p), w(t), n))
    counts = [int(c.split(" ")[0]) for c in env.batch(ref, ref=True)]
    lines, shown = [], []
    for (kind, p, t, n), reads in zip(probes, counts):
        if kind == "f2":
            chars = units(t) - 1               # the second search starts after "b"
        elif kind == "f":
            chars = units(t) - n
        else:
            chars = units(t)
        k = reads // (chars + BASE)
        if k < 1 or k * (chars + BASE) >= reads:
            raise Failed("%s %r: %d reads give no limit below" % (kind, p, reads))
        for limit in (k, k + 1):
            if kind in ("f", "f2"):
                lines.append("f 0 %d %s %s %d" % (limit, w(p), w(t), n))
            elif kind == "m":
                lines.append("m 0 %d %s %s" % (limit, w(p), w(t)))
            elif kind == "s":
                lines.append("s 0 %d %s %s %d" % (limit, w(p), w(t), n))
            else:
                lines.append("r 0 %d %s %s %s %d" % (limit, w(p), w(t), w("x"), n))
            shown.append("%s %r on %d chars, %d reads, limit %d" % (kind, p, units(t), reads, limit))
    got = env.batch(lines)
    for i in range(0, len(lines), 2):
        below, above = got[i], got[i + 1]
        kind = probes[i // 2][0]
        if kind == "f2":
            ok = below == "0:1,-1:-1 TooComplex" and not above.endswith("TooComplex")
        else:
            ok = below.endswith("TooComplex") and not above.endswith("TooComplex")
        if not ok:
            raise Failed("%s: below %r, above %r" % (shown[i], below, above))


def case_deep(env):
    for n, where, want in [(100000, "thread", "none"), (2000000, "thread", "TooComplex"),
                           (20000000, "main", "TooComplex")]:
        out = env.run("deep", str(n), where).strip()
        if out.split(" ")[0] != want:
            raise Failed("%d letters on the %s: %s" % (n, where, out))


def case_threads(env):
    out = env.run("threads", "8").strip()
    if out != "threads agree 8":
        raise Failed(out)


CASES = {"fixed": case_fixed, "differential": case_differential, "errors": case_errors, "limit": case_limit,
         "deep": case_deep, "threads": case_threads}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cases", nargs="*")
    ap.add_argument("--java", default=str(Path(os.environ["JDK25"]) / "bin" / "java") if os.environ.get("JDK25")
                    else shutil.which("java") or "java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--timeout", type=float, default=300.0)
    ap.add_argument("--count", type=int, default=4000)
    ap.add_argument("--speed", action="store_true")
    args = ap.parse_args()
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="regexp_test"))
    work.mkdir(parents=True, exist_ok=True)
    env = Env(args, work)
    failed = []
    try:
        env.compile()
        for name in args.cases or ([] if args.speed else CASES):
            t0 = time.time()
            try:
                CASES[name](env)
                print("ok    %-12s %.1f s" % (name, time.time() - t0))
            except Exception as e:
                failed.append(name)
                print("FAIL  %-12s %s" % (name, str(e)[:3000].encode("ascii", "backslashreplace").decode("ascii")))
        if args.speed:
            print(env.run("speed"), end="")
    finally:
        if not args.keep and not args.work:
            shutil.rmtree(work, ignore_errors=True)
    print("%d failed" % len(failed) if failed else "all passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
