#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Runs the program tests of m3tests with m3j and compares the results.

Usage:
  python3 tools/run_jvm.py [TEST|GROUP]... [--java JAVA] [--jar JAR]
                           [--work DIR] [--keep] [--timeout SECONDS] [-v]
                           [--aot auto|none|FILE] [--jvm-arg=ARG]...

The tests are those of cm3's m3tests that m3j can run, copied to
tests/m3tests (the groups below, with the expected outputs that this tool
reads). TEST is a test
directory (such as tests/m3tests/src/p0/p001) or a test name (p001); GROUP is p0, p1, p2 or r0. Without arguments all
groups p0, p1, p2 and r0 are run. With --compile the tests are only
compiled and their classes verified (the gate for c0 and c1).

Each test is copied to a work directory and compiled there, from the
subdirectory JVM, as "../Main.m3" (as cm3 builds it from AMD64_NT), with
m3j.jvm.Compile -run. Generic instances made by build_generic_intf and
build_generic_impl in the m3makefile are written as source files first;
the packages it imports (other than those that m3j brings, such as libm3
and tcp) are found in tests/pkg (-pkg).
The oracle are the expected files of the test: stdout.pgm and stderr.pgm
(or their variants for 64-bit little-endian targets, such as
stderr.pgm64), compared after the normalization of m3tests (no carriage returns,
backslashes as slashes). Tests that use UNSAFE units are skipped. Tests
that fail for a reason outside m3j (listed in KNOWN with the reason) are
reported as KNOWN.

JAVA defaults to $JDK25/bin/java, else java from the PATH. The AOT cache that build.sh makes next to the jar
(m3j.aot) is used for compiling and running, unless --aot none. Each
--jvm-arg is passed to every JVM that compiles or runs a test (such as the
agent of tools/coverage.py).
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests" / "m3tests" / "src"
GROUPS = ["p0", "p1", "p2", "r0", "c0", "c1"]

# Tests whose expected result m3j does not reach for a reason outside m3j:
# they are reported as KNOWN with the reason (unless they pass).
KNOWN = {
    "p068": "expects a link error of cm3 (an unresolved procedure)",
    "p152": "the expected files are older than cm3: its front end rejects the test as well",
    "p153": "the expected files are older than cm3: its front end rejects the test as well",
    "p154": "the expected files are older than cm3: its front end rejects the test as well",
    "p161": "the expected stderr is older than cm3 (a warning it no longer gives); takes minutes"
            " (cm3 -O: more than 10)",
    "p224": "runs for minutes by design (needs --timeout 600)",
}


def find_tests(names):
    if not names:
        names = ["p0", "p1", "p2", "r0"]
    out = []
    for n in names:
        p = Path(n)
        if p.is_dir() and (p / "m3makefile").exists():
            out.append(p)
        elif n in GROUPS:
            out.extend(sorted(d for d in (TESTS / n).iterdir() if (d / "m3makefile").exists()))
        else:
            for g in GROUPS:
                d = TESTS / g / n
                if d.is_dir():
                    out.append(d)
                    break
            else:
                sys.exit("no such test: " + n)
    return out


def normalize(b):
    s = b.decode("latin-1").replace("\r", "").replace("\\", "/")
    return s


def makefile_units(text):
    """The source files the m3makefile names, and the generic instances."""
    # the 64-bit branch of "if equal(WORD_SIZE, "64BITS") ... else ... end"
    text = re.sub(r'if\s+equal\s*\(\s*WORD_SIZE\s*,\s*"64BITS"\s*\)(.*?)(?:\belse\b.*?)?\bend\b',
                  lambda m: m.group(1), text, flags=re.S)
    files = []
    interfaces = []
    generated = {}
    for m in re.finditer(r'\b(implementation|module|Module|interface|Interface|generic_module|generic_interface)\s*\(\s*"([^"]+)"', text):
        kind, name = m.group(1), m.group(2)
        if kind in ("implementation", "module", "Module"):
            files.append(name + ".m3")
        elif kind in ("interface", "Interface"):
            interfaces.append(name + ".i3")
    for m in re.finditer(r'build_generic_(intf|impl)\s*\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*\[([^\]]*)\]', text):
        kind, name, gen, args = m.groups()
        args = ", ".join(a.strip().strip('"') for a in args.split(",") if a.strip())
        if kind == "intf":
            generated[name + ".i3"] = "INTERFACE %s = %s(%s) END %s.\n" % (name, gen, args, name)
        else:
            generated[name + ".m3"] = "MODULE %s = %s(%s) END %s.\n" % (name, gen, args, name)
            files.append(name + ".m3")
    if not files:
        # a package of interfaces only
        files = interfaces
    return files, generated


def is_unsafe(d):
    for f in list(d.glob("*.m3")) + list(d.glob("*.i3")) + list(d.glob("*.mg")) + list(d.glob("*.ig")):
        head = f.read_text(encoding="latin-1")
        head = re.sub(r"\(\*.*?\*\)", "", head, flags=re.S)
        if re.match(r"\s*(<\*[^*]*\*>\s*)*UNSAFE\b", head):
            return True
    return False


def run_test(d, args, work):
    name = d.name
    if is_unsafe(d):
        return "SKIP-UNSAFE", ""
    if (d / "gen.py").exists() or list(d.glob("*.c")):
        return "SKIP-BUILD", ""
    if not args.compile and not list(d.glob("stdout.pgm*")) and not list(d.glob("stderr.pgm*")):
        # a test of the build system: nothing to compare
        return "SKIP-BUILD", ""
    w = work / name
    if w.exists():
        shutil.rmtree(w)
    shutil.copytree(d, w)
    mk = (d / "m3makefile").read_text(encoding="latin-1")
    files, generated = makefile_units(mk)
    for f, text in generated.items():
        (w / f).write_text(text, encoding="latin-1")
    if not files:
        files = ["Main.m3"]
    files = [f for f in files if (w / f).exists()]
    if not files:
        return "SKIP-BUILD", "no sources"
    b = w / "JVM"
    b.mkdir()
    cmd = [args.java] + args.jvm_opts + ["-Xss64m", "-cp", args.jar, "m3j.jvm.Compile"]
    if not (d / "Test.i3").exists():
        # the shared Test interface, unless the test has its own
        cmd += ["-I", str(TESTS)]
    # the packages the test imports, other than those of the library of m3j
    for pkg in re.findall(r'^\s*import\s*\(\s*"([^"]+)"\s*\)', mk, re.M):
        p = ROOT / "tests" / "pkg" / pkg
        if pkg != "libm3" and p.is_dir():
            cmd += ["-pkg", str(p)]
    cmd.append("-v")
    cmd += ["../" + f for f in files if (w / f).exists()]
    cmd += ["-d", "classes"]
    try:
        c = subprocess.run(cmd, cwd=b, capture_output=True, timeout=args.timeout)
    except subprocess.TimeoutExpired:
        return "TIMEOUT", "compiling"
    cerr = normalize(c.stdout + c.stderr)
    if args.compile:
        # the gate for compile tests: every class verifies
        if "does not verify" in cerr:
            m = re.search(r"does not verify: (.*)", cerr)
            return "VERIFY", m.group(1)[:200] if m else ""
        if c.returncode != 0:
            last = [l for l in cerr.strip().split(chr(10)) if l.strip()]
            return "COMPILE", (last[-1] if last else "")[:200]
        return "VERIFIED", ""
    if c.returncode != 0:
        return "COMPILE", cerr.strip().split(chr(10))[-1][:200]
    m = re.search(r"not compiled yet: (.*)", cerr)
    unsupported = m.group(1) if m else None
    merge = "MERGE_STDOUT_STDERR" in mk
    stdin = None
    if (d / "stdin").exists():
        stdin = (d / "stdin").read_bytes()
    runopts = []
    if (d / "runopts").exists():
        runopts = (d / "runopts").read_text().split()
    sep = ";" if os.name == "nt" else ":"
    cmd = [args.java] + args.jvm_opts + ["-cp", args.jar + sep + "classes", "m3.Main"] + runopts
    try:
        if merge:
            r = subprocess.run(cmd, cwd=b, input=stdin, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               timeout=args.timeout)
        else:
            r = subprocess.run(cmd, cwd=b, input=stdin, capture_output=True, timeout=args.timeout)
    except subprocess.TimeoutExpired:
        return "TIMEOUT", ""
    out, err = normalize(r.stdout), normalize(r.stderr or b"")
    exp_out = expected(d, "stdout.pgm")
    exp_err = expected(d, "stderr.pgm")
    if "m3j: not implemented:" in err:
        status = "UNSUPPORTED"
    elif any(out == o for o in exp_out) and any(err.strip() == e.strip() for e in exp_err):
        status = "PASS"
    else:
        status = "FAIL"
    exp_out, exp_err = exp_out[0], exp_err[0]
    if status == "FAIL" and unsupported:
        status = "UNSUPPORTED"
    detail = ""
    if status != "PASS":
        detail = first_diff("stdout", exp_out, out) or first_diff("stderr", exp_err, err)
        if status == "UNSUPPORTED":
            m = re.search(r"not implemented: (.*)", err)
            detail = m.group(1) if m else (unsupported or detail)
    return status, detail


def expected(d, name):
    """The expected outputs, best first: the 64-bit, little-endian variant
    if there is one, then the others (a test may have outputs for several
    configurations). The lines "Aborted" and "Segmentation fault" that a
    Unix shell adds are dropped (a program on Windows does not print them)."""
    out = []
    for v in (name + "-little_endian64", name + "64", name + "-little_endian", name):
        if (d / v).exists():
            s = normalize((d / v).read_bytes())
            lines = [l for l in s.split(chr(10)) if l not in ("Aborted", "Segmentation fault")]
            out.append(chr(10).join(lines))
    return out or [""]


def first_diff(what, e, g):
    if e == g:
        return ""
    el, gl = e.split("\n"), g.split("\n")
    for i in range(max(len(el), len(gl))):
        a = el[i] if i < len(el) else "<eof>"
        b = gl[i] if i < len(gl) else "<eof>"
        if a != b:
            return "%s line %d: expected %r, got %r" % (what, i + 1, a[:100], b[:100])
    return ""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("tests", nargs="*")
    ap.add_argument("--java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
    ap.add_argument("--work", type=Path)
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--timeout", type=int, default=120)
    ap.add_argument("--aot", default="auto",
                    help="auto (the .aot file next to the jar, if there is one), none, or a file")
    ap.add_argument("--jvm-arg", action="append", default=[],
                    help="an option for every JVM that compiles or runs a test (repeatable; --jvm-arg=-X...)")
    ap.add_argument("-v", "--verbose", action="store_true")
    ap.add_argument("--compile", action="store_true",
                    help="only compile (and verify) the tests, for the compile tests c0 and c1")
    args = ap.parse_args()
    if not args.java:
        jdk = os.environ.get("JDK25")
        args.java = str(Path(jdk) / "bin" / "java") if jdk else "java"
    aot = None if args.aot == "none" else (
        Path(args.jar).with_suffix(".aot") if args.aot == "auto" else Path(args.aot))
    args.jvm_opts = (["-XX:AOTCache=" + str(aot)] if aot and aot.exists() else []) + args.jvm_arg
    work = args.work or Path(tempfile.mkdtemp(prefix="m3j-run-"))
    work.mkdir(parents=True, exist_ok=True)
    counts = {}
    try:
        for d in find_tests(args.tests):
            status, detail = run_test(d, args, work)
            if status not in ("PASS", "VERIFIED") and d.name in KNOWN and not args.compile:
                status, detail = "KNOWN", KNOWN[d.name] + " [" + status + "]"
            counts[status] = counts.get(status, 0) + 1
            line = "%-12s %s" % (status, d.parent.name + "/" + d.name)
            if detail:
                line += "  " + detail
            print(line, flush=True)
    finally:
        if not args.keep and not args.work:
            shutil.rmtree(work, ignore_errors=True)
    print(", ".join("%s %d" % (k, v) for k, v in sorted(counts.items())))


if __name__ == "__main__":
    main()
