# A Modula-3 compiler for the JVM

The front end is a faithful Java port of the front end of Critical Mass Modula-3
(CM3), and its back end new code on the JDK ClassFile API. CM3 is the reference.

The statements about CM3 below were checked against a checkout of `github.com/modula3/cm3`
(commit `20a295f`, 3 November 2026). Line counts are lines of `.i3` and `.m3` files.

## Ground rules

- **Java 25 is the baseline.** The ClassFile API (`java.lang.classfile`) is
  final there, as are virtual threads and scoped values. No preview features
  and nothing from Valhalla are assumed.
- **No UNSAFE code, for now.** The compiler accepts only safe modules and
  interfaces. `UNSAFE` modules, `LOOPHOLE`, `ADR`, `ADDRESS` arithmetic,
  `UNTRACED` references and `DISPOSE` are rejected with a diagnostic by the
  back end. The front end itself stays a faithful port and still checks
  UNSAFE code, because the M3CG comparison runs over the CM3 sources, which
  are largely UNSAFE.

## Existing work

No maintained Modula-3 compiler for the JVM exists, as far as a search
shows.

- **Ideas only.** A comp.compilers thread from 1996 called a translation of
  Modula-3 to the JVM "relatively straightforward". A W3C page of ideas lists
  a back end for the M3 compiler that generates JVM bytecode. Neither led to a
  compiler.
- **The Critical Mass JVM was the other direction.** Critical Mass sold an
  extensible JVM along with CM3 and the Reactor IDE. Bill Kalsow writes that
  he "wrote a clean-room implementation of the JVM in Modula-3, mostly as a
  proof-of-concept". It ran Java bytecode; it did not compile Modula-3 to it.
- **CM3 today.** CM3 has back ends for machine code (one based on GCC, one
  integrated for x86) and one for C (`m3back/src/M3C.m3`), but none for the
  JVM.

Other members of the Wirth family have been compiled for the JVM:

- **M2J** compiles Modula-2 to `.class` files by way of Java source.
- **Gardens Point Component Pascal** (GPCP, Queensland University of
  Technology) has emitters for JVM bytecode and for .NET. Component Pascal
  is close to Oberon-2. The fork `gpcp-JVM` supports Java 8, 11 and 17.
- **Canterbury Oberon-2 for Java** is an older Oberon-2 compiler for the
  JVM.

## Size of the port

| Component | Files | Lines | Needed for |
|---|---|---|---|
| `m3-sys/m3front` | 340 | 55,000 | the front end, all of it |
| `m3-sys/m3middle` | 57 | 19,000 | `Target`, `TInt`, `TWord`, `TFloat`, `M3Buf`, `M3ID`, `M3FP`, `M3RT`, the M3CG interfaces; perhaps 10,000–15,000 lines |
| `m3-libs/m3core/src/fingerprint` | 3 | 500 | the fingerprints (`Fingerprint`, `Poly`) |
| `m3-sys/m3linker` | 26 | 4,000 | ideas for version stamps and module initialization order |
| `m3-sys/cm3`, `m3-sys/m3quake` | 55 | 15,000 | not ported (see below) |
| `m3-libs/m3core`, `m3-libs/libm3` | 625 | 120,000 | not ported; the runtime library is written anew in Java |

## How well a Java front end could be tested against CM3

### What makes testing easy

- **The intermediate code is already exposed.** The CM3 front end passes its
  output to an abstract interface, M3CG (`m3middle/src/M3CG_Ops.i3`), which
  is a stream of calls such as `declare_procedure`, `load`, `add` and `jump`.
  m3middle has readers and writers for it in text and binary form
  (`M3CG_Rd`, `M3CG_Wr`, `M3CG_BinRd`, `M3CG_BinWr`) and a `M3CG_Tee` that
  sends the stream to two consumers. With `-keep`, cm3 is meant to write the
  stream to a side file (`Main.mc`, `Foo.ic`) next to the C output, and
  `m3cgcat` (`m3-sys/m3cgcat`) converts between the binary and the text
  form. The side file needs a small fix of the builder (see "Reference
  setup"), but the front end itself stays unpatched. This test is much stronger
  than token and syntax dumps, because it covers semantics, type checking and
  lowering as well.
- **More output to compare.** The link-info files hold type fingerprints and
  interface version stamps. A faithful port must reproduce the fingerprints
  bit for bit, so mistakes in type equivalence show up at once. The
  fingerprint code (`M3FP` on top of `Fingerprint` and `Poly` in m3core) is
  only about 500 lines and should be ported first.
- **The front end barely depends on the builder.** m3front reaches source
  files only through the interface `m3front/src/misc/Host.i3` (`Initialize`,
  `OpenUnit`, `CloseFile`). A small Java host that resolves imports on a
  search path replaces cm3 and its Quake scripts.
- **Its other dependencies are small.** Apart from m3middle, m3front imports
  basic libm3 and m3core interfaces (`Text`, `Fmt`, `Wr`, `TextWr`,
  `Convert`, tables, sequences, `Pathname`, `Word`). Java's own library
  covers all of them.
- **A test suite exists.** `m3-sys/m3tests/src` has these groups. The
  expected results are in `stdout.build` and `stderr.build` (compiler) and
  `stdout.pgm` and `stderr.pgm` (program):

  | Group | Tests | Kind | Use UNSAFE |
  |---|---|---|---|
  | `c0`, `c1` | 142 | compile only | 12 |
  | `e0` | 41 | compile errors with the expected messages | 0 |
  | `p0`, `p1`, `p2` | 288 | programs with expected output | 35 |
  | `r0` | 5 | runtime errors (unhandled exception, stack overflow, assertion, open array sizes) | 0 |
  | `x0` | 7 | programs that need input on stdin, mostly threads and readers | 0 |

  The error messages have a fixed form, for example
  `"../Main.m3", line 15: Types are not assignable in assignment (2.3.1).`
  Without UNSAFE, 253 of the 288 program tests remain. They mostly import
  `Wr`, `Fmt`, `Stdio`, `Text`, `Thread`, `Word`, `IO` and `RTIO`, so a
  small runtime library already covers most of them.
- **The CM3 sources are a corpus.** The CM3 system is written in Modula-3,
  several hundred thousand lines of it, and every module is a test case for
  the M3CG comparison, UNSAFE or not.

### What makes testing harder

- **The output depends on the target.** Sizes, alignments and record layouts
  appear in the M3CG stream. For the comparison the port must use the same
  `Target` configuration as the reference compiler. On Windows that is
  `AMD64_NT`, which also suits the JVM back end (64-bit `INTEGER`).
- **M3CG is low level for the JVM.** It is a stack machine with explicit
  offsets and memory loads and stores, while a good JVM back end wants typed
  fields and objects. The C back end of CM3 (`M3C`, 8,300 lines) had the same
  problem; it rebuilds types from `declare_record`, `declare_field`,
  `declare_array` and the other declarations in the stream. The back end
  will therefore work on the tree and M3CG serves only as the
  test oracle for the front end.
- **The runtime library is new code.** 97 of 145 modules in m3core and 54 of
  133 in libm3 are UNSAFE, so their implementations cannot be compiled. The
  safe interfaces (`Wr`, `Rd`, `Fmt`, `Text`, `Thread`, ...) are kept as
  `.i3` files and implemented in Java.

## The back end on the JVM

Points that need a design before code is written (the design itself is
in `docs/BACKEND.md`):

- **Structural type equivalence.** Two `REF INTEGER` from different modules
  are the same type. `TYPECASE`, `NARROW`, `ISTYPE` and the typecodes need
  one canonical JVM class per type. Naming the class after the type's
  fingerprint makes it the same in every module, which is one more reason to
  port the fingerprints exactly.
- **Value semantics.** Records and fixed arrays are copied on assignment and
  as value parameters. Without Valhalla they become JVM objects with explicit
  copies, which costs speed.
- **VAR parameters and nested procedures.** A `VAR` actual can be a local,
  an array element or a record field, and nested procedures reach the locals
  of enclosing ones and can be passed as parameters (though not assigned).
  This needs reference cells or accessor objects, as a68j needs for Algol 68
  names.
- **Opaque types and partial revelations.** The full object layout is known
  only when all revelations are seen. On the JVM, inheritance can do most of
  the work, but the class hierarchy must be planned.
- **Modules.** Initialization order follows the import graph, and interface
  version stamps must be checked when a program is put together. This needs a
  small replacement for m3linker.
- **Threads.** `Thread.T` maps to Java threads (virtual threads are an
  option), `MUTEX` and `LOCK` to monitors or `ReentrantLock`, `Thread.Alert`
  to `interrupt`. `WeakRef` maps to `java.lang.ref`.
- **Numbers.** `INTEGER` and `LONGINT` are `long`; `Word` and `Long` use the
  unsigned methods of `Long`. `EXTENDED` becomes `double`. Subrange and
  bounds checks are emitted as in CM3.

## Summary

| Part | Oracle | Corpus |
|---|---|---|
| Front end | the M3CG stream (as text with `m3cgcat`) and the type fingerprints, compared with cm3 | `m3tests` and the CM3 sources, including UNSAFE modules |
| Diagnostics | the error messages and positions of cm3 | `e0` in `m3tests` |
| Back end and runtime | stdout, exit status and the first line of a runtime error, compared with the program compiled natively by cm3 | the safe tests in `p0`–`p2` and `r0`, and other safe programs using the ported parts of the library |

The front end has an exact oracle almost ready to use, and the work on it is
large but low in risk. The risks are in the back end (value semantics,
`VAR` parameters, structural types) and in the size of the runtime library.

## Reference setup

The reference compiler is installed in `cm3` in the root of m3j, built
from the sources in `cm3-src` there (commit `20a295f`, unpacked from the
GitHub archive). Its version string still says `d5.11.9`.

- **Bootstrap.** `cm3-boot-AMD64_NT-d5.12.0.7z` (generated C++) was built
  with cmake and Visual Studio 2022 (MSVC 14.44, Windows SDK 10.0.26100),
  then `scripts/concierge.py full-upgrade` rebuilt the compiler twice and
  the package set `headless`. `concierge.py install` itself fails
  (`TypeError` at `concierge.py:1733`, `source()` returns a string), so the
  three cmake steps were run by hand: configure with `-A x64`, build and
  install with `--config Release`.
- **Environment.** cm3 needs the output of `vcvars64.bat`, and `cm3/bin`
  (in the root of m3j) on `PATH` (also for running the programs, which
  load the runtime DLLs from there). MinGW must not come first on `PATH`.
- **Builder fix for `-keep`.** `PushOneM3` sets the name of the side IR file
  (`Builder.m3:1647`), but `RunM3Front` calls `ResetEnv`, which clears it
  before the code generator is opened, so no side file was ever written. The
  fix, `tools/cm3-keep-side-ir.patch`, saves the name in `RunM3Front` and
  restores it after `ResetEnv`. Only the package `m3-sys/cm3` was rebuilt;
  the unpatched driver is kept as `bin\cm3-unpatched.exe`.
- **`-keep` in the C mode: `M3CG_Tee`.** With the side file, the front end
  writes through `M3CG_Tee` to the C back end and to `M3CG_BinWr`. Tee has
  four bugs that the side file exposes: `declare_procedure` passes the
  caller's `parent` (a Tee `ProcPair`) to both children instead of its
  halves, so `M3C` stops with a failed `NARROW` (`M3CG_MultiPass.m3:425`) at
  the first nested procedure; `case_jump` passes the Tee's label numbers
  instead of each child's, so the C code has undefined labels; and
  `declare_object` reaches only the C back end, so object types are missing
  from the side file; and `next_label` starts from `No_label` (-1) and adds
  the count before it sets the counter to 0, so the first two labels of a
  cm3 process are merged into one. The fix, `tools/cm3-tee.patch`, corrects
  all four.
  `m3-sys/m3middle` was rebuilt and shipped (the unpatched package is kept
  in `cm3/backup/m3middle-unpatched`), then `m3-sys/cm3` was rebuilt.
  Before the fix, binary IR came from the external back-end mode
  (`tools/cm3-external.cfg`, named by `M3CONFIG`, sets `M3_BACKEND_MODE =
  "3"`); that IR differs from that of the C mode (`Target.Init` depends on
  the mode), so it serves only to test the M3CG tools.
- **Front-end options.** In the C mode the configuration
  (`cm3cfg.common`) sets `M3_FRONT_FLAGS = ["-unfold_nested_procs",
  "-check_procs"]`, and `set_config_options` adds `-w1` to `M3_OPTIONS`.
  `cm3 -commands` shows only the latter (`m3front ../src/Main.m3 -w1`).
- **m3cgcat on Windows.** Reading binary M3CG from stdin fails (`bad
  intermediate code version`), and an output file given with `-out-ascii=`
  after an input file raises `OSError.E`. This form works:
  `m3cgcat -in-binary=Main.mc -out-ascii > Main.txt`.
- **Running m3tests.** `cm3 -DHTML` in `m3-sys/m3tests` runs the `p`, `e`
  and `r` tests and writes `m3tests-results.xml` (about 10 minutes). The
  `c` tests run only with `-D_ctests` and the `x` tests need input. If
  `NoDefaultCurrentDirectoryInExePath` is set, cmd does not find `pgm.exe`
  and every program test fails, so it must be unset for the run.

### Results of m3tests on AMD64_NT

The report counts 264 of 292 tests as failed, but almost all of these
differ only in `stdout.build`, where `cl` and `link` echo file names and
linker options on Windows. Beyond that:

- **`e` (37 tests):** all error messages in `stderr.build` match.
- **`r` (5 tests):** r003–r005 match; r001 and r002 lack only the lines
  `Aborted` and `Segmentation fault` that a Unix shell prints.
- **`p` (250 tests):** 235 match. The other 15:
  - link errors with C code from the test (`m3_link` failed): p051, p053,
    p109, p160, p269;
  - crashes of the C back end `M3C`: p201 (`DeclareTypes not
    progressing`), p268, p270 (`M3C.m3:5752`), p277, p280 (`M3C.m3:5986`,
    `TIntLiteral` with `UINT64`);
  - wrong results of the compiled program: p047 (UNSAFE), and the safe
    tests p137 (`LONGINT`), p156, p159, p162 (sets).

So for the program tests the expected files in `m3tests` are the oracle,
not a program compiled by cm3; the cm3 run serves for programs outside the
suite, with the known failures above in mind. The M3CG comparison is not
affected, except for units that crash `M3C` before the stream is complete.

## Suggested order

1. Port `Fingerprint`, `Poly` and `M3FP` and compare them on many inputs.
   Done: `tools/fp_test.py` matches CM3 on 2578 cases. CM3 itself stops
   with a range error in `M3FP.ExtendByInt` for negative values and in
   `Poly.Product` (and so `Poly.Power` for d >= 256) on AMD64, because
   `Word` shifts work on the sign-extended 64-bit word; the port does the
   same (20 fault cases). As far as the sources show, the compiler does
   not reach these cases: it calls `ExtendByInt` only with DFS numbers in
   `TypeFP.m3` and never calls `Product` or `Power`.
2. Port the needed parts of m3middle (`Target` for `AMD64_NT`, `TInt`,
   `TWord`, `TFloat`, `M3ID`, `M3Buf`, M3CG text writer).
   Done: `Target` (all targets), `TargetMap`, `M3RT`, `TInt`, `TWord`,
   `TFloat`, `M3ID`, `M3Buf`, `M3CG`, `M3CG_Ops`, `M3CG_Wr`, `M3CG_Binary`
   and `M3CG_BinRd`, with `Convert` from `m3core` (Gay's `dtoa`/`strtod`
   replaced by exact `BigDecimal` arithmetic). `tools/dump_test.py middle`
   matches CM3 on 40861 cases (37 fault cases fail alike), and
   `tools/cg_test.py` turns all 1617 binary IR files that cm3 wrote for
   m3core, libm3, m3middle, m3front and the m3tests (53.7 MB of text) into
   the same text as `m3cgcat`, byte for byte. (In the external mode cm3
   stops compiling a package's dependent units after the first failed
   back-end run, so the libraries are covered only in part: 269 units of
   m3core, 585 of m3front.) `M3CG_Check`
   is left for step 3, where it sits between the front end and the
   writer.
3. Port m3front with a Java `Host`. Compare the M3CG text with cm3 on `c0`,
   `c1` and `e0`, then on `p0`–`p2`, then on the CM3 sources.
   Done: all of m3front (package `m3j.front`, with the generics in
   `m3j.front.builtinWord` and `m3j.front.builtinAtomic`), `M3CG_Check` and
   `M3CG_BinWr`. `m3j.tools.FrontDump` repeats the front-end runs of a cm3
   build log and passes the code through `M3CG_BinWr`, `M3CG_BinRd` and
   `M3CG_Wr`, as cm3 and m3cgcat do; `tools/front_test.py` compares the
   result with cm3's side files. On m3core, libm3, m3middle and m3front
   (1191 units) and on the 476 test packages of m3tests (626 units):
   - 1777 units are identical byte for byte (the reference has 53.2 MB of
     M3CG text in all);
   - 31 units have the same lines in another order. cm3's `Revelation`
     hashes opaque types by their `M3ID` numbers, which depend on every
     identifier the cm3 process interned before (quake, the builder, the
     link info), so the order in which it declares revelations (and the
     types it compiles for them) cannot be reproduced without running the
     whole cm3 driver;
   - 9 units are cut off in cm3 because its C back end crashes (p240, p268,
     p270, p277, p280, c126; see above); cm3's code is a prefix of the
     port's. In e041 (cyclic revelations) cm3 and the port both overflow
     the stack, and in c120 both stop at the same `<*ASSERT*>` in
     `BitSize.m3`;
   - the diagnostics are the same in all packages. For six p tests the
     expected files of m3tests are older than this cm3 (p070, p135,
     p152–p154, p161); there cm3 itself was run again for comparison.

   Line coverage of `m3j.front` on these packages (measured with JaCoCo
   through `front_test.py --jvm-arg`) is 81.8 %, branch coverage 69.9 %.
   Most of the rest is code for other targets and configurations (the
   stack walker, big-endian and right-to-left argument order, bit-level
   loads and stores), internal errors, debugging output, unused options
   (`-Z`, `<*DEBUG*>`, `<*TRACE*>`), the header scanner of the builder
   (`M3Header`) and UNSAFE operations. The parts of the safe language that
   neither the libraries nor m3tests use are tested by the packages in
   `tests/front` (wide characters and texts, wide brands, `ISTYPE` as a
   value, open arrays assigned to fixed ones, `SUBARRAY` in all its forms,
   `RAISE` with arguments of every kind, steps of known sign in `FOR`, the
   `LINE` pragma, the `Compiler` built-ins at run time, scanner and `CASE`
   label diagnostics), built by cm3 as they are and with `-NoChecks`;
   all 22 units are identical and so are the diagnostics. With them the
   line coverage is 83.8 %. Two things the port does not repeat: the
   diagnostics that cm3's builder prints while it scans the imports of a
   unit (it uses `M3Header` before the front-end run), and the choice of
   a 32-bit `WIDECHAR` (`-widecharuni` or `Unicode_WIDECHAR` in the
   configuration; FrontDump always uses the 16-bit default).
4. Write the back end on the tree for the safe language, and the runtime
   library starting with `Wr`, `Stdio`, `Fmt`, `Text`, `Word` and `Thread`.
   The design is in `docs/BACKEND.md`: how every type is represented on
   the JVM, how parameters, nested procedures, modules, exceptions and
   threads are mapped, the rules for code that the JIT compiles well, and
   the milestones M0 to M5. The runtime library is hybrid: the parts that
   are UNSAFE in CM3 or talk to the operating system are written in Java,
   the safe modules of libm3 are compiled by m3j.

   Status: the milestones M0 to M4 are done and M5 for the most part.
   The back end compiles the whole safe language; methods that would be
   too large for the JIT are split and measured (`tools/split_test.py`),
   constant tables are dynamic constants, and `build.sh` makes an AOT
   cache. `Pickle` writes and reads the same pickles as cm3: the classes
   of the types carry the layout of their referents in cm3 (`M3$PKL`),
   from which the runtime makes the bytes. Not done: `RTType.Get` (cm3's
   type cells).
5. Run the safe program tests against natively compiled cm3 programs.

   Status: the oracle are the expected files of m3tests, which cm3 itself
   matches (see step 3). `tools/run_jvm.py p0 p1 p2 r0`: PASS 216, 6
   KNOWN (failures for reasons outside m3j, listed with the reason in the
   tool: a test that expects a link error, expected files older than cm3,
   programs that run for minutes).
   `tools/run_jvm.py --compile c0 c1`: every class verifies; the
   tests that do not compile are those that cm3 rejects too. The
   benchmarks of `tests/bench` (`tools/perf.py --native`, wall time with
   the start of the JVM, median of 5 runs) take 12.3 s with m3j and
   69.0 s with cm3 `-O` for all 14 programs; m3j is faster on every
   one of them.

   Beyond m3tests, `tools/diff_test.py` compares m3j with cm3 on the test
   programs of cm3's libraries, its examples, the Modula-3 programs of
   Rosetta Code, small programs for errors found in m3j, programs that
   test one area at its edges and random programs (recorded once with
   cm3): of 174 programs that cm3 builds and that run the same twice, 171
   give the same output and exit status (among them
   the pickles of values of every kind, byte for byte, the pathnames of
   Windows and of POSIX, and threads under load), two differ
   only in the last digits of floating-point results (numerical
   derivatives, and functions of `Math`), and one differs for a known
   reason (the place named in the message of an unhandled `OSError.E`). These tests found errors that m3tests did
   not: a variable passed `VAR` in the initializer of another variable,
   `T.m (x)` for an opaque `T`, `NEW` of an opaque reference type, the
   check of `RAISES` clauses, missing parts of the library, and alerts
   that were lost, seen late or seen twice.

   `tools/pkg_gate.py` compiles every package of the cm3 installation
   (`cm3/pkg`, 61 packages, among them cm3's own compiler) with m3j:
   1169 modules compile and verify and 160 are UNSAFE (no module failed
   to verify since readers and writers come from cm3's own modules, so
   that classes of `RdClass` and `WrClass` work).
   It found that classes of procedure types could get names too long for
   a path of Windows, that the verifier needs the partial revelations of
   an opaque type, and an error of cm3's front end that m3j shares: a
   module compiled before an interface it imports can get the alignment
   of a variable of that interface wrong. A build of cm3 compiles the
   interfaces of a package first, and the driver of m3j now does the
   same. With the limit for the size of a method lowered to 300 bytes
   (`-Dm3j.maxMethod=300`), so that m3j splits even small procedures
   (in m3front alone into more than 2800 chunk methods), the same modules compile
   and verify, among them those of m3tk (194 of its 197 modules; the
   other three are UNSAFE).

   `tools/m3front_test.py` turns the port around: m3j compiles CM3's own
   front end (`tests/m3front`: m3front, the part of m3middle it uses and
   a driver, 616 units, seven of them changed to be safe) and runs it on
   the JVM against m3j's front end, on 354 cases: the programs of
   m3tests, the compile tests, the library of m3j and the front end
   itself. Every unit gives the same binary M3CG, byte for byte, and the
   same diagnostics. The test found two errors of the back end that no
   other test had shown: a call of a built-in procedure through a
   constant (`CONST And = Word.And`), and the default values of record
   fields in arrays made by `NEW` and in fields of objects
   (`tests/diff/src/regress` has a program for each). The front end
   compiled by m3j takes about as long as m3j's Java port with its back
   end on these cases. Compiled with the lowered limit of 300 bytes, the
   front end gives the same results on all 354 cases.

   `tools/stubgen_test.py` does the same with a second large program of
   another style: CM3's stub generator for network objects, stubgen, on
   the toolkit m3tk, a front end of its own for Modula-3 with an abstract
   syntax tree of objects, property lists and many generic tables
   (`tests/stubgen`: 535 units, two of them changed to be safe). Compiled
   by m3j and run on the JVM for the 35 network object types of CM3's
   packages, with the versions 1 and 2 of the protocol, it writes the
   same stubs, byte for byte, the same output and the same exit code as
   stubgen built by cm3 (70 runs; one of them stops with stubgen's error
   for a constant it does not support), also when compiled with the
   limit of 300 bytes for a method. The test found an error of m3j's
   runtime: `Text.Hash` was not cm3's function, so a table of texts or
   atoms listed its keys in another order than with cm3 (the imports of a
   stub came out in another order). It also found that the driver did not
   find a module that exports an interface of another name
   (`M3PathElemWin32` of m3tk exports `M3PathElemOSPriv`), and that on
   Windows an interface whose name differs from a class of the runtime
   only in case (m3tk's `FingerPrint`, the runtime's `Fingerprint`) made
   the compiler fail. The runtime now provides `Scheduler`, `RTCollector`
   and `RTTypeSRC.TypeName` (the classes of named reference and object
   types carry their names, as cm3's type cells do) and
   `Process.GetWorkingDirectory`, and the library some more units of
   libm3 that m3tk uses (`Property`, `Formatter`, `RdExtras` and others).
   The stubs are those that m3j would need to run network objects.

   Network objects talk over TCP, so the library of m3j has the package
   tcp of CM3 (`lib/tcp`), with safe modules `TCP` and `IP` on the
   sockets of the Java platform in place of cm3's UNSAFE ones. A thread
   that waits for a connection or for bytes returns at once when it is
   alerted (cm3 looks for an alert once a second). `check/tcp` compares a
   program of connections on the loopback address with cm3: lines,
   twenty connections at once, reads with a timeout, the end of a
   connection, the errors of refused and closed connections and of
   closed connectors, alerts in `Accept` and in reads, and addresses.
   `tools/net_test.py` runs a server and a client as two programs, each
   compiled by m3j or built by cm3: in every pairing they understand each
   other, and the messages of `ConnMsgRW` that m3j sends are those of
   cm3, byte for byte. The tests found errors of cm3, for which m3j has
   output checked by hand (`check/tcp-hand`): `Rd` of libm3 fails with a
   range error when a reader of `ConnMsgRW` reads its second message to
   the end (the indexes of a message start at 0, but the buffer that `Rd`
   keeps for `UnGetChar` still has those of the message before; m3j's
   `ConnMsgRW` empties it); `TCPPeer.Match` compares other bits of the
   address than the first ones on a little-endian machine; and on Windows
   a second connector on the port of an open one takes the port instead
   of raising `IP.PortBusy`.

   `tools/coverage.py` measures with JaCoCo which code of m3j these
   suites execute. Of the back end (`m3j.jvm`) they execute 95.7 % of the
   instructions, 95.2 % of the lines and 85.4 % of the branches; most of
   the rest are options of the driver, internal errors and the messages
   for what is not supported. The measurement showed paths that no test
   reached: arrays and references of 16-bit and floating-point scalars
   and their elements passed `VAR`, default values of fields that are
   not literals, `BITSIZE` of open arrays, `ROOT` in type tests,
   `SUBARRAY` of open arrays of two dimensions, sets of more than 64
   elements, and methods split inside large bodies of `WHILE`, `REPEAT`,
   `TRY EXCEPT`, `TYPECASE` and `LOCK`. `tests/diff/src/check/backend`
   and the program `stmts` of `tools/split_test.py` test them now.

   `tools/expr_fuzz.py` compares m3j with cm3 on programs of random
   expressions (integer, `Word` and `Long`, Boolean and floating-point
   arithmetic and conversions on variables, packed fields, arrays and
   literals). On 200 programs, about 80,000 checks, it found an error of
   the back end: a constant floating-point expression inside a computed
   one was replaced by the value that the front end folds, but the front
   end does the arithmetic of REAL in double precision, and cm3 uses the
   folded value only where the front end puts it in place of the
   expression. With the tests of `tests/diff/src/check` (formatting and
   scanning of numbers, floating-point operations and the functions of
   `Math` at their edges) it showed that `ABS (-0.0)` is `-0.0` in cm3,
   that the NaNs of `IEEESpecial` are signaling NaNs, and that cm3
   computes `expm1`, `log1p`, `asinh`, `acosh` and `atanh` with its own
   formulas on Windows; m3j does the same now. The generator computes
   every value itself, so that it leaves out what C does not define, and
   for a difference in an integer result it tells which compiler is
   right. It found errors of cm3, which it avoids: the C back end writes
   `FIRST (INTEGER)` without parentheses (`x - FIRST (INTEGER)` is off by
   2), shifts by 64 in `Word.Insert` of 64 bits, gives
   `VAL (Long.RightShift (x, n), INTEGER)` the sign of `x`, and the
   result of `MIN` and `MAX` of zeros of different signs depends on the
   C compiler; the front end (and m3j's, until it was fixed, see below)
   fails an assertion when it folds `Word.Divide` to an unsigned value
   beyond `LAST (INTEGER)`. The tests of `check` found more of them: cm3's C back
   end cannot compile comparisons of sets of more than 64 elements and
   `RAISE` with a `REAL` argument, `Math.frexp` and `Math.modf` do not
   link, and the front end rejects an array constructor as the value of
   a field in `NEW`. The tests of runtime errors, sets and `=` on arrays
   (`check/rterrors`, `check/sets-arrays`) found further ones: on
   AMD64_NT cm3 detects division by zero and the dereference of NIL by a
   trap of the processor that no handler can catch; it does not check the
   shape when an open array is assigned to another array (only for array
   constructors); its C back end ignores the upper bound of a range in the
   constructor of a set of at most 64 elements (`{a..b}` is `{a..63}`:
   it shifts -1 right arithmetically), swaps the operands of `<`, `<=`,
   `>`, `>=` on sets of more than 64 elements, and fails an assertion
   (`M3C.m3`, line 6777) for a constant set of at most 64 elements that
   has its element 63. The test of the file system (`check/filesystem`)
   found that `FS.SetModificationTime` always fails on Windows (cm3 opens
   the file read-only, and then cannot set its time). For these, the tests
   compare m3j with output checked by hand. The same test found two errors
   of m3j: `FS.Rename` did not replace a file of the new name, and writing
   a handle opened read-only raised `OSError.E` instead of being a checked
   runtime error. `tests/diff/src/fuzz` has ten of the programs.

   The required interfaces of the language (chapter 3 of "Systems
   Programming with Modula-3") are all there, with every procedure. cm3
   does not follow the specification of `FloatMode` on `AMD64_NT`: every
   procedure but `GetRounding` fails an assertion, where the
   specification asks for answers and for `Failure` when a mode cannot be
   set. m3j follows the specification (`check/floatmode`, output checked
   by hand): the rounding mode is `NearestElseEven`, no flag is ever set,
   and every condition has the behavior `Ignore` except `IntDivByZero`,
   whose checked runtime error is reported as `Trap`. `Text.ForAllDo`,
   which cm3 adds to `Text`, gives the callback the whole text at once
   (cm3 each piece of a concatenation, `check/text-forall`).

   Programs can start other programs: `Process.Create`, `Wait`, `GetID`,
   `GetStandardFileHandles` and `SetWorkingDirectory` and `Pipe.Open` are
   there, on the processes of the Java platform (`check/process` compares
   them with cm3, with `cmd.exe` and `sort.exe` as children). The Java
   platform cannot pass a handle of a file or a pipe to a child as the
   system can, so threads of m3j copy between them and the child, and a
   regular file is opened by its name. The Java platform cannot change
   the working directory of the process either, so m3j keeps one for the
   program, against which its file system resolves relative pathnames.
   cm3 on Windows joins the parameters of a child with spaces, without
   quotes, so that a parameter with a space in it becomes two, and ends
   the command line with a space, which `echo` prints; with m3j each
   parameter reaches the child as one.

   The library also brings the safe units of m3core and libm3 that
   programs may want, not only those that the tests use (`FmtTime`,
   `Unicode`, the geometry of `Point`, `Rect` and `Region`, `Sx`,
   priority queues, `RdCopy`, `Capability` and all the generic instances
   of the installation; `check/libunits` compares them with cm3). Three
   UNSAFE modules among them are changed to safe code with the same
   results (`Transform`, `RdUtils`, `Capability`). `RTIO`, `RTProcess`,
   `RuntimeError` and `Swap` now have all their procedures: `RTIO.PutE`,
   `PutF` and `PutG` print as C's `printf` (cm3 on Windows does not have
   them), and `RTProcess.OnInterrupt` handles Ctrl-C as cm3 does on
   Windows. The test found an error of cm3: a `LOOPHOLE` of a `REAL`
   number to an integer type of 32 bits converts the number (2.5 becomes
   2) instead of taking its bits, so `Transform.Hash` gives other values
   with cm3 (`check/runtime-hand` has m3j's, checked by hand, with the
   procedures that cm3 on Windows cannot link). And the epoch of `Time.T`,
   which the interface leaves to the implementation, differs: cm3 on
   Windows counts from 1601, m3j from 1970 as the Java platform does, so
   a program that formats a constant time gets another date.

   The library also brings the package sysutils of cm3 (`lib/sysutils`):
   procedures on texts, environments, files and directories, and child
   processes with redirections, lists of commands like a shell's and
   pipes (`System`), which cm3 itself, m3tohtml and other programs of
   cm3 use. Its UNSAFE modules for Windows and POSIX are replaced by safe
   procedures on the Java platform; `check/sysutils` compares it with cm3,
   with `findstr.exe` and `sort.exe` as children. On Windows, sysutils
   puts each parameter of a child in quotes, which programs remove but
   the commands of `cmd.exe` do not, with cm3 as with m3j. cm3's
   `System.Hostname` gives `"amnesiac"` on Windows unless the program has
   started Windows Sockets; m3j's gives the name of the machine.

   A program gets files as resources as in cm3: m3bundle of cm3, compiled
   by m3j (`m3bundle.jar`, made by `build.sh`), writes an interface and a
   module with the contents of the files, which `Bundle` and `Rsrc` give
   the program. cm3 runs m3bundle in its build (`bundle` in an
   m3makefile); with m3j it is run by hand. `tools/bundle_test.py`
   compares what it writes with m3bundle of cm3, byte for byte, and
   `check/bundle` a program that reads resources from a bundle and from a
   directory.

   `WeakRef`, which the runtime of network objects needs for its table of
   surrogates, uses the weak references of the Java platform. A weak
   reference with a cleanup procedure also has a keeper, an object that
   only the node references (through a field of `ROOT`) and that
   references the node: when the node becomes unreachable, the Java
   platform finalizes the keeper, which can still reach the node, and a
   thread of `WeakRef` calls the cleanup procedure with it, as cm3's
   collector does; the cleanup may make the node reachable again. This
   differs from cm3 in three ways. The Java platform finalizes in no
   particular order, so the cleanups of nodes that reach each other may
   run in any order, and a weak reference to a node that only another
   node with a cleanup reaches dies with that node, where cm3 keeps such
   a node. Only objects have the field: a node of another type (a `TEXT`
   or the referent of a `REF` type) with a cleanup procedure is never
   collected and never cleaned up (weak references without a cleanup
   work for every node). And finalization is deprecated in Java: a JVM
   started with `--finalization=disabled` calls no cleanup procedures.
   `RTCollector.Collect` now asks the Java platform for a collection.
   `check/weakref` compares the weak references of live nodes with cm3;
   what depends on the collector is checked by hand
   (`check/weakref-hand`), since cm3's collector keeps all the nodes on a
   page to which a stack may point and so gives other results from run
   to run.

   `Pickle2`, which network objects use besides `Pickle`, writes pickles
   of version 3, with the packing of the writer (sizes, byte order,
   alignment) in the header. cm3 writes the data of a referent with
   converters built from its runtime type descriptions; on AMD64_NT
   they write the same bytes as `Pickle` (64-bit words leave no padding
   to skip), so m3j's `Pickle2` is built on the same layouts as its
   `Pickle`, without the converters. The numbers that specials write
   through `PickleStubs` are converted from the packing of the writer, as
   in cm3; the data of a referent from a machine with another packing is
   not converted (a reader raises `Pickle2.Error`). `check/pickle2`
   compares it with cm3, byte for byte: the values of `check/pickle`,
   levels of objects smaller than a word, `PickleStubs`, specials
   registered again and for pseudo pointers, pickles of `Pickle` read by
   `Pickle2`, texts that cm3 wrote with both, and pickles of specials
   from machines with 32-bit words and big-endian ones. It found an error
   of cm3: `Pickle2` fails with an illegal memory access on a referent
   with a procedure field that is not `NIL` (it reads the procedure as a
   traced reference, which the collector checks), so the objects of the
   test have no procedures.

   On these runs the runtime of network objects, the package netobj of
   cm3 (`lib/netobj`): the agent that exports and imports objects by
   name, the stubs' library `StubLib`, the tables of objects and
   surrogates, the distributed collection, vouchers for readers and
   writers, and the transport over TCP. Its UNSAFE modules view the
   buffers of the readers and writers of a connection as headers,
   integers and object IDs; their safe replacements read and write the
   bytes of the messages one by one, as cm3 has them on AMD64_NT, so
   that a program of m3j and one of cm3 call each other's objects. The
   fingerprints of the types, which tell the other program what stubs
   a surrogate needs, are cm3's (`RTTypeFP`), and a surrogate is made
   from its typecode by `M3jPickle.New`. `tests/net/netobj` passes in all
   four pairings of server and client of m3j and cm3: methods with stubs
   of the versions 1 (`Pickle`) and 2 (`Pickle2`) of the protocol, with
   numbers, texts with wide characters, records with sets and `REAL`
   numbers, open arrays, pickled lists, exceptions of the method and
   `NetObj.Error`, a callback to an object of the client, an object that
   comes back to its owner as itself, and readers and writers of the
   client that the server reads and writes. For the library, the driver
   now compiles all the safe modules of a package that export an
   interface under other names (in netobj `AgentStubs` and
   `VoucherStubs` export `NetObjInit`, and two modules implement
   `NetObjMonInit` together). The cm3 installation of the tests had no
   stubgen, which the template of netobj runs; it was built from the
   sources and installed.

   The tests of cm3's netobj run too (`tests/net`: `echorw`, `fd`,
   `longcall`, `luca`, `perf`, `pkl` and `pklWide`; `fd1` runs on POSIX
   only), changed so that their output does not depend on times, the
   host or the language of the system, and their servers end at the end
   of their standard input; `gc` tests the distributed collection. All
   pass in the four pairings. They found two errors of m3j. The compiler
   failed on a method in which a variable holds references of different
   types on two paths that join, when the unit is the first to use the
   types: their classes were made after the class of the unit, so the
   class hierarchy that the stack maps need did not know them yet
   (`regress/merge-types`). And a connection that m3j started while
   Windows tried again to reach a listener whose queue was full failed:
   m3j set `TCP_NODELAY` after `connect`, which fails then; cm3 sets it
   before, as m3j now does (`fd` makes fifty callbacks at once). They
   found two errors of cm3's netobj as well, which the copy of m3j
   corrects (`lib/README.md`): after a flush a surrogate writer gets 8
   bytes more room than its buffer has, so a writer that writes almost a
   buffer at once after a flush fails with a range error (the lines of
   `echorw` are shorter, so that cm3 passes); and the queue of the clean
   calls of a location loses a clean call that comes while its cleaner
   makes a call, so the owner keeps the object for good. With a server
   and a client of m3j, `gc client N count` shows that the objects that
   were dropped are freed at once (with the queue of cm3, a third to a
   half of them stayed); net_test runs it too, against output written
   by hand (`tests/net/expected/gc/m3j-count`), and fails without the
   correction of the queue. The collector of cm3 frees few of them: on
   AMD64_NT it pins the pages that the stacks seem to refer to, and of
   fifty objects with cleanup procedures of weak references that became
   unreachable it called the procedures of 23, so `gc` prints only what
   does not depend on the collectors. The server of `longcall` sees that
   a call was alerted only when the scavenger of `TCPNetObj` looks at the
   connections, once a minute, after the test has ended.

   `tools/prog_fuzz.py` does the same for statements, procedures and
   data: its programs print a trace of control flow through `TRY
   FINALLY`, `TRY EXCEPT`, `LOCK` and loops, of nested procedures that
   use the variables of the procedures around them, of `VAR` formals
   bound to overlapping places, of copies and of open arrays. Each
   program is compiled by m3j twice, the second time with a small limit
   for the size of a method (`-Dm3j.maxMethod`), so that even small
   procedures are split into chunks. On 450 programs compared with cm3
   and 520 more that compared only the two versions of m3j, it found five
   errors of the back end, each now with a program in
   `tests/diff/src/regress`: a `FOR` loop that starts at `LAST (INTEGER)`
   got a counter of 32 bits, because the check of its range overflowed;
   a `WITH` name used in a `FINALLY` clause was copied into the frame, so
   that assignments to it were lost; an `EXIT` or `RETURN` through
   several `TRY` statements ran an inner `FINALLY` clause twice, and inner
   handlers caught the exceptions of outer `FINALLY` clauses; the `ELSE`
   of a `TRY EXCEPT` did not catch `EXIT` and `RETURN` out of its body,
   which the language defines as exceptions (cm3 catches them, and m3j
   now passes the test `p122` of m3tests too); and a nested procedure
   that called a procedure declared further out, which uses the
   variables of an outer procedure, could not pass it the frame of that
   procedure (m3j failed with an internal error). It found errors of
   cm3, which it avoids: the C back end fails (in `M3C.last_param`) on a
   `FINALLY` clause that has a `RETURN` or `EXIT` and a `TRY FINALLY` or
   `LOCK`, and the program it compiles crashes when `EXIT` or `RETURN`
   leaves a `TRY EXCEPT` with named handlers and `ELSE`; the front end
   (and m3j's, until it was fixed, see below) fails with an internal
   error of the code generator on
   a `WITH` name bound to a field of a packed record, and with "stack
   underflow" on a `RETURN` of an array constructor that is not constant
   inside `TRY EXCEPT ELSE`. Its programs also have objects: types with
   methods whose procedures are generated like the others and override
   each other, calls of methods on variables, elements of arrays and
   results of `NEW`, calls of the methods they override, `NEW` with
   methods, `TYPECASE` (with `NULL`), `ISTYPE`, `NARROW`, a list that is
   extended, shortened and walked, `REF INTEGER`, `REF` records and
   `REFANY`. On 240 more programs compared with cm3 and 300 compared
   only between the two versions of m3j, these found no error. They
   also have sets and texts: small and large sets (of `CHAR` too), a set in a
   packed record, in an array and in an object, their operations,
   comparisons and loops over their elements, and the operations of
   `Text`, loops over the characters of a text, `CASE` on characters and
   `TEXT` in `TYPECASE`. On 220 more programs compared with cm3 and 300
   compared only between the two versions of m3j, these found no error
   of m3j, but errors of cm3, which the generator avoids: its C back end
   swaps `=` and `#` on sets of more than 64 elements when they decide a
   branch (`IF a = b THEN` runs the `ELSE` part when the sets are equal,
   while `ORD (a = b)` is right), and its `Text.SetChars` copies the
   characters that follow a text made by `Text.Sub` in the text it was
   taken from into the rest of the array (`TextSub.SubGetChars` passes
   the array on without its length). Its front end, and m3j's until it
   was fixed, refuses to pass an operation on constant small sets, which it folds,
   to a `READONLY` formal ("non-byte-aligned value cannot be passed
   READONLY by reference"). Then programs of several modules: the
   procedures spread over modules with interfaces, so that calls,
   procedure variables, methods, exceptions and `VAR` formals cross
   modules, variables and constants of interfaces, an interface that
   several modules export, procedures that no interface declares,
   cycles of imports, and an opaque object type that one module reveals
   and others extend. These found an error of m3j
   (`tests/diff/src/regress/init-cycle`): in a cycle of imports, its
   linker could initialize a module before an interface it exports,
   whose arrays and records were not yet allocated (cm3 has them in
   static memory), and before a module outside the cycle that it uses
   (`IO`); m3j now orders the units as cm3's `RTLinker` does, by the
   strongly connected components of the imports. On 176 more programs
   compared with cm3 and 150 compared only between the two versions of
   m3j, these found no other error of m3j, but one more of the front
   end (fixed in m3j): an operation on constant small sets with negative elements,
   passed to a value formal, makes it fail (a subscript out of range in
   `SetExpr`); and one of cm3's C back end: a `CASE` whose selector is
   the absolute value of an expression that the front end knows to be
   negative takes the arm of one less (`CASE ABS (ORD (b) - 7) OF` with
   `b` true takes the arm 5, not 6). `tests/diff/src/fuzz` has 25 of
   its programs as well (`tests/diff/prog.list`).

   These errors of cm3's front end, which m3j's front end shared, are
   fixed in m3j; `tests/diff/src/regress/frontend-fixes` tests them
   against output checked by hand, since cm3 cannot compile it. A
   `WITH` name can be bound to a field of a packed record that does not
   start at a byte, also through a reference and in a packed record
   that is itself a field, and to an element of a packed array at a
   constant index: the name holds an address before the field, aligned
   as the record, and its variable the offset in bits from there (for
   an element at an index that is not constant, whose offset is known
   only at run time, the compiler reports a restriction, as for such a
   designator passed as a `VAR` formal). A `RETURN` of an array
   constructor that a `TRY EXCEPT ELSE` absorbs computes the value and
   drops it. An operation on constant small sets that the front end
   folds keeps the bounds of the element type and an alignment to be
   computed (cm3 left them 0), so that it can be passed to a `READONLY`
   formal and, with negative elements, to a value formal. `Word.Divide`
   and `Long.Divide` fold to unsigned values beyond `LAST (INTEGER)`, as
   the other operations of `Word` and `Long` do. One more turned up on
   the way: the front end took an element of an array at a constant
   index as aligned as the array, which a packed element after the first
   is not; it now has the alignment of its offset. `BITSIZE`, `BYTESIZE`
   and `ADRSIZE` of a type of size zero under `EVAL`, where cm3 stops at
   an `<*ASSERT*>` (the test `c120` of m3tests), compile. The M3CG that the
   front end writes for the modules of `tools/m3front_test.py` stays the
   same as that of cm3's front end. The changes are marked "Not in CM3"
   or "Not as in CM3" in the sources.

   m3j builds packages from their m3makefiles with m3build
   (`tools/m3build`, `m3build.jar`): cm3's quake interpreter (m3quake)
   and the parts of cm3's driver that read m3makefiles (`Main`,
   `Makefile`, `M3Build` and their helpers), compiled by m3j, with a
   builder of its own that gives the units of a package to the compiler
   of m3j in the same program, in place of cm3's, which compiles each unit
   and links the objects. A program becomes a jar with scripts that run
   it; a library is its sources and its `.M3EXPORTS`, since m3j compiles
   a program from the sources of its libraries. The UNSAFE parts of these
   sources were small (running a command with `system`, a buffer read
   through a `LOOPHOLE`, the C module `MxConfigC`, a monitor of the
   collector in `ETimer`) and were made safe. cm3's tests of quake give
   the output of `cm3 -build` but for the host, the date and the times
   of files; for 58 of the 61 packages of the cm3 installation, m3build
   writes a `.M3EXPORTS` with the same units, templates and imports as
   cm3 (the others choose by `TARGET_OS` or need the C library ODBC);
   cm3's libraries `arithmetic`, `bitvector`, `sortedtableextras`,
   `tempfiles`, `m3tk-misc` and `m3tk`, built and installed by m3build,
   and the programs that use them build, and their tests print what the
   same tests built by cm3 print (`tools/build_test.py`). Stubs of
   network objects that m3build makes with stubgen compiled by m3j are
   those of stubgen built by cm3. m3build found an error of m3j's
   runtime: a child process whose output went to a file got a handle that
   only appends (the Java platform opens a file so for appending), which
   the programs of MSYS and Cygwin, such as Git's `echo` and `sed` on
   Windows, cannot write; m3j now opens an empty file for writing and
   writes the output to a file that is not empty through a thread. In
   cm3 on Windows, `hostname()` of quake is `amnesiac`, the value that
   `System.Hostname` of sysutils returns when `gethostname` of WinSock
   fails; m3j's is the name of the host.

   For editors, m3j has cm3's language server (`tools/langserver`,
   `m3_lang_server.jar`), built by m3build from its m3makefile: through
   the Language Server Protocol it gives the errors and warnings of a
   file while it is edited, the types of identifiers and the places of
   their declarations, from m3tk (compiled by m3j, as for stubgen), with
   the imports of a package from `m3build -tfile`. The libraries that it
   uses, json and libbuf, are in `lib`, made safe (their tests print what
   the same tests built by cm3 print). cm3's server was written for
   POSIX and the workspace of one package: it was changed for the file
   URIs of Windows, for a package for each file, for requests that come
   while it compiles, and for messages that are not as it expects; the
   tests of `tools/lsp_test.py` (sessions of the protocol, in a
   directory with a space in its name) found errors of the original
   (incremental changes over several lines, the declaration in a module
   whose interface has the same name, the escapes of JSON), of m3tk (a
   directory of the imports with a space in its name) and of m3j's
   compiler (a module that exports an interface whose procedures another
   module of the same directory also exports, as in m3tk, was left out).
   `tools/langserver/vscode` is an extension for Visual Studio Code: the
   language, its grammar and a client of the server of its own.

   The tree needs no cm3 to build m3j, to compile programs and to run the
   tests that compare with recorded results: the library that programs
   use (the units of m3core and libm3 that the programs and tests use,
   and the packages tcp, netobj, sysutils, json and libbuf, some changed for m3j) is in `lib` and in the jar, and the inputs of the tests are
   copied to `tests` (m3tests, the test programs and examples of the
   differential tests and the packages they use). Only the tools that
   compare with cm3 itself need a cm3 installation or its sources.
