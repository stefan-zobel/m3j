#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Test of the debugger of m3j: the description of each unit that the
compiler writes into its class for a debugger.

  python3 tools/dap_test.py [CASE]... [--java JAVA] [--jar JAR] [--work DIR]
                            [--keep] [--record]

The program tests/dap/pkgs/debug is compiled with -Dm3j.maxMethod=300 (so
that its big procedures are split in chunks) into a jar. The cases:

  info   m3j.debug.Dump prints the descriptions of the units of the
         program (Main, Shapes, IntStack) as Modula-3; the output must be
         tests/dap/expected/info.txt (--record writes it; check it by hand
         against the sources)
  check  tests/dap/InfoCheck.java checks the descriptions against the
         classes of the program and of the jars that m3j compiles from
         Modula-3 next to JAR (m3build, m3bundle, stubgen, the language
         server, those of them that build.sh made): every method with
         lines is described, every named slot is a described variable,
         every variable in a frame is a field of the frame
"""

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests" / "dap"
PKG = TESTS / "pkgs" / "debug" / "src"
JARS = ["m3build.jar", "m3bundle.jar", "stubgen.jar", "m3_lang_server.jar"]
UNITS = ["Main", "Shapes", "IntStack"]
# the warnings that the program gives on purpose
EXPECTED_WARNINGS = [
    "warning: Open array passed by value.",
    "warning: potentially unhandled exception: Main.Oops",
    "m3j: warning: no module of interface RT0 is compiled",
]


class Failed(Exception):
    pass


def run(cmd, **kw):
    r = subprocess.run(cmd, capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=600, **kw)
    if r.returncode:
        raise Failed("%s: exit %d\n%s%s" % (Path(cmd[0]).name, r.returncode, r.stdout, r.stderr))
    return r


class Env:
    def __init__(self, args, work):
        self.args = args
        self.work = work
        self.java = args.java
        self.program = work / "debug.jar"
        self.tools = work / "tools"

    def compile(self):
        r = run([self.java, "-Dm3j.maxMethod=300", "-cp", self.args.jar, "m3j.jvm.Compile",
                 *[str(PKG / (u + ".m3")) for u in UNITS], "-d", str(self.program)], cwd=self.work)
        for line in (r.stdout + r.stderr).splitlines():
            if "warning" in line and "warnings encountered" not in line \
                    and not any(w in line for w in EXPECTED_WARNINGS):
                raise Failed("compile:\n" + r.stdout + r.stderr)
        javac = str(Path(self.java).with_name("javac"))
        run([javac, "--release", "25", "-cp", self.args.jar, "-d", str(self.tools), str(TESTS / "InfoCheck.java")])


def compare(got, name, args):
    expected = TESTS / "expected" / name
    if args.record:
        expected.parent.mkdir(parents=True, exist_ok=True)
        expected.write_text(got, newline="\n")
        return "recorded"
    want = expected.read_text()
    if got != want:
        g, w = got.splitlines(), want.splitlines()
        i = next((k for k in range(min(len(g), len(w))) if g[k] != w[k]), min(len(g), len(w)))
        raise Failed("differs from %s at line %d:\n  got  %s\n  want %s"
                     % (expected.name, i + 1, g[i] if i < len(g) else "(end)", w[i] if i < len(w) else "(end)"))
    return None


def info(env):
    units = [a for u in UNITS for a in ("-unit", u)]
    r = run([env.java, "-cp", env.args.jar, "m3j.debug.Dump", *units, str(env.program)])
    got = r.stdout.replace("\r\n", "\n")
    return compare(got, "info.txt", env.args) or "%d units" % got.count(": module ")


def check(env):
    jars = [str(p) for p in (Path(env.args.jar).with_name(n) for n in JARS) if p.exists()]
    if not jars:
        raise Failed("no jar of build.sh next to " + env.args.jar)
    r = run([env.java, "-cp", env.args.jar + os.pathsep + str(env.tools), "InfoCheck", str(env.program), *jars])
    return "%s (%s)" % (r.stdout.strip(), ", ".join(Path(j).name for j in jars))


CASES = {"info": info, "check": check}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cases", nargs="*")
    ap.add_argument("--java", default=str(Path(os.environ["JDK25"]) / "bin" / "java") if os.environ.get("JDK25")
                    else shutil.which("java") or "java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--record", action="store_true")
    args = ap.parse_args()
    for c in args.cases:
        if c not in CASES:
            ap.error("unknown case %s (known: %s)" % (c, ", ".join(CASES)))
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="dap_test"))
    work.mkdir(parents=True, exist_ok=True)
    env = Env(args, work)
    failed = []
    try:
        env.compile()
        for name in args.cases or CASES:
            t0 = time.time()
            try:
                note = CASES[name](env)
                print("ok    %-6s %.1f s  %s" % (name, time.time() - t0, note))
            except Exception as e:
                failed.append(name)
                print("FAIL  %-6s %s" % (name, str(e)[:3000]))
    except Failed as e:
        failed.append("compile")
        print("FAIL  compile %s" % str(e)[:3000])
    finally:
        if not args.keep and not args.work:
            shutil.rmtree(work, ignore_errors=True)
    print("%d failed" % len(failed) if failed else "all passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
