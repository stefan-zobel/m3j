#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Tests of the feed reader in examples/feedreader, built by m3build in a
copy. They need no connection to the internet: the feeds come from a
server of the test on 127.0.0.1.

  python3 tools/feedreader_test.py [CASE]... [--java JAVA] [--work DIR] [--keep]
                                   [--timeout SECONDS] [--update]

The cases:

  parse    the feeds of tests/feedreader/feeds, through "feedreader fetch FILE
           -html", against tests/feedreader/expected/parse-NAME.txt; the
           message of the XML parser of the JDK, which is in the language of
           the system, is left out
  fetch    "sync" of feeds that answer with ETag and 304, Last-Modified and
           304, a redirect, gzip, 404 and 500, a body over the limit plain
           and in gzip, a server too slow, broken XML, and a feed that grows
           and changes; what the database holds afterwards, read with the
           sqlite3 of Python, and the times of the next attempts; "add" of a
           web page that names a feed
  web      the web interface: the first user, login and logout, the pages,
           marking, search, the frame of an entry and its policy, the
           setting of images and the link of an entry, a list in OPML up and
           down, forms without the token or from another site,
           a second user who sees nothing of the first
  api      the JSON API with and without a token
  events   a page that waits for news hears of the entries of a new feed
  migrate  a database of version 1 brought up to version 2; one of a newer
           version refused
  threads  60 feeds fetched by 8 threads: each fetched once, each entry kept
           once
  stop     the log of "serve": the database and its users, a login refused
           for a wrong password and for no such user, a session cookie
           refused that names no session and one that has expired; Ctrl+C
           ends "serve" in order (exit 0, the database closed and its WAL
           file gone), and a second Ctrl+C while a page waits for news ends
           it at once (exit 130); on Windows Ctrl+C is sent to a console of
           the program's own

The program needs the driver of SQLite: examples/feedreader/lib, where
fetch-driver.sh puts it, or the local repository of Maven.

--update writes the expected files of the cases that compare with them,
which then must be checked by hand.

JAVA defaults to $JDK25/bin/java, else java from the PATH.
"""

import argparse
import collections
import gzip
import hashlib
import http.client
import http.cookiejar
import http.server
import json
import os
import re
import shutil
import signal
import socket
import sqlite3
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
EXAMPLE = ROOT / "examples" / "feedreader"
TESTS = ROOT / "tests" / "feedreader"
M3BUILD = ROOT / "m3build.jar"
BASE = "https://example.org/feeds/"
DRIVER = "sqlite-jdbc-3.53.4.0.jar"
MAVEN = Path.home() / ".m2" / "repository" / "org" / "xerial" / "sqlite-jdbc" / "3.53.4.0" / DRIVER
PASSWORD = "the-password-1"
LIMIT = 32 * 1024 * 1024


class Failed(Exception):
    pass


def check(cond, what):
    if not cond:
        raise Failed(what)


# ------------------------------------------------------------------ the feeds

def rss(title, items):
    """An RSS 2.0 feed; items are (guid, title, day of July 2003)."""
    parts = ['<?xml version="1.0" encoding="UTF-8"?>\n<rss version="2.0"><channel><title>%s</title>'
             '<link>http://example.org/</link><description>a test</description>' % title]
    for guid, t, day in items:
        parts.append('<item><title>%s</title><link>http://example.org/%s</link><guid isPermaLink="false">%s</guid>'
                     '<pubDate>%s, %02d Jul 2003 10:00:00 +0000</pubDate><description>Text of %s.</description>'
                     '</item>' % (t, guid, guid, "Tue", day, t))
    parts.append("</channel></rss>\n")
    return "".join(parts).encode("utf-8")


def atom(title, n):
    parts = ['<?xml version="1.0" encoding="utf-8"?>\n<feed xmlns="http://www.w3.org/2005/Atom"><title>%s</title>'
             % title]
    for i in range(n):
        parts.append('<entry><id>urn:a:%d</id><title>Atom %d</title><link href="http://example.org/a/%d"/>'
                     '<updated>2003-07-%02dT10:00:00Z</updated><content type="html">&lt;p&gt;Body %d&lt;/p&gt;'
                     '</content></entry>' % (i, i, i, i + 1, i))
    parts.append("</feed>\n")
    return "".join(parts).encode("utf-8")


class Feeds:
    """What the server of the test serves, and what it saw."""

    def __init__(self):
        self.lock = threading.Lock()
        self.hits = collections.Counter()
        self.grow = 0
        self.gzip_asked = False
        self.agents = set()

    def serve(self, h):
        path = urllib.parse.urlsplit(h.path).path
        with self.lock:
            self.hits[path] += 1
            self.agents.add(h.headers.get("User-Agent"))
        if path == "/good.xml":
            if h.headers.get("If-None-Match") == '"v1"':
                return 304, {"ETag": '"v1"'}, b""
            return 200, {"ETag": '"v1"', "Content-Type": "application/rss+xml"}, \
                rss("Good", [("g1", "One", 1), ("g2", "Two", 2), ("g3", "Three", 3)])
        if path == "/lm.xml":
            lm = "Tue, 01 Jul 2003 10:00:00 GMT"
            if h.headers.get("If-Modified-Since") == lm:
                return 304, {}, b""
            return 200, {"Last-Modified": lm}, rss("LM", [("l1", "Only", 1)])
        if path == "/grow.xml":
            with self.lock:
                self.grow += 1
                v = self.grow
            items = [("w%d" % i, "Grow %d" % i, i) for i in range(1, v + 3)]
            if v > 1:
                items[0] = ("w1", "Grow 1, changed", 1)
            return 200, {}, rss("Grow", items)
        if path == "/redirect.xml":
            return 301, {"Location": "/good.xml"}, b""
        if path == "/gzip.xml":
            body = atom("Gzip", 4)
            if "gzip" in (h.headers.get("Accept-Encoding") or ""):
                with self.lock:
                    self.gzip_asked = True
                return 200, {"Content-Encoding": "gzip"}, gzip.compress(body)
            return 200, {}, body
        if path == "/bomb.xml":
            return 200, {"Content-Encoding": "gzip"}, gzip.compress(b"<rss>" + b" " * (LIMIT + 1000) + b"</rss>")
        if path == "/big.xml":
            return 200, {}, b"<rss>" + b" " * (LIMIT + 1000) + b"</rss>"
        if path == "/missing.xml":
            return 404, {}, b"not here"
        if path == "/error.xml":
            return 500, {}, b"broken"
        if path == "/slow.xml":
            time.sleep(6)
            return 200, {}, rss("Slow", [])
        if path == "/broken.xml":
            return 200, {}, b"<rss><channel><title>Broken</channel></rss>"
        if path == "/page.html":
            return 200, {"Content-Type": "text/html"}, \
                b'<!DOCTYPE html><html><head><title>A site</title>' \
                b'<link rel="stylesheet" href="/s.css"><LINK REL=alternate TYPE="application/atom+xml" ' \
                b'HREF="/gzip.xml"></head><body><p>Hello</p></body></html>'
        m = re.fullmatch(r"/n/(\d+)\.xml", path)
        if m:
            k = int(m.group(1))
            return 200, {}, rss("Feed %d" % k, [("n%d-%d" % (k, i), "Entry %d of %d" % (i, k), i + 1)
                                               for i in range(k % 5 + 1)])
        return 404, {}, b"no such feed"


def start_server(feeds):
    class Handler(http.server.BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def do_GET(self):
            status, headers, body = feeds.serve(self)
            self.send_response(status)
            for k, v in headers.items():
                self.send_header(k, v)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            try:
                self.wfile.write(body)
            except (ConnectionError, OSError):
                pass

        def log_message(self, *a):
            pass

    class Quiet(http.server.ThreadingHTTPServer):
        # room for the connections of many workers at once: Windows refuses
        # a connection when the queue of listen (5 by default) is full
        request_queue_size = 64

        def handle_error(self, request, client_address):
            pass  # a client that gives up a connection

    srv = Quiet(("127.0.0.1", 0), Handler)
    srv.daemon_threads = True
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    return srv, "http://127.0.0.1:%d" % srv.server_address[1]


def opml(urls):
    out = ['<?xml version="1.0"?>\n<opml version="2.0"><head><title>t</title></head><body>']
    for u in urls:
        out.append('<outline text="%s" xmlUrl="%s"/>' % (u.rsplit("/", 1)[-1], u))
    out.append("</body></opml>\n")
    return "".join(out)


# ------------------------------------------------------------------ the program

class Env:
    def __init__(self, args, work):
        self.args = args
        self.work = work
        self.pkg = work / "feedreader"

    def build(self):
        """Copies the example into the work and builds it."""
        if self.pkg.exists():
            shutil.rmtree(self.pkg)
        shutil.copytree(EXAMPLE, self.pkg, ignore=shutil.ignore_patterns("JVM"))
        jar = self.pkg / "lib" / DRIVER
        if not jar.exists():
            if not MAVEN.exists():
                raise Failed("no driver of SQLite: run examples/feedreader/fetch-driver.sh")
            jar.parent.mkdir(exist_ok=True)
            shutil.copy(MAVEN, jar)
        r = subprocess.run([self.args.java, "-jar", str(M3BUILD)], cwd=self.pkg, capture_output=True,
                           timeout=self.args.timeout, stdin=subprocess.DEVNULL)
        out = (r.stdout + r.stderr).decode("latin-1")
        if r.returncode or not (self.pkg / "JVM" / "feedreader.jar").exists() or "warning" in out:
            raise Failed("m3build:\n" + out)

    def command(self):
        prog = self.pkg / "JVM" / "feedreader"
        return ["cmd", "/c", str(prog) + ".cmd"] if os.name == "nt" else ["sh", str(prog)]

    def run(self, *params, cwd=None, stdin=None):
        """Runs the program: (exit code, stdout, stderr), as text."""
        r = subprocess.run(self.command() + list(params), cwd=cwd or self.work, capture_output=True,
                           timeout=self.args.timeout, input=stdin.encode("utf-8") if stdin else None,
                           stdin=None if stdin else subprocess.DEVNULL)
        return (r.returncode, r.stdout.decode("utf-8").replace("\r\n", "\n"),
                r.stderr.decode("utf-8").replace("\r\n", "\n"))

    def ok(self, *params, stdin=None):
        code, out, err = self.run(*params, stdin=stdin)
        if code:
            raise Failed("%s: exit %d: %s%s" % (" ".join(params), code, out, err))
        return out

    def fresh(self, name):
        """A new database with a user "alice"."""
        db = self.work / name
        for suffix in ("", "-wal", "-shm"):
            Path(str(db) + suffix).unlink(missing_ok=True)
        self.ok("-db", str(db), "adduser", "alice", stdin=PASSWORD + "\n")
        return str(db)


class Server:
    """The program serving its web interface on a free port."""

    def __init__(self, env, db):
        with socket.socket() as s:
            s.bind(("127.0.0.1", 0))
            self.port = s.getsockname()[1]
        self.base = "http://127.0.0.1:%d" % self.port
        self.log = open(env.work / ("serve-%d.log" % self.port), "wb")
        self.p = subprocess.Popen(env.command() + ["-db", db, "-timeout", "5", "serve", "-listen",
                                                   "127.0.0.1:%d" % self.port],
                                  cwd=env.work, stdout=self.log, stderr=subprocess.STDOUT,
                                  stdin=subprocess.DEVNULL)
        end = time.time() + 30
        while time.time() < end:
            try:
                socket.create_connection(("127.0.0.1", self.port), timeout=1).close()
                return
            except OSError:
                time.sleep(0.2)
        self.stop()
        raise Failed("the server did not start")

    def stop(self):
        if os.name == "nt":
            subprocess.run(["taskkill", "/T", "/F", "/PID", str(self.p.pid)], capture_output=True)
        else:
            self.p.kill()
        self.p.wait()
        self.log.close()


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a):
        return None


class Browser:
    """A client with cookies that does not follow redirects, and that sends
    the "Origin" of the page with a POST, as browsers do."""

    def __init__(self, base):
        self.base = base
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar), NoRedirect())

    def request(self, path, data=None, headers=None, method=None):
        """(status, headers, body as text)"""
        if isinstance(data, dict):
            data = urllib.parse.urlencode(data).encode("utf-8")
        headers = dict(headers or {})
        if data is not None and "Origin" not in headers:
            headers["Origin"] = self.base
        req = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            with self.opener.open(req, timeout=60) as r:
                return r.status, r.headers, r.read().decode("utf-8")
        except urllib.error.HTTPError as e:
            return e.code, e.headers, e.read().decode("utf-8", "replace")

    def get(self, path):
        return self.request(path)

    def post(self, path, fields, headers=None):
        return self.request(path, fields, headers)

    def csrf(self, page):
        m = re.search(r'name="csrf" value="([^"]*)"', page)
        check(m, "no token in the page")
        return m.group(1)


def expect(env, name, got):
    path = TESTS / "expected" / (name + ".txt")
    if env.args.update:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(got, encoding="utf-8", newline="\n")
        return
    if not path.exists():
        raise Failed("%s: no expected output (--update writes it)" % path.name)
    want = path.read_text(encoding="utf-8")
    if got != want:
        g, w = got.split("\n"), want.split("\n")
        for i in range(max(len(g), len(w))):
            a = g[i] if i < len(g) else "<end>"
            b = w[i] if i < len(w) else "<end>"
            if a != b:
                raise Failed("%s, line %d:\n  got  %r\n  want %r" % (path.name, i + 1, a[:300], b[:300]))


# ------------------------------------------------------------------ the cases

def case_parse(env):
    feeds = TESTS / "feeds"
    for f in sorted(feeds.glob("*.xml")):
        code, out, err = env.run("fetch", f.name, "-base", BASE + f.name, "-html", cwd=feeds)
        err = re.sub(r"(?m)^(feedreader: [^:\n]+: \d+:\d+): .*$", r"\1: <message of the parser>", err)
        expect(env, "parse-" + f.stem, out + err + "exit %d\n" % code)


def feed_rows(db):
    with sqlite3.connect(db) as c:
        rows = c.execute("SELECT url, etag, last_modified, errors, last_error, last_fetch, next_fetch,"
                         " (SELECT count (*) FROM entry e WHERE e.feed_id = f.id) FROM feed f").fetchall()
    return {r[0].rsplit("/", 1)[-1]: r for r in rows}


def case_fetch(env):
    feeds = Feeds()
    srv, base = start_server(feeds)
    try:
        db = env.fresh("fetch.db")
        names = ["good.xml", "lm.xml", "grow.xml", "redirect.xml", "gzip.xml", "bomb.xml", "big.xml",
                 "missing.xml", "error.xml", "slow.xml", "broken.xml"]
        (env.work / "fetch.opml").write_text(opml([base + "/" + n for n in names]), encoding="utf-8")
        out = env.ok("-db", db, "import", str(env.work / "fetch.opml"))
        check("subscribed to 11 feeds" in out, "import: " + out)
        out = env.ok("-db", db, "-timeout", "2", "sync")
        check(re.search(r"^5 fetched with 14 new entries, 0 not modified, 6 failed", out, re.M), "first sync:\n" + out)
        rows = feed_rows(db)
        check(rows["good.xml"][1] == '"v1"' and rows["good.xml"][7] == 3, "good: %r" % (rows["good.xml"],))
        check(rows["lm.xml"][2] == "Tue, 01 Jul 2003 10:00:00 GMT", "lm: %r" % (rows["lm.xml"],))
        check(rows["redirect.xml"][7] == 3, "redirect: %r" % (rows["redirect.xml"],))
        check(rows["gzip.xml"][7] == 4 and feeds.gzip_asked, "gzip: %r" % (rows["gzip.xml"],))
        check(rows["grow.xml"][7] == 3, "grow: %r" % (rows["grow.xml"],))
        for name, error in [("bomb.xml", "larger than 32 MB"), ("big.xml", "larger than 32 MB"),
                            ("missing.xml", "HTTP status 404 Not Found"),
                            ("error.xml", "HTTP status 500 Internal Server Error"), ("slow.xml", ""),
                            ("broken.xml", "")]:
            r = rows[name]
            check(r[3] == 1 and r[4] is not None and error in r[4], "%s: %r" % (name, r))
            check(r[6] - r[5] == 15 * 60, "%s: next attempt after %d s" % (name, r[6] - r[5]))
            check(r[7] == 0, "%s: entries %d" % (name, r[7]))
        for name in ["good.xml", "lm.xml", "gzip.xml", "grow.xml"]:
            r = rows[name]
            check(r[3] == 0 and r[4] is None and r[6] - r[5] == 30 * 60, "%s: %r" % (name, r))
        check(feeds.agents == {"feedreader/1.0 (Modula-3)"}, "agents: %r" % feeds.agents)

        out = env.ok("-db", db, "sync")
        check(re.search(r"^0 fetched with 0 new entries, 0 not modified, 0 failed", out, re.M),
              "nothing is due:\n" + out)
        out = env.ok("-db", db, "-timeout", "2", "sync", "-all")
        for name in ["good.xml", "lm.xml", "redirect.xml"]:
            check(re.search(r"/%s: not modified" % re.escape(name), out), "%s again:\n%s" % (name, out))
        check(re.search(r"/grow\.xml: 1 new", out), "grow again:\n" + out)
        rows = feed_rows(db)
        check(rows["grow.xml"][7] == 4, "grow: %r" % (rows["grow.xml"],))
        with sqlite3.connect(db) as c:
            titles = [t for (t,) in c.execute("SELECT title FROM entry WHERE key = 'w1'")]
        check(titles == ["Grow 1, changed"], "a changed entry: %r" % titles)
        for name in ["bomb.xml", "missing.xml", "slow.xml"]:
            r = rows[name]
            check(r[3] == 2 and r[6] - r[5] == 30 * 60, "%s after two failures: %r" % (name, r))

        out = env.ok("-db", db, "add", base + "/page.html", "-title", "Found")
        check("(%s/gzip.xml)" % base in out, "add a page: " + out)
        code, out, err = env.run("-db", db, "add", base + "/missing.xml")
        check(code == 1 and "HTTP status 404" in err, "add a missing feed: %d %s %s" % (code, out, err))
        out = env.ok("-db", db, "feeds")
        check(out.count("failed 2 times") == 6, "feeds:\n" + out)
        out = env.ok("-db", db, "list", "-feed", "1", "-n", "2")
        check(out.count("\n") == 4 and "good.xml: Three" in out, "list:\n" + out)
    finally:
        srv.shutdown()


def case_web(env):
    feeds = Feeds()
    srv, base = start_server(feeds)
    db = str(env.work / "web.db")
    for suffix in ("", "-wal", "-shm"):
        Path(db + suffix).unlink(missing_ok=True)
    s = Server(env, db)
    try:
        b = Browser(s.base)
        st, h, _ = b.get("/")
        check(st == 303 and h["Location"].endswith("/setup"), "no user: %d %s" % (st, h["Location"]))
        st, _, page = b.get("/setup")
        check(st == 200 and "Make the first user" in page, "setup page")
        st, h, _ = b.get("/setup")
        check(h["Referrer-Policy"] == "same-origin", "a policy that keeps the origin of forms: %s"
              % h["Referrer-Policy"])
        st, _, page = b.post("/setup", {"name": "alice", "password": PASSWORD, "again": PASSWORD},
                             {"Origin": "null"})
        check(st == 403, "a form of no origin: %d" % st)
        st, _, page = b.post("/setup", {"name": "alice", "password": "short", "again": "short"})
        check(st == 200 and "at least 8 characters" in page, "short password")
        st, _, page = b.post("/setup", {"name": "alice", "password": PASSWORD, "again": PASSWORD + "x"})
        check("differ" in page, "different passwords")
        st, h, _ = b.post("/setup", {"name": "alice", "password": PASSWORD, "again": PASSWORD})
        check(st == 303, "setup: %d" % st)
        cookie = h["Set-Cookie"]
        check("HttpOnly" in cookie and "SameSite=Lax" in cookie and "Path=/" in cookie, "cookie: " + cookie)
        st, _, _ = Browser(s.base).post("/setup", {"name": "eve", "password": PASSWORD, "again": PASSWORD})
        check(st == 403, "a second setup: %d" % st)

        st, _, page = b.get("/")
        check(st == 200 and "You have no feeds yet" in page, "the empty home page")
        token = b.csrf(page)
        st, _, page = b.post("/feeds/add", {"csrf": token, "url": base + "/good.xml", "title": "Good feed"})
        check(st == 303, "add: %d %s" % (st, page[:300]))
        st, _, page = b.post("/feeds/add", {"csrf": token, "url": base + "/page.html"})
        check(st == 303, "add a page: %d" % st)
        st, _, page = b.post("/feeds/add", {"csrf": token, "url": base + "/missing.xml"})
        check(st == 200 and "HTTP status 404" in page, "add a missing feed")
        st, _, page = b.post("/feeds/add", {"csrf": token, "url": "not a url"})
        check("not the address of a web page" in page, "add nonsense")
        st, _, page = b.post("/feeds/add", {"csrf": "wrong", "url": base + "/lm.xml"})
        check(st == 403, "add without the token: %d" % st)
        st, _, page = b.post("/feeds/add", {"csrf": token, "url": base + "/lm.xml"},
                             {"Origin": "https://elsewhere.example"})
        check(st == 403, "add from another site: %d" % st)

        st, _, page = b.get("/")
        check(page.count('<article class="entry unread">') == 7, "unread entries: %d" % page.count("<article"))
        check("Good feed" in page and "Gzip" in page, "the feeds in the side bar")
        ids = re.findall(r'href="/entry/(\d+)"', page)
        first = ids[0]
        st, h, page = b.get("/entry/" + first)
        check(st == 200 and 'sandbox="allow-popups allow-popups-to-escape-sandbox"' in page, "entry page")
        check("frame-ancestors 'none'" in h["Content-Security-Policy"], "policy of a page")
        check('src="/content/%s?images=1"' % first in page and "Hide the images" in page, "images at first")
        st, h, page = b.get("/content/" + first)
        policy = h["Content-Security-Policy"]
        check(st == 200 and policy.startswith("sandbox ") and "default-src 'none'" in policy
              and policy.endswith("img-src data: https: http:"), "policy of the content: " + policy)
        st, h, page = b.get("/content/" + first + "?images=0")
        policy = h["Content-Security-Policy"]
        check(policy.endswith("img-src data:"), "no images for this entry: " + policy)
        st, _, page = b.get("/feeds")
        check("Do not show them" in page, "the setting of images on the page of the feeds")
        st, h, _ = b.post("/settings", {"csrf": token, "images": "0"})
        check(st == 303, "images off: %d" % st)
        st, h, page = b.get("/content/" + first)
        check(h["Content-Security-Policy"].endswith("img-src data:"), "no images after the setting")
        st, _, page = b.get("/entry/" + first)
        check('src="/content/%s?images=0"' % first in page and "Show the images" in page, "the link of the entry")
        st, h, page = b.get("/content/" + first + "?images=1")
        check(h["Content-Security-Policy"].endswith("img-src data: https: http:"), "images for this entry")
        st, _, page = b.post("/settings", {"csrf": "wrong", "images": "1"})
        check(st == 403, "the setting without the token: %d" % st)
        st, _, page = b.get("/")
        check(page.count('<article class="entry unread">') == 6, "read by being seen")
        st, h, _ = b.post("/entry/" + ids[1], {"csrf": token, "starred": "1", "read": "1", "back": "/?show=all"})
        check(st == 303 and h["Location"].endswith("/?show=all"), "star: %d" % st)
        st, h, _ = b.post("/entry/" + ids[1], {"csrf": token, "read": "0", "back": "//evil.example/"})
        check(h["Location"].endswith("/entry/" + ids[1]), "a foreign place to go back to: " + h["Location"])
        st, _, page = b.get("/?show=starred")
        check(page.count("<article") == 1, "starred")
        st, _, page = b.get("/?q=atom")
        check(page.count("<article") == 4 and "<mark>Atom</mark>" in page, "search")
        st, _, page = b.get("/?q=%3Cscript%3E")
        check("<script>" not in page.split("</head>", 1)[1], "the search is escaped")

        st, h, page = b.get("/export.opml")
        check(st == 200 and page.count("<outline") == 2 and "attachment" in h["Content-Disposition"], "export")
        boundary = "b0undary"
        body = ("--%s\r\nContent-Disposition: form-data; name=\"csrf\"\r\n\r\n%s\r\n"
                "--%s\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.opml\"\r\n"
                "Content-Type: text/xml\r\n\r\n%s\r\n--%s--\r\n"
                % (boundary, token, boundary, opml([base + "/lm.xml", base + "/good.xml"]), boundary)).encode()
        st, _, page = b.request("/import", body, {"Content-Type": "multipart/form-data; boundary=" + boundary})
        check(st == 200 and "Subscribed to 2 feeds" in page, "import: %d %s" % (st, page[-500:]))
        end = time.time() + 20
        while time.time() < end and "Not fetched yet" in page:
            time.sleep(0.5)
            st, _, page = b.get("/feeds")
        check("Not fetched yet" not in page, "the new feeds are not fetched")
        st, _, page = b.request("/import", body.replace(token.encode(), b"wrong"),
                                {"Content-Type": "multipart/form-data; boundary=" + boundary})
        check(st == 403, "import without the token: %d" % st)
        st, _, page = b.get("/feeds")
        check(page.count("<tr><td>") == 3, "three feeds: %d" % page.count("<tr><td>"))
        feed = re.search(r'action="/feeds/(\d+)/remove"', page).group(1)
        st, _, _ = b.post("/feeds/%s/remove" % feed, {"csrf": token})
        st, _, page = b.get("/feeds")
        check(page.count("<tr><td>") == 2, "unsubscribed")
        st, _, page = b.post("/feeds/0/read", {"csrf": token, "back": "/"})
        st, _, page = b.get("/")
        check("Nothing unread" in page, "all read")

        echo = subprocess.run(env.command() + ["-db", db, "adduser", "bob"], input=b"other-password\n",
                              capture_output=True, cwd=env.work)
        check(echo.returncode == 0, "adduser bob")
        bob = Browser(s.base)
        st, _, page = bob.post("/login", {"name": "bob", "password": "wrong-password"})
        check(st == 401 and "wrong" in page, "a wrong password: %d" % st)
        st, _, _ = bob.post("/login", {"name": "bob", "password": "other-password"})
        check(st == 303, "bob logs in")
        st, _, page = bob.get("/?show=all")
        check("<article" not in page, "bob sees no entries")
        st, _, _ = bob.get("/entry/" + first)
        check(st == 404, "bob reads an entry of alice: %d" % st)
        st, _, _ = bob.get("/content/" + first)
        check(st == 404, "bob reads the content of alice: %d" % st)

        st, h, _ = b.post("/logout", {"csrf": token})
        check(st == 303 and "Max-Age=0" in h["Set-Cookie"], "logout")
        st, h, _ = b.get("/")
        check(st == 303 and h["Location"].endswith("/login"), "after the logout")
        st, h, page = b.get("/static/style.css")
        check(st == 200 and h["Content-Type"].startswith("text/css") and "--accent" in page, "the style")
        st, _, _ = b.get("/static/../web.db")
        check(st == 404, "a file beside the static ones: %d" % st)
    finally:
        s.stop()
        srv.shutdown()


def login(env, db):
    """A server with a logged-in user "alice", and a token of the API."""
    s = Server(env, db)
    b = Browser(s.base)
    st, _, _ = b.post("/login", {"name": "alice", "password": PASSWORD})
    check(st == 303, "login: %d" % st)
    st, _, page = b.get("/feeds")
    st, _, page = b.post("/token", {"csrf": b.csrf(page)})
    m = re.search(r'<pre class="token">([^<]*)</pre>', page)
    check(m, "no token")
    return s, b, m.group(1)


def api(s, method, path, token, body=None):
    conn = http.client.HTTPConnection("127.0.0.1", s.port, timeout=60)
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    conn.request(method, path, json.dumps(body) if body is not None else None, headers)
    r = conn.getresponse()
    data = r.read()
    conn.close()
    return r.status, r.getheader("WWW-Authenticate"), json.loads(data) if data else None


def case_api(env):
    feeds = Feeds()
    srv, base = start_server(feeds)
    db = env.fresh("api.db")
    s, b, token = login(env, db)
    try:
        st, auth, data = api(s, "GET", "/api/feeds", None)
        check(st == 401 and auth == "Bearer" and "token" in data["error"], "no token: %d" % st)
        st, _, _ = api(s, "GET", "/api/feeds", "wrong")
        check(st == 401, "a wrong token: %d" % st)
        st, _, data = api(s, "GET", "/api/feeds", token)
        check(st == 200 and data == [], "no feeds: %r" % data)
        st, _, data = api(s, "POST", "/api/feeds", token, {"url": base + "/good.xml", "title": "Good"})
        check(st == 201 and data["entries"] == 3 and data["title"] == "Good", "subscribe: %d %r" % (st, data))
        st, _, data = api(s, "POST", "/api/feeds", token, {"url": base + "/missing.xml"})
        check(st == 422 and "404" in data["error"], "a missing feed: %d %r" % (st, data))
        st, _, data = api(s, "POST", "/api/feeds", token, {"nothing": 1})
        check(st == 400, "no url: %d" % st)
        st, _, data = api(s, "GET", "/api/feeds", token)
        check(len(data) == 1 and data[0]["unread"] == 3 and data[0]["url"] == base + "/good.xml", "feeds: %r" % data)
        st, _, data = api(s, "GET", "/api/entries?n=2", token)
        check(st == 200 and len(data) == 2 and data[0]["title"] == "Three", "entries: %r" % data)
        st, _, data = api(s, "GET", "/api/entries?q=two", token)
        check(len(data) == 1 and data[0]["title"] == "Two" and "\x01" not in data[0]["excerpt"], "search: %r" % data)
        eid = data[0]["id"]
        st, _, data = api(s, "POST", "/api/entries/%d" % eid, token, {"read": True, "starred": True})
        check(st == 204, "mark: %d" % st)
        st, _, data = api(s, "GET", "/api/entries/%d" % eid, token)
        check(data["read"] and data["starred"] and data["summary"] == "Text of Two.", "entry: %r" % data)
        st, _, data = api(s, "GET", "/api/entries?unread=1", token)
        check(len(data) == 2, "unread: %r" % data)
        st, _, data = api(s, "GET", "/api/entries?starred=1", token)
        check(len(data) == 1, "starred: %r" % data)
        conn = http.client.HTTPConnection("127.0.0.1", s.port, timeout=60)
        conn.request("POST", "/api/entries/%d" % eid, "{\"read\": tru", {"Authorization": "Bearer " + token})
        r = conn.getresponse()
        check(r.status == 400 and "error" in json.loads(r.read()), "broken JSON: %d" % r.status)
        conn.close()
        st, _, data = api(s, "GET", "/api/entries/99999", token)
        check(st == 404, "a missing entry: %d" % st)
        st, _, _ = api(s, "DELETE", "/api/feeds/%d" % 1, token)
        check(st == 204, "unsubscribe: %d" % st)
        st, _, data = api(s, "GET", "/api/entries", token)
        check(data == [], "no entries left: %r" % data)
        st, _, data = api(s, "GET", "/api/nothing", token)
        check(st == 404, "no such path: %d" % st)
    finally:
        s.stop()
        srv.shutdown()


def case_events(env):
    feeds = Feeds()
    srv, base = start_server(feeds)
    db = env.fresh("events.db")
    s, b, token = login(env, db)
    lines = []
    try:
        cookie = "; ".join("%s=%s" % (c.name, c.value) for c in b.jar)
        conn = http.client.HTTPConnection("127.0.0.1", s.port, timeout=30)
        conn.request("GET", "/events", headers={"Cookie": cookie})
        r = conn.getresponse()
        check(r.status == 200 and r.getheader("Content-Type") == "text/event-stream", "events: %d" % r.status)

        def read():
            try:
                while True:
                    line = r.fp.readline()
                    if not line:
                        break
                    lines.append(line.decode().rstrip("\r\n"))
            except (OSError, AttributeError):  # the connection is closed at the end
                pass

        reader = threading.Thread(target=read, daemon=True)
        reader.start()
        time.sleep(1)
        st, _, data = api(s, "POST", "/api/feeds", token, {"url": base + "/gzip.xml"})
        check(st == 201, "subscribe: %d" % st)
        end = time.time() + 20
        while time.time() < end and "event: news" not in lines:
            time.sleep(0.2)
        check("event: news" in lines, "no news: %r" % lines)
        end = time.time() + 20
        while time.time() < end and ": still here" not in lines:
            time.sleep(0.5)
        check(": still here" in lines, "no sign of life: %r" % lines)
        conn.close()
    finally:
        s.stop()
        srv.shutdown()


def case_migrate(env):
    db = env.fresh("migrate.db")
    env.ok("-db", db, "list")
    with sqlite3.connect(db) as c:
        check(c.execute("SELECT value FROM meta WHERE key = 'schema'").fetchone()[0] == "2", "a new database")
        c.execute("UPDATE user SET show_images = 0")
        # back to version 1, which had no setting of images
        c.execute("ALTER TABLE user DROP COLUMN show_images")
        c.execute("UPDATE meta SET value = '1' WHERE key = 'schema'")
    env.ok("-db", db, "list")
    with sqlite3.connect(db) as c:
        check(c.execute("SELECT value FROM meta WHERE key = 'schema'").fetchone()[0] == "2", "not migrated")
        check(c.execute("SELECT show_images FROM user").fetchall() == [(1,)], "the setting after the migration")
        c.execute("UPDATE meta SET value = '3' WHERE key = 'schema'")
    code, out, err = env.run("-db", db, "list")
    check(code == 1 and "newer version" in err, "a database of a newer version: %d %s" % (code, err))


def case_threads(env):
    feeds = Feeds()
    srv, base = start_server(feeds)
    try:
        db = env.fresh("threads.db")
        n = 60
        (env.work / "threads.opml").write_text(opml(["%s/n/%d.xml" % (base, k) for k in range(n)]), encoding="utf-8")
        env.ok("-db", db, "import", str(env.work / "threads.opml"))
        want = sum(k % 5 + 1 for k in range(n))
        out = env.ok("-db", db, "sync", "-workers", "8")
        check(re.search(r"^%d fetched with %d new entries, 0 not modified, 0 failed" % (n, want), out, re.M),
              "sync:\n" + "\n".join(l for l in out.splitlines() if " new (" not in l)[-1500:])
        hits = {p: c for p, c in feeds.hits.items() if p.startswith("/n/")}
        check(len(hits) == n and set(hits.values()) == {1}, "each feed once: %r" % hits)
        with sqlite3.connect(db) as c:
            total, distinct = c.execute("SELECT count (*), count (DISTINCT feed_id || ' ' || key) FROM entry").fetchone()
            fts = c.execute("SELECT count (*) FROM entry_fts WHERE entry_fts MATCH 'entry'").fetchone()[0]
        check(total == want and distinct == want and fts == want, "entries: %d %d %d, want %d" % (total, distinct,
                                                                                                 fts, want))
    finally:
        srv.shutdown()


# Ctrl+C for a program on Windows, which has no signal for it: runs the
# command in the console of this program, with Ctrl+C enabled (the console
# of the test may have it disabled, which children inherit), sends Ctrl+C
# to the console each time a file STOP.1, STOP.2 ... appears, and writes
# the exit code of the command to STOP.exit.
CTRL_C = """import ctypes, subprocess, sys, time
from pathlib import Path
stop, k = sys.argv[1], ctypes.windll.kernel32
k.SetConsoleCtrlHandler(None, 0)
handler = ctypes.WINFUNCTYPE(ctypes.c_int, ctypes.c_uint)(lambda event: 1)
k.SetConsoleCtrlHandler(handler, 1)
p = subprocess.Popen(sys.argv[2:], stdin=subprocess.DEVNULL)
n = 1
while p.poll() is None:
    if Path("%s.%d" % (stop, n)).exists():
        k.GenerateConsoleCtrlEvent(0, 0)
        n += 1
    time.sleep(0.05)
Path(stop + ".exit").write_text(str(p.returncode))
"""


class Interruptible:
    """The program serving, to be ended by Ctrl+C as in a terminal."""

    def __init__(self, env, db, name):
        with socket.socket() as s:
            s.bind(("127.0.0.1", 0))
            self.port = s.getsockname()[1]
        self.base = "http://127.0.0.1:%d" % self.port
        self.log = env.work / (name + ".log")
        self.stop = env.work / name
        self.sent = 0
        cmd = env.command() + ["-db", db, "serve", "-listen", "127.0.0.1:%d" % self.port]
        with open(self.log, "wb") as log:
            if os.name == "nt":
                wrapper = env.work / "ctrl_c.py"
                wrapper.write_text(CTRL_C, encoding="utf-8")
                si = subprocess.STARTUPINFO()
                si.dwFlags |= subprocess.STARTF_USESHOWWINDOW
                si.wShowWindow = 0  # hidden
                self.p = subprocess.Popen([sys.executable, "-I", str(wrapper), str(self.stop)] + cmd, cwd=env.work,
                                          stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                                          creationflags=subprocess.CREATE_NEW_CONSOLE, startupinfo=si)
            else:
                # a group of its own, to which Ctrl+C in a terminal goes
                self.p = subprocess.Popen(cmd, cwd=env.work, stdout=log, stderr=subprocess.STDOUT,
                                          stdin=subprocess.DEVNULL, start_new_session=True)
        end = time.time() + 30
        while time.time() < end:
            try:
                socket.create_connection(("127.0.0.1", self.port), timeout=1).close()
                return
            except OSError:
                time.sleep(0.2)
        self.kill()
        raise Failed("the server did not start")

    def interrupt(self):
        self.sent += 1
        if os.name == "nt":
            Path("%s.%d" % (self.stop, self.sent)).write_text("")
        else:
            os.killpg(self.p.pid, signal.SIGINT)

    def kill(self):
        if os.name == "nt":
            subprocess.run(["taskkill", "/T", "/F", "/PID", str(self.p.pid)], capture_output=True)
        else:
            os.killpg(self.p.pid, signal.SIGKILL)
        self.p.wait()

    def wait(self):
        """(exit code of the program, its log)"""
        try:
            self.p.wait(timeout=60)
        except subprocess.TimeoutExpired:
            self.kill()
            raise Failed("the program did not end after Ctrl+C")
        code = int(Path(str(self.stop) + ".exit").read_text()) if os.name == "nt" else self.p.returncode
        return code, self.log.read_bytes().decode("utf-8", "replace").replace("\r\n", "\n")


def case_stop(env):
    db = env.fresh("stop.db")
    s = Interruptible(env, db, "stop")
    try:
        b = Browser(s.base)
        st, _, _ = b.post("/login", {"name": "alice", "password": "not-" + PASSWORD})
        check(st == 401, "a wrong password: %d" % st)
        st, _, _ = b.post("/login", {"name": "nobody\x01", "password": PASSWORD})
        check(st == 401, "no such user: %d" % st)
        st, h, _ = b.request("/", headers={"Cookie": "session=no-such-token"})
        check(st == 303 and h["Location"].endswith("/login"), "a cookie of no session: %d" % st)
        # a session that expired a minute ago; the database keeps the hash
        # of its token
        token = "an-old-token"
        c = sqlite3.connect(db)
        user = c.execute("SELECT id FROM user WHERE name = 'alice'").fetchone()[0]
        c.execute("INSERT INTO session (hash, user_id, csrf, expires) VALUES (?, ?, 'x', ?)",
                  (hashlib.sha256(token.encode()).digest(), user, int(time.time()) - 60))
        c.commit()
        c.close()
        st, h, _ = b.request("/", headers={"Cookie": "session=" + token})
        check(st == 303 and h["Location"].endswith("/login"), "an expired session: %d" % st)
        st, _, _ = b.post("/login", {"name": "alice", "password": PASSWORD})
        check(st == 303, "login: %d" % st)
        st, _, _ = b.get("/")
        check(st == 200, "home: %d" % st)
        s.interrupt()
        code, log = s.wait()
    except BaseException:
        s.kill()
        raise
    for want in (r"database \S.*stop\.db, 1 user$", r'a login as "alice" refused: wrong password$',
                 r'a login as "nobody\?" refused: no such user$', r"a session cookie refused: no such session$",
                 r"a session cookie refused: the session has expired$",
                 r"interrupted: shutting down \(Ctrl\+C again ends at once\)$",
                 r"stopped; the database is closed$"):
        check(re.search(want, log, re.M), "not in the log: %s\n%s" % (want, log))
    check(code == 0, "exit %d after Ctrl+C\n%s" % (code, log))
    check(not Path(db + "-wal").exists(), "the WAL file is left")
    env.ok("-db", db, "list")

    # Ctrl+C twice, while a page waits for news, which the server would
    # give 2 seconds
    s = Interruptible(env, db, "stop2")
    try:
        b = Browser(s.base)
        st, _, _ = b.post("/login", {"name": "alice", "password": PASSWORD})
        check(st == 303, "login: %d" % st)
        cookie = "; ".join("%s=%s" % (c.name, c.value) for c in b.jar)
        conn = http.client.HTTPConnection("127.0.0.1", s.port, timeout=30)
        conn.request("GET", "/events", headers={"Cookie": cookie})
        r = conn.getresponse()
        check(r.status == 200, "events: %d" % r.status)
        s.interrupt()
        time.sleep(0.5)
        s.interrupt()
        code, log = s.wait()
        conn.close()
    except BaseException:
        s.kill()
        raise
    check("interrupted again: ending now" in log, "not in the log: ending now\n" + log)
    check("the database is closed" not in log, "closed in order\n" + log)
    check(code == 130, "exit %d after Ctrl+C twice\n%s" % (code, log))
    env.ok("-db", db, "list")


CASES = {"parse": case_parse, "fetch": case_fetch, "web": case_web, "api": case_api, "events": case_events,
         "migrate": case_migrate, "threads": case_threads, "stop": case_stop}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cases", nargs="*")
    default_java = os.path.join(os.environ["JDK25"], "bin", "java") if os.environ.get("JDK25") else \
        (shutil.which("java") or "java")
    ap.add_argument("--java", default=default_java)
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--update", action="store_true")
    ap.add_argument("--timeout", type=float, default=300.0)
    args = ap.parse_args()
    for name in args.cases:
        if name not in CASES:
            ap.error("no case " + name)
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="feedreader_test"))
    work.mkdir(parents=True, exist_ok=True)
    env = Env(args, work)
    failed = []
    try:
        t0 = time.time()
        env.build()
        print("ok    %-8s %.1f s" % ("build", time.time() - t0), flush=True)
        for name in args.cases or CASES:
            t0 = time.time()
            try:
                CASES[name](env)
                print("ok    %-8s %.1f s" % (name, time.time() - t0), flush=True)
            except Exception as e:
                failed.append(name)
                print("FAIL  %-8s %s" % (name, str(e)[:3000].encode("ascii", "backslashreplace").decode("ascii")),
                      flush=True)
    except Failed as e:
        failed.append("build")
        print("FAIL  build    %s" % str(e)[:3000])
    finally:
        if not args.keep and not args.work:
            shutil.rmtree(work, ignore_errors=True)
    print("%d failed" % len(failed) if failed else "all passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
