#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Differential test of the M3CG binary reader and text writer against CM3.

Usage:
  python3 tools/cg_test.py DIR... [--java JAVA] [--keep WORKDIR]

Finds the M3CG binary files (*.mc, *.ic, written by cm3 -keep) below the
directories DIR, converts each to text with CM3's m3cgcat (which must be on
the PATH, with its runtime DLLs) and with m3j.tools.CgCat (from m3j.jar),
and compares the results byte for byte. The outputs are written to a
temporary directory, or to WORKDIR with --keep.

JAVA defaults to $JDK25/bin/java, else java from the PATH.
"""

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dirs", nargs="+")
    ap.add_argument("--java")
    ap.add_argument("--keep", metavar="WORKDIR")
    args = ap.parse_args()

    files = []
    for d in args.dirs:
        files += sorted(p for p in Path(d).rglob("*") if p.suffix in (".mc", ".ic") and p.is_file())
    if not files:
        sys.exit("no .mc or .ic files found")

    java = args.java
    if not java:
        jdk = os.environ.get("JDK25")
        java = str(Path(jdk) / "bin" / "java") if jdk else "java"
    m3cgcat = shutil.which("m3cgcat")
    if not m3cgcat:
        sys.exit("m3cgcat not found on the PATH")

    work = Path(args.keep) if args.keep else Path(tempfile.mkdtemp(prefix="cg_test"))
    work.mkdir(parents=True, exist_ok=True)
    pairs = []
    for i, f in enumerate(files):
        exp = work / f"{i:05d}.expected.txt"
        got = work / f"{i:05d}.got.txt"
        with open(exp, "wb") as out:
            subprocess.run([m3cgcat, f"-in-binary={f}", "-out-ascii"], stdout=out, check=False)
        pairs.append((f, exp, got))

    batch = work / "batch.txt"
    batch.write_text("".join(f"{f}\t{got}\n" for f, _, got in pairs), encoding="utf-8")
    p = subprocess.run([java, "-cp", str(ROOT / "m3j.jar"), "m3j.tools.CgCat", f"-batch={batch}"],
                       stderr=subprocess.PIPE)
    if p.returncode != 0:
        print(f"CgCat exited with {p.returncode}: {p.stderr.decode('latin-1', 'replace').strip()[:500]}")

    bad = 0
    nbytes = 0
    for f, exp, got in pairs:
        e = exp.read_bytes()
        g = got.read_bytes() if got.exists() else b""
        nbytes += len(e)
        if e != g:
            bad += 1
            if bad <= 10:
                el, gl = e.split(b"\n"), g.split(b"\n")
                k = next((j for j in range(max(len(el), len(gl)))
                          if (el[j] if j < len(el) else None) != (gl[j] if j < len(gl) else None)), 0)
                print(f"FAIL {f} (line {k + 1}):\n"
                      f"  expected {el[k][:200] if k < len(el) else '<eof>'!r}\n"
                      f"  got      {gl[k][:200] if k < len(gl) else '<eof>'!r}")
    print(f"{len(pairs) - bad} of {len(pairs)} files identical ({nbytes} bytes of text)")
    if not args.keep:
        shutil.rmtree(work, ignore_errors=True)
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
