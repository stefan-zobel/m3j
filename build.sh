#!/bin/sh
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
# Build m3j (requires JDK 25 or later).
# Uses $JDK25 if set, else $JAVA_HOME if it is JDK 25 or newer, else javac
# and jar from the PATH.
set -e
cd "$(dirname "$0")"

if [ -z "$JDK25" ] && [ -n "$JAVA_HOME" ]; then
  v=$("$JAVA_HOME/bin/java" -XshowSettings:properties -version 2>&1 \
      | sed -n 's/ *java.specification.version = //p' | tr -d '\r')
  if [ -n "$v" ] && [ "$v" -ge 25 ] 2>/dev/null; then JDK25=$JAVA_HOME; fi
fi
if [ -n "$JDK25" ]; then
  JAVAC="$JDK25/bin/javac"; JAR="$JDK25/bin/jar"
else
  JAVAC=javac; JAR=jar
fi

rm -rf out && mkdir -p out
# JSch for the library ssh (src/vendor/jsch, taken by tools/jsch_import.py),
# without lint: it stays as it comes
"$JAVAC" --release 25 -Xlint:none -XDsuppressNotes -encoding UTF-8 -d out \
  $(find src/vendor/jsch -name '*.java')
"$JAVAC" --release 25 -Xlint:all -Werror -encoding UTF-8 -cp out -d out \
  $(find src/main/java -name '*.java')
# the Modula-3 library that m3j brings (lib: m3core, libm3, tcp, netobj, sysutils, json, libbuf and
# patternmatching of cm3, tls, http, xml, regexp, zip, encoding, crypto, ssh, sql, and the units of m3j),
# a resource of the jar
cp -r lib out/lib

# The version of m3j (the file VERSION) as the Implementation-Version of the jar.
V=$(tr -d ' \r\n' < VERSION)
printf 'Implementation-Title: m3j\nImplementation-Version: %s\n' "$V" > out.mf
"$JAR" --create --file m3j.jar --manifest out.mf -C out .
echo "built m3j.jar"

# The runtime jar: what a compiled program needs at run time (m3j.rt and
# the Java implementations of library interfaces, m3.i, with the
# fingerprints of m3j.fingerprint and m3j.util that m3.i.Fingerprint
# uses), without the compiler. A program compiled with -d runs with
# java -cp m3j-runtime.jar:<outdir> m3.Main
# The library ssh is in a jar of its own, m3j-ssh.jar: JSch in m3j.jsch and
# its bridge m3j.rt.Ssh, which a program that does not use ssh never loads.
# m3build puts it on the class path of a program that imports ssh (the
# .M3EXPORTS of ssh names it); without m3build such a program runs with
# java -cp m3j-runtime.jar:m3j-ssh.jar:<outdir> m3.Main
mkdir -p out/runtime/m3j out/ssh/m3j/rt
cp -r out/m3j/rt out/m3j/fingerprint out/m3j/util out/runtime/m3j
cp -r out/m3 out/runtime
mv out/runtime/m3j/rt/Ssh.class out/runtime/m3j/rt/Ssh\$*.class out/ssh/m3j/rt
cp -r out/m3j/jsch out/ssh/m3j
"$JAR" --create --file m3j-runtime.jar --manifest out.mf -C out/runtime .
echo "built m3j-runtime.jar"
"$JAR" --create --file m3j-ssh.jar --manifest out.mf -C out/ssh .
rm -f out.mf
echo "built m3j-ssh.jar"

if [ -n "$JDK25" ]; then JAVA="$JDK25/bin/java"; else JAVA=java; fi

# m3bundle of cm3 (tools/m3bundle), compiled by m3j: it writes an interface
# and a module that bring files into a program as a Bundle.T. With
# m3j-runtime.jar next to it: java -jar m3bundle.jar -name NAME
# [-element ELEMENT PATH]...
"$JAVA" -cp m3j.jar m3j.jvm.Compile tools/m3bundle/src/m3bundle.m3 -d m3bundle.jar
printf 'Class-Path: m3j-runtime.jar\n' > out.mf
"$JAR" --update --file m3bundle.jar --manifest out.mf
rm -f out.mf
echo "built m3bundle.jar"

# m3build (tools/m3build): the build system of m3j, made of cm3's quake
# interpreter (m3quake) and the parts of its driver that read m3makefiles,
# compiled by m3j; it compiles with the compiler in m3j.jar next to it,
# with the configuration m3j.cfg there. In the directory of a package:
# java -jar m3build.jar [-build|-clean|-realclean|-ship] [-root DIR] ...
B=tools/m3build/src
"$JAVA" -cp m3j.jar m3j.jvm.Compile $B/cm3/Main.m3 -I $B/cm3 -I $B/m3j -I $B/m3quake \
  -I $B/m3middle -I $B/etimer -I $B/derived -d m3build.jar
printf 'Class-Path: m3j.jar\n' > out.mf
"$JAR" --update --file m3build.jar --manifest out.mf
rm -f out.mf
cp tools/m3build/m3j.cfg m3j.cfg
echo "built m3build.jar"

# stubgen of cm3 (the stub generator of network objects, on its toolkit
# m3tk, in tests/stubgen), compiled by m3j: m3build runs it for netobj()
# in an m3makefile. With m3j-runtime.jar next to it:
# java -Xss64m -jar stubgen.jar ... M3J_STUBGEN=no skips it.
if [ "${M3J_STUBGEN:-yes}" != no ]; then
  S=tests/stubgen
  "$JAVA" -Xss64m -cp m3j.jar m3j.jvm.Compile -pkg $S/m3tk -pkg $S/m3tk-misc \
    $S/stubgen/src/*.i3 $S/stubgen/src/*.m3 -d stubgen.jar
  printf 'Class-Path: m3j-runtime.jar\n' > out.mf
  "$JAR" --update --file stubgen.jar --manifest out.mf
  rm -f out.mf
  echo "built stubgen.jar"
fi

# The language server (tools/langserver): cm3's server of the Language
# Server Protocol on m3tk (in tests/stubgen), built by m3build from its
# m3makefile; it runs m3build for the packages of the files it checks.
# Editors run it as java -jar m3_lang_server.jar, with m3j.jar next to it.
# M3J_LSP=no skips it.
if [ "${M3J_LSP:-yes}" != no ]; then
  (cd tools/langserver && "$JAVA" -jar ../../m3build.jar -override > ../../out.log 2>&1) \
    || { cat out.log; exit 1; }
  rm -f out.log
  cp tools/langserver/JVM/m3_lang_server.jar m3_lang_server.jar
  printf 'Class-Path: m3j.jar\n' > out.mf
  "$JAR" --update --file m3_lang_server.jar --manifest out.mf
  rm -f out.mf
  echo "built m3_lang_server.jar"
fi

# The AOT cache (JDK 25, JEP 483/514/515): the classes of the compiler and of
# the runtime, loaded and linked, with method profiles from a training run
# that compiles and runs tools/aot/Main.m3. Used with
# java -XX:AOTCache=m3j.aot -cp m3j.jar ... (also when the classes of a
# program follow the jar on the class path). It belongs to this jar at this
# (absolute) path and to this JDK: after a rebuild of the jar or with
# another JDK the JVM ignores it.
# M3J_AOT=no skips it.
if [ "${M3J_AOT:-yes}" != no ]; then
  rm -f m3j.aot
  # the jar by its absolute path, the path the cache is valid for
  HERE=$(pwd -W 2>/dev/null || pwd)
  "$JAVA" -XX:AOTCacheOutput="$HERE/m3j.aot" -cp "$HERE/m3j.jar" m3j.jvm.Compile tools/aot/Main.m3 -run > /dev/null 2>&1
  echo "built m3j.aot"
fi
