#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Differential tests against cm3: programs from outside m3tests (the test
packages of cm3's libraries, the examples, Rosetta Code programs) are built
by cm3 once (--record) and by m3j, run with the same arguments, input and
working directory, and their stdout, stderr and exit status compared.

  python3 tools/diff_test.py [NAME|PREFIX]... [--record] [--java JAVA] [--jar JAR]
                             [--work DIR] [--keep] [--timeout S] [-v]
                             [--aot auto|none|FILE] [--jvm-arg=ARG]...

The tests are listed in the files tests/diff/*.list, one per line:

  NAME  DIR  [arg ...]  [stdin=FILE]  [exe=PROGRAM]  [approx=TOL]  [data=DATA]
        [os=OS]  [result=NAME2]  [expected=FILE]

DIR is a cm3 package (a directory with src/m3makefile, relative to the
root of m3j; the programs of cm3 are copied to tests/diff/src); FILE is relative to DIR; the program runs in DIR/src (a
copy), with the files of the directory DATA (relative to the root of m3j)
copied into it first. approx=TOL marks a numerically sensitive test: its numbers may
differ by TOL * max (1, |x|, |y|). os=OS (POSIX or WIN32) compiles and
runs the test with m3j for that system (the property m3j.os of the JVM);
result=NAME2 takes the expected result from the record of the test NAME2,
which must come earlier in the lists: so a test for POSIX compares with
what cm3 gives for a program that has cm3's module for POSIX in it.
expected=FILE (relative to DIR) is for what cm3 gets wrong: the test is
compared not with the run of cm3 but with FILE, its stdout, checked by
hand (with exit status 0 and nothing on stderr); --record still builds it
with cm3, for the units, and keeps cm3's run in the record.
Lines starting with # are comments.

--record builds each test with cm3 (cm3 -build -keep -commands; cm3 and
the C compiler must be on the PATH), runs it twice and writes
tests/diff/expected/NAME.json: the output, the units that cm3 compiled
for the program (from its m3front lines), the packages it linked and the
generic instances that cm3 generated (written to tests/diff/expected/NAME/).
Differences whose reason is known and outside m3j are listed in
tests/diff/known.txt (NAME, a tab, the reason) and reported as KNOWN.
A test whose two runs differ (time, random numbers, addresses) is recorded
as unstable and not compared. Without --record, each recorded test is
compiled with m3j from the same units (with -pkg for the packages other
than those that m3j brings in lib, m3core, libm3, tcp, netobj, sysutils,
libbuf and patternmatching, copied from cm3 to tests/pkg; a package that cm3 linked but that is
not there, whose units the program does not use, is left out) and its run compared with the record. Outputs that differ only in floating-point
numbers, by a relative 1e-6 at most, are APPROX (the mathematical functions
of the C runtime and of Java may differ in the last bit).

The AOT cache that build.sh makes next to the jar (m3j.aot) is used for
compiling and running, unless --aot none. Each --jvm-arg is passed to every
JVM that compiles or runs a test (such as the agent of tools/coverage.py).
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DIFF = ROOT / "tests" / "diff"
EXPECTED = DIFF / "expected"
PKG = ROOT / "tests" / "pkg"
# the packages that m3j brings (lib/NAME)
BUNDLED = {p.name for p in (ROOT / "lib").iterdir() if (p / "src").is_dir()}
SEP = ";" if os.name == "nt" else ":"
TARGET = "AMD64_NT"


def read_known():
    """tests/diff/known.txt: NAME, a tab, the reason of a known difference."""
    known = {}
    f = DIFF / "known.txt"
    if f.exists():
        for line in f.read_text().splitlines():
            if line.strip() and not line.startswith("#"):
                name, _, reason = line.partition("	")
                known[name.strip()] = reason.strip()
    return known


def read_list():
    tests = []
    lines = []
    for f in sorted(DIFF.glob("*.list")):
        lines += f.read_text().splitlines()
    for line in lines:
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        w = line.split()
        t = {"name": w[0], "dir": w[1], "args": [], "stdin": None, "exe": None, "approx": None,
             "data": None, "os": None, "result": None, "expected": None}
        for x in w[2:]:
            if x.startswith("stdin="):
                t["stdin"] = x[6:]
            elif x.startswith("exe="):
                t["exe"] = x[4:]
            elif x.startswith("data="):
                t["data"] = x[5:]
            elif x.startswith("approx="):
                t["approx"] = float(x[7:])
            elif x.startswith("os="):
                t["os"] = x[3:]
            elif x.startswith("result="):
                t["result"] = x[7:]
            elif x.startswith("expected="):
                t["expected"] = x[9:]
            else:
                t["args"].append(x)
        tests.append(t)
    return tests


def normalize(b):
    return b.replace(b"\r\n", b"\n").decode("latin-1")


def copy_package(t, work):
    """A copy of the package of test t in work/NAME/pkg; returns its src directory."""
    p = work / t["name"] / "pkg"
    if p.exists():
        shutil.rmtree(p)
    shutil.copytree(ROOT / t["dir"], p)
    for o in p.rglob("m3overrides"):
        o.unlink()   # the libraries are those of the installation
    if t["data"]:
        for f in (ROOT / t["data"]).iterdir():
            shutil.copy(f, p / "src" / f.name)
    return p / "src"


def run(cmd, cwd, t, timeout):
    stdin = (ROOT / t["dir"] / t["stdin"]).read_bytes() if t["stdin"] else b""
    try:
        r = subprocess.run(cmd + t["args"], cwd=cwd, input=stdin, capture_output=True, timeout=timeout)
        return {"stdout": normalize(r.stdout), "stderr": normalize(r.stderr), "status": r.returncode}
    except subprocess.TimeoutExpired:
        return {"timeout": True}


def record(t, args, work):
    src = copy_package(t, work)
    derived = src.parent / TARGET
    c = subprocess.run(["cm3", "-build", "-keep", "-commands"], cwd=src, capture_output=True,
                       shell=os.name == "nt")
    log = normalize(c.stdout + c.stderr)
    exes = sorted(derived.glob("*.exe"))
    if t["exe"]:
        exes = [e for e in exes if e.stem == t["exe"]]
    if c.returncode != 0 or not exes:
        return "CM3-FAILED", log.strip().splitlines()[-1:] if log.strip() else ""
    units, generated, pkgs = [], [], []
    gen_dir = EXPECTED / t["name"]
    if gen_dir.exists():
        shutil.rmtree(gen_dir)
    for line in log.splitlines():
        if line.startswith("m3front ") and not line.startswith("m3front failed"):
            f = (derived / line[8:].split()[0]).resolve()
            if f.parent == derived.resolve():
                gen_dir.mkdir(parents=True, exist_ok=True)
                shutil.copy(f, gen_dir / f.name)
                generated.append(f.name)
            else:
                units.append(Path(t["dir"]).joinpath(f.relative_to(src.parent.resolve())).as_posix())
        m = re.match(r"inhale .*[\\/]pkg[\\/]([^\\/]+)[\\/]" + TARGET, line)
        if m and m.group(1) not in pkgs:
            pkgs.append(m.group(1))
    if t["result"]:
        other = json.loads((EXPECTED / (t["result"] + ".json")).read_text())
        first, stable = other["result"], other["stable"]
    else:
        first = run([str(exes[0])], src, t, args.timeout)
        second = run([str(exes[0])], src, t, args.timeout)
        stable = first == second and not first.get("timeout")
    rec = {"units": units, "generated": generated, "pkgs": pkgs, "result": first, "stable": stable}
    if t["result"]:
        rec["result_of"] = t["result"]
    (EXPECTED / (t["name"] + ".json")).parent.mkdir(parents=True, exist_ok=True)
    (EXPECTED / (t["name"] + ".json")).write_text(json.dumps(rec, indent=1) + "\n")
    if t["expected"]:
        # compared with the file, not with this run
        return "RECORDED", f"{len(units)} units, {len(generated)} generated (expected: {t['expected']})"
    return ("RECORDED" if rec["stable"] else "UNSTABLE"), f"{len(units)} units, {len(generated)} generated"


NUMBER = re.compile(r"[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eEdDxX][-+]?\d+)?")


def approx_equal(e, g, rel=1e-6, tol=None):
    """True if e and g differ only in floating-point numbers, by at most rel
    (relative), or by tol * max (1, |x|, |y|) if tol is given."""
    # numbers padded to a width: the padding may differ with the number
    e, g = re.sub(r"[ 	]+", "", e), re.sub(r"[ 	]+", "", g)
    if NUMBER.sub("#", e) != NUMBER.sub("#", g):
        return False
    for a, b in zip(NUMBER.findall(e), NUMBER.findall(g)):
        if a == b:
            continue
        try:
            x = float(re.sub("[dDxX]", "e", a))
            y = float(re.sub("[dDxX]", "e", b))
        except ValueError:
            return False
        if tol is not None:
            if abs(x - y) > tol * max(1.0, abs(x), abs(y)):
                return False
        elif abs(x - y) > rel * max(abs(x), abs(y), 1e-300):
            return False
    return True


def first_diff(e, g):
    el, gl = e.splitlines(), g.splitlines()
    for i in range(max(len(el), len(gl))):
        a = el[i] if i < len(el) else "<end>"
        b = gl[i] if i < len(gl) else "<end>"
        if a != b:
            return f"line {i + 1}: expected {a[:100]!r}, got {b[:100]!r}"
    return ""


def check(t, args, work):
    f = EXPECTED / (t["name"] + ".json")
    if not f.exists():
        return "NOT-RECORDED", ""
    rec = json.loads(f.read_text())
    if t["expected"]:
        e = {"stdout": normalize((ROOT / t["dir"] / t["expected"]).read_bytes()), "stderr": "", "status": 0}
    elif not rec["stable"]:
        return "UNSTABLE", ""
    else:
        e = rec["result"]
    src = copy_package(t, work)
    out = src.parent / "JVM"
    out.mkdir()
    for g in rec["generated"]:
        shutil.copy(EXPECTED / t["name"] / g, out / g)
    jvm_opts = args.jvm_opts + (["-Dm3j.os=" + t["os"]] if t["os"] else [])
    cmd = [args.java] + jvm_opts + ["-Xss64m", "-cp", args.jar, "m3j.jvm.Compile", "-v"]
    for p in rec["pkgs"]:
        if p not in BUNDLED and (PKG / p).is_dir():
            # the other packages are copies of cm3's in tests/pkg
            cmd += ["-pkg", str(PKG / p)]
    for u in rec["units"]:
        # relative to the directory of compiling, as cm3 names them from
        # its AMD64_NT ("../src/Main.m3"): the names of runtime errors
        cmd.append(Path(os.path.relpath(src.parent / Path(u).relative_to(t["dir"]), out)).as_posix())
    cmd += rec["generated"]
    cmd += ["-d", "classes"]
    try:
        c = subprocess.run(cmd, cwd=out, capture_output=True, timeout=args.timeout)
    except subprocess.TimeoutExpired:
        return "TIMEOUT", "compiling"
    msg = normalize(c.stdout + c.stderr)
    if c.returncode != 0:
        last = [l for l in msg.strip().splitlines() if l.strip()]
        return "COMPILE", (last[-1] if last else "")[:200]
    r = run([args.java] + jvm_opts + ["-cp", args.jar + SEP + str(out / "classes"), "m3.Main"], src, t,
            args.timeout)
    if r.get("timeout"):
        return "TIMEOUT", ""
    if r["status"] != e["status"]:
        return "FAIL", f"exit status {r['status']}, expected {e['status']}"
    if r["stderr"] != e["stderr"]:
        return "FAIL", "stderr " + first_diff(e["stderr"], r["stderr"])
    if r["stdout"] != e["stdout"]:
        if approx_equal(e["stdout"], r["stdout"], tol=t["approx"]):
            return "APPROX", "stdout " + first_diff(e["stdout"], r["stdout"])
        return "FAIL", "stdout " + first_diff(e["stdout"], r["stdout"])
    m = re.search(r"not compiled yet: (.*)", msg)
    return "PASS", ("(unsupported: " + m.group(1)[:80] + ")") if m else ""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("tests", nargs="*")
    ap.add_argument("--record", action="store_true")
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
    args = ap.parse_args()
    if not args.java:
        jdk = os.environ.get("JDK25")
        args.java = str(Path(jdk) / "bin" / "java") if jdk else "java"
    aot = None if args.aot == "none" else (
        Path(args.jar).with_suffix(".aot") if args.aot == "auto" else Path(args.aot))
    args.jvm_opts = (["-XX:AOTCache=" + str(aot)] if aot and aot.exists() else []) + args.jvm_arg
    work = args.work or Path(tempfile.mkdtemp(prefix="m3j-diff-"))
    work.mkdir(parents=True, exist_ok=True)
    counts = {}
    known = read_known()
    try:
        for t in read_list():
            if args.tests and not any(t["name"] == n or t["name"].startswith(n + "/") for n in args.tests):
                continue
            status, detail = (record if args.record else check)(t, args, work)
            if status in ("FAIL", "COMPILE") and t["name"] in known:
                status, detail = "KNOWN", known[t["name"]] + " [" + detail + "]"
            counts[status] = counts.get(status, 0) + 1
            print(f"{status:12} {t['name']}" + (f"  {detail}" if detail else ""), flush=True)
    finally:
        if not args.keep and not args.work:
            shutil.rmtree(work, ignore_errors=True)
    print(", ".join(f"{k} {v}" for k, v in sorted(counts.items())))
    sys.exit(1 if any(k in counts for k in ("FAIL", "COMPILE", "TIMEOUT")) else 0)


if __name__ == "__main__":
    main()
