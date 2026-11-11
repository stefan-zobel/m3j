# The Modula-3 library of m3j

`build.sh` puts this directory into `m3j.jar` (as the resource `lib`), and
the compiler finds the units of a program's library here, so no cm3
installation is needed. Each directory is a source package: its units are
in `src` and below, and the generic instances that a build of cm3 makes
(`AtomList` is `List (Atom)`) are in `AMD64_NT`, as in a cm3 installation.
In messages and runtime errors the compiler names a unit as cm3 does,
relative to the build directory of its package (`../src/rw/IO.m3`).

For m3build, each package has a `.M3EXPORTS` in `JVM`, as a package of a
cm3 installation has in `AMD64_NT`: its units, its imports and the quake
templates that it exports (`table.tmpl`, `sequence.tmpl`, `bundle.tmpl`,
`netobj.tmpl` and the others, copied from the sources of cm3 to the same
place). `tools/gen_lib_exports.py` writes them from those of cm3 for the
units that are here; m3core imports the package `m3j`.

## m3core and libm3

Copied from the packages m3core and libm3 of CM3 5.12.0 (the sources of
commit `20a295f`, as installed for the target AMD64_NT, which lists the
units of the target in `pkg/NAME/AMD64_NT/.M3EXPORTS`), with their
copyright notices (`COPYRIGHT-CMASS`, `src/COPYRIGHT`): the units that
the programs and tests of m3j use (the interfaces, generic units, generic
instances and safe modules that their compilation reads), and the safe
units that programs may want (`Boolean`, `Int32`, `Int64`, `WideChar`,
`Unicode`, `FmtTime`; the geometry of `Point`, `Rect`, `Interval`,
`Region`, `Transform` and others; `Sx`, `PQueue`, `MProperty`, `NullRd`,
`NullWr`, `RdCopy`, `RdUtils`, `Bundle`, `Rsrc`, `Terminal`,
`Capability`, `RealType`, `LongrealType`, `FloatExtras`; and all the
generic instances of the installation but those of pickles of version
2), at the places they have in cm3, and the interfaces `Word` and `Long`
(with `GenWord`, `WordRep` and `LongRep`), which the front end has built
in, for the tools that read their sources (m3tk, for the language
server). Not copied are the other units, the modules of the
interfaces that the runtime of m3j implements in Java (`src/main/java/m3`:
`Text`, `Fmt`, `Convert`, `Thread` and others) and the UNSAFE modules,
which m3j does not compile. A program that needs another unit of m3core or
libm3 gets it from a cm3 installation (`-cm3 DIR` or `-pkg DIR`); a unit
that a new test uses is copied here from the same version of cm3. The
units for POSIX that an installation for Windows lacks
(`libm3/src/os/POSIX/OSConfig.i3` and `PathnamePosix.m3`) come from the
sources of the same commit. Of a unit that is here for both systems, in
a directory `POSIX` and in one `WIN32` (`OSConfig.i3`), the compiler takes
the one of the system it runs on.

These modules are changed for m3j (each says so and why at its top):

| Module | Change |
|---|---|
| `m3core/src/text/TextConv.m3`, `libm3/src/text/TextExtras.m3`, `libm3/src/rw/Rd.m3`, `libm3/src/rw/TextWr.m3` | texts made with `Text.FromChars` instead of `Text8.Create` (`TEXT` is a Java string) |
| `libm3/src/rw/FileRd.m3`, `libm3/src/rw/FileWr.m3` | safe: the buffers are read and written through `M3jFile` instead of a `LOOPHOLE` to bytes; `FileRd` has a buffer of 64 KB instead of 4 KB, for reads of large blocks |
| `libm3/src/rw/Stdio.m3` | the standard files from `M3jFile` |
| `libm3/src/os/Common/FS.m3` | the file system of the Java platform (`M3jFS`) |
| `libm3/src/os/Common/Process.m3` | new, in place of the UNSAFE `ProcessWin32.m3` and `ProcessPosix.m3`: the processes of the Java platform (`M3jProc`), and a working directory of the program that `SetWorkingDirectory` changes |
| `libm3/src/geometry/Transform.m3` | safe: `Hash` takes the bits of the `REAL` numbers from `M3jFloat` instead of a `LOOPHOLE` |
| `libm3/src/rw/RdUtils.m3` | safe: `Find` takes the characters of the pattern with `Text.SetChars` (not from a `Text8.T`) |
| `libm3/src/uid/Common/Capability.m3` | safe: the bytes of the random number set one by one, and `Hash` computed from them, instead of with `LOOPHOLE` (the same bytes and hash as cm3 on AMD64_NT) |
| `libm3/src/os/Common/Pipe.m3` | `Open` makes a pipe of the Java platform (`M3jFS.Pipe`; in cm3 in `PipeWin32.m3` and `PipePosix.m3`) |
| `libm3/src/random/IEEE/ext64/RandomReal.m3` | the number computed with `Scalb` instead of packed with `LOOPHOLE` |
| `libm3/src/uid/Common/TimeStamp.m3` | safe: the 16 bytes set one by one instead of converted with `LOOPHOLE` |
| `libm3/src/hash/MD5.m3`, `libm3/src/hash/SHA256.m3` | safe: the digests of the Java platform (`M3jHash`) |
| `m3core/src/time/Common/Date.m3` | new, in place of the UNSAFE `DateWin32.m3` (`M3jDate`) |
| `m3core/src/weakref/WeakRef.m3` | the weak references of the Java platform (`M3jWeak`) instead of those of cm3's collector (`RTWeakRef`); a thread of the module calls the cleanup procedures, as cm3's collector does |
| `libm3/src/os/POSIX/PathnamePosix.m3`, `libm3/src/os/WIN32/PathnameWin32.m3` | export interfaces of their own (`PathnamePosix.i3`, `PathnameWin32.i3`, new) instead of `Pathname` |
| `libm3/src/os/Common/Pathname.m3` | new: calls `PathnamePosix` or `PathnameWin32` after the system that the program runs on (`M3jOS`) |
| `libm3/src/pickle/ver1/Pickle.m3`, `libm3/src/pickle/ver1/BuiltinSpecials.m3` | safe, with the same pickles as cm3: the bytes of a referent in the layout of cm3 come from `M3jPickle` instead of from memory, and `TEXT` is written as cm3 writes a text literal |
| `libm3/src/pickle/ver2/Pickle2.m3`, `libm3/src/pickle/ver2/BuiltinSpecials2.m3` | safe, with the same pickles as cm3, as `Pickle`: the data of a referent in the packing of AMD64_NT (cm3's converters `ConvertPacking` and `PklTipeMap`, which work on memory, are left out; on AMD64_NT they write the data as `Pickle` does), and a reader takes the data of referents in that packing only; the fingerprints that `PklFpMap` maps are mapped in `Pickle2.m3` |
| `libm3/src/pickle/ver2/PickleStubs.m3` | safe: numbers written and read byte by byte, in the packing of the writer, instead of with `LOOPHOLE` and `Swap` |
| `libm3/src/pickle/ver2/ConvertPacking.i3`, `libm3/src/pickle/ver2/ConvertPacking.m3` | only the kinds of conversions between packings (`CPKind`, `GetWordKind` and the others), which `PickleRd`, `PickleWr` and `PickleStubs` use |
| `m3core/src/runtime/common/RTPacking.m3` | safe: the packing of the host is that of cm3 on AMD64_NT, instead of being measured with addresses |

## tcp

The package tcp of CM3 5.12.0 (the sources of the same commit): TCP
connections, their readers and writers (`ConnRW`) and messages
(`ConnMsgRW`), and the addresses of IP. Its interfaces and safe modules
are copied with their copyright notices (`TCPMisc.i3` has the notice of
John D. Polstra in it). In place of cm3's UNSAFE modules, which call
Windows Sockets or the sockets of POSIX and C, the package has safe
modules on the sockets and the lookup of names of the Java platform
(`M3jNet`):

| Module | Change |
|---|---|
| `tcp/src/common/TCP.m3` | new, in place of `WIN32/TCP.m3`, `TCPPeer.m3` and `TCPExtras.m3`: exports `TCP`, `TCPMisc`, `TCPSpecial`, `TCPPeer` and `TCPExtras`, with the exceptions and lists of atoms of cm3's module for Windows; a thread that waits for a socket returns at once when it is alerted |
| `tcp/src/common/IP.m3` | new, in place of `WIN32/IP.m3`, `IPCommon.m3`, `IPInternal.m3` and `IP_c.c` |
| `tcp/src/common/IPError.m3` | with `RaiseOS` (in cm3 in `IPErrorWin32.m3`), which raises the atom without the number of an error |
| `tcp/src/common/ConnMsgRW.m3` | safe: the headers of fragments read and written byte by byte; and `nextMsg` empties the buffer that `Rd` keeps for `UnGetChar` (with cm3, the second message read to its end fails with a range error in `Rd.m3`) |

Where m3j differs from cm3 for Windows: `NewConnector` on the port of an
open connector raises `IP.PortBusy` (cm3 sets `SO_REUSEADDR`, which lets
a second socket take the port on Windows, not on POSIX); `TCPPeer.Match`
compares the first bits of the address, as its interface says (cm3
compares others on a little-endian machine); `GetAddrInfo` and
`GetNameInfo` know only some services by name (`ftp`, `ssh`, `telnet`,
`smtp`, `domain`, `http`, `pop3`, `nntp`, `imap`, `https`).

## tls

Not a package of cm3, which has no TLS: `TLS` (`src/TLS.i3`, `src/TLS.m3`,
safe), a `ConnFD.T` around another one, on the engine of the Java platform
(`M3jTls`, `m3j.rt.Tls`). The engine does no input or output; the module
moves the bytes over the connection below, so its timeouts and alerts stay.

## http

Not a package of cm3, whose `m3-www/http` has no keep-alive, no limits and a
broken `chunked`, so this is a new design that takes only some tables and
the outline of a few procedures from it (with the notice of Digital Equipment
Corporation, `http/src/COPYRIGHT`): `HTTP` (fields of messages, escapes,
dates, basic authentication), `HTTPServer` (safe; HTTP/1.1 on any
`ConnFD.T`) and `HTTPClient` (the client of the Java platform, through
`M3jHttp` and `m3j.rt.Http`). Both use `Deflate` of zip for
`Content-Encoding: gzip`. `HTTPCookie` (RFC 6265 and the rules of browsers)
and `Multipart` (streaming reader and writer of `multipart/form-data`, RFC
2046 and 7578) are safe Modula-3; the writer takes its random boundaries
from crypto, so http imports crypto and encoding.

## xml

Not a package of cm3, whose `sgml` is of another kind: `XmlReader` (safe), a
pull parser with namespaces, and two interfaces that it binds (`M3jXml`,
`M3jXmlFill`) with `<*EXTERNAL ... :JAVA*>`. The class `m3j.rt.Xml` is a layer on
the StAX parser of the Java platform; `docs/BACKEND.md` says why the parser is
not bound directly. `XmlWriter` (safe) writes documents, with `XmlName` for the
characters of names; both are plain Modula-3.

## zip

Not a package of cm3, which has no compression: `Deflate` (safe), readers and
writers for the gzip, zlib and raw formats, and `Zip` (safe), archives, on the
compressor and the ZIP classes of the Java platform (`M3jZip`, `m3j.rt.Zip`).
The Java side does no input or output; the modules move the bytes, so failures
and alerts of a stream stay in Modula-3. The format of gzip is read and written
in `Deflate`, because `GZIPInputStream` takes garbage after the data for its
end. `m3j.rt.Zip` compares the CRC and the length of the entries of an archive,
which `ZipFile` does not do.

## encoding

Not a package of cm3: `Hex`, `Utf8`, `Base64` (RFC 4648, strict), `URI`
(RFC 3986: parts, resolution, normalization, queries and escapes), `RFC3339`,
`RFC5322` (the times of mail and RSS) and `Html` (escaping, character
references), all safe and in plain Modula-3. The escapes and the basic authentication of
`HTTP` are built on `URI` and `Base64`.

## crypto

Not a package of cm3: `Crypto` (errors, random bytes, a comparison in constant
time), `Digest`, `Aead`, `Kdf`, `Signature`, `X25519` and `UUID`, safe, on the
digests, ciphers, signatures and key agreement of the Java platform (`M3jCrypto`,
`m3j.rt.Crypto`). The Java side makes the checks that the platform leaves out:
the length of a nonce of GCM, the size of an RSA key, points of curves, PBKDF2
with an empty password, and keys of other algorithms in PEM texts, which are
read in Modula-3 with the strict `Base64` of encoding. `CryptoRep` is shared by
the modules of the package only.

## ssh

Not a package of cm3: `SSH` (safe), commands on a server (`run` collects the
output, `start` gives a process with a writer for its input and readers for
its outputs), and `SFTP` (safe), files of the server as readers and writers,
their attributes, directories and links. Both are on a copy of JSch
(`m3j.jsch`, imported by `tools/jsch_import.py`, see
`src/vendor/jsch/ORIGIN`), through `M3jSsh` and `m3j.rt.Ssh`, which are in a
jar of their own, `m3j-ssh.jar`, next to `m3j-runtime.jar`: a program that
does not use ssh never loads them and does not need the jar. The .M3EXPORTS
of ssh names the jar with `java_classpath`, so m3build puts it on the class
path of a program that imports ssh; without m3build it is named with `-cp`.
A program that calls the library without the jar stops at the first call,
with a message that names it. The bridge
answers JSch itself: the key of the server is checked against known_hosts
(also hashed entries and `@revoked`), taken on first use (`AcceptNew`, which
appends a line and never takes a changed key) or compared with a fingerprint;
there is no way to take any key. The algorithms are those of JSch, fixed for
each session, so the properties `jsch.*` have no effect. The outputs of a
process go into buffers of the bridge, so an output that is not read never
stops the session. JSch takes data as fast as the server sends it and never
slows the server down, so a reader slower than its command ends the process
with `TooLarge` once `maxBuffer` bytes wait for it. Each `SFTP.T` has its own
thread; an alert or a timeout ends the wait of the caller, not the request,
and the `SFTP.T` is `Busy` while one of its files is open. `SSHRep` is shared
by the modules of the package only.

## sql

Not a package of cm3, though its interface follows the one of cm3's `DB`
(m3-db): `SQL` (safe), relational databases through JDBC (`java.sql`),
with statements whose values are parameters (`?`), typed getters for the
columns and errors with the SQLSTATE of the driver, through `M3jSql` and
`m3j.rt.Sql`. m3j brings no driver: a program names its jar with
`java_classpath` or `-cp`, and the bridge finds it as a service of the class
loader of the thread (`-run` sets that loader), else through `DriverManager`;
`Options.driver` names the class of a driver that is no service. All calls of
the driver for a connection run on one thread of its own, since an alert
interrupts the thread of the caller and a driver may close its files when an
interrupt hits its input or output: the caller waits for that thread, and an
alert or a timeout cancels the statement (`Statement.cancel`); a driver that
does not end the call within five seconds loses its connection. Values of
parameters stay in the bridge until the statement is executed. Rows come in
blocks of `fetch`, converted to a few Java types that the getters read
without the driver; texts, decimal numbers and dates of SQLite, which has no
types of columns, are converted from what it keeps. The kind of an error comes
from the class of the exception of the driver, else from the class of its
SQLSTATE, else, for SQLite, which has no SQLSTATE, from its result code.
sqlite-jdbc keeps a query for the generated keys of each `INSERT` open, which
would keep a cancel of SQLite in effect for every later statement of the
connection; the bridge closes it unless the statement was prepared with
`keys`. Rows that a program leaves open still keep such a cancel in effect
(SQLite ends it only when no statement runs), until they are closed.

## netobj

The package netobj of CM3 5.12.0 (the sources of the same commit): the
runtime of network objects (`src/netobjrt`: `NetObj`, `StubLib`,
`NetObjRT`, the agent, vouchers for readers and writers, the distributed
collection), their transport over TCP (`src/tcpnetobj`: `TCPNetObj`)
and the monitor of the collection (`NetObjMon`, with the stubs that the
package of cm3 has for it). The units that cm3 builds are copied with
their copyright notice (`src/COPYRIGHT`); those that it does not build
(`StubProt`, `ListPkl`, `AtomPkl`, `NetObjMonLocal`, `Protocol.m3`) are
left out. The stubs of a program's network objects are written by
stubgen (in `tests/stubgen`, which m3j compiles to run on the JVM). In
place of cm3's UNSAFE modules the package has safe ones that read and
write the bytes of the messages one by one, as cm3 has them on AMD64_NT
(64-bit integers, little endian, IEEE floating point):

| Module | Change |
|---|---|
| `netobj/src/netobjrt/StubLib.m3` | safe: the headers of messages and calls, integers, object IDs and wide characters read and written byte by byte in the buffers of the readers and writers of a connection instead of through `LOOPHOLE`; numbers of a sender with another representation converted as in cm3; a text written with 16-bit characters if it has characters beyond the first 256 (`Text.HasWideChars`), as m3j has no classes of texts; the bytes that align a number are zero |
| `netobj/src/netobjrt/VoucherStubs.m3` | safe: the headers of the messages of readers and writers of another program byte by byte; `SWFlush` leaves a surrogate writer the room that is left in the buffer of the connection (cm3 gives it room for the 8 bytes of the header more, so a writer that writes almost a buffer at once after a flush fails with a range error in `Wr.m3`) |
| `netobj/src/netobjrt/WireRep.m3` | safe: the time, the number and the address space of the ID of an object set and read byte by byte |
| `netobj/src/netobjrt/SpaceID.m3` | safe: the bytes of the time stamp copied one by one; their fingerprint from `Fingerprint.FromText` (`Fingerprint.OfEmpty` is not provided) |
| `netobj/src/tcpnetobj/HeaderOps.m3` | safe: the fixed part of the header of a connection byte by byte |
| `netobj/src/netobjrt/NetObjRT.m3` | a surrogate allocated by `M3jPickle.New` (cm3 calls `RTAllocator.NewTraced`, which m3j does not have); `CleanerEnqueue` puts a clean call that comes while the cleaner of a location makes its call at the head of the empty queue (cm3 puts it after the element that the cleaner has taken, where the cleaner does not find it, so that the owner keeps the object) |
| `netobj/src/netobjrt/WIN32/NetObjEpoch.i3` | the epoch 0, as for POSIX: a `Time.T` of m3j counts from 1970 on every system |

## sysutils

The package sysutils of CM3 5.12.0 (as installed for AMD64_NT, with the
generic instance `TextTextSeqTbl`): procedures on texts (`TextUtils`,
`TextReadingUtils`, `FastLex`), environments (`ProcessEnv`, `EnvUtils`),
files and directories (`FSUtils`, `DirStack`), pathnames (`PathRepr`),
messages (`SMsg`, `MsgX`, `MsgIF`), child processes with redirections and
pipes (`System`), and others. The copyright notice of elego Software
Solutions is in each file. The modules of cm3 for one system are merged
into modules for both, which follow the system that the program runs on
(`M3jOS`), and the UNSAFE ones are replaced:

| Module | Change |
|---|---|
| `sysutils/src/PathRepr.m3` | new, in place of `PathReprCommon.m3`, `WIN32/PathReprWin32.m3` and `POSIX/PathReprPosix.m3` |
| `sysutils/src/OSSpecials.m3` | new, in place of `WIN32/OSSpecialsWin32.m3` and `POSIX/OSSpecialsPosix.m3` (on Windows, `QuotedProcessArgument` puts a parameter in quotes) |
| `sysutils/src/System.m3` | with `Hostname` (from `M3jNet`) and `Wait` (`Process.Wait`), in place of the UNSAFE `WIN32/SystemWin32.m3` and `POSIX/SystemPosix.m3` |
| `sysutils/src/FSUtils.m3` | with `IsReadable`, `IsWritable` and `IsExecutable` (access of POSIX from `M3jFS`; on Windows as `FSysWin32` does it: the file is opened, and a program is known by its extension), in place of the UNSAFE `WIN32/FSysWin32.m3` and `POSIX/FSUnix_cm3.m3` |
| `sysutils/src/cm3/TextUtils.m3` | the texts made from arrays of characters with `Text.FromChars` instead of `Text8.Create` |

Where m3j differs from cm3 for Windows: `System.Hostname` gives the name
of the machine (cm3 gives `"amnesiac"` in a program that has not started
Windows Sockets, as its `gethostname` fails then).

## patternmatching

The package patternmatching of CM3: `RegEx`, the small regular expressions
of regex(3) (`.`, `[]`, `*`, `+`, `?`, `^`, `$`, `\(` `\)` and `\1` to
`\9`), `Glob`, shell patterns for file names, and `GlobTree`, expressions
of them with AND, OR and NOT. `RegEx.m3` and `GlobTree.m3` are copies; the
module `RegEx` is safe here (cm3's is UNSAFE only for calls that it had
already replaced). cm3's `Glob.m3` is UNSAFE and calls the function
`fnmatch` of C (from FreeBSD) through `Uglob`; m3j's `Glob.m3` is that
function written in Modula-3, safe, with all its options, and `Uglob` is
left out. As in C, a text ends at its first null character for `Glob`, and
`CaseFold` folds only the letters of ASCII. The notices are in each file and
in `licenses/COPYRIGHT-XEROX`, `BSD-POLSTRA` and `BSD-UCB`; the test against
cm3 is `check/patternmatching` of `tools/diff_test.py`.

`RegEx.Compile ("")` reads a variable that it has not set; in m3j it is
null, so the empty pattern matches every text, while in cm3 the result
depends on what was on the stack.

## regexp

Not a package of cm3: `Regexp` (safe), the regular expressions of the Java
platform (`java.util.regex`), through `M3jRegexp` and `M3jRegexpScan`, which
bind the classes `m3j.rt.Regexp` and `m3j.rt.RegexpScan`. The layer is there
for the limit: the engine reads a text only through `CharSequence.charAt`
(but for canonical equivalence, which is not offered), so the text is wrapped
in a sequence that counts the reads and stops the search when its budget,
`limit` times the characters searched plus 100 000, is spent; the pattern
stays usable. Measured on JDK 25: the wrapper costs 15 to 35 percent (75 for
a pattern of one character), and ordinary patterns read each character fewer
than seven times. Since Java 9 the engine remembers where a loop failed, so
the classic patterns of exponential time (`(a+)+$`, `(a|aa)*c`) are linear
there; bounded repetitions of a group (`(a{1,2}){1,40}b`), nested `.*`
(`(.*,){11}P`) and back references still explode, at 4 to 10 nanoseconds a
read. A group with alternatives repeated some 480 000 times overflows a stack
of 64 MB (a thread of `Thread.Fork`), some 7.7 million times the 1 GB of the
main thread; the layer catches the `StackOverflowError`. With a limit of 0
the text is passed bare. A `Match` holds the matcher of its search, so that
`Next` goes on as `Matcher.find` does (after an empty match, and for `\G`);
a match asked again for its successor repeats the searches from the start.
`Replace` checks the replacement before it searches, so a reference to a
group that the pattern does not have is an error even if nothing matches,
unlike in Java. The test is `tools/regexp_test.py`.

## json

Not the package json of cm3, whose module `Json` (a tree of nodes, searched
by paths) m3j does not bring: it kept arrays in the order of the texts of
their indexes, so that the element 10 came before the element 2, and it gave
strings as the text of the source, with their escapes. Its only user in cm3
is the language server, which m3j has moved to the two units of this
package, both m3j's own, in plain Modula-3: `JsonWriter` writes JSON piece by
piece to a `Wr.T`, and `JsonReader` reads it piece by piece from a `Rd.T`,
following RFC 8259 strictly and decoding strings.

## libbuf

The package libbuf of CM3 5.12.0 (`m3-libs/libbuf`, with the notice of
Digital Equipment Corporation, `src/COPYRIGHT`): a file or a text read
into an array of characters (`Buf`), and texts built in memory (`Wx`).

| Module | Change |
|---|---|
| `libbuf/src/Buf.m3` | safe: `FromFile` reads the file into bytes and copies them to the characters, instead of reading into the characters through a `LOOPHOLE` |
| `libbuf/src/Wx.m3` | `ToText` makes the text with `Text.FromChars` instead of `Text8.Create` |

## java

m3j's own package for the bindings of Java classes (see "Java APIs" in the
README of m3j). `Java` has the Java types as Modula-3 types
(`Java.Int`, ...), the implicit exception `Java.Exception` and the helpers
for any reference, boxes and arrays, implemented by the class
`m3.i.Java`. `JavaObject` and `JavaThrowable` bind `java.lang.Object` and
`java.lang.Throwable`; they are the types that other bindings extend, and
that object types can extend. The copies of `Java` are for Java arrays that
a program holds in a `REFANY`; a binding can take Modula-3 arrays directly.
cm3
cannot compile these interfaces, so `m3front_test.py` leaves them out.

## m3j

The units of m3j's own: interfaces of `<*EXTERNAL*>` procedures that the
runtime implements with the Java platform (`M3jFS`, `M3jDate`,
`M3jHash`, `M3jFloat`, `M3jRef`, `M3jNet`, `M3jProc`, methods of `m3j.rt.Externals`), `M3jFile`,
which reveals `File.T` and `RegularFile.T` as handles of `M3jFS`, and
`M3jPickle`, the parts of `Pickle` that depend on the representation of
values, which the runtime implements in Java (`m3.i.M3jPickle`), and
`M3jOS`, which tells a program whether it runs on a POSIX system or on
Windows (`m3.i.M3jOS`), and `M3jRef`, a hash of the identity of a
reference, for the tables that a program of cm3 keys by the address of
a reference (`LOOPHOLE (r, Word.T)`), and `M3jNet`, sockets of TCP that
do not block and the lookup of names of hosts, for the modules `TCP` and
`IP` of tcp (`m3j.rt.Net`), and `M3jProc`, the processes of the Java
platform for `Process` (`m3j.rt.Proc`), and `M3jWeak`, the weak
references of the Java platform for `WeakRef` (`m3j.rt.Weak`), and
`M3jTls`, the engine of TLS of the Java platform for the module `TLS` of
tls (`m3j.rt.Tls`), and `M3jHttp`, the client of HTTP of the Java platform
for the module `HTTPClient` of http (`m3j.rt.Http`), and `M3jZip`, the
compression and the archives of the Java platform for the modules `Deflate`
and `Zip` of zip (`m3j.rt.Zip`), and `M3jCrypto`, the cryptography of the
Java platform for the package crypto (`m3j.rt.Crypto`), and `M3jSsh`, the
copy of JSch for the package ssh (`m3j.rt.Ssh`), and `M3jSql`, JDBC of the
Java platform for the package sql (`m3j.rt.Sql`).
