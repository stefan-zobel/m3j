#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Tests of the library http of m3j. They need no connection to the internet.

  python3 tools/http_test.py [CASE]... [--java JAVA] [--jar JAR] [--work DIR]
                             [--keep] [--timeout SECONDS] [--speed]

The cases:

  codec    escapes, dates and basic authentication against the standard
           library of Python, with fixed and with random values, also of
           characters above 255 and of surrogates
  headers  tests/http/src/Main.m3 prints the behavior of HTTP.Headers; the
           output, checked by hand, is tests/http/expected/headers.txt
  table    requests on a raw socket with the expected status and whether the
           connection ends: what RFC 9110 and 9112 require of a server
  behavior framing, limits, timeouts, connections and the shutdown
  clients  http.client of Python and curl as independent clients
  tls      the server over TLS with the module ssl of Python
  client   HTTPClient against http.server of Python, over TCP and TLS; the
           output, checked by hand, is tests/http/expected/client.txt
  together server and client in one program, with
           tests/http/expected/together.txt
  gzip     compression in the server, on raw sockets, against zlib of Python
           and curl
  gzclient compression in the client, against http.server of Python and the
           server of m3j; the output, checked by hand, is
           tests/http/expected/gzclient.txt
  cookie   HTTPCookie: tables of RFC 6265 and of the rules of browsers, every
           character, random dates against a second reading of RFC 6265,
           http.cookies of Python, and requests and curl against the server
  multipart
           Multipart: tables of valid and malformed bodies, exact limits,
           every split of the input and every truncation, forms of urllib3
           and requests_toolbelt, the writer read by python-multipart and
           requests_toolbelt, and requests and curl against the server;
           needs the Python packages requests, requests_toolbelt and
           python-multipart

Without arguments all cases run. --speed also measures the rates of a server
on this machine, for information. JAVA defaults to $JDK25/bin/java, else java
from the PATH; JAR to m3j.jar in the root of m3j.
"""

import argparse
import base64
import calendar
import datetime
import email.utils
import gzip
import hashlib
import http
import http.client
import http.cookies
import http.server
import math
import os
import random
import re
import shutil
import socket
import ssl
import subprocess
import sys
import tempfile
import threading
import time
import urllib.parse
import zlib
from pathlib import Path

import tls_test

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests" / "http"
SEED = 20261109


class Failed(Exception):
    pass


class Env:
    def __init__(self, args, work):
        self.args = args
        self.work = work
        self.java = args.java
        self.classes = work / "classes"
        self.cp = str(self.classes) + os.pathsep + args.jar
        self.tls = None

    def compile(self):
        src = TESTS / "src"
        cmd = [self.java, "-Xss64m", "-cp", self.args.jar, "m3j.jvm.Compile", str(src / "Main.m3"), "-d", str(self.classes)]
        r = subprocess.run(cmd, cwd=self.work, capture_output=True, text=True, stdin=subprocess.DEVNULL)
        if r.returncode:
            raise Failed("compile:\n" + r.stdout + r.stderr)

    def run(self, *a, data=None):
        r = subprocess.run([self.java, "-cp", self.cp, "m3.Main", *a], cwd=self.work, input=data, capture_output=True,
                           timeout=self.args.timeout)
        if r.returncode:
            raise Failed("exit %d: %s" % (r.returncode, (r.stdout + r.stderr).decode("latin-1")))
        return r.stdout.decode("latin-1")


def arg(b):
    return b.hex() if b else "-"


def compare(what, got, want):
    if got != want:
        raise Failed("%s:\n  got  %r\n  want %r" % (what, got, want))


def hexbytes(t):
    return t.encode("latin-1").hex()


WEEK = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
FULL = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
MON = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

BAD_DATES = [
    "",
    "Sun",
    "Sun, 06 Nov 1994 08:49:37",
    "Sun, 06 Nov 1994 08:49:37 UTC",
    "Sun, 06 Nov 1994 08:49:37 GMT ",
    " Sun, 06 Nov 1994 08:49:37 GMT",
    "Sun,  6 Nov 1994 08:49:37 GMT",
    "Sun, 6 Nov 1994 08:49:37 GMT",
    "sun, 06 Nov 1994 08:49:37 GMT",
    "Sun, 06 nov 1994 08:49:37 GMT",
    "Xyz, 06 Nov 1994 08:49:37 GMT",
    "Sun, 31 Feb 1994 08:49:37 GMT",
    "Sun, 00 Nov 1994 08:49:37 GMT",
    "Sun, 32 Oct 1994 08:49:37 GMT",
    "Sun, 06 Nov 1994 24:00:00 GMT",
    "Sun, 06 Nov 1994 08:60:37 GMT",
    "Sun, 06 Nov 1994 08:49:61 GMT",
    "Sun, 06 Nov 94 08:49:37 GMT",
    "Sun, 29 Feb 1900 08:49:37 GMT",
    "Sunday, 06-Nov-1994 08:49:37 GMT",
    "Sunday 06-Nov-94 08:49:37 GMT",
    "Sun Nov 6 08:49:37 1994",
    "Sun Nov  6 08:49:37 94",
    "Sun Nov  6 08:49:37 1994 GMT",
    "Sun, 06-Nov-94 08:49:37 GMT",
]

BAD_ESCAPES = ["%", "%4", "a%", "a%4", "%G0", "%0G", "%%", "%-1", "% 1"]

BAD_AUTH = ["", "Basic", "Basic ", "Basic  ", "Bearer dTpw", "Basicc dTpw", "Basic dTp", "Basic dTp!", "Basic =Tpw",
            "Basic d=pw", "Basic dT=w", "Basic dTpw=", "Basic dTpw====", "Basic dGVzdA==", "Basic\tdTpw", "Basic dTpw "]

# Base64 whose last character has bits left over that are not zero: lax
# decoders read "u:" and "u:pw", the strict one refuses it
NONCANONICAL_AUTH = ["Basic dTp=", "Basic dTpwdx=="]


def wide_bytes(units):
    """The bytes that HTTP.Escape and HTTP.BasicAuth make of the UTF-16 code
    units: a character up to 255 is a byte, a larger one goes out in UTF-8, a
    pair of surrogates as one character, a surrogate without a partner as
    U+FFFD."""
    text = b"".join(u.to_bytes(2, "big") for u in units).decode("utf-16-be", errors="replace")
    return b"".join(bytes([ord(c)]) if ord(c) <= 0xFF else c.encode("utf-8") for c in text)


def arg16(units):
    return "".join("%04x" % u for u in units) if units else "-"

TOKENS = ["a", "Content-Type", "!#$%&'*+-.^_`|~09AZaz"]
NOT_TOKENS = ["", " ", "a b", "a:b", "a\r", "a\n", "(x)", "a,b", "a\tb", "\x7f", "\xe9", "a/b", "a=b", "[", "{", '"']
VALUES = ["", "a b", "a\tb", "\xe9\xff", "x" * 1000, "a,b;c=\"d\""]
NOT_VALUES = ["\r", "\n", "a\r\nX-Evil: 1", "\0", "\x7f", "a\x01"]


def case_codec(env):
    rnd = random.Random(SEED)
    req = []

    def ask(line, want):
        req.append((line, want))

    def blob():
        n = rnd.choice([0, 1, 2, 3, 4, 5, 20, 300])
        return bytes(rnd.getrandbits(8) for _ in range(n))

    for b in [b"", b" ", b"a b+c%d/e?f=g&h", bytes(range(256)), b"-._~", b"~"] + [blob() for _ in range(1000)]:
        ask("E " + arg(b), urllib.parse.quote_from_bytes(b, safe="").encode().hex())
        ask("F " + arg(b), urllib.parse.quote_plus(b, safe="").encode().hex())
        q = urllib.parse.quote_from_bytes(b, safe="").encode()
        ask("U " + arg(q), "ok " + b.hex())
        q = urllib.parse.quote_plus(b, safe="").encode()
        ask("V " + arg(q), "ok " + b.hex())

    # unescaped text, lower case digits, and "+" that is not a space without form
    for s in ["a+b", "%41%6a%6A", "%2B", "/a/b?c"]:
        b = s.encode()
        ask("U " + arg(b), "ok " + urllib.parse.unquote_to_bytes(b).hex())
        ask("V " + arg(b), "ok " + urllib.parse.unquote_to_bytes(b.replace(b"+", b" ")).hex())
    for s in BAD_ESCAPES:
        ask("U " + arg(s.encode()), "bad")
        ask("V " + arg(s.encode()), "bad")

    now = time.gmtime().tm_year
    stamps = [0, 784111777, 951782400, 1709164800, 4102444799, 32503679999] + [rnd.randrange(0, 4102444800) for _ in range(1000)]
    for t in stamps:
        text = email.utils.formatdate(t, usegmt=True)
        ask("D %d" % t, text)
        ask("P " + arg(text.encode()), "ok %d" % t)
        g = time.gmtime(t)
        day = WEEK[(g.tm_wday + 1) % 7]
        asc = "%s %s %2d %02d:%02d:%02d %04d" % (day, MON[g.tm_mon - 1], g.tm_mday, g.tm_hour, g.tm_min, g.tm_sec, g.tm_year)
        ask("P " + arg(asc.encode()), "ok %d" % t)
        if now - 50 < g.tm_year <= now + 50:
            rfc = "%s, %02d-%s-%02d %02d:%02d:%02d GMT" % (FULL[(g.tm_wday + 1) % 7], g.tm_mday, MON[g.tm_mon - 1], g.tm_year % 100,
                                                           g.tm_hour, g.tm_min, g.tm_sec)
            ask("P " + arg(rfc.encode()), "ok %d" % t)

    # the leap second and the end of a leap year's February
    ask("P " + arg(b"Sat, 31 Dec 2016 23:59:60 GMT"), "ok %d" % (calendar.timegm((2017, 1, 1, 0, 0, 0))))
    ask("P " + arg(b"Thu, 29 Feb 2024 12:00:00 GMT"), "ok %d" % (calendar.timegm((2024, 2, 29, 12, 0, 0))))
    for s in BAD_DATES:
        ask("P " + arg(s.encode()), "bad")

    pairs = [(b"Aladdin", b"open sesame"), (b"", b""), (b"u", b""), (b"", b"p"), (b"a", b"b:c:d"), (bytes(range(58)) + b"\xff", bytes(range(256)))]
    for _ in range(500):
        pairs.append((blob().replace(b":", b"-"), blob()))
    for user, password in pairs:
        value = b"Basic " + base64.b64encode(user + b":" + password)
        ask("B %s %s" % (arg(user), arg(password)), value.hex())
        ask("A " + arg(value), "ok %s %s" % (user.hex(), password.hex()))
    ask("A " + arg(b"bAsIc   " + base64.b64encode(b"u:p")), "ok %s %s" % (b"u".hex(), b"p".hex()))
    ask("B %s %s" % (arg(b"a:b"), arg(b"c")), "bad")
    for s in BAD_AUTH:
        ask("A " + arg(s.encode()), "bad")
    for s in NONCANONICAL_AUTH:
        if base64.b64decode(s[6:]) not in (b"u:", b"u:pw"):
            raise Failed("Python reads %r differently" % s)
        ask("A " + arg(s.encode()), "bad")

    # characters above 255 and surrogates, with and without a partner
    def unit():
        k = rnd.randrange(7)
        if k == 0:
            return [rnd.randrange(0x20, 0x7F)]
        if k == 1:
            return [rnd.randrange(0x80, 0x100)]
        if k == 2:
            return [rnd.randrange(0x100, 0x800)]
        if k == 3:
            return [rnd.choice([rnd.randrange(0x800, 0xD800), rnd.randrange(0xE000, 0x10000)])]
        if k == 4:
            c = rnd.randrange(0x10000, 0x110000) - 0x10000
            return [0xD800 + c // 1024, 0xDC00 + c % 1024]
        if k == 5:
            return [rnd.randrange(0xD800, 0xDC00)]
        return [rnd.randrange(0xDC00, 0xE000)]

    texts = [[], [0x61, 0xE9, 0x20AC], [0xD83D, 0xDE00], [0xD800, 0xDC00], [0xDBFF, 0xDFFF], [0xD800], [0xDBFF],
             [0xDC00], [0xDFFF], [0xD800, 0xD800, 0xDC00], [0xDC00, 0xD800], [0x61, 0xD800], [0xD800, 0x61],
             [0xD83D, 0xDE00, 0xDE00], [0xFF, 0x100], [0x7FF, 0x800], [0xD7FF, 0xE000, 0xFFFD, 0xFFFF], [0x20, 0x2B, 0x20AC]]
    for _ in range(1000):
        texts.append([u for _ in range(rnd.randrange(1, 12)) for u in unit()])
    for units in texts:
        b = wide_bytes(units)
        ask("X " + arg16(units), urllib.parse.quote_from_bytes(b, safe="").encode().hex())
        ask("Y " + arg16(units), urllib.parse.quote_plus(b, safe="").encode().hex())
    for i in range(0, len(texts) - 1, 2):
        user = [0x2D if u == 0x3A else u for u in texts[i]]
        password = texts[i + 1]
        ask("Z %s %s" % (arg16(user), arg16(password)),
            (b"Basic " + base64.b64encode(wide_bytes(user) + b":" + wide_bytes(password))).hex())
    ask("Z %s %s" % (arg16([0x20AC, 0x3A]), arg16([0x61])), "bad")

    for s in TOKENS:
        ask("N " + arg(s.encode("latin-1")), "1")
    for s in NOT_TOKENS:
        ask("N " + arg(s.encode("latin-1")), "0")
    for s in VALUES:
        ask("W " + arg(s.encode("latin-1")), "1")
    for s in NOT_VALUES:
        ask("W " + arg(s.encode("latin-1")), "0")

    for code in http.HTTPStatus:
        c = int(code)
        if c in (102, 103, 207, 208, 226, 418, 423, 424, 425):
            continue  # registered, but not in RFC 9110
        ask("R %d" % c, code.phrase)
    ask("R 299", "")
    ask("R 0", "")
    ask("R 1000", "")

    out = env.run("codec", data="".join(r + "\n" for r, _ in req).encode()).splitlines()
    compare("number of answers", len(out), len(req))
    wrong = [(r, g, w) for (r, w), g in zip(req, out) if g != w]
    if wrong:
        r, g, w = wrong[0]
        raise Failed("%d of %d wrong; first:\n  request %s\n  got  %s\n  want %s" % (len(wrong), len(req), r, g, w))
    print("          %d answers" % len(req))


def case_headers(env):
    compare("headers", env.run("headers").replace("\r\n", "\n"), (TESTS / "expected" / "headers.txt").read_text())


def certificates(env):
    """The keys and certificates of tls_test.py, made once."""
    if env.tls is None:
        env.tls = tls_test.Env(env.args, env.work)
        env.tls.make_certs()
        env.tls.peer("export", "wrong.p12", tls_test.PASSWORD, "wrong.pem")
    return env.tls


class Server:
    """The test server of Main.m3 in a process of its own."""

    def __init__(self, env, *a):
        self.proc = subprocess.Popen([env.java, "-cp", env.cp, "m3.Main", "serve", *a], cwd=env.work, stdin=subprocess.DEVNULL,
                                     stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        line = self.proc.stdout.readline()
        if not line.strip().isdigit():
            raise Failed("server: %r %r" % (line, self.proc.stdout.read()))
        self.port = int(line)
        self.log = []
        self.reader = threading.Thread(target=self.drain, daemon=True)
        self.reader.start()

    def drain(self):
        for line in self.proc.stdout:
            self.log.append(line.decode("latin-1").rstrip())

    def stop(self):
        if self.proc.poll() is None:
            self.proc.kill()
        self.proc.wait()
        self.reader.join(5)


class Peer:
    """A client on a raw socket."""

    def __init__(self, port, wrap=None, rcvbuf=None):
        s = socket.socket()
        if rcvbuf:
            s.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, rcvbuf)
        s.settimeout(10)
        s.connect(("127.0.0.1", port))
        self.s = wrap(s) if wrap else s
        self.buf = b""

    def send(self, b):
        self.s.sendall(b)
        return self

    def fill(self):
        d = self.s.recv(65536)
        if not d:
            raise EOFError
        self.buf += d

    def line(self):
        while b"\r\n" not in self.buf:
            self.fill()
        i = self.buf.index(b"\r\n")
        r, self.buf = self.buf[:i], self.buf[i + 2:]
        return r

    def take(self, n):
        while len(self.buf) < n:
            self.fill()
        r, self.buf = self.buf[:n], self.buf[n:]
        return r

    def response(self, head=False):
        """Status, fields (lower case names, in a list of pairs), body."""
        status = int(self.line().split(b" ")[1])
        fields = []
        while True:
            line = self.line()
            if not line:
                break
            name, value = line.split(b":", 1)
            fields.append((name.decode().lower(), value.strip().decode("latin-1")))
        d = dict(fields)
        self.fields = fields
        if head or status < 200 or status in (204, 304):
            return status, d, b""
        if d.get("transfer-encoding") == "chunked":
            body = b""
            while True:
                size = int(self.line().split(b";")[0], 16)
                if size == 0:
                    while self.line():
                        pass
                    return status, d, body
                body += self.take(size)
                self.take(2)
        if "content-length" in d:
            return status, d, self.take(int(d["content-length"]))
        while True:
            try:
                self.fill()
            except EOFError:
                r, self.buf = self.buf, b""
                return status, d, r

    def closed(self, wait=0.5):
        """Has the server ended the connection? Pending bytes count as not."""
        self.s.settimeout(wait)
        try:
            d = self.s.recv(1)
        except socket.timeout:
            return False
        except OSError:
            return True
        finally:
            self.s.settimeout(10)
        if d:
            self.buf += d
            return False
        return True

    def close(self):
        self.s.close()


H = b"Host: x\r\n"

# name, bytes of the request, status, does the connection end
TABLE = [
    ("plain", b"GET /hello HTTP/1.1\r\n" + H + b"\r\n", 200, False),
    ("leading empty line", b"\r\nGET /hello HTTP/1.1\r\n" + H + b"\r\n", 200, False),
    ("http 1.0", b"GET /hello HTTP/1.0\r\n\r\n", 200, True),
    ("connection close", b"GET /hello HTTP/1.1\r\n" + H + b"Connection: close\r\n\r\n", 200, True),
    ("connection close in a list", b"GET /hello HTTP/1.1\r\n" + H + b"Connection: x, CLOSE\r\n\r\n", 200, True),
    ("no such handler", b"GET /missing HTTP/1.1\r\n" + H + b"\r\n", 404, False),
    ("options star", b"OPTIONS * HTTP/1.1\r\n" + H + b"\r\n", 404, False),
    ("star for get", b"GET * HTTP/1.1\r\n" + H + b"\r\n", 400, True),
    ("no host", b"GET /hello HTTP/1.1\r\n\r\n", 400, True),
    ("two hosts", b"GET /hello HTTP/1.1\r\n" + H + H + b"\r\n", 400, True),
    ("bad host", b"GET /hello HTTP/1.1\r\nHost: a b\r\n\r\n", 400, True),
    ("obs-fold", b"GET /hello HTTP/1.1\r\n" + H + b"X-A: 1\r\n  2\r\n\r\n", 400, True),
    ("space before colon", b"GET /hello HTTP/1.1\r\n" + H + b"X-A : 1\r\n\r\n", 400, True),
    ("no colon", b"GET /hello HTTP/1.1\r\n" + H + b"X-A\r\n\r\n", 400, True),
    ("empty name", b"GET /hello HTTP/1.1\r\n" + H + b": 1\r\n\r\n", 400, True),
    ("bad character in a name", b"GET /hello HTTP/1.1\r\n" + H + b"X(A: 1\r\n\r\n", 400, True),
    ("nul in a value", b"GET /hello HTTP/1.1\r\n" + H + b"X-A: a\0b\r\n\r\n", 400, True),
    ("del in a value", b"GET /hello HTTP/1.1\r\n" + H + b"X-A: a\x7fb\r\n\r\n", 400, True),
    ("bare cr in a value", b"GET /hello HTTP/1.1\r\n" + H + b"X-A: a\rb\r\n\r\n", 400, True),
    ("bare lf", b"GET /hello HTTP/1.1\n" + H + b"\r\n", 400, True),
    ("bare lf in a field", b"GET /hello HTTP/1.1\r\n" + b"Host: x\n\r\n", 400, True),
    ("two spaces", b"GET  /hello HTTP/1.1\r\n" + H + b"\r\n", 400, True),
    ("no version", b"GET /hello\r\n\r\n", 400, True),
    ("bad version", b"GET /hello HTTP/1.1x\r\n" + H + b"\r\n", 400, True),
    ("lower case version", b"GET /hello http/1.1\r\n" + H + b"\r\n", 400, True),
    ("version 2.0", b"GET /hello HTTP/2.0\r\n" + H + b"\r\n", 505, True),
    ("version 1.2", b"GET /hello HTTP/1.2\r\n" + H + b"\r\n", 505, True),
    ("connect", b"CONNECT x:443 HTTP/1.1\r\n" + H + b"\r\n", 501, True),
    ("bad method", b"G(T /hello HTTP/1.1\r\n" + H + b"\r\n", 400, True),
    ("target without slash", b"GET hello HTTP/1.1\r\n" + H + b"\r\n", 400, True),
    ("fragment", b"GET /hello#a HTTP/1.1\r\n" + H + b"\r\n", 400, True),
    ("byte above 126 in a target", b"GET /h\xe9llo HTTP/1.1\r\n" + H + b"\r\n", 400, True),
    ("bad escape", b"GET /a%zz HTTP/1.1\r\n" + H + b"\r\n", 400, True),
    ("absolute form", b"GET http://other/hello HTTP/1.1\r\n" + H + b"\r\n", 200, False),
    ("absolute form without a host field", b"GET http://other/hello HTTP/1.1\r\n\r\n", 400, True),
    ("absolute form with two host fields", b"GET http://other/hello HTTP/1.1\r\n" + H + H + b"\r\n", 400, True),
    ("absolute form without authority", b"GET http:///hello HTTP/1.1\r\n" + H + b"\r\n", 400, True),
    ("long target", b"GET /" + b"a" * 2000 + b" HTTP/1.1\r\n" + H + b"\r\n", 414, True),
    ("long field", b"GET /hello HTTP/1.1\r\n" + H + b"X-A: " + b"a" * 2000 + b"\r\n\r\n", 431, True),
    ("many fields", b"GET /hello HTTP/1.1\r\n" + H + b"".join(b"X-%d: 1\r\n" % i for i in range(30)) + b"\r\n", 431, True),
    ("large head", b"GET /hello HTTP/1.1\r\n" + H + b"".join(b"X-%d: " % i + b"a" * 900 + b"\r\n" for i in range(10)) + b"\r\n", 431, True),
    ("length and chunked", b"POST /echo HTTP/1.1\r\n" + H + b"Content-Length: 3\r\nTransfer-Encoding: chunked\r\n\r\n", 400, True),
    ("two lengths", b"POST /echo HTTP/1.1\r\n" + H + b"Content-Length: 3\r\nContent-Length: 4\r\n\r\nabcd", 400, True),
    ("same length twice", b"POST /echo HTTP/1.1\r\n" + H + b"Content-Length: 3\r\nContent-Length: 3\r\n\r\nabc", 400, True),
    ("length list", b"POST /echo HTTP/1.1\r\n" + H + b"Content-Length: 3, 3\r\n\r\nabc", 400, True),
    ("sign in a length", b"POST /echo HTTP/1.1\r\n" + H + b"Content-Length: +3\r\n\r\nabc", 400, True),
    ("negative length", b"POST /echo HTTP/1.1\r\n" + H + b"Content-Length: -3\r\n\r\n", 400, True),
    ("hex length", b"POST /echo HTTP/1.1\r\n" + H + b"Content-Length: 0x3\r\n\r\nabc", 400, True),
    ("empty length", b"POST /echo HTTP/1.1\r\n" + H + b"Content-Length:\r\n\r\n", 400, True),
    ("length over the limit", b"POST /echo HTTP/1.1\r\n" + H + b"Content-Length: 1048577\r\n\r\n", 413, True),
    ("huge length", b"POST /echo HTTP/1.1\r\n" + H + b"Content-Length: 99999999999999999999999\r\n\r\n", 413, True),
    ("leading zeros in a length", b"POST /echo HTTP/1.1\r\n" + H + b"Content-Length: 0000000000000000000000000000003\r\n\r\nabc", 200, False),
    ("coding other than chunked", b"POST /echo HTTP/1.1\r\n" + H + b"Transfer-Encoding: gzip\r\n\r\n", 501, True),
    ("coding list", b"POST /echo HTTP/1.1\r\n" + H + b"Transfer-Encoding: gzip, chunked\r\n\r\n", 501, True),
    ("chunked twice", b"POST /echo HTTP/1.1\r\n" + H + b"Transfer-Encoding: chunked\r\nTransfer-Encoding: chunked\r\n\r\n", 501, True),
    ("chunked under 1.0", b"POST /echo HTTP/1.0\r\nTransfer-Encoding: chunked\r\n\r\n", 400, True),
    ("chunked", b"POST /echo HTTP/1.1\r\n" + H + b"Transfer-Encoding: Chunked\r\n\r\n3\r\nabc\r\n0\r\n\r\n", 200, False),
    ("bad chunk size", b"POST /echo HTTP/1.1\r\n" + H + b"Transfer-Encoding: chunked\r\n\r\nxy\r\nabc\r\n0\r\n\r\n", 400, True),
    ("chunk without terminator", b"POST /echo HTTP/1.1\r\n" + H + b"Transfer-Encoding: chunked\r\n\r\n3\r\nabcX\r\n0\r\n\r\n", 400, True),
    ("chunk over the limit", b"POST /echo HTTP/1.1\r\n" + H + b"Transfer-Encoding: chunked\r\n\r\n100001\r\n", 413, True),
    ("huge chunk", b"POST /echo HTTP/1.1\r\n" + H + b"Transfer-Encoding: chunked\r\n\r\nFFFFFFFFFFFFFFFFFFFFF\r\n", 413, True),
    ("bad expectation", b"GET /hello HTTP/1.1\r\n" + H + b"Expect: nothing\r\n\r\n", 417, True),
    ("no framing in a response to 204", b"GET /status?c=204&body=1 HTTP/1.1\r\n" + H + b"\r\n", 204, False),
    ("no body in a response to 304", b"GET /status?c=304&body=1 HTTP/1.1\r\n" + H + b"\r\n", 304, False),
    ("status below 200", b"GET /status?c=100 HTTP/1.1\r\n" + H + b"\r\n", 500, True),
    ("status above 599", b"GET /status?c=600 HTTP/1.1\r\n" + H + b"\r\n", 500, True),
    ("unknown status", b"GET /status?c=299 HTTP/1.1\r\n" + H + b"\r\n", 299, False),
    ("handler without a body", b"GET /nobody HTTP/1.1\r\n" + H + b"\r\n", 200, False),
    ("invalid value", b"GET /badvalue HTTP/1.1\r\n" + H + b"\r\n", 500, True),
    ("invalid name", b"GET /badname HTTP/1.1\r\n" + H + b"\r\n", 500, True),
    ("transfer-encoding from a handler", b"GET /badte HTTP/1.1\r\n" + H + b"\r\n", 500, True),
    ("failure of a handler", b"GET /fail-other HTTP/1.1\r\n" + H + b"\r\n", 500, True),
    ("failure with a status", b"GET /fail HTTP/1.1\r\n" + H + b"\r\n", 400, True),
    ("checked runtime error", b"GET /crash HTTP/1.1\r\n" + H + b"\r\n", 500, True),
    ("unlisted exception", b"GET /unlisted HTTP/1.1\r\n" + H + b"\r\n", 500, True),
    ("too many bytes", b"GET /len?n=5&claim=3 HTTP/1.1\r\n" + H + b"\r\n", 500, True),
]


def case_table(env):
    srv = Server(env)
    wrong = []
    try:
        for name, raw, status, closed in TABLE:
            p = Peer(srv.port)
            try:
                p.send(raw)
                st, _, _ = p.response()
                c = p.closed()
                if st != status or c != closed:
                    wrong.append("%s: status %d, closed %s" % (name, st, c))
            except (OSError, EOFError, ValueError, IndexError) as e:
                wrong.append("%s: %r" % (name, e))
            p.close()
        if wrong:
            raise Failed("%d of %d:\n  " % (len(wrong), len(TABLE)) + "\n  ".join(wrong))
        if not any("checked runtime error" in l for l in srv.log):
            raise Failed("no message about the error in a handler: %s" % srv.log)
    finally:
        srv.stop()
    print("          %d requests" % len(TABLE))


def pattern(n):
    return bytes(i % 251 for i in range(n))


def get(path, extra=b""):
    return b"GET " + path + b" HTTP/1.1\r\n" + H + extra + b"\r\n"


def check(what, got, want):
    if got != want:
        raise Failed("%s: got %r, want %r" % (what, got, want))


def case_behavior(env):
    srv = Server(env)
    try:
        port = srv.port

        # one socket for several requests, and requests that are pipelined
        p = Peer(port)
        for _ in range(3):
            check("reuse", p.send(get(b"/hello")).response()[2], b"Hello\n")
        p.send(get(b"/hello") + get(b"/echo-len") + get(b"/hello"))
        check("pipeline 1", p.response()[2], b"Hello\n")
        check("pipeline 2", p.response()[2], b"0 0")
        check("pipeline 3", p.response()[2], b"Hello\n")
        p.close()

        # fields of the response
        st, d, body = Peer(port).send(get(b"/hello")).response()
        check("length of a small body", d.get("content-length"), "6")
        if "transfer-encoding" in d or "date" not in d:
            raise Failed("fields: %s" % d)
        check("own date", Peer(port).send(get(b"/date")).response()[1]["date"], "Sun, 06 Nov 1994 08:49:37 GMT")

        # what a handler sees
        info = Peer(port).send(b"GET /info?a=1%20b&c HTTP/1.1\r\nHOST: x\r\nX-Multi: 1\r\nx-multi:  2  \r\n\r\n").response()[2].decode()
        for want in ["method=GET", "target=/info?a=1%20b&c", "path=/info", "query=a=1%20b&c", "version=HTTP/1.1", "secure=0",
                     "h HOST=x", "h X-Multi=1", "h x-multi=2"]:
            if want not in info.splitlines():
                raise Failed("info: no %r in %r" % (want, info))
        info = Peer(port).send(get(b"/inf%6F")).response()[2].decode()
        if "path=/info" not in info.splitlines():
            raise Failed("escape in a path: %r" % info)
        info = Peer(port).send(b"GET http://OTHER:81/info?x HTTP/1.1\r\nHost: x\r\n\r\n").response()[2].decode()
        for want in ["path=/info", "query=x", "h Host=OTHER:81"]:
            if want not in info.splitlines():
                raise Failed("absolute form: no %r in %r" % (want, info))

        # bodies of requests
        check("length", Peer(port).send(b"POST /echo HTTP/1.1\r\n" + H + b"Content-Length: 5\r\n\r\nabcde").response()[2], b"abcde")
        check("reported length", Peer(port).send(b"POST /echo-len HTTP/1.1\r\n" + H + b"Content-Length: 5\r\n\r\nabcde").response()[2], b"5 5")
        chunks = b"3\r\nabc\r\nA;ext=1;x=\"y\"\r\n0123456789\r\n0\r\nX-T: 1\r\n\r\n"
        p = Peer(port).send(b"POST /echo HTTP/1.1\r\n" + H + b"Transfer-Encoding: chunked\r\n\r\n" + chunks)
        check("chunked", p.response()[2], b"abc0123456789")
        check("after a trailer", p.send(get(b"/hello")).response()[2], b"Hello\n")
        p.close()
        check("chunked, unknown length", Peer(port).send(b"POST /echo-len HTTP/1.1\r\n" + H + b"Transfer-Encoding: chunked\r\n\r\n1\r\na\r\n0\r\n\r\n").response()[2], b"1 -1")
        data = bytes(random.Random(1).getrandbits(8) for _ in range(200000))
        check("large body", Peer(port).send(b"POST /echo HTTP/1.1\r\n" + H + b"Content-Length: 200000\r\n\r\n" + data).response()[2], data)

        # a body that the handler leaves unread keeps the connection in step
        p = Peer(port).send(b"POST /noread HTTP/1.1\r\n" + H + b"Content-Length: 5\r\n\r\nabcde")
        check("unread", p.response()[2], b"unread\n")
        check("after unread", p.send(get(b"/hello")).response()[2], b"Hello\n")
        p.close()
        p = Peer(port).send(b"POST /noread HTTP/1.1\r\n" + H + b"Transfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n0\r\n\r\n")
        check("unread chunked", p.response()[2], b"unread\n")
        check("after unread chunked", p.send(get(b"/hello")).response()[2], b"Hello\n")
        p.close()

        # a body larger than the limit, found while it is read
        big = (b"%x\r\n" % 600000 + b"a" * 600000 + b"\r\n") * 2 + b"0\r\n\r\n"
        p = Peer(port).send(b"POST /echo HTTP/1.1\r\n" + H + b"Transfer-Encoding: chunked\r\n\r\n" + big)
        check("over the limit", p.response()[0], 413)
        p.close()

        # a client that stops inside a body
        p = Peer(port).send(b"POST /echo HTTP/1.1\r\n" + H + b"Content-Length: 5\r\n\r\nab")
        check("timeout in a body", p.response()[0], 408)
        check("closed after 408", p.closed(), True)
        p.close()
        p = Peer(port).send(b"POST /echo HTTP/1.1\r\n" + H + b"Content-Length: 5\r\n\r\nab")
        p.s.shutdown(socket.SHUT_WR)
        check("end inside a body", p.response()[0], 400)
        p.close()

        # 100 Continue
        p = Peer(port).send(b"POST /echo HTTP/1.1\r\n" + H + b"Content-Length: 5\r\nExpect: 100-Continue\r\n\r\n")
        check("continue", p.response()[0], 100)
        check("after continue", p.send(b"abcde").response()[2], b"abcde")
        check("keeps the connection", p.send(get(b"/hello")).response()[2], b"Hello\n")
        p.close()
        # a handler that does not read: no 100, and the connection ends
        p = Peer(port).send(b"POST /noread HTTP/1.1\r\n" + H + b"Content-Length: 5\r\nExpect: 100-continue\r\n\r\n")
        r = p.response()
        check("no continue", r[0], 200)
        check("connection field", r[1].get("connection"), "close")
        check("closed without continue", p.closed(), True)
        p.close()
        p = Peer(port).send(b"GET /hello HTTP/1.1\r\n" + H + b"Expect: 100-continue\r\n\r\n")
        check("expectation without a body", p.response()[0], 200)
        p.close()

        # HEAD
        p = Peer(port).send(b"HEAD /hello HTTP/1.1\r\n" + H + b"\r\n")
        r = p.response(head=True)
        check("head length", r[1].get("content-length"), "6")
        check("no body after head", p.closed(), False)
        check("nothing was sent", p.buf, b"")
        check("after head", p.send(get(b"/hello")).response()[2], b"Hello\n")
        p.close()
        p = Peer(port).send(b"HEAD /big?n=100000 HTTP/1.1\r\n" + H + b"\r\n")
        r = p.response(head=True)
        if "transfer-encoding" in r[1] or "content-length" in r[1]:
            raise Failed("head of a long body: %s" % r[1])
        check("no body after a long head", p.closed(), False)
        check("nothing was sent", p.buf, b"")
        p.close()
        p = Peer(port).send(b"HEAD /missing HTTP/1.1\r\n" + H + b"\r\n")
        check("404 for head", p.response(head=True)[0], 404)
        check("nothing was sent", p.closed(), False)
        check("nothing was sent", p.buf, b"")
        p.close()
        p = Peer(port).send(b"HEAD /a%zz HTTP/1.1\r\n" + H + b"\r\n")
        check("error for head", p.response(head=True)[0], 400)
        check("closes", p.closed(), True)
        check("nothing was sent", p.buf, b"")
        p.close()

        # responses without a body
        p = Peer(port).send(get(b"/status?c=204&body=1"))
        r = p.response()
        if "content-length" in r[1] or "transfer-encoding" in r[1]:
            raise Failed("204: %s" % r[1])
        check("204 sends nothing", p.closed(), False)
        check("204 sends nothing", p.buf, b"")
        p.close()

        # framing of responses
        for n in (0, 1, 8191, 8192, 8193, 70000, 300000):
            p = Peer(port).send(get(b"/big?n=%d" % n))
            r = p.response()
            check("length of /big", len(r[2]), n)
            check("data of /big", r[2], pattern(n))
            framing = "content-length" if n <= 8192 else "transfer-encoding"
            if framing not in r[1]:
                raise Failed("/big %d: %s" % (n, r[1]))
            check("after /big", p.send(get(b"/hello")).response()[2], b"Hello\n")
            p.close()
        r = Peer(port).send(b"GET /big?n=70000 HTTP/1.0\r\n\r\n").response()
        if "transfer-encoding" in r[1] or "content-length" in r[1]:
            raise Failed("long body under 1.0: %s" % r[1])
        check("end of the connection ends the body", r[2], pattern(70000))
        r = Peer(port).send(b"GET /big?n=5 HTTP/1.0\r\n\r\n").response()
        check("short body under 1.0", r[1].get("content-length"), "5")

        p = Peer(port).send(get(b"/flush"))
        t0 = time.time()
        status_line = p.line()
        first = time.time() - t0
        check("flush status", status_line.split(b" ")[1], b"200")
        p.buf = status_line + b"\r\n" + p.buf
        r = p.response()
        check("flush chunked", r[1].get("transfer-encoding"), "chunked")
        check("flush body", r[2], b"one\ntwo\n")
        if first > 0.15:
            raise Failed("the head came late: %.2f" % first)
        p.close()

        r = Peer(port).send(get(b"/close")).response()
        check("closed writer", r[2], b"closed\n")
        p = Peer(port).send(get(b"/conn-close"))
        r = p.response()
        check("connection field once", r[1].get("connection"), "close")
        check("close by the handler", p.closed(), True)
        p.close()

        # lengths that the handler gets wrong
        p = Peer(port).send(get(b"/len?n=3&claim=3"))
        check("exact", p.response()[2], pattern(3))
        check("exact keeps the connection", p.closed(), False)
        p.close()
        p = Peer(port).send(get(b"/len?n=2&claim=5"))
        p.line()
        while p.line():
            pass
        got = b""
        try:
            while True:
                p.fill()
        except (EOFError, OSError):
            got = p.buf
        check("too few bytes", got, pattern(2))
        p.close()

        p = Peer(port).send(get(b"/fail-late"))
        r0 = p.line()
        while p.line():
            pass
        check("failure after the head", r0.split(b" ")[1], b"200")
        size = int(p.line(), 16)
        p.take(size + 2)
        if not p.closed(3):
            raise Failed("the connection stays open after a failure")
        if b"0\r\n\r\n" in p.buf:
            raise Failed("a broken response looks complete")
        p.close()

        # a slow head: 408; a silent connection: no response
        p = Peer(port).send(b"GET /hello HTTP/1.1\r\n")
        t0 = time.time()
        check("slow head", p.response()[0], 408)
        if not 0.5 < time.time() - t0 < 3:
            raise Failed("408 after %.2f" % (time.time() - t0))
        p.close()
        p = Peer(port)
        if not p.closed(4):
            raise Failed("a silent connection stays open")
        check("no response to silence", p.buf, b"")
        p.close()

        # the idle timeout between requests
        p = Peer(port)
        p.send(get(b"/hello")).response()
        t0 = time.time()
        if not p.closed(4):
            raise Failed("an idle connection stays open")
        if not 1.0 < time.time() - t0 < 3:
            raise Failed("closed after %.2f" % (time.time() - t0))
        p.close()

        # a client that does not read: the write limit frees the thread
        p = Peer(port, rcvbuf=4096).send(get(b"/big?n=800000000"))
        time.sleep(4.5)
        ok = Peer(port).send(get(b"/hello")).response()[2]
        check("server after a stalled client", ok, b"Hello\n")
        p.s.settimeout(1)
        total = 0
        try:
            while True:
                d = p.s.recv(1 << 20)
                if not d:
                    break
                total += len(d)
        except OSError:
            pass
        if total >= 800000000:
            raise Failed("the stalled transfer was finished")
        p.close()

        # the limit of connections: a seventh one waits until one ends
        peers = [Peer(port).send(b"GET /sleep?ms=1000 HTTP/1.1\r\n" + H + b"\r\n") for _ in range(6)]
        time.sleep(0.3)
        late = Peer(port).send(get(b"/hello"))
        late.s.settimeout(0.4)
        try:
            d = late.s.recv(1)
            raise Failed("the seventh connection was served: %r" % d)
        except socket.timeout:
            pass
        late.s.settimeout(10)
        for q in peers:
            check("sleep", q.response()[2], b"slept\n")
            q.close()
        check("served after the others", late.response()[2], b"Hello\n")
        late.close()

        # shutdown: a response under way finishes, idle connections end
        idle = Peer(port)
        idle.send(get(b"/hello")).response()
        busy = Peer(port).send(b"GET /sleep?ms=800 HTTP/1.1\r\n" + H + b"\r\n")
        time.sleep(0.2)
        stop = Peer(port).send(get(b"/shutdown"))
        check("shutdown", stop.response()[2], b"stopping\n")
        stop.close()
        time.sleep(0.4)
        if not idle.closed(1):
            raise Failed("an idle connection stays open at the shutdown")
        idle.close()
        r = busy.response()
        check("busy response", r[2], b"slept\n")
        check("busy closes", r[1].get("connection"), "close")
        busy.close()
        srv.proc.wait(10)
        srv.reader.join(5)
        if "serve returned" not in srv.log or srv.proc.returncode != 0:
            raise Failed("shutdown: %s %s" % (srv.proc.returncode, srv.log))
    finally:
        srv.stop()


def case_clients(env):
    srv = Server(env)
    try:
        c = http.client.HTTPConnection("127.0.0.1", srv.port, timeout=10)
        for _ in range(2):
            c.request("GET", "/hello")
            r = c.getresponse()
            check("http.client", (r.status, r.read()), (200, b"Hello\n"))
        c.request("POST", "/echo", body=b"abc", headers={"Content-Type": "text/plain"})
        check("http.client post", c.getresponse().read(), b"abc")

        def chunks():
            yield b"abc"
            yield b"defg"

        c.request("POST", "/echo", body=chunks(), encode_chunked=True, headers={"Transfer-Encoding": "chunked"})
        check("http.client chunked", c.getresponse().read(), b"abcdefg")
        c.request("GET", "/big?n=300000")
        check("http.client long", c.getresponse().read(), pattern(300000))
        c.request("HEAD", "/hello")
        check("http.client head", c.getresponse().read(), b"")
        c.close()

        curl = shutil.which("curl") or r"C:\Windows\System32\curl.exe"
        if not Path(curl).exists():
            print("          no curl")
            return
        url = "http://127.0.0.1:%d" % srv.port

        def run(*a, data=None):
            return subprocess.run([curl, "-s", "-S", "--max-time", "20", "-o", "-", *a], input=data, capture_output=True, timeout=30)

        r = run(url + "/hello")
        check("curl", (r.returncode, r.stdout), (0, b"Hello\n"))
        r = run("--http1.0", url + "/hello")
        check("curl 1.0", (r.returncode, r.stdout), (0, b"Hello\n"))
        r = run("--data-binary", "@-", url + "/echo", data=pattern(100000))
        check("curl post", (r.returncode, r.stdout), (0, pattern(100000)))
        r = run("-H", "Transfer-Encoding: chunked", "--data-binary", "@-", url + "/echo", data=pattern(20000))
        check("curl chunked", (r.returncode, r.stdout), (0, pattern(20000)))
        r = run(url + "/big?n=500000")
        check("curl long", (r.returncode, r.stdout), (0, pattern(500000)))
        r = run("-I", url + "/hello")
        if r.returncode or not r.stdout.startswith(b"HTTP/1.1 200"):
            raise Failed("curl head: %r %r" % (r.stdout, r.stderr))
    finally:
        srv.stop()


def case_tls(env):
    t = certificates(env)
    srv = Server(env, str(t.certs / "server.p12"))
    try:
        ctx = ssl.create_default_context(cafile=str(t.certs / "ca.pem"))
        ctx.check_hostname = True

        def wrap(s):
            return ctx.wrap_socket(s, server_hostname="localhost")

        for version in (ssl.TLSVersion.TLSv1_2, ssl.TLSVersion.TLSv1_3):
            ctx.minimum_version = version
            ctx.maximum_version = version
            p = Peer(srv.port, wrap=wrap)
            check("tls", p.send(get(b"/hello")).response()[2], b"Hello\n")
            check("tls reuse", p.send(get(b"/hello")).response()[2], b"Hello\n")
            info = p.send(get(b"/info")).response()[2]
            if b"secure=1" not in info:
                raise Failed("secure: %r" % info)
            check("tls post", p.send(b"POST /echo HTTP/1.1\r\n" + H + b"Content-Length: 100000\r\n\r\n" + pattern(100000)).response()[2], pattern(100000))
            r = p.send(get(b"/big?n=300000")).response()
            check("tls long", r[2], pattern(300000))
            p.close()
            p = Peer(srv.port, wrap=wrap).send(b"GET /hello HTTP/1.0\r\n\r\n")
            check("tls close", p.response()[2], b"Hello\n")
            check("tls close", p.closed(), True)
            p.close()
            p = Peer(srv.port, wrap=wrap).send(b"GET /hello HTTP/1.1\r\n\r\n")
            check("tls error", p.response()[0], 400)
            p.close()
        ctx.minimum_version = ssl.TLSVersion.TLSv1_2
        ctx.maximum_version = ssl.TLSVersion.TLSv1_3
        c = http.client.HTTPSConnection("localhost", srv.port, context=ctx, timeout=10)
        c.request("GET", "/hello")
        check("https", c.getresponse().read(), b"Hello\n")
        c.close()

        # a client that does not read: the write limit also works under TLS
        p = Peer(srv.port, wrap=wrap, rcvbuf=4096).send(get(b"/big?n=800000000"))
        time.sleep(4.5)
        q = Peer(srv.port, wrap=wrap)
        check("server after a stalled client", q.send(get(b"/hello")).response()[2], b"Hello\n")
        q.close()
        p.close()

        # plain text on the port of TLS ends in the handshake, and the server goes on
        s = socket.create_connection(("127.0.0.1", srv.port), timeout=10)
        s.sendall(get(b"/hello"))
        try:
            s.recv(1000)
        except OSError:
            pass
        s.close()
        p = Peer(srv.port, wrap=wrap)
        check("after a plain client", p.send(get(b"/hello")).response()[2], b"Hello\n")
        p.close()
    finally:
        srv.stop()


class Backend(http.server.BaseHTTPRequestHandler):
    """The server for the tests of the client: only the standard library."""

    protocol_version = "HTTP/1.1"

    def log_message(self, *a):
        pass

    def reply(self, status, body=b"", fields=()):
        self.send_response(status)
        for k, v in fields:
            self.send_header(k, v)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def read_body(self):
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            out = b""
            while True:
                size = int(self.rfile.readline().split(b";")[0], 16)
                if size == 0:
                    while self.rfile.readline() not in (b"\r\n", b""):
                        pass
                    return out
                out += self.rfile.read(size)
                self.rfile.readline()
        return self.rfile.read(int(self.headers.get("Content-Length", "0")))

    watched = "nothing"

    def watch(self):
        """Half a response, and then what the client does with the connection."""
        data = gzip.compress(noise(20000), mtime=0)
        self.send_response(200)
        self.send_header("Content-Encoding", "gzip")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data[:len(data) // 2])
        self.wfile.flush()
        self.connection.settimeout(4)
        try:
            Backend.watched = "data" if self.connection.recv(1) else "closed"
        except socket.timeout:
            Backend.watched = "open"
        except OSError:
            Backend.watched = "closed"
        self.close_connection = True

    def gz(self, what):
        """A response in gzip, of the sort that the name says."""
        if what == "watch":
            self.watch()
            return
        if what == "watched":
            self.reply(200, Backend.watched.encode())
            return
        if self.command == "POST":
            self.read_body()
        plain = b"hello compressed " * 100
        data = gzip.compress(plain, mtime=0)
        fields = [("X-Seen", self.headers.get("Accept-Encoding", "-")), ("X-Port", str(self.client_address[1]))]
        if what in ("204", "304"):
            self.send_response(int(what))
            self.send_header("Content-Encoding", "gzip")
            for k, v in fields:
                self.send_header(k, v)
            self.end_headers()
            return
        if what == "redirect":
            self.reply(302, b"", [("Location", "/gz/plain")])
            return
        if what == "cut" or what == "slow":
            if what == "slow":
                data = gzip.compress(noise(20000), mtime=0)
            self.send_response(200)
            self.send_header("Content-Encoding", "gzip")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data[:len(data) // 2])
            self.wfile.flush()
            if what == "slow":
                time.sleep(3)
            self.close_connection = True
            return
        # a block of the reserved type, the same in every version of zlib
        invalid = bytes.fromhex("1f8b08000000000000ff") + b"\x07" + bytes(20)
        body, codings = {"plain": (data, ["gzip"]), "upper": (data, ["GZIP"]), "x-gzip": (data, ["x-gzip"]),
                         "list": (data, ["gzip, br"]), "twofields": (data, ["gzip", "gzip"]), "br": (data, ["br"]),
                         "none": (plain, []), "empty": (b"", ["gzip"]), "emptygz": (gzip.compress(b"", mtime=0), ["gzip"]),
                         "members": (data + data, ["gzip"]), "zeros": (data + bytes(10), ["gzip"]),
                         "corrupt": (invalid, ["gzip"]), "badcrc": (data[:-8] + bytes([data[-8] ^ 1]) + data[-7:], ["gzip"]),
                         "trunc": (data[:-4], ["gzip"]), "notgzip": (plain, ["gzip"]), "trailing": (data + b"xx", ["gzip"]),
                         "error": (data, ["gzip"]), "big": (gzip.compress(pattern(3000000), mtime=0), ["gzip"])}[what]
        self.send_response(500 if what == "error" else 200)
        for c in codings:
            self.send_header("Content-Encoding", c)
        for k, v in fields:
            self.send_header(k, v)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(body)

    def handle_any(self):
        path = self.path.replace("http://proxied.invalid", "")
        if path.startswith("/gz/"):
            self.gz(path[4:])
        elif path.startswith("/text"):
            self.reply(200, b"hello")
        elif path.startswith("/status/"):
            self.reply(int(path[8:]), b"status body")
        elif path == "/multi":
            self.reply(200, b"", [("Set-Cookie", "a=1; Path=/"), ("X-A", "1"), ("set-cookie", "b=2"), ("X-Empty", "")])
        elif path == "/redirect":
            self.reply(302, b"", [("Location", "/text")])
        elif path == "/echo":
            body = self.read_body()
            info = "%s len=%d sum=%s type=%s te=%s multi=%s" % (self.command, len(body), hashlib.sha256(body).hexdigest()[:8],
                                                                self.headers.get("Content-Type"), self.headers.get("Transfer-Encoding"),
                                                                self.headers.get_all("X-Multi"))
            self.reply(200, info.encode())
        elif path == "/slow-head":
            time.sleep(3)
            self.close_connection = True
        elif path == "/slow-body":
            self.send_response(200)
            self.send_header("Content-Length", "100")
            self.end_headers()
            self.wfile.write(b"part")
            self.wfile.flush()
            time.sleep(3)
            self.close_connection = True
        elif path == "/truncated":
            self.send_response(200)
            self.send_header("Content-Length", "100")
            self.end_headers()
            self.wfile.write(b"short")
            self.close_connection = True
        elif path == "/garbage":
            self.wfile.write(b"this is not http\r\n\r\n")
            self.close_connection = True
        elif path == "/hangup":
            self.close_connection = True
        elif path.startswith("/download"):
            n = int(path.split("=")[1])
            self.send_response(200)
            self.send_header("Content-Length", str(n))
            self.end_headers()
            block = bytes((i * 7 + i // 251) % 256 for i in range(65536))
            sent = 0
            try:
                while sent < n:
                    k = min(n - sent, len(block))
                    self.wfile.write(block[:k])
                    sent += k
            except OSError:
                self.close_connection = True
        else:
            self.reply(404, b"unknown")

    do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = do_HEAD = handle_any


class Backends:
    def __init__(self, certs):
        self.servers = []
        self.plain = self.start(None)
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ctx.load_cert_chain(str(certs / "server.pem"))
        self.tls = self.start(ctx)
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ctx.load_cert_chain(str(certs / "wrong.pem"))
        self.wrong = self.start(ctx)

    def start(self, ctx):
        srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Backend)
        srv.daemon_threads = True
        srv.handle_error = lambda *a: None  # a client that gives up is a part of the tests
        if ctx:
            srv.socket = ctx.wrap_socket(srv.socket, server_side=True)
        threading.Thread(target=srv.serve_forever, daemon=True).start()
        self.servers.append(srv)
        return srv.server_address[1]

    def stop(self):
        for s in self.servers:
            s.shutdown()
            s.server_close()


def free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def case_client(env):
    t = certificates(env)
    b = Backends(t.certs)
    try:
        out = env.run("client", "http://127.0.0.1:%d" % b.plain, "https://localhost:%d" % b.tls, str(t.certs / "ca.pem"),
                      str(t.certs / "other.pem"), "https://localhost:%d" % b.wrong, str(free_port()),
                      "127.0.0.1:%d" % b.plain).replace("\r\n", "\n")
    finally:
        b.stop()
    compare("client", out, (TESTS / "expected" / "client.txt").read_text())


def case_together(env):
    t = certificates(env)
    out = env.run("together", str(t.certs / "server.p12"), str(t.certs / "ca.pem")).replace("\r\n", "\n")
    compare("together", out, (TESTS / "expected" / "together.txt").read_text())


def noise(n):
    """The bytes of PutNoise in Main.m3: they do not compress."""
    out = bytearray(n)
    x = 1
    for i in range(n):
        x = (x * 1103515245 + 12345) % 2147483648
        out[i] = (x >> 16) & 255
    return bytes(out)


def gunzip(b):
    """The data of exactly one gzip member; anything else is a failure."""
    d = zlib.decompressobj(31)
    try:
        out = d.decompress(b) + d.flush()
    except zlib.error as e:
        raise Failed("gzip: %s in %r" % (e, b[:40]))
    if not d.eof or d.unused_data:
        raise Failed("gzip: eof %s, %d bytes after it" % (d.eof, len(d.unused_data)))
    return out


def gz_request(query, ae=b"gzip", method=b"GET", version=b"HTTP/1.1"):
    fields = H if version == b"HTTP/1.1" else b""
    if ae is not None:
        for v in [ae] if isinstance(ae, bytes) else ae:
            fields += b"Accept-Encoding: " + v + b"\r\n"
    return method + b" /gz" + query + b" " + version + b"\r\n" + fields + b"\r\n"


# the field or fields of a request, and whether the answer is in gzip
ACCEPT = [
    (b"gzip", True),
    (b"GZIP", True),
    (b"Gzip", True),
    (b"x-gzip", True),
    (b"X-GZIP", True),
    (b"deflate, gzip", True),
    (b"deflate,gzip,br", True),
    (b"  gzip  ", True),
    (b"gzip;q=1", True),
    (b"gzip;q=1.", True),
    (b"gzip;q=1.0", True),
    (b"gzip;q=1.000", True),
    (b"gzip;q=0.5", True),
    (b"gzip;q=0.001", True),
    (b"gzip ; q=0.5", True),
    (b"gzip;Q=1", True),
    (b"gzip,", True),
    (b",gzip", True),
    (b"*", True),
    (b"*;q=0.1", True),
    (b"br, *", True),
    (b"gzip;q=0.5, *;q=0", True),
    ([b"br", b"gzip"], True),
    ([b"gzip", b"br"], True),
    (None, False),
    (b"", False),
    (b"identity", False),
    (b"br", False),
    (b"deflate, br", False),
    (b"gzipx", False),
    (b"notgzip", False),
    (b"gzip;q=0", False),
    (b"gzip;q=0.", False),
    (b"gzip;q=0.0", False),
    (b"gzip;q=0.000", False),
    (b"gzip ; q=0", False),
    (b"GZIP;Q=0", False),
    (b"x-gzip;q=0", False),
    (b"*;q=0", False),
    (b"gzip;q=0, *", False),
    (b"*, gzip;q=0", False),
    (b"*;q=0, br", False),
    (b"*, *;q=0", False),
    (b"gzip, gzip;q=0", False),
    (b"gzip;q=0, x-gzip", False),
    ([b"gzip", b"gzip;q=0"], False),
    (b"gzip;q=abc", False),
    (b"gzip;q=0.a", False),
    (b"gzip;q=0.00x", False),
    (b"gzip;q=", False),
    (b"gzip;", False),
    (b"gzip;q=2", False),
    (b"gzip;q=1.5", False),
    (b"gzip;q=1.0000", False),
    (b"gzip;q=0.0001", False),
    (b"gzip;level=1", False),
    (b"gzip;q=0.5;x=1", False),
]


def case_gzip(env):
    srv = Server(env)
    try:
        port = srv.port
        p = Peer(port)
        want = pattern(1000)
        for ae, yes in ACCEPT:
            st, d, body = p.send(gz_request(b"", ae)).response()
            what = "accept %r" % (ae,)
            check(what, st, 200)
            check(what + ": vary", d.get("vary"), "Accept-Encoding")
            if yes:
                check(what + ": coding", d.get("content-encoding"), "gzip")
                check(what + ": data", gunzip(body), want)
                check(what + ": smaller", len(body) < 1000, True)
            else:
                check(what + ": coding", d.get("content-encoding"), None)
                check(what + ": data", body, want)
            check(what + ": framing", d.get("content-length"), str(len(body)))
        p.close()

        def one(query, ae=b"gzip", method=b"GET", version=b"HTTP/1.1"):
            q = Peer(port).send(gz_request(query, ae, method, version))
            r = q.response(head=method == b"HEAD")
            fields = q.fields
            q.close()
            return r[0], r[1], r[2], fields

        # short responses have a length, others are chunked
        st, d, body, _ = one(b"?n=300000&noise=1")
        check("long: chunked", d.get("transfer-encoding"), "chunked")
        check("long: no length", d.get("content-length"), None)
        check("long: data", gunzip(body), noise(300000))
        st, d, body, _ = one(b"?n=20000&noise=1")
        check("over the buffer: chunked", d.get("transfer-encoding"), "chunked")
        check("over the buffer: data", gunzip(body), noise(20000))
        st, d, body, _ = one(b"?n=1000000")
        check("much more than the buffer, compressed to less: length", d.get("content-length"), str(len(body)))
        check("much more than the buffer, compressed to less: data", gunzip(body), pattern(1000000))

        # bodies that are empty
        st, d, body, _ = one(b"?n=0")
        check("empty: coding", d.get("content-encoding"), "gzip")
        check("empty: data", gunzip(body), b"")
        st, d, body, _ = one(b"?nobody=1")
        check("no body: coding", d.get("content-encoding"), None)
        check("no body: vary", d.get("vary"), "Accept-Encoding")
        check("no body: length", (d.get("content-length"), body), ("0", b""))

        # Vary
        for query, want_vary in [(b"?vary=1", ["Origin", "Accept-Encoding"]), (b"?vary=2", ["*"]), (b"?vary=3", ["accept-encoding"])]:
            for ae in (b"gzip", None):
                vary = [v for k, v in one(query, ae)[3] if k == "vary"]
                check("vary %r %r" % (query, ae), vary, want_vary)

        # what forbids compression
        for query, what in [(b"?c=206", "206"), (b"?c=206&cr=1", "206 with a range"), (b"?ce=1", "own coding"), (b"?cr=1", "range")]:
            st, d, body, _ = one(query)
            check(what + ": data", body, want)
            check(what + ": vary", d.get("vary"), "Accept-Encoding")
            check(what + ": coding", d.get("content-encoding"), "br" if what == "own coding" else None)
        for c in (204, 304):
            st, d, body, _ = one(b"?c=%d" % c)
            check("%d: status" % c, st, c)
            check("%d: coding" % c, d.get("content-encoding"), None)
            check("%d: vary" % c, d.get("vary"), "Accept-Encoding")
            check("%d: no body" % c, body, b"")
        st, d, body, _ = one(b"?after=204")
        check("status set after body", (st, d.get("content-encoding"), body), (204, None, b""))
        st, d, body, _ = one(b"?after=304")
        check("status set after body", (st, d.get("content-encoding"), body), (304, None, b""))
        st, d, body, _ = one(b"?c=404")
        check("404 is compressed", (st, gunzip(body)), (404, want))
        st, d, body, _ = one(b"?late=1")
        check("late: coding", d.get("content-encoding"), None)
        check("late: vary", d.get("vary"), None)
        check("late: data", body, want)

        # fields that compression changes
        st, d, body, _ = one(b"?etag=1")
        check("strong tag", d.get("etag"), 'W/"abc"')
        st, d, body, _ = one(b"?etag=2")
        check("weak tag", d.get("etag"), 'W/"abc"')
        st, d, body, _ = one(b"?etag=1", None)
        check("tag, not compressed", d.get("etag"), '"abc"')
        st, d, body, _ = one(b"?claim=1000")
        check("declared length dropped", d.get("content-length"), str(len(body)))
        check("declared length, data", gunzip(body), want)
        st, d, body, _ = one(b"?claim=1000", None)
        check("declared length kept", d.get("content-length"), "1000")
        check("declared length kept, data", body, want)

        # HEAD has the fields of GET
        got = one(b"?etag=1")[1]
        head = one(b"?etag=1", method=b"HEAD")[1]
        for name in ("content-encoding", "vary", "etag", "content-length"):
            check("head " + name, head.get(name), got.get(name))
        head = one(b"?n=300000&noise=1", method=b"HEAD")[1]
        check("long head: coding", head.get("content-encoding"), "gzip")
        check("long head: no framing", (head.get("content-length"), head.get("transfer-encoding")), (None, None))

        # a flush reaches the client while the handler goes on
        q = Peer(port).send(gz_request(b"?n=100&flush=1"))
        while q.line():
            pass
        d = zlib.decompressobj(31)
        out = b""
        while len(out) < 100:
            out += d.decompress(q.take(int(q.line(), 16)))
            q.take(2)
        check("flush: before the pause", out, pattern(100))
        while True:
            size = int(q.line(), 16)
            if size == 0:
                break
            out += d.decompress(q.take(size))
            q.take(2)
        check("flush: all", (out, d.eof), (pattern(100) + b"after", True))
        q.close()

        # a handler that closes the writer
        st, d, body, _ = one(b"?close=1")
        check("close: data", gunzip(body), want)

        # the connection stays in step
        q = Peer(port)
        q.send(gz_request(b"") + gz_request(b"?n=300000&noise=1") + get(b"/hello") + gz_request(b"", None) + gz_request(b"?n=0"))
        check("keep-alive 1", gunzip(q.response()[2]), want)
        check("keep-alive 2", gunzip(q.response()[2]), noise(300000))
        check("keep-alive 3", q.response()[2], b"Hello\n")
        check("keep-alive 4", q.response()[2], want)
        check("keep-alive 5", gunzip(q.response()[2]), b"")
        check("keep-alive open", q.closed(), False)
        q.close()

        # failures of a handler
        q = Peer(port).send(gz_request(b"?n=10&fail=1"))
        st, d, body = q.response()
        check("failure before the head", st, 500)
        check("failure before the head: fields", (d.get("content-encoding"), d.get("vary")), (None, None))
        check("failure before the head: text", body, b"Internal Server Error\n")
        q.close()
        q = Peer(port).send(gz_request(b"?n=10&fail=2"))
        try:
            q.response()
            raise Failed("failure after the head: the response looks complete")
        except (EOFError, OSError):
            pass
        q.close()

        # HTTP/1.0
        q = Peer(port).send(gz_request(b"", version=b"HTTP/1.0"))
        st, d, body = q.response()
        check("1.0: length", d.get("content-length"), str(len(body)))
        check("1.0: data", gunzip(body), want)
        check("1.0: closes", q.closed(), True)
        q.close()
        q = Peer(port).send(gz_request(b"?n=300000&noise=1", version=b"HTTP/1.0"))
        st, d, body = q.response()
        check("1.0 long: framing", (d.get("content-length"), d.get("transfer-encoding")), (None, None))
        check("1.0 long: data", gunzip(body), noise(300000))
        q.close()

        # a body of a request stays raw, and a handler can take it
        z = gzip.compress(b"upload " * 1000, mtime=0)
        post = b"POST %s HTTP/1.1\r\n" + H + b"Content-Length: %d\r\n\r\n"
        q = Peer(port).send(post % (b"/gunzip", len(z)) + z)
        check("upload", q.response()[2], b"upload " * 1000)
        q.close()
        q = Peer(port).send(post % (b"/echo", len(z)) + z)
        check("raw upload", q.response()[2], z)
        q.close()
        q = Peer(port).send(post % (b"/gunzip", 4) + b"abcd")
        check("damaged upload", q.response()[0], 500)
        q.close()
        q = Peer(port).send(post % (b"/gunzip?max=100", len(z)) + z)
        check("upload over its limit", q.response()[0], 500)
        q.close()

        check("server log", srv.log, [])

        # an independent client
        curl = shutil.which("curl") or r"C:\Windows\System32\curl.exe"
        if not Path(curl).exists():
            print("          no curl")
            return
        for query, data in [(b"?n=1000", want), (b"?n=300000&noise=1", noise(300000))]:
            r = subprocess.run([curl, "-s", "-S", "--max-time", "20", "--compressed", "http://127.0.0.1:%d/gz%s" % (port, query.decode())],
                               capture_output=True, timeout=30)
            check("curl", (r.returncode, r.stdout), (0, data))
    finally:
        srv.stop()


def case_gzclient(env):
    t = certificates(env)
    b = Backends(t.certs)
    try:
        out = env.run("gzclient", "http://127.0.0.1:%d" % b.plain, str(t.certs / "server.p12"), str(t.certs / "ca.pem")).replace("\r\n", "\n")
    finally:
        b.stop()
    compare("gzclient", out, (TESTS / "expected" / "gzclient.txt").read_text())


# cookies and multipart bodies

def cx(t):
    """An argument of the tests cookie and multipart: "-" for None, "x" and
    the bytes in hexadecimal, or "w" and the text in UTF-16 if it has
    characters above 255."""
    if t is None:
        return "-"
    if isinstance(t, str):
        if any(ord(c) > 255 for c in t):
            return "w" + t.encode("utf-16-be").hex()
        t = t.encode("latin-1")
    return "x" + t.hex()


def batch(env, test, lines):
    """The answers of Main.m3 to the lines, one each."""
    out = env.run(test, data=("\n".join(lines) + "\n").encode("ascii")).replace("\r\n", "\n").split("\n")
    if out and out[-1] == "":
        out.pop()
    if len(out) != len(lines):
        raise Failed("%s: %d answers to %d lines: %r" % (test, len(out), len(lines), out[-3:]))
    return out


def expect_all(what, lines, got, want):
    bad = [(l, g, w) for l, g, w in zip(lines, got, want) if g != w]
    if bad:
        l, g, w = bad[0]
        raise Failed("%s: %d wrong, the first:\n  line %s\n  got  %s\n  want %s" % (what, len(bad), l[:300], g[:300], w[:300]))


def http_date(t):
    d = datetime.datetime(1970, 1, 1) + datetime.timedelta(seconds=math.floor(t))
    return "%s, %02d %s %04d %02d:%02d:%02d GMT" % (WEEK[(d.weekday() + 1) % 7], d.day, MON[d.month - 1], d.year,
                                                     d.hour, d.minute, d.second)


def b_(t):
    return t if isinstance(t, bytes) else t.encode("latin-1")


TCHARS = b"!#$%&'*+-.^_`|~0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
OCTETS = bytes([0x21, *range(0x23, 0x2C), *range(0x2D, 0x3B), *range(0x3C, 0x5C), *range(0x5D, 0x7F)])
PATH_CHARS = bytes([*range(0x20, 0x3B), *range(0x3C, 0x7F)])
DOMAIN_CHARS = b"-.0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
EARLIEST = -11644473600
LATEST = 253402300800


def cookie_format(name, value, path=None, domain=None, expires=None, maxage=-1, flags="-"):
    """What Format makes of a valid cookie."""
    r = b_(name) + b"=" + b_(value)
    if expires is not None:
        r += b"; Expires=" + http_date(expires).encode()
    if maxage != -1:
        r += b"; Max-Age=%d" % maxage
    if domain is not None:
        r += b"; Domain=" + b_(domain)
    if path is not None:
        r += b"; Path=" + b_(path)
    if "s" in flags:
        r += b"; Secure"
    if "h" in flags:
        r += b"; HttpOnly"
    for f, word in (("S", b"Strict"), ("L", b"Lax"), ("N", b"None")):
        if f in flags:
            r += b"; SameSite=" + word
    return r


def format_line(name, value, path=None, domain=None, expires=None, maxage=-1, flags="-"):
    return "f %s %s %s %s %s %d %s" % (cx(name), cx(value), cx(path), cx(domain),
                                       "-" if expires is None else repr(expires), maxage, flags)


FORMAT_OK = [
    dict(name="a", value="b"),
    dict(name="id", value="a3fWa", path="/", maxage=2592000, flags="shL"),
    dict(name="e", value=""),
    dict(name="q", value='"abc"'),
    dict(name="q", value='""'),
    dict(name="o", value=OCTETS),
    dict(name="o", value=b'"' + OCTETS + b'"'),
    dict(name=TCHARS, value="v"),
    dict(name="d", value="1", expires=784111777),
    dict(name="d", value="1", expires=784111777.9),
    dict(name="d", value="1", expires=0),
    dict(name="d", value="1", expires=EARLIEST),
    dict(name="d", value="1", expires=LATEST - 1),
    dict(name="m", value="1", maxage=0),
    dict(name="m", value="1", maxage=2 ** 63 - 1),
    dict(name="c", value="1", domain="example.org"),
    dict(name="c", value="1", domain=".Example-1.org"),
    dict(name="c", value="1", domain=DOMAIN_CHARS),
    dict(name="p", value="1", path=b"/" + PATH_CHARS),
    dict(name="s", value="1", flags="sS"),
    dict(name="s", value="1", flags="sN"),
    dict(name="h", value="1", flags="h"),
    dict(name="__Secure-x", value="1", flags="s"),
    dict(name="__secure-x", value="1", flags="s"),
    dict(name="__Host-x", value="1", path="/", flags="s"),
    dict(name="__HOST-", value="1", path="/", flags="sh"),
    dict(name="__Hostx", value="1"),
    dict(name="all", value="v", path="/p", domain="d.org", expires=784111777, maxage=5, flags="shS"),
    dict(name="n" * 2048, value="v" * 2048),
    dict(name="p", value="1", path="/" + "a" * 1023),
    dict(name="c", value="1", domain="a" * 1024),
]

# the attributes, with exact texts, that the tables above rely on
FORMAT_ANCHORS = [
    (dict(name="id", value="a3fWa", path="/", maxage=2592000, flags="shL"),
     b"id=a3fWa; Max-Age=2592000; Path=/; Secure; HttpOnly; SameSite=Lax"),
    (dict(name="all", value="v", path="/p", domain="d.org", expires=784111777, maxage=5, flags="shS"),
     b"all=v; Expires=Sun, 06 Nov 1994 08:49:37 GMT; Max-Age=5; Domain=d.org; Path=/p; Secure; HttpOnly; "
     b"SameSite=Strict"),
    (dict(name="d", value="1", expires=0), b"d=1; Expires=Thu, 01 Jan 1970 00:00:00 GMT"),
    (dict(name="d", value="1", expires=EARLIEST), b"d=1; Expires=Mon, 01 Jan 1601 00:00:00 GMT"),
    (dict(name="d", value="1", expires=LATEST - 1), b"d=1; Expires=Fri, 31 Dec 9999 23:59:59 GMT"),
    (dict(name="s", value="1", flags="sN"), b"s=1; Secure; SameSite=None"),
]


def format_bad():
    """Cookies that Format rejects, with the expected answer."""
    bad = lambda d: "err HTTP.BadMessage (%s)" % d
    large = lambda d: "err HTTP.TooLarge (%s)" % d
    cases = []
    for c in range(256):
        if c not in TCHARS:
            cases.append((dict(name=b"a" + bytes([c]), value="v"), bad("invalid name of a cookie")))
        if c not in OCTETS:
            cases.append((dict(name="a", value=b"x" + bytes([c]) + b"y"), bad("invalid value of a cookie")))
        if c not in PATH_CHARS:
            cases.append((dict(name="a", value="v", path=b"/a" + bytes([c])), bad("invalid path of a cookie")))
        if c not in DOMAIN_CHARS:
            cases.append((dict(name="a", value="v", domain=b"a" + bytes([c])), bad("invalid domain of a cookie")))
    for name in ["", "w\u20ac"]:
        cases.append((dict(name=name, value="v"), bad("invalid name of a cookie")))
    for value in ['"', '"abc', 'abc"', '"a"b"', '"a b"', "a\u20ac", 'a"b']:
        cases.append((dict(name="a", value=value), bad("invalid value of a cookie")))
    for path in ["", "a", "a/", "/\u20ac"]:
        cases.append((dict(name="a", value="v", path=path), bad("invalid path of a cookie")))
    for domain in ["", "\u20ac"]:
        cases.append((dict(name="a", value="v", domain=domain), bad("invalid domain of a cookie")))
    for t in [EARLIEST - 1, EARLIEST - 0.5, LATEST, LATEST + 1e9]:
        cases.append((dict(name="a", value="v", expires=t), bad("expiry of a cookie out of range")))
    for m in [-2, -1000, -2 ** 63]:
        cases.append((dict(name="a", value="v", maxage=m), bad("negative Max-Age of a cookie")))
    cases.append((dict(name="a", value="v", flags="N"), bad("SameSite=None without Secure")))
    cases.append((dict(name="a", value="v", flags="hN"), bad("SameSite=None without Secure")))
    for name in ["__Secure-a", "__SECURE-a", "__secure-"]:
        cases.append((dict(name=name, value="v"), bad("cookie with the prefix __Secure- without Secure")))
    host = bad("cookie with the prefix __Host- without Secure, Path=/ or with Domain")
    for kw in [dict(path="/"), dict(flags="s"), dict(path="/a", flags="s"), dict(path="//", flags="s"),
               dict(path="/", domain="a.org", flags="s")]:
        cases.append((dict(name="__Host-a", value="v", **kw), host))
        cases.append((dict(name="__host-a", value="v", **kw), host))
    cases.append((dict(name="n" * 2048, value="v" * 2049), large("cookie larger than 4096 bytes")))
    cases.append((dict(name="n" * 4097, value=""), large("cookie larger than 4096 bytes")))
    cases.append((dict(name="a", value="v", path="/" + "a" * 1024), large("path of a cookie too long")))
    cases.append((dict(name="a", value="v", domain="a" * 1025), large("domain of a cookie too long")))
    return cases


def parsed(name, value, path=None, domain=None, expires=None, maxage=-1, flags="-"):
    """The answer to "p" for a valid field."""
    return "ok %s %s %s %s %s %d %s" % (cx(name), cx(value), cx(path), cx(domain),
                                        "-" if expires is None else "%d" % expires, maxage, flags)


MAX_INT = 2 ** 63 - 1

PARSE = [
    ("a=b", parsed("a", "b")),
    ("  a  =  b  ", parsed("a", "b")),
    ("\ta\t=\tb\t", parsed("a", "b")),
    ("a=b c", parsed("a", "b c")),
    ("a=", parsed("a", "")),
    ("a=b=c", parsed("a", "b=c")),
    ('a="b"', parsed("a", '"b"')),
    ('a="b; c"', parsed("a", '"b')),
    ("a=b;", parsed("a", "b")),
    ("a=b;;;", parsed("a", "b")),
    ("a=\xe9\xff", parsed("a", "\xe9\xff")),
    ("a,b=c", parsed("a,b", "c")),
    ("=b", None),
    (" =b", None),
    ("a", None),
    ("", None),
    ("a;b=c", None),
    (";a=b", None),
    ("a=b\x01", None),
    ("a=b; Path=/\x00", None),
    ("a=b\x7f", None),
    ("a=b\r\n", None),
    ("a=b\n", None),
    ("a=\u20ac", None),
    ("a=b; Path", parsed("a", "b")),
    ("a=b; Path=", parsed("a", "b")),
    ("a=b; Path=x", parsed("a", "b")),
    ("a=b; Path=/x", parsed("a", "b", path="/x")),
    ("a=b; path=/x", parsed("a", "b", path="/x")),
    ("a=b; PATH = /x y ", parsed("a", "b", path="/x y")),
    ("a=b; Path=/x; Path=y", parsed("a", "b")),
    ("a=b; Path=/x; Path=/y", parsed("a", "b", path="/y")),
    ("a=b; Path=/x=y", parsed("a", "b", path="/x=y")),
    ("a=b; Domain=", parsed("a", "b")),
    ("a=b; Domain", parsed("a", "b")),
    ("a=b; Domain=.Example.ORG", parsed("a", "b", domain="example.org")),
    ("a=b; domain=example.org; Domain=", parsed("a", "b", domain="example.org")),
    ("a=b; Domain=.", parsed("a", "b", domain="")),
    ("a=b; Domain=..x", parsed("a", "b", domain=".x")),
    ("a=b; Domain=\xc9X", parsed("a", "b", domain="\xc9x")),
    ("a=b; Max-Age=0", parsed("a", "b", maxage=0)),
    ("a=b; Max-Age=-1", parsed("a", "b", maxage=0)),
    ("a=b; Max-Age=-0", parsed("a", "b", maxage=0)),
    ("a=b; max-age=123", parsed("a", "b", maxage=123)),
    ("a=b; Max-Age= 5 ", parsed("a", "b", maxage=5)),
    ("a=b; Max-Age=+5", parsed("a", "b")),
    ("a=b; Max-Age=1a", parsed("a", "b")),
    ("a=b; Max-Age=", parsed("a", "b")),
    ("a=b; Max-Age=-", parsed("a", "b")),
    ("a=b; Max-Age=--1", parsed("a", "b")),
    ("a=b; Max-Age=1 2", parsed("a", "b")),
    ("a=b; Max-Age=9223372036854775807", parsed("a", "b", maxage=MAX_INT)),
    ("a=b; Max-Age=9223372036854775808", parsed("a", "b", maxage=MAX_INT)),
    ("a=b; Max-Age=99999999999999999999999999", parsed("a", "b", maxage=MAX_INT)),
    ("a=b; Max-Age=-99999999999999999999999999", parsed("a", "b", maxage=0)),
    ("a=b; Max-Age=5; Max-Age=x", parsed("a", "b", maxage=5)),
    ("a=b; Max-Age=5; Max-Age=7", parsed("a", "b", maxage=7)),
    ("a=b; Max-Age=0005", parsed("a", "b", maxage=5)),
    ("a=b; Secure", parsed("a", "b", flags="s")),
    ("a=b; SECURE=no", parsed("a", "b", flags="s")),
    ("a=b; httponly", parsed("a", "b", flags="h")),
    ("a=b; Secure ; HttpOnly", parsed("a", "b", flags="sh")),
    ("a=b; Secured", parsed("a", "b")),
    ("a=b; SameSite=Strict", parsed("a", "b", flags="S")),
    ("a=b; samesite=lax", parsed("a", "b", flags="L")),
    ("a=b; SameSite=NONE", parsed("a", "b", flags="N")),
    ("a=b; SameSite=bogus", parsed("a", "b")),
    ("a=b; SameSite=", parsed("a", "b")),
    ("a=b; SameSite", parsed("a", "b")),
    ("a=b; SameSite=Lax; SameSite=x", parsed("a", "b")),
    ("a=b; SameSite = Lax ", parsed("a", "b", flags="L")),
    ("a=b; foo=bar; Comment=x; Version=1", parsed("a", "b")),
    ("a=b; Expires=Thu, 01 Jan 1970 00:00:00 GMT", parsed("a", "b", expires=0)),
    ("a=b; Expires=Sun, 06 Nov 1994 08:49:37 GMT; Expires=bad", parsed("a", "b", expires=784111777)),
    ("a=b; Expires=Sun, 06 Nov 1994 08:49:37 GMT; Expires=Thu, 01 Jan 1970 00:00:00 GMT",
     parsed("a", "b", expires=0)),
    ("n" * 2048 + "=" + "v" * 2048, parsed("n" * 2048, "v" * 2048)),
    ("n" * 2048 + "=" + "v" * 2049, None),
    ("n" * 4097 + "=", None),
    ("n" * 2048 + " = " + "v" * 2048 + " ", parsed("n" * 2048, "v" * 2048)),
    ("a=b; Path=/" + "p" * 1023, parsed("a", "b", path="/" + "p" * 1023)),
    ("a=b; Path=/" + "p" * 1024, parsed("a", "b")),
    ("a=b; Path=/x; Path=/" + "p" * 1024, parsed("a", "b", path="/x")),
    ("a=b; Domain=" + "d" * 1024, parsed("a", "b", domain="d" * 1024)),
    ("a=b; Domain=" + "d" * 1025, parsed("a", "b")),
    ("a=b; Unknown=" + "u" * 5000, parsed("a", "b")),
    ("a=b; Path=  /" + "p" * 1023 + "  ", parsed("a", "b", path="/" + "p" * 1023)),
    ("a=b; Secure; " + "x" * 3000, parsed("a", "b", flags="s")),
]

MONTHS = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"]
DATE_DELIMITERS = bytes([0x09, *range(0x20, 0x30), *range(0x3B, 0x41), *range(0x5B, 0x61), *range(0x7B, 0x7F)])


def cookie_date(b):
    """Section 5.1.1 of RFC 6265, written down separately: the time or None."""
    tokens = [t for t in re.split(b"[" + re.escape(DATE_DELIMITERS) + b"]+", b) if t]
    found = {}
    for t in tokens:
        m = re.fullmatch(rb"(\d{1,2}):(\d{1,2}):(\d{1,2})(?:\D.*)?", t, re.S)
        if "time" not in found and m:
            found["time"] = tuple(int(g) for g in m.groups())
            continue
        m = re.fullmatch(rb"(\d{1,2})(?:\D.*)?", t, re.S)
        if "day" not in found and m:
            found["day"] = int(m.group(1))
            continue
        if "month" not in found and t[:3].lower().decode("latin-1") in MONTHS:
            found["month"] = MONTHS.index(t[:3].lower().decode("latin-1")) + 1
            continue
        m = re.fullmatch(rb"(\d{2,4})(?:\D.*)?", t, re.S)
        if "year" not in found and m:
            found["year"] = int(m.group(1))
    if len(found) < 4:
        return None
    year = found["year"]
    if 70 <= year <= 99:
        year += 1900
    elif year <= 69:
        year += 2000
    h, mi, s = found["time"]
    if not 1 <= found["day"] <= 31 or year < 1601 or h > 23 or mi > 59 or s > 59:
        return None
    try:
        d = datetime.datetime(year, found["month"], found["day"], h, mi, s)
    except ValueError:
        return None
    return (d - datetime.datetime(1970, 1, 1)) // datetime.timedelta(seconds=1)


T94 = 784111777
DATES = [
    ("Sun, 06 Nov 1994 08:49:37 GMT", T94),
    ("Sunday, 06-Nov-94 08:49:37 GMT", T94),
    ("Sun Nov  6 08:49:37 1994", T94),
    ("Thu, 01-Jan-1970 00:00:00 GMT", 0),
    ("Wed, 21 Oct 2015 07:28:00 GMT", 1445412480),
    ("6 nov 1994 8:49:37", T94),
    ("06 NOVEMBER 1994 08:49:37", T94),
    ("1994 Nov 06 08:49:37", T94),
    ("08:49:37 06 Nov 94", T94),
    ("08:49:37 06 Nov 69", 3150953377),
    ("08:49:37 06 Nov 70", 26729377),
    ("06 Nov 00 08:49:37", 973500577),
    ("06 Nov 0 08:49:37", None),
    ("01 Jan 1601 00:00:00", EARLIEST),
    ("31 Dec 9999 23:59:59", LATEST - 1),
    ("Sun, 06 Nov 1994 08:49:37 GMT+05:00", T94),
    ("Sun, 06 Nov 1994 08:49:37.123 GMT", T94),
    ("xx Sun, 06 Nov 1994 08:49:37 GMT yy", T94),
    ("6th Nov 1994 08:49:37", T94),
    ("Nov 06 08:49:37 1994 1995", T94),
    ("08:49:37 1994 Nov 06", T94),
    ("06 1994 Nov 08:49:37", T94),
    ("1 1 Nov 08:49:37", None),
    ("10 11 Nov 08:49:37", 1320914977),
    ("29 Feb 2000 00:00:00", 951782400),
    ("", None),
    ("Sun, 06 Nov 1994", None),
    ("Sun, 31 Feb 1994 08:49:37 GMT", None),
    ("Sun, 00 Nov 1994 08:49:37 GMT", None),
    ("32 Nov 1994 08:49:37", None),
    ("06 Nov 1994 24:00:00", None),
    ("06 Nov 1994 23:60:00", None),
    ("06 Nov 1994 23:59:60", None),
    ("06 Nov 1600 00:00:00", None),
    ("31 Dec 1600 23:59:59", None),
    ("06 Nov 1994 8:49:375", None),
    ("06 Nov 1994 123:49:37", None),
    ("06 Xyz 1994 08:49:37", None),
    ("06 Nov 19945 08:49:37", None),
    ("29 Feb 1900 00:00:00", None),
    ("06 No 1994 08:49:37", None),
    ("06-Nov-1994-08:49:37", T94),
    ("06/Nov/1994/08:49:37", T94),
    ("Nov061994 08:49:37", None),
]


def random_date(rng):
    """A date of a few random tokens, often valid."""
    pool = [
        lambda: str(rng.randint(0, 40)).zfill(rng.choice([1, 2])),
        lambda: rng.choice(MONTHS + ["MAR", "Sept", "december", "ja", "xyz"]) + rng.choice(["", "", "x", "1"]),
        lambda: str(rng.randint(0, 12000)).zfill(rng.choice([1, 2, 3, 4])),
        lambda: ":".join(str(rng.randint(0, 70)).zfill(rng.choice([1, 2])) for _ in range(rng.choice([2, 3, 3, 3, 4]))),
        lambda: rng.choice(["Sun", "Monday", "GMT", "UTC", "+0100", "1st", "2nd", "\xe9t\xe9", "x9"]),
        lambda: str(rng.randint(0, 99)) + rng.choice(["th", ":", "a", "9"]),
    ]
    tokens = [rng.choice(pool)() for _ in range(rng.randint(1, 7))]
    seps = [c for c in DATE_DELIMITERS.decode("latin-1") if c != ";"]
    return "".join(t + "".join(rng.choice(seps) for _ in range(rng.randint(1, 2))) for t in tokens).strip(" \t")


def get_line(name, fields):
    return "g %s %s" % (cx(name), " ".join("%s %s" % (cx(f), cx(v)) for f, v in fields))


GETS = [
    ("b", [("Cookie", "a=1; b=2")], "2"),
    ("a", [("Cookie", "a=1; b=2")], "1"),
    ("c", [("Cookie", "a=1; b=2")], None),
    ("a", [("Cookie", "  a = 1 ;b=2")], "1"),
    ("A", [("Cookie", "a=1")], None),
    ("a", [("Cookie", "b=1"), ("Cookie", "a=2")], "2"),
    ("a", [("Cookie", "a=1; a=2")], "1"),
    ("a", [("Cookie", "a=b=c")], "b=c"),
    ("a", [("Cookie", 'a="x y"')], '"x y"'),
    ("a", [("Cookie", "a=")], ""),
    ("a", [("Cookie", "flag; a=1")], "1"),
    ("flag", [("Cookie", "flag; a=1")], None),
    ("a", [("Set-Cookie", "a=1"), ("X-Cookie", "a=2"), ("cookie", "a=3")], "3"),
    ("", [("Cookie", "=v; a=1")], "v"),
    ("a", [], None),
    ("a", [("Cookie", "")], None),
    ("a", [("Cookie", ";;a=1;;")], "1"),
    ("b", [("Cookie", "a=1;b")], None),
]


def case_cookie(env):
    lines, want = [], []
    for kw, text in FORMAT_ANCHORS:
        compare("Format anchor", cookie_format(**kw), text)
    for kw in FORMAT_OK:
        lines.append(format_line(**kw))
        want.append("ok " + cx(cookie_format(**kw)))
    for kw, w in format_bad():
        lines.append(format_line(**kw))
        want.append(w)
    for value, w in PARSE:
        lines.append("p " + cx(value))
        want.append(w or "no")
    for d, t in DATES:
        compare("date table " + d, cookie_date(d.encode("latin-1")), t)
        lines.append("p " + cx("a=b; Expires=" + d))
        want.append(parsed("a", "b", expires=t))
    rng = random.Random(SEED)
    for _ in range(3000):
        d = random_date(rng)
        lines.append("p " + cx("a=b; Expires=" + d))
        want.append(parsed("a", "b", expires=cookie_date(d.encode("latin-1"))))
    for name, fields, value in GETS:
        lines.append(get_line(name, fields))
        want.append(cx(value))
    got = batch(env, "cookie", lines)
    expect_all("cookie", lines, got, want)

    # the standard library of Python reads what Format makes
    for kw in FORMAT_OK:
        text = cookie_format(**kw).decode("latin-1")
        if b" " in b_(kw.get("path") or b"") or kw["value"] in ('""', b'"' + OCTETS + b'"'):
            continue
        c = http.cookies.SimpleCookie()
        c.load(text)
        name = b_(kw["name"]).decode("latin-1")
        if list(c) != [name]:
            raise Failed("SimpleCookie of %r: %r" % (text, list(c)))
        m = c[name]
        check("SimpleCookie value " + text, m.coded_value, b_(kw["value"]).decode("latin-1"))
        check("SimpleCookie path " + text, m["path"], b_(kw.get("path") or b"").decode("latin-1"))
        check("SimpleCookie domain " + text, m["domain"], b_(kw.get("domain") or b"").decode("latin-1"))
        check("SimpleCookie max-age " + text, m["max-age"], "" if kw.get("maxage", -1) == -1 else str(kw["maxage"]))
        check("SimpleCookie expires " + text, m["expires"],
              "" if kw.get("expires") is None else http_date(kw["expires"]))
        flags = kw.get("flags", "-")
        check("SimpleCookie secure " + text, bool(m["secure"]), "s" in flags)
        check("SimpleCookie httponly " + text, bool(m["httponly"]), "h" in flags)
        check("SimpleCookie samesite " + text, m["samesite"],
              {"S": "Strict", "L": "Lax", "N": "None"}.get(([f for f in flags if f in "SLN"] or [""])[0], ""))

    # clients against the server
    try:
        import requests
    except ImportError:
        raise Failed("the package requests of Python is missing")
    srv = Server(env)
    try:
        url = "http://127.0.0.1:%d/cookies" % srv.port
        c = http.client.HTTPConnection("127.0.0.1", srv.port, timeout=10)
        c.request("GET", "/cookies")
        r = c.getresponse()
        r.read()
        check("Set-Cookie fields", r.msg.get_all("Set-Cookie"),
              ["plain=v1", "full=v2; Max-Age=3600; Path=/; HttpOnly; SameSite=Lax",
               'dated="q"; Expires=Fri, 01 Jan 2100 00:00:00 GMT; Path=/',
               "gone=x; Max-Age=0; Path=/", "old=y; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"])
        c.close()
        none = "plain=-\nfull=-\ndated=-\ngone=-\nold=-\nmissing=-\n"
        stored = "plain=%s\nfull=%s\ndated=%s\ngone=-\nold=-\nmissing=-\n" % (cx("v1"), cx("v2"), cx('"q"'))
        s = requests.Session()
        check("requests first", s.get(url, timeout=10).text, none)
        jar = {k.name: k for k in s.cookies}
        check("requests jar", sorted(jar), ["dated", "full", "plain"])
        check("requests path", (jar["plain"].path, jar["full"].path), ("/", "/"))
        check("requests expires", jar["dated"].expires, 4102444800)
        if not 3500 < jar["full"].expires - time.time() < 3700:
            raise Failed("requests: Max-Age gives %r" % jar["full"].expires)
        check("requests HttpOnly", jar["full"].has_nonstandard_attr("HttpOnly"), True)
        check("requests second", s.get(url, timeout=10).text, stored)

        curl = shutil.which("curl") or r"C:\Windows\System32\curl.exe"
        if Path(curl).exists():
            jarfile = env.work / "cookies.txt"
            for want in (none, stored):
                r = subprocess.run([curl, "-s", "-S", "--max-time", "20", "-b", str(jarfile), "-c", str(jarfile), url],
                                   capture_output=True, timeout=30)
                check("curl", (r.returncode, r.stdout.decode("latin-1").replace("\r\n", "\n")), (0, want))
            rows = {}
            for line in jarfile.read_text().splitlines():
                if line and (not line.startswith("#") or line.startswith("#HttpOnly_")):
                    f = line.split("\t")
                    rows[f[5]] = (f[0].startswith("#HttpOnly_"), f[2], f[4], f[6])
            check("curl jar", sorted(rows), ["dated", "full", "plain"])
            # curl keeps a cookie for 400 days at most, as draft-ietf-httpbis-rfc6265bis asks
            dated = rows["dated"]
            if not (dated[3] == '"q"' and dated[:2] == (False, "/")
                    and (dated[2] == "4102444800" or abs(int(dated[2]) - time.time() - 400 * 86400) < 86400)):
                raise Failed("curl jar dated: %r" % (dated,))
            check("curl jar full", rows["full"][:2], (True, "/"))
        else:
            print("          no curl")
    finally:
        srv.stop()


# multipart

def quote_param(t):
    return b'"' + t.replace(b"\\", b"\\\\") + b'"'


def py_write(boundary, parts):
    """What Multipart.Writer makes of the parts (name, filename, type, body)."""
    out = b""
    for i, (n, f, c, b) in enumerate(parts):
        out += (b"\r\n" if i else b"") + b"--" + boundary + b"\r\nContent-Disposition: form-data; name=" + quote_param(n)
        if f is not None:
            out += b"; filename=" + quote_param(f)
        out += b"\r\n"
        if c is None and f is not None:
            c = b"application/octet-stream"
        if c is not None:
            out += b"Content-Type: " + c + b"\r\n"
        out += b"\r\n" + b
    return out + (b"\r\n" if parts else b"") + b"--" + boundary + b"--\r\n"


def write_line(boundary, parts):
    return "w %s %s" % (cx(boundary), " ".join(",".join(cx(v) for v in p) for p in parts))


def read_line(body, boundary=b"B", source="a", limits="-", mode="a"):
    return "r %s %s %s %s %s" % (source, limits, mode, cx(boundary), cx(body))


def shown(parts, end="end", mode="a"):
    """The answer to "r": parts are (name, filename, type, fields, body)."""
    s = ""
    for n, f, c, k, b in parts:
        body = {"a": cx(b), "k": "?", "h": cx(b[:5] if b is not None else b"")}[mode]
        s += " P %s %s %s %d %s" % (cx(n), cx(f), cx(c), k, body)
    return s + " | " + end


def mp_err(what, kind="BadMessage"):
    return "err HTTP.%s (%s) again:same" % (kind, what)


TRUNCATED = mp_err("multipart body without its last boundary")
CD = b"Content-Disposition: form-data; "


def mp_part(head, body=b"", b=b"B"):
    return b"--" + b + b"\r\n" + head + b"\r\n" + body + b"\r\n"


def mp_body(*parts, b=b"B", pre=b"", epi=b""):
    return pre + b"".join(parts) + b"--" + b + b"--" + epi


ONE = (b"a", None, None, 1, b"x")

# (body, boundary, parts or None, end)
MP_TABLE = [
    (mp_body(), b"B", [], "end"),
    (mp_body(epi=b"\r\nepilogue"), b"B", [], "end"),
    (mp_body(epi=b"garbage without a line end"), b"B", [], "end"),
    (mp_body(mp_part(CD + b'name="a"\r\n', b"x"), pre=b"preamble\r\n"), b"B", [ONE], "end"),
    (mp_body(mp_part(CD + b'name="a"\r\n', b"x"), pre=b"\r\n"), b"B", [ONE], "end"),
    (mp_body(mp_part(CD + b'name="a"\r\n', b"x"), pre=b"--B\r\n"), b"B", None, mp_err("malformed field in the head of a part")),
    (mp_body(mp_part(b"", b"body")), b"B", [(None, None, None, 0, b"body")], "end"),
    (mp_body(mp_part(b"X-A: 1\r\n", b"")), b"B", [(None, None, None, 1, b"")], "end"),
    (b"--B \t \r\n" + CD + b'name="a"\r\n\r\nx\r\n--B\t\r\n\r\ny\r\n--B--', b"B",
     [ONE, (None, None, None, 0, b"y")], "end"),
    (b"--B" + b" " * 1024 + b"\r\n\r\nx\r\n--B--", b"B", [(None, None, None, 0, b"x")], "end"),
    (b"--B" + b" " * 1025 + b"\r\n\r\nx\r\n--B--", b"B", [], mp_err("too many spaces after a boundary")),
    (mp_body(mp_part(CD + b'name="a"\r\n', b"\r\n--b\r\n--BB?\r--B\n\r\n-")), b"B", None, mp_err("garbage after a boundary")),
    (mp_body(mp_part(CD + b'name="a"\r\n', b"\r\n--b\r\n--\r\n--A\r--B\n\r\n-")), b"B",
     [(b"a", None, None, 1, b"\r\n--b\r\n--\r\n--A\r--B\n\r\n-")], "end"),
    (mp_body(mp_part(CD + b'name="a"\r\n', b"x--B")), b"B", [(b"a", None, None, 1, b"x--B")], "end"),
    (b"--B\r\n" + CD + b'name="a"\r\n\r\n--B--', b"B", None, mp_err("boundary right after the head of a part")),
    (b"--B\r\n\r\n--B\r\n\r\nx\r\n--B--", b"B", None, mp_err("boundary right after the head of a part")),
    (b"--B\r\n\r\n--Bx\r\n--B--", b"B", None, mp_err("boundary right after the head of a part")),
    (b"--B\r\n\r\n--x\r\n--B--", b"B", [(None, None, None, 0, b"--x")], "end"),
    (b"--Bx\r\n\r\nx\r\n--B--", b"B", [], mp_err("garbage after a boundary")),
    (b"--B\n\r\nx\r\n--B--", b"B", [], mp_err("garbage after a boundary")),
    (b"--B\rX\r\n\r\nx\r\n--B--", b"B", [], mp_err("garbage after a boundary")),
    (b"--B \r\r\n\r\nx\r\n--B--", b"B", [], mp_err("garbage after a boundary")),
    (b"--B-\r\n\r\nx\r\n--B--", b"B", [], mp_err("garbage after a boundary")),
    (b"--B\r\n\r\nx\r\n--B-x", b"B", None, mp_err("garbage after a boundary")),
    (b"--B\nA: b\n\nx\n--B--\n", b"B", [], mp_err("garbage after a boundary")),
    (b"", b"B", [], TRUNCATED),
    (b"no boundary at all", b"B", [], TRUNCATED),
    (b"--B", b"B", [], TRUNCATED),
    (b"--B-", b"B", [], TRUNCATED),
    (b"--B\r", b"B", [], TRUNCATED),
    (b"--B  ", b"B", [], TRUNCATED),
    (b"--B\r\nA: b", b"B", [], TRUNCATED),
    (b"--B\r\nA: b\r\n", b"B", [], TRUNCATED),
    (b"--B\r\n\r\nx", b"B", None, TRUNCATED),
    (b"--B\r\n\r\nx\r\n--B", b"B", None, TRUNCATED),
    (b"--B\r\n\r\nx\r\n--B\r\n", b"B", None, TRUNCATED),
    (mp_body(mp_part(b"A b: c\r\n")), b"B", [], mp_err("malformed field in the head of a part")),
    (mp_body(mp_part(b"A : c\r\n")), b"B", [], mp_err("malformed field in the head of a part")),
    (mp_body(mp_part(b": c\r\n")), b"B", [], mp_err("malformed field in the head of a part")),
    (mp_body(mp_part(b"Abc\r\n")), b"B", [], mp_err("malformed field in the head of a part")),
    (mp_body(mp_part(b"A: b\x00c\r\n")), b"B", [], mp_err("malformed field in the head of a part")),
    (mp_body(mp_part(b"A: b\nC: d\r\n")), b"B", [], mp_err("malformed field in the head of a part")),
    (mp_body(mp_part(b"A: b\rC: d\r\n")), b"B", [], mp_err("malformed field in the head of a part")),
    (mp_body(mp_part(b"A: b\r\n c\r\n")), b"B", [], mp_err("folded field in the head of a part")),
    (mp_body(mp_part(b"A: b\r\n\tc\r\n")), b"B", [], mp_err("folded field in the head of a part")),
    (mp_body(mp_part(b"A:b\r\nC:  d \t\r\nE:\r\n")), b"B", [(None, None, None, 3, b"")], "end"),
    (mp_body(mp_part(b"A: \xe9\xff\r\n")), b"B", [(None, None, None, 1, b"")], "end"),
    (mp_body(mp_part(CD + b'name="a"\r\nContent-Disposition: form-data; name="b"\r\n')), b"B", [],
     mp_err("two Content-Disposition fields in a part")),
    (mp_body(mp_part(b"Content-Type: a/b\r\ncontent-type: a/b\r\n")), b"B", [],
     mp_err("two Content-Type fields in a part")),
    (mp_body(mp_part(CD + b'name="a"; NAME="b"\r\n')), b"B", [], mp_err("two names of a part")),
    (mp_body(mp_part(CD + b'filename="a"; filename="b"\r\n')), b"B", [], mp_err("two file names of a part")),
    (mp_body(mp_part(CD + b"name\r\n")), b"B", [], mp_err("malformed Content-Disposition of a part")),
    (mp_body(mp_part(CD + b'name="a\r\n')), b"B", [], mp_err("malformed Content-Disposition of a part")),
    (mp_body(mp_part(CD + b"name=a b\r\n")), b"B", [], mp_err("malformed Content-Disposition of a part")),
    (mp_body(mp_part(CD + b"name =a\r\n")), b"B", [], mp_err("malformed Content-Disposition of a part")),
    (mp_body(mp_part(CD + b"name= a\r\n")), b"B", [], mp_err("malformed Content-Disposition of a part")),
    (mp_body(mp_part(CD + b"name=\r\n")), b"B", [], mp_err("malformed Content-Disposition of a part")),
    (mp_body(mp_part(b"Content-Disposition: form-data name=a\r\n")), b"B", [],
     mp_err("malformed Content-Disposition of a part")),
    (mp_body(mp_part(b"Content-Disposition: ; name=a\r\n")), b"B", [],
     mp_err("malformed Content-Disposition of a part")),
    (mp_body(mp_part(b"Content-Disposition: \r\n")), b"B", [], mp_err("malformed Content-Disposition of a part")),
    (mp_body(mp_part(CD + b'name="a"; =b\r\n')), b"B", [], mp_err("malformed Content-Disposition of a part")),
    (mp_body(mp_part(CD + b'name="a""b"\r\n')), b"B", [], mp_err("malformed Content-Disposition of a part")),
    (mp_body(mp_part(CD + b'name="a"x\r\n')), b"B", [], mp_err("malformed Content-Disposition of a part")),
    (mp_body(mp_part(CD + b'name=a"b\r\n')), b"B", [], mp_err("malformed Content-Disposition of a part")),
    (mp_body(mp_part(CD + b'name="a\\"\r\n')), b"B", [], mp_err("malformed Content-Disposition of a part")),
    (mp_body(mp_part(b'Content-Disposition: form-data;name=a;filename=b.txt;\r\n')), b"B",
     [(b"a", b"b.txt", None, 1, b"")], "end"),
    (mp_body(mp_part(b'content-disposition: attachment ; NAME="a" ; FileName="C:\\dir\\x.txt"\r\n')), b"B",
     [(b"a", b"C:\\dir\\x.txt", None, 1, b"")], "end"),
    (mp_body(mp_part(CD + b'name="a\\"b"; filename="c\\\\d"\r\n')), b"B", [(b'a"b', b"c\\d", None, 1, b"")], "end"),
    (mp_body(mp_part(CD + b'name="a\\\\\\x"\r\n')), b"B", [(b"a\\\\x", None, None, 1, b"")], "end"),
    (mp_body(mp_part(CD + b'name=""; filename=""\r\n')), b"B", [(b"", b"", None, 1, b"")], "end"),
    (mp_body(mp_part(CD + b'name="a;b=c"\r\n')), b"B", [(b"a;b=c", None, None, 1, b"")], "end"),
    (mp_body(mp_part(CD + b"name=a/b:c\xe9\r\n")), b"B", [(b"a/b:c\xe9", None, None, 1, b"")], "end"),
    (mp_body(mp_part(CD + b"name=\"a\"; filename*=UTF-8''%e2%82%ac.txt\r\n")), b"B", [(b"a", None, None, 1, b"")],
     "end"),
    (mp_body(mp_part(CD + b'name="n\xc3\xa4me"; filename="f\xc3\xa4.txt"\r\nContent-Type:  text/plain \r\n', b"t")),
     b"B", [(b"n\xc3\xa4me", b"f\xc3\xa4.txt", b"text/plain", 2, b"t")], "end"),
    (mp_body(mp_part(CD + b'name="a"\r\n', b"x", b=b"a'()+_,-./:=? b"), b=b"a'()+_,-./:=? b"), b"a'()+_,-./:=? b",
     [(b"a", None, None, 1, b"x")], "end"),
    (mp_body(mp_part(CD + b'name="a"\r\n', b"x", b=b"B" * 70), b=b"B" * 70), b"B" * 70, [(b"a", None, None, 1, b"x")], "end"),
    (mp_body(), b"B" * 71, [], mp_err("missing or invalid boundary of a multipart body")),
    (mp_body(), b"B ", [], mp_err("missing or invalid boundary of a multipart body")),
    (mp_body(), b"", [], mp_err("missing or invalid boundary of a multipart body")),
    (mp_body(), None, [], mp_err("missing or invalid boundary of a multipart body")),
    (mp_body(mp_part(CD + b'name="a"\r\n', b"x", b=b"b"), b=b"b"), b"B", [], TRUNCATED),
]

BCHARS = b"0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz'()+_,-./:=? "

BOUNDARIES = [
    ("multipart/form-data; boundary=abc", b"abc"),
    ('MULTIPART/Mixed;BOUNDARY="a b"', b"a b"),
    ('multipart/form-data; boundary="' + "x" * 70 + '"', b"x" * 70),
    ('multipart/form-data; boundary="' + "x" * 71 + '"', None),
    ('multipart/form-data; boundary="ab "', None),
    ('multipart/form-data; boundary=""', None),
    ("multipart/form-data", None),
    ("multipart/form-data;", None),
    ("text/plain; boundary=a", None),
    ("multipart/form-data; boundary=a; boundary=b", None),
    ("multipart/form-data; boundary=a; Boundary=a", None),
    ("multipart/form-data; charset=utf-8; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW",
     b"----WebKitFormBoundary7MA4YWxkTrZu0gW"),
    ("multipart/form-data; boundary=a=b", b"a=b"),
    ("multipart/form-data; boundary = a", None),
    ("multipart/form-data; boundary= a", None),
    ("multipart/form-data;boundary=a", b"a"),
    (" multipart/form-data ; boundary=a ", b"a"),
    ("multipart/form-data; boundary=a;", b"a"),
    ("multipart/form-data;; boundary=a", b"a"),
    ("multipart/form-data; boundary=a b", None),
    ('multipart/form-data; boundary="a\\"b"', None),
    ('multipart/form-data; boundary="unterminated', None),
    ("multipart/form-data; =a", None),
    ("multipart; boundary=a", None),
    ("multipart/; boundary=a", None),
    ("multipart /form-data; boundary=a", None),
    ("multipart/form-data; boundary=\xe9", None),
    ("multipart/form-data; boundary=\u20ac", None),
    ("multipart/form-data; boundary=a\x00", None),
    (None, None),
]


def limits_body():
    """A body of three parts, and the values of its limits."""
    head1 = CD + b'name="a"\r\nContent-Type: text/plain\r\nX-A: 1\r\n'
    body = mp_body(mp_part(head1, b"0123456789"), mp_part(CD + b'name="b"\r\n', b"abc"), mp_part(b"", b"xy"),
                   pre=b"pre\r\n", epi=b"\r\nepilogue")
    parts = [(b"a", None, b"text/plain", 3, b"0123456789"), (b"b", None, None, 1, b"abc"), (None, None, None, 0, b"xy")]
    exact = dict(parts=3, header=len(head1) + 2, headers=3, preamble=5, part=10, total=body.index(b"--B--") + 5)
    return body, parts, exact


def limits_text(parts=1000, header=16384, headers=32, preamble=65536, part=0, total=0):
    return "%d,%d,%d,%d,%d,%d" % (parts, header, headers, preamble, part, total)


SPLIT_BOUNDARY = b"XyZ"


def split_body():
    """A body whose content comes close to the boundary in many ways."""
    b = SPLIT_BOUNDARY
    c1 = b"\r\n--Xy\r\r\n-\r\n--\r\n--X"
    c2 = b"x--XyZ\r\n\r--xyz\r\n--XYZ"
    c3 = b"\r\r\r\n\r\n--Xy"
    body = (b"pre\r\n--XyZ\r\n" + CD + b'name="a"\r\n\r\n' + c1 + b"\r\n--XyZ \t\r\n" + CD
            + b'name="b"; filename="f.txt"\r\nContent-Type: text/plain\r\n\r\n' + c2 + b"\r\n--XyZ\r\n\r\n" + c3
            + b"\r\n--XyZ--\r\nepi")
    parts = [(b"a", None, None, 1, c1), (b"b", b"f.txt", b"text/plain", 2, c2), (None, None, None, 0, c3)]
    return body, b, parts


def random_name(rng, filename=False):
    """Bytes that Multipart.Writer accepts as a name, without backslashes
    and slashes, which the parsers of Python treat in their own ways."""
    chars = [c for c in range(0x20, 0x100) if c not in (0x22, 0x5C, 0x2F, 0x7F)]
    n = rng.choice([0, 1, 5, 20]) if not filename else rng.choice([1, 8])
    return bytes(rng.choice(chars) for _ in range(n))


def random_content(rng, boundary):
    """Bytes with pieces of the delimiter, but never all of it."""
    pieces = [b"\r", b"\n", b"\r\n", b"-", b"--", b"\r\n--", b"\r\n--" + boundary[:-1], b"--" + boundary, b"x", b" "]
    while True:
        n = rng.choice([0, 1, 2, 10, 100, 1000, 6000])
        out = b"".join(rng.choice(pieces) if rng.random() < 0.3 else rng.randbytes(rng.randint(1, 30))
                       for _ in range(n // 10 + 1))[:n]
        if b"\r\n--" + boundary not in out and not out.startswith(b"--" + boundary):
            return out


def random_form(rng, boundary):
    parts = []
    for _ in range(rng.randint(0, 5)):
        filename = random_name(rng, True) if rng.random() < 0.4 else None
        ctype = rng.choice([None, b"text/plain", b"application/x-m3; a=1"]) if rng.random() < 0.6 else None
        parts.append((random_name(rng), filename, ctype, random_content(rng, boundary)))
    return parts


def python_parts(body, boundary):
    """The parts of a body by python-multipart: (headers, data)."""
    import python_multipart.multipart as pm
    parts = []

    def begin():
        parts.append([[], b""])

    def field(b, s, e):
        parts[-1][0].append([b[s:e], b""])

    def value(b, s, e):
        parts[-1][0][-1][1] += b[s:e]

    def data(b, s, e):
        parts[-1][1] += b[s:e]

    callbacks = {"on_part_begin": begin, "on_header_field": field, "on_header_value": value, "on_part_data": data}
    try:
        p = pm.MultipartParser(boundary, callbacks, max_header_count=100)
    except TypeError:
        # older versions (0.0.20 of Ubuntu) have no limit of headers, nor this parameter
        p = pm.MultipartParser(boundary, callbacks)
    p.write(body)
    p.finalize()
    return [(dict((k.lower(), v) for k, v in h), d) for h, d in parts]


def check_written(what, boundary, parts, body):
    """Compares a body that Multipart.Writer made with the parts, by two
    parsers of Python."""
    from requests_toolbelt.multipart import decoder
    import python_multipart.multipart as pm
    got = python_parts(body, boundary)
    check(what + ": parts by python-multipart", len(got), len(parts))
    for (h, d), (n, f, c, b) in zip(got, parts):
        _, params = pm.parse_options_header(h[b"content-disposition"])
        check(what + ": name", params[b"name"], n)
        check(what + ": filename", params.get(b"filename"), f)
        check(what + ": type", h.get(b"content-type"), c if c is not None or f is None else b"application/octet-stream")
        check(what + ": data", d, b)
    # requests_toolbelt splits at each "--" and boundary, even within a body
    if parts and not any(b"--" + boundary in p[3] for p in parts):
        dec = decoder.MultipartDecoder(body, "multipart/form-data; boundary=" + boundary.decode("latin-1"),
                                       encoding="latin-1")
        check(what + ": parts by requests_toolbelt", [p.content for p in dec.parts], [p[3] for p in parts])


def case_multipart(env):
    try:
        import python_multipart  # noqa: F401
        import requests
        import requests_toolbelt  # noqa: F401
        import urllib3
    except ImportError as e:
        raise Failed("a package of Python is missing: %s" % e)
    from requests_toolbelt.multipart import encoder
    rng = random.Random(SEED)
    lines, want = [], []

    def add(line, w):
        lines.append(line)
        want.append(w)

    # Boundary and ValidBoundary
    for ct, b in BOUNDARIES:
        add("b " + cx(ct), cx(b))
    for c in range(256):
        add("v " + cx(bytes([c])), "1" if c in BCHARS and c != 0x20 else "0")
    for b, ok in [(b"x" * 70, 1), (b"x" * 71, 0), (b"", 0), (b"a b", 1), (b" a", 1), (b"a ", 0), (None, 0),
                  ("a\u20ac", 0)]:
        add("v " + cx(b), str(ok))

    # the table, read at once, byte by byte and in pieces of 1 to 17
    for body, boundary, parts, end in MP_TABLE:
        for source in ("a", "c1", "c0"):
            add(read_line(body, boundary, source), (shown(parts, end) if parts is not None else None, end))

    # limits, exact and one below
    body, parts, exact = limits_body()
    names = {"parts": ("too many parts in a multipart body", "TooLarge"),
             "header": ("head of a part too large", "TooLarge"),
             "headers": ("too many fields in the head of a part", "TooLarge"),
             "preamble": ("preamble of a multipart body too long", "TooLarge"),
             "part": ("part of a multipart body too large", "TooLarge"),
             "total": ("multipart body too large", "TooLarge")}
    for key, value in exact.items():
        for source in ("a", "c1", "c0"):
            for mode in ("a", "k"):
                add(read_line(body, b"B", source, limits_text(**{key: value}), mode), (shown(parts, mode=mode), None))
                add(read_line(body, b"B", source, limits_text(**{key: value - 1}), mode),
                    (None, mp_err(*names[key])))
    add(read_line(body, b"B", "a", limits_text(parts=0)), (shown([], mp_err(*names["parts"])), None))

    # every split around the boundaries and every truncation
    body, b, parts = split_body()
    full = shown(parts)
    end = body.index(b"--XyZ--") + 7
    for size in range(0, 81):
        add(read_line(body, b, "c%d" % size), (full, None))
    for s in range(1, len(body)):
        add(read_line(body, b, "s%d" % s), (full, None))
    for mode in ("k", "h"):
        add(read_line(body, b, "c3", mode=mode), (shown(parts, mode=mode), None))
    stale = " stale:Multipart.Stale"
    add(read_line(body, b, "c5", mode="z"),
        ("".join(" P %s %s %s %d ?" % (cx(n), cx(f), cx(c), k) + stale for n, f, c, k, _ in parts) + " | end", None))
    add(read_line(body, b, "c5", mode="e"),
        ("".join(" P %s %s %s %d %s stale:eof" % (cx(n), cx(f), cx(c), k, cx(d)) for n, f, c, k, d in parts)
         + " | end", None))
    for n in range(len(body) + 1):
        add(read_line(body[:n], b, "c0"), (full, None) if n >= end else (None, TRUNCATED))
        add(read_line(body[:n], b, "f"),
            (full, None) if n >= end else (None, "err Test.Broken again:same"))

    # forms of other writers
    for i in range(60):
        boundary = rng.choice([b"BB", b"----WebKitFormBoundary" + rng.randbytes(8).hex().encode(), rng.randbytes(16).hex().encode()])
        fields = []
        for k in range(rng.randint(1, 5)):
            name = "field%d-\u00e4\u20ac" % k
            if rng.random() < 0.5:
                fields.append((name, rng.choice(["", "value", "a\r\nb", "\u00e4\u00f6\u00fc"])))
            else:
                fields.append((name, ("file %d.txt" % k, random_content(rng, boundary), rng.choice(["text/plain", "application/octet-stream"]))))
        if i % 2:
            data, ct = urllib3.encode_multipart_formdata(fields, boundary=boundary.decode())
        else:
            e = encoder.MultipartEncoder(fields=fields, boundary=boundary.decode())
            data, ct = e.to_string(), e.content_type
        expected = []
        for name, v in fields:
            if isinstance(v, tuple):
                expected.append((name.encode(), v[0].encode(), v[2].encode(), 2, v[1]))
            else:
                expected.append((name.encode(), None, None, 1, v.encode()))
        add("b " + cx(ct), cx(boundary))
        for source in ("a", "c0", "c%d" % rng.randint(1, 100)):
            add(read_line(data, boundary, source), (shown(expected), None))

    # the writer: a fixed example, random forms and invalid parts
    golden = (b'--B\r\nContent-Disposition: form-data; name="title"\r\n\r\nHoliday\r\n--B\r\nContent-Disposition: '
              b'form-data; name="upload"; filename="a\\\\b.jpg"\r\nContent-Type: image/jpeg\r\n\r\nJPEG\r\n'
              b'--B\r\nContent-Disposition: form-data; name="f"; filename=""\r\nContent-Type: '
              b'application/octet-stream\r\n\r\n\r\n--B--\r\n')
    gparts = [(b"title", None, None, b"Holiday"), (b"upload", b"a\\b.jpg", b"image/jpeg", b"JPEG"), (b"f", b"", None, b"")]
    compare("writer golden", py_write(b"B", gparts), golden)
    written = [(len(lines), b"B", gparts)]
    add(write_line(b"B", gparts), "ok %s %s" % (cx("multipart/form-data; boundary=B"), cx(golden)))
    add(write_line(b"B", []), "ok %s %s" % (cx("multipart/form-data; boundary=B"), cx(b"--B--\r\n")))
    add(write_line(b"a b", gparts[:1]), "ok %s %s" % (cx('multipart/form-data; boundary="a b"'),
                                                     cx(py_write(b"a b", gparts[:1]))))
    for _ in range(40):
        boundary = rng.choice([b"BB", b"a'()+_,-./:=? b", rng.randbytes(12).hex().encode()])
        parts = random_form(rng, boundary)
        written.append((len(lines), boundary, parts))
        ct =b"multipart/form-data; boundary=" + (boundary if re.fullmatch(rb"[!#$%&'*+.^_`|~0-9A-Za-z-]+", boundary)
                                                   else b'"' + boundary + b'"')
        add(write_line(boundary, parts), "ok %s %s" % (cx(ct), cx(py_write(boundary, parts))))
        add(read_line(py_write(boundary, parts), boundary, "c0"),
            (shown([(n, f, c if c is not None or f is None else b"application/octet-stream",
                     1 + (c is not None or f is not None), b) for n, f, c, b in parts]), None))
    good = (b"g", None, None, b"1")
    for bad, what in [((b'a"b', None, None, b""), "invalid name of a part"),
                      ((None, None, None, b""), "invalid name of a part"),
                      (("a\u20ac", None, None, b""), "invalid name of a part"),
                      ((b"a", b'"', None, b""), "invalid file name of a part"),
                      ((b"a", "\u20ac", None, b""), "invalid file name of a part"),
                      ((b"a", None, b"", b""), "invalid content type of a part"),
                      ((b"a", None, b"a\r\nb", b""), "invalid content type of a part")] + \
                     [((b"a" + bytes([c]), None, None, b""), "invalid name of a part") for c in [*range(32), 127]] + \
                     [((b"a", b"f" + bytes([c]), None, b""), "invalid file name of a part") for c in [*range(32), 127]]:
        prefix = py_write(b"B", [good])[:-len(b"\r\n--B--\r\n")]
        add(write_line(b"B", [good, bad]), "err %s HTTP.BadMessage (%s)" % (cx(prefix), what))
    randoms = len(lines)
    add(write_line(None, [good]), None)
    add(write_line(None, [good]), None)

    got = batch(env, "multipart", lines)
    for i, (l, g, w) in enumerate(zip(lines, got, want)):
        if isinstance(w, tuple):
            whole, tail = w
            ok = g == whole if whole is not None else g.endswith(" | " + tail)
            if not ok:
                raise Failed("multipart: line %d\n  line %s\n  got  %s\n  want %s" % (i, l[:300], g[:500], (whole or tail)[:500]))
        elif w is not None and g != w:
            raise Failed("multipart: line %d\n  line %s\n  got  %s\n  want %s" % (i, l[:300], g[:500], w[:500]))

    # the forms of the writer, by the parsers of Python
    for i, boundary, parts in written:
        f = got[i].split(" ")
        check_written("writer %d" % i, boundary, parts, bytes.fromhex(f[2][1:]))
    r1, r2 = got[randoms].split(" "), got[randoms + 1].split(" ")
    for r in (r1, r2):
        ct = bytes.fromhex(r[1][1:])
        if not re.fullmatch(rb"multipart/form-data; boundary=----[0-9a-f]{32}", ct):
            raise Failed("random boundary: %r" % ct)
        boundary = ct.split(b"=", 1)[1]
        check_written("random boundary", boundary, [good], bytes.fromhex(r[2][1:]))
    if r1[1] == r2[1]:
        raise Failed("the same random boundary twice")

    # clients against the server
    srv = Server(env)
    try:
        url = "http://127.0.0.1:%d/form" % srv.port
        upload = rng.randbytes(200000)

        def form_lines(fields):
            return "".join("%s %s %s %s\n" % tuple(cx(v) for v in f) for f in fields)

        expected = form_lines([(b"title", None, None, b"Holiday"), (b"upload", b"beach.jpg", b"image/jpeg", upload)])
        r = requests.post(url, data={"title": "Holiday"}, files={"upload": ("beach.jpg", upload, "image/jpeg")}, timeout=20)
        check("requests form", (r.status_code, r.text), (200, expected))
        curl = shutil.which("curl") or r"C:\Windows\System32\curl.exe"
        if Path(curl).exists():
            (env.work / "upload.bin").write_bytes(upload)
            r = subprocess.run([curl, "-s", "-S", "--max-time", "20", "-F", "title=Holiday",
                                "-F", "upload=@upload.bin;type=image/jpeg;filename=beach.jpg", url],
                               capture_output=True, timeout=30, cwd=env.work)
            check("curl form", (r.returncode, r.stdout.decode("latin-1").replace("\r\n", "\n")), (0, expected))
        else:
            print("          no curl")
        for path, ct, body, status in [
                ("/form", "multipart/form-data; boundary=B", b"--B\r\n\r\nx", 400),
                ("/form", "text/plain", b"--B\r\n\r\nx\r\n--B--", 400),
                ("/form", "multipart/form-data", b"--B\r\n\r\nx\r\n--B--", 400),
                ("/form?maxpart=10", "multipart/form-data; boundary=B", b"--B\r\n\r\n01234567890\r\n--B--", 413),
                ("/form?maxpart=10", "multipart/form-data; boundary=B", b"--B\r\n\r\n0123456789\r\n--B--", 200)]:
            c = http.client.HTTPConnection("127.0.0.1", srv.port, timeout=10)
            c.request("POST", path, body=body, headers={"Content-Type": ct})
            r = c.getresponse()
            r.read()
            check("status of %r %r" % (ct, body), r.status, status)
            c.close()
    finally:
        srv.stop()


def speed(env):
    """The rates on this machine, for information."""
    srv = Server(env)
    try:
        p = Peer(srv.port)
        for seconds in (3, 3):
            n = 0
            t0 = time.time()
            while time.time() - t0 < seconds:
                p.send(get(b"/hello")).response()
                n += 1
            print("          keep-alive: %.0f requests per second" % (n / (time.time() - t0)), flush=True)
        for _ in range(3):
            size = 64 * 1024 * 1024
            t0 = time.time()
            p.send(get(b"/len?n=%d&claim=%d" % (size, size)))
            while p.line():
                pass
            got = len(p.buf)
            p.buf = b""
            while got < size:
                got += len(p.s.recv(1 << 20))
            check("size", got, size)
            print("          download: %.0f MB/s" % (got / 1048576 / (time.time() - t0)), flush=True)
    finally:
        srv.stop()


CASES = {"codec": case_codec, "headers": case_headers, "table": case_table, "behavior": case_behavior,
         "clients": case_clients, "tls": case_tls, "client": case_client,
         "together": case_together, "gzip": case_gzip, "gzclient": case_gzclient, "cookie": case_cookie,
         "multipart": case_multipart}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cases", nargs="*")
    jdk = os.environ.get("JDK25")
    ap.add_argument("--java", default=str(Path(jdk) / "bin" / "java") if jdk else "java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--timeout", type=float, default=120)
    ap.add_argument("--speed", action="store_true")
    args = ap.parse_args()
    if sys.platform == "win32" and not args.java.endswith(".exe") and Path(args.java + ".exe").exists():
        args.java += ".exe"

    names = args.cases or list(CASES)
    for n in names:
        if n not in CASES:
            ap.error("unknown case " + n)
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="m3j_http_"))
    work.mkdir(parents=True, exist_ok=True)
    env = Env(args, work)
    failed = 0
    start = time.time()
    try:
        env.compile()
        for name in names:
            try:
                CASES[name](env)
                print("PASS      " + name, flush=True)
            except (Failed, tls_test.Failed, subprocess.TimeoutExpired, OSError, EOFError) as e:
                failed += 1
                print("FAIL      %s: %s" % (name, e), flush=True)
        if args.speed:
            speed(env)
    except Failed as e:
        failed += 1
        print("FAIL      %s" % e, flush=True)
    finally:
        if not args.keep and not args.work:
            shutil.rmtree(work, ignore_errors=True)
    print("failed %d, %.0fs" % (failed, time.time() - start))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
