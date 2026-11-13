#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Runs the tests of m3j that need only the tree, in groups, and reports
each test as PASS or FAIL with its time.

  python3 tools/suite.py [--list] [--logs DIR] GROUP...

GROUP is one of

  backend   m3tests (p0-p2, r0, the compile tests c0 and c1), split_test,
            lvt_test, cli_test, dump_test fingerprint and middle
  programs  diff_test: test programs, examples and Rosetta Code against
            the results of cm3
  tools     programs compiled by m3j: m3front_test, stubgen_test,
            bundle_test, build_test (quake, cases), bind_test, lsp_test,
            dap_test
  libs      the libraries: zip, regexp, encoding, crypto, json, xml, tls,
            http, net, ssh, sql
  apps      feedreader_test, and on Linux a release packed by dist.sh into
            a new directory and tested by release_test (releases are
            packed on Linux; the workflow Release tests that archive on
            every system)
  all       all of them

Each group takes about 15 minutes or less on a machine with 8 cores.

or the name of one test (--list shows them). Every test runs, also after
one has failed; the exit status is 1 if one failed. The output of each
test goes to the console and to DIR/NAME.log (DIR by default a new
directory below the temporary directory). In GitHub Actions the output of
each test is a group of the log and the table of results goes into the
summary of the job.

The tests need what tools/README.md lists for them: the jars of build.sh,
JDK 25 ($JDK25, else $JAVA_HOME), Python packages, OpenSSH, openssl, node,
and the drivers of JDBC in the local repository of Maven.
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
PY = sys.executable


def tool(name, *args):
    return [PY, str(ROOT / "tools" / name)] + list(args)


GROUPS = {
    "backend": [
        ("m3tests", tool("run_jvm.py", "p0", "p1", "p2", "r0")),
        ("m3tests-compile", tool("run_jvm.py", "--compile", "c0", "c1")),
        ("split", tool("split_test.py")),
        ("lvt", tool("lvt_test.py")),
        ("cli", tool("cli_test.py")),
        ("fingerprint", tool("dump_test.py", "fingerprint")),
        ("middle", tool("dump_test.py", "middle")),
    ],
    "programs": [
        ("diff", tool("diff_test.py")),
    ],
    "tools": [
        ("m3front", tool("m3front_test.py")),
        ("stubgen", tool("stubgen_test.py")),
        ("bundle", tool("bundle_test.py")),
        ("build", tool("build_test.py", "quake", "cases")),
        ("bind", tool("bind_test.py")),
        ("lsp", tool("lsp_test.py")),
        ("dap", tool("dap_test.py")),
    ],
    "libs": [(name, tool(name + "_test.py")) for name in
             ("zip", "regexp", "encoding", "crypto", "json", "xml", "tls", "http", "net", "ssh", "sql")],
    "apps": [
        ("feedreader", tool("feedreader_test.py")),
        ("release", None),
    ],
}


def run_release(log):
    """dist.sh into a new directory, then release_test.py on its archive."""
    if not sys.platform.startswith("linux"):
        return "skipped: a release is packed on Linux"
    out = Path(tempfile.mkdtemp(prefix="m3j-dist-"))
    if run(["sh", str(ROOT / "dist.sh"), str(out)], log) != 0:
        return False
    archives = sorted(out.glob("m3j-*.tar.gz"))
    if len(archives) != 1:
        log.write("dist.sh made no single archive in %s\n" % out)
        return False
    ok = run(tool("release_test.py", str(archives[0])), log) == 0
    if ok:
        shutil.rmtree(out, ignore_errors=True)
    return ok


def run(cmd, log):
    """Runs cmd in the root, its output to the console and to log."""
    log.write("$ %s\n" % " ".join(cmd))
    log.flush()
    p = subprocess.Popen(cmd, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    for line in iter(p.stdout.readline, b""):
        text = line.decode("utf-8", "replace")
        sys.stdout.write(text)
        sys.stdout.flush()
        log.write(text)
    return p.wait()


def main():
    ap = argparse.ArgumentParser(description="Runs the tests of m3j in groups.")
    ap.add_argument("groups", nargs="*")
    ap.add_argument("--list", action="store_true", help="show the groups and their tests")
    ap.add_argument("--logs", help="directory for the log of each test")
    args = ap.parse_args()

    if args.list:
        for g, tests in GROUPS.items():
            print("%-9s %s" % (g, " ".join(name for name, _ in tests)))
        return 0
    if not args.groups:
        ap.error("name a group or a test (--list shows them)")

    by_name = {name: (name, cmd) for tests in GROUPS.values() for name, cmd in tests}
    chosen = []
    for g in args.groups:
        if g == "all":
            items = [t for tests in GROUPS.values() for t in tests]
        elif g in GROUPS:
            items = GROUPS[g]
        elif g in by_name:
            items = [by_name[g]]
        else:
            ap.error("no group or test %s (--list shows them)" % g)
        chosen += [t for t in items if t not in chosen]

    # the tests take the JDK from JDK25
    if not os.environ.get("JDK25") and os.environ.get("JAVA_HOME"):
        os.environ["JDK25"] = os.environ["JAVA_HOME"]
    if not os.environ.get("JDK25"):
        print("suite: JDK25 (or JAVA_HOME) is not set; the tests take java from the PATH", flush=True)
    os.environ["PYTHONUNBUFFERED"] = "1"

    logs = Path(args.logs) if args.logs else Path(tempfile.mkdtemp(prefix="m3j-suite-"))
    logs.mkdir(parents=True, exist_ok=True)
    actions = os.environ.get("GITHUB_ACTIONS") == "true"

    results = []
    for name, cmd in chosen:
        if actions:
            print("::group::%s" % name, flush=True)
        else:
            print("=== %s" % name, flush=True)
        t = time.time()
        with open(logs / (name + ".log"), "w", encoding="utf-8", newline="\n") as log:
            if cmd is None:
                got = run_release(log)
            else:
                got = run(cmd, log) == 0
        secs = time.time() - t
        if got is True:
            status, note = "PASS", ""
        elif got is False:
            status, note = "FAIL", "see %s" % (logs / (name + ".log"))
        else:
            status, note = "SKIP", got
        if actions:
            print("::endgroup::", flush=True)
            if status == "FAIL":
                print("::error title=%s::%s failed" % (name, name), flush=True)
        print("%-4s  %-16s %7.1f s  %s" % (status, name, secs, note), flush=True)
        results.append((name, status, secs, note))

    print()
    print("Results (logs in %s):" % logs)
    for name, status, secs, note in results:
        print("  %-4s  %-16s %7.1f s  %s" % (status, name, secs, note))
    failed = [r for r in results if r[1] == "FAIL"]
    print("%d passed, %d failed, %d skipped" % (sum(r[1] == "PASS" for r in results), len(failed),
                                               sum(r[1] == "SKIP" for r in results)))

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write("| Test | Result | Time |\n|---|---|---|\n")
            for name, status, secs, note in results:
                f.write("| %s | %s%s | %.0f s |\n" % (name, status, " (%s)" % note if status == "SKIP" else "", secs))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
