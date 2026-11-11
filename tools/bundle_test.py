#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Runs m3bundle of cm3, compiled by m3j (m3bundle.jar, which build.sh
makes from tools/m3bundle), and compares what it writes with what
m3bundle built by cm3 writes.

Usage:
  python3 tools/bundle_test.py [CASE]... [--record] [--java JAVA]
                               [--jar JAR] [--m3bundle EXE] [--work DIR]
                               [--keep] [--timeout SECONDS] [-v]

A case is a line of tests/bundle/cases.list: its name and the parameters
of m3bundle. It runs in a new directory with a copy of tests/bundle/data
(in data). The files that it writes there (the interface NAME.i3 and the
module NAME.m3 of the bundle), its output and its exit code must be those
of m3bundle built by cm3, recorded in tests/bundle/expected/CASE: the
files, and run.txt with the exit code and the output (the name of the
program, parameter 0, written as <m3bundle>). The record is of Windows: on
another system its lines are compared as ending in LF. Without arguments
all cases run.

--record runs m3bundle of a cm3 installation (EXE, by default m3bundle
from the PATH) and records its results, instead of running m3j's.

JAVA defaults to $JDK25/bin/java, else java from the PATH; JAR to
m3bundle.jar in the root of m3j.
"""

import argparse
import os
import shlex
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests" / "bundle"
EXPECTED = TESTS / "expected"
RUN = "run.txt"


class Case:
    def __init__(self, line):
        parts = shlex.split(line)
        self.name, self.params = parts[0], parts[1:]


def read_cases(names):
    cases = [Case(l) for l in (TESTS / "cases.list").read_text(encoding="latin-1").splitlines()
             if l.strip() and not l.startswith("#")]
    if names:
        cases = [c for c in cases if c.name in names]
        if not cases:
            sys.exit("bundle_test: no such case: " + " ".join(names))
    return cases


def normalize(b, program):
    """The output of a run, with the name of the program written as <m3bundle>."""
    return b.decode("latin-1").replace(program + ":", "<m3bundle>:").replace(
        "usage: " + program + " ", "usage: <m3bundle> ")


def run(args, cmd, program, d):
    """Runs m3bundle in the new directory d: {file: bytes} of what it wrote, and run.txt."""
    if d.exists():
        shutil.rmtree(d)
    d.mkdir(parents=True)
    shutil.copytree(TESTS / "data", d / "data")
    try:
        c = subprocess.run(cmd, cwd=d, capture_output=True, timeout=args.timeout)
        text = "exit %d\n--- stdout\n%s--- stderr\n%s" % (
            c.returncode, normalize(c.stdout, program), normalize(c.stderr, program))
    except subprocess.TimeoutExpired:
        text = "timeout\n"
    files = {f.name: f.read_bytes() for f in sorted(d.iterdir()) if f.is_file()}
    files[RUN] = text.replace("\r\n", "\n").encode("latin-1")
    return files


def write(files, d):
    if d.exists():
        shutil.rmtree(d)
    d.mkdir(parents=True)
    for name, data in files.items():
        (d / name).write_bytes(data)


def read(d):
    return {f.name: f.read_bytes() for f in sorted(d.iterdir()) if f.is_file()}


def expected(d):
    """The recorded files of a case. m3bundle of cm3 was recorded on Windows,
    whose lines end in CR LF; on another system they end in LF."""
    files = read(d)
    if os.name != "nt":
        files = {name: data.replace(b"\r\n", b"\n") for name, data in files.items()}
    return files


def compare(got, exp):
    """The differences between the files of a run and the expected ones, or []."""
    diffs = []
    for name in sorted(set(got) | set(exp)):
        if name not in got:
            diffs.append("missing " + name)
        elif name not in exp:
            diffs.append("extra " + name)
        elif got[name] != exp[name]:
            g, e = got[name], exp[name]
            i = next((k for k in range(min(len(g), len(e))) if g[k] != e[k]), min(len(g), len(e)))
            diffs.append("%s differs at byte %d (%d bytes, expected %d)" % (name, i, len(g), len(e)))
    return diffs


def main():
    ap = argparse.ArgumentParser(description="m3bundle compiled by m3j against m3bundle of cm3")
    ap.add_argument("cases", nargs="*")
    ap.add_argument("--record", action="store_true")
    jdk = os.environ.get("JDK25")
    ap.add_argument("--java", default=str(Path(jdk) / "bin" / "java") if jdk else "java")
    ap.add_argument("--jar", default=str(ROOT / "m3bundle.jar"))
    ap.add_argument("--m3bundle", default="m3bundle")
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--timeout", type=float, default=60)
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()

    cases = read_cases(args.cases)
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="bundle_test_"))
    work.mkdir(parents=True, exist_ok=True)
    counts = {}
    try:
        for case in cases:
            if args.record:
                exe = shutil.which(args.m3bundle) or args.m3bundle
                files = run(args, [exe] + case.params, exe, work / "record" / case.name)
                write(files, EXPECTED / case.name)
                status = "RECORDED"
            else:
                exp_dir = EXPECTED / case.name
                if not exp_dir.is_dir():
                    status, diffs = "NOT-RECORDED", []
                else:
                    files = run(args, [args.java, "-jar", args.jar] + case.params, "pgm",
                                work / "m3j" / case.name)
                    diffs = compare(files, expected(exp_dir))
                    status = "FAIL" if diffs else "PASS"
                    if diffs:
                        print("%-12s %s" % (status, case.name))
                        for d in diffs:
                            print("    " + d)
                        if args.verbose and RUN in files:
                            print(files[RUN].decode("latin-1"))
                        counts[status] = counts.get(status, 0) + 1
                        continue
            counts[status] = counts.get(status, 0) + 1
            print("%-12s %s" % (status, case.name))
    finally:
        if not args.keep and not args.work:
            shutil.rmtree(work, ignore_errors=True)
    print(" ".join("%s %d" % kv for kv in sorted(counts.items())))
    return 1 if counts.get("FAIL") else 0


if __name__ == "__main__":
    sys.exit(main())
