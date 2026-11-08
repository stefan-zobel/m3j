#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Prepare the Modula-3 solutions of Rosetta Code as cm3 packages for the
differential tests (tools/diff_test.py).

  python3 tools/rosetta_prep.py --orig DIR [--out tests/rosetta]

DIR holds the solutions as in https://github.com/acmeism/RosettaCodeData
(Task/<task>/Modula-3/<task>[-N].mod3, stored as <task>[-N].mod3). For
every solution that is a whole program (a module that is or exports Main),
and not UNSAFE, this writes

  OUT/orig/<name>.mod3          the solution exactly as on Rosetta Code
  OUT/<name>/src/<Unit>.i3/.m3  its units, each with a line naming the
                                origin and the licence first
  OUT/<name>/src/m3makefile     import ("libm3"), the units, program (...)

and the list of the tests, tests/diff/rosetta.list (with input for the
tasks that read standard input, from INPUTS below). OUT/index.tsv lists
every solution and what happened to it. The content of Rosetta Code is
available under the GNU Free Documentation License 1.3.
"""

import argparse
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

UNIT = re.compile(r'^[ \t]*(?:UNSAFE[ \t]+)?(?:GENERIC[ \t]+)?(INTERFACE|MODULE)[ \t]+(\w+)', re.M)

# standard input for the tasks that read it
INPUTS = {
    "A+B": "2 3\n",
    "Input-loop": "first line\nsecond line\nthird\n",
    "User-input-Text": "hello\n75000\n",
}


# files that a program reads, in its working directory (its src directory)
FILES = {
    "File-input-output": {"input.txt": "The first line.\nThe second line, a little longer.\n"},
}

# programs whose output depends on the machine or the moment, not compared
EXCLUDE = {
    "Command-line-arguments": "prints the path of the program",
    "Environment-variables": "prints the environment",
    "Hostname": "prints the name of the machine",
    "System-time": "prints the time",
}


def units(text):
    """The units of a solution: (kind, name, text), or None if they cannot be separated."""
    starts = [(m.start(), m.group(1), m.group(2)) for m in UNIT.finditer(text)]
    if not starts:
        return None
    result = []
    for i, (pos, kind, name) in enumerate(starts):
        begin = 0 if i == 0 else pos
        end = starts[i + 1][0] if i + 1 < len(starts) else len(text)
        m = re.search(r'\bEND[ \t]+' + name + r'[ \t]*\.', text[pos:end])
        if not m:
            return None
        result.append((kind, name, text[begin:pos + m.end()] + "\n"))
    return result


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--orig", type=Path, required=True)
    ap.add_argument("--out", type=Path, default=ROOT / "tests" / "rosetta")
    args = ap.parse_args()
    out = args.out
    (out / "orig").mkdir(parents=True, exist_ok=True)
    index, tests = [], []
    for f in sorted(args.orig.glob("*.mod3")):
        name = f.stem
        task = re.sub(r'-\d$', '', name)   # <task>-N.mod3 when a task has several solutions
        text = f.read_text(encoding="latin-1").replace("\r\n", "\n")
        us = units(text)
        if task in EXCLUDE:
            index.append((name, "excluded: " + EXCLUDE[task]))
            continue
        if "UNSAFE" in text:
            index.append((name, "unsafe"))
            continue
        if us is None:
            index.append((name, "fragment"))
            continue
        mains = [u for u in us if u[0] == "MODULE"
                 and (u[1] == "Main" or re.search(r'MODULE\s+' + u[1] + r'\s+EXPORTS\s+(\w+\s*,\s*)*Main\b', u[2]))]
        if not mains:
            index.append((name, "no program"))
            continue
        shutil.copy(f, out / "orig" / f.name)
        pkg = out / name
        if pkg.exists():
            shutil.rmtree(pkg)
        src = pkg / "src"
        src.mkdir(parents=True)
        header = (f"(* Rosetta Code, task {task}: https://rosettacode.org/wiki/{task.replace('-', '_')}"
                  f" (GNU Free Documentation License 1.3) *)\n")
        intfs = {u[1] for u in us if u[0] == "INTERFACE"}
        mods = {u[1] for u in us if u[0] == "MODULE"}
        mk = ['import ("libm3")']
        for kind, uname, utext in us:
            ext = ".i3" if kind == "INTERFACE" else ".m3"
            (src / (uname + ext)).write_text(header + utext, encoding="latin-1")
            if kind == "INTERFACE":
                mk.append(f'{"interface" if uname not in mods else "module"} ("{uname}")')
            elif uname not in intfs:
                mk.append(f'implementation ("{uname}")')
        mk.append(f'program ("{re.sub(r"[^A-Za-z0-9_]", "_", name)}")')
        (src / "m3makefile").write_text("\n".join(mk) + "\n")
        for fname, text in FILES.get(task, {}).items():
            (src / fname).write_text(text)
        line = f"rosetta/{name} {out.relative_to(ROOT).as_posix()}/{name}"
        if task in INPUTS:
            (pkg / "input.txt").write_text(INPUTS[task])
            line += " stdin=input.txt"
        tests.append(line)
        index.append((name, "program"))
    (out / "index.tsv").write_text("".join(f"{n}\t{s}\n" for n, s in index))
    (ROOT / "tests" / "diff" / "rosetta.list").write_text(
        "# The Modula-3 programs of Rosetta Code (written by tools/rosetta_prep.py)\n" + "\n".join(tests) + "\n")
    print(len(tests), "programs of", len(index), "solutions")


if __name__ == "__main__":
    main()
