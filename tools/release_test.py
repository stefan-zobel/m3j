#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Test of a release of m3j (dist.sh): unpacks the archive in a new
directory and uses it as someone who downloaded it would, with the start
scripts in its bin (m3j.cmd and m3build.cmd on Windows, else m3j and
m3build).

  python3 tools/release_test.py ARCHIVE [--java JAVA] [--work DIR] [--keep]
                                [--no-feedreader]

ARCHIVE is m3j-VERSION.tar.gz or m3j-VERSION.zip. The cases:

  files       the files of a release are there (the jars, m3j.cfg, bin,
              lib, docs, examples, licenses, the extension for Visual
              Studio Code), m3j.aot is not; from a tar.gz on POSIX the
              scripts are executable
  version     m3j -version names the version of the file VERSION
  run         a program compiled in memory and run (m3j Main.m3 -run)
  jar         the same program compiled into a jar, run with the runtime
  package     a package built by m3build that imports regexp of lib (with
              its binding of a Java class), and its start script
  eol         m3bundle writes the lines of the system it runs on (CR LF on
              Windows, LF elsewhere), wherever the release was built
  feedreader  examples/feedreader built by m3build (with the driver of
              SQLite from the local repository of Maven, checked by its
              fetch-driver.sh, or fetched by it; needs sh) and its usage

JAVA (default $JDK25/bin/java, else java from the PATH) is the java of the
start scripts, as JAVA_HOME.
"""

import argparse
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
import zipfile
from pathlib import Path

WINDOWS = os.name == "nt"
SQLITE = "sqlite-jdbc-3.53.4.0.jar"
JARS = ["m3j.jar", "m3j-runtime.jar", "m3j-ssh.jar", "m3build.jar", "m3bundle.jar", "stubgen.jar",
        "m3_lang_server.jar"]

HELLO = """MODULE Main;

IMPORT IO;

BEGIN
  IO.Put ("Hello, world\\n");
END Main.
"""

DEMO = """MODULE Main;

IMPORT IO, Regexp;

<*FATAL Regexp.Error, Regexp.TooComplex*>

BEGIN
  IO.Put (Regexp.Replace (Regexp.Compile ("[0-9]+"), "a1b22c333", "#") & "\\n");
END Main.
"""

DEMO_MAKEFILE = """import ("libm3")
import ("regexp")
implementation ("Main")
program ("demo")
"""


class Failed(Exception):
    pass


def check(what, got, want):
    if got != want:
        raise Failed("%s: got %r, want %r" % (what, got, want))


class Release:
    def __init__(self, args, root):
        self.args = args
        self.root = root
        self.env = dict(os.environ)
        self.env["JAVA_HOME"] = str(Path(args.java).resolve().parent.parent)
        self.env["PATH"] = str(root / "bin") + os.pathsep + self.env.get("PATH", "")

    def script(self, name):
        if WINDOWS:
            return [str(self.root / "bin" / (name + ".cmd"))]
        return ["sh", str(self.root / "bin" / name)]

    def run(self, cmd, cwd, want_code=0):
        c = subprocess.run(cmd, cwd=cwd, env=self.env, capture_output=True, timeout=self.args.timeout)
        out = c.stdout.decode("latin-1").replace("\r\n", "\n")
        err = c.stderr.decode("latin-1").replace("\r\n", "\n")
        if want_code is not None and c.returncode != want_code:
            raise Failed("%s: exit %d, want %d\n%s%s" % (" ".join(cmd), c.returncode, want_code, out, err[-2000:]))
        return out, err

    def program(self, pkg, name):
        """The command of a program that m3build built in the package pkg."""
        if WINDOWS:
            return [str(pkg / "JVM" / (name + ".cmd"))]
        return ["sh", str(pkg / "JVM" / name)]


def unpack(archive, work):
    work.mkdir(parents=True)
    if archive.name.endswith(".tar.gz"):
        with tarfile.open(archive) as t:
            t.extractall(work, filter="data")
    elif archive.suffix == ".zip":
        with zipfile.ZipFile(archive) as z:
            z.extractall(work)
    else:
        sys.exit("release_test: not a .tar.gz or .zip: %s" % archive)
    dirs = [d for d in work.iterdir() if d.is_dir()]
    if len(dirs) != 1 or not dirs[0].name.startswith("m3j-"):
        sys.exit("release_test: the archive does not hold one directory m3j-VERSION")
    return dirs[0]


def case_files(r, work, archive):
    for f in JARS + ["m3j.cfg", "VERSION", "LICENSE", "README.md", "bin/m3j", "bin/m3build", "bin/m3j.cmd",
                     "bin/m3build.cmd", "lib/libm3/JVM/.M3EXPORTS", "lib/regexp/JVM/.M3EXPORTS",
                     "docs/USING.md", "licenses/COPYRIGHT-CMASS", "examples/feedreader/fetch-driver.sh",
                     "tools/langserver/vscode/package.json"]:
        if not (r.root / f).is_file():
            raise Failed("missing: " + f)
    if (r.root / "m3j.aot").exists():
        raise Failed("m3j.aot is in the release")
    check("the version of the directory", r.root.name, "m3j-" + (r.root / "VERSION").read_text().strip())
    if not WINDOWS and archive.name.endswith(".tar.gz"):
        for f in ("bin/m3j", "bin/m3build", "examples/feedreader/fetch-driver.sh"):
            if not os.access(r.root / f, os.X_OK):
                raise Failed("not executable: " + f)
    if (r.root / "bin" / "m3j.cmd").read_bytes().count(b"\r\n") == 0:
        raise Failed("bin/m3j.cmd has no CR LF")


def case_version(r, work, archive):
    out, _ = r.run(r.script("m3j") + ["-version"], work)
    check("m3j -version", out, "m3j version %s\n" % (r.root / "VERSION").read_text().strip())


def hello_dir(work):
    d = work / "hello"
    d.mkdir(exist_ok=True)
    (d / "Main.m3").write_text(HELLO)
    return d


def case_run(r, work, archive):
    out, _ = r.run(r.script("m3j") + ["Main.m3", "-run"], hello_dir(work))
    check("m3j Main.m3 -run", out, "Hello, world\n")


def case_jar(r, work, archive):
    d = hello_dir(work)
    r.run(r.script("m3j") + ["Main.m3", "-d", "hello.jar"], d)
    out, _ = r.run([r.args.java, "-cp", str(r.root / "m3j-runtime.jar") + os.pathsep + "hello.jar", "m3.Main"], d)
    check("the jar", out, "Hello, world\n")


def case_package(r, work, archive):
    pkg = work / "demo"
    (pkg / "src").mkdir(parents=True)
    (pkg / "src" / "m3makefile").write_text(DEMO_MAKEFILE)
    (pkg / "src" / "Main.m3").write_text(DEMO)
    r.run(r.script("m3build"), pkg)
    out, _ = r.run(r.program(pkg, "demo"), work)
    check("the program of the package", out, "a#b#c#\n")


def case_eol(r, work, archive):
    d = work / "eol"
    d.mkdir()
    (d / "a.txt").write_text("a\n")
    r.run([r.args.java, "-jar", str(r.root / "m3bundle.jar"), "-name", "B", "-element", "a", "a.txt"], d)
    data = (d / "B.i3").read_bytes()
    crlf = data.count(b"\r\n")
    if WINDOWS and crlf != data.count(b"\n"):
        raise Failed("B.i3: lines without CR LF on Windows")
    if not WINDOWS and crlf:
        raise Failed("B.i3: CR LF on POSIX")


def case_feedreader(r, work, archive):
    if r.args.no_feedreader:
        return "skipped (--no-feedreader)"
    sh = shutil.which("sh")
    if not sh:
        return "skipped: no sh for fetch-driver.sh"
    fr = work / "feedreader"
    shutil.copytree(r.root / "examples" / "feedreader", fr)
    m2 = Path.home() / ".m2" / "repository" / "org" / "xerial" / "sqlite-jdbc" / "3.53.4.0" / SQLITE
    if m2.is_file():
        (fr / "lib").mkdir(exist_ok=True)
        shutil.copy(m2, fr / "lib" / SQLITE)
    r.run([sh, "fetch-driver.sh"], fr)
    r.run(r.script("m3build"), fr)
    out, err = r.run(r.program(fr, "feedreader"), work, want_code=2)
    if "usage: feedreader" not in out + err:
        raise Failed("feedreader without arguments: %r" % (out + err)[:300])


CASES = [("files", case_files), ("version", case_version), ("run", case_run), ("jar", case_jar),
         ("package", case_package), ("eol", case_eol), ("feedreader", case_feedreader)]


def main():
    ap = argparse.ArgumentParser(description="test of a release of m3j")
    ap.add_argument("archive")
    jdk = os.environ.get("JDK25")
    ap.add_argument("--java", default=str(Path(jdk) / "bin" / "java") if jdk else shutil.which("java") or "java")
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--timeout", type=float, default=600)
    ap.add_argument("--no-feedreader", action="store_true")
    args = ap.parse_args()
    if WINDOWS and not args.java.endswith(".exe") and Path(args.java + ".exe").exists():
        args.java += ".exe"
    archive = Path(args.archive).resolve()
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="m3j-release-"))
    work.mkdir(parents=True, exist_ok=True)
    failed = 0
    try:
        r = Release(args, unpack(archive, work / "unpacked"))
        for name, case in CASES:
            try:
                note = case(r, work, archive)
                print("PASS      %s%s" % (name, "  " + note if note else ""), flush=True)
            except (Failed, subprocess.TimeoutExpired) as e:
                failed += 1
                print("FAIL      %s  %s" % (name, e), flush=True)
    finally:
        if args.keep or args.work:
            print("work directory:", work)
        else:
            shutil.rmtree(work, ignore_errors=True)
    print("failed %d" % failed)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
