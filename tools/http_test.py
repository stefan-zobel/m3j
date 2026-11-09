#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Tests of the library http of m3j. They need no connection to the internet.

  python3 tools/http_test.py [CASE]... [--java JAVA] [--jar JAR] [--work DIR]
                             [--keep] [--timeout SECONDS] [--speed]

The cases:

  codec    escapes, dates and basic authentication against the standard
           library of Python, with fixed and with random values
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

Without arguments all cases run. --speed also measures the rates of a server
on this machine, for information. JAVA defaults to $JDK25/bin/java, else java
from the PATH; JAR to m3j.jar in the root of m3j.
"""

import argparse
import base64
import calendar
import email.utils
import hashlib
import http
import http.client
import http.server
import os
import random
import shutil
import socket
import ssl
import subprocess
import sys
import tempfile
import threading
import time
import urllib.parse
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

    def handle_any(self):
        path = self.path.replace("http://proxied.invalid", "")
        if path.startswith("/text"):
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

    do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = handle_any


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
         "together": case_together}


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
