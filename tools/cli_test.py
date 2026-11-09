#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Test of the command line of the compiler (m3j.jvm.Compile): the usage
message, -version (the version of the file VERSION, from the manifest of
the jar), -run (compiles in memory and runs the program with the arguments
that follow), an option of the front end (-NoChecks), the exit status after
an error and for an UNSAFE module, the restriction on WITH and a packed
element at an index known only at run time, the search for the module of an
interface in NAMEWin32.m3 by its EXPORTS, and in the modules of a source
package (-pkg) by their EXPORTS, without a warning for an interface of
variables alone, and with the module of the name of an interface the other
modules of its directory that export it too (not those of another
directory), and the errors of bindings of Java classes. The programs are
in tests/cli; the expected results are below, checked by hand.

  python3 tools/cli_test.py [--java JAVA] [--jar JAR] [--keep] [--jvm-arg=ARG]...
"""

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "tests" / "cli"

# name, directory below tests/cli, arguments of the compiler, exit status
# (None: not 0), stdout (None: not checked), a text that stderr must contain
# (None: stderr must be empty)
CASES = [
    ("usage", ".", [], 2, "", "usage: m3j.jvm.Compile"),
    ("version", ".", ["-version"], 0,
     "m3j version " + (ROOT / "VERSION").read_text(encoding="ascii").strip() + "\n", None),
    ("run", ".", ["Args.m3", "-run", "x", "y z", ""], 0, "3 [x] [y z] []\n", None),
    ("checks", ".", ["Checks.m3", "-run"], None, "",
     'value was out of range.\n***    file "Checks.m3", line 19'),
    ("nochecks", ".", ["-NoChecks", "Checks.m3", "-run"], 0, "s 8\nresult 0\n",
     '"Checks.m3", line 15: warning: function may not return a value'),
    ("error", ".", ["Bad.m3"], 1, None, '"Bad.m3", line 5'),
    ("unsafe", ".", ["Unsafe.m3"], 1, None, "UNSAFE modules are not supported"),
    ("packed-with", ".", ["PackedWith.m3"], 1, None,
     '"PackedWith.m3", line 12: CM3 restriction: WITH cannot bind a name to a packed element'),
    ("exports", "exports", ["Main.m3", "-run"], 0, "FooWin32 Bar\n", None),
    ("package", "package", ["-pkg", "pkg", "Main.m3", "-run"], 0, "Impl / 7\n", None),
    ("split", "split", ["-pkg", "pkg", "Main.m3", "-run"], 0, "A B\n", None),
    ("java-class", "java/class", ["Main.m3"], 1, None, "line 1: Java class not found: no.such.Klass"),
    ("java-method", "java/method", ["Main.m3"], 1, None, "line 4: no public Java method java.lang.Math.nope"),
    ("java-ambiguous", "java/ambiguous", ["Main.m3"], 1, None,
     "line 6: ambiguous Java method java.lang.Math.max: give the descriptor, one of max(JJ)J, max(II)I"),
    ("java-mismatch", "java/mismatch", ["Main.m3"], 1, None,
     "line 4: no overload of Java method java.lang.Math.max fits this declaration"),
    ("java-callback", "java/callback", ["Main.m3"], 1, None,
     "line 6: no overload of Java method java.util.Collections.sort fits this declaration"),
    ("java-final", "java/final", ["Main.m3"], 1, None,
     '"Main.m3", line 6: cannot assign to the final Java field MAX_VALUE'),
    ("java-module", "java/module", ["Main.m3"], 1, None, '"Main.m3", line 3: External declarations only allowed in interfaces'),
    ("java-var", "java/var", ["Main.m3"], 1, None, "line 4: a parameter of a Java procedure is VAR or READONLY only if it is an array"),
    ("java-field", "java/field", ["Main.m3"], 1, None,
     "line 3: the type of Java field java.lang.Integer.MAX_VALUE is int: declare it as Java.Int"),
    ("java-abstract", "java/abstract", ["Main.m3"], 1, None,
     "line 5: Java class java.util.AbstractList is abstract"),
    ("java-value", "java/value", ["Main.m3", "-run"], 0, "", None),
    ("java-as-method", "java/method2", ["Main.m3", "-run"], 0, "", None),
    ("java-array-element", "java/array-element", ["Main.m3"], 1, None,
     "line 4: no overload of Java method java.util.Arrays.sort fits this declaration: a Java int[] is an ARRAY OF Java.Int"),
    ("java-array-nested", "java/array-nested", ["Main.m3"], 1, None,
     "line 4: no overload of Java method java.util.Arrays.deepToString fits this declaration"),
    ("java-sub-ctor", "java/sub-ctor", ["Main.m3"], 1, None,
     "line 5: Java class java.io.PrintStream has no constructor without parameters to extend it with"),
    ("java-sub-final", "java/sub-final", ["Main.m3"], 1, None,
     "line 5: Java class java.lang.Integer cannot be extended: it is final or sealed"),
    ("java-sub-sig", "java/sub-sig", ["Main.m3"], 1, None,
     "line 6: method run does not fit Java method of java.lang.Runnable: run()V"),
    ("java-sub-ambiguous", "java/sub-ambiguous", ["Main.m3"], 1, None,
     "line 6: method write fits more than one Java method of java.io.Writer"),
    ("java-sub-new", "java/sub-new", ["Main.m3"], 1, None,
     "line 10: NEW: Java method run()V of java.lang.Runnable is not implemented"),
    ("java-new-binding", "java/new-binding", ["Main.m3"], 1, None,
     "line 8: NEW of a Java type: call a constructor of the binding"),
    ("java-field-type", "java/field-type", ["Main.m3"], 1, None,
     "line 5: the type of Java field java.awt.Point.x is int: declare it as Java.Int"),
    ("java-field-missing", "java/field-missing", ["Main.m3"], 1, None,
     "line 5: no public Java field java.awt.Point.z"),
    ("java-field-method", "java/field-method", ["Main.m3"], 1, None,
     "line 5: the type of a Java binding lists fields only"),
    ("java-field-default", "java/field-default", ["Main.m3"], 1, None,
     "line 5: a field of a Java binding has no default value: x"),
    ("java-field-static", "java/field-static", ["Main.m3"], 1, None,
     "line 5: Java field java.lang.Integer.MAX_VALUE is static"),
]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--jvm-arg", action="append", default=[],
                    help="an option for every JVM that the test starts (repeatable; --jvm-arg=-X...)")
    args = ap.parse_args()
    if not args.java:
        jdk = os.environ.get("JDK25")
        args.java = str(Path(jdk) / "bin" / "java") if jdk else "java"
    work = Path(tempfile.mkdtemp(prefix="m3j-cli-"))
    failed = 0
    try:
        shutil.copytree(SOURCES, work / "cli")
        for name, sub, cargs, status, out, err in CASES:
            c = subprocess.run([args.java] + args.jvm_arg + ["-Xss64m", "-cp", args.jar, "m3j.jvm.Compile"] + cargs,
                               cwd=work / "cli" / sub, capture_output=True)
            so = c.stdout.decode("latin-1").replace("\r\n", "\n")
            se = c.stderr.decode("latin-1").replace("\r\n", "\n")
            why = None
            if status is None and c.returncode == 0:
                why = "exit status 0, expected an error"
            elif status is not None and c.returncode != status:
                why = f"exit status {c.returncode}, expected {status}"
            elif out is not None and so != out:
                why = f"stdout {so!r}, expected {out!r}"
            elif err is None and se.strip():
                why = f"stderr {se.strip()[:300]!r}"
            elif err is not None and err not in se:
                why = f"stderr {se.strip()[:300]!r} lacks {err!r}"
            if why:
                print(f"FAIL {name}: {why}")
                failed += 1
            else:
                print(f"PASS {name}")
    finally:
        if args.keep:
            print("work directory:", work)
        else:
            shutil.rmtree(work, ignore_errors=True)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
