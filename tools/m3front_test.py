#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Runs CM3's front end, compiled by m3j, on the JVM and compares it with
the front end of m3j.

Usage:
  python3 tools/m3front_test.py [CASE|CORPUS]... [--java JAVA] [--work DIR]
                                [--keep] [--jobs N] [--timeout SECONDS]
                                [--program JAR] [--jvm-arg=ARG]... [-v]

First m3j compiles the program tests/m3front (the packages m3front and
m3middle of cm3, with sysutils from the library of m3j, and the driver in driver/src, a
Modula-3 port of the environment of m3j's front end, m3j.jvm.FrontEnv)
with every module named on the command line, and every class must verify
(--program JAR uses a program built before). Then, for each case, m3j's
compiler driver compiles the case with -mc, which writes the binary
intermediate code (M3CG) of each unit it compiles and the list of these
units, and the compiled CM3 front end compiles the same list in one
session with the same search path. The two must give the same bytes of
intermediate code for every unit, the same diagnostics and the same exit
code. For a difference in the code the first differing line of the code
as text (m3j.tools.CgCat) is shown.

CASE is a test of m3tests (p001, or a directory such as
tests/m3tests/src/p0/p001), set up as tools/run_jvm.py sets it up. CORPUS
is a group of m3tests (p0, p1, p2, r0, c0, c1), "lib" (the units of
m3j's library, lib) or "self" (tests/m3front itself). Without arguments
all of them are run. Tests that use UNSAFE units are skipped.

Needs the build of build.sh: m3j.jar, m3j-runtime.jar and the classes in
out (the oracle runs from out, so that the library is in files that the
compiled front end can read). JAVA defaults to $JDK25/bin/java, else java
from the PATH.
"""

import argparse
import concurrent.futures
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import run_jvm  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests" / "m3tests" / "src"
PROGRAM = ROOT / "tests" / "m3front"
PACKAGES = ["m3front", "m3middle"]
LIB = ROOT / "out" / "lib"
GROUPS = ["p0", "p1", "p2", "r0", "c0", "c1"]
SEP = ";" if os.name == "nt" else ":"


def is_unsafe_file(f):
    text = re.sub(r"\(\*.*?\*\)", "", f.read_text(encoding="latin-1"), flags=re.S)
    return re.match(r"\s*(<\*[^*]*\*>\s*)*UNSAFE\b", text) is not None


def program_files():
    """The modules of the program: all safe modules of tests/m3front."""
    return sorted(str(f) for f in PROGRAM.rglob("*.m3") if not is_unsafe_file(f))


def build(args, work):
    jar = work / "m3front.jar"
    cmd = [args.java] + args.jvm_arg + ["-Xss64m", "-cp", str(ROOT / "m3j.jar"), "m3j.jvm.Compile"]
    for p in PACKAGES:
        cmd += ["-pkg", str(PROGRAM / p)]
    cmd += program_files() + ["-d", str(jar)]
    t = time.time()
    c = subprocess.run(cmd, capture_output=True)
    out = (c.stdout + c.stderr).decode("latin-1")
    if c.returncode != 0:
        print(out)
        sys.exit("m3front: the program does not compile")
    if args.verbose:
        print(out, end="")
    print("built %s in %.1f s" % (jar, time.time() - t))
    return jar


class Case:
    """A compilation: the files, the source directories and packages, the working directory."""

    def __init__(self, name, cwd, files, dirs=(), pkgs=(), options=()):
        self.name = name
        self.cwd = cwd
        self.files = list(files)
        self.dirs = [str(d) for d in dirs]
        self.pkgs = [str(p) for p in pkgs]
        self.options = list(options)


def m3tests_case(d, work):
    """The case of test directory d, set up as run_jvm sets it up, or None (skipped)."""
    if run_jvm.is_unsafe(d) or (d / "gen.py").exists() or list(d.glob("*.c")):
        return None
    w = work / d.name
    if w.exists():
        shutil.rmtree(w)
    shutil.copytree(d, w)
    mk = (d / "m3makefile").read_text(encoding="latin-1")
    files, generated = run_jvm.makefile_units(mk)
    for f, text in generated.items():
        (w / f).write_text(text, encoding="latin-1")
    if not files:
        files = ["Main.m3"]
    files = [f for f in files if (w / f).exists()]
    if not files:
        return None
    b = w / "JVM"
    b.mkdir()
    dirs = [] if (d / "Test.i3").exists() else [TESTS]
    pkgs = []
    for pkg in re.findall(r'^\s*import\s*\(\s*"([^"]+)"\s*\)', mk, re.M):
        p = ROOT / "tests" / "pkg" / pkg
        if pkg != "libm3" and p.is_dir():
            pkgs.append(p)
    return Case(d.name, b, ["../" + f for f in files], dirs, pkgs)


def lib_case(work):
    w = work / "lib"
    w.mkdir(parents=True, exist_ok=True)
    # not the packages with bindings of Java classes (lib/java, and those
    # with <*EXTERNAL ...:JAVA*>, such as regexp and xml): their language
    # is one that cm3 does not have
    java = {"java"} | {f.relative_to(ROOT / "lib").parts[0] for f in (ROOT / "lib").rglob("*.i3")
                       if re.search(r":JAVA\s*\*>", f.read_text(encoding="latin-1"))}
    files = sorted(str(f) for f in (ROOT / "lib").rglob("*")
                   if f.suffix in (".i3", ".m3") and f.relative_to(ROOT / "lib").parts[0] not in java)
    return Case("lib", w, files)


def self_case(work):
    w = work / "self"
    w.mkdir(parents=True, exist_ok=True)
    return Case("self", w, program_files(), pkgs=[PROGRAM / p for p in PACKAGES])


def run_case(args, jar, case):
    oracle = case.cwd / "oracle"
    jvm = case.cwd / "jvm"
    for d in (oracle, jvm):
        if d.exists():
            shutil.rmtree(d)
        d.mkdir()
    cmd = [args.java] + args.jvm_arg + ["-Xss64m", "-cp", str(ROOT / "out"), "m3j.jvm.Compile", "-mc", str(oracle)]
    for d in case.dirs:
        cmd += ["-I", d]
    for p in case.pkgs:
        cmd += ["-pkg", p]
    cmd += case.options + case.files
    t0 = time.time()
    try:
        o = subprocess.run(cmd, cwd=case.cwd, capture_output=True, timeout=args.timeout)
    except subprocess.TimeoutExpired:
        return "TIMEOUT", "oracle", 0, 0
    t1 = time.time()
    if not (oracle / "units").exists():
        return "ORACLE", lines(o.stdout + o.stderr)[-1:], t1 - t0, 0
    # the search path of m3j's driver: the directories of the files, the
    # source directories, the library, the packages
    search = []
    for f in case.files:
        # as Java's toAbsolutePath().getParent(): not normalized ("JVM\..")
        d = str((case.cwd / f).parent)
        if d not in case.dirs:
            search += ["-I", d]
    for d in case.dirs:
        search += ["-I", d]
    for p in ("libm3", "m3core", "m3j"):
        search += ["-pkg", str(LIB / p)]
    for p in case.pkgs:
        search += ["-pkg", p]
    cmd = ([args.java] + args.jvm_arg + ["-Xss64m", "-cp", str(ROOT / "m3j-runtime.jar") + SEP + str(jar), "m3.Main"]
           + search + ["-o", str(jvm), "-list", str(oracle / "units")] + case.options)
    try:
        g = subprocess.run(cmd, cwd=case.cwd, capture_output=True, timeout=args.timeout)
    except subprocess.TimeoutExpired:
        return "TIMEOUT", "m3front", t1 - t0, args.timeout
    t2 = time.time()
    times = (t1 - t0, t2 - t1)
    units = lines((oracle / "units").read_bytes())
    oall = lines(o.stderr)
    # m3j's driver reports an exception of its front end as "*** FILE: ..."
    # with a stack trace, and goes on with the next unit
    ocrash = any(l.startswith("*** ") for l in oall)
    odiag = [l for l in oall if not l.startswith(("m3j: ", "*** ", "\tat ", "Caused by"))
             and not re.match(r"[\w.$]+(Error|Exception)\b", l)]
    gdiag = lines(g.stderr)
    gcrash = g.returncode not in (0, 1) or any(l.startswith(("***", "java.", "Exception", "\tat ")) for l in gdiag)
    if gcrash:
        crash = [l for l in gdiag if not l.startswith("\"")]
        if not ocrash:
            return ("CRASH", " | ".join(crash[:3])[:300]) + times
        # both front ends fail (an assertion): the units before must agree
        done = len(list(jvm.glob("*.cg")))
        for k in range(1, done):
            r = compare_unit(args, oracle, jvm, units, k)
            if r:
                return ("CODE", r) + times
        return ("PASS", "both front ends fail: " + " ".join(crash[1:3]).strip()[:200]) + times
    if odiag != gdiag:
        j = next((i for i in range(max(len(odiag), len(gdiag)))
                  if (odiag[i] if i < len(odiag) else None) != (gdiag[i] if i < len(gdiag) else None)), 0)
        return ("DIAG", "line %d: expected %r, got %r" % (
            j + 1, odiag[j] if j < len(odiag) else None, gdiag[j] if j < len(gdiag) else None)) + times
    if o.returncode != g.returncode:
        return ("EXIT", "expected %d, got %d" % (o.returncode, g.returncode)) + times
    for k in range(1, len(units) + 1):
        r = compare_unit(args, oracle, jvm, units, k)
        if r:
            return ("CODE", r) + times
    return ("PASS", "%d units" % len(units)) + times


def compare_unit(args, oracle, jvm, units, k):
    """The difference in the code of unit k, or None."""
    a, b = oracle / ("%d.cg" % k), jvm / ("%d.cg" % k)
    if a.exists() != b.exists():
        return "%s: written by %s only" % (unit_name(units, k), "m3j" if a.exists() else "m3front")
    if a.exists() and a.read_bytes() != b.read_bytes():
        return "%s: %s" % (unit_name(units, k), code_diff(args, a, b))
    return None


def unit_name(units, k):
    return units[k - 1].split("\t")[2]


def lines(b):
    return [l for l in b.decode("latin-1").replace("\r", "").split("\n") if l != ""]


def code_diff(args, a, b):
    """The first differing line of the code of files a and b, as text."""
    texts = []
    for f in (a, b):
        c = subprocess.run([args.java, "-cp", str(ROOT / "m3j.jar"), "m3j.tools.CgCat", str(f)],
                           capture_output=True)
        texts.append(lines(c.stdout))
    e, g = texts
    for j in range(max(len(e), len(g))):
        x = e[j] if j < len(e) else None
        y = g[j] if j < len(g) else None
        if x != y:
            return "line %d: expected %r, got %r" % (j + 1, x, y)
    return "the bytes differ, the text does not"


def main():
    ap = argparse.ArgumentParser(description="CM3's front end on the JVM against m3j's front end")
    ap.add_argument("cases", nargs="*")
    ap.add_argument("--java")
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--jobs", type=int, default=max(1, (os.cpu_count() or 2) // 2))
    ap.add_argument("--timeout", type=int, default=600)
    ap.add_argument("--program")
    ap.add_argument("--jvm-arg", action="append", default=[],
                    help="an option for every JVM that builds or runs the program or the oracle "
                         "(repeatable; --jvm-arg=-X...)")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()
    if not args.java:
        jdk = os.environ.get("JDK25")
        args.java = str(Path(jdk) / "bin" / "java") if jdk else "java"
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="m3front_"))
    work.mkdir(parents=True, exist_ok=True)
    names = args.cases or GROUPS + ["lib", "self"]
    try:
        jar = Path(args.program) if args.program else build(args, work)
        cases = []
        for n in names:
            if n == "lib":
                cases.append(lib_case(work))
            elif n == "self":
                cases.append(self_case(work))
            else:
                for d in run_jvm.find_tests([n]):
                    c = m3tests_case(d, work)
                    if c is not None:
                        cases.append(c)
        counts = {}
        total = [0.0, 0.0]
        with concurrent.futures.ThreadPoolExecutor(args.jobs) as pool:
            futures = [(c, pool.submit(run_case, args, jar, c)) for c in cases]
            for c, f in futures:
                status, detail, to, tg = f.result()
                counts[status] = counts.get(status, 0) + 1
                total[0] += to
                total[1] += tg
                if status != "PASS" or args.verbose:
                    print("%-8s %s  %s" % (status, c.name, detail), flush=True)
        print(", ".join("%s %d" % (k, counts[k]) for k in sorted(counts)))
        print("time: m3j's front end (and back end) %.1f s, CM3's front end on the JVM %.1f s"
              % (total[0], total[1]))
        return 0 if set(counts) <= {"PASS"} else 1
    finally:
        if not args.keep and not args.work:
            shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
