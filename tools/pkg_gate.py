#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Compile gate over the packages of a cm3 installation.

Usage:
  python3 tools/pkg_gate.py [PACKAGE...] [--cm3 DIR] [--java JAVA] [--jar JAR]
                            [--work DIR] [--show N] [--jvm-arg=ARG]...

For every package in DIR/pkg (all of them by default), the script reads
AMD64_NT/.M3EXPORTS: the modules of the package (with the instances of
generics) and the packages it imports (transitively).  It compiles the
safe modules with m3j (-lib: other modules that the compiler pulls in are
optional), all at once, and every class must verify.  The modules that
fail are taken out and the rest is compiled again.  Every module is
counted as one of

  OK            compiled, its classes verify
  UNSAFE        an UNSAFE module (not compiled)
  UNSAFE-IMPORT a safe module that imports an UNSAFE interface (rejected)
  ERROR         rejected by the front end for another reason
  VERIFY        a class does not verify
  CRASH         the compiler failed (an exception)
  BLOCKED       not compiled because of an error elsewhere (an interface)
  TIMEOUT       the compiler did not finish

The modules that are not OK are listed per package (at most N per status).
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGET = "AMD64_NT"
COMMENT = re.compile(rb"\(\*.*?\*\)|<\*.*?\*>", re.S)


def is_unsafe(f):
    """Whether the unit in file f is UNSAFE (its first token, after the comments and pragmas)."""
    return re.match(rb"\s*UNSAFE\b", COMMENT.sub(b" ", f.read_bytes())) is not None


def exports(pkg):
    """(modules, imported packages) of the package directory pkg."""
    text = (pkg / TARGET / ".M3EXPORTS").read_text(encoding="latin-1")
    mods = []
    for name, _, sub in re.findall(r'_map_add_module\("([^"]+)", "([^"]+)", "([^"]*)"', text):
        mods.append(pkg / sub / name)
    imps = re.findall(r'import_version\("([^"]+)"', text)
    return mods, imps


def closure(root, name, seen):
    """The packages that package name imports, transitively."""
    for p in exports(root / name)[1]:
        if p not in seen and (root / p / TARGET / ".M3EXPORTS").exists():
            seen.add(p)
            closure(root, p, seen)
    return seen


ERR = re.compile(r'^"([^"]+)", line \d+: (.*)$', re.M)
CRASH = re.compile(r"^\*\*\* (.+?): (.*)$", re.M)
VERIFY = re.compile(r"class Lm3/[mi]/([A-Za-z0-9_]+); does not verify: (.*)$", re.M)


def compile_units(args, pkgs, files, work):
    """Compiles files (-lib: the other modules are optional); (rc, output)."""
    cls = work / "classes"
    if cls.exists():
        shutil.rmtree(cls)
    cmd = [args.java] + args.jvm_arg + ["-Xss64m", "-cp", args.jar, "m3j.jvm.Compile", "-lib", "-cm3", str(args.cm3)]
    for p in pkgs:
        if p not in ("m3core", "libm3"):
            cmd += ["-pkg", str(args.cm3 / "pkg" / p / TARGET)]
    cmd += [str(f) for f in files] + ["-d", str(cls)]
    try:
        c = subprocess.run(cmd, cwd=work, capture_output=True, timeout=args.timeout)
    except subprocess.TimeoutExpired:
        return None, ""
    return c.returncode, (c.stdout + c.stderr).decode("latin-1")


def failures(out, files):
    """The files of files that out reports as failing: {file: (status, why)}."""
    by_name = {f.name: f for f in files}
    by_path = {os.path.normcase(os.path.abspath(f)): f for f in files}
    res = {}

    def find(name):
        return by_path.get(os.path.normcase(os.path.abspath(name))) or by_name.get(Path(name).name)

    for m in VERIFY.finditer(out):
        f = by_name.get(m.group(1) + ".m3")
        if f is not None:
            res.setdefault(f, ("VERIFY", m.group(2)[:160]))
    for m in CRASH.finditer(out):
        f = find(m.group(1))
        if f is not None:
            res.setdefault(f, ("CRASH", m.group(2)[:160]))
    for m in ERR.finditer(out):
        f = find(m.group(1))
        why = m.group(2)
        if f is None or why.startswith("warning"):
            continue
        s = "UNSAFE-IMPORT" if re.search(r"unsafe", why, re.I) else "ERROR"
        res.setdefault(f, (s, why[:160]))
    return res


def last_line(out):
    lines = [l for l in out.splitlines() if l.strip() and "warning" not in l and not l.startswith("	at ")]
    return (lines[0] if lines else "").strip()[:160]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("packages", nargs="*")
    ap.add_argument("--cm3", type=Path, default=ROOT / "cm3")
    ap.add_argument("--java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
    ap.add_argument("--work", type=Path)
    ap.add_argument("--show", type=int, default=5)
    ap.add_argument("--timeout", type=int, default=600)
    ap.add_argument("--jvm-arg", action="append", default=[],
                    help="an option for every JVM that compiles (repeatable; --jvm-arg=-X...)")
    args = ap.parse_args()
    if not args.java:
        jdk = os.environ.get("JDK25")
        args.java = str(Path(jdk) / "bin" / "java") if jdk else "java"
    root = args.cm3 / "pkg"
    names = args.packages or sorted(p.name for p in root.iterdir()
                                    if (p / TARGET / ".M3EXPORTS").exists())
    work = args.work or Path(tempfile.mkdtemp(prefix="pkg_gate"))
    work.mkdir(parents=True, exist_ok=True)
    total = Counter()
    for name in names:
        mods, _ = exports(root / name)
        mods = [m for m in mods if m.exists()]
        pkgs = sorted(closure(root, name, set()) | {name})
        status = {}
        safe = []
        for m in mods:
            if is_unsafe(m):
                status[m] = ("UNSAFE", "")
            else:
                safe.append(m)
        rest = list(safe)
        while rest:
            rc, out = compile_units(args, pkgs, rest, work)
            if rc is None:
                for m in rest:
                    status[m] = ("TIMEOUT", "")
                break
            if rc == 0:
                for m in rest:
                    status[m] = ("OK", "")
                break
            bad = failures(out, rest)
            if not bad:
                if failures(out, mods).keys() <= status.keys():
                    # only modules that failed before, which the compiler pulls in again
                    for m in rest:
                        status[m] = ("OK", "")
                else:
                    # a failure that names none of the modules (an imported interface)
                    for m in rest:
                        status[m] = ("BLOCKED", last_line(out))
                break
            status.update(bad)
            rest = [m for m in rest if m not in bad]
        c = Counter(s for s, _ in status.values())
        total.update(c)
        print(f"{name:20} " + "  ".join(f"{k} {v}" for k, v in sorted(c.items())), flush=True)
        shown = Counter()
        for m, (s, why) in sorted(status.items()):
            if s not in ("OK", "UNSAFE") and shown[s] < args.show:
                shown[s] += 1
                print(f"    {s:13} {m.relative_to(root)}: {why}", flush=True)
    print("total: " + "  ".join(f"{k} {v}" for k, v in sorted(total.items())))
    if not args.work:
        shutil.rmtree(work, ignore_errors=True)
    return 0 if set(total) <= {"OK", "UNSAFE", "UNSAFE-IMPORT"} else 1


if __name__ == "__main__":
    sys.exit(main())
