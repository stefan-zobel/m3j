#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Tests of the library xml of m3j. They need no connection to the internet.

  python3 tools/xml_test.py [CASE]... [--java JAVA] [--jar JAR] [--work DIR]
                            [--keep] [--timeout SECONDS] [--count N] [--speed]

The cases:

  differential  the tokens of fixed and random documents, in several encodings
                and with all reporting options, against the parser expat,
                written in C, that comes with Python
  malformed     documents that are not well-formed: expat refuses them too,
                and the kind and position of the error are the expected ones
  security      nothing outside a document is read: external entities, DTDs
                and parameter entities against a local server that must see
                no connection; entity expansion attacks
  limits        each limit, just below and just above
  text          XmlReader.FromText against the same documents as bytes
  nav           tests/xml/src/Main.m3 goes through documents by their
                structure; the output, checked by hand, is tests/xml/expected
  source        inputs that fail or block (expected output as above)
  stream        a token comes before the input after it is read (the same)
  big           a generated document under a small heap, so tokens are not
                kept

--speed measures tokens per second and the rate, for information.
"""

import argparse
import http.server
import os
import random
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request
from pathlib import Path
from xml.parsers import expat

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests" / "xml"
SEED = 20261109
BLANK = " \t\r\n"


class Failed(Exception):
    pass


class Env:
    def __init__(self, args, work):
        self.args = args
        self.work = work
        self.java = args.java
        self.classes = work / "classes"
        self.cp = str(self.classes) + os.pathsep + args.jar
        self.batches = 0

    def compile(self):
        src = TESTS / "src"
        cmd = [self.java, "-Xss64m", "-cp", self.args.jar, "m3j.jvm.Compile", str(src / "Main.m3"), "-d", str(self.classes)]
        r = subprocess.run(cmd, cwd=self.work, capture_output=True, text=True, stdin=subprocess.DEVNULL)
        if r.returncode:
            raise Failed("compile:\n" + r.stdout + r.stderr)

    def run(self, *a, heap=None):
        opts = ["-Xmx" + heap] if heap else []
        r = subprocess.run([self.java, *opts, "-cp", self.cp, "m3.Main", *a], cwd=self.work, capture_output=True,
                           timeout=self.args.timeout)
        if r.returncode:
            raise Failed("exit %d: %s" % (r.returncode, (r.stdout + r.stderr).decode("latin-1")))
        if r.stderr:
            raise Failed("output on standard error: %s" % r.stderr.decode("latin-1")[:500])
        return r.stdout.decode("latin-1")

    def batch(self, docs):
        """The tokens of each (bytes, flags), as a list of lines without comments."""
        self.batches += 1
        d = "b%d" % self.batches
        (self.work / d).mkdir()
        lines = []
        for i, (data, flags) in enumerate(docs):
            name = "%s/%05d.xml" % (d, i)
            (self.work / name).write_bytes(data)
            lines.append(" ".join([name, *flags]))
        (self.work / (d + ".list")).write_text("\n".join(lines) + "\n", newline="\n")
        self.run("batch", d + ".list")
        out = []
        for i in range(len(docs)):
            t = (self.work / ("%s/%05d.xml.out" % (d, i))).read_text(encoding="ascii")
            out.append([x for x in t.splitlines() if not x.startswith("# ")])
        return out

    def one(self, data, flags=()):
        return self.batch([(data, list(flags))])[0]


def esc(s):
    """As Esc of tests/xml/src/Main.m3: code units of UTF-16."""
    out = []
    b = s.encode("utf-16-le", "surrogatepass")
    for i in range(0, len(b), 2):
        c = b[i] + 256 * b[i + 1]
        if 0x20 <= c < 0x7F and c != 0x5C:
            out.append(chr(c))
        else:
            out.append("\\%04x" % c)
    return "".join(out)


def name_of(n):
    parts = n.split("\x01")
    if len(parts) == 1:
        return "|%s|" % esc(parts[0])
    if len(parts) == 2:
        return "%s|%s|" % (esc(parts[0]), esc(parts[1]))
    return "%s|%s|%s" % (esc(parts[0]), esc(parts[1]), esc(parts[2]))


def expat_tokens(data, flags=()):
    """The tokens by expat in the form of the program, or raises ExpatError."""
    p = expat.ParserCreate(namespace_separator="\x01")
    p.namespace_prefixes = True
    p.ordered_attributes = True
    out = []
    text = []

    def flush():
        t = "".join(text)
        text.clear()
        if t and ("nowhitespace" not in flags or t.strip(BLANK)):
            out.append("T " + esc(t))

    def start(n, a):
        flush()
        s = "S " + name_of(n)
        for i in range(0, len(a), 2):
            s += " %s=%s" % (name_of(a[i]), esc(a[i + 1]))
        out.append(s)

    def end(n):
        flush()
        out.append("E " + name_of(n))

    def comment(t):
        if "comments" in flags:
            flush()
            out.append("C " + esc(t))

    def pi(target, d):
        if "instructions" in flags:
            flush()
            out.append("P %s %s" % (esc(target), esc(d)))

    p.StartElementHandler = start
    p.EndElementHandler = end
    p.CharacterDataHandler = text.append
    p.CommentHandler = comment
    p.ProcessingInstructionHandler = pi
    p.Parse(data, True)
    flush()
    out.append("EOF")
    return out


def compare_docs(env, docs):
    """Each (bytes, flags) must give the tokens of expat."""
    want = [expat_tokens(d, f) for d, f in docs]
    got = env.batch(docs)
    for (d, f), a, b in zip(docs, got, want):
        if a != b:
            first = next((i for i, (x, y) in enumerate(zip(a, b)) if x != y), min(len(a), len(b)))
            raise Failed("document %r %s:\n  at token %d\n  got  %s\n  want %s"
                         % (d[:300], f, first, a[first:first + 2], b[first:first + 2]))


FIXED = [
    "<a/>",
    "<a></a>",
    "<a>x</a>",
    "\n\n<a/>\n\n",
    "<a> </a>",
    "<a>\r\nb\rc\nd</a>",
    "<a x='1' y=\"2\" z=''/>",
    "<a x='&lt;&gt;&amp;&apos;&quot;'/>",
    "<a x=' a\tb\nc\r\nd '/>",
    "<a x='&#9;&#10;&#13;&#32;'/>",
    "<a>&lt;&gt;&amp;&apos;&quot;</a>",
    "<a>&#65;&#x41;&#x20AC;&#x1F600;&#xE9;</a>",
    "<a><![CDATA[x<y&z]]></a>",
    "<a><![CDATA[]]></a>",
    "<a>p<![CDATA[q]]>r&amp;s<![CDATA[t]]></a>",
    "<a><![CDATA[]]]]><![CDATA[>]]></a>",
    "<a>]]</a>",
    "<a>x<!--c-->y<?p d?>z</a>",
    "<!--before--><?p d?><a/><!--after--><?q?>",
    "<a><!----></a>",
    "<a><?p   spaced  data ?></a>",
    "<?xml version='1.0'?><a/>",
    "<?xml version='1.0' encoding='UTF-8'?><a/>",
    "<?xml version='1.0' encoding='utf-8' standalone='yes'?><a/>",
    "<?xml-stylesheet href='a'?><a/>",
    "<!DOCTYPE a><a/>",
    "<!DOCTYPE a SYSTEM 'nothing.dtd'><a/>",
    "<!DOCTYPE a PUBLIC 'p' 'nothing.dtd'><a/>",
    "<a xmlns='urn:d'><b/><c xmlns=''><d/></c></a>",
    "<p:a xmlns:p='urn:p'><p:b/><q:c xmlns:q='urn:q' q:x='1' p:y='2' z='3'/></p:a>",
    "<a xmlns:p='urn:p' xmlns:q='urn:q' p:x='1' q:x='2' x='3'/>",
    "<a xml:lang='de' xml:space='preserve'> </a>",
    "<a xmlns='urn:d' x='1'/>",
    "<a b.c-d_e='1'><f.g/><_h/></a>",
    "<caf\u00e9 \u4e2d='\u20ac'>\u00e9\u4e2d\U0001F600</caf\u00e9>",
    "<a>" + "x" * 200000 + "</a>",
    "<a><![CDATA[" + "x" * 200000 + "]]></a>",
    "<a x='" + "y" * 300000 + "'/>",
    "<a>" + "&lt;" * 100000 + "</a>",
    "<a>" + "&#65;" * 100000 + "</a>",
    "<a>" + "<b/>" * 5000 + "</a>",
    "<a>" + "x<!--c-->" * 3000 + "</a>",
]

FLAGS = [[], ["comments"], ["instructions"], ["comments", "instructions"], ["nowhitespace"],
         ["comments", "nowhitespace"]]


def encoded(rng, doc):
    """Bytes of doc (text without a declaration, all of it in UTF-8's range)."""
    kind = rng.randrange(7)
    if kind == 0:
        return doc.encode("utf-8")
    if kind == 1:
        return b"\xef\xbb\xbf" + doc.encode("utf-8")
    if kind == 2:
        return ("<?xml version='1.0' encoding='UTF-8'?>\n" + doc).encode("utf-8")
    if kind == 3:
        return b"\xff\xfe" + doc.encode("utf-16-le")
    if kind == 4:
        return b"\xfe\xff" + doc.encode("utf-16-be")
    if kind == 5:
        return b"\xff\xfe" + ("<?xml version='1.0' encoding='UTF-16'?>" + doc).encode("utf-16-le")
    return b"\xfe\xff" + ("<?xml version='1.0' encoding='utf-16'?>" + doc).encode("utf-16-be")


class Gen:
    """Random well-formed documents; `limit` is the last code point of the encoding."""

    NAMES = ["a", "b", "item", "x-y", "n.m", "_u", "caf\u00e9", "\u4e2d\u6587"]
    ASCII_NAMES = ["a", "b", "item", "x-y", "n.m", "_u"]
    CHARS = "abc XYZ 012\t\n" + "\u00e9\u00fc\u20ac\u4e2d\U0001F600\U00010348"

    def __init__(self, rng, limit):
        self.rng = rng
        self.limit = limit
        self.names = self.NAMES if limit > 0xFF else self.ASCII_NAMES

    def raw(self, n, forbidden=""):
        """Characters that the encoding has, for places without references."""
        cs = [c for c in self.CHARS if ord(c) <= self.limit and c not in forbidden]
        return "".join(self.rng.choice(cs) for _ in range(self.rng.randrange(n)))

    def data(self, quote=None):
        """Character data or an attribute value, with references."""
        out = []
        for _ in range(self.rng.randrange(6)):
            r = self.rng.randrange(8)
            if r == 0:
                out.append(self.rng.choice(["&lt;", "&gt;", "&amp;", "&apos;", "&quot;"]))
            elif r == 1:
                out.append("&#%d;" % self.rng.choice([65, 233, 8364, 128512, 10, 9]))
            elif r == 2:
                out.append("&#x%x;" % self.rng.choice([0x41, 0xE9, 0x20AC, 0x1F600]))
            elif r == 3 and quote is None:
                out.append("]]")
            elif r == 4:
                out.append("\r\n")
            elif r == 5:
                out.append(self.big_char())
            else:
                out.append(self.raw(9))
        return "".join(out)

    def big_char(self):
        c = self.rng.choice("\u00e9\u20ac\u4e2d\U0001F600")
        return c if ord(c) <= self.limit else "&#%d;" % ord(c)

    def misc(self):
        r = self.rng.randrange(2)
        if r == 0:
            return "<!--%s-->" % self.raw(9, "-")
        return "<?pi%d %s?>" % (self.rng.randrange(3), self.raw(9, "?"))

    def element(self, depth, scope):
        rng = self.rng
        scope = dict(scope)
        attrs = []
        seen = set()
        for _ in range(rng.randrange(3)):
            r = rng.randrange(4)
            if r == 0:
                p = rng.choice(["p", "q"])
                u = "urn:%s" % rng.choice("uvw")
                scope[p] = u
                attrs.append("xmlns:%s='%s'" % (p, u))
            elif r == 1:
                u = rng.choice(["", "urn:d"])
                scope[""] = u
                attrs.append("xmlns='%s'" % u)
        decl = {a.split("=")[0] for a in attrs}
        attrs = [a for i, a in enumerate(attrs) if a.split("=")[0] not in [b.split("=")[0] for b in attrs[:i]]]
        # the last duplicate must not be what the scope says: take the first
        scope = dict(scope)
        for a in attrs:
            k, v = a.split("=")
            scope[k[6:] if k.startswith("xmlns:") else ""] = v.strip("'")
        del decl
        for _ in range(rng.randrange(4)):
            n = rng.choice(self.names)
            ps = [p for p in scope if p]
            if ps and rng.randrange(2):
                p = rng.choice(ps)
                key = (scope[p], n)
                q = "%s:%s" % (p, n)
            else:
                key = ("", n)
                q = n
            if key in seen:
                continue
            seen.add(key)
            v = self.data("'").replace("'", "&apos;").replace("<", "&lt;")
            attrs.append("%s='%s'" % (q, v))
        n = rng.choice(self.names)
        ps = [p for p in scope if p]
        if ps and rng.randrange(3) == 0:
            n = "%s:%s" % (rng.choice(ps), n)
        head = " ".join([n, *attrs])
        if rng.randrange(5) == 0:
            return "<%s/>" % head
        body = []
        for _ in range(rng.randrange(7)):
            r = rng.randrange(6)
            if r <= 1:
                body.append(self.data())
            elif r == 2 and depth < 7:
                body.append(self.element(depth + 1, scope))
            elif r == 3:
                body.append("<![CDATA[%s]]>" % self.raw(9, "]"))
            elif r == 4:
                body.append(self.misc())
            elif depth < 7:
                body.append(self.element(depth + 1, scope))
        return "<%s>%s</%s>" % (head, "".join(body), n)

    def document(self):
        rng = self.rng
        before = "".join(self.misc() for _ in range(rng.randrange(3)))
        after = "".join(self.misc() for _ in range(rng.randrange(3)))
        gap = rng.choice(["", "\n", "  "])
        return before + gap + self.element(0, {}) + gap + after


def case_differential(env):
    docs = []
    for d in FIXED:
        for f in FLAGS:
            docs.append((d.encode("utf-8"), f))
    docs += [
        ("<?xml version='1.0' encoding='ISO-8859-1'?><a x='\u00e9'>\u00e9\u00ff</a>".encode("latin-1"), []),
        ("<?xml version='1.0' encoding='iso-8859-1'?><\u00e9/>".encode("latin-1"), []),
        (b"<?xml version=\"1.0\"   encoding = \"US-ASCII\" ?><a>x</a>", []),
        (b"<?xml version='1.0'\n encoding='utf-8'\n?>\n<a/>", []),
    ]
    rng = random.Random(SEED)
    for _ in range(env.args.count):
        flags = rng.choice(FLAGS)
        r = rng.randrange(4)
        if r == 0:
            d = Gen(rng, 0xFF).document()
            data = ("<?xml version='1.0' encoding='ISO-8859-1'?>" + d).encode("latin-1")
        elif r == 1:
            d = Gen(rng, 0x7F).document()
            data = ("<?xml version='1.0' encoding='us-ascii'?>" + d).encode("ascii")
        else:
            data = encoded(rng, Gen(rng, 0x10FFFF).document())
        docs.append((data, flags))
    print("          %d documents" % len(docs), flush=True)
    compare_docs(env, docs)


U = "\xe9".encode("utf-8")

# (name, bytes, the problem, line, column, whether expat refuses it)
MALFORMED = [
    ("end tag of another name", b"<a></b>", "Malformed", 1, 6, True),
    ("ends in the content", b"<a><b>", "Malformed", 1, 7, True),
    ("ends in a tag", b"<a x='1'", "Malformed", 1, 9, True),
    ("two roots", b"<a/><b/>", "Malformed", 1, 6, True),
    ("text after the root", b"<a/>x", "Malformed", 1, 5, True),
    ("text before the root", b"x<a/>", "Malformed", 1, 1, True),
    ("attribute twice", b"<a x='1' x='2'/>", "Malformed", 1, 17, True),
    ("attribute twice by namespace", b"<a xmlns:p='u' xmlns:q='u' p:x='1' q:x='2'/>", "Malformed", 1, 45, True),
    ("unbound element prefix", b"<p:a/>", "Malformed", 1, 7, True),
    ("unbound attribute prefix", b"<a p:x='1'/>", "Malformed", 1, 13, True),
    ("empty", b"", "Malformed", 1, 1, True),
    ("only white space", b"   ", "Malformed", 1, 4, True),
    ("control character", b"<a>\x01</a>", "Malformed", 1, 4, True),
    ("character reference to 0", b"<a>&#0;</a>", "Malformed", 1, 8, True),
    ("character reference to a surrogate", b"<a>&#xD800;</a>", "Malformed", 1, 12, True),
    ("less-than in an attribute", b"<a x='<'/>", "Malformed", 1, 7, True),
    ("bare ampersand", b"<a>&</a>", "Malformed", 1, 5, True),
    ("undeclared entity", b"<a>&foo;</a>", "Malformed", 1, 9, False),
    ("end of CDATA in text", b"<a>]]></a>", "Malformed", 1, 7, True),
    ("attribute without quotes", b"<a x=1/>", "Malformed", 1, 6, True),
    ("attribute without value", b"<a x/>", "Malformed", 1, 5, True),
    ("two hyphens in a comment", b"<a><!-- -- --></a>", "Malformed", 1, 11, True),
    ("comment not ended", b"<a><!-- ", "Malformed", 1, 9, True),
    ("CDATA not ended", b"<a><![CDATA[x", "Malformed", 1, 14, True),
    ("instruction named xml", b"<a><?XML x?></a>", "Malformed", 1, 9, True),
    ("declaration not at the start", b" <?xml version='1.0'?><a/>", "Malformed", 1, 7, True),
    ("name starting with a digit", b"<1a/>", "Malformed", 1, 2, True),
    ("empty namespace of a prefix", b"<a xmlns:p=''/>", "Malformed", 1, 14, True),
    ("rebinding of the prefix xml", b"<a xmlns:xml='urn:x'/>", "Malformed", 1, 21, True),
    ("invalid UTF-8 at once", b"\xc3(<a/>", "Encoding", 1, 1, True),
    ("invalid UTF-8 in the content", b"<a>x\xc3(</a>", "Encoding", 1, 5, True),
    ("invalid UTF-8 far in", b"<a>" + b"x" * 100000 + b"\xc3(</a>", "Encoding", 1, 100004, True),
    ("truncated UTF-8 at the end", b"<a>\xe2\x82", "Encoding", 1, 4, True),
    ("overlong UTF-8", b"<a>\xc0\x80</a>", "Encoding", 1, 4, True),
    ("a high byte in ASCII", b"<?xml version='1.0' encoding='US-ASCII'?><a>\xe9</a>", "Encoding", 1, 45, True),
    ("unknown encoding", b"<?xml version='1.0' encoding='NOSUCH-1'?><a/>", "Encoding", 1, 1, True),
    ("UTF-16 declared on 8-bit bytes", b"<?xml version='1.0' encoding='UTF-16'?><a/>", "Encoding", 1, 1, True),
    ("UTF-16 cut in a unit", b"\xff\xfe<\x00a", "Encoding", 1, 2, True),
]


def case_malformed(env):
    got = env.batch([(d, []) for _, d, *_ in MALFORMED])
    for (name, d, problem, line, col, refused), lines in zip(MALFORMED, got):
        try:
            expat_tokens(d)
            accepted = True
        except (expat.ExpatError, LookupError):
            accepted = False
        if refused and accepted:
            raise Failed("%s: expat takes it" % name)
        last = lines[-1] if lines else ""
        if last != "ERROR %s %d:%d" % (problem, line, col):
            raise Failed("%s: got %r, want %s at %d:%d" % (name, last, problem, line, col))


class Counting(http.server.BaseHTTPRequestHandler):
    hits = 0

    def do_GET(self):
        Counting.hits += 1
        self.send_response(200)
        self.send_header("Content-Length", "1")
        self.end_headers()
        self.wfile.write(b"x")

    def log_message(self, *a):
        pass


def case_security(env):
    secret = env.work / "secret.txt"
    secret.write_text("TOP-SECRET", newline="\n")
    url = secret.resolve().as_uri()
    Counting.hits = 0
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Counting)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    base = "http://127.0.0.1:%d" % srv.server_address[1]
    try:
        # the server would notice a request
        urllib.request.urlopen(base + "/check", timeout=10).read()
        if Counting.hits != 1:
            raise Failed("the local server does not count")
        Counting.hits = 0
        laughs = "<!DOCTYPE a [<!ENTITY a0 'aaaaaaaaaa'>"
        for i in range(1, 10):
            laughs += "<!ENTITY a%d '%s'>" % (i, ("&a%d;" % (i - 1)) * 10)
        laughs += "]><a>&a9;</a>"
        quad = "<!DOCTYPE a [<!ENTITY x '%s'>]><a>%s</a>" % ("x" * 50000, "&x;" * 50000)
        table = [
            ("external entity, file", "<!DOCTYPE a [<!ENTITY x SYSTEM '%s'>]><a>&x;</a>" % url, [], "ERROR Malformed"),
            ("external entity, server", "<!DOCTYPE a [<!ENTITY x SYSTEM '%s/e'>]><a>&x;</a>" % base, [], "ERROR Malformed"),
            ("external entity in an attribute", "<!DOCTYPE a [<!ENTITY x SYSTEM '%s/e'>]><a b='&x;'/>" % base, [], "ERROR Malformed"),
            ("internal entity", "<!DOCTYPE a [<!ENTITY x 'v'>]><a>&x;</a>", [], "ERROR Malformed"),
            ("external DTD", "<!DOCTYPE a SYSTEM '%s/d.dtd'><a/>" % base, [], "EOF"),
            ("public DTD", "<!DOCTYPE a PUBLIC '-//X//Y' '%s/p.dtd'><a/>" % base, [], "EOF"),
            ("parameter entity", "<!DOCTYPE a [<!ENTITY %% p SYSTEM '%s/p'> %%p;]><a/>" % base, [], "EOF"),
            ("XInclude stays an element", "<a xmlns:i='http://www.w3.org/2001/XInclude'><i:include href='%s/i'/></a>" % base, [], "EOF"),
            ("schema location stays an attribute",
             "<a xmlns:s='http://www.w3.org/2001/XMLSchema-instance' s:schemaLocation='u %s/s.xsd'/>" % base, [], "EOF"),
            ("billion laughs", laughs, [], "ERROR Malformed"),
            ("quadratic blowup", quad, [], "ERROR Malformed"),
            ("doctype refused", "<!DOCTYPE a><a/>", ["reject"], "ERROR Malformed"),
            ("doctype refused, no use of entities", "<!DOCTYPE a SYSTEM '%s/r.dtd'><a/>" % base, ["reject"], "ERROR Malformed"),
        ]
        t0 = time.time()
        got = env.batch([(d.encode("utf-8"), f) for _, d, f, _ in table])
        for (name, _, _, want), lines in zip(table, got):
            if not lines[-1].startswith(want):
                raise Failed("%s: got %s, want %s" % (name, lines[-1], want))
            if any("TOP-SECRET" in x for x in lines):
                raise Failed("%s: the secret was read" % name)
        if time.time() - t0 > 30:
            raise Failed("the attacks took %.0f s" % (time.time() - t0))
        time.sleep(0.3)
        if Counting.hits:
            raise Failed("the local server saw %d requests" % Counting.hits)
    finally:
        srv.shutdown()
        srv.server_close()


def nest(n):
    return "<a>" * n + "</a>" * n


def attrs(n):
    return "<a %s/>" % " ".join("x%d=''" % i for i in range(n))


def case_limits(env):
    x = "x" * 100000
    token = ["token=100000"]
    many = "<a>" + ("<b>%s</b>" % ("y" * 1000)) * 1000 + "</a>"
    table = [
        ("depth at the default", nest(256), [], None),
        ("depth above the default", nest(257), [], "Limit"),
        ("depth at the limit", nest(10), ["depth=10"], None),
        ("depth above the limit", nest(11), ["depth=10"], "Limit"),
        ("depth without a limit", nest(5000), ["depth=0"], None),
        ("attributes at the default", attrs(1024), [], None),
        ("attributes above the default", attrs(1025), [], "Limit"),
        ("attributes at the limit", attrs(10), ["attrs=10"], None),
        ("attributes above the limit", attrs(11), ["attrs=10"], "Limit"),
        ("attributes without a limit", attrs(3000), ["attrs=0"], None),
        ("name at the default", "<%s/>" % ("n" * 1024), [], None),
        ("name above the default", "<%s/>" % ("n" * 1025), [], "Limit"),
        ("name at the limit", "<%s/>" % ("n" * 20), ["names=20"], None),
        ("name above the limit", "<%s/>" % ("n" * 21), ["names=20"], "Limit"),
        ("name without a limit", "<%s/>" % ("n" * 5000), ["names=0"], None),
        ("text at the limit", "<a>%s</a>" % x, token, None),
        ("text above the limit", "<a>%sx</a>" % x, token, "Limit"),
        ("CDATA at the limit", "<a><![CDATA[%s]]></a>" % x, token, None),
        ("CDATA above the limit", "<a><![CDATA[%sx]]></a>" % x, token, "Limit"),
        ("CDATA far above the limit", "<a><![CDATA[%s]]></a>" % (x * 3), token, "Limit"),
        ("joined text above the limit", "<a>%s<![CDATA[%s]]></a>" % (x[:60000], x[:60000]), token, "Limit"),
        ("text around a comment above the limit", "<a>%s<!--c-->%s</a>" % (x[:60000], x[:60000]), token, "Limit"),
        ("reported comment at the limit", "<a><!--%s--></a>" % x, ["comments", *token], None),
        ("reported comment above the limit", "<a><!--%sx--></a>" % x, ["comments", *token], "Limit"),
        ("reported instruction above the limit", "<a><?p %sx?></a>" % x, ["instructions", *token], "Limit"),
        ("attribute below the limit", "<a x='%s'/>" % x[:50000], token, None),
        ("attribute far above the limit", "<a x='%s'/>" % (x * 3), token, "Limit"),
        ("unreported comment far above the limit", "<a><!--%s--></a>" % (x * 3), token, "Limit"),
        ("many small tokens", many, ["token=2000"], None),
        ("tokens without a limit", "<a><![CDATA[%s]]></a>" % (x * 3), ["token=0"], None),
    ]
    got = env.batch([(d.encode("utf-8"), f) for _, d, f, _ in table])
    for (name, _, _, want), lines in zip(table, got):
        last = lines[-1]
        if want is None:
            if last != "EOF":
                raise Failed("%s: got %s" % (name, last))
        elif not last.startswith("ERROR %s " % want):
            raise Failed("%s: got %s, want %s" % (name, last, want))


def case_text(env):
    docs = ["<a x='\u20ac'>\u00e9\u4e2d\U0001F600<![CDATA[p]]>&#x1F600;</a>",
            "<?xml version='1.0' encoding='ISO-8859-1'?><a>\u20ac</a>",
            "\ufeff<a>mark</a>",
            "<a>" + "\u20ac" * 50000 + "</a>"]
    for i, d in enumerate(docs):
        (env.work / ("t%d.bin" % i)).write_bytes(d.encode("utf-16-le", "surrogatepass"))
        got = [x for x in env.run("text", "t%d.bin" % i).splitlines() if not x.startswith("# ")]
        body = d.lstrip("\ufeff")
        if body.startswith("<?xml"):
            body = body[body.index("?>") + 2:]
        want = expat_tokens(body.encode("utf-8"))
        if got != want:
            raise Failed("document %d:\n  got  %s\n  want %s" % (i, got[:4], want[:4]))


def expected(env, mode):
    got = env.run(mode)
    want = (TESTS / "expected" / (mode + ".txt")).read_text(encoding="ascii")
    if got.replace("\r\n", "\n") != want.replace("\r\n", "\n"):
        (env.work / (mode + ".got")).write_text(got, newline="\n")
        a = got.replace("\r\n", "\n").splitlines()
        b = want.replace("\r\n", "\n").splitlines()
        first = next((i for i, (x, y) in enumerate(zip(a, b)) if x != y), min(len(a), len(b)))
        raise Failed("line %d:\n  got  %s\n  want %s" % (first + 1, a[first:first + 1], b[first:first + 1]))


def case_nav(env):
    expected(env, "nav")


def case_source(env):
    expected(env, "source")


def case_stream(env):
    expected(env, "stream")


def big_counts(mb):
    """What the generated document gives: the tokens and the sum."""
    left = mb * 1000 * 1000
    n = 0
    total = 0
    while left > 0:
        n += 1
        line = '<item id="%d" k="v">text %d</item>\n' % (n, n)
        left -= len(line)
        total += n % 7 + len("text %d" % n) + 1
    return 4 * n + 3, total


def case_big(env):
    mb = 200
    out = env.run("big", str(mb), heap="64m")
    want = "events %d sum %d\n" % big_counts(mb)
    if out.replace("\r\n", "\n") != want:
        raise Failed("got %r, want %r" % (out, want))


def speed(env):
    print(env.run("big", "500", "speed").rstrip().splitlines()[-1], flush=True)


CASES = {
    "differential": case_differential,
    "malformed": case_malformed,
    "security": case_security,
    "limits": case_limits,
    "text": case_text,
    "nav": case_nav,
    "source": case_source,
    "stream": case_stream,
    "big": case_big,
}


def main():
    ap = argparse.ArgumentParser(description="tests of the library xml")
    ap.add_argument("cases", nargs="*")
    ap.add_argument("--java", default=str(Path(os.environ.get("JDK25", "")) / "bin" / "java") if os.environ.get("JDK25") else "java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--timeout", type=float, default=240)
    ap.add_argument("--count", type=int, default=1500, help="random documents")
    ap.add_argument("--speed", action="store_true")
    args = ap.parse_args()
    if sys.platform == "win32" and not args.java.endswith(".exe") and Path(args.java + ".exe").exists():
        args.java += ".exe"

    names = args.cases or list(CASES)
    for n in names:
        if n not in CASES:
            ap.error("unknown case " + n)
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="m3j_xml_"))
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
            except (Failed, subprocess.TimeoutExpired, OSError, expat.ExpatError) as e:
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
