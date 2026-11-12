#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Takes the sources of JSch (the fork of mwiede, https://github.com/mwiede/jsch)
into m3j, for the library ssh: only what SSH commands and SFTP need, the
package com.jcraft.jsch renamed to m3j.jsch, of each class the newest of its
multi-release versions up to Java 25. Apart from the package name, the two
lines of the m3j notice and the few fixes in PATCHES the files stay as they
are, so that a new release of JSch is a new run of this script.

  python3 tools/jsch_import.py ARCHIVE [--out DIR] [--sha256 HEX]

ARCHIVE is the archive of the release tag from GitHub,
https://github.com/mwiede/jsch/archive/refs/tags/jsch-VERSION.zip. Its
SHA-256 must be the one below (or the one given with --sha256, for a new
release: then VERSION and SHA256 below change too). The files go to
src/vendor/jsch (or DIR), with ORIGIN (where they come from) and FILES (the
list of files written). A second run first removes exactly the files of that
list, nothing else.
"""

import argparse
import hashlib
import os
import re
import sys
import zipfile

VERSION = "2.28.8"
SHA256 = "d4bcde785b7af0399f378944100de54e80c3f809b4d8c518acbcfc90afa20c98"
URL = "https://github.com/mwiede/jsch/archive/refs/tags/jsch-%s.zip" % VERSION
JAVA = 25

OLD = "com.jcraft.jsch"
NEW = "m3j.jsch"

# What SSH commands and SFTP do not need, relative to com/jcraft/jsch: the
# packages for Bouncy Castle, zlib and GSS-API, the connections to loggers,
# agents and proxies, OpenSSH configuration files, and the algorithms that are
# not in the lists the bridge sets (old key exchanges, ciphers and MACs, DSA,
# ssh-rsa with SHA-1, Ed448). The CBC ciphers, 3DES-CBC, MD5 and SHA-1 stay:
# KeyPair decrypts PEM and PKCS#8 keys with them.
DROP_DIRS = ["bc/", "jzlib/", "juz/", "jgss/"]
DROP_FILES = [
    # loggers, agents, sockets, proxies, configuration, GSS-API
    "Slf4jLogger", "Log4j2Logger", "JulLogger", "JplLogger",
    "PageantConnector", "PageantFFMConnector", "SSHAgentConnector", "WindowsSSHAgentConnector",
    "AgentIdentityRepository", "JUnixSocketFactory", "UnixDomainSocketFactory", "USocketFactory",
    "ProxyHTTP", "ProxySOCKS4", "ProxySOCKS5", "JSchProxyException",
    "OpenSSHConfig", "UserAuthGSSAPIWithMIC", "GSSContext",
    "Argon2", "SCrypt", "BCrypt", "PBKDF", "PBKDF2", "FileChannelFactory", "JavaThreadId",
    # key exchanges
    "DH448", "DH25519SNTRUP761", "DHEC256MLKEM768", "DHEC384MLKEM1024",
    "DHG1", "DHG14", "DHG14224", "DHG15", "DHG15256", "DHG15384", "DHG15N", "DHG16384", "DHG17",
    "DHGEX1", "DHGEX224", "DHGEX384", "DHGEX512",
    # ciphers, MACs, hashes, signatures
    "jce/TripleDESCTR", "jce/BlowfishCBC", "jce/BlowfishCTR",
    "jce/ARCFOUR", "jce/ARCFOUR128", "jce/ARCFOUR256",
    "jce/HMACMD5", "jce/HMACMD596", "jce/HMACMD5ETM", "jce/HMACMD596ETM",
    "jce/HMACSHA196", "jce/HMACSHA196ETM",
    "jce/HMACSHA224SSHCOM", "jce/HMACSHA2562SSHCOM", "jce/HMACSHA256SSHCOM",
    "jce/HMACSHA384SSHCOM", "jce/HMACSHA512SSHCOM",
    "jce/SHA224", "jce/ECDH256", "jce/ECDH384", "jce/ECDH521", "jce/MLKEM1024",
    "jce/SignatureDSA", "jce/KeyPairGenDSA", "jce/SignatureRSA", "jce/SignatureEd448",
    "jce/SignatureRSASHA224SSHCOM", "jce/SignatureRSASHA256SSHCOM",
    "jce/SignatureRSASHA384SSHCOM", "jce/SignatureRSASHA512SSHCOM",
]

# Fixes, each an exact replacement in a converted file (relative to
# m3j/jsch). Every one must match exactly once; a new release that changed
# the place needs a look, and one that fixed it drops the entry.
#
# ECDSA keys in PEM and PKCS#8 with explicit parameters of the curve instead
# of its OID (ssh-keygen with LibreSSL writes them): JSch took nistp256 for
# them, and an ECDSA key in PEM got its key size, and with it the hash of its
# signatures, from the length of the private value, which may be shorter than
# the field. The curve now follows from the OID, else from the size of the
# public point, and the key size from the curve.
PATCHES = [
    ("KeyPairECDSA.java", """\
      for (int i = 0; i < oids.length; i++) {
        if (Util.array_equals(oids[i], oid_array)) {
          name = Util.str2byte(names[i]);
          break;
        }
      }
""", """\
      int curve = -1;
      for (int i = 0; i < oids.length; i++) {
        if (Util.array_equals(oids[i], oid_array)) {
          name = Util.str2byte(names[i]);
          curve = i;
          break;
        }
      }
"""),
    ("KeyPairECDSA.java", """\
      byte[][] tmp = fromPoint(Q_array);
      r_array = tmp[0];
      s_array = tmp[1];

      if (prv_array != null)
        key_size = prv_array.length >= 64 ? 521 : (prv_array.length >= 48 ? 384 : 256);
""", """\
      byte[][] tmp = fromPoint(Q_array);
      r_array = tmp[0];
      s_array = tmp[1];

      // Changed for m3j: without the OID of the curve (explicit parameters)
      // the size of the public point names the curve; the key size follows
      // the curve, as the private value may be shorter than the field
      if (curve < 0) {
        curve = r_array.length >= 66 ? 2 : (r_array.length >= 48 ? 1 : 0);
        name = Util.str2byte(names[curve]);
      }
      key_size = curve == 2 ? 521 : (curve == 1 ? 384 : 256);
"""),
    ("KeyPairPKCS8.java", """\
        if (!contents[1].isOBJECT()) {
          throw new ASN1Exception();
        }

        byte[] namedCurve = contents[1].getContent();
        byte[] name;
        if (!Util.array_equals(namedCurve, secp256r1)) {
""", """\
        // Changed for m3j: explicit parameters (a SEQUENCE) instead of the OID
        // of the curve; the curve then follows from the public point, when
        // KeyPairECDSA parses the key again below
        boolean explicit = contents[1].isSEQUENCE();
        if (!contents[1].isOBJECT() && !explicit) {
          throw new ASN1Exception();
        }

        byte[] namedCurve = contents[1].getContent();
        byte[] name;
        if (explicit) {
          name = null;
        } else if (!Util.array_equals(namedCurve, secp256r1)) {
"""),
    ("KeyPairPKCS8.java", """\
          if (!goo[0].isOBJECT()) {
            throw new ASN1Exception();
          }
          if (!Util.array_equals(goo[0].getContent(), namedCurve)) {
            throw new ASN1Exception();
          }
""", """\
          if (!explicit && !goo[0].isOBJECT()) {
            throw new ASN1Exception();
          }
          if (!explicit && !Util.array_equals(goo[0].getContent(), namedCurve)) {
            throw new ASN1Exception();
          }
"""),
]

NOTICE = ("// Changes for m3j: Copyright (c) 2026 the m3j authors, distributed\n"
          "// under the MIT license in the file LICENSE.\n")
NO_HEADER = "// From JSch, distributed under the license in licenses/BSD-JSCH.\n"


class Failed(Exception):
    pass


def version_of(directory):
    """The Java version of a source directory: java -> 8, java9 -> 9, ..."""
    if directory == "java":
        return 8
    m = re.fullmatch(r"java(\d+)", directory)
    return int(m.group(1)) if m else None


def select(archive):
    """The sources to take: a map from the path under com/jcraft/jsch to the
    text, of each file the version from the highest directory up to JAVA."""
    with zipfile.ZipFile(archive) as z:
        names = z.namelist()
        tops = {n.split("/")[0] for n in names}
        if len(tops) != 1:
            raise Failed("the archive has not one top directory: %r" % sorted(tops))
        top = tops.pop()
        best = {}
        for n in names:
            parts = n.split("/")
            if n.endswith("/") or parts[1:3] != ["src", "main"]:
                continue
            d = parts[3]
            rest = "/".join(parts[4:])
            if d == "java-templates":
                v = 8
            else:
                v = version_of(d)
                if v is None:
                    raise Failed("unknown source directory %s" % d)
            if v > JAVA or not rest.endswith(".java") or rest == "module-info.java":
                continue
            if not rest.startswith("com/jcraft/jsch/"):
                raise Failed("a source outside the package: %s" % n)
            rest = rest[len("com/jcraft/jsch/"):]
            if rest not in best or best[rest][0] < v:
                best[rest] = (v, n)
        files = {}
        for rest, (v, n) in best.items():
            files[rest] = z.read(n).decode("utf-8")
        licenses = {name: z.read(top + "/" + name).decode("utf-8")
                    for name in ("LICENSE.txt", "LICENSE.jBCrypt.txt")}
    return files, licenses


def strip(files):
    """Removes what is not needed; every entry of the lists must exist."""
    stale = []
    for d in DROP_DIRS:
        hit = [k for k in files if k.startswith(d)]
        if not hit:
            stale.append(d)
        for k in hit:
            del files[k]
    for f in DROP_FILES:
        k = f + ".java"
        if k not in files:
            stale.append(f)
        else:
            del files[k]
    if stale:
        raise Failed("not in this release, the lists need a look: %s" % ", ".join(stale))


def convert(text):
    """The package renamed, the version filled in, the m3j notice after the
    original one."""
    text = text.replace("${versionWithoutMinus}", VERSION)
    if "${" in text:
        raise Failed("a template variable is left")
    text = text.replace(OLD, NEW)
    if text.startswith("/*"):
        end = text.index("*/") + 2
        if not text.startswith("\n", end):
            raise Failed("unexpected end of the notice")
        return text[:end + 1] + NOTICE + text[end + 1:]
    if text.startswith("//"):
        lines = text.splitlines(keepends=True)
        i = 0
        while i < len(lines) and lines[i].startswith("//"):
            i += 1
        return "".join(lines[:i]) + NOTICE + "".join(lines[i:])
    return NO_HEADER + NOTICE + "\n" + text


def patch(files):
    """Applies PATCHES to the converted files."""
    for rest, old, new in PATCHES:
        if rest not in files:
            raise Failed("PATCHES: no file %s" % rest)
        n = files[rest].count(old)
        if n != 1:
            raise Failed("PATCHES: %d places in %s, the entry needs a look:\n%s" % (n, rest, old))
        files[rest] = files[rest].replace(old, new)


def remove_old(out):
    listing = os.path.join(out, "FILES")
    if not os.path.exists(listing):
        return
    with open(listing, encoding="utf-8") as f:
        old = [line.strip() for line in f if line.strip()]
    dirs = set()
    for rel in old:
        p = os.path.join(out, rel)
        if os.path.exists(p):
            os.remove(p)
        d = os.path.dirname(p)
        while os.path.normcase(os.path.abspath(d)) != os.path.normcase(os.path.abspath(out)):
            dirs.add(d)
            d = os.path.dirname(d)
    for d in sorted(dirs, key=len, reverse=True):
        if os.path.isdir(d) and not os.listdir(d):
            os.rmdir(d)
    os.remove(listing)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(text)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("archive")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                  "..", "src", "vendor", "jsch"))
    ap.add_argument("--sha256", default=SHA256)
    a = ap.parse_args()
    try:
        with open(a.archive, "rb") as f:
            digest = hashlib.sha256(f.read()).hexdigest()
        if digest != a.sha256:
            raise Failed("SHA-256 of the archive is %s, expected %s" % (digest, a.sha256))
        files, licenses = select(a.archive)
        strip(files)
        files = {rest: convert(text) for rest, text in files.items()}
        patch(files)
        out = os.path.normpath(a.out)
        remove_old(out)
        written = []
        for rest in sorted(files):
            rel = "m3j/jsch/" + rest
            write(os.path.join(out, rel), files[rest])
            written.append(rel)
        write(os.path.join(out, "ORIGIN"),
              "JSch %s of https://github.com/mwiede/jsch\n"
              "archive %s\n"
              "SHA-256 %s\n"
              "taken by tools/jsch_import.py: package %s renamed to %s, of each class\n"
              "the newest version up to Java %d, the parts that the library ssh does not\n"
              "need left out, the fixes in PATCHES of the script applied. The licenses\n"
              "are in licenses/BSD-JSCH and licenses/ISC-JBCRYPT.\n" % (VERSION, URL, digest, OLD, NEW, JAVA))
        written.append("ORIGIN")
        write(os.path.join(out, "FILES"), "".join(w + "\n" for w in written))
        root = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
        if os.path.normcase(out) == os.path.normcase(os.path.join(root, "src", "vendor", "jsch")):
            write(os.path.join(root, "licenses", "BSD-JSCH"), licenses["LICENSE.txt"])
            write(os.path.join(root, "licenses", "ISC-JBCRYPT"), licenses["LICENSE.jBCrypt.txt"])
        print("%d files in %s" % (len(files), out))
    except Failed as e:
        print("jsch_import: %s" % e, file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
