#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Test of the LocalVariableTable that m3j writes, which gives a Java
debugger the names of the variables.

  python3 tools/lvt_test.py [CASE]... [--java JAVA] [--jar JAR] [--work DIR]
                            [--keep] [--record]

The cases:

  probe  tests/lvt/src/Main.m3 is compiled and run under the debugger
         tests/lvt/Probe.java (on JDI), which stops at each line marked
         "(* probe *)" and prints the variables it sees, with their
         values; the output must be tests/lvt/expected.txt (--record
         writes it; check it by hand against Main.m3)
  check  tests/lvt/Check.java checks every entry of the table in the
         classes of Main.m3 and in the jars that m3j compiles from
         Modula-3 next to JAR (m3build, m3bundle, stubgen, the language
         server, those of them that build.sh made): its range, its slot
         and that every access of its slot is of the kind of its type
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
TESTS = ROOT / "tests" / "lvt"
JARS = ["m3build.jar", "m3bundle.jar", "stubgen.jar", "m3_lang_server.jar"]


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
        self.classes = work / "classes"
        self.tools = work / "tools"

    def compile(self):
        r = run([self.java, "-cp", self.args.jar, "m3j.jvm.Compile", str(TESTS / "src" / "Main.m3"),
                 "-d", str(self.classes)], cwd=self.work)
        # the formal b of Sum is an open array passed by value on purpose
        out = (r.stdout + r.stderr).replace("warning: Open array passed by value.", "")
        if "warning" in out.replace("1 warning encountered", ""):
            raise Failed("compile:\n" + r.stdout + r.stderr)
        javac = str(Path(self.java).with_name("javac"))
        run([javac, "-d", str(self.tools), str(TESTS / "Probe.java"), str(TESTS / "Check.java")])


def probe(env):
    lines = [str(i + 1) for i, s in enumerate((TESTS / "src" / "Main.m3").read_text().splitlines())
             if "(* probe *)" in s]
    runtime = str(Path(env.args.jar).with_name("m3j-runtime.jar"))
    environ = dict(os.environ, CLASSPATH=runtime + os.pathsep + str(env.classes))
    r = run([env.java, "-cp", str(env.tools), "Probe", "m3.Main", "m3.m.Main", *lines], env=environ)
    got = r.stdout.replace("\r\n", "\n")
    expected = TESTS / "expected.txt"
    if env.args.record:
        expected.write_text(got, newline="\n")
        return "recorded"
    want = expected.read_text()
    if got != want:
        g, w = got.splitlines(), want.splitlines()
        i = next((k for k in range(min(len(g), len(w))) if g[k] != w[k]), min(len(g), len(w)))
        raise Failed("differs from %s at line %d:\n  got  %s\n  want %s"
                     % (expected.name, i + 1, g[i] if i < len(g) else "(end)", w[i] if i < len(w) else "(end)"))
    return "%d stops" % got.count("\nline ")


def check(env):
    jars = [str(p) for p in (Path(env.args.jar).with_name(n) for n in JARS) if p.exists()]
    if not jars:
        raise Failed("no jar of build.sh next to " + env.args.jar)
    r = run([env.java, "-cp", str(env.tools), "Check", str(env.classes), *jars])
    return "%s (%s)" % (r.stdout.strip(), ", ".join(Path(j).name for j in jars))


CASES = {"probe": probe, "check": check}


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
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="lvt_test"))
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
