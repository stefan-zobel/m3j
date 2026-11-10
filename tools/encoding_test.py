#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Tests of the library encoding of m3j, through tests/encoding/src/Main.m3,
against the standard library of Python and the tables of the RFCs. They need
no connection to the internet.

  python3 tools/encoding_test.py [CASE]... [--java JAVA] [--jar JAR] [--work DIR]
                                 [--keep] [--timeout SECONDS]

The cases:

  hex       every byte, both cases, what is refused
  utf8      all pairs of bytes and many longer sequences, as Python decides
  base64    all short inputs and damaged encodings, in all modes
  escape    percent escapes against urllib.parse
  query     queries against urllib.parse, raw and in form coding
  uri       the parts of references, against tables and urllib.parse
  ipv6      IP literals, against the module ipaddress
  resolve   the examples of section 5.4 of RFC 3986
  normalize normalization by syntax
  rfc3339   formatting and parsing against datetime and tables
  breach    what must be a checked runtime error
"""

import argparse
import base64
import binascii
import calendar
import datetime
import ipaddress
import itertools
import os
import random
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests" / "encoding"
SPACE = b" \t\r\n"


class Failed(Exception):
    pass


def x(b):
    if isinstance(b, str):
        b = b.encode("latin-1")
    return "x" + b.hex()


def w(units):
    return "w" + "".join("%04x" % u for u in units)


def utf16(s):
    d = s.encode("utf-16-be", "surrogatepass")
    return [d[i] * 256 + d[i + 1] for i in range(0, len(d), 2)]


def unx(t):
    return bytes.fromhex(t[1:])


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
        if r.returncode or "no module" in r.stdout + r.stderr or "warning" in r.stdout + r.stderr:
            raise Failed("compile:\n" + r.stdout + r.stderr)

    def command(self, *a):
        return subprocess.run([self.java, "-cp", self.cp, "m3.Main", *a], cwd=self.work, capture_output=True,
                              timeout=self.args.timeout)

    def batch(self, lines):
        """The result line of each of the lines."""
        self.batches += 1
        name = "b%d" % self.batches
        (self.work / (name + ".in")).write_text("\n".join(lines) + "\n", newline="\n")
        r = self.command("batch", name + ".in", name + ".out")
        if r.returncode or r.stderr:
            raise Failed("exit %d: %s" % (r.returncode, (r.stdout + r.stderr).decode("latin-1")[:2000]))
        out = (self.work / (name + ".out")).read_text().splitlines()
        if len(out) != len(lines):
            raise Failed("%d results for %d lines" % (len(out), len(lines)))
        return out


def check(got, want, what):
    if got != want:
        raise Failed("%s: got %r, want %r" % (what, got, want))


def compare(env, lines, wants, shows=None):
    for i, (got, want) in enumerate(zip(env.batch(lines), wants)):
        if got != want:
            raise Failed("%s: got %r, want %r" % (shows[i] if shows else lines[i][:200], got[:200], want[:200]))


def case_hex(env):
    data = bytes(range(256))
    check(env.batch(["he " + x(data)]), [x(data.hex().encode())], "all bytes")
    lines, wants = [], []
    for i in range(256):
        for t in (bytes([i]).hex(), bytes([i]).hex().upper()):
            lines.append("hd " + x(t))
            wants.append(x(bytes([i])))
    good = set(b"0123456789abcdefABCDEF")
    for i in range(256):
        lines.append("hd " + x(bytes([i, 0x30])))
        lines.append("hd " + x(bytes([0x30, i])))
        v = (x(bytes([int(chr(i), 16) * 16])) if i in good else "no", x(bytes([int(chr(i), 16)])) if i in good else "no")
        wants.extend(v)
    for bad in [b"0", b"012", b"0 ", b" 0", b"0x"]:
        lines.append("hd " + x(bad))
        wants.append("no")
    lines.append("hdw " + w([0x4e2d, 0x30]))
    wants.append("no")
    lines.append("he " + x(b""))
    wants.append(x(b""))
    lines.append("hd " + x(b""))
    wants.append(x(b""))
    compare(env, lines, wants)


def decode_want(b):
    try:
        return w(utf16(b.decode("utf-8")))
    except UnicodeDecodeError:
        return "no"


def case_utf8(env):
    rng = random.Random(8)
    inputs = [bytes(p) for p in itertools.product(range(256), repeat=1)]
    inputs += [bytes(p) for p in itertools.product(range(256), repeat=2)]
    leads = [0x41, 0x7f, 0x80, 0xbf, 0xc0, 0xc1, 0xc2, 0xdf, 0xe0, 0xe1, 0xec, 0xed, 0xee, 0xef, 0xf0, 0xf1, 0xf3,
             0xf4, 0xf5, 0xff]
    seconds = [0x00, 0x7f, 0x80, 0x8f, 0x90, 0x9f, 0xa0, 0xbf, 0xc0, 0xff]
    inputs += [bytes(p) for p in itertools.product(leads, seconds, seconds)]
    inputs += [bytes(p) for p in itertools.product(leads, seconds, seconds, seconds)]
    for _ in range(20000):
        inputs.append(bytes(rng.choice(leads + seconds) for _ in range(rng.randint(1, 12))))
    compare(env, ["ud " + x(b) for b in inputs], [decode_want(b) for b in inputs])

    chars = [0, 0x41, 0x7f, 0x80, 0xe9, 0xff, 0x100, 0x7ff, 0x800, 0xd7ff, 0xe000, 0xfffd, 0xffff, 0x10000, 0x1f600,
             0x10ffff]
    texts = [[c] for c in chars] + [[a, b] for a in chars for b in chars]
    for _ in range(5000):
        texts.append([rng.choice(chars + [rng.randrange(0x110000)]) for _ in range(rng.randint(0, 20))])
    texts = [[c for c in t if not 0xd800 <= c < 0xe000] for t in texts]
    strings = ["".join(chr(c) for c in t) for t in texts]
    compare(env, ["ue " + w(utf16(s)) for s in strings], [x(s.encode("utf-8")) for s in strings])

    fffd = "\ufffd".encode("utf-8")
    table = [([0xd800], fffd), ([0xdc00], fffd), ([0xdfff], fffd), ([0xdbff], fffd), ([0xdc00, 0xd800], fffd + fffd),
             ([0xd800, 0x41], fffd + b"A"), ([0xd800, 0xd800, 0xdc00], fffd + "\U00010000".encode("utf-8")),
             ([0xd800, 0xdc00], "\U00010000".encode("utf-8")), ([0xdbff, 0xdfff], "\U0010ffff".encode("utf-8")),
             ([0xdc00, 0xdc00], fffd + fffd), ([0xdfff, 0xdc00, 0x41], fffd + fffd + b"A")]
    compare(env, ["ue " + w(t) for t, _ in table], [x(b) for _, b in table])
    compare(env, ["udw " + w([0x41, 0x4e2d]), "udw " + w([0xc3, 0xa9])], ["no", w([0xe9])])


def reference_b64(s, alphabet, padding, space):
    """What Decode must do, by the module binascii of Python."""
    if space:
        s = bytes(c for c in s if c not in SPACE)
    if alphabet == 1:
        if b"+" in s or b"/" in s:
            return None
        s = s.replace(b"-", b"+").replace(b"_", b"/")
    bare = s.rstrip(b"=")
    has = len(s) != len(bare)
    if padding == 1 and has:
        return None
    if padding == 0 and not has and len(s) % 4:
        return None
    if not has:
        s += b"=" * (-len(s) % 4)
    if len(s) % 4:
        return None
    try:
        out = binascii.a2b_base64(s, strict_mode=True)
    except binascii.Error:
        return None
    if base64.b64encode(out) != s:
        return None
    return out


def encoded(data, alphabet, pad):
    s = base64.b64encode(data)
    if alphabet == 1:
        s = s.replace(b"+", b"-").replace(b"/", b"_")
    return s if pad else s.rstrip(b"=")


def case_base64(env):
    rng = random.Random(64)
    lines, wants = [], []

    def probe(s, alphabet, padding, space):
        out = reference_b64(s, alphabet, padding, space)
        lines.append("bd %d %d %d %s" % (alphabet, padding, space, x(s)))
        wants.append(x(out) if out is not None else "no")

    enc_lines, enc_wants = [], []
    for n in list(range(0, 301)) + [3000, 3001, 3002]:
        data = bytes(rng.randrange(256) for _ in range(n))
        for alphabet, pad in itertools.product((0, 1), (0, 1)):
            enc_lines.append("be %d %d %s" % (alphabet, pad, x(data)))
            s = encoded(data, alphabet, pad)
            enc_wants.append(x(s))
            for padding, space in itertools.product((0, 1, 2), (0, 1)):
                probe(s, alphabet, padding, space)
    compare(env, enc_lines, enc_wants)

    symbols = b"AQ/+-_= \n"
    for size in range(0, 5):
        for p in itertools.product(symbols, repeat=size):
            for alphabet, padding, space in itertools.product((0, 1), (0, 1, 2), (0, 1)):
                probe(bytes(p), alphabet, padding, space)

    for _ in range(4000):
        s = bytearray(encoded(bytes(rng.randrange(256) for _ in range(rng.randint(1, 40))), rng.randint(0, 1), 1))
        for _ in range(rng.randint(1, 3)):
            kind = rng.randint(0, 3)
            pos = rng.randrange(len(s) + 1)
            if kind == 0 and pos < len(s):
                s[pos] = rng.choice(b"AQ=+/-_ \n\r\t\x00\xff.")
            elif kind == 1:
                s.insert(pos, rng.choice(b"= \n+-"))
            elif kind == 2 and pos < len(s):
                del s[pos]
            elif s:
                s.pop()
        for alphabet, padding, space in itertools.product((0, 1), (0, 1, 2), (0, 1)):
            probe(bytes(s), alphabet, padding, space)
    compare(env, lines, wants)

    table = [("QQ==", 0, 0, 0, b"A"), ("QUI=", 0, 0, 0, b"AB"), ("QUJD", 0, 0, 0, b"ABC"), ("QR==", 0, 0, 0, None),
             ("QUJ=", 0, 0, 0, None), ("QQ", 0, 0, 0, None), ("QQ", 0, 1, 0, b"A"), ("QQ==", 0, 1, 0, None),
             ("QQ", 0, 2, 0, b"A"), ("QQ==", 0, 2, 0, b"A"), ("QQ=", 0, 2, 0, None), ("Q", 0, 2, 0, None),
             ("Q===", 0, 0, 0, None), ("====", 0, 0, 0, None), ("=", 0, 2, 0, None), ("QQ==QQ==", 0, 0, 0, None),
             ("Q Q==", 0, 0, 0, None), ("Q Q=\n=", 0, 0, 1, b"A"), ("-_-_", 0, 0, 0, None), ("+/+/", 1, 0, 0, None),
             ("-_-_", 1, 0, 0, b"\xfb\xff\xbf"), ("", 0, 0, 0, b""), ("", 1, 1, 0, b"")]
    compare(env, ["bd %d %d %d %s" % (a, p, s, x(t)) for t, a, p, s, _ in table],
            [x(o) if o is not None else "no" for _, _, _, _, o in table])
    check(env.batch(["bdw " + w([0x4e2d, 0x41, 0x41, 0x41])]), ["no"], "wide")


def case_escape(env):
    rng = random.Random(37)
    samples = [bytes(range(256)), b"", b"a b+c%d/e", b"%", b"+", b" "]
    samples += [bytes(rng.randrange(256) for _ in range(rng.randint(0, 40))) for _ in range(2000)]
    lines, wants = [], []
    for s in samples:
        lines.append("ee 0 0 " + x(s))
        wants.append(x(urllib.parse.quote_from_bytes(s, safe="").encode()))
        lines.append("ee 1 0 " + x(s))
        wants.append(x(urllib.parse.quote_from_bytes(s, safe="/").encode()))
        lines.append("ee 0 1 " + x(s))
        wants.append(x(urllib.parse.quote_from_bytes(s, safe="").replace("%20", "+").encode()))
        q = urllib.parse.quote_from_bytes(s, safe="").encode()
        lines.append("eu 0 " + x(q))
        wants.append(x(s))
        lines.append("eu 1 " + x(q.replace(b"%20", b"+")))
        wants.append(x(s))
    compare(env, lines, wants)

    table = [("ee 2 0 " + x(b"%+ a"), x(b"%25+ a")), ("ee 2 1 " + x(b"%+ a"), x(b"%25%2B+a")),
             ("eu 0 " + x(b"a+b"), x(b"a+b")), ("eu 1 " + x(b"a+b"), x(b"a b")), ("eu 0 " + x(b"%7e%7E"), x(b"~~")),
             ("eu 0 " + x(b"%"), "no"), ("eu 0 " + x(b"%4"), "no"), ("eu 0 " + x(b"%4g"), "no"),
             ("eu 0 " + x(b"%g4"), "no"), ("eu 0 " + x(b"a%"), "no"), ("eu 1 " + x(b"%zz"), "no"),
             ("euw " + w([0x4e2d]), "no")]
    compare(env, [a for a, _ in table], [b for _, b in table])


def pairs_of(line):
    t = line.split(" ")
    return [(unx(t[i]), unx(t[i + 1])) for i in range(1, len(t), 2)]


def case_query(env):
    rng = random.Random(55)
    queries = []
    for _ in range(3000):
        raw = [(bytes(rng.choice(b"ab c+=&%\xe9/") for _ in range(rng.randint(0, 6))),
                bytes(rng.choice(b"xy z+=&%\xe9/") for _ in range(rng.randint(0, 6))))
               for _ in range(rng.randint(0, 5))]
        queries.append(raw)
    lines, wants = [], []
    for raw in queries:
        lines.append("qf 1 " + " ".join(x(a) + " " + x(b) for a, b in raw) if raw else "qf 1")
        wants.append(x(urllib.parse.urlencode([(a, b) for a, b in raw], quote_via=urllib.parse.quote_plus,
                                              safe="").encode()))
    compare(env, lines, wants)

    lines, shows = [], []
    for raw in queries:
        q = urllib.parse.urlencode(raw, quote_via=urllib.parse.quote_plus, safe="")
        lines.append("qp 1 " + x(q))
        shows.append(q)
    got = env.batch(lines)
    for q, line in zip(shows, got):
        want = [(a.encode("latin-1"), b.encode("latin-1")) for a, b in
                urllib.parse.parse_qsl(q, keep_blank_values=True, encoding="latin-1", errors="strict",
                                       separator="&")]
        check(pairs_of(line) if line != "ok" else [], want, "query %r" % q)

    rawq = [("a=1&b=2", [(b"a", b"1"), (b"b", b"2")]), ("a", [(b"a", b"")]), ("a=", [(b"a", b"")]), ("=b", [(b"", b"b")]),
            ("", []), ("&&", []), ("a=1&&b=2&", [(b"a", b"1"), (b"b", b"2")]), ("a=b=c", [(b"a", b"b=c")]),
            ("a+b=%41%zz+", [(b"a+b", b"%41%zz+")]), ("a;b=c", [(b"a;b", b"c")]), ("=", [(b"", b"")]),
            ("a&b=c", [(b"a", b""), (b"b", b"c")]), ("a&&b&c=d=e", [(b"a", b""), (b"b", b""), (b"c", b"d=e")])]
    got = env.batch(["qp 0 " + x(q) for q, _ in rawq])
    for (q, want), line in zip(rawq, got):
        check(pairs_of(line) if line != "ok" else [], want, "raw query %r" % q)
    table = [("qp 1 " + x(b"a=%zz"), "no"), ("qp 1 " + x(b"%=b"), "no"), ("qp 1 " + x(b"a=%4"), "no"),
             ("qpw 1 " + w([0x61, 0x4e2d]), "no"), ("qpw 0 " + w([0x61, 0x4e2d]), "no"),
             ("qn", "0"), ("qf 0", x(b"")), ("qf 0 " + x(b"a+b") + " " + x(b"%41 c"), x(b"a+b=%41 c")),
             ("qf 0 " + x(b"a") + " " + x(b"") + " " + x(b"b") + " " + x(b"c"), x(b"a=&b=c")),
             ("qf 1 " + x(b"a b") + " " + x(b"+&=%"), x(b"a+b=%2B%26%3D%25"))]
    compare(env, [a for a, _ in table], [b for _, b in table])


def fields(scheme=None, user=None, host=None, port=-1, path="", query=None, fragment=None):
    def o(v):
        return "-" if v is None else x(v)
    return " ".join([o(scheme), o(user), o(host), str(port), x(path), o(query), o(fragment)])


VALID = [
    ("ftp://ftp.is.co.za/rfc/rfc1808.txt", fields("ftp", None, "ftp.is.co.za", -1, "/rfc/rfc1808.txt")),
    ("http://www.ietf.org/rfc/rfc2396.txt", fields("http", None, "www.ietf.org", -1, "/rfc/rfc2396.txt")),
    ("ldap://[2001:db8::7]/c=GB?objectClass?one",
     fields("ldap", None, "[2001:db8::7]", -1, "/c=GB", "objectClass?one")),
    ("mailto:John.Doe@example.com", fields("mailto", path="John.Doe@example.com")),
    ("news:comp.infosystems.www.servers.unix", fields("news", path="comp.infosystems.www.servers.unix")),
    ("tel:+1-816-555-1212", fields("tel", path="+1-816-555-1212")),
    ("telnet://192.0.2.16:80/", fields("telnet", None, "192.0.2.16", 80, "/")),
    ("urn:oasis:names:specification:docbook:dtd:xml:4.1.2",
     fields("urn", path="oasis:names:specification:docbook:dtd:xml:4.1.2")),
    ("http://a", fields("http", host="a")),
    ("http://a?", fields("http", host="a", query="")),
    ("http://a#", fields("http", host="a", fragment="")),
    ("http://a?#", fields("http", host="a", query="", fragment="")),
    ("http://u:p@h:0/x?q#f", fields("http", "u:p", "h", 0, "/x", "q", "f")),
    ("http://@h", fields("http", "", "h")),
    ("http://h:65535", fields("http", None, "h", 65535)),
    ("file:///x", fields("file", host="", path="/x")),
    ("http://[::1]:8/", fields("http", None, "[::1]", 8, "/")),
    ("http://[v1a.b:c!]/", fields("http", None, "[v1a.b:c!]", -1, "/")),
    ("HTTP://EX.com/%7e", fields("HTTP", None, "EX.com", -1, "/%7e")),
    ("a+b-c.d:e", fields("a+b-c.d", path="e")),
    ("http://a%41b/", fields("http", None, "a%41b", -1, "/")),
    ("http://h/a:b@c!$&'()*+,;=-._~%20", fields("http", None, "h", -1, "/a:b@c!$&'()*+,;=-._~%20")),
    ("http://h/?a/b?c:@", fields("http", None, "h", -1, "/", "a/b?c:@")),
    ("http://h/#a/b?c:@", fields("http", None, "h", -1, "/", None, "a/b?c:@")),
    ("//h/p", fields(None, None, "h", -1, "/p")),
    ("//", fields(None, None, "")),
    ("", fields()),
    ("?", fields(query="")),
    ("#", fields(fragment="")),
    ("/", fields(path="/")),
    ("a", fields(path="a")),
    ("a/b:c", fields(path="a/b:c")),
    ("./a:b", fields(path="./a:b")),
    ("/a:b", fields(path="/a:b")),
    ("/a//b", fields(path="/a//b")),
]

ROUND_TRIP_ONLY_OTHERWISE = [("http://h:/", fields("http", None, "h", -1, "/")), ("http://h:", fields("http", None, "h"))]

INVALID = ["http://a b/", "a\\b", "http:\\\\h", "http://h\\/", "http://h:65536/", "http://h:99999999999999999999/",
           "http://h:x/", "http://h:1x/", "http://h:-1/", "http://[::1", "http://[::1]x/", "http://[::1]:x/",
           "http://[:::1]/", "http://[]/", "http://[1:2:3:4:5:6:7:8:9]/", "http://[::g]/", "http://[1.2.3.4]/",
           "http://[::256.1.1.1]/", "http://[::01.1.1.1]/", "http://[v.a]/", "http://[v1.]/", "http://[va]/",
           "http://[vg.a]/", "http://[v1.a/]/", "http://u@v@h/", "http://u[@h/", "http://h]/", "http://[h/",
           "1a:b", ":a", "+a:b", "%41:b", "a_b:c", "a%", "a%4", "a%4g", "/%g4", "a b", " a", "a ", "http://h/\x7f",
           "http://h/\xe9", "\xe9", "a^", "a|b", 'a"b', "<a>", "a`b", "a{b}", "a[b]", "http://h/a[b]", "http://h/?a[b]",
           "http://h/#a#b", "http://h/#a[", "a\tb", "a\nb", "a\rb", "a\x00b", "http://h/%", "http://h/?%", "http://h/#%",
           "http://u%:p@h/", "http://h%/", "http://[v1.a%]/", "http://[v1.a%41]/", "http://[v1.a\"]/", "http://[v1.a<]/"]


def case_uri(env):
    compare(env, ["up " + x(t) for t, _ in VALID + ROUND_TRIP_ONLY_OTHERWISE],
            [f for _, f in VALID + ROUND_TRIP_ONLY_OTHERWISE])
    compare(env, ["uf " + x(t) for t, _ in VALID], [x(t) for t, _ in VALID])
    compare(env, ["up " + x(t) for t in INVALID], ["no"] * len(INVALID))
    check(env.batch(["upw " + w([0x68, 0x4e2d])]), ["no"], "wide")

    rng = random.Random(3986)
    plain = "abcXYZ019-._~!$&'()*+,;="

    def part(extra, n=8):
        out = ""
        for _ in range(rng.randint(0, n)):
            k = rng.randint(0, 9)
            out += rng.choice(extra) if k < 2 and extra else "%%%02X" % rng.randrange(256) if k < 3 else rng.choice(plain)
        return out

    generated = []
    for _ in range(15000):
        scheme = rng.choice(["http", "a+b.c-1", "x", "HtTp"]) if rng.random() < .8 else None
        s = scheme + ":" if scheme else ""
        host = user = None
        port = -1
        if rng.random() < .7:
            host = rng.choice([part(""), "[::1]", "[1:2::3.4.5.6]", "[v1.a:b]", "192.0.2.1"])
            s += "//"
            if rng.random() < .3:
                user = part(":")
                s += user + "@"
            s += host
            if rng.random() < .3:
                port = rng.randint(0, 65535)
                s += ":%d" % port
        path = "".join("/" + part(":@") for _ in range(rng.randint(0, 3)))
        if host is None and path.startswith("//"):
            path = "/x" + path[1:]
        if host is None and not path and rng.random() < .5:
            path = part("@") or "p"
            if scheme is None:
                path = "." + path
        s += path
        query = fragment = None
        if rng.random() < .5:
            query = part(":@/?")
            s += "?" + query
        if rng.random() < .5:
            fragment = part(":@/?")
            s += "#" + fragment
        generated.append((s, scheme, user, host, port, path, query, fragment))

    compare(env, ["up " + x(g[0]) for g in generated], [fields(*g[1:]) for g in generated])
    compare(env, ["uf " + x(g[0]) for g in generated], [x(g[0]) for g in generated])
    for s, scheme, user, host, port, path, query, fragment in generated:
        u = urllib.parse.urlsplit(s)
        check(u.scheme, (scheme or "").lower(), "scheme by Python of %r" % s)
        netloc = "" if host is None else (user + "@" if user is not None else "") + host + (":%d" % port if port >= 0 else "")
        check((u.netloc, u.path, u.query, u.fragment), (netloc, path, query or "", fragment or ""), "by Python %r" % s)


def valid_v6(a):
    try:
        ipaddress.IPv6Address(a)
        return True
    except ValueError:
        return False


def case_ipv6(env):
    fixed = ["::", "1::", "::1", "1:2:3:4:5:6:7:8", "1:2:3:4:5:6:7::", "::2:3:4:5:6:7:8", "1::3:4:5:6:7:8", "::1.2.3.4",
             "1:2:3:4:5:6:1.2.3.4", "1::1.2.3.4", "1:2:3:4:5:6:7:1.2.3.4", "1:", ":1", "1:::2", "12345::", "1::2::3", ":",
             "1:2:3:4:5:6:7", "abcd:EF01::", "1.2.3.4", "::1.2.3", "::1.2.3.4.5", "::1.2.3.256", "::1.2.3.4:5", "0:0:0:0:0:0:0:0",
             "::ffff:192.0.2.1", "1:2:3:4:5::1.2.3.4", "1:2:3:4:5:6::1.2.3.4"]
    rng = random.Random(6)
    tokens = ["1", "ab", "ffff", "0", "12345", "g", ":", "::", ".", "1.2.3.4", "256", "01", "0.0.0.0"]
    samples = list(fixed)
    for _ in range(30000):
        samples.append("".join(rng.choice(tokens) if rng.random() < .35 else rng.choice([":", "1", "::", "a"])
                               for _ in range(rng.randint(1, 18))))
    samples = [s for s in samples if "%" not in s and "/" not in s]
    lines = ["up " + x("http://[%s]/" % s) for s in samples]
    got = env.batch(lines)
    for s, g in zip(samples, got):
        check(g != "no", valid_v6(s), "IPv6 %r" % s)


RESOLVE = [("g:h", "g:h"), ("g", "http://a/b/c/g"), ("./g", "http://a/b/c/g"), ("g/", "http://a/b/c/g/"), ("/g", "http://a/g"),
           ("//g", "http://g"), ("?y", "http://a/b/c/d;p?y"), ("g?y", "http://a/b/c/g?y"), ("#s", "http://a/b/c/d;p?q#s"),
           ("g#s", "http://a/b/c/g#s"), ("g?y#s", "http://a/b/c/g?y#s"), (";x", "http://a/b/c/;x"), ("g;x", "http://a/b/c/g;x"),
           ("g;x?y#s", "http://a/b/c/g;x?y#s"), ("", "http://a/b/c/d;p?q"), (".", "http://a/b/c/"), ("./", "http://a/b/c/"),
           ("..", "http://a/b/"), ("../", "http://a/b/"), ("../g", "http://a/b/g"), ("../..", "http://a/"), ("../../", "http://a/"),
           ("../../g", "http://a/g"),
           ("../../../g", "http://a/g"), ("../../../../g", "http://a/g"), ("/./g", "http://a/g"), ("/../g", "http://a/g"),
           ("g.", "http://a/b/c/g."), (".g", "http://a/b/c/.g"), ("g..", "http://a/b/c/g.."), ("..g", "http://a/b/c/..g"),
           ("./../g", "http://a/b/g"), ("./g/.", "http://a/b/c/g/"), ("g/./h", "http://a/b/c/g/h"), ("g/../h", "http://a/b/c/h"),
           ("g;x=1/./y", "http://a/b/c/g;x=1/y"), ("g;x=1/../y", "http://a/b/c/y"), ("g?y/./x", "http://a/b/c/g?y/./x"),
           ("g?y/../x", "http://a/b/c/g?y/../x"), ("g#s/./x", "http://a/b/c/g#s/./x"), ("g#s/../x", "http://a/b/c/g#s/../x"),
           ("http:g", "http:g")]

RESOLVE_OTHER = [("http://a", "g", "http://a/g"), ("http://u:p@a:8", "g", "http://u:p@a:8/g"), ("http://a?x", "?", "http://a?"),
                 ("http://a?x", "#f", "http://a?x#f"), ("mailto:a", "b", "mailto:b"), ("a:b/c", "d", "a:b/d"),
                 ("http://a/b", "//u@c:1/d/../e?f", "http://u@c:1/e?f"), ("http://a/b/", "x:/y/../z", "x:/z"),
                 ("http://a/b/", "x:../y", "x:y"), ("http://a/b/", "x:./y", "x:y"), ("http://a/b/", "x:.", "x:"),
                 ("http://a/b/", "x:..", "x:"), ("http://a/b/", "x:../../y/./z/..", "x:y/"), ("http://a/b/", "x:.../y", "x:.../y")]


def case_resolve(env):
    base = "http://a/b/c/d;p?q"
    compare(env, ["ur %s %s" % (x(base), x(r)) for r, _ in RESOLVE], [x(t) for _, t in RESOLVE],
            [r for r, _ in RESOLVE])
    compare(env, ["ur %s %s" % (x(b), x(r)) for b, r, _ in RESOLVE_OTHER], [x(t) for _, _, t in RESOLVE_OTHER],
            [b + " + " + r for b, r, _ in RESOLVE_OTHER])


NORMALIZE = [("HTTP://EXAMPLE.COM/", "http://example.com/"), ("http://h/%7e%7Efoo", "http://h/~~foo"), ("http://h/%2f%2F", "http://h/%2F%2F"),
             ("http://h/a/./b/../c", "http://h/a/c"), ("http://h", "http://h"), ("http://h:80/", "http://h:80/"),
             ("http://U%7e:P@h/", "http://U~:P@h/"), ("http://%41%2fB/", "http://a%2Fb/"), ("http://h/?%7e%2f", "http://h/?~%2F"),
             ("http://h/#%7e%2f", "http://h/#~%2F"), ("a/../b", "a/../b"), ("/a/../b", "/b"), ("HTTP:a/./b", "http:a/./b"),
             ("http://[2001:DB8::A]/", "http://[2001:db8::a]/"), ("http://h/A", "http://h/A"), ("http://h?Q#F", "http://h?Q#F"),
             ("http://h/%c3%a9", "http://h/%C3%A9")]


def case_normalize(env):
    compare(env, ["un " + x(a) for a, _ in NORMALIZE], [x(b) for _, b in NORMALIZE], [a for a, _ in NORMALIZE])


FIRST_PYTHON = -62135596800 + 2 * 86400     # datetime begins with the year 1


def formatted(sec, micros, offset, digits):
    tz = datetime.timezone(datetime.timedelta(seconds=offset))
    d = datetime.datetime(1970, 1, 1, tzinfo=datetime.timezone.utc) + datetime.timedelta(seconds=sec)
    d = d.astimezone(tz)
    t = "%04d-%02d-%02dT%02d:%02d:%02d" % (d.year, d.month, d.day, d.hour, d.minute, d.second)
    if digits:
        t += "." + ("%06d" % micros)[:digits]
    if offset == 0:
        return t + "Z"
    return t + "%s%02d:%02d" % ("-" if offset < 0 else "+", abs(offset) // 3600, abs(offset) // 60 % 60)


RFC_VALID = [("1985-04-12T23:20:50.52Z", 482196050.52, 0), ("1996-12-19T16:39:57-08:00", 851042397.0, -28800),
             ("1990-12-31T23:59:60Z", 662688000.0, 0), ("1990-12-31T15:59:60-08:00", 662688000.0, -28800),
             ("1937-01-01T12:00:27.87+00:20", -1041336000 + 27.87 - 1200 + 0, 1200),
             ("2000-02-29T00:00:00Z", 951782400.0, 0), ("2024-02-29t01:02:03z", 1709168523.0, 0),
             ("1970-01-01T00:00:00-00:00", 0.0, 0), ("1970-01-01T00:00:00+00:00", 0.0, 0), ("1970-01-01T00:00:00+23:59", -86340.0, 86340),
             ("1970-01-01T00:00:00-23:59", 86340.0, -86340), ("0000-01-01T00:00:00Z", -62167219200.0, 0),
             ("9999-12-31T23:59:59Z", 253402300799.0, 0), ("1970-01-01T00:00:00.0000000000000000000001Z", 0.0, 0),
             ("2016-06-30T23:59:60Z", 1467331200.0, 0), ("2016-03-31T23:59:60Z", 1459468800.0, 0)]

RFC_INVALID = ["", "1985", "1985-04-12", "1985-04-12T23:20:50", "1985-04-12 23:20:50Z", "1985-04-12T23:20:50.Z", "1985-04-12T23:20:50.5",
               "1985-04-12T23:20Z", "85-04-12T23:20:50Z", "1985-4-12T23:20:50Z", "1985-13-12T23:20:50Z", "1985-00-12T23:20:50Z",
               "1985-04-00T23:20:50Z", "1985-04-31T23:20:50Z", "1985-02-29T23:20:50Z", "1900-02-29T00:00:00Z", "1985-04-32T00:00:00Z",
               "1985-04-12T24:00:00Z", "1985-04-12T23:60:00Z", "1985-04-12T23:20:61Z", "1985-04-12T22:59:60Z", "1990-12-30T23:59:60Z",
               "1990-12-31T23:58:60Z", "1990-12-31T23:59:60+01:00", "1985-04-12T23:20:50+24:00", "1985-04-12T23:20:50+00:60",
               "1985-04-12T23:20:50+0100", "1985-04-12T23:20:50+01", "1985-04-12T23:20:50+1:00", "1985-04-12T23:20:50Z ",
               " 1985-04-12T23:20:50Z", "1985-04-12T23:20:50ZZ", "1985-04-12T23:20:50.5.5Z", "1985-04-12T23:20:50,5Z", "1985-04-12T23:20:50.aZ",
               "1985/04/12T23:20:50Z", "1985-04-12X23:20:50Z", "1985-04-12T23-20-50Z", "198a-04-12T23:20:50Z", "1985-04-12T23:20:50x",
               "1985-04-12T23:20:50.5+01:00x", "1985-04-12T23:20:50+01:0a", "+985-04-12T23:20:50Z",
               "1985-04-12T23:20:50+01x00", "1985-04-12T23:20:50-01.00", "1990-12-01T04:59:60Z", "1991-01-01T00:59:60Z",
               "1990-12-31T22:59:60Z"]


def case_rfc3339(env):
    rng = random.Random(3339)
    lines, wants = [], []
    fixed = [(0, 0, 0, 0), (0, 0, 3600, 0), (0, 0, -3600, 0), (0, 0, 19800, 0), (0, 0, -19800, 0), (0, 0, 86340, 0), (0, 0, -86340, 0),
             (1700000000, 123000, 0, 3), (1700000000, 123456, 0, 6), (1700000000, 999999, 0, 3), (1700000000, 1000, 0, 6)]
    for sec, micros, offset, digits in fixed:
        lines.append("rf %r %d %d" % (sec + micros / 1e6, offset, digits))
        wants.append(formatted(sec, micros, offset, digits))
    for _ in range(20000):
        sec = rng.randint(-2**31, 2**31)
        micros = rng.randrange(1000000)
        digits = rng.randint(0, 6)
        offset = rng.randint(-1439, 1439) * 60
        lines.append("rf %r %d %d" % (sec + micros / 1e6, offset, digits))
        wants.append(formatted(sec, micros, offset, digits))
    for _ in range(20000):
        sec = rng.randint(FIRST_PYTHON, 253402300799 - 86400)
        offset = rng.randint(-1439, 1439) * 60
        lines.append("rf %r %d 0" % (float(sec), offset))
        wants.append(formatted(sec, 0, offset, 0))
    compare(env, lines, wants)

    table = [("rf 1.123 0 3", "1970-01-01T00:00:01.123Z"), ("rf 1.2 0 1", "1970-01-01T00:00:01.2Z"), ("rf 0.3 0 1", "1970-01-01T00:00:00.3Z"),
             ("rf 1.9999996 0 0", "1970-01-01T00:00:02Z"), ("rf 1.9999996 0 6", "1970-01-01T00:00:02.000000Z"),
             ("rf 1.9999994 0 6", "1970-01-01T00:00:01.999999Z"), ("rf 1.9999994 0 2", "1970-01-01T00:00:01.99Z"),
             ("rf -0.5 0 1", "1969-12-31T23:59:59.5Z"), ("rf -0.000001 0 6", "1969-12-31T23:59:59.999999Z"),
             ("rf -62167219200.0 0 0", "0000-01-01T00:00:00Z"), ("rf 253402300799.0 0 0", "9999-12-31T23:59:59Z"),
             ("rf 1709251199.0 0 0", "2024-02-29T23:59:59Z")]
    compare(env, [a for a, _ in table], [b for _, b in table])

    lines, wants = [], []
    for _ in range(20000):
        sec = rng.randint(FIRST_PYTHON, 253402300799 - 86400 * 2)
        micros = rng.randrange(1000000) if abs(sec) < 2**31 else 0
        digits = rng.randint(1, 6) if micros else 0
        offset = rng.randint(-1439, 1439) * 60
        micros = int(("%06d" % micros)[:digits].ljust(6, "0")) if digits else 0
        lines.append("rp " + x(formatted(sec, micros, offset, digits)))
        wants.append("%.6f %d" % (sec + micros / 1e6, offset))
    compare(env, lines, wants)

    compare(env, ["rp " + x(t) for t, _, _ in RFC_VALID], ["%.6f %d" % (s, o) for _, s, o in RFC_VALID], [t for t, _, _ in RFC_VALID])
    compare(env, ["rp " + x(t) for t in RFC_INVALID], ["no"] * len(RFC_INVALID), RFC_INVALID)
    wides = [utf16("1985-04-12T23:20:50Z\u4e2d"), utf16("1985-04-12T23:20:5\u0130Z"), utf16("1985-04-12T23:20:50\u015a"),
             utf16("1985-04-12\u0154 23:20:50Z")]
    compare(env, ["rpw " + w(u) for u in wides], ["no"] * len(wides))


BREACHES = [("hex", "Hex.m3"), ("base64", "Base64.m3"), ("escape", "URI.m3"), ("query", "URI.m3"), ("rawquery", "URI.m3"),
            ("digits", "RFC3339.m3"), ("minutes", "RFC3339.m3"), ("day", "RFC3339.m3"), ("early", "RFC3339.m3"),
            ("late", "RFC3339.m3"), ("lateoffset", "RFC3339.m3")]


def case_breach(env):
    for kind, unit in BREACHES:
        r = env.command("assert", kind)
        text = (r.stdout + r.stderr).decode("latin-1")
        if r.returncode == 0 or "<*ASSERT*> failed" not in text or ('"../src/%s"' % unit) not in text:
            raise Failed("%s: exit %d: %r" % (kind, r.returncode, text[:300]))


CASES = {"hex": case_hex, "utf8": case_utf8, "base64": case_base64, "escape": case_escape, "query": case_query, "uri": case_uri,
         "ipv6": case_ipv6, "resolve": case_resolve, "normalize": case_normalize, "rfc3339": case_rfc3339, "breach": case_breach}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cases", nargs="*")
    ap.add_argument("--java", default=shutil.which("java") or "java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--timeout", type=float, default=300.0)
    args = ap.parse_args()
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="encoding_test"))
    work.mkdir(parents=True, exist_ok=True)
    env = Env(args, work)
    failed = []
    try:
        env.compile()
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
