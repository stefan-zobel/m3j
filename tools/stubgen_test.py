#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Runs CM3's stub generator for network objects (stubgen), compiled by
m3j, on the JVM and compares its stubs with those of stubgen built by cm3.

Usage:
  python3 tools/stubgen_test.py [CASE]... [--record] [--java JAVA]
                                [--work DIR] [--keep] [--jobs N]
                                [--timeout SECONDS] [--program JAR]
                                [--stubgen DIR] [--jvm-arg=ARG]... [-v]

tests/stubgen has the program: stubgen (in stubgen/src) and the packages
of cm3 it is built on, m3tk and m3tk-misc. First m3j compiles it, and
every class must verify (--program JAR uses a program built before; the
JVM options of --jvm-arg are for the compiling). Then it runs each case.

A case is a line of tests/stubgen/cases.list: PKG, a network object type
INTF.T and, for netobjrv1, the interface that -i names. The interfaces of
PKG are in tests/stubgen/cases/PKG, the others they import in
tests/stubgen/intf. Each case runs twice, for the versions 1 and 2 of the
protocol, each in a directory of its own, as the netobj template of cm3
runs stubgen:

  stubgen -vN -sno INTF.T [-i USE] -Dcases/PKG;intf

The files that it writes there (the stubs INTF_T_vN.i3, .m3), its output
and its exit code must be those of stubgen built by cm3, recorded in
tests/stubgen/expected/PKG/INTF.T-vN: the files, and run.txt with the
exit code and the output (the path of tests/stubgen written as
<stubgen>). CASE is PKG or PKG/INTF.T; without arguments all cases run.

--record builds stubgen with cm3 (cm3 -build in a copy of DIR, by
default the stubgen of the sources of cm3, cm3-src/m3-comm/stubgen; cm3
must be on the PATH, with its C environment) and records its results for
the cases, instead of running m3j's.

JAVA defaults to $JDK25/bin/java, else java from the PATH.
"""

import argparse
import concurrent.futures
import os
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests" / "stubgen"
EXPECTED = TESTS / "expected"
PACKAGES = ["m3tk", "m3tk-misc"]
SEP = ";" if os.name == "nt" else ":"
RUN = "run.txt"


def build(args, work):
    jar = work / "stubgen.jar"
    src = TESTS / "stubgen" / "src"
    cmd = [args.java] + args.jvm_arg + ["-Xss64m", "-cp", str(ROOT / "m3j.jar"), "m3j.jvm.Compile"]
    for p in PACKAGES:
        cmd += ["-pkg", str(TESTS / p)]
    cmd += [str(f) for f in sorted(src.glob("*.[im]3"))] + ["-d", str(jar)]
    t = time.time()
    c = subprocess.run(cmd, capture_output=True)
    out = (c.stdout + c.stderr).decode("latin-1")
    if c.returncode != 0:
        print(out)
        sys.exit("stubgen: the program does not compile")
    if args.verbose:
        print(out, end="")
    print("built %s in %.1f s" % (jar, time.time() - t))
    return jar


def build_native(args, work):
    """stubgen built by cm3 from the sources in args.stubgen."""
    d = work / "native"
    if d.exists():
        shutil.rmtree(d)
    shutil.copytree(Path(args.stubgen), d)
    c = subprocess.run(["cm3", "-build"], cwd=d / "src", capture_output=True, shell=os.name == "nt")
    exe = d / "AMD64_NT" / ("stubgen.exe" if os.name == "nt" else "stubgen")
    if c.returncode != 0 or not exe.exists():
        print((c.stdout + c.stderr).decode("latin-1"))
        sys.exit("stubgen: cm3 does not build it")
    print("built %s with cm3" % exe)
    return exe


class Case:
    def __init__(self, line):
        parts = line.split()
        self.pkg, self.obj = parts[0], parts[1]
        self.use = parts[2] if len(parts) > 2 else None
        self.name = self.pkg + "/" + self.obj

    def args(self, version):
        search = SEP.join(str(p) for p in (TESTS / "cases" / self.pkg, TESTS / "intf"))
        return ["-v%d" % version, "-sno", self.obj] + (["-i", self.use] if self.use else []) + ["-D" + search]


def read_cases(names):
    cases = [Case(l) for l in (TESTS / "cases.list").read_text(encoding="latin-1").splitlines() if l.strip()]
    if names:
        cases = [c for c in cases if c.pkg in names or c.name in names]
        if not cases:
            sys.exit("stubgen: no such case: " + " ".join(names))
    return cases


def normalize(b):
    """The output of a run, with the path of tests/stubgen written as <stubgen>."""
    t = b.decode("latin-1")
    for p in (str(TESTS), TESTS.as_posix()):
        t = t.replace(p, "<stubgen>")
    return t


def run(args, cmd, d):
    """Runs stubgen in the new directory d: {file: bytes} of what it wrote, and run.txt."""
    if d.exists():
        shutil.rmtree(d)
    d.mkdir(parents=True)
    try:
        c = subprocess.run(cmd, cwd=d, capture_output=True, timeout=args.timeout)
        text = "exit %d\n--- stdout\n%s--- stderr\n%s" % (c.returncode, normalize(c.stdout), normalize(c.stderr))
    except subprocess.TimeoutExpired:
        text = "timeout\n"
    files = {f.name: f.read_bytes() for f in sorted(d.iterdir()) if f.is_file()}
    files[RUN] = text.replace("\r\n", "\n").encode("latin-1")
    return files


def expected_dir(case, version):
    return EXPECTED / case.pkg / ("%s-v%d" % (case.obj, version))


def record_case(args, exe, case, version, work):
    files = run(args, [str(exe)] + case.args(version), work / "record" / case.pkg / ("%s-v%d" % (case.obj, version)))
    e = expected_dir(case, version)
    old = {f.name: f.read_bytes() for f in e.iterdir()} if e.exists() else None
    if e.exists():
        shutil.rmtree(e)
    e.mkdir(parents=True)
    for n, b in files.items():
        (e / n).write_bytes(b)
    status = "SAME" if old == files else ("NEW" if old is None else "CHANGED")
    return status, "%d files, %s" % (len(files) - 1, files[RUN].decode("latin-1").splitlines()[0])


def check_case(args, jar, case, version, work):
    e = expected_dir(case, version)
    if not e.exists():
        return "NOT-RECORDED", ""
    cmd = [args.java, "-Xss64m", "-cp", str(ROOT / "m3j-runtime.jar") + SEP + str(jar), "m3.Main"] + case.args(version)
    got = run(args, cmd, work / "jvm" / case.pkg / ("%s-v%d" % (case.obj, version)))
    want = {f.name: f.read_bytes() for f in e.iterdir()}
    if got == want:
        return "PASS", "%d files" % (len(want) - 1)
    for n in sorted(set(want) | set(got)):
        if n not in got:
            return "FAIL", "%s: not written" % n
        if n not in want:
            return "FAIL", "%s: written, cm3's stubgen does not" % n
        if got[n] != want[n]:
            a = want[n].decode("latin-1").splitlines()
            b = got[n].decode("latin-1").splitlines()
            j = next((i for i in range(max(len(a), len(b)))
                      if (a[i] if i < len(a) else None) != (b[i] if i < len(b) else None)), None)
            if j is None:
                return "FAIL", "%s: the bytes differ (line ends), the lines do not" % n
            return "FAIL", "%s line %d: expected %r, got %r" % (
                n, j + 1, a[j] if j < len(a) else None, b[j] if j < len(b) else None)
    return "FAIL", "?"


def main():
    ap = argparse.ArgumentParser(description="CM3's stubgen on the JVM against stubgen built by cm3")
    ap.add_argument("cases", nargs="*")
    ap.add_argument("--record", action="store_true")
    ap.add_argument("--java")
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--jobs", type=int, default=max(1, (os.cpu_count() or 2) // 2))
    ap.add_argument("--timeout", type=int, default=300)
    ap.add_argument("--program")
    ap.add_argument("--stubgen", default=str(ROOT / "cm3-src" / "m3-comm" / "stubgen"))
    ap.add_argument("--jvm-arg", action="append", default=[],
                    help="an option for the JVM that compiles the program (repeatable; --jvm-arg=-X...)")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()
    if not args.java:
        jdk = os.environ.get("JDK25")
        args.java = str(Path(jdk) / "bin" / "java") if jdk else "java"
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="stubgen_"))
    work.mkdir(parents=True, exist_ok=True)
    cases = read_cases(args.cases)
    try:
        if args.record:
            exe = build_native(args, work)
            fn = lambda c, v: record_case(args, exe, c, v, work)  # noqa: E731
        else:
            jar = Path(args.program) if args.program else build(args, work)
            fn = lambda c, v: check_case(args, jar, c, v, work)  # noqa: E731
        counts = {}
        t = time.time()
        with concurrent.futures.ThreadPoolExecutor(args.jobs) as pool:
            futures = [(c, v, pool.submit(fn, c, v)) for c in cases for v in (1, 2)]
            for c, v, f in futures:
                status, detail = f.result()
                counts[status] = counts.get(status, 0) + 1
                if status not in ("PASS", "SAME") or args.verbose:
                    print("%-8s %s -v%d  %s" % (status, c.name, v, detail), flush=True)
        print(", ".join("%s %d" % (k, counts[k]) for k in sorted(counts)) + "  (%.1f s)" % (time.time() - t))
        return 0 if set(counts) <= {"PASS", "SAME", "NEW", "CHANGED"} else 1
    finally:
        if not args.keep and not args.work:
            shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
