# Using m3j

How to build m3j, compile and run programs, call Java classes, build
packages with m3build and use an editor. The libraries are described in
[`LIBRARIES.md`](LIBRARIES.md), the tests in [`TESTING.md`](TESTING.md).

## Build

Needs a POSIX shell (Linux, macOS, WSL or Git Bash on Windows) and JDK 25 or later.

```sh
JDK25=/path/to/jdk-25 sh build.sh
```

writes `m3j.jar` (compiler, runtime and the Modula-3 library), `m3j-runtime.jar`
(only what a compiled program needs), `m3j-ssh.jar` (JSch for the library
ssh, which only programs that use it need), `m3bundle.jar` (cm3's m3bundle,
compiled by m3j), `m3build.jar` (the build system, see below) with its
configuration `m3j.cfg`, `stubgen.jar` (cm3's stub generator of network
objects, compiled by m3j; `M3J_STUBGEN=no` skips it), `m3_lang_server.jar`
(cm3's language server, built by m3build; `M3J_LSP=no` skips it) and the
AOT cache `m3j.aot`, which saves start time of the compiler and of
programs run with `m3j.jar` (`M3J_AOT=no` skips it).

## Compiling and running

The library of Modula-3 ([`LIBRARIES.md`](LIBRARIES.md)) is in the jar;
for it no cm3 installation is needed:

```sh
java -XX:AOTCache=m3j.aot -cp m3j.jar m3j.jvm.Compile Main.m3 -d out
java -cp m3j-runtime.jar:out m3.Main
java -cp m3j.jar m3j.jvm.Compile Main.m3 -run ARG...   # in memory
```

A program that uses the library ssh also needs `m3j-ssh.jar`:
`java -cp m3j-runtime.jar:m3j-ssh.jar:out m3.Main`. m3build adds it by
itself, and `-run` has it in `m3j.jar`.

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

A compiled program follows the conventions of the operating system that it
runs on (`Pathname`, `OSConfig`); `-Dm3j.os=POSIX` or `-Dm3j.os=WIN32` for
the JVM chooses one. The constants `Wr.EOL`, `OSConfig.LineSep`, `ArcSep`
and `PathSep` are fixed when the program is compiled: they are those of the
system that the compiler runs on (or that `-Dm3j.os` names for it).

# Java APIs

An interface can stand for a Java class. The program then calls its
methods, holds its objects in typed variables and catches its exceptions,
and the compiler checks the declarations against the class:

```modula-3
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
INTEGER): INTEGER;`. Bindings are declared in interfaces only; their
formals are VALUE formals, except arrays. A bound procedure is a procedure
value like any other: it can be assigned, compared (`=` holds across the
whole program), passed to Java as a callback and be the default of a
method. The class or member is looked up when the interface is compiled: a
class that is missing, a method without a fitting overload or an ambiguous
one (the message lists the descriptors) is an error at the declaration,
even if the program never calls it.

The public instance fields of a class are the fields of its type `T`:

```modula-3
<*EXTERNAL "java.awt.Point":JAVA*> INTERFACE Point;

IMPORT Java, JavaObject;

TYPE T <: JavaObject.T OBJECT x, y: Java.Int END;

PROCEDURE New (x, y: Java.Int): T;

END Point.
```

`p.x := 3` is then a `putfield`. The type of a field must fit as for a
static field, it has no default value, assigning to a `final` field is an
error, and the list has fields only: methods are procedures of the
interface.

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
| `ARRAY OF E`, `ARRAY [a .. b] OF E` (VALUE, READONLY, VAR) | the Java array of the same elements, as a copy (see below) |
| `REF ARRAY OF E`, as a result | the Java array, as a copy; `null` is `NIL` |

Results convert the other way round, with a cast where the Java type is
wider, and `void` is a proper procedure. `ISTYPE`, `NARROW` and `TYPECASE`
on the types of bindings are `instanceof`; a Java object is not a `ROOT`.
Scalars are not boxed for a parameter of type `Object`. `Java.i3` has the
boxes (`FromInt`, `FromLong`, `ToInt`), copies for Java arrays held in a
`REFANY`, and `ToText`, `Equals`, `HashCode`, `ClassName` and `IsInstance`
for any reference.

An array of one dimension is passed as a copy, and a VAR array is copied
back after the call; the cost grows with the array. The elements must be
of the same kind in both languages: `INTEGER` is `long`, `Java.Int` `int`,
`Java.Short` `short`, `Java.Byte` and `CHAR` `byte`, `WIDECHAR` `char`,
`BOOLEAN` `boolean`, `REAL` `float`, `LONGREAL` `double`, `TEXT` `String`,
`REFANY` `Object` and the `T` of a binding its class. Java passes an array
to a procedure or method as a copy in a VALUE or READONLY `ARRAY OF E`, and
gets a copy of a `REF ARRAY OF E` that it returns. Other arrays (several
dimensions, records) stay in a `REFANY`.

An object type can have the `T` of a binding as its supertype, and its
objects are then objects of the Java class:

```modula-3
TYPE
  Worker = JThread.T OBJECT                 (* extends java.lang.Thread *)
    count := 0;
  METHODS
    run () := Run;                          (* overrides Thread.run *)
    report (): TEXT := Report;              (* of Modula-3 only *)
  END;
  Chars = CharSequence.T OBJECT             (* implements java.lang.CharSequence *)
    s: TEXT;
  METHODS
    length (): Java.Int := Length;
    charAt (i: Java.Int): Java.Char := CharAt;
    subSequence (a, b: Java.Int): CharSequence.T := Sub;
    toString (): TEXT := ToText;
  END;
```

A method that the type declares (in `METHODS`) with the name of a public or
protected Java method that is not final or static implements it; its
signature must fit, or it is an error, and one that fits several overloads
equally is one too. Each name is one method of a type: for two overloads of
one Java method, declare the second in a subtype. `OVERRIDES`, `NEW (T, m :=
P)` and further subtypes reach Java as well. The class must not be final,
and needs a public or protected constructor without parameters, which is
the one that runs; `NEW` is an error if an abstract Java method is left
without an implementation. There are no calls of the overridden Java method
(`super`), a type has one Java supertype (a class or an interface), and
such a type is a subtype of `REFANY` but not of `ROOT`. Its objects cannot
be pickled. `NEW (Point.T)` of the type of a binding is an error: call a
constructor of the binding. Exceptions pass through Java as they do from a
callback.

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
started there is no such call: an exception or a checked runtime error
that nothing handles ends the program, as in a thread of `Thread.Fork`.
What a Java library catches itself stays with it: `Future.get` reports the
exception of a task of `ExecutorService.submit` as its cause.

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
for what it inherits. The public instance fields that a class declares are
the field list of `T`; a field is left out if its name is no identifier of
Modula-3 (with a warning) or if no type of Modula-3 can be assigned to it.
A Java array of a type with a name in Modula-3 is a `READONLY ARRAY OF E`
parameter and a `REF ARRAY OF E` result, with `CHAR` for `byte`; any other
array is `REFANY`. Where Java fills an array, change `READONLY` to `VAR`.
Parameters for callbacks are `REFANY` in the output: to pass a procedure,
write the formal with a procedure type by hand.

Not supported: several Java interfaces for one type, calls of the
overridden Java method (`super`), and arrays of several dimensions other
than in a `REFANY`.

# Packages: m3build

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
brings (`lib`: m3core, libm3, tcp, netobj, sysutils, libbuf, patternmatching, tls, http, xml, regexp, json, java, zip, encoding, crypto, ssh, sql), then in a cm3
installation (`-cm3 DIR` or `CM3_ROOT`). C sources and the options of
cm3's back ends are not supported; `-exports` writes only the
`.M3EXPORTS`. `java -jar m3build.jar -help` lists the options.

A package with bindings of Java classes does `import ("java")` and names
the jars or directories of the classes that are not those of the JDK:
`java_classpath ("../lib/foo.jar")`, relative to the file that says so.
The compiler gets them, the program runs with them, and it is compiled
again when one of the jars changes. A library passes its jars on: each is
installed beside the `.M3EXPORTS`, which names it, so that a package that
imports the library, directly or through another one, has it on its class
path. A directory cannot be passed on (pack it into a jar), and two jars
of a library must differ in name. The option `-pkg DIR` of the compiler
reads such a `.M3EXPORTS` too.

`java_option ("--enable-native-access=ALL-UNNAMED")` adds an option to the
java command of the scripts `JVM/NAME` and `JVM/NAME.cmd`, quoted for the
shell and for cmd, for example for a JDBC driver that loads native code. It
counts in the m3makefile of the program only, not in a package that the
program imports; an option may contain neither a quote nor a line end.

# Editors: the language server

`m3_lang_server.jar` is cm3's language server for Modula-3, compiled by
m3j: through the Language Server Protocol, an editor gets the errors and
warnings of a file while it is edited, the type of an identifier (hover)
and the place of its declaration and of the definition of its type, from
m3tk, cm3's toolkit for Modula-3. It is started as
`java -jar m3_lang_server.jar` (with `m3j.jar` and `m3build.jar` next to
it) and checks the files of packages (below a directory `src` with an
`m3makefile`), whose imports it gets from m3build, and those of the library
(`lib`). [`tools/langserver/vscode`](../tools/langserver/vscode)
is an extension for Visual Studio Code (syntax highlighting and a client
of the server, without other packages); [`tools/langserver/README.md`](../tools/langserver/README.md) has
the settings for Neovim, Emacs (Eglot), Helix and Kate.
