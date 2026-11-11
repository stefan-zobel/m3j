# Testing m3j

CM3 is the reference for everything:

- **Front end** (`m3j.front`, with the parts of `m3middle` and `m3core` it
  needs): on CM3's own libraries and on the m3tests suite it produces the
  same intermediate code and the same diagnostics as cm3 (see
  [`MODULA3-JVM.md`](../MODULA3-JVM.md)).
- **Back end** (`m3j.jvm`) and **runtime** (`m3j.rt`, and Java versions of
  the library interfaces in `m3.i`): the safe language, threads and the
  parts of libm3 and m3core that safe programs use; the other library
  modules are compiled from their sources, which m3j brings (`lib`). The
  program tests of m3tests (`p0`-`p2`, `r0`) give the expected output
  except for a few listed cases (`tools/run_jvm.py`), and every class of
  the compile tests `c0` and `c1` verifies. The design is in
  [`BACKEND.md`](BACKEND.md).

## Running the tests

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
JDK25=/path/to/jdk-25 python3 tools/zip_test.py                 # the library zip, against Python's zlib, gzip, zipfile
JDK25=/path/to/jdk-25 python3 tools/regexp_test.py              # the library regexp, against java.util.regex
JDK25=/path/to/jdk-25 python3 tools/lsp_test.py                 # the language server (and the client of the
                                                                # extension for Visual Studio Code, with node)
JDK25=/path/to/jdk-25 python3 tools/feedreader_test.py          # the example examples/feedreader (needs its
                                                                # driver of SQLite, see its README)
JDK25=/path/to/jdk-25 python3 tools/perf.py                     # benchmarks (tests/bench)
JDK25=/path/to/jdk-25 python3 tools/dump_test.py fingerprint
JDK25=/path/to/jdk-25 python3 tools/dump_test.py middle
```

The tests run on Windows and on Linux; [`tools/README.md`](../tools/README.md)
lists all of them and what each needs. Some compare with packages of
Python or with OpenSSH; on Ubuntu they come with

```sh
sudo apt-get install --no-install-recommends python3-asyncssh python3-cryptography \
  python3-nacl python3-python-multipart python3-requests-toolbelt openssh-sftp-server
```

(without `--no-install-recommends`, apt may also install and start an SSH
server).

A release, packed by `dist.sh` after `build.sh`, is tested on each system
by `python3 tools/release_test.py dist/m3j-VERSION.tar.gz`, which unpacks
it in a new directory and uses its start scripts. The tests of the SQL library need the drivers of H2 and SQLite in
the local repository of Maven (`~/.m2`), see `tools/sql_test.py`.

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

[`tools/README.md`](../tools/README.md) lists the tools, [`PORTING.md`](PORTING.md) the rules for porting
CM3 sources, [`BACKEND.md`](BACKEND.md) the design of the JVM back end.
