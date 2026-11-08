#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Writes the .M3EXPORTS of the packages that m3j brings (lib/PKG/JVM),
through which m3build (tools/m3build) imports them, and copies the quake
templates of cm3 that they export into lib.

Usage:
  python3 tools/gen_lib_exports.py [--cm3 CM3DIR] [--src CM3SRC]

The exports of a package are those that cm3 installed for it
(CM3DIR/pkg/PKG/AMD64_NT/.M3EXPORTS, by default cm3 in the root of m3j),
for the units that lib has: a unit of cm3 that lib does not have (C
sources, UNSAFE modules that m3j leaves out) is left out, and a unit of lib
that cm3 does not list (m3j's own) is added, an interface visible and a
module hidden, as cm3 lists them. A unit that is in more than one
directory of the package (the parts for POSIX and for Windows) is listed
once, the first found in cm3's exports or else in the order of the
directories. import_version names the build directory JVM, and a
package that lib does not have is not imported (json imports table-list
in cm3, whose units it does not use); the linker data of cm3
(_import_m3lib, import_sys_lib, _map_add_c, _map_add_h) is left out. m3core imports the package m3j (lib/m3j: the interfaces that
the runtime of m3j implements), so that its units are found too.

The templates (_import_template) are copied from the sources of cm3
(CM3SRC, by default cm3-src in the root of m3j) to the same place in lib.
The same is written for the source packages of m3tk and m3tk-misc in
tests/stubgen (TOOLKIT), so that m3build imports them through an override
(the language server, tools/langserver).

The results are part of the tree: run this after a change of the units of
lib.
"""

import argparse
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LIB = ROOT / "lib"
PACKAGES = ["m3core", "libm3", "tcp", "netobj", "sysutils", "json", "libbuf"]
# source packages outside lib: where they are in the tree of m3j
TOOLKIT = {"m3tk-misc": ROOT / "tests" / "stubgen" / "m3tk-misc",
           "m3tk": ROOT / "tests" / "stubgen" / "m3tk"}
# where the sources of each package are in the tree of cm3
SOURCES = {"m3core": "m3-libs/m3core", "libm3": "m3-libs/libm3", "tcp": "m3-comm/tcp",
           "netobj": "m3-comm/netobj", "sysutils": "m3-libs/sysutils", "json": "m3-libs/json",
           "libbuf": "m3-libs/libbuf", "m3tk-misc": "m3-libs/m3tk-misc", "m3tk": "m3-tools/m3tk"}
LINE = re.compile(r'^(_map_add_(?:interface|module|generic_interface|generic_module))'
                  r'\("([^"]+)", "([^"]+)", "([^"]*)", "([^"]*)"\)$')
KINDS = {".i3": "_map_add_interface", ".m3": "_map_add_module",
         ".ig": "_map_add_generic_interface", ".mg": "_map_add_generic_module"}


def place(pkg):
    """The directory of the package pkg."""
    return TOOLKIT.get(pkg, LIB / pkg)


def units(pkg):
    """The units of lib/pkg: (file name, directory relative to the package)."""
    base = place(pkg)
    found = []
    for f in sorted(base.rglob("*")):
        if f.suffix in KINDS and f.is_file() and "JVM" not in f.relative_to(base).parts:
            found.append((f.name, f.parent.relative_to(base).as_posix()))
    return found


def exports(pkg, cm3, src, out_lines):
    have = units(pkg)
    where = {}
    for name, d in have:
        where.setdefault(name, []).append(d)
    listed = set()
    lines = ["%% exports of %s (m3j)" % pkg]
    cm3_file = cm3 / "pkg" / pkg / "AMD64_NT" / ".M3EXPORTS"
    for line in cm3_file.read_text(encoding="latin-1").splitlines():
        m = LINE.match(line)
        if m:
            kind, name, p, d, hidden = m.groups()
            if name in listed or d not in where.get(name, []):
                continue
            listed.add(name)
            lines.append(line)
        elif line.startswith("_define_lib(") or line.startswith("export_interface("):
            lines.append(line)
            if line.startswith("_define_lib(") and pkg == "m3core":
                lines.append('import_version("m3j", "JVM")')
        elif line.startswith("import_version("):
            imported = re.match(r'import_version\("([^"]+)"', line).group(1)
            if (place(imported) / "src").is_dir():
                lines.append(line.replace('"AMD64_NT"', '"JVM"'))
        elif line.startswith("_import_template("):
            t = re.match(r'_import_template\("([^"]+)", "([^"]+)", "([^"]*)"\)', line)
            tmpl, d = t.group(1), t.group(3)
            dest = place(pkg) / d / tmpl
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(src / SOURCES[pkg] / d / tmpl, dest)
            lines.append(line)
    for name, d in have:
        if name in listed:
            continue
        listed.add(name)
        hidden = "hidden" if name.endswith(".m3") else ""
        lines.append('%s("%s", "%s", "%s", "%s")' % (KINDS[name[-3:]], name, pkg, d, hidden))
    out_lines[pkg] = lines


def m3j_exports(out_lines):
    lines = ["% exports of m3j: the interfaces that the runtime of m3j implements",
             '_define_lib("m3j")']
    for name, d in units("m3j"):
        hidden = "hidden" if name.endswith(".m3") else ""
        lines.append('%s("%s", "m3j", "%s", "%s")' % (KINDS[name[-3:]], name, d, hidden))
    out_lines["m3j"] = lines


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--cm3", default=str(ROOT / "cm3"))
    ap.add_argument("--src", default=str(ROOT / "cm3-src"))
    args = ap.parse_args()
    out = {}
    for pkg in PACKAGES + list(TOOLKIT):
        exports(pkg, Path(args.cm3), Path(args.src), out)
    m3j_exports(out)
    for pkg, lines in out.items():
        d = place(pkg) / "JVM"
        d.mkdir(exist_ok=True)
        (d / ".M3EXPORTS").write_bytes(("\n".join(lines) + "\n").encode("latin-1"))
        print("%-9s %d lines" % (pkg, len(lines)))


if __name__ == "__main__":
    main()
