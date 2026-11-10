#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Tests of SSH and SFTP in m3j, against a server of asyncssh (an independent
implementation in Python) that runs in this process on the loopback address.
They need no connection to the internet.

  python3 tools/ssh_test.py [CASE]... [--java JAVA] [--runtime JAR]
                            [--work DIR] [--keep] [--timeout SECONDS]

The cases:

  jsch     the JSch of m3j directly (tests/ssh/Probe.java): each key
           exchange, host key algorithm, cipher and MAC that it offers,
           one at a time, with a command and SFTP; the defaults; servers
           that offer only algorithms that were left out fail cleanly;
           ML-KEM-768 (unknown to asyncssh) only against the JDK

Without arguments all cases run. JAVA defaults to $JDK25/bin/java, else java
from the PATH; JAR to m3j-runtime.jar in the root of m3j. The Python module
asyncssh must be installed.
"""

import argparse
import asyncio
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path

try:
    import asyncssh
except ImportError:
    asyncssh = None

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests" / "ssh"
USER = "m3j"
PASSWORD = "secret"

KEX = ["curve25519-sha256", "curve25519-sha256@libssh.org", "ecdh-sha2-nistp256", "ecdh-sha2-nistp384",
       "ecdh-sha2-nistp521", "diffie-hellman-group-exchange-sha256", "diffie-hellman-group16-sha512",
       "diffie-hellman-group18-sha512", "diffie-hellman-group14-sha256"]
HOST_KEYS = ["ssh-ed25519", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521",
             "rsa-sha2-256", "rsa-sha2-512"]
CIPHERS = ["aes128-gcm@openssh.com", "aes256-gcm@openssh.com", "aes128-ctr", "aes192-ctr", "aes256-ctr"]
MACS = ["hmac-sha2-256-etm@openssh.com", "hmac-sha2-512-etm@openssh.com", "hmac-sha1-etm@openssh.com",
        "hmac-sha2-256", "hmac-sha2-512", "hmac-sha1"]

# servers that offer only what was left out: a key exchange, a cipher, a MAC
LEFT_OUT = [{"kex_algs": ["diffie-hellman-group1-sha1"]},
            {"kex_algs": ["diffie-hellman-group14-sha1"]},
            {"kex_algs": ["curve448-sha512"]},
            {"encryption_algs": ["chacha20-poly1305@openssh.com"]},
            {"encryption_algs": ["aes128-cbc"]},
            {"encryption_algs": ["3des-cbc"]},
            {"mac_algs": ["hmac-md5"], "encryption_algs": ["aes128-ctr"]},
            {"mac_algs": ["hmac-sha2-256-96"], "encryption_algs": ["aes128-ctr"]}]


class Failed(Exception):
    pass


class Server:
    """SSH servers of asyncssh in a thread with its own event loop."""

    def __init__(self, work):
        self.work = work
        self.root = work / "sftp"
        self.root.mkdir(parents=True, exist_ok=True)
        for name in ("a.txt", "b.txt"):
            (self.root / name).write_bytes(b"x")
        self.loop = asyncio.new_event_loop()
        self.loop.set_exception_handler(self.loop_error)
        self.errors = []
        self.thread = threading.Thread(target=self.loop.run_forever, daemon=True)
        self.thread.start()
        self.keys = [asyncssh.generate_private_key(alg) for alg in
                     ("ssh-ed25519", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521")]
        self.keys.append(asyncssh.generate_private_key("ssh-rsa", key_size=3072))
        self.servers = []

    def loop_error(self, loop, context):
        """A client that resets its connection is no error of the server;
        anything else is kept for the report."""
        e = context.get("exception")
        if isinstance(e, (ConnectionResetError, ConnectionAbortedError, BrokenPipeError)):
            return
        self.errors.append(context.get("message", "") + ": " + repr(e))

    def call(self, coro):
        return asyncio.run_coroutine_threadsafe(coro, self.loop).result(60)

    def start(self, **options):
        """A new server; returns its port."""

        class Auth(asyncssh.SSHServer):
            def begin_auth(self, username):
                return True

            def password_auth_supported(self):
                return True

            def validate_password(self, username, password):
                return username == USER and password == PASSWORD

        async def process(p):
            if p.command == "echo probe":
                p.stdout.write("probe\n")
                p.exit(0)
            else:
                p.stderr.write("unknown command\n")
                p.exit(127)

        root = str(self.root)

        async def start():
            return await asyncssh.create_server(
                Auth, "127.0.0.1", 0, server_host_keys=self.keys, process_factory=process,
                sftp_factory=lambda chan: asyncssh.SFTPServer(chan, chroot=root), **options)

        server = self.call(start())
        self.servers.append(server)
        return server.sockets[0].getsockname()[1]

    def known_hosts(self, port, path):
        lines = []
        for k in self.keys:
            alg, blob = k.export_public_key("openssh").decode().split()[:2]
            lines.append("[127.0.0.1]:%d %s %s" % (port, alg, blob))
        path.write_text("\n".join(lines) + "\n", encoding="ascii")

    def close(self):
        async def stop():
            for s in self.servers:
                s.close()
                await s.wait_closed()
        self.call(stop())
        self.loop.call_soon_threadsafe(self.loop.stop)
        self.thread.join(10)


class Env:
    def __init__(self, args, work):
        self.args = args
        self.work = work
        self.server = Server(work)

    def probe(self, port, lines):
        known = self.work / ("known_hosts_%d" % port)
        self.server.known_hosts(port, known)
        r = subprocess.run([self.args.java, "-cp", self.args.runtime, str(TESTS / "Probe.java"), "127.0.0.1",
                            str(port), USER, PASSWORD, str(known)],
                           input="".join(line + "\n" for line in lines), capture_output=True, text=True,
                           timeout=self.args.timeout)
        if r.returncode or r.stderr.strip():
            raise Failed("Probe: exit %d\n%s" % (r.returncode, r.stderr[-2000:]))
        answers = r.stdout.splitlines()
        if len(answers) != len(lines):
            raise Failed("Probe: %d answers to %d requests" % (len(answers), len(lines)))
        return answers


def case_jsch(env):
    port = env.server.start()
    lines, wants = [], []
    for k in KEX:
        lines.append("%s - - -" % k)
        wants.append((k, None, None, None))
    for h in HOST_KEYS:
        lines.append("- %s - -" % h)
        wants.append((None, h, None, None))
    for c in CIPHERS:
        lines.append("- - %s -" % c)
        wants.append((None, None, c, None))
    for m in MACS:
        lines.append("- - aes128-ctr %s" % m)
        wants.append((None, None, "aes128-ctr", m))
    # the defaults: ML-KEM is offered first, asyncssh does not know it
    lines.append("- - - -")
    wants.append(("curve25519-sha256", "ssh-ed25519", "aes128-gcm@openssh.com", None))
    for line, want, got in zip(lines, wants, env.probe(port, lines)):
        f = got.split(" ")
        if f[0] != "ok":
            raise Failed("%s: %s" % (line, got))
        tail = got.split(" | ", 1)[1]
        if tail != "probe 0 | a.txt,b.txt":
            raise Failed("%s: command and SFTP: %s" % (line, got))
        for i, w in enumerate(want):
            if w is not None and f[1 + i] != w:
                raise Failed("%s: negotiated %s" % (line, got))
    for options in LEFT_OUT:
        p = env.server.start(**options)
        got = env.probe(p, ["- - - -"])[0]
        if not got.startswith("error m3j.jsch.JSchAlgoNegoFailException"):
            raise Failed("server with %s: %s" % (options, got))
    got = env.probe(port, ["mlkem"])[0]
    if got != "ok m3j.jsch.jce.MLKEM768 1184":
        raise Failed("ML-KEM-768: %s" % got)
    if env.server.errors:
        raise Failed("server: %s" % "; ".join(env.server.errors))


CASES = {"jsch": case_jsch}


def main():
    jdk = os.environ.get("JDK25")
    ap = argparse.ArgumentParser()
    ap.add_argument("cases", nargs="*")
    ap.add_argument("--java", default=str(Path(jdk) / "bin" / "java") if jdk else "java")
    ap.add_argument("--runtime", default=str(ROOT / "m3j-runtime.jar"))
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--timeout", type=int, default=300)
    args = ap.parse_args()
    if sys.platform == "win32" and not args.java.endswith(".exe") and Path(args.java + ".exe").exists():
        args.java += ".exe"
    if asyncssh is None:
        print("FAIL      setup  the Python module asyncssh is missing")
        return 1
    names = args.cases or list(CASES)
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="ssh_"))
    work.mkdir(parents=True, exist_ok=True)
    failed = 0
    t0 = time.time()
    env = None
    try:
        env = Env(args, work)
        for name in names:
            try:
                CASES[name](env)
                print("PASS      " + name, flush=True)
            except (Failed, subprocess.TimeoutExpired, OSError, asyncssh.Error) as e:
                failed += 1
                print("FAIL      %s  %s" % (name, e), flush=True)
    except Failed as e:
        failed += 1
        print("FAIL      setup  %s" % e, flush=True)
    finally:
        if env:
            env.server.close()
        if args.keep or args.work:
            print("work: %s" % work)
        else:
            shutil.rmtree(work, ignore_errors=True)
    print("failed %d, %.0fs" % (failed, time.time() - t0))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
