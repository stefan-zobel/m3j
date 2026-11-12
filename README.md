# m3j - Modula-3 on the JVM

m3j compiles Modula-3 to Java bytecode. A compiled program needs nothing
but a JDK 25 or later: no C compiler and no cm3 installation.

The front end is a faithful Java port of the front end of [Critical Mass
Modula-3 (CM3)](https://github.com/modula3/cm3), which stays the reference:
on CM3's libraries and test suite, m3j gives the same diagnostics as cm3
and, but for a few listed cases, the same output. The back end is new,
written on the JDK's ClassFile API. m3j runs the safe language, with
threads, garbage collection and generics, the libraries of CM3 that safe
programs use, among them network objects, and CM3's build system and
language server. It adds libraries for today's work: HTTP and TLS, JSON
and XML, SQL, SSH, regular expressions, compression and cryptography; and
Modula-3 code calls Java classes directly. UNSAFE modules and the GUI
libraries of CM3 are not supported.

## Who might care

- those who know Modula-3 and want to run it, or their old code, on a
  current platform;
- those who want to see how a language with value records, nested
  procedures, structural type equivalence and exceptions maps onto the
  JVM: the article [Modula-3 on the JVM](https://stefan-zobel.github.io/m3j/)
  gives an overview, [`docs/BACKEND.md`](docs/BACKEND.md) explains every
  decision;
- those who look for a small, clean language with modules, objects and
  threads to teach or to learn from.

## Quick start

m3j needs JDK 25 or later. Its tests run on Windows, Linux and macOS
(arm64).

A release (`m3j-VERSION.tar.gz`, one for every system; `tar -xzf` unpacks
it on Windows too) is built: unpack it and put its directory `bin` on the PATH,
with the start scripts `m3j` (the compiler: `m3j Main.m3 -run`) and
`m3build`. From the sources, m3j is built with a POSIX shell (Linux, macOS,
or Git Bash on Windows), in the directory of m3j:

```sh
JDK25=/path/to/jdk-25 sh build.sh
```

(`sh dist.sh` then packs a release in `dist`.)

A program, `Main.m3`:

```modula-3
MODULE Main;

IMPORT IO;

BEGIN
  IO.Put ("Hello, world\n");
END Main.
```

runs at once, compiled in memory, or is compiled into a jar that runs with
the runtime of m3j (`M3J` stands for the directory of m3j; with `bin` on
the PATH, `m3j` stands for `java -cp $M3J/m3j.jar m3j.jvm.Compile` and
`m3build` for `java -jar $M3J/m3build.jar`):

```sh
java -cp $M3J/m3j.jar m3j.jvm.Compile Main.m3 -run
java -cp $M3J/m3j.jar m3j.jvm.Compile Main.m3 -d hello.jar
java -cp $M3J/m3j-runtime.jar:hello.jar m3.Main
```

A package is built by m3build from its `src/m3makefile`, as by cm3:

```
import ("libm3")
implementation ("Main")
program ("hello")
```

```sh
java -jar $M3J/m3build.jar     # in the directory above src
JVM/hello                      # JVM\hello.cmd on Windows
```

On Windows, the entries of a class path are separated by `;`.

## What is there

- **Libraries** ([`docs/LIBRARIES.md`](docs/LIBRARIES.md)): from CM3
  m3core, libm3, tcp, netobj, sysutils, libbuf and patternmatching, as safe
  programs use them ([`lib/README.md`](lib/README.md) lists what m3j
  changed); m3j's own: [TLS](docs/LIBRARIES.md#tls), an [HTTP](docs/LIBRARIES.md#http) server and
  client, [XML](docs/LIBRARIES.md#xml), [regular expressions](docs/LIBRARIES.md#regular-expressions),
  [JSON](docs/LIBRARIES.md#json), [compression](docs/LIBRARIES.md#compression),
  [encodings](docs/LIBRARIES.md#encodings), [cryptography](docs/LIBRARIES.md#cryptography),
  [SSH and SFTP](docs/LIBRARIES.md#ssh-and-sftp) and [SQL](docs/LIBRARIES.md#databases-sql)
  through JDBC.
- **Java classes** called from Modula-3, with their objects, fields,
  exceptions and callbacks, and Modula-3 object types that extend them
  ([`docs/USING.md`](docs/USING.md#java-apis)); m3jbind writes the
  interfaces.
- **m3build**, CM3's build system, for packages with m3makefiles
  ([`docs/USING.md`](docs/USING.md#packages-m3build)).
- **The language server** of CM3, with an extension for Visual Studio Code
  and settings for other editors
  ([`docs/USING.md`](docs/USING.md#editors-the-language-server)).
- **m3bundle and stubgen** of CM3, compiled by m3j, for resources and
  network objects.

## An example

[`examples/feedreader`](examples/feedreader) is a reader of news feeds
with a web interface: it fetches RSS and Atom feeds with several threads,
keeps them in SQLite with a full-text search, has a login, a JSON API and
live updates, and shows the HTML of the feeds in a sandbox. It is built by
m3build like any package and uses most of the libraries above.

## Documentation

| File | What it has |
|---|---|
| [`docs/USING.md`](docs/USING.md) | building m3j, the compiler and its options, resources, Java classes, m3build, editors |
| [`docs/LIBRARIES.md`](docs/LIBRARIES.md) | the libraries, with examples |
| [`docs/TESTING.md`](docs/TESTING.md) | what the tests show and how to run them |
| [`docs/BACKEND.md`](docs/BACKEND.md) | the design of the JVM back end |
| [`docs/PORTING.md`](docs/PORTING.md) | the rules for porting CM3 sources |
| [`MODULA3-JVM.md`](MODULA3-JVM.md) | the plan of m3j: ground rules, the size of the port, how the front end is tested against CM3 |
| [`lib/README.md`](lib/README.md) | the packages of `lib` and what m3j changed in those of CM3 |
| [`tools/README.md`](tools/README.md) | the tools and tests |
| [`examples/feedreader/README.md`](examples/feedreader/README.md) | the example: how to run it, a tour of its sources |

## Continuous integration

The workflow CI ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) runs the tests that need
only the tree (`tools/suite.py`) on Linux, Windows and macOS, once a week
and when started by hand in the Actions tab of GitHub. The workflow Release
([`.github/workflows/release.yml`](.github/workflows/release.yml)) builds and packs a release on Linux and
tests the archive on the three systems; started by hand it keeps the
archive as an artifact of the run, and for a pushed tag `vVERSION` it makes
a draft release with the archive and its `SHA256SUMS`.

## License

The sources written for m3j are distributed under the MIT license in
[`LICENSE`](LICENSE); their head says so (`SPDX-License-Identifier: MIT`). The
sources ported or copied from CM3 keep their original license; where m3j
changed one, a line in its head says that the changes are distributed
under the MIT license in [`LICENSE`](LICENSE).

The ported CM3 sources are distributed under their original licenses, in
[`licenses/`](licenses); each file there names the sources it covers:

| File | Code |
|---|---|
| [`COPYRIGHT-DEC`](licenses/COPYRIGHT-DEC) | the code from Digital Equipment Corporation |
| [`COPYRIGHT-CMASS`](licenses/COPYRIGHT-CMASS) | the code from Critical Mass |
| [`COPYRIGHT-COLUMBIA`](licenses/COPYRIGHT-COLUMBIA) | the code from Columbia University |
| [`COPYRIGHT-LUCENT`](licenses/COPYRIGHT-LUCENT) | the notice of David Gay's conversion code |
| [`MIT-BATES`](licenses/MIT-BATES) | `ArrayExpr` of the front end |
| [`MIT-MCKINNA`](licenses/MIT-MCKINNA) | the language server |
| [`COPYRIGHT-BSD`](licenses/COPYRIGHT-BSD) | the interfaces `MD5` and `SHA256` of libm3, and two examples in the tests |
| [`COPYRIGHT-OLIVETTI`](licenses/COPYRIGHT-OLIVETTI) | the toolkit m3tk (in stubgen and the language server) and `TextExtras` |
| [`COPYRIGHT-SUN`](licenses/COPYRIGHT-SUN) | parts of m3tk |
| [`COPYRIGHT-XEROX`](licenses/COPYRIGHT-XEROX) | `RegEx` and `FloatExtras` |
| [`COPYRIGHT-GENCAP`](licenses/COPYRIGHT-GENCAP) | `QPromise` of m3quake (in m3build) |
| [`BSD-ELEGO`](licenses/BSD-ELEGO) | the package sysutils |
| [`BSD-POLSTRA`](licenses/BSD-POLSTRA) | `Glob` and `GlobTree`, and the stream readers and writers of tcp |
| [`BSD-UCB`](licenses/BSD-UCB) | the function fnmatch of FreeBSD, after which `Glob` is written |

The sources copied from CM3 keep their notices: `lib` (m3core, libm3, tcp,
netobj, sysutils, libbuf and patternmatching, see [`lib/README.md`](lib/README.md)), `tools/langserver/src`
(CM3's language server), `tests/m3tests`,
`tests/diff/src`, `tests/pkg`, `tests/net`, `tests/m3front` (CM3's front end, see
[`tests/m3front/README.md`](tests/m3front/README.md)) and `tests/stubgen` (CM3's stub generator for
network objects, see [`tests/stubgen/README.md`](tests/stubgen/README.md)), and `tools/m3bundle` (CM3's m3bundle).

`src/vendor/jsch` is a part of JSch (https://github.com/mwiede/jsch), taken by
`tools/jsch_import.py` with the package renamed to `m3j.jsch`; it is distributed
under its license in [`BSD-JSCH`](licenses/BSD-JSCH), its bcrypt under [`ISC-JBCRYPT`](licenses/ISC-JBCRYPT),
in `m3j-ssh.jar` and `m3j.jar`.
Every file keeps its notice; the changes, the package name and a few fixes, are distributed
under the MIT license in [`LICENSE`](LICENSE). `src/vendor/jsch/ORIGIN` names the release.

A program built with m3j contains, compiled, the units of `lib` that it
uses, and so do the jars of the tools. Whoever passes on such a program
passes on the notices of those units with it: the ones in their heads,
which are also in [`licenses/`](licenses).

`tests/rosetta` holds solutions from Rosetta Code, which are available under
the GNU Free Documentation License 1.3, in [`GFDL-1.3`](licenses/GFDL-1.3) (see
[`tests/rosetta/README.md`](tests/rosetta/README.md)).

## Acknowledgments

As the licenses in [`BSD-POLSTRA`](licenses/BSD-POLSTRA) and [`COPYRIGHT-BSD`](licenses/COPYRIGHT-BSD) ask of
material that mentions features or use of the software they cover:

This product includes software developed by John D. Polstra.

This product includes software developed by the University of California,
Berkeley and its contributors.
