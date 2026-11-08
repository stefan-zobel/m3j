#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Differential test of the front end against CM3's.

Usage:
  python3 tools/front_test.py PACKAGE... [--tree DIR]... [--java JAVA] [--jvm-arg=ARG]...
                              [--install DIR]
                              [--pkg NAME=DIR]... [--keep WORKDIR] [--show N]

Each PACKAGE is a package directory that the reference cm3 has built with
"cm3 -keep -commands > build.log 2>&1" (in the C back-end mode, which needs
the fix of M3CG_Tee).  The script runs m3j.tools.FrontDump (from m3j.jar) on
the package, which repeats the front-end runs of build.log, and compares the
M3CG text of every unit with the text that cm3's m3cgcat (on the PATH, with
its runtime DLLs) makes from the binary intermediate code (.mc, .ic) that
cm3 kept.  It also compares the diagnostics of the front end.

Units whose code differs only in the order of its lines are counted
separately (failures with --strict): cm3's Revelation module hashes opaque
types by their M3ID numbers, which depend on every identifier the whole cm3
process has interned before (quake, the builder, the link info), so the
order of the revelations it declares cannot be reproduced from the build
log alone.

JAVA defaults to $JDK25/bin/java, else java from the PATH; DIR (the cm3
installation) defaults to the parent of the directory of m3cgcat.
"""

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUILD = "AMD64_NT"


def m3front_runs(pkg):
    """The front-end runs in build.log: (file, options)."""
    runs = []
    for line in (pkg / "build.log").read_text(encoding="latin-1").splitlines():
        if line.startswith("m3front ") and not line.startswith("m3front failed"):
            w = line[8:].split()
            runs.append((w[0], w[1:]))
    return runs


def diagnostics(log):
    """The lines of the build log that come from the front end."""
    return [l for l in log if l.startswith('"') and '", line ' in l
            and "quake runtime error" not in l]


def first_diff(e, g):
    el, gl = e.split(b"\n"), g.split(b"\n")
    for j in range(max(len(el), len(gl))):
        a = el[j] if j < len(el) else None
        b = gl[j] if j < len(gl) else None
        if a != b:
            return j, a, b
    return 0, None, None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("packages", nargs="*")
    ap.add_argument("--tree", action="append", default=[], metavar="DIR",
                    help="also test every package below DIR that has a build.log")
    ap.add_argument("--java")
    ap.add_argument("--jvm-arg", action="append", default=[], metavar="ARG",
                    help="pass ARG to the JVM that runs FrontDump (write --jvm-arg=-X... "
                         "for an ARG that begins with a minus sign)")
    ap.add_argument("--install")
    ap.add_argument("--pkg", action="append", default=[])
    ap.add_argument("--keep", metavar="WORKDIR")
    ap.add_argument("--show", type=int, default=10)
    ap.add_argument("--strict", action="store_true",
                    help="count units whose lines differ only in order as failures")
    args = ap.parse_args()

    java = args.java
    if not java:
        jdk = os.environ.get("JDK25")
        java = str(Path(jdk) / "bin" / "java") if jdk else "java"
    m3cgcat = shutil.which("m3cgcat")
    if not m3cgcat:
        sys.exit("m3cgcat not found on the PATH")
    install = args.install or str(Path(m3cgcat).resolve().parent.parent)

    for t in args.tree:
        args.packages += sorted(str(b.parent) for b in Path(t).rglob("build.log")
                                if (b.parent / BUILD).is_dir())
    work = Path(args.keep) if args.keep else Path(tempfile.mkdtemp(prefix="front_test"))
    work.mkdir(parents=True, exist_ok=True)
    total = bad = diag_bad = order_only = cut = shown = 0
    nbytes = 0
    for k, p in enumerate(args.packages):
        pkg = Path(p).resolve()
        runs = m3front_runs(pkg)
        if not runs:
            continue
        out = work / f"{k:04d}"
        out.mkdir(exist_ok=True)
        cmd = [java, "-Xss64m"] + args.jvm_arg + ["-cp", str(ROOT / "m3j.jar"), "m3j.tools.FrontDump",
               f"-install={install}", f"-out={out}"] + [f"-pkg={x}" for x in args.pkg] + [str(pkg)]
        r = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        jlog = r.stdout.decode("latin-1").splitlines()
        clog = (pkg / "build.log").read_text(encoding="latin-1").splitlines()
        # cm3's C back end (M3C) crashes on some units; their side files
        # are cut off, and M3C's own messages are not the front end's
        crashed = any("M3C.m3" in l for l in clog)
        clog = [l for l in clog if "DeclareTypes not progressing" not in l]
        if (any(l.startswith("Fatal Error: unable to open file for reading: ..") for l in clog)
                and (pkg / "stdout.build").exists()):
            # a test of m3tests built on its own: the quake code of the test
            # stops cm3 before it flushes the diagnostics; the test's
            # expected output has them
            clog = (pkg / "stdout.build").read_text(encoding="latin-1").splitlines()
        if diagnostics(jlog) != diagnostics(clog) or any(l.startswith("***") for l in jlog):
            diag_bad += 1
            if shown < args.show:
                shown += 1
                print(f"DIAGNOSTICS {pkg}:")
                print("  expected: " + "\n            ".join(diagnostics(clog)[:5]))
                print("  got:      " + "\n            ".join(
                    (diagnostics(jlog) + [l for l in jlog if l.startswith("***")])[:5]))
        seen = set()
        for f, _ in reversed(runs):          # the last run of a unit counts
            base = Path(f).name
            if base in seen:
                continue
            seen.add(base)
            stem, ext = base[:-3], base[-3:]
            kind = ".ic" if ext in (".i3", ".ig") else ".mc"
            binary = pkg / BUILD / (stem + kind)
            if not binary.exists():
                continue                  # cm3 wrote no code (error)
            total += 1
            exp = subprocess.run([m3cgcat, f"-in-binary={binary}", "-out-ascii"],
                                 stdout=subprocess.PIPE).stdout
            got_file = out / (stem + kind + ".txt")
            got = got_file.read_bytes() if got_file.exists() else b""
            nbytes += len(exp)
            complete = exp.rpartition(b"\n")[0]   # without the last, partial line
            if binary.stat().st_size == 0 or (crashed and exp != got and got.startswith(complete)):
                # cm3 crashed: its code is cut off and a prefix of ours
                cut += 1
                continue
            if exp != got and sorted(exp.splitlines()) == sorted(got.splitlines()):
                # the same lines in another order; see --strict
                order_only += 1
                if args.strict:
                    bad += 1
                continue
            if exp != got:
                bad += 1
                if shown < args.show:
                    shown += 1
                    j, a, b = first_diff(exp, got)
                    print(f"FAIL {pkg / BUILD / base} (line {j + 1}):\n"
                          f"  expected {a[:200] if a is not None else '<eof>'!r}\n"
                          f"  got      {b[:200] if b is not None else '<eof>'!r}")
    print(f"{total - bad - cut - (0 if args.strict else order_only)} of {total} units identical, "
          f"{order_only} with the same lines in another order, {cut} cut off by a crash of cm3 "
          f"({nbytes} bytes of text); "
          f"diagnostics differ in {diag_bad} of {len(args.packages)} packages")
    if not args.keep:
        shutil.rmtree(work, ignore_errors=True)
    sys.exit(1 if bad or diag_bad else 0)


if __name__ == "__main__":
    main()
