# Porting rules: CM3 Modula-3 sources → Java (m3j)

These rules make independently ported modules fit together. They apply to
the ports of CM3's front end (`m3-sys/m3front`), of the parts of
`m3-sys/m3middle` it needs, and of the few pieces of `m3core` and `libm3`
the compiler depends on (so far the fingerprints).

The reference is the CM3 source tree in `cm3-src` (commit `20a295f`).

## 1. Fidelity

- Port **1:1**: the same procedures, in the same order, with the same
  control flow and the same comments (as Java comments). Do not refactor,
  simplify or fix anything.
- Quirks and bugs of the original are kept and marked with a comment that
  starts with `Sic:`. This includes checked runtime errors that the
  original reaches on a 64-bit target (see `Poly.rightShift1`).
- Where Java needs a different form (addresses, `LOOPHOLE`, `VAR`
  parameters, byte order), the comment starts with `Port:` and says why the
  result is the same. Code that is dropped gets `// Port, omitted: ...`.
- Keep the exact text of every diagnostic.
- The target is `AMD64_NT`: little-endian, 64-bit `INTEGER`, 8-bit `CHAR`.

## 2. Classes and names

- One Java class per Modula-3 module; the interface and the module become
  one class. The package follows the CM3 package: `m3j.fingerprint`
  (`m3core/src/fingerprint`), `m3j.convert` (`m3core/src/convert`),
  `m3j.middle` (`m3middle`), `m3j.front` (`m3front`). Checked runtime
  errors that the original reaches are `m3j.util.RangeError` and
  `m3j.util.AssertError`; a subscript out of range is Java's own
  `ArrayIndexOutOfBoundsException`.
- Classes are `public final` with a private constructor and static methods
  only. Module variables become fields of a nested `State` class
  (`Target.State`, `M3ID.State`), not mutable static fields; the
  procedures take the state objects they use as their first parameters
  (`Target.init (t, map, rt, ...)`, `M3ID.add (ids, ...)`).
- An object type becomes a Java class with the same methods
  (`M3CG.T`, `M3CG_Ops.Public`); procedures of the module that take the
  object as their first parameter become its methods (`M3CG_Wr.U`).
- Procedure names become lowerCamelCase (`FromText` → `fromText`); names
  that start with a lowercase letter are kept (`declare_array`). In the
  front end, a leading acronym becomes lowercase (`CGType` → `cgType`), and
  names in capitals only are kept (`Error.QID`, `CG.GCD`). Constants
  and variables keep their names (`Zero`, `OfEmpty`, `Integer`). A name
  that is a Java keyword gets a trailing underscore (`Int` → `int_`,
  `super` → `super_`, `return` → `return_`).
- Every file starts with the CM3 path it ports, the original copyright
  line, the license file in `licenses/`, and the line that puts the
  changes for m3j under the MIT license in `LICENSE`.

## 3. Types

| Modula-3 | Java |
|---|---|
| `INTEGER`, `Word.T`, `CARDINAL` | `long` |
| 32-bit subranges (`Int32`, `M3FP.Int`) | `int` |
| `BOOLEAN` | `boolean` |
| `CHAR` | `char` in [0..255] |
| `TEXT` | `String` (a character above 255 is the checked runtime error of `Text.GetChar`) |
| `ARRAY OF CHAR` | `char[]`; a `SUBARRAY` is passed as array, start and length |
| small value records (`Fingerprint.T`) | immutable Java records |
| fixed arrays of numbers (`Poly.T`) | Java arrays; procedures do not modify their arguments |
| value types passed as `VAR` (`TInt.Int`, `Target.Float`, `Target.Int_type`) | mutable Java objects (`int[9]`, classes) |
| `REAL`, `LONGREAL`, `EXTENDED` | `float`, `double`, `double` (EXTENDED is 64-bit on the target) |
| `Wr.T`, `Rd.T` | `java.io.Writer`, `java.io.InputStream` (characters [0..255] as single bytes) |

`VAR` parameters that only return a value become return values; the Javadoc
says so. Where a procedure writes its `VAR` result also when it fails (as
`TInt.Add` does on overflow), the result stays a parameter: a mutable value
the caller passes in. An assignment of such a value is a copy
(`TInt.copy`, `TInt.assign`, `Float.copy`); constants of these types must
never be passed as results.

Values are range-checked where the original assigns to a subrange and the
value is not provably in range (`TInt.ibyte`, `M3CG_BinRd.frequency`).

C code that the Modula-3 code calls is not translated where Java has an
exact equivalent: David Gay's `dtoa`/`strtod` (in `m3core`) are replaced by
exact `BigDecimal` arithmetic with the same correctly rounded results
(`m3j.convert.CConvert`). Where the reference cm3 depends on its C back end
(`TRUNC` and `ROUND` of out-of-range values), the port reproduces what the
reference does on AMD64_NT.

## 4. Testing

Each ported module is compared with CM3 on recorded results, the way a68j
is compared with ga68. The reference programs are Modula-3 programs in
`tools/` built with the reference cm3; their results are recorded under
`tests/` so that cm3 is needed only to record them again
(`tools/dump_test.py`). The M3CG reader and writer are compared with
cm3's `m3cgcat` on binary intermediate code that cm3 writes
(`tools/cg_test.py`). The front end is compared with cm3 on whole packages:
`m3j.tools.FrontDump` repeats the front-end runs of cm3's build log, and
`tools/front_test.py` compares the intermediate code of every unit and the
diagnostics (`tools/README.md` explains how to build the reference).

## 5. The front end (`m3front`)

The front end is larger than the rest together, and its modules share much
state, so some of the rules above take a different form here. Everything is
in the package `m3j.front`, flat, except the generic modules (see below).

### Module state

The front end is a monitor: one compilation at a time (`M3Front.i3`). Its
module variables become `static` fields of the module's class, with the same
names and initial values; `Initialize` and `Reset` procedures set them as in
the original. The state objects of `m3middle` that the front end uses are
static fields of `M3`, set before a compilation:

| Modula-3 | Java |
|---|---|
| `Target.Integer.size` | `M3.target.Integer.size` |
| `TargetMap.CG_Size[t]` | `M3.targetMap.CG_Size[t.ordinal()]` |
| `M3RT.MA_count` | `M3.rt.MA_count` |
| `M3ID.ToText (id)` | `M3ID.toText(M3.ids, id)` |

Constants of `Target` (`Target.Byte`, `Target.Word8`) stay `Target.X`.

### Opaque types and revelations

An opaque type and all its revelations become one Java class, declared
where the public name lives, with the fields and methods of the full
revelation:

| Modula-3 | Java |
|---|---|
| `M3.Node`, `M3.ValueNode` | `M3.Node`, `M3.ValueNode` |
| `M3.Type`, `Type.T`, `TypeRep.TT` | `Type.T` (fields and methods of `TypeRep`) |
| `M3.Expr`, `Expr.T` | `Expr.T` (fields and methods of `ExprRep`) |
| `M3.Value`, `Value.T` | `Value.T` (fields and methods of `ValueRep`) |
| `M3.Stmt`, `Stmt.T` | `Stmt.T` (fields and methods of `StmtRep`) |
| `M3.Scope`, `Scope.T` | `Scope.T` |
| `M3.ExSet`, `ESet.T` | `ESet.T` |
| `M3.EqAssumption`, `Type.Assumption` | `Type.Assumption` |
| `M3.CheckState`, `Expr.CheckState`, ... | `M3.CheckState` |
| `CallExpr.T_`, `CallExpr.T` | `CallExpr.T` (the partial revelation is not kept apart) |

The same holds for every other opaque type (`ProcBody.T`, `CG.Val`,
`M3String.T`): the class lives in the module that declares the type.
Private object types (`AddExpr.P`) are nested classes of their module.

### Objects and methods

- An object type is a Java class; a subtype is a subclass. Fields keep
  their names and default values. `NEW (T, f := x)` becomes a constructor
  call followed by field assignments.
- A method with a default procedure becomes a Java method whose body calls
  that procedure (`Expr.T.typeOf()` calls `ExprRep.noType(this)`). An
  override becomes an overriding method that calls the overriding
  procedure, which stays a static method of the module, with its comments.
  A method without a default throws `m3j.util.AssertError`, for the checked
  runtime error of calling a `NIL` method.
- `TYPECASE` and `ISTYPE` treat `NIL` as the original does: `NIL` is a
  member of every reference type, so `TYPECASE x OF A (a) => ...` takes the
  first branch that names a reference type, and `ISTYPE (NIL, T)` is true.
  A `TYPECASE` or `CASE` without `ELSE` that matches nothing throws
  `m3j.util.AssertError`; a failed `NARROW` is a `ClassCastException`.
  `x = y` on references is `==`.

### Values

| Modula-3 | Java |
|---|---|
| `INTEGER`, `CARDINAL`, subranges of them | `long`; `int` only where the interface says so |
| `M3ID.T`, `CG.Name`, `CG.TypeUID` | `int` |
| `CG.Label`, `CG.Offset`, `CG.Size`, `CG.Alignment` | `long` |
| `CG.Frequency` | `int` |
| `CG.Type`, `M3CG.Type` and its subranges | `Target.CGType` |
| `Target.Int` | `int[]` (`TInt.newInt()`), copied on assignment with `TInt.assign` |
| `Target.Float` | `Target.Float`, copied on assignment |
| enumerations | Java enums (`ORD` → `ordinal()`, `VAL (i, E)` → `E.values()[i]`) |
| `ARRAY E OF T` for an enumeration `E` | `T[]` indexed by `ordinal()` |
| `ARRAY [a..b] OF T` | `T[]` of `b - a + 1` elements, indexed by `i - a` |
| `SET OF E` | `EnumSet<E>`, copied on assignment; constant sets are never modified |
| records | mutable classes with a constructor that sets the default values, and `copy()` |
| `REF ARRAY OF T`, open arrays | `T[]` |
| procedure types | functional interfaces in the declaring module (`CallExpr.Typer`), with method references as values |
| `TEXT` | `String`; `Text.Equal (a, b)` → `a.equals(b)` |

An assignment of a record, array, set or `Target.Int` value is a copy; a
record or array that is a field or an element is never shared. `WITH x = d`
on a designator `d` that is written through `x` becomes code that writes
`d` itself.

### Parameters

- `VAR` parameters of a record type, of `Target.Int` or `Target.Float`
  become the mutable object itself; the callee writes into it.
- Other `VAR` parameters (numbers, Booleans, references, enumerations)
  become one-element arrays (`long[] min`, `Expr.T[] x`); the caller passes
  an array and reads element 0 after the call.
- `READONLY` parameters are passed as they are and are not modified.
- Default parameters become overloads that supply the default.

### Generic modules

`builtinWord` and `builtinAtomic` hold generic modules, instantiated by the
m3makefile. A generic module becomes a class in `m3j.front.builtinWord` or
`m3j.front.builtinAtomic` whose instances are the instantiations: the
generic parameters become constructor parameters and the module variables
instance fields. The instantiations that other modules import
(`WordModule`, `AtomicIntModule`) are classes in `m3j.front` with the
interface of the generic interface as static members; they create and hold
the instances of the other generics (`WordPlus`, `LongPlus`).

### Library

`Text`, `Fmt.Int`, `Word` and the tables of `libm3` are replaced by the
corresponding Java code, with the same results: `Word.Plus` is `+`,
`Word.LT` is `Long.compareUnsigned`, `DIV` and `MOD` are `Math.floorDiv`
and `Math.floorMod`, `IntRefTbl` is a `HashMap` (the front end only looks
values up and never iterates). `RTIO` and `RTParams` debugging output goes
to `System.err`.

### Changes for the back end

The JVM back end (`m3j.jvm`) reads the checked tree directly. For that,
and only for that, the front end differs from a strict port in two ways:

- All nested classes of `m3j.front` (the node classes `P` and `T` of the
  expressions, statements, types and values, and the helper classes they
  refer to) and their fields are `public`. Nothing else about them changed.
- `M3Front.backEnd`, if set, is called with the `Module.T` of a unit that
  compiled without errors, right after `M3Front.compile` has finished it
  and before the next compilation resets the front end's state. The
  intermediate code is still generated (the back end passes an `M3CG_BinWr`
  that writes to `OutputStream.nullOutputStream()`), so the diagnostics of
  the code generation phase stay those of cm3.
