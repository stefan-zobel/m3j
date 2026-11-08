#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Measures which code of m3j the tests execute, with JaCoCo.

Usage:
  python3 tools/coverage.py [SUITE]... [--jacoco DIR] [--out DIR] [--java JAVA]
                            [--report-only] [--top N]

Runs the test suites with the JaCoCo agent in every JVM that they start
(the compiler, the compiled programs and the oracles; --jvm-arg of each
tool, and --aot none where a tool uses the AOT cache: the cache does not
work with an agent), writes the execution data of all of them to
OUT/jacoco.exec, and makes a report from it: OUT/html (per package, class
and method, with the source lines marked), OUT/coverage.csv and
OUT/coverage.xml. Then it prints the coverage per package and the classes
of the back end and the runtime with the most branches that no test
executes (at most N per package, default 15).

The classes measured are those of the build (out): the packages m3j.*
and the runtime units written in Java (out/m3/i), not the classes that
m3j generates. The build must be current (build.sh), else JaCoCo cannot
match the execution data with the classes.

SUITE is one of

  jvm          tools/run_jvm.py p0 p1 p2 r0
  compile      tools/run_jvm.py --compile c0 c1
  diff         tools/diff_test.py
  split        tools/split_test.py
  cli          tools/cli_test.py
  m3front      tools/m3front_test.py
  fingerprint  tools/dump_test.py fingerprint
  middle       tools/dump_test.py middle
  pkg          tools/pkg_gate.py (needs a cm3 installation in cm3)

Without SUITE all but pkg are run. The output of each suite is written to
OUT/logs/SUITE.txt; a suite that fails is reported, and its coverage still
counts. --report-only makes the report from the execution data that is
already in OUT.

DIR (--jacoco) is a directory with jacocoagent.jar and jacococli.jar
somewhere below it, such as the lib directory of the JaCoCo distribution
(0.8.14 or later, for the class files of JDK 25); the default is
download, where it can be unpacked. OUT defaults to a new temporary
directory. JAVA defaults to $JDK25/bin/java, else java from the PATH.
"""

import argparse
import csv
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TOOLS = ROOT / "tools"
OUT_CLASSES = ROOT / "out"

# name: (tool and its arguments, whether the tool has --aot, its --timeout
# or None): the timeouts are twice the tools' defaults, as the agent makes
# the JVM slower
SUITES = {
    "jvm": (["run_jvm.py", "p0", "p1", "p2", "r0"], True, 240),
    "compile": (["run_jvm.py", "--compile", "c0", "c1"], True, 240),
    "diff": (["diff_test.py"], True, 240),
    "split": (["split_test.py"], False, None),
    "cli": (["cli_test.py"], False, None),
    "m3front": (["m3front_test.py"], False, 1200),
    "fingerprint": (["dump_test.py", "fingerprint"], False, None),
    "middle": (["dump_test.py", "middle"], False, None),
    "pkg": (["pkg_gate.py"], False, 1200),
}
DEFAULT = ["jvm", "compile", "diff", "split", "cli", "m3front", "fingerprint", "middle"]
# the packages whose classes are listed per class
DETAIL = ["m3j.jvm", "m3j.rt", "m3.i"]


def find_jar(base, name):
    found = sorted(base.rglob(name))
    if not found:
        sys.exit(f"coverage: {name} not found below {base} (--jacoco DIR)")
    return found[-1]


def provided_classes():
    """The runtime units written in Java: the top-level classes in out/m3/i."""
    return sorted(f.stem for f in (OUT_CLASSES / "m3" / "i").glob("*.class") if "$" not in f.stem)


def agent_option(agent, exec_file):
    includes = ["m3j.*"]
    for c in provided_classes():
        includes += ["m3.i." + c, "m3.i." + c + "$*"]
    return f"-javaagent:{agent}=destfile={exec_file},includes={':'.join(includes)}"


def run_suite(name, args, agent_opt, logs):
    tool, has_aot, timeout = SUITES[name]
    cmd = [sys.executable, str(TOOLS / tool[0])] + tool[1:] + ["--java", args.java, "--jvm-arg=" + agent_opt]
    if has_aot:
        cmd += ["--aot", "none"]
    if timeout:
        cmd += ["--timeout", str(timeout)]
    t = time.time()
    with open(logs / (name + ".txt"), "wb") as log:
        r = subprocess.run(cmd, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    text = (logs / (name + ".txt")).read_text(encoding="latin-1").strip().splitlines()
    last = text[-1] if text else ""
    print(f"{name:12s} exit {r.returncode}  {time.time() - t:7.0f} s  {last[:100]}", flush=True)
    return r.returncode


def report(args, cli, exec_file, out):
    cmd = [args.java, "-jar", str(cli), "report", str(exec_file),
           "--classfiles", str(OUT_CLASSES / "m3j")]
    for c in sorted((OUT_CLASSES / "m3" / "i").glob("*.class")):
        cmd += ["--classfiles", str(c)]
    cmd += ["--sourcefiles", str(ROOT / "src" / "main" / "java"), "--name", "m3j",
            "--html", str(out / "html"), "--csv", str(out / "coverage.csv"), "--xml", str(out / "coverage.xml"),
            "--quiet"]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit("coverage: the report failed:\n" + r.stdout + r.stderr)
    # JaCoCo warns about classes whose execution data does not match (a stale build)
    for line in (r.stdout + r.stderr).splitlines():
        if line.strip():
            print(line)


def pct(missed, covered):
    total = missed + covered
    return f"{100.0 * covered / total:5.1f}%" if total else "    -"


def summary(out, top):
    rows = list(csv.DictReader(open(out / "coverage.csv", newline="")))
    keys = ["INSTRUCTION", "BRANCH", "LINE", "METHOD"]
    packages = {}
    for r in rows:
        p = packages.setdefault(r["PACKAGE"], {k: [0, 0] for k in keys})
        for k in keys:
            p[k][0] += int(r[k + "_MISSED"])
            p[k][1] += int(r[k + "_COVERED"])
    total = {k: [sum(p[k][0] for p in packages.values()), sum(p[k][1] for p in packages.values())] for k in keys}
    print()
    print(f"{'package':24s} {'instr':>7s} {'branch':>7s} {'line':>7s} {'method':>7s}  missed branches")
    for name in sorted(packages) + ["(all)"]:
        p = total if name == "(all)" else packages[name]
        print(f"{name:24s} " + " ".join(f"{pct(*p[k]):>7s}" for k in keys) + f"  {p['BRANCH'][0]:6d}")
    for pkg in DETAIL:
        cls = [r for r in rows if r["PACKAGE"] == pkg]
        cls.sort(key=lambda r: -int(r["BRANCH_MISSED"]))
        print()
        print(f"{pkg}: the classes with the most missed branches")
        for r in cls[:top]:
            if int(r["BRANCH_MISSED"]) == 0:
                break
            print(f"  {r['CLASS']:40s} branch {pct(int(r['BRANCH_MISSED']), int(r['BRANCH_COVERED']))}"
                  f" ({int(r['BRANCH_MISSED']):5d} missed)  line {pct(int(r['LINE_MISSED']), int(r['LINE_COVERED']))}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("suites", nargs="*", metavar="SUITE")
    ap.add_argument("--jacoco", type=Path, default=ROOT / "download")
    ap.add_argument("--out", type=Path)
    ap.add_argument("--java")
    ap.add_argument("--report-only", action="store_true")
    ap.add_argument("--top", type=int, default=15)
    args = ap.parse_args()
    for s in args.suites:
        if s not in SUITES:
            sys.exit(f"coverage: unknown suite {s} (one of {', '.join(SUITES)})")
    if not args.java:
        jdk = os.environ.get("JDK25")
        args.java = str(Path(jdk) / "bin" / "java") if jdk else "java"
    agent = find_jar(args.jacoco, "jacocoagent.jar")
    cli = find_jar(args.jacoco, "jacococli.jar")
    out = (args.out or Path(tempfile.mkdtemp(prefix="m3j-coverage-"))).resolve()
    out.mkdir(parents=True, exist_ok=True)
    exec_file = out / "jacoco.exec"
    if not args.report_only:
        if exec_file.exists():
            # the agent appends: start with no data
            exec_file.unlink()
        logs = out / "logs"
        logs.mkdir(exist_ok=True)
        opt = agent_option(agent, exec_file)
        failed = [s for s in (args.suites or DEFAULT) if run_suite(s, args, opt, logs) != 0]
        if failed:
            print("coverage: suites with failures (see " + str(logs) + "): " + " ".join(failed))
    if not exec_file.exists():
        sys.exit(f"coverage: no execution data in {exec_file}")
    report(args, cli, exec_file, out)
    summary(out, args.top)
    print()
    print("report: " + str(out / "html" / "index.html"))


if __name__ == "__main__":
    main()
