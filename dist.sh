#!/bin/sh
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
#
# Packs a release of m3j, after build.sh, from the files of the git
# repository and the jars that build.sh made: dist/m3j-VERSION.tar.gz, for
# every system. Build and pack on Linux, which keeps the executable bits of
# the scripts; the jars of m3j run on every system.
#
# A release has the jars, m3j.cfg, bin with the start scripts m3j and
# m3build (tools/dist), and of the repository lib, docs, examples, licenses
# and the extension for Visual Studio Code (tools/langserver/vscode), at the
# same paths. It has no AOT cache (m3j.aot), which belongs to one JDK and
# one path of the jar. Uses java of $JDK25 if set, else of $JAVA_HOME, else
# from the PATH, as build.sh.

set -e
cd "$(dirname "$0")"

if [ -z "$JDK25" ] && [ -n "$JAVA_HOME" ]; then JDK25=$JAVA_HOME; fi
if [ -n "$JDK25" ]; then JAVA="$JDK25/bin/java"; else JAVA=java; fi

V=$(tr -d ' \r\n' < VERSION)
NAME=m3j-$V
JARS="m3j.jar m3j-runtime.jar m3j-ssh.jar m3build.jar m3bundle.jar stubgen.jar m3_lang_server.jar"
for f in $JARS m3j.cfg; do
  [ -f "$f" ] || { echo "dist.sh: $f is missing: run build.sh first" >&2; exit 1; }
done
# the jars must be those of this version
got=$("$JAVA" -cp m3j.jar m3j.jvm.Compile -version | tr -d '\r')
if [ "$got" != "m3j version $V" ]; then
  echo "dist.sh: m3j.jar is \"$got\", VERSION is $V: run build.sh first" >&2
  exit 1
fi

D=dist/$NAME
rm -rf "$D" "dist/$NAME.tar.gz"
mkdir -p "$D/bin"
cp $JARS m3j.cfg VERSION LICENSE README.md "$D"
cp tools/dist/m3j tools/dist/m3build tools/dist/m3j.cmd tools/dist/m3build.cmd "$D/bin"
chmod 755 "$D/bin/m3j" "$D/bin/m3build"
# the files of the repository, with their executable bits
git ls-files -z lib docs examples licenses tools/langserver/vscode \
  | tar --null -cf - -T - | (cd "$D" && tar -xf -)
# and the scripts of the examples executable, as fetch-driver.sh
find "$D/examples" -name '*.sh' -exec chmod 755 {} +

# without the name of the user who packs
(cd dist && tar --owner=0 --group=0 --numeric-owner -czf "$NAME.tar.gz" "$NAME")
echo "packed dist/$NAME.tar.gz"
