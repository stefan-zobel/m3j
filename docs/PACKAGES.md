# CM3 packages on m3j

Which packages of a CM3 installation compile with m3j, and what is missing
in those that only partly do. The numbers come from `tools/pkg_gate.py`
over the 63 packages of the CM3 installation that m3j is tested against
(CM3 commit `20a295f`, target `AMD64_NT`).

"Compiles" means that every safe module of the package compiles and every
class verifies. It does not mean "tested": apart from the packages that
m3j brings itself, none of them has been run on m3j. Reports of a package
that compiles but behaves differently than with cm3 are welcome.

m3build finds the packages of a CM3 installation with `-cm3 DIR` (or
`CM3_ROOT`), after those of m3j, so an m3makefile can `import` them as
with cm3 ([`USING.md`](USING.md#packages-m3build)).

## The result in short

- 1,345 modules: 1,182 compile and verify, 163 are UNSAFE and left out.
- No safe module fails: no error of the front end, no class that does not
  verify, no crash of the compiler.
- Where a package is incomplete, the reason is always an UNSAFE module:
  one of its own, or one of another package that m3j does not replace.

## Brought by m3j

| Package | How |
|---|---|
| m3core, libm3, tcp, netobj, sysutils, libbuf, patternmatching | in `lib`: the safe modules of CM3, some changed, and in place of the UNSAFE ones the runtime of m3j in Java; [`lib/README.md`](../lib/README.md) lists what is there and what changed |
| m3quake, and the parts of cm3 and m3middle that read m3makefiles | in `m3build.jar` |
| m3bundle | `m3bundle.jar` |
| m3tk, m3tk-misc | in `stubgen.jar` and `m3_lang_server.jar`, in a changed copy (`tests/stubgen`); not installed as libraries |

m3j's `json` is a library of its own under the same name, not CM3's (whose
`Json` module is UNSAFE); `import ("json")` gets m3j's.

## Complete

Every module is safe, and nothing they use is missing.

| Package | Kind |
|---|---|
| bitvector | library |
| commandrw | library |
| digraph | library |
| libsio | library |
| listfuncs | library |
| m3crypto | library |
| parseparams | library |
| set | library |
| sortedtableextras | library |
| table-list | library |
| tempfiles | library |
| unittest | library |
| unittest-numeric | library |
| cmpdir | program |
| cmpfp | program |
| m3cggen | program |

## Partly

The safe modules compile; the units in the last two columns have no
implementation on m3j, so a call of their procedures fails at run time.

| Package | Kind | Safe modules | Its UNSAFE modules (left out) | Missing from other packages |
|---|---|---:|---|---|
| arithmetic | library | 294 | Bits | |
| binIO | library | 1 | FastBinIO | |
| db | library | 3 | ODBCDB | m3core: Text8, Text8Short |
| debug | library | 1 | | m3core: ThreadF |
| events | library | 15 | EventWireRep, EventSpaceID, EventWr, EventStubLib, EventPort | m3core: ThreadF |
| m3markup | library | 6 | ID | m3scan: m3scan_M3ID |
| m3scan | library | 2 | m3scan_M3ID | |
| m3scanner | library | 1 | M3Keyword | |
| rdwr | library | 3 | SimpleMsgRW | |
| realgeometry | library | 6 | | libm3: Path, PathPrivate |
| smalldb | library | 1 | OSSupportWIN32 | |
| sgml | library | 6 | | deepcopy: DeepCopy; the parts of CM3 below |
| fix_nl | program | 1 | OSWIN32 | |
| sgship | program | 12 | FRefRefTbl | m3tk: WiredStandard; m3core: RT0 |

Text8 and Text8Short are CM3's representation of TEXT; on m3j a TEXT is a
`java.lang.String`, and there are no such modules. ThreadF is part of
CM3's thread implementation.

## Parts of CM3 itself

These packages compile too, but they are the compiler, its tools and what
those build on. They need m3middle's UNSAFE modules (M3ID, M3FP, TFloat,
M3File, M3Process) and the internals of CM3's runtime (RT0, RTHeapInfo,
RTCollectorSRC and other `RT` interfaces of m3core), which m3j has no use
for: its own front end is in Java.

cm3, cm3ide, m3back, m3cgcat, m3front, m3linker, m3middle, m3objfile,
m3quake (see above for m3build), m3staloneback, m3tools.

## Not available

| Package | Why |
|---|---|
| deepcopy, odbc, serial, slisp, udp | libraries whose modules are all UNSAFE |
| dirfp, libdump, mklib, tapi | programs whose main module is UNSAFE |
| json | CM3's `Json` is UNSAFE (see above for m3j's `json`) |
| mtex, windowsResources | nothing to compile in the installation |

Not in the installation, and not supported by m3j: the GUI libraries
(Trestle, VBTkit, FormsVBT) and what builds on them.

## Running it again

```sh
JDK25=/path/to/jdk-25 python3 tools/pkg_gate.py --cm3 CM3DIR [PACKAGE...]
```

It prints a line per package with the number of modules per status, the
modules that are not OK, and with "no module of" the interfaces that the
compiled modules use but that no module implements.
