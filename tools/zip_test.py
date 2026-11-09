#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Tests of the library zip of m3j, through tests/zip/src/Main.m3, against the
modules zlib, gzip and zipfile of Python. They need no connection to the
internet.

  python3 tools/zip_test.py [CASE]... [--java JAVA] [--jar JAR] [--work DIR]
                            [--keep] [--timeout SECONDS] [--mb N] [--speed]

The cases:

  read     streams made by Python, of all formats, levels and many sizes
  write    streams made by the library, read by Python
  chunks   the compressed data arrives in pieces of any size
  flush    after a flush the reader has everything written so far
  headers  all the optional parts of a gzip header, and invalid ones
  damage   each byte of a stream changed: accepted exactly if Python accepts
  cut      each prefix of a stream is reported as cut off
  members  several members, zero bytes and garbage after them
  limit    the limit of output, to the byte, also for a small heap
  feed     sources and targets that fail or block, the methods (expected
           output in tests/zip/expected)
  zipread  archives made by Python, with everything that they may contain
  badzip   archives that must be refused or fail
  zipwrite archives made by the library, read by Python
  safe     the names that SafeName accepts
  many     many streams one after the other do not pile up memory
  big      a large stream under a small heap, compared in both directions

--speed measures the rate, for information.
"""

import argparse
import gzip
import os
import random
import shutil
import struct
import subprocess
import sys
import tempfile
import time
import warnings
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests" / "zip"
SIZES = [0, 1, 2, 100, 32767, 32768, 32769, 65535, 65536, 65537, 200000]
FORMATS = ["gzip", "zlib", "raw"]


class Failed(Exception):
    pass


class Env:
    def __init__(self, args, work):
        self.args = args
        self.work = work
        self.java = args.java
        self.cp = str(work / "classes") + os.pathsep + args.jar
        self.batches = 0

    def compile(self):
        cmd = [self.java, "-Xss64m", "-cp", self.args.jar, "m3j.jvm.Compile", str(TESTS / "src" / "Main.m3"),
               "-d", str(self.work / "classes")]
        r = subprocess.run(cmd, cwd=self.work, capture_output=True, text=True, stdin=subprocess.DEVNULL)
        if r.returncode or "no module" in r.stdout + r.stderr:
            raise Failed("compile:\n" + r.stdout + r.stderr)

    def run(self, *a, heap=None):
        opts = ["-Xmx" + heap] if heap else []
        r = subprocess.run([self.java, *opts, "-cp", self.cp, "m3.Main", *a], cwd=self.work, capture_output=True,
                           timeout=self.args.timeout)
        if r.returncode:
            raise Failed("exit %d: %s" % (r.returncode, (r.stdout + r.stderr).decode("latin-1")[:2000]))
        if r.stderr:
            raise Failed("output on standard error: %s" % r.stderr.decode("latin-1")[:500])
        return r.stdout.decode("latin-1")

    def fresh(self):
        self.batches += 1
        d = self.work / ("b%d" % self.batches)
        d.mkdir()
        return d

    def decompress(self, docs):
        """Each (format, data, chunk, max, concat) gives (result line, output)."""
        d = self.fresh()
        lines = []
        for i, (fmt, data, chunk, mx, concat) in enumerate(docs):
            (d / ("%d.in" % i)).write_bytes(data)
            lines.append("d %s/%d.in %s/%d.out %s %d %d %d" % (d.name, i, d.name, i, fmt, chunk, mx, concat))
        (self.work / (d.name + ".list")).write_text("\n".join(lines) + "\n", newline="\n")
        self.run("batch", d.name + ".list")
        out = []
        for i in range(len(docs)):
            out.append(((d / ("%d.out.res" % i)).read_text().strip(), (d / ("%d.out" % i)).read_bytes()))
        return out

    def compress(self, docs):
        """Each (format, data, chunk, level, every, how) gives (result, output, marks)."""
        d = self.fresh()
        lines = []
        for i, (fmt, data, chunk, level, every, how) in enumerate(docs):
            (d / ("%d.in" % i)).write_bytes(data)
            lines.append("c %s/%d.in %s/%d.out %s %d %d %d %s" % (d.name, i, d.name, i, fmt, chunk, level, every, how))
        (self.work / (d.name + ".list")).write_text("\n".join(lines) + "\n", newline="\n")
        self.run("batch", d.name + ".list")
        out = []
        for i in range(len(docs)):
            marks = [tuple(map(int, x.split())) for x in (d / ("%d.out.marks" % i)).read_text().splitlines()]
            out.append(((d / ("%d.out.res" % i)).read_text().strip(), (d / ("%d.out" % i)).read_bytes(), marks))
        return out


def payloads(rng):
    out = []
    for n in SIZES:
        out.append(bytes(n))
        out.append(rng.randbytes(n))
        out.append((b"the quick brown fox %d " % rng.randrange(100) * (n // 20 + 1))[:n])
    return out


def pack(fmt, data, level=-1):
    if fmt == "gzip":
        return gzip.compress(data, compresslevel=9 if level < 0 else level, mtime=0)
    if fmt == "zlib":
        return zlib.compress(data, level)
    c = zlib.compressobj(level, zlib.DEFLATED, -15)
    return c.compress(data) + c.flush()


def unpack(fmt, data):
    if fmt == "gzip":
        return gzip.decompress(data)
    return zlib.decompress(data, 15 if fmt == "zlib" else -15)


def member(data=b"data", flags=0, extra=b"", name=b"", comment=b"", hcrc=None, method=8):
    """A gzip member with the optional parts that "flags" names."""
    h = b"\x1f\x8b" + bytes([method, flags]) + b"\0\0\0\0" + b"\0\xff"
    if flags & 4:
        h += struct.pack("<H", len(extra)) + extra
    if flags & 8:
        h += name + b"\0"
    if flags & 16:
        h += comment + b"\0"
    if flags & 2:
        h += struct.pack("<H", (zlib.crc32(h) & 0xFFFF) if hcrc is None else hcrc)
    c = zlib.compressobj(6, zlib.DEFLATED, -15)
    return h + c.compress(data) + c.flush() + struct.pack("<II", zlib.crc32(data), len(data) & 0xFFFFFFFF)


def strict_gzip(d):
    """gzip.decompress with what it leaves out for a single member: reserved
    flags and the checksum of the header."""
    if len(d) >= 4:
        flags = d[3]
        if flags & 0xE0:
            raise ValueError("reserved")
        if flags & 2:
            p = 10
            if flags & 4:
                if len(d) < p + 2:
                    raise ValueError("short")
                p += 2 + struct.unpack("<H", d[p:p + 2])[0]
            for bit in (8, 16):
                if flags & bit:
                    e = d.find(b"\0", p)
                    if e < 0:
                        raise ValueError("short")
                    p = e + 1
            if len(d) < p + 2 or struct.unpack("<H", d[p:p + 2])[0] != zlib.crc32(d[:p]) & 0xFFFF:
                raise ValueError("header crc")
    return gzip.decompress(d)


def check(cond, what):
    if not cond:
        raise Failed(what)


def case_read(env):
    rng = random.Random(1)
    docs, want = [], []
    for data in payloads(rng):
        for fmt in FORMATS:
            for level in (0, 1, 9):
                docs.append((fmt, pack(fmt, data, level), -1, 0, 1))
                want.append(data)
    for (res, out), w, doc in zip(env.decompress(docs), want, docs):
        check(res == "OK" and out == w, "%s of %d bytes: %s" % (doc[0], len(w), res))


def case_write(env):
    rng = random.Random(2)
    docs = []
    for data in payloads(rng):
        for fmt in FORMATS:
            for level, how in ((-1, "close"), (0, "finish"), (1, "close"), (9, "finish")):
                docs.append((fmt, data, 4096, level, 0, how))
    for (res, out, _), doc in zip(env.compress(docs), docs):
        check(res == "OK", res)
        check(unpack(doc[0], out) == doc[1], "%s of %d bytes" % (doc[0], len(doc[1])))
    # the same input gives the same output, and the header is as documented
    res = env.compress([("gzip", b"abc", 1, -1, 0, "close")] * 2)
    check(res[0][1] == res[1][1], "two runs differ")
    check(res[0][1][:10] == bytes.fromhex("1f8b08000000000000ff"), "header %s" % res[0][1][:10].hex())
    # level 0 stores, level 9 compresses
    res = env.compress([("raw", bytes(100000), 8192, lv, 0, "close") for lv in (0, 9)])
    check(len(res[0][1]) > 100000 > 1000 > len(res[1][1]), "levels: %d %d" % (len(res[0][1]), len(res[1][1])))


def case_chunks(env):
    rng = random.Random(3)
    data = rng.randbytes(1000) + bytes(70000) + b"xyz" * 3000
    docs = []
    for fmt in FORMATS:
        for chunk in (0, 1, 2, 3, 7, 64, 4096, 65537):
            docs.append((fmt, pack(fmt, data), chunk, 0, 1))
    docs.append(("gzip", member(data, 2 | 4 | 8 | 16, extra=b"ab" * 100, name=b"n" * 300, comment=b"c" * 300), 1, 0, 1))
    docs.append(("gzip", pack("gzip", data) + bytes(10) + pack("gzip", data), 1, 0, 1))
    for i, ((res, out), doc) in enumerate(zip(env.decompress(docs), docs)):
        check(res == "OK", "document %d: %s" % (i, res))
        check(out == data * (2 if i == len(docs) - 1 else 1), "document %d differs" % i)


def case_flush(env):
    rng = random.Random(4)
    data = rng.randbytes(50000) + b"text " * 40000
    docs = [(fmt, data, 777, 6, 5, "close") for fmt in FORMATS]
    # a flush whose output is more than the buffer of the writer takes
    docs += [(fmt, rng.randbytes(400000), 32768, 0, 1, "close") for fmt in FORMATS]
    docs += [(fmt, rng.randbytes(400000), 32768, 6, 1, "close") for fmt in FORMATS]
    wbits = {"gzip": 31, "zlib": 15, "raw": -15}
    for (res, out, marks), doc in zip(env.compress(docs), docs):
        data = doc[1]
        check(res == "OK" and marks, "%s: %s, %d marks" % (doc[0], res, len(marks)))
        d = zlib.decompressobj(wbits[doc[0]])
        got, pos = b"", 0
        for written, at in marks:
            got += d.decompress(out[pos:at])
            pos = at
            check(got == data[:written], "%s: after %d bytes, %d decoded" % (doc[0], written, len(got)))
        check(got + d.decompress(out[pos:]) + d.flush() == data, doc[0])
        check(unpack(doc[0], out) == data, doc[0])


def case_headers(env):
    data = b"payload"
    good = []
    for flags in range(32):
        good.append(member(data, flags, extra=b"xy", name=b"file.txt", comment=b"remark"))
    good.append(member(data, 4, extra=b""))
    good.append(member(data, 8, name=b""))
    good.append(member(data, 2 | 16, comment=b"\xe4\xf6"))
    docs = [("gzip", d, c, 0, 1) for d in good for c in (-1, 1)]
    for (res, out), doc in zip(env.decompress(docs), docs):
        check(res == "OK" and out == data, "%s: %s" % (doc[1][:12].hex(), res))
    bad = [
        (member(data, 0x20), "Deflate.Corrupt"),
        (member(data, 0x40), "Deflate.Corrupt"),
        (member(data, 0x80), "Deflate.Corrupt"),
        (member(data, method=7), "Deflate.Corrupt"),
        (member(data, 2, hcrc=0), "Deflate.Corrupt"),
        (b"\x1f\x8c" + member(data)[2:], "Deflate.Corrupt"),
        (b"PK\x03\x04" + member(data), "Deflate.Corrupt"),
        (member(data, 4, extra=b"x" * 10)[:14], "Deflate.Truncated"),
        (b"\x1f\x8b\x08\x08\0\0\0\0\0\xffname-without-end", "Deflate.Truncated"),
    ]
    docs = [("gzip", d, c, 0, 1) for d, _ in bad for c in (-1, 1)]
    wants = [w for _, w in bad for _ in (-1, 1)]
    for (res, out), want, doc in zip(env.decompress(docs), wants, docs):
        check(res == "ERROR " + want, "%s: %s, wanted %s" % (doc[1][:12].hex(), res, want))
    # the wrong sum of the data, and of its length
    d = member(data)
    for name, bad_doc in (("crc", d[:-8] + b"\0\0\0\0" + d[-4:]), ("length", d[:-4] + b"\1\0\0\0")):
        (res, _), = env.decompress([("gzip", bad_doc, -1, 0, 1)])
        check(res == "ERROR Deflate.Corrupt", "%s: %s" % (name, res))
    # a preset dictionary cannot be read
    c = zlib.compressobj(6, zlib.DEFLATED, 15, 8, zlib.Z_DEFAULT_STRATEGY, b"dictionary")
    (res, _), = env.decompress([("zlib", c.compress(b"dictionary") + c.flush(), -1, 0, 1)])
    check(res == "ERROR Deflate.Corrupt", "dictionary: %s" % res)


def case_damage(env):
    rng = random.Random(5)
    bases = [(fmt, pack(fmt, rng.randbytes(40) + b"abcabc" * 40)) for fmt in FORMATS]
    bases.append(("gzip", member(b"hello hello", 2 | 8, name=b"n")))
    docs = []
    for fmt, base in bases:
        for i in range(len(base)):
            for x in [rng.randrange(1, 256) for _ in range(3)] + [1, 0x80]:
                d = bytearray(base)
                d[i] ^= x
                docs.append((fmt, bytes(d), -1, 0, 1))
    accepted = 0
    for (res, out), doc in zip(env.decompress(docs), docs):
        try:
            want = strict_gzip(doc[1]) if doc[0] == "gzip" else unpack(doc[0], doc[1])
        except Exception:
            want = None
        if want is None:
            check(res.startswith("ERROR Deflate."), "%s accepted: %s" % (doc[0], doc[1].hex()))
        else:
            accepted += 1
            check(res == "OK" and out == want, "%s refused or wrong (%s): %s" % (doc[0], res, doc[1].hex()))
    print("    %d documents, %d of them accepted" % (len(docs), accepted))


def case_cut(env):
    rng = random.Random(6)
    docs = []
    for fmt, base in [(f, pack(f, rng.randbytes(50) + b"ab" * 50)) for f in FORMATS] + \
                     [("gzip", member(b"data", 2 | 4 | 8 | 16, extra=b"ex", name=b"n", comment=b"c"))]:
        for n in range(len(base)):
            docs.append((fmt, base[:n], -1, 0, 1))
    for (res, _), doc in zip(env.decompress(docs), docs):
        if doc[0] == "gzip" and not doc[1]:
            check(res == "OK", "empty gzip: %s" % res)
        else:
            check(res == "ERROR Deflate.Truncated", "%s of %d bytes: %s" % (doc[0], len(doc[1]), res))


def case_members(env):
    a, b = member(b"first"), member(b"second")
    cases = [
        (a + b, 1, "OK", b"firstsecond"),
        (a + bytes(5), 1, "OK", b"first"),
        (a + bytes(5) + b, 1, "OK", b"firstsecond"),
        (a + b + a, 1, "OK", b"firstsecondfirst"),
        (a + b"garbage", 1, "ERROR Deflate.Corrupt", None),
        (a + bytes(3) + b"x", 1, "ERROR Deflate.Corrupt", None),
        (a + b"\x1f", 1, "ERROR Deflate.Truncated", None),
        (a + b[:20], 1, "ERROR Deflate.Truncated", None),
        (a + b, 0, "OK", b"first"),
        (a + b"garbage", 0, "OK", b"first"),
        (bytes(3) + a, 1, "ERROR Deflate.Corrupt", None),
    ]
    docs = [("gzip", d, c, 0, concat) for d, concat, _, _ in cases for c in (-1, 1)]
    wants = [(res, out) for _, _, res, out in cases for _ in (-1, 1)]
    for (res, out), (wres, wout), doc in zip(env.decompress(docs), wants, docs):
        check(res == wres, "%s: %s, wanted %s" % (doc[1][:20].hex(), res, wres))
        if wres == "OK":
            check(out == wout, "%s: output %r" % (doc[1][:20].hex(), out))
        if wres == "OK" and doc[4]:
            check(gzip.decompress(doc[1]) == out, "Python disagrees")
    # zlib and raw leave what follows alone, as Python does
    for fmt in ("zlib", "raw"):
        d = pack(fmt, b"data") + b"more"
        (res, out), = env.decompress([(fmt, d, -1, 0, 1)])
        check(res == "OK" and out == b"data" == unpack(fmt, d), "%s: %s %r" % (fmt, res, out))
    (res, _), = env.decompress([("zlib", b"", -1, 0, 1)])
    check(res == "ERROR Deflate.Truncated", res)


def case_limit(env):
    data = b"x" * 100000
    docs, want = [], []
    for fmt in FORMATS:
        for mx, wr, wn in ((100000, "OK", 100000), (100001, "OK", 100000), (99999, "ERROR Deflate.TooLarge", 99999),
                           (1, "ERROR Deflate.TooLarge", 1), (32768, "ERROR Deflate.TooLarge", 32768)):
            docs.append((fmt, pack(fmt, data), -1, mx, 1))
            want.append((wr, wn))
    # over several members
    docs.append(("gzip", member(b"a" * 10) + member(b"b" * 10), -1, 15, 1))
    want.append(("ERROR Deflate.TooLarge", 15))
    docs.append(("gzip", member(b"a" * 10) + member(b"b" * 10), -1, 20, 1))
    want.append(("OK", 20))
    for (res, out), (wr, wn), doc in zip(env.decompress(docs), want, docs):
        check(res == wr and len(out) == wn, "%s max %d: %s, %d bytes" % (doc[0], doc[3], res, len(out)))
    # a gigabyte of zeros, under a small heap
    bomb = env.work / "bomb.gz"
    c = zlib.compressobj(9, zlib.DEFLATED, 31)
    block = bytes(1 << 20)
    with open(bomb, "wb") as f:
        for _ in range(1024):
            f.write(c.compress(block))
        f.write(c.flush())
    print("    bomb of %d bytes" % bomb.stat().st_size)
    out = env.run("bomb", "bomb.gz", "5000000", heap="32m").strip()
    check(out == "Deflate.TooLarge 5000000", out)
    out = env.run("bomb", "bomb.gz", "0", heap="32m").strip()
    check(out == "END %d" % (1 << 30), out)


def case_feed(env):
    out = env.run("feed")
    expected = (TESTS / "expected" / "feed.txt").read_text()
    if out != expected:
        (env.work / "feed.out").write_text(out, newline="\n")
        raise Failed("feed differs, see %s:\n%s" % (env.work / "feed.out", out))


def read_zip(env, data, mx=0):
    d = env.fresh()
    (d / "a.zip").write_bytes(data)
    env.run("zipread", d.name + "/a.zip", d.name + "/a.out", str(mx))
    return (d / "a.out").read_text().splitlines(), (d / "a.out.bin").read_bytes()


def hexname(s):
    b = s.encode("utf-16-be")
    return b.hex()


def case_zipread(env):
    rng = random.Random(7)
    entries = [
        ("empty.txt", b"", zipfile.ZIP_STORED),
        ("stored.bin", rng.randbytes(70000), zipfile.ZIP_STORED),
        ("deflated.txt", b"hello " * 50000, zipfile.ZIP_DEFLATED),
        ("dir/", b"", zipfile.ZIP_STORED),
        ("dir/inner.txt", b"inner", zipfile.ZIP_DEFLATED),
        ("\u00e4\u00f6\u00fc/\u20ac/\U0001f600.txt", b"unicode", zipfile.ZIP_DEFLATED),
        ("../odd/name.txt", b"odd", zipfile.ZIP_STORED),
    ]
    path = env.work / "z1.zip"
    with zipfile.ZipFile(path, "w") as z:
        z.comment = b"a comment"
        for name, data, method in entries:
            info = zipfile.ZipInfo(name, (2001, 2, 3, 4, 5, 6))
            info.compress_type = method
            z.writestr(info, data)
    lines, blob = read_zip(env, path.read_bytes())
    check(lines[0] == "N %d" % len(entries), lines[0])
    pos = 0
    with zipfile.ZipFile(path) as z:
        for line, info, (name, data, method) in zip(lines[1:], z.infolist(), entries):
            f = line.split()
            check(f[0] == "E" and f[1] == hexname(name), line)
            check(int(f[2]) == info.file_size and int(f[3]) == info.compress_size and int(f[4]) == info.CRC, line)
            check(f[5] == ("TRUE" if name.endswith("/") else "FALSE"), line)
            when = time.mktime((*info.date_time, 0, 0, -1))
            check(abs(float(f[6]) - when) < 3, "%s: time %s, wanted %s" % (name, f[6], when))
            check(int(f[7]) == len(data) and f[8] == "OK", line)
            check(blob[pos:pos + len(data)] == data, name)
            pos += len(data)
    check(pos == len(blob), "more bytes than the entries have")

    # a limit stops one entry
    lines, _ = read_zip(env, path.read_bytes(), mx=1000)
    check(lines[2].endswith(" 1000 Deflate.TooLarge"), lines[2])
    check(lines[5].endswith(" 5 OK"), lines[5])

    # more entries than the first format can count
    path = env.work / "z2.zip"
    n = 70000
    with zipfile.ZipFile(path, "w", allowZip64=True) as z:
        for i in range(n):
            z.writestr("f%d" % i, b"")
    lines, _ = read_zip(env, path.read_bytes())
    check(lines[0] == "N %d" % n and len(lines) == n + 1, lines[0])
    check(all(x.split()[1] == hexname("f%d" % i) for i, x in enumerate(lines[1:])), "order or names")
    check(all(x.endswith(" OK") for x in lines[1:]), "reading")

    # a name in code page 437
    lines, _ = read_zip(env, hand_zip("\u00e4".encode("cp437"), 0, 0, b"x"))
    check(lines[1].split()[1] == hexname("\u00e4"), lines[1])

    # an empty archive
    path = env.work / "z3.zip"
    with zipfile.ZipFile(path, "w"):
        pass
    lines, _ = read_zip(env, path.read_bytes())
    check(lines == ["N 0"], lines)


def hand_zip(name, flags, method, body, crc=None, entries=1):
    """An archive of one entry repeated, built byte by byte."""
    crc = zlib.crc32(body) if crc is None else crc
    fixed = struct.pack("<HHHHHIIIH", 20, flags, method, 0, 0x21, crc, len(body), len(body), len(name))
    local = b"PK\x03\x04" + fixed + b"\0\0" + name + body
    out, cen = b"", b""
    for _ in range(entries):
        cen += b"PK\x01\x02" + struct.pack("<H", 20) + fixed + struct.pack("<HHHHII", 0, 0, 0, 0, 0, len(out)) + name
        out += local
    return out + cen + b"PK\x05\x06" + struct.pack("<HHHHIIH", 0, 0, entries, entries, len(cen), len(out), 0)


def case_badzip(env):
    good = hand_zip(b"a.txt", 0, 0, b"hello")
    check(read_zip(env, good)[0][1].endswith(" 5 OK"), "the hand-made archive is valid")
    # stored data that does not match its CRC or its stated length
    lines, _ = read_zip(env, hand_zip(b"a.txt", 0, 0, b"hello", crc=1))
    check(lines[1].endswith(" Deflate.Corrupt"), lines[1])
    # deflated data that is damaged
    with tempfile.TemporaryDirectory() as t:
        p = Path(t) / "x.zip"
        with zipfile.ZipFile(p, "w", zipfile.ZIP_DEFLATED) as z:
            z.writestr("a", b"some data to compress " * 100)
        data = bytearray(p.read_bytes())
    data[50] ^= 0xFF
    lines, _ = read_zip(env, bytes(data))
    check(lines[1].endswith(" Deflate.Corrupt"), lines[1])

    refused = [
        (hand_zip(b"a.txt", 1, 0, b"hello"), "Zip.Unsupported"),
        (hand_zip(b"a.txt", 0, 99, b"hello"), "Zip.Unsupported"),
        (hand_zip(b"a.txt", 0, 0, b"hello", entries=2), "Zip.Duplicate"),
        (hand_zip(b"a\xffb", 0x800, 0, b"hello"), "Deflate.Corrupt"),
        (b"this is not an archive at all, " * 10, "Deflate.Corrupt"),
        (good[:-5], "Deflate.Corrupt"),
        (b"", "Deflate.Corrupt"),
    ]
    for data, want in refused:
        lines, _ = read_zip(env, data)
        check(lines == ["ERROR " + want], "%s: %s, wanted %s" % (data[:30].hex(), lines, want))
    # a file that is not there
    d = env.fresh()
    env.run("zipread", d.name + "/missing.zip", d.name + "/a.out", "0")
    check((d / "a.out").read_text().strip() == "ERROR OSError", "a missing file")


def write_zip(env, entries):
    d = env.fresh()
    lines = []
    for i, (name, millis, level, data) in enumerate(entries):
        path = ""
        if data is not None:
            path = "%s/%d.in" % (d.name, i)
            (env.work / path).write_bytes(data)
        lines.append("%s %d %d %s" % (hexname(name), millis, level, path))
    (d / "spec").write_text("".join(x + "\n" for x in lines), newline="\n")
    env.run("zipwrite", d.name + "/spec", d.name + "/o.zip")
    return d / "o.zip", (d / "o.zip.res").read_text().splitlines()


def case_zipwrite(env):
    rng = random.Random(8)
    when = 1000000000
    entries = [
        ("a.txt", when, -1, b"hello"),
        ("b.bin", when + 86400, 0, rng.randbytes(100000)),
        ("c.txt", when, 9, b"compress " * 100000),
        ("stored.txt", when, 0, b"compress " * 10000),
        ("empty", when, 1, b""),
        ("dir/", when, -1, None),
        ("\u00e4\u20ac\U0001f600.txt", when, -1, b"unicode"),
    ]
    path, res = write_zip(env, entries)
    check(res == ["OK"] * len(entries), res)
    with zipfile.ZipFile(path) as z:
        check(z.testzip() is None, "testzip")
        infos = z.infolist()
        check([i.filename for i in infos] == [e[0] for e in entries], [i.filename for i in infos])
        for info, (name, millis, level, data) in zip(infos, entries):
            check(z.read(info) == (data or b""), name)
            check(info.compress_type == zipfile.ZIP_DEFLATED or name.endswith("/"), name)
            want = time.mktime(time.localtime(millis)[:5] + (time.localtime(millis)[5] & ~1, 0, 0, -1))
            got = time.mktime((*info.date_time, 0, 0, -1))
            check(abs(got - want) < 3, "%s: time %s, wanted %s" % (name, got, want))
    # compressed sizes follow the level
    sizes = {i.filename: i.compress_size for i in infos}
    check(sizes["c.txt"] < 5000 and sizes["stored.txt"] >= 90000, sizes)
    # the second of two names is refused, and the archive stays good
    path, res = write_zip(env, [("x", when, -1, b"one"), ("x", when, -1, b"two"), ("y", when, -1, b"three")])
    check(res == ["OK", "ERROR Zip.Duplicate", "OK"], res)
    with zipfile.ZipFile(path) as z:
        check(z.testzip() is None and z.namelist() == ["x", "y"] and z.read("x") == b"one", z.namelist())
    # a name beyond the limit: in characters and in bytes
    for name in ("n" * 65001, "\u00e4" * 32501, "n" * 70000):
        path, res = write_zip(env, [(name, when, -1, b"z"), ("ok", when, -1, b"y")])
        check(res == ["ERROR Zip.Unsupported", "OK"], res)
        with zipfile.ZipFile(path) as z:
            check(z.testzip() is None and z.namelist() == ["ok"], z.namelist())
    # the longest names that fit
    for name in ("n" * 65000, "\u00e4" * 32500):
        path, res = write_zip(env, [(name, when, -1, b"z")])
        check(res == ["OK"], res)
        with zipfile.ZipFile(path) as z:
            check(z.namelist() == [name] and z.read(name) == b"z", "the longest name")
    # no entry at all
    path, res = write_zip(env, [])
    with zipfile.ZipFile(path) as z:
        check(z.namelist() == [], z.namelist())
    # more entries than the first format can count
    path, res = write_zip(env, [("f%d" % i, when, -1, None) for i in range(70000)])
    check(res == ["OK"] * 70000, "entries")
    with zipfile.ZipFile(path) as z:
        check(len(z.namelist()) == 70000 and z.testzip() is None, "70000 entries")
    # what the library wrote it can read
    lines, blob = read_zip(env, path.read_bytes())
    check(lines[0] == "N 70000", lines[0])


def case_safe(env):
    table = [
        ("a.txt", 1), ("a/b/c.txt", 1), ("dir/", 1), ("a..b", 1), ("..a/b", 1), ("a/..b", 1), ("...", 1), (".", 1),
        ("./a", 1), ("\u00e4/\u20ac", 1), ("ab:c", 1),
        ("", 0), ("/a", 0), ("/", 0), ("..", 0), ("../a", 0), ("a/..", 0), ("a/../b", 0), ("a\\b", 0), ("\\a", 0),
        ("..\\a", 0), ("C:", 0), ("C:/a", 0), ("c:a", 0), ("a:b", 0), ("a\0b", 0), ("\0", 0),
    ]
    p = env.work / "safe.list"
    p.write_text("".join(hexname(n) + "\n" for n, _ in table), newline="\n")
    out = env.run("safe", "safe.list").strip()
    want = "".join(str(w) for _, w in table)
    for (name, w), c in zip(table, out):
        check(str(w) == c, "%r: %s" % (name, c))
    check(out == want, out)


def peak_memory(popen):
    """The peak working set of a finished process, on Windows; else None."""
    if sys.platform != "win32":
        return None
    import ctypes
    from ctypes import wintypes

    class Counters(ctypes.Structure):
        _fields_ = [("cb", wintypes.DWORD), ("PageFaultCount", wintypes.DWORD), ("PeakWorkingSetSize", ctypes.c_size_t),
                    ("WorkingSetSize", ctypes.c_size_t), ("a", ctypes.c_size_t), ("b", ctypes.c_size_t),
                    ("c", ctypes.c_size_t), ("d", ctypes.c_size_t), ("PagefileUsage", ctypes.c_size_t),
                    ("PeakPagefileUsage", ctypes.c_size_t)]

    c = Counters()
    c.cb = ctypes.sizeof(c)
    fn = ctypes.WinDLL("psapi").GetProcessMemoryInfo
    fn.argtypes = [wintypes.HANDLE, ctypes.POINTER(Counters), wintypes.DWORD]
    if not fn(int(popen._handle), ctypes.byref(c), c.cb):
        return None
    return c.PeakWorkingSetSize


def case_many(env):
    n = 100000
    p = subprocess.Popen([env.java, "-Xmx1g", "-cp", env.cp, "m3.Main", "many", str(n)], cwd=env.work,
                         stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    out, err = p.communicate(timeout=env.args.timeout)
    check(p.returncode == 0 and not err, err.decode("latin-1")[:500])
    check(out.decode().strip() == str(28 * n), out)
    peak = peak_memory(p)
    if peak is not None:
        print("    %d streams, peak working set %d MB" % (n, peak >> 20))
        check(peak < 1 << 31, "peak working set of %d MB" % (peak >> 20))


def generated(mb):
    x = 1
    block = bytearray(1 << 20)
    for _ in range(mb):
        for i in range(1 << 20):
            if i % 64 == 0:
                x = (x * 1103515245 + 12345) % 2147483648
            block[i] = (x // 65536) % 64 + 32
        yield bytes(block)


def case_big(env):
    mb = env.args.mb
    out = env.work / "big.gz"
    env.run("big", str(mb), "big.gz", heap="64m")
    # Python reads what m3j wrote, checking every byte of a shorter generated
    # prefix and the length and sum of the whole
    d = zlib.decompressobj(31)
    total, crc = 0, 0
    with open(out, "rb") as f:
        while True:
            b = f.read(1 << 20)
            if not b:
                break
            part = d.decompress(b)
            total += len(part)
            crc = zlib.crc32(part, crc)
    check(d.eof and total == mb << 20, "total %d, eof %s" % (total, d.eof))
    want = 0
    for block in generated(min(mb, 16)):
        want = zlib.crc32(block, want)
    prefix = gzip.GzipFile(out).read(min(mb, 16) << 20)
    check(zlib.crc32(prefix) == want, "the generated bytes differ")
    r = env.run("bigread", "big.gz", str(mb), heap="64m").strip()
    check(r == "OK", r)
    print("    %d MB in %d MB" % (mb, out.stat().st_size >> 20))


def speed(env):
    rng = random.Random(9)
    words = [bytes(rng.choices(b"abcdefghijklmnopqrstuvwxyz", k=rng.randrange(2, 12))) for _ in range(5000)]
    data = b" ".join(rng.choices(words, k=4000000))[:100 << 20]
    (env.work / "speed.bin").write_bytes(data)
    print("    " + env.run("speed", "speed.bin").strip())


CASES = {"read": case_read, "write": case_write, "chunks": case_chunks, "flush": case_flush, "headers": case_headers,
         "damage": case_damage, "cut": case_cut, "members": case_members, "limit": case_limit, "feed": case_feed,
         "zipread": case_zipread, "badzip": case_badzip, "zipwrite": case_zipwrite, "safe": case_safe,
         "many": case_many, "big": case_big}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cases", nargs="*")
    ap.add_argument("--java", default=shutil.which("java") or "java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--timeout", type=float, default=900.0)
    ap.add_argument("--mb", type=int, default=2048)
    ap.add_argument("--speed", action="store_true")
    args = ap.parse_args()
    warnings.simplefilter("ignore")
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="zip_test"))
    work.mkdir(parents=True, exist_ok=True)
    env = Env(args, work)
    failed = []
    try:
        env.compile()
        if args.speed:
            speed(env)
            return 0
        for name in args.cases or CASES:
            t0 = time.time()
            try:
                CASES[name](env)
                print("ok    %-9s %.1f s" % (name, time.time() - t0))
            except Exception as e:
                failed.append(name)
                print("FAIL  %-9s %s" % (name, str(e)[:3000].encode("ascii", "backslashreplace").decode("ascii")))
    finally:
        if not args.keep and not args.work:
            shutil.rmtree(work, ignore_errors=True)
    print("%d failed" % len(failed) if failed else "all passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
