#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Differential tests of the ported modules against CM3.

Usage:
  python3 tools/dump_test.py SUITE [--java JAVA] [--jvm-arg=ARG]...
      Run the Java side of SUITE (from m3j.jar) on tests/SUITE/cases.txt
      and compare its output with tests/SUITE/expected.txt; run each line
      of tests/SUITE/faults.txt on its own and check that it fails.
  python3 tools/dump_test.py SUITE --record EXE
      Record expected.txt with the Modula-3 program EXE (built with cm3
      from the program's directory in tools; its runtime DLLs must be on
      PATH), and check that EXE fails on every line of faults.txt.

SUITE is one of:
  fingerprint  m3j.tools.FpDump  / tools/fpdump   (Fingerprint, Poly, M3FP)
  middle       m3j.tools.MidDump / tools/middump  (TInt, TWord, TFloat,
               Convert, M3Buf, M3ID, Target, TargetMap, M3RT)

JAVA defaults to $JDK25/bin/java, else java from the PATH.
"""

import argparse
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

SUITES = {
    "fingerprint": "m3j.tools.FpDump",
    "middle": "m3j.tools.MidDump",
}


def run(cmd, data):
    p = subprocess.run(cmd, input=data.encode("latin-1"),
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out = p.stdout.decode("latin-1").replace("\r\n", "\n")
    return p.returncode, out, p.stderr.decode("latin-1", "replace")


def fault_lines(tests):
    return [l for l in (tests / "faults.txt").read_text().splitlines() if l]


def check_faults(tests, cmd):
    bad = 0
    for line in fault_lines(tests):
        code, out, _ = run(cmd, line + "\n")
        if code == 0:
            print(f"FAIL (no runtime error): {line} -> {out.strip()[:200]}")
            bad += 1
    return bad


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("suite", choices=sorted(SUITES))
    ap.add_argument("--record", metavar="EXE")
    ap.add_argument("--java")
    ap.add_argument("--jvm-arg", action="append", default=[],
                    help="an option for the JVM (repeatable; --jvm-arg=-X...)")
    args = ap.parse_args()
    tests = ROOT / "tests" / args.suite
    cases = (tests / "cases.txt").read_text()
    nfaults = len(fault_lines(tests))

    if args.record:
        cmd = [str(Path(args.record).resolve())]
        code, out, err = run(cmd, cases)
        if code != 0:
            sys.exit(f"{args.record} failed ({code}) after {len(out.splitlines())} lines: {err}")
        (tests / "expected.txt").write_text(out, newline="\n")
        bad = check_faults(tests, cmd)
        print(f"recorded {len(out.splitlines())} results; "
              f"{nfaults - bad} of {nfaults} fault cases fail")
        sys.exit(1 if bad else 0)

    java = args.java
    if not java:
        jdk = os.environ.get("JDK25")
        java = str(Path(jdk) / "bin" / "java") if jdk else "java"
    cmd = [java] + args.jvm_arg + ["-cp", str(ROOT / "m3j.jar"), SUITES[args.suite]]
    code, out, err = run(cmd, cases)
    expected = (tests / "expected.txt").read_text().splitlines()
    got = out.splitlines()
    bad = 0
    if code != 0:
        print(f"FAIL: {SUITES[args.suite]} exited with {code}: {err.strip()}")
        bad += 1
    for i, e in enumerate(expected):
        g = got[i] if i < len(got) else "<missing>"
        if g != e:
            bad += 1
            if bad <= 20:
                print(f"FAIL line {i + 1}:\n  expected {e[:300]}\n  got      {g[:300]}")
    if len(got) > len(expected):
        print(f"FAIL: {len(got) - len(expected)} extra lines")
        bad += 1
    bad_faults = check_faults(tests, cmd)
    n = len(expected)
    print(f"{n - min(bad, n)} of {n} cases match; "
          f"{nfaults - bad_faults} of {nfaults} fault cases fail")
    sys.exit(1 if bad or bad_faults else 0)


if __name__ == "__main__":
    main()
