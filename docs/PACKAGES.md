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

These packages need no CM3 installation: m3j has them, as libraries or
inside its tools.

| Package | What you get |
|---|---|
| m3core, libm3, tcp, netobj, sysutils, libbuf, patternmatching | Libraries, which a program uses as with cm3 (`import ("libm3")` in an m3makefile). They are CM3's safe modules, a few of them changed; what CM3 does in UNSAFE modules, m3j's runtime does in Java. [`lib/README.md`](../lib/README.md) lists what is there and what changed. |
| m3quake, and the parts of cm3 and m3middle that read m3makefiles | Not a library: they are the tool m3build, which builds a package from its m3makefile as cm3 does. |
| m3bundle | Not a library: the tool m3bundle, which m3build runs for `bundle` in an m3makefile. |
| m3tk, m3tk-misc | Not a library: m3j's tools stubgen (the stubs of network objects, for `netobj` in an m3makefile) and the language server are built on a changed copy of them; m3j does not install it as a library. |

m3j's `json` is a library of its own under the same name, not CM3's (whose
`Json` module is UNSAFE); `import ("json")` gets m3j's.

## Complete

Every module is safe, and nothing they use is missing.

| Package | Kind |
|---|---|
| bitvector | library |
| commandrw | library |
| debug | library |
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

## Mostly usable

Most of the package compiles and is there; one part is not, because it is
UNSAFE or needs something that is.

| Package | Kind | Not available | Available |
|---|---|---|---|
| arithmetic | library | the interface `Bits` | all the rest (294 modules) |
| rdwr | library | `SimpleMsgRW` | `RdWrPipe`, `TeeWr` |
| realgeometry | library | `PathExtra`: it needs `Path` of libm3, which is UNSAFE | `RealInterval`, `RealPath`, `RealPoint`, `RealRect`, `RealTransform` |

## Compiles, but its core is missing

The safe modules compile, but the main interface of the package needs a
module that is UNSAFE, so the package is of little use on m3j.

| Package | Kind | Why |
|---|---|---|
| binIO | library | `BinIO` needs `FastBinIO`, which is UNSAFE. |
| db | library | `DB` reaches its databases through `ODBCDB`, which is UNSAFE. |
| events | library | `EventWr`, `EventPort`, `EventStubLib` and two more are UNSAFE. What does not need them is complete: `EventCounter`, `RdWrMutex` and `WorkerPool`. |
| m3markup | library | The module of its interface `ID` is UNSAFE, and it needs the scanner of m3scan, which does not work (next row). |
| m3scan | library | `M3Scanner` needs `m3scan_M3ID`, which is UNSAFE. |
| m3scanner | library | `M3Scanner` needs `M3Keyword`, which is UNSAFE. |
| smalldb | library | `SmallDB` needs `OSSupport`, which is UNSAFE. |
| sgml | library | `FSM` needs `DeepCopy` of the package deepcopy, which is UNSAFE; sgml also builds on m3quake and m3middle (next section). |
| fix_nl | program | Its main module needs `OS`, which is UNSAFE. |
| sgship | program | `AstToType` needs `FRefRefTbl`, which is UNSAFE, and `WiredStandard` of m3tk, which is UNSAFE too. |

## CM3's compiler and its tools

These packages are CM3 itself. They compile with m3j, but they would not
run on it: they rely on UNSAFE parts of m3middle and on the internals of
CM3's runtime. They are not needed either, since m3j has a compiler of
its own and m3build in place of cm3.

| Package | What it is |
|---|---|
| cm3 | the compiler driver (m3build has its reading of m3makefiles) |
| m3front | the front end (m3j has it ported to Java) |
| m3middle | the layer between front end and back ends |
| m3back, m3staloneback, m3objfile, m3linker | the native back end, object files, linking |
| m3quake | the quake interpreter (m3build has a changed copy) |
| m3cgcat | prints CM3's intermediate code |
| cm3ide, m3tools | CM3's IDE (a web server) and its parser of Modula-3 |

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
