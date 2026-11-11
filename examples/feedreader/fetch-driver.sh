#!/bin/sh
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
#
# Fetches the JDBC driver of SQLite (sqlite-jdbc, under the Apache License
# 2.0) from Maven Central into lib, and checks it against its SHA-256.
# Run it once before the first build, in the directory of the feed reader.

set -e
cd "$(dirname "$0")"
VERSION=3.53.4.0
JAR=sqlite-jdbc-$VERSION.jar
SHA256=bcb1f51e36f940867e83342f9efbf5968ac44a6bef4d397bb4af7b17b45cd2fb
URL=https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/$VERSION/$JAR

mkdir -p lib
if [ -f "lib/$JAR" ]; then
  echo "lib/$JAR is there"
else
  curl -fL --proto =https -o "lib/$JAR.part" "$URL"
  mv "lib/$JAR.part" "lib/$JAR"
fi
if command -v sha256sum > /dev/null; then
  SUM=$(sha256sum "lib/$JAR" | cut -d ' ' -f 1)
else
  SUM=$(shasum -a 256 "lib/$JAR" | cut -d ' ' -f 1)
fi
if [ "$SUM" != "$SHA256" ]; then
  echo "lib/$JAR: wrong SHA-256 $SUM, removed" >&2
  rm -f "lib/$JAR"
  exit 1
fi
echo "lib/$JAR: SHA-256 checked"
