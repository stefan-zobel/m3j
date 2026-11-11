#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Tests of m3build, the build system of m3j (m3build.jar, which build.sh
makes from tools/m3build).

Usage:
  python3 tools/build_test.py [quake] [exports [PKG...]] [cases [CASE...]]
                              [packages [PKG...]]
                              [--java JAVA] [--cm3 CM3DIR] [--src CM3SRC]
                              [--work DIR] [--keep] [--timeout SECONDS] [-v]

Without a part, all four run.

quake: the tests of cm3's quake interpreter (tests/quake/src/m3makefile,
from m3-sys/m3quake/test) run as the m3makefile of a package, which m3build
builds; the output must be that of cm3 -build, recorded on AMD64_NT in
tests/quake/expected/cm3-AMD64_NT.txt, but for the lines that depend on
the run (the name of the host, the date, the directory of the package and
the times of files) and the name of the build directory (JVM, AMD64_NT);
the test d001 expects that the directory of the work (--work) does not
contain "test", as in the record of cm3. The test e002 runs java --version
(of JAVA, put on the PATH) instead of cm3 -version. The tests are written
for POSIX, and cm3 on Windows fails some of them (pathnames with a
backslash); on a POSIX system the output must be
tests/quake/expected/m3j-POSIX.txt, that of m3build on Linux, checked by
hand against the record of cm3.
The tests run programs of a POSIX shell (Git's on Windows) from the PATH,
as with cm3.

exports: the m3makefiles of cm3's packages, against the .M3EXPORTS that
cm3 installed for them. For each package of an installation of cm3
(CM3DIR/pkg/PKG/AMD64_NT/.M3EXPORTS; by default cm3 in the root of m3j)
whose sources are in the sources of cm3 (CM3SRC, by default cm3-src),
m3build -exports runs on a copy of its sources, with the packages of that
installation (-cm3 CM3DIR), and the units, templates and imports of its
.M3EXPORTS must be those of cm3's: the names of the build directories
(JVM, AMD64_NT) are not compared, nor what only cm3 has (C sources and
headers, linker data), nor the order of the imports, nor the import of
the package m3j, which the m3core of m3j imports. tests/build/exports.list names the packages that
differ for a reason that it gives (KNOWN).

cases: the packages of tests/build (the cases of tests/build/cases.list),
built by m3build in a copy: each line is a case, its directory, then the
commands, separated by ";": a command of m3build (m3build ARGS, in the
directory of a package of the case), a program that m3build built (run
PKG PROGRAM ARGS), a file that must exist or not (exists PATH, absent
PATH, relative to the copy), or Java classes for a package (javac DIR
FILE..., which compiles the files to DIR, and jar FILE DIR, which packs
DIR; the paths are relative to the copy). The output of the commands, with the
directory of the copy written as <work>, must be tests/build/expected/
CASE.txt, written by hand on Windows: on a POSIX system a backslash
between the parts of a pathname in it is read as '/'.

packages: packages of cm3 built by m3build from a copy of their sources
with their own m3makefiles (PACKAGES in this file): libraries, which
m3build installs (-ship) in an installation of the test, and programs that
use them, whose output must be that of the same program built by cm3, as
diff_test recorded it (tests/diff/expected; APPROX: numbers differ within
the tolerance of the test), or that only build (m3bundle, and stubgen on
the libraries m3tk and m3tk-misc; stubgen from tests/stubgen, the safe
copy of stubgen_test).

JAVA defaults to $JDK25/bin/java, else java from the PATH.
"""

import argparse
import os
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests"
M3BUILD = ROOT / "m3build.jar"
JAVA_CM3 = ('q_exec( \\"cm3 -version > cm3.version\\" )', 'q_exec( \\"java --version > cm3.version\\" )')
COMPARED = re.compile(r'^(_map_add_(?:interface|module|generic_interface|generic_module)'
                      r'|_define_lib|_define_pgm|import_version|_import_template|export_interface)\(')


def m3build(args, cwd, *params, env=None):
    """Runs m3build in cwd: (exit code, output)."""
    cmd = [args.java, "-jar", str(M3BUILD)] + list(params)
    try:
        c = subprocess.run(cmd, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                           timeout=args.timeout, env=env)
    except subprocess.TimeoutExpired:
        return None, "timeout\n"
    out = c.stdout.decode("latin-1").replace("\r\n", "\n")
    return c.returncode, out


def report(status, what, detail=""):
    print("%-12s %s%s" % (status, what, ("  " + detail) if detail else ""), flush=True)


# ------------------------------------------------------------------ quake

def quake(args, work, counts):
    pkg = work / "quake"
    if pkg.exists():
        shutil.rmtree(pkg)
    (pkg / "src").mkdir(parents=True)
    # the test e002 runs a program of the PATH that writes its version:
    # java (of the JVM of the test, on the PATH), not cm3
    mk = (TESTS / "quake" / "src" / "m3makefile").read_bytes().decode("latin-1")
    (pkg / "src" / "m3makefile").write_bytes(mk.replace(JAVA_CM3[0], JAVA_CM3[1]).encode("latin-1"))
    env = dict(os.environ)
    env["PATH"] = str(Path(args.java).parent) + os.pathsep + env.get("PATH", "")
    code, out = m3build(args, pkg / "src", env=env)
    record = "cm3-AMD64_NT.txt" if os.name == "nt" else "m3j-POSIX.txt"
    exp = (TESTS / "quake" / "expected" / record).read_bytes().decode("latin-1")
    exp = exp.replace(JAVA_CM3[0].replace('\\"', '"'), JAVA_CM3[1].replace('\\"', '"'))
    got, want = normalize_quake(out, pkg), normalize_quake(exp, None)
    status = "PASS" if code == 0 and got == want else "FAIL"
    detail = "" if status == "PASS" else first_diff(want, got)
    counts[status] = counts.get(status, 0) + 1
    report(status, "quake", detail)


def normalize_quake(text, pkg):
    lines = []
    for l in text.replace("\r\n", "\n").split("\n"):
        l = l.replace("AMD64_NT", "JVM")
        if re.match(r"(hostname|date|datetime|datestamp) +=", l):
            l = l.split("=")[0] + "= <run>"
        # a line of ls -l (owner, group, size and time depend on the run)
        l = re.sub(r"^-r[-w][-x]\S+ +\d+ .+? \w{3} +\d+ \d\d:\d\d (\S+)$",
                   lambda m: "<file> " + m.group(1), l)
        l = re.sub(r"^out = total \d+$", "out = total <blocks>", l)
        l = re.sub(r"^base = .*$", "base = <package>", l)
        # the directory of the package, as the tests d002 and d004 show it
        l = re.sub(r'^result:   "(/|[A-Za-z]:\\\\).*$', 'result:   <package>', l)
        lines.append(l)
    return "\n".join(lines)


def first_diff(want, got):
    w, g = want.split("\n"), got.split("\n")
    for i in range(max(len(w), len(g))):
        a = w[i] if i < len(w) else "<end>"
        b = g[i] if i < len(g) else "<end>"
        if a != b:
            return "line %d: expected %r, got %r" % (i + 1, a[:100], b[:100])
    return ""


# ------------------------------------------------------------------ exports

def sources(src):
    """{package: its directory in the sources of cm3}."""
    found = {}
    for mk in src.rglob("src/m3makefile"):
        d = mk.parent.parent
        if "tests" in d.relative_to(src).parts or "test" in d.relative_to(src).parts:
            continue
        found.setdefault(d.name, []).append(d)
    return {p: ds[0] for p, ds in found.items() if len(ds) == 1}


def known(name):
    f = TESTS / "build" / name
    res = {}
    if f.exists():
        for l in f.read_text(encoding="latin-1").splitlines():
            if l.strip() and not l.startswith("#"):
                p, why = l.split(None, 1)
                res[p] = why
    return res


def exports_lines(text):
    """The compared lines of a .M3EXPORTS: the imports first, sorted (their
    order is that of a hash table), without the package m3j, then the
    others in their order."""
    imports, res = set(), []
    for l in text.replace("\r\n", "\n").split("\n"):
        if COMPARED.match(l):
            l = l.replace('"AMD64_NT"', '"<build>"').replace('"JVM"', '"<build>"')
            if not l.startswith("import_version("):
                res.append(l)
            elif not l.startswith('import_version("m3j"'):
                imports.add(l)
    return sorted(imports) + res


def exports(args, work, names, counts):
    cm3, src = Path(args.cm3), Path(args.src)
    srcs = sources(src)
    pkgs = sorted(p.name for p in (cm3 / "pkg").iterdir()
                  if (p / "AMD64_NT" / ".M3EXPORTS").exists())
    if names:
        pkgs = [p for p in pkgs if p in names]
    skip = known("exports.list")
    # the quake files of cm3's scripts, which some m3makefiles include
    # (cm3's version.quake)
    (work / "exports" / "scripts").mkdir(parents=True, exist_ok=True)
    for q in (src / "scripts").glob("*.quake"):
        shutil.copyfile(q, work / "exports" / "scripts" / q.name)
    for p in pkgs:
        if p not in srcs:
            counts["NO-SOURCES"] = counts.get("NO-SOURCES", 0) + 1
            report("NO-SOURCES", "exports " + p)
            continue
        rel = srcs[p].relative_to(src)
        copy = work / "exports" / rel
        if copy.exists():
            shutil.rmtree(copy)
        shutil.copytree(srcs[p], copy, ignore=shutil.ignore_patterns("AMD64_NT", "JVM", "*.o", "*.obj"))
        code, out = m3build(args, copy, "-exports", "-cm3", str(cm3))
        mine = copy / "JVM" / ".M3EXPORTS"
        if code != 0 or not mine.exists():
            status, detail = "FAIL", "m3build: " + " | ".join(out.strip().split("\n")[-4:])
        else:
            want = exports_lines((cm3 / "pkg" / p / "AMD64_NT" / ".M3EXPORTS").read_text(encoding="latin-1"))
            got = exports_lines(mine.read_text(encoding="latin-1"))
            if want == got:
                status, detail = "PASS", ""
            else:
                status, detail = "FAIL", first_diff("\n".join(want), "\n".join(got))
        if status == "FAIL" and p in skip:
            status, detail = "KNOWN", skip[p]
        counts[status] = counts.get(status, 0) + 1
        report(status, "exports " + p, detail)
        if args.verbose and status != "PASS":
            print(out)


# ------------------------------------------------------------------ cases

def program(copy, path):
    """The command that runs the program of m3build at path (relative to
    the copy, without extension): its script for the system."""
    prog = copy / path
    if os.name == "nt":
        return ["cmd", "/c", str(prog) + ".cmd"]
    return ["sh", str(prog)]


def sort_elements(got):
    """The output with the elements of each m3bundle command sorted: they come
    from a table of quake, whose order depends on the numbers of identifiers."""
    lines = []
    for line in got.split("\n"):
        parts = line.split(" -element ")
        lines.append(" -element ".join(parts[:1] + sorted(parts[1:])))
    return "\n".join(lines)


def text(b):
    return b.decode("latin-1").replace("\r\n", "\n").rstrip("\n")


def pair(args, copy, path, out):
    """Runs the program at path as a server ("PROGRAM server", which prints
    "port N" first and serves until the end of its standard input) and as
    its client ("PROGRAM client N")."""
    cmd = program(copy, path)
    s = subprocess.Popen(cmd + ["server"], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                         stderr=subprocess.STDOUT)
    first = s.stdout.readline().decode("latin-1").strip()
    if not first.startswith("port "):
        s.kill()
        out.append("server: " + first)
        return
    c = subprocess.run(cmd + ["client", first[5:]], capture_output=True, timeout=args.timeout)
    rest, _ = s.communicate(timeout=args.timeout)
    out.append("server:")
    out.append(text(rest))
    out.append("client:")
    out.append(text(c.stdout + c.stderr))
    out.append("exit %d" % c.returncode)


def cases(args, work, names, counts):
    lines = [l for l in (TESTS / "build" / "cases.list").read_text(encoding="latin-1").splitlines()
             if l.strip() and not l.startswith("#")]
    if names:
        unknown = set(names) - {l.split()[0] for l in lines}
        if unknown:
            sys.exit("build_test: no such case: " + " ".join(sorted(unknown)))
    for line in lines:
        name, d, rest = line.split(None, 2)
        if names and name not in names:
            continue
        copy = work / "cases" / name
        if copy.exists():
            shutil.rmtree(copy)
        shutil.copytree(TESTS / "build" / d, copy)
        out = []
        for cmd in [c.strip() for c in rest.split(";") if c.strip()]:
            words = [w.replace("<work>", str(copy)) for w in shlex.split(cmd)]
            out.append("$ " + cmd)
            if words[0] == "m3build":
                code, t = m3build(args, copy / words[1], *words[2:])
                out.append(t.rstrip("\n"))
                out.append("exit %s" % code)
            elif words[0] == "run":
                c = subprocess.run(program(copy, words[1]) + words[2:], capture_output=True,
                                   timeout=args.timeout)
                out.append(text(c.stdout + c.stderr))
                out.append("exit %d" % c.returncode)
            elif words[0] == "javac":
                subprocess.run([str(Path(args.java).with_name("javac")), "-d", str(copy / words[1])]
                               + [str(copy / w) for w in words[2:]], check=True, capture_output=True)
            elif words[0] == "jar":
                (copy / words[1]).parent.mkdir(parents=True, exist_ok=True)
                subprocess.run([str(Path(args.java).with_name("jar")), "--create", "--file", str(copy / words[1]),
                                "-C", str(copy / words[2]), "."], check=True, capture_output=True)
            elif words[0] == "pair":
                pair(args, copy, words[1], out)
            elif words[0] == "touch":
                f = copy / words[1]
                f.write_bytes(f.read_bytes())
                t = max(x.stat().st_mtime for x in copy.rglob("*") if x.is_file()) + 2
                os.utime(f, (t, t))
            elif words[0] in ("exists", "absent"):
                there = (copy / words[1]).exists()
                out.append("yes" if there == (words[0] == "exists") else "NOT AS EXPECTED")
            else:
                sys.exit("build_test: unknown command in case %s: %s" % (name, cmd))
        got = "\n".join(out)
        for c in (str(copy), str(copy).replace("\\", "/")):
            got = got.replace(c, "<work>")
        got = re.sub(r"<work>\\", "<work>/", got)
        got = got.replace(str(ROOT), "<m3j>").replace(str(ROOT).replace("\\", "/"), "<m3j>")
        # the java that runs the programs of the templates (M3J_JAVA)
        got = re.sub(r'"[^"\n]*[\\/]bin[\\/]java(\.exe)?"', '"<java>"', got)
        got = sort_elements(got)
        exp_file = TESTS / "build" / "expected" / (name + ".txt")
        if args.record_cases:
            exp_file.parent.mkdir(exist_ok=True)
            exp_file.write_bytes((got + "\n").encode("latin-1"))
        want = exp_file.read_bytes().decode("latin-1").replace("\r\n", "\n").rstrip("\n") \
            if exp_file.exists() else None
        if want is not None and os.name != "nt":
            # recorded on Windows: '\' between the parts of a pathname
            want = re.sub(r"(?<=[\w.>])\\(?=[\w.])", "/", want)
        if want is None:
            status, detail = "NO-EXPECTED", ""
        elif want == got:
            status, detail = "PASS", ""
        else:
            status, detail = "FAIL", first_diff(want, got)
        counts[status] = counts.get(status, 0) + 1
        report(status, "case " + name, detail)
        if args.verbose and status != "PASS":
            print(got)


# ------------------------------------------------------------------ packages

# The packages of cm3 that m3build builds in this order, with their own
# m3makefiles: libraries, which are installed (ship), and programs, which
# run and whose output must be what diff_test recorded of the same program
# built by cm3 (run, with the name of the test in tests/diff/expected and
# the tolerance of numbers, as approx= of diff_test), or which only build
# (build).
PACKAGES = [
    ("m3-libs/tempfiles", "ship", None, None),
    ("m3-libs/arithmetic", "ship", None, None),
    ("m3-libs/arithmetic/test", "run", "lib/arithmetic", 1e-5),
    ("m3-libs/bitvector", "ship", None, None),
    ("m3-libs/bitvector/test", "run", "lib/bitvector", None),
    ("m3-libs/sortedtableextras", "ship", None, None),
    ("m3-libs/sortedtableextras/tests/rbtest", "run", "lib/sortedtableextras/rbtest", None),
    ("m3-tools/m3bundle", "build", None, None),
    ("m3-libs/m3tk-misc", "ship", None, None),
    ("m3-tools/m3tk", "ship", None, None),
    # stubgen of cm3 has an UNSAFE module: the safe copy of stubgen_test
    ("tests/stubgen/stubgen", "build", None, None),
]


def packages(args, work, names, counts):
    sys.path.insert(0, str(ROOT / "tools"))
    import diff_test
    src = Path(args.src)
    root = work / "packages-root"
    for rel, what, test, tol in PACKAGES:
        if names and Path(rel).name not in names and rel not in names:
            continue
        copy = work / "packages" / rel
        if copy.exists():
            shutil.rmtree(copy)
        base = ROOT if rel.startswith("tests/") else src
        shutil.copytree(base / rel, copy, ignore=shutil.ignore_patterns("AMD64_NT", "JVM", "*.o", "*.obj"))
        what_txt = "package %s (%s)" % (rel, what)
        code, out = m3build(args, copy, "-root", str(root))
        if code == 0 and what == "ship":
            code, out = m3build(args, copy, "-ship", "-root", str(root))
        if code != 0:
            status, detail = "FAIL", "m3build: " + " | ".join(out.strip().split("\n")[-3:])
        elif what == "run":
            prog = sorted((copy / "JVM").glob("*.jar"))[0].stem
            c = subprocess.run(program(copy, "JVM/" + prog), capture_output=True, timeout=args.timeout)
            exp = __import__("json").loads((TESTS / "diff" / "expected" / (test + ".json")).read_text())["result"]
            got_out = c.stdout.decode("latin-1").replace("\r\n", "\n")
            if c.returncode != exp.get("status") or c.stderr.decode("latin-1").strip() != exp.get("stderr", "").strip():
                status, detail = "FAIL", "exit %d, stderr %r" % (c.returncode, c.stderr[:200])
            elif got_out == exp["stdout"]:
                status, detail = "PASS", ""
            elif tol is not None and diff_test.approx_equal(exp["stdout"], got_out, tol=tol):
                status, detail = "APPROX", ""
            else:
                status, detail = "FAIL", "stdout " + first_diff(exp["stdout"], got_out)
        else:
            status, detail = "PASS", ""
        counts[status] = counts.get(status, 0) + 1
        report(status, what_txt, detail)
        if args.verbose and status not in ("PASS", "APPROX"):
            print(out)


def main():
    ap = argparse.ArgumentParser(description="tests of m3build")
    ap.add_argument("parts", nargs="*")
    jdk = os.environ.get("JDK25")
    ap.add_argument("--java", default=str(Path(jdk) / "bin" / "java") if jdk else "java")
    ap.add_argument("--cm3", default=str(ROOT / "cm3"))
    ap.add_argument("--src", default=str(ROOT / "cm3-src"))
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--timeout", type=float, default=300)
    ap.add_argument("--record-cases", action="store_true", help=argparse.SUPPRESS)
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()

    parts, cur = {}, None
    for w in args.parts:
        if w in ("quake", "exports", "cases", "packages"):
            cur = w
            parts[cur] = []
        elif cur is None:
            sys.exit("build_test: unknown part " + w)
        else:
            parts[cur].append(w)
    if not parts:
        parts = {"quake": [], "exports": [], "cases": [], "packages": []}

    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="m3build-"))
    work.mkdir(parents=True, exist_ok=True)
    counts = {}
    try:
        if "quake" in parts:
            quake(args, work, counts)
        if "exports" in parts:
            exports(args, work, parts["exports"], counts)
        if "cases" in parts:
            cases(args, work, parts["cases"], counts)
        if "packages" in parts:
            packages(args, work, parts["packages"], counts)
    finally:
        if not args.keep and not args.work:
            shutil.rmtree(work, ignore_errors=True)
    print(", ".join("%s %d" % (k, v) for k, v in sorted(counts.items())))
    sys.exit(0 if set(counts) <= {"PASS", "KNOWN", "APPROX"} else 1)


if __name__ == "__main__":
    main()
