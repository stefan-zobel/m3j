#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Runs the benchmarks of tests/bench and compares the run times.

  python3 tools/perf.py [NAME]... [--jar KEY=JAR]... [--native] [--runs N]
                        [--stat median|min] [--work DIR] [--java JAVA]
                        [--jvm-arg ARG]... [--aot auto|none|FILE] [--log FILE]

Every benchmark is a program tests/bench/NAME.m3 (module Main) that prints
a checksum. Each variant compiles it once: a --jar KEY=JAR is the m3j.jar
of a version of m3j (default: m3j=m3j.jar of this tree; give two to compare
versions A/B), --native adds the program built by cm3 with -O (cm3 and the
C compiler must be on the PATH). Then the variants run in turn, RUNS times
each (default 5), so that drift of the machine load hits all alike; one
run at a time. The time of a run is the wall time of the process, JVM
start included, and compile time is not. Printed is the median of the runs
(--stat min: the minimum) per program and variant, and the sum. The output
of every variant must be the same, else the program is marked. The AOT
cache that build.sh makes next to a jar (m3j.aot) is used for compiling
and running, unless --aot none.
"""

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BENCH = ROOT / "tests" / "bench"
SEP = ";" if os.name == "nt" else ":"


def compile_jvm(args, jar, src, out):
    """Compiles src with the m3j of jar into the directory out; the classes are in out/classes."""
    out.mkdir(parents=True, exist_ok=True)
    shutil.copy(src, out / "Main.m3")
    aot = aot_option(args, jar)
    cmd = [args.java] + aot + ["-Xss64m", "-cp", jar, "m3j.jvm.Compile"] + (["-cm3", str(args.cm3)] if args.cm3 else []) + [
           "Main.m3", "-d", "classes"]
    r = subprocess.run(cmd, cwd=out, capture_output=True)
    if r.returncode != 0:
        raise RuntimeError("m3j failed on " + src.name + ":\n" + (r.stdout + r.stderr).decode(errors="replace"))
    return [args.java] + aot + args.jvm_arg + ["-cp", jar + SEP + "classes", "m3.Main"]


def aot_option(args, jar):
    """The option of the AOT cache for jar: --aot auto takes the .aot file next to it, if there is one."""
    if args.aot == "none":
        return []
    f = Path(jar).with_suffix(".aot") if args.aot == "auto" else Path(args.aot)
    return ["-XX:AOTCache=" + str(f)] if f.exists() else []


def compile_native(args, src, out):
    """Builds src with cm3 -O in out; returns the command of the program."""
    s = out / "src"
    s.mkdir(parents=True, exist_ok=True)
    shutil.copy(src, s / "Main.m3")
    (s / "m3makefile").write_text('import("libm3")\nimplementation("Main")\nprogram("bench")\n')
    r = subprocess.run(["cm3", "-build", "-O"], cwd=s, capture_output=True, shell=os.name == "nt")
    exe = next((p for p in out.glob("*/bench.exe")), None) or next((p for p in out.glob("*/bench")), None)
    if r.returncode != 0 or exe is None:
        raise RuntimeError("cm3 failed on " + src.name + ":\n" + (r.stdout + r.stderr).decode(errors="replace"))
    return [str(exe)]


def run(cmd, cwd):
    t0 = time.monotonic()
    r = subprocess.run(cmd, cwd=cwd, capture_output=True, stdin=subprocess.DEVNULL)
    t = time.monotonic() - t0
    return t, r.returncode, r.stdout.replace(b"\r\n", b"\n")


def main():
    ap = argparse.ArgumentParser(description="Runs the benchmarks of tests/bench.")
    ap.add_argument("names", nargs="*")
    ap.add_argument("--jar", action="append", default=[], help="KEY=JAR, a version of m3j to measure")
    ap.add_argument("--native", action="store_true", help="also the programs built by cm3 -O")
    ap.add_argument("--runs", type=int, default=5)
    ap.add_argument("--stat", choices=["median", "min"], default="median")
    ap.add_argument("--work", type=Path)
    ap.add_argument("--java")
    ap.add_argument("--jvm-arg", action="append", default=[])
    ap.add_argument("--cm3", type=Path, default=None,
                    help="a cm3 installation, for a version of m3j without its own library")
    ap.add_argument("--aot", default="auto",
                    help="auto (the .aot file next to each jar, if there is one), none, or a file")
    ap.add_argument("--log", type=Path, help="append all times of each program as a line of JSON")
    args = ap.parse_args()
    if not args.java:
        jdk = os.environ.get("JDK25")
        args.java = str(Path(jdk) / "bin" / "java") if jdk else "java"
    variants = []
    for j in args.jar or ["m3j=" + str(ROOT / "m3j.jar")]:
        key, _, jar = j.partition("=")
        if not jar:
            sys.exit("--jar needs KEY=JAR")
        variants.append((key, str(Path(jar).resolve())))
    keys = [k for k, _ in variants] + (["cm3"] if args.native else [])
    names = args.names or sorted(p.stem for p in BENCH.glob("*.m3"))
    work = args.work or Path(tempfile.mkdtemp(prefix="m3j-perf-"))
    total = {k: 0.0 for k in keys}
    print(" ".join(f"{k:>8}" for k in keys) + "  program", flush=True)
    for n in names:
        src = BENCH / (n + ".m3")
        cmds = {}
        try:
            for k, jar in variants:
                d = work / n / k
                cmds[k] = (compile_jvm(args, jar, src, d), d)
            if args.native:
                d = work / n / "cm3"
                cmds["cm3"] = (compile_native(args, src, d), d)
        except RuntimeError as e:
            print(f"{n}: {e}", file=sys.stderr, flush=True)
            continue
        times = {k: [] for k in keys}
        outs = {}
        for _ in range(args.runs):
            for k in keys:
                t, rc, out = run(*cmds[k])
                times[k].append(t)
                outs.setdefault(k, (rc, out))
        if args.log:
            with open(args.log, "a") as f:
                f.write(json.dumps({"name": n, "times": times}) + "\n")
        if args.stat == "median":
            best = {k: sorted(t)[len(t) // 2] for k, t in times.items()}
        else:
            best = {k: min(t) for k, t in times.items()}
        for k in keys:
            total[k] += best[k]
        same = len(set(outs.values())) == 1
        mark = "" if same else "  (outputs differ: " + ", ".join(
            f"{k}: rc {rc}, {out[:60]!r}" for k, (rc, out) in outs.items()) + ")"
        print(" ".join(f"{best[k]:8.2f}" for k in keys) + "  " + n + mark, flush=True)
    print(" ".join(f"{total[k]:8.2f}" for k in keys) + "  sum")
    if not args.work:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
