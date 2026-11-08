# m3j: the JVM back end — design rules

The back end translates the tree that the front end (`m3j.front`) has
checked into JVM class files, using the Class-File API
(`java.lang.classfile`, JDK 25). It is new code, not a port. CM3 stays the
reference for the meaning of every construct: where the language report
leaves something to the implementation (evaluation order, which checks are
made, the text of runtime errors, the results at the edges of the number
ranges), the back end does what a program compiled natively by cm3 for
`AMD64_NT` does.

The goal is bytecode that the JIT compiles well: unboxed values, static
calls, small methods, checks as plain compares. The representation of
values is decided here, before code is written, because it is the part
that is hard to change later.

Packages:

- `m3j.jvm`: the compiler (analysis passes, code generators, driver,
  linker);
- `m3j.rt`: the runtime library, shipped as its own jar
  (`m3j-runtime.jar`), so that a compiled program needs only the runtime
  and not the compiler.

The safe language only. UNSAFE interfaces and modules are rejected, as
before; the front end's own import of `RTHooks` does not count.

## 1. Pipeline

1. **Front end.** `M3Front.compile` runs as it does for the comparison with
   cm3 (`checkBuiltins`, parse, type check, compile to M3CG). The M3CG
   phase is kept because some diagnostics come only from it ("unreachable
   statement", "function may not return a value"); its output goes to an
   `M3CG_BinWr` on `OutputStream.nullOutputStream()`. A callback receives
   the checked `Module.T` of each unit while its session is still current:
   the next unit resets scopes, makes other interfaces current and reuses
   revelations, so the back end must be done with a unit before the front
   end goes on.
2. **Analysis** of the tree of one unit (`Frames`): the procedures and
   their nesting; the variables that nested procedures use (the front
   end's `up_level` flag) and so belong in frames; which nested procedures
   need a static link (they, or a procedure they use, need a variable of
   an enclosing procedure); and the scalar variables passed `VAR`, which
   live in cells. The bounds of expressions (`Expr.getBounds`) decide
   during code generation which checks can be left out.
3. **Code generators**, split by area: units, declarations and types,
   statements, expressions, calls, and the aggregates (records, arrays,
   sets). They match on the front end's node classes with pattern
   switches; the node classes are public for that.
4. **Size control.** Every class is generated, read back with
   `ClassFile.parse`, and its methods are measured (section 11).
5. **Link** (the replacement for m3linker), when a program is put
   together: it checks the interface fingerprints, computes the order of
   initialization along the import graph, writes the link table
   (interface procedure → exporting class and method) and the `main`
   class, and keeps one copy of every type class (section 3).

The driver `m3j.jvm.Compile` finds `.i3` and `.m3` files on a search path,
so it needs no cm3 build log. Generics are instantiated by the front end.
Output: `-run` (load and run in memory), `-d DIR` or `-d prog.jar`.

The target description stays `AMD64_NT`, so `BITSIZE`, `BYTESIZE`,
`FIRST`, `LAST`, `Word.Size` and the values of constant expressions are
those of the reference. `WIDECHAR` has 16 bits.

## 2. Values

Every type has a JVM representation on the stack and one in storage
(fields and array elements):

| Modula-3 | On the stack, in locals | In storage |
|---|---|---|
| `INTEGER`, `CARDINAL`, `LONGINT`, `Word.T`, `Long.T` | `long` | `long` |
| subranges inside `int` | `int` | the smallest type that holds the range: `byte`, `short`, `char`, `int` |
| subranges outside `int` | `long` | `long` |
| `BOOLEAN` | `int` (0, 1) | `boolean` |
| `CHAR` | `int` | `byte` (read with `& 0xFF`) |
| `WIDECHAR` | `int` | `char` |
| enumerations | `int` (the ordinal) | `byte` up to 256 elements, else `short`/`int` |
| `REAL` | `float` | `float` |
| `LONGREAL`, `EXTENDED` | `double` | `double` (`EXTENDED` is 64 bits on `AMD64_NT`) |
| sets up to 64 elements | `long` (bit mask) | `long` |
| larger sets | `long[]` | `long[]`, owned |
| `TEXT` | `String` | `String` |
| `REFANY`, `REF T`, objects | a class (section 3) | the same |
| `NULL` | `null` | — |
| procedures | a functional interface (section 5) | the same |
| records | a class per type (section 3) | owned object |
| fixed arrays | storage and offset (section 3) | owned storage |
| open arrays | storage, offset, shape (section 3) | — |

Rules for numbers:

- Integer arithmetic wraps, as in CM3, which does not check overflow.
  `int` arithmetic is used only where `Ranges` shows that the `long`
  result would be the same; otherwise values are widened.
- `DIV` and `MOD` are `Math.floorDiv` and `Math.floorMod` (with a constant
  divisor the JIT turns them into multiplications).
- `Word` and `Long` operations use the unsigned methods of `Long`
  (`compareUnsigned`, `divideUnsigned`, `remainderUnsigned`), which are
  intrinsics. Java masks shift counts to 6 bits, but `Word.Shift` with a
  count of 64 or more (either sign) gives 0, so a count that is not a
  constant gets a guard. `LeftShift` and `RightShift` take counts in
  `[0..63]`, checked like any subrange parameter. `Word.Rotate` is
  `Long.rotateLeft`/`rotateRight`, whose masking is the `MOD` of the
  definition.
- `ROUND` rounds halves away from zero, like C's `round`, not like
  `Math.round`. `TRUNC`, `FLOOR`, `CEILING` and `ROUND` of a value that
  does not fit in `INTEGER` (and of a NaN) give `FIRST(INTEGER)` in the
  reference, whose conversion instruction returns that value, while the
  JVM saturates and maps NaN to 0; a runtime helper corrects the result.
- Subrange checks on assignment, on value parameters and on `VAL` are
  emitted where CM3 emits them (`AssignStmt`, `Formal`, the built-ins),
  and left out where `Ranges` proves them unnecessary. Like cm3, the
  back end makes no checks with `-NoChecks`.

## 3. Types

**Type classes.** Records, reference types and object types become
classes named after the type's UID (the fingerprint-based number of
`Type.globalUID`), in a package of their own. Structurally equal types
have the same UID in every unit, so every unit that needs the class can
generate it, the copies are identical, and Link keeps one. This gives
Modula-3's structural equivalence for `TYPECASE`, `NARROW`, `ISTYPE` and
typecodes without a registry at run time.

**Records.** One class with typed fields in storage representation.
A field of record or fixed array type holds an owned object that is
created with the record and never replaced. Generated methods:
`copyFrom(other)` for assignment and value parameters, `eq` for `=` and
`#`, and the methods of the interface `m3j.rt.Rec` for arrays of records.
Records have value semantics: a record variable holds its own object for
its whole life; assignment copies into it. The default values of fields
are not part of the class: record types that differ only in their
defaults have the same UID, so the defaults are set where a variable or
a `NEW (REF R)` is created, from the declared type.

**Fixed arrays.** Flat, row-major storage of the element's storage type:
`ARRAY [0..9] OF ARRAY [0..9] OF INTEGER` is a `long[100]`, an array of
`CHAR` a `byte[]`. A designator of an array or of a row of one is a pair
(storage, offset) with a shape known at compile time, so `a[i]` of a
two-dimensional array is an offset, not an object. Arrays of records are
`R[]` with all elements created with the array. Assignment and `=` use
`System.arraycopy` and `Arrays.equals`/`Arrays.mismatch` on ranges, which
are intrinsics. `=` on arrays and records gives what cm3 gives: a fixed
array, and a record without holes, compares as its bits (for REAL types
0.0 and -0.0 differ and a NaN equals itself); where cm3 compares element
by element, the elements of a REAL type compare as numbers: if one of
the arrays is open (also `SUBARRAY`), and for the fields of a record with
holes (`Type.Info.isSolid` of the front end).

**Open arrays.** A formal of an open array type is passed as storage,
offset and one `int` per open dimension, so `SUBARRAY` and the rows of a
multi-dimensional array are passed without a copy. `REF ARRAY OF T` is an
object that holds the storage and the dimensions. Every assignment
between arrays of different static shapes checks the shape
(`IncompatibleArrayShape`). `NEW` with a negative length is a runtime
error, and the product of the dimensions is checked for overflow of
`int`.

**Sets.** Up to 64 elements: a `long` bit mask, bit 0 for `FIRST` of the
base type. Larger sets: `long[]` with value semantics like a fixed array.
`IN` with an element outside the base range is FALSE; `{a..b}` with
`a > b` is empty.

**References.** One class per `REF T` type. `REF R` for a record type `R`
is a subclass of the class of `R`, so `p^.f` is one field access, not two.
`REF` of other types holds the referent in a field. `REFANY` is `Object`,
`NIL` is `null`.

The classes of the types that a unit is the first to use are made after
the class of the unit (`Types.flush`). When the stack maps of a method
need the superclass of such a class, because paths join where a variable
holds references of different types, the class hierarchy that the class
file API asks (`Types.resolver`) makes the classes that are waiting at
that moment.

**Objects.** The class hierarchy follows the supertypes; `ROOT` is a
class in `m3j.rt`. Methods are JVM virtual methods whose names carry the
UID of the type that declares them, so that a method of a subtype with
the same name as a hidden method of a supertype never overrides it by
accident. An override in a subtype overrides the JVM method of the
declaring type. The body of a JVM method calls the bound procedure
statically; a method that is NIL raises the runtime error of the
reference. `NEW (T, m := P)` creates an instance of a subclass generated
for that expression.

**Opaque types.** The class of the type revealed for an opaque type is
named after the opaque type's UID (`OpaqueType`). A client that sees only
a partial revelation names that class and its fields and methods only
symbolically; the JVM resolves them lazily, so no offsets are needed at
compile time. Only the unit with the full revelation generates the class.

**TYPECASE, NARROW, ISTYPE.** `NIL` belongs to every reference type, so
the test for `null` comes first. Then `instanceof` and `checkcast`; a
`TYPECASE` with many arms uses `SwitchBootstraps.typeSwitch`. `TYPECODE`
and the typecode functions use a `ClassValue` registry.

**TEXT.** `java.lang.String`. `&` uses `StringConcatFactory`.
`Text.FromChars` and `Text.SetChars` use ISO-8859-1. Units that import
`TextClass` (which exposes the representation of texts) are rejected.

**Structured constants** are built where they are used, like a
constructor. An array constant whose elements are numbers, characters,
booleans or small sets (also nested in fixed arrays) is a dynamic constant
(`condy`) instead, made once from strings that hold its elements; a named
constant uses it directly (no one writes it), a constructor in an
expression gets a copy (`clone`). So large tables need no code. The
elements of `..` in a constructor are copied by a loop when there are more
than a few. A constructor in which the front end found a checked runtime
error (a value out of range, too many or too few elements) raises it
where it is used, as cm3 does.

## 4. Variables and parameters

**Variables.** A local variable is a JVM local, unless an inner procedure
uses it (then it is a field of a frame, section 5) or it is passed as a
`VAR` parameter and so needs a reference (then it lives in a cell for its whole life, so a call
does not allocate). Module variables are static fields of the module's
class; variables declared in an interface are static fields of the
interface's class.

**Value and READONLY parameters.** As in CM3, the caller copies records,
fixed arrays and large sets passed by value; the copy is left out for a
fresh value (a constructor, a call result, `NEW`). Open arrays passed by
value are copied by the callee. A `READONLY` parameter is passed by
reference when the actual is a designator of the same type, as the
language requires (a change through an alias is visible in the callee);
otherwise a copy is passed. For scalars this is a reference object as for
`VAR` (below), holding a copy if needed. Every parameter has its exact
JVM type in the descriptor.

**VAR parameters.**

- Records, fixed arrays and large sets: the object, or (storage,
  offset). No wrapper is needed, because the callee writes into it.
- Scalars, including references, objects, `TEXT`, procedures and small
  sets: a typed reference, one abstract class per JVM type (`LongRef`,
  `IntRef`, `DoubleRef`, `FloatRef`, `ObjRef`) with final subclasses: a
  cell (`LongCell`, ...: a local variable passed `VAR` lives in a cell for
  its whole life), an element reference per storage kind (`LongElem`,
  `UByteElem`, ...), and a reference class generated per record, object,
  frame or static field. Escape analysis removes the allocation of an
  element or field reference when the callee is inlined.
- `INC`, `DEC` and assignments never create references.

**WITH.** The parts of the designator (container, offset, index) are
evaluated once into locals; the body works on them directly. A `WITH` on
a value that is not a designator is a local of the value's type.

## 5. Procedures

**Top-level procedures** are static methods of the unit's class.

**Nested procedures** are static methods too. The variables of a
procedure that nested procedures use (the front end marks them
`up_level`) are fields of a frame class generated for that procedure;
the variables passed `VAR` live in cells. A nested procedure takes the
frame of the nearest enclosing procedure that has one (its static link)
as its first parameter, but only if it, or a procedure it uses, needs a
variable of an enclosing procedure; frames are linked through a field
`up`. A nested procedure that needs nothing of its parents is an
ordinary static method.

**Procedure values.** One functional interface is generated per JVM
descriptor (procedure parameters are `Object` in it). A top-level
procedure as a value is made by `LambdaMetafactory` in an accessor method
of its unit (`$proc$NAME`, reached from other units like an interface
procedure), so the same procedure is always the same object. A nested
procedure as a value is an object of a closure class generated for it,
which holds the static link and implements the marker interface
`m3j.rt.Nested`; procedure values are compared with `equals`, which for
closures compares procedure and frame. Assigning a nested procedure is
the checked runtime error of cm3 (statically, or with the marker when the
value comes from a formal).

**Calls between units.** A call of a procedure of an interface goes
through `invokedynamic`; the bootstrap method looks the procedure up in
the link table and returns a `ConstantCallSite` for the exporting
module's static method, which the JIT treats like `invokestatic`. The
exporting module itself, and calls inside one unit, use `invokestatic`.
The indirection is needed because which module exports an interface is
known only when the program is linked.

## 6. Modules and initialization

A module is a class with static fields for its variables, static methods
for its procedures, and an explicit `init` method for its body (not
`<clinit>`: the order of initialization must be the one of the import
graph, as computed by Link, not the order in which the JVM first touches
a class). An interface is a class with static fields for its variables
and an `init` method for their initial values. The `main` class that Link
writes calls the `init` methods in order and then runs the program in a
thread with a large stack.

The order is the one of cm3's RTLinker: a unit depends on the interfaces
it imports and exports and on the modules that export the interfaces it
imports, and the units of a cycle of imports (a strongly connected
component) come after all units the cycle depends on. Within a cycle,
the interfaces come first, each after the interfaces it imports, since
the modules of the cycle may use their variables, which cm3 has in
static memory but m3j allocates in the `init` method of the interface;
the modules follow in the order in which the search finished them.

`<*EXTERNAL*>` procedures (the safe ones the tests and the library use,
such as `Cstdlib.exit`) are found by their external name in a registry of
Java implementations; a name that is not there is a link error.

## 7. Exceptions and runtime errors

**Exceptions.** Every `EXCEPTION` declaration becomes a class without a
stack trace (`writableStackTrace` false) that carries the argument and
the position of the `RAISE`. `TRY EXCEPT` becomes entries of the
exception table; `TRY FINALLY` is generated as javac does it (the
`FINALLY` part on every exit, including `RETURN` and `EXIT`). A jump out
of nested `TRY` statements runs their `FINALLY` parts from the inside
out; the handlers of the statements it leaves do not protect these parts
and the jump (an exception raised in an outer `FINALLY` part is not
caught by an inner handler), and protect the code again after the jump.
The language defines `EXIT` and `RETURN` as exceptions, so the `ELSE` of
a `TRY EXCEPT` catches them as cm3 does: a jump out of the body goes to
the `ELSE` handler, and then on after the `TRY`. A `RAISES`
clause (and its absence, which means `RAISES {}`) becomes a handler around
the procedure body that turns an exception not in the list into the
checked runtime error of cm3 ("Exception "X" not in RAISES list", at the
place of the `RAISE`; like any runtime error, `EXCEPT ELSE` catches it);
runtime errors pass. The handler is left out when every `RAISE` and every
call in the body can only raise exceptions of the clause (a procedure
called checks its own); it costs nothing until an exception is thrown.

**Runtime errors.** Checked runtime errors raise `RuntimeError.E` with the
error code (`<*IMPLICIT*>`, so it is never in a `RAISES` list). A program
that does not handle it ends with the message and the exit status of the
reference:

```
***
*** runtime error:
***    Unhandled exception: Main.a
***    file "../Main.m3", line 13
***
```

The lines come from the `LineNumberTable` and the file from `SourceFile`.
As in cm3, a `CASE` or `TYPECASE` without a matching arm reports the line
of the last statement compiled before the check (that of the last arm),
and a range error of `SUBARRAY` is `ValueOutOfRange`. `Process.Crash`
prints its message as cm3 does (`*** message` after an empty line).
JVM exceptions that stand for a checked runtime error
(`ArithmeticException`, `IndexOutOfBoundsException`,
`NullPointerException`, `ClassCastException`, `StackOverflowError`) are
mapped to `RuntimeError.E` wherever Modula-3 code can see them: in
`EXCEPT ELSE`, `FINALLY`, `LOCK` and at the top of the program. Before the
program exits, the procedures registered with `Process.RegisterExitor`
run and `Stdio` is flushed.

## 8. Threads

- `Thread.T` is a platform thread with a large stack; virtual threads are
  an option.
- `MUTEX` (`m3j.rt.Mutex`) is a lock that knows its holder and is not
  re-entrant: locking a mutex the thread already holds is the error of the
  reference, not a second hold count. Readers and writers lock their mutex
  for every character, so locking is cheap: while the program has not
  forked a thread, the holder is set and cleared with plain writes; after
  the first `Thread.Fork`, it is taken with a compare-and-set, and a thread
  that finds it held waits on the mutex's monitor until a release notifies
  it.
- `LOCK m DO ... END` acquires, runs the body in a `try`, and releases on
  every exit.
- `Thread.Condition` has its own queue of waiting threads.
- `Thread.Alert` is a flag per thread plus `Thread.interrupt`, so that
  `AlertWait`, `AlertJoin` and `AlertPause` wake up. The flag is the
  alert: those procedures test it before they wait and take it when an
  interrupt ends the wait. `Alert` sets the flag before it interrupts, so
  an interrupt that finds the flag clear belongs to an alert that
  `TestAlert` has taken in the meantime, and is ignored. `Wait`, `Join`
  and `Pause` leave the flag as it is.
- Typecodes are given on first use, once per type even when threads use
  a type for the first time at the same moment.

## 9. Runtime library

The library is hybrid:

- **Written in Java** (package `m3.i`), the interfaces that are UNSAFE in
  CM3, talk to the operating system, or depend on the representation of
  `TEXT`: `Text`, `Fmt` (with CM3's formatting of real numbers, ported
  from `FmtBuf`), `Convert`, `RTIO`, `RTProcess`, `Params`, `Env`, `Time`,
  `Thread`, `RuntimeError`, `RealFloat`, `LongFloat`,
  `ExtendedFloat`, `FloatMode`, `Math`, `RTType` (without `Get`),
  `RTTypeFP`, `Real`, `LongReal`, `Extended`, `IEEESpecial`, `Swap`,
  `Tick`, `OSConfig`, `OSError`, `M3jOS` (the operating system, below),
  `M3jPickle` (for `Pickle`, below), `Scheduler`, `RTCollector` (the
  collector of the Java platform runs on its own: disabling it or its
  motion has no effect; `Collect` asks it for a collection) and
  `RTTypeSRC` (`TypeName`, `TypecodeName`).
  `RTIO` writes to the standard error stream as cm3's module does; its
  `PutE`, `PutF` and `PutG` write to the standard output as C's `printf`
  with `%e`, `%f` and `%g` (the exact value rounded half to even; cm3 on
  Windows has none of these, nor `PutBytes`, whose C file is empty
  there); a safe program has no address but `NIL`, so `FmtAddr` and
  `PutAddr` show a reference by its identity hash as `PutRef` does, and
  reading memory at an address (`PutChars`, `PutBytes`) is a bad memory
  reference. `RTProcess.OnInterrupt` installs a handler of the signal
  `INT` of the Java platform (`sun.misc.Signal`), which calls the handler
  of the program as cm3 does on Windows (removed while it runs, and
  installed again); `Fork` returns -1, since the Java platform cannot
  fork the process, and fork handlers are never called.
  `RuntimeError.Raise` raises `E`, and `Self` returns `NIL` (the runtime
  has no descriptors of exceptions, `RT0` is UNSAFE);
  `Swap.Swap8` swaps the halves of an `Int64On32` record, made by
  reflection on the program's class of the type.
  `FloatMode` follows its specification, not cm3, which fails an
  assertion in every procedure but `GetRounding` on `AMD64_NT`: the
  rounding mode is `NearestElseEven`, no flag is ever set (the
  floating-point operations of the JVM and integer overflow have the
  behavior `Ignore`; `DIV` and `MOD` by zero, a checked runtime error,
  are reported as `Trap`), and a request to set anything else raises
  `Failure`.
  For `RTTypeFP` every class of a type has a constant
  `M3$FP` with the fingerprint of the type (the same as cm3's), and the
  main class lists the classes of all types (`M3$TYPES`, continued in
  `M3$TYPES1`, ... when the list does not fit in one constant).
  For `Pickle` every class of a record, reference or object type also has
  a constant `M3$PKL` (made by `m3j.jvm.Layouts`): the layout of the
  referents of the type in the memory of cm3 on `AMD64_NT`, taken from
  the front end (the offset, size and kind of every field; for an object
  type the fields of its own level). From it `m3.i.M3jPickle` makes the
  bytes of a referent as cm3 keeps them, with the slots of its references,
  and sets the fields from such bytes; so `Pickle` (a safe version of
  cm3's module in the library) writes and reads the same pickles as cm3,
  and so does `Pickle2` (safe versions of `Pickle2`, `PickleStubs` and
  `BuiltinSpecials2`, and `RTPacking` with the packing of AMD64_NT: on
  AMD64_NT cm3's converters of the packing write the data of a referent
  as `Pickle` does).
  For `RTTypeSRC` the class of a reference or object type that a
  declaration names has a constant `M3$NAME` with the name that cm3 puts
  in the type cell (`M3AST_AS.EXP`); another type is `<anon type>`.
  Such a class is marked by a static field `M3$PROVIDED`; the compiler
  does not generate a class for its interface, calls its procedures with
  `invokestatic`, and maps the opaque types of the interface to its nested
  classes (`Thread.T` is `m3.i.Thread$T`). In its descriptors the classes of
  Modula-3 types are `Object`, except these nested classes of its own
  opaque types (`M3jPickle.NewTable` returns `m3.i.M3jPickle$RefTable`).
  `<*EXTERNAL*>` procedures are methods of
  `m3j.rt.Externals`, named after their external names.
- **Compiled by m3j**, the other modules of libm3 and m3core: when a
  program imports an interface that the runtime does not provide, the
  driver compiles the module of the same name (or, from a package of a
  cm3 installation, `NAMEWin32` if it exports the interface, or, from such
  a package, one given with `-pkg` or the library of m3j, the safe
  modules of the package that export it under other names, as
  `M3PathElemWin32` of m3tk exports `M3PathElemOSPriv` and two modules of
  netobj together export `NetObjMonInit`) from the library
  of m3j (below) as well, on trial: if it is UNSAFE or does not compile, it is left out, and
  the driver warns if the interface declares procedures that then have no
  implementation, or variables and has a module that is left out (an
  interface of variables alone needs no module: it initializes them
  itself). So the generic tables, lists and sequences,
  `Atom`, `Scan`, `Pathname` and the like come from their sources, and so
  do the readers and writers: `Wr`, `TextRd`, `IO`, `RdCopy`, `NullRd`,
  `NullWr`, `AutoFlushWr` with the interfaces `RdClass`, `WrClass`,
  `UnsafeRd` and `UnsafeWr`, so that programs can define readers and
  writers of their own as with cm3. The library reports such a unit as cm3
  does, by its path relative to the build directory of its package
  (`../src/rw/IO.m3`).
- **The library of m3j** (`lib`, a resource of the jar, described in
  `lib/README.md`): the units of m3core and libm3 that the programs and
  tests of m3j use, and the packages tcp, netobj and sysutils (interfaces,
  generic units and safe modules), copied
  from cm3 with the structure of its packages, without the UNSAFE modules
  and the modules of the interfaces that the runtime implements in Java;
  so such a program needs no cm3 installation. A
  cm3 installation (`-cm3`) or other packages (`-pkg`) come after it.
  Some modules of cm3 that are UNSAFE or use the representation of `TEXT`
  of m3core are changed there to safe code with the same results: `TextConv` and `TextExtras` (`Text.FromChars` instead of
  `Text8`), `RandomReal` (the number computed with `Scalb` instead of
  packed with `LOOPHOLE`), `TimeStamp`, `Rd` and `TextWr` (`Text.FromChars`
  instead of `Text8`), `FileRd` and `FileWr` (their buffers read and written
  as characters through `M3jFile` instead of through a `LOOPHOLE` to bytes),
  `Stdio` (the standard files from `M3jFile`), `MD5` and `SHA256` (the
  digests of the Java platform, `M3jHash`), and `Date` and `FS` on top of
  interfaces of `<*EXTERNAL*>` procedures (`M3jDate`, `M3jFS`) implemented
  with the Java platform. `M3jFile` reveals `File.T` and `RegularFile.T` as
  handles of `M3jFS`; the handles 0, 1 and 2 are the standard streams.
  As in cm3, reading a handle that is not readable or writing one that is
  not writable (a file opened read-only, the standard input) is a checked
  runtime error (an exception blocked by a RAISES clause, which cm3 names
  `FileWin32.IllegalDirection` and m3j `M3jFile.IllegalDirection`), and
  `FS.Rename` replaces a file of the new name.
  `Process` is a module of the library on `M3jProc` (`m3j.rt.Proc`), and
  `Pipe.Open` makes a pipe of `M3jFS`, a channel in the JVM that counts
  the references to its ends. A file handle given to a child process for
  its standard input, output or error output reaches it so: a standard
  handle in its own place is inherited; the file of a regular file handle
  is opened by its name (the child reads it from the position of the
  handle and writes an empty file, and when `Wait` returns, the handle is
  at the end of the file, as if the child had shared it; the output and
  the error output to the same file are one stream); the output to a file
  that is not empty goes through a thread that writes it at the position
  of the handle (the Java platform opens a file to append to it only for
  appending, and programs of MSYS and Cygwin on Windows, such as those of
  Git, cannot write such a handle); other handles (the
  ends of pipes) are connected by a thread that copies the bytes and holds
  a reference to the end of the pipe until it is done, as the system
  would hold the handle that a child inherits; without a handle the child
  reads an empty input and cannot write. Each parameter reaches the child
  as one (cm3 on Windows joins them with spaces, without quotes, and ends
  the command line with a space). The Java platform cannot change the
  working directory of the process, so m3j keeps one for the program
  (`SetWorkingDirectory`): `M3jFS` resolves relative pathnames against it,
  and children start in it.
  The package tcp of cm3 (`lib/tcp`) has safe modules `TCP` and `IP` on
  `M3jNet` (`m3j.rt.Net`): each socket is a channel of the Java platform
  that does not block, with two selectors, one to wait for reading,
  accepting and connecting and one to wait for writing, so that a thread
  that reads a connection and one that writes it do not wait for each
  other. A wait that may be alerted returns when `Thread.Alert`
  interrupts it (`Thread.alertPending` tells the runtime that the flag is
  set); one that may not keeps the interrupt for later. The errors are
  the numbers of Windows Sockets, as cm3 puts them into the lists of
  atoms of `IP.Error`, `Rd.Failure` and `Wr.Failure`.
  The package sysutils of cm3 (`lib/sysutils`) has the modules of cm3 for
  Windows and for POSIX merged into modules for both (`PathRepr`,
  `OSSpecials`), and its UNSAFE procedures are safe ones in `System`
  (`Hostname` from `M3jNet`) and `FSUtils` (`IsReadable`, `IsWritable`,
  `IsExecutable` from `M3jFS.Access`).
  Because `Stdio` registers its flush with `Process.RegisterExitor`, the
  exitors also run when a program ends with a runtime error, as with cm3.
- **The operating system.** cm3 builds a program for the system of its
  target; a program of m3j runs on any JVM and follows the conventions of
  the system it runs on (`m3j.rt.OS`: Windows or POSIX, or the one that
  the property `m3j.os` of the JVM names, `WIN32` or `POSIX`). `Pathname`
  (`libm3/src/os/Common/Pathname.m3`, new) calls cm3's `PathnamePosix` or
  `PathnameWin32`, which are both in the library, changed to export
  interfaces of their own, after `M3jOS.Posix ()`, and the procedures of
  `OSConfig` report the system the program runs on. The constants of
  `OSConfig` (`ArcSep`, `PathSep`, `LineSep`, and with it `Wr.EOL`) are
  constants of the language, which the front end folds into the code:
  they are those of the system that the compiler runs on, which takes, of
  a unit that the library has in a directory `POSIX` and in one `WIN32`
  (`OSConfig.i3`), the one of its system. Everything else stays as on the
  target `AMD64_NT` on every system: sizes, alignments and layouts, and
  the messages and exit status of runtime errors.

Java code that calls compiled code (a `Thread.Closure`'s `apply`, a
procedure value of type `PROCEDURE ()`) looks the method up by its name
pattern, because method names and type classes are named after UIDs.
`MUTEX` is `m3j.rt.Mutex`. The interfaces that the front end imports by
itself (`RTHooks` and others) stay on the search path.

## 10. Code that the JIT compiles well

These rules come from the experience with a similar back end:

- **Methods under 8000 bytes.** HotSpot does not compile larger methods
  at all (`HugeMethodLimit`), so they run in the interpreter forever, and
  the JVM does not load methods over 64 KB. The size is measured, never
  estimated: a procedure (or a module body) whose statements are large
  is generated into a scratch class and parsed; if its method is larger
  than 8000 bytes, runs of statements become methods of their own
  (chunks, `Split`). A chunk takes the frame of the procedure, which then
  holds every variable that a chunk uses but does not declare (formals
  too, as for nested procedures), and returns 0 when its statements
  complete, 1 after a `RETURN` (the value is in a field of the frame) and
  2 after an `EXIT` of a loop around it; the caller then returns or exits
  itself, through its own `FINALLY` parts. Runs that use a `WITH` name
  bound outside them are not cut out. A statement list of up to half the
  limit becomes one chunk; a statement that is still too large (a `CASE`
  with many arms) gets a chunk per arm. The plan is made with estimates,
  all methods are measured, and the plan is made again with half the
  limit until every method fits (if none fits, the plan with the smallest
  largest method is taken). `tools/split_test.py` tests this with
  procedures of 90 KB and an interpreter loop with 300 arms. For tests,
  the system property `m3j.maxMethod` of the compiling JVM lowers the
  limit of 8000 bytes (`java -Dm3j.maxMethod=200 ... m3j.jvm.Compile`):
  then small procedures are split too, and any program tests the chunks.
  At such limits a procedure of thousands of statements may still have
  methods over the limit, since the calls of its chunks stay in one
  list, but the program is correct.
- **Module bodies run no hot code.** A module body runs once, so the JIT
  could compile its loops only by on-stack replacement, without a profile
  for the code after the loop. Every loop in an `init` method therefore
  becomes a method of its own.
- **Checks are compares.** Range and subscript checks are inline compares
  (`Objects.checkIndex(long, long)` before `l2i`), left out where `Ranges`
  proves them unnecessary.
- **FOR loops** with an `int` counter when the counter, the limit and the
  step fit in `int` and the last increment (`limit + step`) cannot
  overflow; otherwise with a `long` counter and CM3's order (increment,
  then test). Such loops are counted loops for C2.
- **CASE** becomes `tableswitch` or `lookupswitch` when the labels are
  dense and in the `int` range (after a range test), else a chain of
  compares. A value without a matching label and no `ELSE` is the runtime
  error of the reference.
- **Exact parameter types.** Every parameter has its final JVM type, not
  `Object` and a cast.
- **Array descriptors in locals.** In a loop, the storage, offset and
  length of a `REF ARRAY` are kept in locals, so the JIT can hoist the
  bounds checks.
- **Start-up.** The runtime is shipped with an AOT cache (JEP 483, 514,
  515).

Performance is measured with an A/B tool: two versions are run
alternately, five runs each, the median counts, no other JVM runs at the
same time, and compile time is not included. `-XX:+PrintCompilation` and
JFR show whether the hot methods are compiled by C2.

## 11. Testing

- The front end must give the same results as before on the whole corpus
  (`tools/front_test.py`) after every change to it.
- Every generated class must pass `ClassFile.verify`. Every unit of the
  compile tests `c0` and `c1` of m3tests must give classes that verify.
- `tools/run_jvm.py` compiles and runs the safe programs of `p0`–`p2`
  and `r0` with m3j and compares stdout and stderr with the expected
  files of m3tests (their variants for 64-bit little-endian targets where
  there are such). These files, not a program compiled by cm3 here, are
  the oracle: the run of m3tests with cm3 on AMD64_NT matched them except
  for known failures of cm3's C back end (see `MODULA3-JVM.md`).
  `run_jvm.py --compile c0 c1` is the gate for the compile tests.
- `tools/diff_test.py` compares m3j with cm3 on programs from outside
  m3tests (recorded once with cm3); `tests/diff/src/regress` has small
  programs for errors of m3j that other tests found, `tests/diff/src/check`
  programs that test the formatting and scanning of numbers, the
  floating-point operations, pickles and pathnames at their edges, and
  threads under load (`check/threads`, and `check/threads-10` with ten
  times the work; their output does not depend on the order in which the
  threads run), the checked runtime errors (`check/rterrors`: each one
  caught by a handler for `RuntimeError.E`, and with an argument one of
  them, or an error that the runtime reports, ending the program), and
  set constructors with bounds known only at run time and `=` on arrays
  and records of every kind of element (`check/sets-arrays`), and the
  file system interfaces at their edges (`check/filesystem`), the units
  that the library brings for programs (`check/libunits`: types,
  `FmtTime`, geometry, `Sx`, priority queues, properties, readers and
  writers, generic instances), child processes and pipes
  (`check/process`: exit codes, the standard handles,
  pipes to and from a filter also with much data, regular files, the
  environment and the working directory of a child, and
  `SetWorkingDirectory`), the package sysutils (`check/sysutils`: texts,
  environments, files and directories, pathnames, messages, and child
  processes with redirections, lists of commands and pipes), bundles
  and resources (`check/bundle`: `Bundle` and `Rsrc` with the bundle that
  cm3's m3bundle makes of three files, and a directory; that m3j's
  m3bundle writes the same bundle, `tools/bundle_test.py` tests), weak
  references (`check/weakref`; what depends on the collector, which
  cm3's conservative collector does differently from run to run, is
  checked by hand in `check/weakref-hand`: dead weak references, cleanup
  procedures, a node that its cleanup makes reachable again), the
  pickles of `Pickle2` (`check/pickle2`: the values of `check/pickle`,
  `PickleStubs`, specials registered again and for pseudo pointers,
  pickles of `Pickle` and of machines with other packings),
  `Text.ForAllDo` (`check/text-forall`: m3j calls a callback once for
  the whole text, cm3 once for each piece of a concatenation, so the
  test compares the number of calls only for single literals), and
  `tests/diff/src/fuzz` random programs of the two generators below
  (`fuzz.list`, `prog.list`). What cm3 gets
  wrong is compared with output checked by hand (`expected=FILE` in the
  list): the traps of division by zero and of NIL, which cm3's handlers
  cannot catch on AMD64_NT, the shape check of an assignment from an open
  array, which cm3 does not make (`check/rterrors-traps`), and ranges of
  small sets and the order of large sets, which cm3's C back end gets
  wrong (`check/sets-arrays-hand`), and `FS.SetModificationTime`, which
  always fails in cm3 on Windows (`check/filesystem-time`), and
  `FloatMode`, which m3j implements as its specification describes it
  (`check/floatmode`), and `RTIO.PutE`, `PutF`, `PutG` and `PutBytes`,
  which cm3 on Windows cannot link, and `Transform.Hash`, for which cm3's
  `LOOPHOLE` converts `REAL` numbers instead of taking their bits
  (`check/runtime-hand`; its record is written by hand). Pathnames of POSIX are compared with
  cm3 on Windows too: `check/pathname-posix` has a copy of cm3's module for
  them, and `check/pathname-on-posix` runs `check/pathname` with
  `-Dm3j.os=POSIX` and compares it with the output of that program.
- `tools/expr_fuzz.py` generates programs of random expressions for any
  number of seeds and compares m3j with cm3 on them. It computes every
  value itself, so that it avoids what C leaves undefined and the known
  errors of cm3, and it tells for an integer result which compiler is
  right.
- `tools/prog_fuzz.py` does the same for statements, procedures and data:
  its programs print a trace of control flow through `TRY`, `LOCK` and
  loops, of nested procedures that use the variables of the procedures
  around them, of `VAR` formals bound to overlapping places, of copies,
  of open arrays, of objects (calls of methods, of the methods they
  override and of methods as procedures, `NEW` with methods) and of
  references (`TYPECASE`, `ISTYPE`, `NARROW`, a list, `REFANY`), of
  sets (small and large, packed, in arrays and objects), of texts and
  characters, and of several modules (calls, methods and exceptions
  across modules, cycles of imports, an opaque type). Each program is
  compiled twice, normally and with
  a small `m3j.maxMethod`, so that the chunks of split procedures are
  compared with the whole procedures and both with cm3. A program that
  fails is made smaller by removing parts of it while it keeps failing
  (`--reduce`).
- `tools/cli_test.py` tests the command line of the compiler: the usage
  message, `-run`, an option of the front end (`-NoChecks`), the exit
  status after errors and for an UNSAFE module, the module of an
  interface found in `NAMEWin32.m3` by its EXPORTS, and the module of two
  interfaces of a source package named after neither (`tests/cli`).
- `tools/coverage.py` runs the suites with JaCoCo and reports the code of
  m3j that no test executes.
- `tools/m3front_test.py` compiles CM3's front end itself
  (`tests/m3front`, about 600 units of m3front and m3middle) with m3j and
  runs it on the JVM: for the programs of m3tests, the compile tests, the
  library and the front end itself it must give the same binary M3CG,
  byte for byte, and the same diagnostics as m3j's front end (which gives
  those of cm3). A large program whose every result is checked. Run
  with `--jvm-arg=-Dm3j.maxMethod=300`, it checks the splitting of
  methods on real code: the procedures of the front end become some
  thousands of chunk methods, and the results stay the same.
- `tools/pkg_gate.py` compiles every package of the cm3 installation
  with m3j, and every class must verify; with a lowered
  `m3j.maxMethod` as well.
- `tools/stubgen_test.py` compiles CM3's stub generator for network
  objects (`tests/stubgen`: stubgen on the toolkit m3tk, a front end of
  its own for Modula-3) with m3j and runs it on the JVM for the network
  object types of CM3's packages, with the versions 1 and 2 of the
  protocol: it must write the same stubs, byte for byte, and the same
  output as stubgen built by cm3, recorded in `tests/stubgen/expected`;
  also when compiled with `--jvm-arg=-Dm3j.maxMethod=300`.
- `tools/net_test.py` runs programs that talk over TCP (`tests/net`) as
  a server and a client, compiled by m3j and, with `--cm3`, built by cm3,
  in each pairing, against the record of cm3's pair: the programs of m3j
  and of cm3 must understand each other, and m3j's must send the same
  bytes (`echo`: lines, and messages of `ConnMsgRW` of many sizes, whose
  fragments the server takes apart; `netobj`: calls of the methods of
  network objects with stubs of the versions 1 and 2 of the protocol,
  whose arguments and results are numbers, texts, records, open arrays,
  pickled lists, network objects both ways, and readers and writers of
  the other program, and their exceptions; the tests of cm3's netobj,
  whose output does not depend on times, the host or the language of the
  system: a reader and a writer of the client that the server copies,
  fifty callbacks at once, alerted calls, readers of files of the
  server, the arguments of all kinds of `perf` and the pickles of `pkl`
  and `pklWide`; `gc`: the objects that the programs keep answer,
  whenever the clean calls of the distributed collection come, and, in a
  run of m3j's server and client against output written by hand
  (`tests/net/expected/gc/m3j-count`), the collectors free all the
  objects that were dropped, which cm3's collector does not). The
  stubs of a test are those that stubgen built by cm3 wrote when it was
  recorded (`tests/net/expected/TEST/gen`). A server that waits for the
  end of its standard input, as those of cm3's tests do, ends when the
  client has ended.
- `tools/build_test.py` tests m3build, the build system (cm3's quake
  interpreter and the parts of cm3's driver that read m3makefiles,
  compiled by m3j, `tools/m3build`): cm3's tests of quake against the
  output of `cm3 -build` (`tests/quake`); the `.M3EXPORTS` that it writes
  for the m3makefiles of the packages of the cm3 installation against
  those of cm3, which name the same units, templates and imports
  (`exports`); the packages of `tests/build`, built, installed, imported
  and run (`cases`: generics, libraries, overrides, bundles, network
  objects, errors); and packages of cm3 with their own m3makefiles,
  libraries that it installs and programs that use them, which must
  print what `diff_test` recorded (`packages`). m3build runs the compiler
  in its program (`M3jCompile`, the option `-wd` of `m3j.jvm.Compile`
  for its working directory, which the JVM cannot change).
- `tools/lsp_test.py` tests the language server (`tools/langserver`,
  cm3's server on m3tk, compiled by m3j and built by m3build): sessions
  of the Language Server Protocol (`tests/lsp/sessions`) against replies
  checked by hand (`tests/lsp/expected`), in a copy of the packages of
  `tests/lsp/pkgs` whose directory has a space in its name; with node,
  the client of the extension for Visual Studio Code against the server.
  It runs m3tk on more than stubgen does: walks of the semantic trees
  (`M3AST_SM_Walk`), generic instances and the files of imports
  (`.M3IMPTAB`). The driver compiles, with a module of a package that
  exports an interface, the other safe modules of its directory that
  export it too (`M3AST_SM_Walk.m3` exports procedures of `M3AST_SM_F`,
  whose module `M3AST_SM_F.m3` has the others).
