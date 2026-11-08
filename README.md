# m3j - Modula-3 on the JVM

m3j compiles Modula-3 to Java bytecode. Its front end is a faithful Java
port of the front end of Critical Mass Modula-3 (CM3), its back end new
code on the JDK's ClassFile API. CM3 is the reference for everything.

- **Front end** (`m3j.front`, with the parts of `m3middle` and `m3core` it
  needs): on CM3's own libraries and on the m3tests suite it produces the
  same intermediate code and the same diagnostics as cm3 (see
  [`MODULA3-JVM.md`](MODULA3-JVM.md)).
- **Back end** (`m3j.jvm`) and **runtime** (`m3j.rt`, and Java versions of
  the library interfaces in `m3.i`): the safe language, threads and the
  parts of libm3 and m3core that safe programs use; the other library
  modules are compiled from their sources, which m3j brings (`lib`). The
  program tests of m3tests (`p0`-`p2`, `r0`) give the expected output
  except for a few listed cases (`tools/run_jvm.py`), and every class of
  the compile tests `c0` and `c1` verifies. The design is in
  [`docs/BACKEND.md`](docs/BACKEND.md).

UNSAFE modules are not supported.

## Build

Needs a POSIX shell (Linux, macOS, WSL or Git Bash on Windows) and JDK 25 or later.

```sh
JDK25=/path/to/jdk-25 sh build.sh
```

writes `m3j.jar` (compiler, runtime and the Modula-3 library), `m3j-runtime.jar`
(only what a compiled program needs), `m3bundle.jar` (cm3's m3bundle,
compiled by m3j), `m3build.jar` (the build system, see below) with its
configuration `m3j.cfg`, `stubgen.jar` (cm3's stub generator of network
objects, compiled by m3j; `M3J_STUBGEN=no` skips it), `m3_lang_server.jar`
(cm3's language server, built by m3build; `M3J_LSP=no` skips it) and the
AOT cache `m3j.aot`, which saves start time of the compiler and of
programs run with `m3j.jar` (`M3J_AOT=no` skips it).

## Use

The library (the units of m3core and libm3 that the tests use: `IO`, `Rd`,
`Wr`, `Fmt`, `Scan`, `Text`, `Thread`, `Random`, the common generics and
others, the package tcp: `TCP`, `IP`, `ConnRW`, `ConnMsgRW`, the package
netobj: `NetObj`, `TCPNetObj`, `StubLib` and the rest of the runtime of
network objects, the package sysutils: `System`, `FSUtils`,
`TextUtils` and others, and the packages json and libbuf: `Json`, `Buf`,
`Wx`, and the bindings of the Java platform in the package java) is in
the jar; for these no cm3 installation is needed:

```sh
java -XX:AOTCache=m3j.aot -cp m3j.jar m3j.jvm.Compile Main.m3 -d out
java -cp m3j-runtime.jar:out m3.Main
java -cp m3j.jar m3j.jvm.Compile Main.m3 -run ARG...   # in memory
```

`-I DIR` adds a source directory, `-pkg DIR` another package: a package
directory of a cm3 installation (`CM3DIR/pkg/NAME/AMD64_NT`) or a source
package (its units in `DIR/src`, as in `tests/pkg`), `-d prog.jar` writes a
jar. With `-lib`, modules that are not given are optional: they are
compiled if they can be (safe ones), as the modules of libraries are.
`-cm3 DIR` adds the packages of a cm3 installation after the library of
m3j, for the other units of m3core and libm3. `-version` prints the
version of m3j.

Files become resources of a program as in cm3, through a bundle that
m3bundle writes (the interface `NAME.i3` and the module `NAME.m3`, which
are compiled with the program; `Bundle.Get (NAME.Get (), ELEMENT)` and
`Rsrc` give the contents of a file):

```sh
java -jar m3bundle.jar -name NAME -element ELEMENT FILE ...   # m3j-runtime.jar next to it
```

### Java APIs

An interface can stand for a Java class. The program then calls its
methods, holds its objects in typed variables and catches its exceptions,
and the compiler checks the declarations against the class:

```modula3
<*EXTERNAL "java.util.ArrayList":JAVA*> INTERFACE ArrayList;

IMPORT Java, JavaObject;

TYPE T <: JavaObject.T;                         (* the class java.util.ArrayList *)

PROCEDURE New (): T;                            (* a constructor: New or "<init>" *)
PROCEDURE add (self: T; e: REFANY): BOOLEAN;    (* the first formal is the receiver *)
PROCEDURE size (self: T): Java.Int;

<*EXTERNAL "add(ILjava/lang/Object;)V"*>        (* a name and a descriptor pick an overload *)
PROCEDURE addAt (self: T; i: Java.Int; e: REFANY);

END ArrayList.
```

A static method has no receiver, and a static field is a variable
(`<*EXTERNAL*> VAR MAX_VALUE: Java.Int;`; assigning to a `final` field is
an error). One procedure of an ordinary interface is bound by naming the
class too: `<*EXTERNAL "java.lang.Math.max":JAVA*> PROCEDURE Max (a, b:
INTEGER): INTEGER;`. Bindings are declared in interfaces only, and their
formals are VALUE formals. The class or member is looked up when the
interface is compiled: a class that is missing, a method without a fitting
overload or an ambiguous one (the message lists the descriptors) is an
error at the declaration, even if the program never calls it.

| Modula-3 | Java |
|---|---|
| `INTEGER`, `LONGINT`, subranges | `long`, or `int`, `short`, `byte` with a check of the range (`Java.Int`, `Java.Short`, `Java.Byte` need none) |
| `BOOLEAN` | `boolean` |
| `CHAR`, `WIDECHAR` (`Java.Char`) | `char` |
| `REAL` | `float`, or `double` for a parameter |
| `LONGREAL` | `double` |
| `TEXT` | `String` or a supertype of it (`self: TEXT` in a binding of `String`) |
| the type `T` of a binding | the class, or a supertype of it |
| `REFANY` | any class, `Object` without a cast; `NIL` is `null` |
| a procedure type | an interface with one abstract method |

Results convert the other way round, with a cast where the Java type is
wider, and `void` is a proper procedure. `ISTYPE`, `NARROW` and `TYPECASE`
on the types of bindings are `instanceof`; a Java object is not a `ROOT`.
Scalars are not boxed for a parameter of type `Object`, and Java arrays are
held in a `REFANY`: `Java.i3` has the boxes (`FromInt`, `FromLong`,
`ToInt`), copies between arrays of both languages, and `ToText`, `Equals`,
`HashCode`, `ClassName` and `IsInstance` for any reference.

A Java exception that ends a call is `Java.Exception (t)`, with the
`JavaThrowable.T` as its argument. Like `RuntimeError.E` it is implicit and
need not be in a `RAISES` clause. Those that stand for a checked runtime
error (`null` as receiver, an index out of bounds, a bad cast, overflow of
the stack, no memory left) are that runtime error instead.

A procedure passed for a parameter of a functional interface (`Runnable`,
`Comparator`) is called by Java, also on Java's threads, with its arguments
and result converted as above; `NIL` stays `null`, and it can be a nested
procedure. A Modula-3 exception that it raises passes through Java and
comes back to the call that made Java run it. On a thread that Java
started there is no such call: the exception ends that thread only and
the JVM prints it.

Classes that are not those of the JDK are given with `-cp` (separated as
in a class path of the system). A jar written with `-d` finds them through
its manifest:

```sh
java -cp m3j.jar m3j.jvm.Compile -cp tool.jar Main.m3 -d prog.jar
```

m3jbind writes the interfaces. Each class gets `DIR/Name.i3` with its
public constructors (`New`, `New2`, ...), the public methods that it
declares and its public constants, each with its descriptor:

```sh
java -cp m3j.jar m3j.jvm.Bind [-cp PATH] [-d DIR] [-prefix P] [-name a.b.C=Name]... a.b.C ...
```

Overloaded methods have the names of their parameter types as a suffix
(`add_int_Object`), and a name that is not an identifier of Modula-3 or is
taken gets a changed name with the Java name in the pragma (`END_`). The
interface is named after the class (`Map_Entry` for a nested class); a
name that the library has too is reported, and the interface hides that of
the library, so give another with `-prefix` or `-name`. A parameter or
result has the type of a binding if its class is bound, in the run or by
an interface in `DIR`; any other class is `REFANY`, and so is a class that
would make two interfaces import each other (`File` and `Path`). `T` is a
subtype of the type of the nearest bound superclass or superinterface. A
binding has the methods the class declares, so bind the supertypes too
for what it inherits. Parameters for callbacks are `REFANY` in the output:
to pass a procedure, write the formal with a procedure type by hand.

Not supported: Modula-3 subtypes of Java classes, implementing interfaces
with several methods, instance fields, and a bound procedure as a
procedure value or a method (each is an error).

### Packages: m3build

m3build builds a package from its `m3makefile`, as `cm3` does: it is cm3's
quake interpreter and the parts of cm3's driver that read m3makefiles,
compiled by m3j, with the compiler of m3j in place of cm3's back ends. In
the directory of a package (with `src/m3makefile`):

```sh
java -jar m3build.jar                        # -build: into the directory JVM
java -jar m3build.jar -ship -root DIR        # install into DIR/pkg, DIR/bin
java -jar m3build.jar -clean                 # or -realclean
```

The m3makefile is that of cm3: `import`, `module`, `interface`,
`implementation` and the other units (hidden or visible), the generic
instances of the library (`Table`, `Sequence`, `List`, `Sorted_table` and
the others) and `build_generic_intf`, `template`, `include_dir`, `resource`
and `bundle` (with m3bundle.jar), `netobj` (with stubgen.jar), `program`,
`library`, `m3overrides` with `-override`, and the rest of quake. A
program becomes `JVM/NAME.jar` with the scripts `JVM/NAME` (POSIX) and
`JVM/NAME.cmd` (Windows) that run it; it is compiled again when one of its
sources changed. A library is checked (its UNSAFE modules are left out)
and installed as its sources and its `.M3EXPORTS`, since m3j compiles a
program from the sources of the libraries it uses. Packages are found in
the installation (`-root DIR` or `M3J_ROOT`), then among those that m3j
brings (`lib`: m3core, libm3, tcp, netobj, sysutils, json, libbuf, java), then in a cm3
installation (`-cm3 DIR` or `CM3_ROOT`). C sources and the options of
cm3's back ends are not supported; `-exports` writes only the
`.M3EXPORTS`. `java -jar m3build.jar -help` lists the options.

A package with bindings of Java classes does `import ("java")` and names
the jars or directories of the classes that are not those of the JDK:
`java_classpath ("../lib/foo.jar")`, relative to the file that says so.
The compiler gets them, the program runs with them, and it is compiled
again when one of the jars changes. They are not passed on to the
programs that import a library: each program names its own.

### Editors: the language server

`m3_lang_server.jar` is cm3's language server for Modula-3, compiled by
m3j: through the Language Server Protocol, an editor gets the errors and
warnings of a file while it is edited, the type of an identifier (hover)
and the place of its declaration and of the definition of its type, from
m3tk, cm3's toolkit for Modula-3. It is started as
`java -jar m3_lang_server.jar` (with `m3j.jar` and `m3build.jar` next to
it) and checks the files of packages (below a directory `src` with an
`m3makefile`), whose imports it gets from m3build. [`tools/langserver/vscode`](tools/langserver/vscode)
is an extension for Visual Studio Code (syntax highlighting and a client
of the server, without other packages); [`tools/langserver/README.md`](tools/langserver/README.md) has
the settings for Neovim, Emacs (Eglot), Helix and Kate.

A compiled program follows the conventions of the operating system that it
runs on (`Pathname`, `OSConfig`); `-Dm3j.os=POSIX` or `-Dm3j.os=WIN32` for
the JVM chooses one. The constants `Wr.EOL`, `OSConfig.LineSep`, `ArcSep`
and `PathSep` are fixed when the program is compiled: they are those of the
system that the compiler runs on (or that `-Dm3j.os` names for it).

## Tests

These tests need nothing but the tree (their inputs and the recorded
results of cm3 are in `tests`):

```sh
JDK25=/path/to/jdk-25 python3 tools/run_jvm.py p0 p1 p2 r0      # m3tests programs
JDK25=/path/to/jdk-25 python3 tools/run_jvm.py --compile c0 c1
JDK25=/path/to/jdk-25 python3 tools/diff_test.py                # against cm3: library tests, examples, Rosetta Code,
                                                                # edge cases, random expressions
JDK25=/path/to/jdk-25 python3 tools/split_test.py               # methods too large for the JIT
JDK25=/path/to/jdk-25 python3 tools/m3front_test.py             # CM3's front end, compiled by m3j, against m3j's
JDK25=/path/to/jdk-25 python3 tools/bundle_test.py              # m3bundle, compiled by m3j, against cm3's
JDK25=/path/to/jdk-25 python3 tools/build_test.py quake cases   # m3build: cm3's quake tests, packages of tests/build
JDK25=/path/to/jdk-25 python3 tools/bind_test.py                # m3jbind: bindings of Java classes
JDK25=/path/to/jdk-25 python3 tools/lsp_test.py                 # the language server (and the client of the
                                                                # extension for Visual Studio Code, with node)
JDK25=/path/to/jdk-25 python3 tools/perf.py                     # benchmarks (tests/bench)
JDK25=/path/to/jdk-25 python3 tools/dump_test.py fingerprint
JDK25=/path/to/jdk-25 python3 tools/dump_test.py middle
```

These need a cm3 installation or cm3 sources outside the tree:

```sh
JDK25=/path/to/jdk-25 python3 tools/pkg_gate.py --cm3 CM3DIR    # compile all packages of a cm3 installation
JDK25=/path/to/jdk-25 python3 tools/build_test.py exports packages --cm3 CM3DIR --src CM3SRC
                                                                # m3build on the m3makefiles of cm3's packages
JDK25=/path/to/jdk-25 python3 tools/perf.py --native            # also cm3 -O (cm3 on the PATH)
JDK25=/path/to/jdk-25 python3 tools/diff_test.py --record       # record the results of cm3 (cm3 on the PATH)
JDK25=/path/to/jdk-25 python3 tools/expr_fuzz.py --seeds 1-100  # random expressions against cm3 (cm3 on the PATH)
JDK25=/path/to/jdk-25 python3 tools/prog_fuzz.py --seeds 1-100  # random programs against cm3 (cm3 on the PATH)
JDK25=/path/to/jdk-25 python3 tools/cg_test.py DIR...   # needs cm3's m3cgcat
JDK25=/path/to/jdk-25 python3 tools/front_test.py PACKAGE... [--tree DIR]   # packages built by cm3
```

The coverage of m3j by these tests is measured with JaCoCo (unpacked below
`download`):

```sh
JDK25=/path/to/jdk-25 python3 tools/coverage.py --out DIR     # report in DIR/html
```

`tests/front` holds cm3 packages that cover parts of the language that
CM3's libraries and m3tests do not use; build each of them with
`cm3 -keep -commands > build.log 2>&1` (the package `safe` also with
`-DNOCHECKS`, in a copy) and pass the package directories to
`front_test.py`.

[`tools/README.md`](tools/README.md) lists the tools, [`docs/PORTING.md`](docs/PORTING.md) the rules for porting
CM3 sources, [`docs/BACKEND.md`](docs/BACKEND.md) the design of the JVM back end.

## License

The sources written for m3j are distributed under the MIT license in
[`LICENSE`](LICENSE); their head says so (`SPDX-License-Identifier: MIT`). The
sources ported or copied from CM3 keep their original license; where m3j
changed one, a line in its head says that the changes are distributed
under the MIT license in [`LICENSE`](LICENSE).

The ported CM3 sources are distributed under their original licenses, in
[`licenses/`](licenses) ([`COPYRIGHT-DEC`](licenses/COPYRIGHT-DEC) for the code from Digital Equipment
Corporation, [`COPYRIGHT-CMASS`](licenses/COPYRIGHT-CMASS) for the code from Critical Mass,
[`COPYRIGHT-COLUMBIA`](licenses/COPYRIGHT-COLUMBIA) for the code from Columbia University,
[`COPYRIGHT-LUCENT`](licenses/COPYRIGHT-LUCENT) for the notice of David Gay's conversion code,
[`MIT-BATES`](licenses/MIT-BATES) for `ArrayExpr`, and [`MIT-MCKINNA`](licenses/MIT-MCKINNA) for the language server).
The sources copied from CM3 keep their notices: `lib` (m3core, libm3, tcp,
netobj, sysutils, libbuf, and json with the notice of its author,
[`lib/json/COPYRIGHT-BSD`](lib/json/COPYRIGHT-BSD), see [`lib/README.md`](lib/README.md)), `tools/langserver/src`
(CM3's language server), `tests/m3tests`,
`tests/diff/src`, `tests/pkg`, `tests/net`, `tests/m3front` (CM3's front end, see
[`tests/m3front/README.md`](tests/m3front/README.md)) and `tests/stubgen` (CM3's stub generator for
network objects, see [`tests/stubgen/README.md`](tests/stubgen/README.md)), and `tools/m3bundle` (CM3's m3bundle).
