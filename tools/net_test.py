#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Tests of programs that talk over TCP: a server and a client, built by
m3j and by cm3, in each pairing, against the record of cm3's pair.

Usage:
  python3 tools/net_test.py [TEST]... [--cm3] [--record] [--java JAVA]
                            [--work DIR] [--keep] [--timeout SECONDS] [-v]

A test is a package tests/net/TEST (src/m3makefile, src/Main.m3 and the
units it needs besides those that m3j brings). Its program runs as

  PROGRAM server       prints "port N" as its first line, the port on the
                       loopback address that it accepts connections on,
                       serves one client and ends (when the client is done,
                       or at the end of its standard input, which is closed
                       when the client has ended)
  PROGRAM client N     is that client

Without options m3j compiles the program, and its server and client run
together; their outputs (the server's without the line of the port) must
be those of the server and client built by cm3, recorded in
tests/net/expected/TEST/server.txt and client.txt. With --cm3, cm3 builds
the program too (cm3 must be on the PATH, with its C environment), and
the server and client of each pairing (m3j-m3j, cm3-m3j, m3j-cm3 and
cm3-cm3) run against the record: so the programs of m3j and of cm3 must
understand each other's bytes. --record builds the program with cm3 and
records the outputs of its server and client instead, and the units that
the build of cm3 generates (the stubs of network objects that stubgen
writes for netobj() of the m3makefile) in tests/net/expected/TEST/gen,
which m3j compiles with the program. Without arguments all tests run.

A test may also have runs of a server and a client of m3j whose output is
written by hand, for what the record of cm3 cannot show: each directory
tests/net/expected/TEST/m3j-NAME holds the arguments that the client gets
after the port (the words of the file args) and the outputs of the server
and the client (server.txt and client.txt); --record leaves them alone.

JAVA defaults to $JDK25/bin/java, else java from the PATH.
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
TESTS = ROOT / "tests" / "net"
EXPECTED = TESTS / "expected"
SEP = ";" if os.name == "nt" else ":"


def tests(names):
    found = sorted(p.name for p in TESTS.iterdir() if (p / "src" / "m3makefile").exists())
    for n in names:
        if n not in found:
            sys.exit("net_test: no such test: " + n)
    return names or found


def build_m3j(args, name, work):
    """The command that runs the program of test name, compiled by m3j."""
    out = work / name / "m3j"
    out.mkdir(parents=True, exist_ok=True)
    src = TESTS / name / "src"
    cmd = [args.java, "-Xss64m", "-cp", str(ROOT / "m3j.jar"), "m3j.jvm.Compile", "Main.m3"]
    gen = EXPECTED / name / "gen"
    if gen.is_dir():
        cmd += [str(f) for f in sorted(gen.iterdir())]
    cmd += ["-d", str(out)]
    c = subprocess.run(cmd, cwd=src, capture_output=True)
    msg = (c.stdout + c.stderr).decode("latin-1")
    if c.returncode != 0:
        print(msg)
        return None
    if args.verbose and msg:
        print(msg, end="")
    return [args.java, "-cp", str(ROOT / "m3j-runtime.jar") + SEP + str(out), "m3.Main"]


def build_cm3(args, name, work):
    """The command that runs the program of test name, built by cm3."""
    d = work / name / "cm3"
    if d.exists():
        shutil.rmtree(d)
    shutil.copytree(TESTS / name, d)
    c = subprocess.run(["cm3", "-build"], cwd=d / "src", capture_output=True, shell=os.name == "nt")
    exes = sorted((d / "AMD64_NT").glob("*.exe")) if (d / "AMD64_NT").is_dir() else []
    if c.returncode != 0 or not exes:
        print((c.stdout + c.stderr).decode("latin-1"))
        return None
    if args.record:
        # the units that the build generated (stubs), for m3j
        gen = EXPECTED / name / "gen"
        if gen.exists():
            shutil.rmtree(gen)
        for f in sorted((d / "AMD64_NT").iterdir()):
            if f.suffix in (".i3", ".m3"):
                gen.mkdir(parents=True, exist_ok=True)
                shutil.copy(f, gen / f.name)
    return [str(exes[0])]


def text(b):
    return b.decode("latin-1").replace("\r\n", "\n")


def run_pair(args, server, client, work, extra=()):
    """Runs a server and its client, which gets the arguments extra after
    the port: (server output, client output), or a text that says what
    went wrong."""
    work.mkdir(parents=True, exist_ok=True)
    s = subprocess.Popen(server + ["server"], cwd=work, stdin=subprocess.PIPE,
                         stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    try:
        first = s.stdout.readline().decode("latin-1").strip()
        if not first.startswith("port "):
            return None, "server: " + (first or "no port")
        try:
            c = subprocess.run(client + ["client", first[5:]] + list(extra), cwd=work, capture_output=True,
                               timeout=args.timeout)
        except subprocess.TimeoutExpired:
            return None, "client: timeout"
        try:
            # the end of the server's input ends a server that waits for it
            rest, _ = s.communicate(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            return None, "server: timeout"
        cout = text(c.stdout + c.stderr)
        if c.returncode != 0:
            cout += "exit %d\n" % c.returncode
        sout = text(rest)
        if s.returncode != 0:
            sout += "exit %d\n" % s.returncode
        return sout, cout
    finally:
        if s.poll() is None:
            s.kill()
            s.wait()


def first_diff(want, got):
    a, b = want.splitlines(), got.splitlines()
    for i in range(max(len(a), len(b))):
        x = a[i] if i < len(a) else None
        y = b[i] if i < len(b) else None
        if x != y:
            return "line %d: expected %r, got %r" % (i + 1, x, y)
    return "?"


def main():
    ap = argparse.ArgumentParser(description="servers and clients of m3j and cm3 against cm3's")
    ap.add_argument("tests", nargs="*")
    ap.add_argument("--cm3", action="store_true", help="also the pairings with programs built by cm3")
    ap.add_argument("--record", action="store_true")
    ap.add_argument("--java")
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--timeout", type=int, default=120)
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()
    if not args.java:
        jdk = os.environ.get("JDK25")
        args.java = str(Path(jdk) / "bin" / "java") if jdk else "java"
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="net_"))
    work.mkdir(parents=True, exist_ok=True)
    counts = {}

    def report(status, what, detail=""):
        counts[status] = counts.get(status, 0) + 1
        if status not in ("PASS", "RECORDED") or args.verbose:
            print("%-9s %s  %s" % (status, what, detail), flush=True)

    t0 = time.time()
    try:
        for name in tests(args.tests):
            progs = {}
            if args.record or args.cm3:
                progs["cm3"] = build_cm3(args, name, work)
                if progs["cm3"] is None:
                    report("CM3-FAILED", name)
                    continue
            if args.record:
                sout, cout = run_pair(args, progs["cm3"], progs["cm3"], work / name / "run-cm3-cm3")
                if sout is None:
                    report("CM3-FAILED", name, cout)
                    continue
                e = EXPECTED / name
                e.mkdir(parents=True, exist_ok=True)
                (e / "server.txt").write_bytes(sout.encode("latin-1"))
                (e / "client.txt").write_bytes(cout.encode("latin-1"))
                report("RECORDED", name)
                continue
            e = EXPECTED / name
            if not (e / "client.txt").exists():
                report("NOT-RECORDED", name)
                continue
            want_s = text((e / "server.txt").read_bytes())
            want_c = text((e / "client.txt").read_bytes())
            progs["m3j"] = build_m3j(args, name, work)
            if progs["m3j"] is None:
                report("COMPILE", name)
                continue
            # (server, client, run, arguments of the client, expected outputs)
            runs = [("m3j", "m3j", "m3j-m3j", [], want_s, want_c)]
            if args.cm3:
                runs += [(ps, pc, "%s-%s" % (ps, pc), [], want_s, want_c)
                         for ps, pc in [("cm3", "m3j"), ("m3j", "cm3"), ("cm3", "cm3")]]
            for d in sorted(e.glob("m3j-*")):
                if (d / "client.txt").exists():
                    runs.append(("m3j", "m3j", d.name, (d / "args").read_text().split(),
                                 text((d / "server.txt").read_bytes()),
                                 text((d / "client.txt").read_bytes())))
            for ps, pc, run, extra, ws, wc in runs:
                what = "%s (server %s, client %s%s)" % (
                    name, ps, pc, "" if run == "%s-%s" % (ps, pc) else ", " + run[4:])
                sout, cout = run_pair(args, progs[ps], progs[pc], work / name / ("run-" + run), extra)
                if sout is None:
                    report("FAIL", what, cout)
                elif sout != ws:
                    report("FAIL", what, "server " + first_diff(ws, sout))
                elif cout != wc:
                    report("FAIL", what, "client " + first_diff(wc, cout))
                else:
                    report("PASS", what)
        print(", ".join("%s %d" % (k, counts[k]) for k in sorted(counts)) + "  (%.1f s)" % (time.time() - t0))
        return 0 if set(counts) <= {"PASS", "RECORDED"} else 1
    finally:
        if not args.keep and not args.work:
            shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
