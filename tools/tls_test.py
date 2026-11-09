#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Tests of the library tls of m3j. They need no connection to the internet.

  python3 tools/tls_test.py [CASE]... [--java JAVA] [--jar JAR] [--work DIR]
                            [--keep] [--timeout SECONDS] [--speed]

keytool makes the keys and certificates in the working directory, so that
no key is part of the tree: a certificate authority, servers for localhost
and 127.0.0.1, for another name and with a certificate that has expired,
a server of another authority, and a client.

The cases:

  local    tests/tls/src/Main.m3 with server and client in one program;
           the output, checked by hand, is tests/tls/expected/local.txt
  python   with the module ssl of Python (OpenSSL), an independent
           implementation, as client and as server, TLS 1.2 and 1.3
  rekey    with a server of the JDK (tests/tls/Peer.java) that starts a new
           handshake on the connection: renegotiation under TLS 1.2, an
           update of the keys under TLS 1.3

Without arguments all cases run. --speed also measures the rate of a
transfer over the loopback address, for information. JAVA defaults to
$JDK25/bin/java, else java from the PATH; JAR to m3j.jar in the root of m3j.
"""

import argparse
import os
import shutil
import socket
import ssl
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests" / "tls"
PASSWORD = "changeit"
SIZES = [1, 100, 16383, 16384, 16385, 70000, 1048576]


class Failed(Exception):
    pass


class Env:
    def __init__(self, args, work):
        self.args = args
        self.work = work
        self.certs = work / "certs"
        self.java = args.java
        self.keytool = str(Path(args.java).with_name("keytool" + Path(args.java).suffix))
        self.classes = work / "classes"
        self.cp = str(self.classes) + os.pathsep + args.jar

    def key(self, *a, data=None):
        r = subprocess.run([self.keytool, *a, "-storepass", PASSWORD], cwd=self.certs, input=data,
                           capture_output=True)
        if r.returncode:
            raise Failed("keytool %s: %s" % (a, (r.stdout + r.stderr).decode("latin-1")))
        return r.stdout

    def authority(self, name):
        self.key("-genkeypair", "-alias", "ca", "-keyalg", "EC", "-groupname", "secp256r1",
                 "-dname", "CN=m3j test " + name, "-ext", "bc:c", "-ext", "ku:c=keyCertSign,cRLSign", "-validity", "3650",
                 "-keystore", name + ".p12", "-storetype", "PKCS12")
        pem = self.key("-exportcert", "-rfc", "-alias", "ca", "-keystore", name + ".p12")
        (self.certs / (name + ".pem")).write_bytes(pem)

    def leaf(self, name, cn, san=None, ca="ca", validity=("-validity", "3650")):
        store = name + ".p12"
        self.key("-genkeypair", "-alias", "leaf", "-keyalg", "EC", "-groupname", "secp256r1",
                 "-dname", "CN=" + cn, "-validity", "3650", "-keystore", store, "-storetype", "PKCS12")
        req = self.key("-certreq", "-alias", "leaf", "-keystore", store)
        ext = ["-ext", "san=" + san] if san else []
        cert = self.key("-gencert", "-alias", "ca", "-keystore", ca + ".p12", "-rfc", *validity, *ext, data=req)
        (self.certs / (name + ".crt")).write_bytes(cert)
        self.key("-importcert", "-noprompt", "-alias", "ca", "-file", ca + ".pem", "-keystore", store)
        self.key("-importcert", "-alias", "leaf", "-file", name + ".crt", "-keystore", store)

    def make_certs(self):
        self.certs.mkdir(parents=True, exist_ok=True)
        local = "dns:localhost,ip:127.0.0.1"
        self.authority("ca")
        self.authority("other")
        self.leaf("server", "localhost", local)
        self.leaf("wrong", "wrong.example", "dns:wrong.example")
        self.leaf("expired", "localhost", local, validity=("-startdate", "-2d", "-validity", "1"))
        self.leaf("foreign", "localhost", local, ca="other")
        self.leaf("client", "m3j test client")
        self.peer("export", "server.p12", PASSWORD, "server.pem")

    def peer_cmd(self, *a):
        return [self.java, str(TESTS / "Peer.java"), *a]

    def peer(self, *a):
        r = subprocess.run(self.peer_cmd(*a), cwd=self.certs, capture_output=True, text=True,
                           timeout=self.args.timeout)
        if r.returncode:
            raise Failed("Peer %s: %s" % (a, r.stdout + r.stderr))

    def compile(self):
        r = subprocess.run([self.java, "-Xss64m", "-cp", self.args.jar, "m3j.jvm.Compile",
                            str(TESTS / "src" / "Main.m3"), "-d", str(self.classes)],
                           capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=self.args.timeout)
        if r.returncode:
            raise Failed("compile:\n" + r.stdout + r.stderr)

    def program(self, *a):
        return [self.java, "-cp", self.cp, "m3.Main", *a]

    def run(self, *a):
        r = subprocess.run(self.program(*a), cwd=self.work, capture_output=True, text=True,
                           stdin=subprocess.DEVNULL, timeout=self.args.timeout)
        return r.returncode, r.stdout, r.stderr

    def popen(self, *a):
        return subprocess.Popen(self.program(*a), cwd=self.work, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                stdin=subprocess.DEVNULL, text=True)


def compare(what, got, want):
    if got.replace("\r\n", "\n") != want.replace("\r\n", "\n"):
        g = got.replace("\r\n", "\n").splitlines()
        w = want.replace("\r\n", "\n").splitlines()
        for i in range(max(len(g), len(w))):
            a = g[i] if i < len(g) else "<none>"
            b = w[i] if i < len(w) else "<none>"
            if a != b:
                raise Failed("%s: line %d: got %r, want %r" % (what, i + 1, a, b))


def case_local(env):
    code, out, err = env.run("local")
    if code:
        raise Failed("local: exit %d\n%s\n%s" % (code, out[-2000:], err[-2000:]))
    compare("local", out, (TESTS / "expected" / "local.txt").read_text())


def at(i):
    return (i * 7 + i // 251) % 256


def block(n):
    return bytes(at(i) for i in range(n))


def recv_all(s, n):
    b = bytearray()
    while len(b) < n:
        c = s.recv(min(65536, n - len(b)))
        if not c:
            break
        b += c
    return bytes(b)


def client_output(version, alpn):
    lines = ["%s protocol %s" % (version, alpn)]
    lines += ["client %d: read %d wrong 0" % (n, n) for n in SIZES]
    lines += ["client: EOF TRUE"]
    return "\n".join(lines) + "\n"


def python_server(env, version, name):
    """A server of Python that echoes; a client of m3j."""
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.minimum_version = ctx.maximum_version = version
    ctx.load_cert_chain(str(env.certs / "server.pem"))
    ctx.set_alpn_protocols(["echo"])
    errors = []
    listener = socket.create_server(("127.0.0.1", 0))
    port = listener.getsockname()[1]

    def serve():
        try:
            raw, _ = listener.accept()
            s = ctx.wrap_socket(raw, server_side=True)
            s.settimeout(env.args.timeout)
            while True:
                c = s.recv(65536)
                if not c:
                    break
                s.sendall(c)
            s.unwrap().close()
        except Exception as e:
            errors.append(repr(e))

    th = threading.Thread(target=serve, daemon=True)
    th.start()
    code, out, err = env.run("client", str(port))
    th.join(env.args.timeout)
    listener.close()
    if code or errors:
        raise Failed("python server %s: exit %d %s\n%s\n%s" % (name, code, errors, out[-1000:], err[-2000:]))
    compare("python server " + name, out, client_output(name, "echo"))


def python_client(env, version, name):
    """A client of Python; a server of m3j."""
    p = env.popen("server")
    try:
        port = int(p.stdout.readline().split()[1])
        ctx = ssl.create_default_context(cafile=str(env.certs / "ca.pem"))
        ctx.minimum_version = ctx.maximum_version = version
        ctx.set_alpn_protocols(["echo"])
        raw = socket.create_connection(("127.0.0.1", port), timeout=env.args.timeout)
        s = ctx.wrap_socket(raw, server_hostname="localhost")
        if s.version() != name or s.selected_alpn_protocol() != "echo":
            raise Failed("python client %s: %s %s" % (name, s.version(), s.selected_alpn_protocol()))
        for n in SIZES:
            data = block(n)
            got = bytearray()
            # by turns, for an SSLSocket cannot be read and written at the same time
            for i in range(0, n, 8192):
                part = data[i:i + 8192]
                s.sendall(part)
                got += recv_all(s, len(part))
            if got != data:
                raise Failed("python client %s: block of %d differs" % (name, n))
        s.unwrap().close()
        out, err = p.communicate(timeout=env.args.timeout)
    finally:
        if p.poll() is None:
            p.kill()
    if p.returncode:
        raise Failed("python client %s: server exit %d\n%s" % (name, p.returncode, err[-2000:]))
    compare("python client " + name, out, "%s protocol echo\nserver: done\n" % name)


def case_python(env):
    versions = [(ssl.TLSVersion.TLSv1_3, "TLSv1.3"), (ssl.TLSVersion.TLSv1_2, "TLSv1.2")]
    for v, name in versions:
        python_server(env, v, name)
        python_client(env, v, name)


def case_rekey(env):
    for version in ("TLSv1.2", "TLSv1.3"):
        p = subprocess.Popen(env.peer_cmd("rekey", "server.p12", PASSWORD, version), cwd=env.certs,
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            port = p.stdout.readline().split()[1]
            code, out, err = env.run("client", port)
            pout, perr = p.communicate(timeout=env.args.timeout)
        finally:
            if p.poll() is None:
                p.kill()
        if code:
            raise Failed("rekey %s: exit %d\n%s\n%s\npeer: %s" % (version, code, out[-1000:], err[-2000:], perr[-2000:]))
        compare("rekey " + version, out, client_output(version, "NIL"))
        total = sum(SIZES)
        compare("rekey peer " + version, pout, "bytes %d handshake again true\n" % total)


CASES = {"local": case_local, "python": case_python, "rekey": case_rekey}


def main():
    jdk = os.environ.get("JDK25")
    ap = argparse.ArgumentParser()
    ap.add_argument("cases", nargs="*")
    ap.add_argument("--java", default=str(Path(jdk) / "bin" / "java") if jdk else "java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--timeout", type=int, default=120)
    ap.add_argument("--speed", action="store_true")
    args = ap.parse_args()
    if sys.platform == "win32" and not args.java.endswith(".exe") and Path(args.java + ".exe").exists():
        args.java += ".exe"
    names = args.cases or list(CASES)
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="tls_"))
    work.mkdir(parents=True, exist_ok=True)
    env = Env(args, work)
    failed = 0
    t0 = time.time()
    try:
        env.make_certs()
        env.compile()
        for name in names:
            try:
                CASES[name](env)
                print("PASS      " + name, flush=True)
            except (Failed, subprocess.TimeoutExpired, OSError, ssl.SSLError) as e:
                failed += 1
                print("FAIL      %s  %s" % (name, e), flush=True)
        if args.speed:
            code, out, err = env.run("speed")
            print(out + err, end="", flush=True)
            failed += 1 if code else 0
    except Failed as e:
        failed += 1
        print("FAIL      setup  %s" % e, flush=True)
    finally:
        if args.keep or args.work:
            print("work: %s" % work)
        else:
            shutil.rmtree(work, ignore_errors=True)
    print("failed %d, %.0fs" % (failed, time.time() - t0))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
