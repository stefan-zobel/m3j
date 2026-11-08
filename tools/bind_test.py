#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Test of m3jbind (m3j.jvm.Bind), the writer of the interfaces that bind
Java classes.

  python3 tools/bind_test.py [CASE]... [--java JAVA] [--jar JAR] [--work DIR]
                             [--keep] [--jvm-arg=ARG]...

The cases:

  jdk     binds classes of the JDK and runs tests/bind/jdk/Main.m3 on them
  demo    compiles the Java classes of tests/bind/demo/java, binds them with
          -cp, compares the interface of demo.Dot with
          tests/bind/demo/Dot.i3 and runs tests/bind/demo/Main.m3
  cycle   java.io.File and java.nio.file.Path refer to each other: the
          interfaces compile, also after one of them is bound again
  later   the supertype is found among the interfaces of the directory
  names   -name, -prefix, a nested class and the warning for a name that
          the library has
  broad   binds 48 classes of the JDK: everything that it writes compiles
  missing a class that a bound class needs is not on the class path
  errors  the exit status and the message of what m3jbind refuses

Without arguments all cases run. JAVA defaults to $JDK25/bin/java, else
java from the PATH; JAR to m3j.jar in the root of m3j.
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests" / "bind"

BROAD = """java.util.ArrayList java.util.List java.util.Collection java.util.Map
java.util.HashMap java.util.Map.Entry java.util.Iterator java.util.Optional
java.util.Comparator java.util.Random java.util.Arrays java.util.Collections
java.util.Objects java.lang.StringBuilder java.lang.String java.lang.Integer
java.lang.Long java.lang.Double java.lang.Character java.lang.Boolean
java.lang.Object java.lang.Throwable java.lang.Exception
java.lang.RuntimeException java.lang.Math java.lang.System java.lang.Thread
java.lang.Runnable java.lang.Enum java.io.File java.io.InputStream
java.io.PrintStream java.nio.file.Path java.nio.file.Paths
java.nio.file.Files java.nio.file.StandardOpenOption java.time.Duration
java.time.Instant java.time.LocalDate java.util.regex.Pattern
java.util.regex.Matcher java.util.stream.Stream java.util.stream.IntStream
java.math.BigInteger java.math.BigDecimal java.net.URI
java.util.concurrent.ConcurrentHashMap
java.util.concurrent.atomic.AtomicLong""".split()


class Failed(Exception):
    pass


class Env:
    def __init__(self, args, work):
        self.args = args
        self.work = work

    def java(self, cls, params, cwd, classes=None):
        c = subprocess.run([self.args.java] + self.args.jvm_arg + ["-Xss64m", "-cp", classes or self.args.jar, cls] + params,
                           cwd=cwd, capture_output=True, timeout=600)
        return (c.returncode, c.stdout.decode("latin-1").replace("\r\n", "\n"),
                c.stderr.decode("latin-1").replace("\r\n", "\n"))

    def bind(self, params, cwd):
        """Runs m3jbind, which must succeed; what it wrote to stderr."""
        rc, out, err = self.java("m3j.jvm.Bind", params, cwd)
        if rc != 0:
            raise Failed("m3jbind exit status %d: %s" % (rc, err.strip()[:600]))
        return err

    def compile(self, params, cwd):
        """Compiles and runs a program, which must succeed; its output."""
        rc, out, err = self.java("m3j.jvm.Compile", params, cwd)
        if rc != 0:
            raise Failed("exit status %d: %s" % (rc, (err or out).strip()[:600]))
        return out


def new_dir(env, name):
    d = env.work / name
    d.mkdir(parents=True)
    return d


def expect(got, want, what):
    if got != want:
        raise Failed("%s: got %r, expected %r" % (what, got, want))


def read(p):
    return p.read_text(encoding="latin-1").replace("\r\n", "\n")


def import_all(d):
    """Writes a program that imports every interface of the directory d."""
    names = sorted(f.stem for f in d.glob("*.i3"))
    (d / "Main.m3").write_text("MODULE Main;\n" + "".join("IMPORT %s;\n" % n for n in names)
                               + "BEGIN END Main.\n", encoding="ascii")


def case_jdk(env):
    d = new_dir(env, "jdk")
    env.bind(["java.util.ArrayList", "java.util.List", "java.lang.StringBuilder"], d)
    shutil.copy(TESTS / "jdk" / "Main.m3", d)
    out = env.compile(["-I", ".", "Main.m3", "-run"], d)
    expect(out, read(TESTS / "jdk" / "expected.txt"), "output")


def case_demo(env):
    d = new_dir(env, "demo")
    javac = str(Path(env.args.java).with_name("javac"))
    sources = [str(f) for f in sorted((TESTS / "demo" / "java" / "demo").glob("*.java"))]
    subprocess.run([javac, "-d", str(d / "classes")] + sources, check=True, capture_output=True)
    env.bind(["-cp", "classes", "-d", "m3", "demo.Dot", "demo.Dot$Kind", "demo.Shape"], d)
    expect(read(d / "m3" / "Dot.i3"), read(TESTS / "demo" / "Dot.i3"), "Dot.i3")
    shape = read(d / "m3" / "Shape.i3")
    if "New" in shape:
        raise Failed("the binding of an abstract class has a constructor")
    shutil.copy(TESTS / "demo" / "Main.m3", d)
    out = env.compile(["-cp", "classes", "-I", "m3", "Main.m3", "-run"], d)
    expect(out, read(TESTS / "demo" / "expected.txt"), "output")
    # a jar finds the classes by its manifest
    env.compile(["-cp", "classes", "-I", "m3", "Main.m3", "-d", "demo.jar"], d)
    rc, out, err = env.java("m3.Main", [], d, env.args.jar + os.pathsep + "demo.jar")
    expect((rc, out), (0, read(TESTS / "demo" / "expected.txt")), "output of the jar")


def case_cycle(env):
    d = new_dir(env, "cycle")
    params = ["-prefix", "J", "java.io.File", "java.nio.file.Path"]
    env.bind(params, d)
    import_all(d)
    env.compile(["-I", ".", "Main.m3"], d)
    if not re.search(r"^IMPORT .*JPath,", read(d / "JFile.i3"), re.M):
        raise Failed("JFile does not import JPath")
    if "JFile" in read(d / "JPath.i3"):
        raise Failed("JPath imports JFile, which imports it")
    # the interface that is in the directory counts too
    env.bind(["-prefix", "J", "java.nio.file.Path"], d)
    if "JFile" in read(d / "JPath.i3"):
        raise Failed("JPath imports JFile after a second run")
    env.compile(["-I", ".", "Main.m3"], d)


def case_later(env):
    d = new_dir(env, "later")
    env.bind(["java.util.List"], d)
    env.bind(["java.util.ArrayList"], d)
    if "TYPE T <: List.T;" not in read(d / "ArrayList.i3"):
        raise Failed("ArrayList.T is not a subtype of List.T")
    env.bind(["java.util.AbstractList"], d)
    if "TYPE T <: List.T;" not in read(d / "AbstractList.i3"):
        raise Failed("AbstractList.T is not a subtype of List.T")
    # a class and the interface that a new run gives another name
    env.bind(["-name", "java.util.List=Lst", "java.util.List"], d)
    import_all(d)
    env.compile(["-I", ".", "Main.m3"], d)


def case_names(env):
    d = new_dir(env, "names")
    err = env.bind(["-prefix", "P", "-name", "java.util.HashMap=Table", "java.util.Map.Entry", "java.util.HashMap",
                    "java.util.List"], d)
    expect(err, "", "stderr")
    expect(sorted(f.name for f in d.iterdir()), ["PList.i3", "PMap_Entry.i3", "Table.i3"], "files")
    if '<*EXTERNAL "java.util.Map$Entry":JAVA*> INTERFACE PMap_Entry;' not in read(d / "PMap_Entry.i3"):
        raise Failed("the name of the nested class")
    err = env.bind(["java.lang.Thread"], d)
    if "warning: the library has an interface Thread too" not in err:
        raise Failed("no warning: " + err)


def case_broad(env):
    d = new_dir(env, "broad")
    err = env.bind(["-prefix", "J"] + BROAD, d)
    expect(err, "", "stderr")
    expect(len(list(d.glob("*.i3"))), len(BROAD), "number of interfaces")
    import_all(d)
    env.compile(["-I", ".", "Main.m3"], d)


def case_missing(env):
    d = new_dir(env, "missing")
    javac = str(Path(env.args.java).with_name("javac"))
    sources = [str(f) for f in sorted((TESTS / "missing").glob("*.java"))]
    subprocess.run([javac, "-d", str(d / "classes")] + sources, check=True, capture_output=True)
    (d / "classes" / "late" / "Gone.class").unlink()
    rc, out, err = env.java("m3j.jvm.Bind", ["-cp", "classes", "late.Needs"], d)
    expect(rc, 1, "exit status of m3jbind")
    if "a class that a bound class needs is missing" not in err:
        raise Failed("stderr of m3jbind: %r" % err)
    expect(sorted(f.name for f in d.iterdir()), ["classes"], "files written")
    for name, text, want in [
        ("Ctor", "TYPE T <: REFANY;\nPROCEDURE New (): T;\n", "the constructors of Java class late.Needs need a class"),
        ("Meth", "PROCEDURE one (): INTEGER;\n", "the methods of Java class late.Needs need a class"),
    ]:
        c = new_dir(env, "missing-" + name)
        (c / "B.i3").write_text('<*EXTERNAL "late.Needs":JAVA*> INTERFACE B;\n%sEND B.\n' % text, encoding="ascii")
        (c / "Main.m3").write_text("MODULE Main;\nIMPORT B;\nBEGIN\nEND Main.\n", encoding="ascii")
        rc, out, err = env.java("m3j.jvm.Compile", ["-cp", str(d / "classes"), "Main.m3"], c)
        expect(rc, 1, "exit status for " + name)
        if want not in err:
            raise Failed("stderr for %s: %r lacks %r" % (name, err, want))


def case_errors(env):
    d = new_dir(env, "errors")
    for params, status, text in [
        ([], 2, "usage: m3j.jvm.Bind"),
        (["-d"], 2, "usage: m3j.jvm.Bind"),
        (["-x", "java.util.List"], 2, "usage: m3j.jvm.Bind"),
        (["no.such.Class"], 1, "Java class not found or not accessible: no.such.Class"),
        (["jdk.internal.misc.Unsafe"], 1, "not found or not accessible"),
        (["-name", "java.util.List=BEGIN", "java.util.List"], 1, "not the name of an interface: BEGIN"),
        (["-name", "java.util.List=X", "-name", "java.util.Map=X", "java.util.List", "java.util.Map"], 1,
         "two classes have the interface name X"),
    ]:
        rc, out, err = env.java("m3j.jvm.Bind", params, d)
        expect(rc, status, "exit status of %s" % params)
        if text not in err:
            raise Failed("stderr of %s: %r lacks %r" % (params, err, text))
    expect(list(d.iterdir()), [], "files written")


CASES = {
    "jdk": case_jdk, "demo": case_demo, "cycle": case_cycle, "later": case_later,
    "names": case_names, "broad": case_broad, "missing": case_missing, "errors": case_errors,
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cases", nargs="*")
    jdk = os.environ.get("JDK25")
    ap.add_argument("--java", default=str(Path(jdk) / "bin" / "java") if jdk else "java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--jvm-arg", action="append", default=[],
                    help="an option for every JVM that the test starts (repeatable; --jvm-arg=-X...)")
    args = ap.parse_args()
    for name in args.cases:
        if name not in CASES:
            sys.exit("bind_test: no such case: " + name)
    args.jar = str(Path(args.jar).resolve())
    work = Path(args.work).resolve() if args.work else Path(tempfile.mkdtemp(prefix="m3j-bind-"))
    work.mkdir(parents=True, exist_ok=True)
    failed = 0
    try:
        env = Env(args, work)
        for name in args.cases or CASES:
            try:
                CASES[name](env)
                print("PASS", name)
            except (Failed, subprocess.CalledProcessError, subprocess.TimeoutExpired) as e:
                print("FAIL %s: %s" % (name, e))
                failed += 1
    finally:
        if args.keep or args.work:
            print("work directory:", work)
        else:
            shutil.rmtree(work, ignore_errors=True)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
