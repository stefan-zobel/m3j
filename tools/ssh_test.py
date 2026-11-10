#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Tests of SSH and SFTP in m3j, against a server of asyncssh (an independent
implementation in Python) that runs in this process on the loopback address,
and against the sftp-server of OpenSSH. They need no connection to the
internet.

  python3 tools/ssh_test.py [CASE]... [--java JAVA] [--jar JAR]
                            [--runtime JAR] [--work DIR] [--keep]
                            [--timeout SECONDS]

The cases:

  jsch     the JSch of m3j directly (tests/ssh/Probe.java): each key
           exchange, host key algorithm, cipher and MAC that it offers,
           one at a time, with a command and SFTP; the defaults; servers
           that offer only algorithms that were left out fail cleanly;
           ML-KEM-768 (unknown to asyncssh) only against the JDK
  algos    the library ssh (tests/ssh/src/Main.m3) against servers that
           offer one key exchange, host key, cipher or MAC each, and
           servers that offer only what was left out
  hostkey  known hosts: plain, hashed by ssh-keygen -H, another key,
           @revoked, none; AcceptNew twice; fingerprints against
           ssh-keygen -l
  auth     password, keyboard-interactive (a prompt with and without
           "password:", which JSch answers itself) and keys made by ssh-keygen
           (Ed25519, ECDSA, RSA; OpenSSH, PEM and PKCS#8; with and without
           a passphrase), from a file and as text, and the failures
  exec     exit status, signals, 10 MB through cat, standard output and
           error apart, limits, a broken connection, alerts, timeouts,
           many commands at the same time
  sftp     against the server of asyncssh and against sftp-server of
           OpenSSH: sizes around the blocks of 32 KB, offsets, appending,
           all operations, errors, names with umlauts, Busy, alerts,
           timeouts and a connection that breaks in a read

Without arguments all cases run. JAVA defaults to $JDK25/bin/java, else java
from the PATH; JAR to m3j.jar and the runtime to m3j-runtime.jar in the root
of m3j. The Python module asyncssh must be installed; ssh-keygen and
sftp-server of OpenSSH are taken from Git for Windows on Windows and from
the PATH or /usr/lib/openssh elsewhere.
"""

import argparse
import asyncio
import functools
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
PASSPHRASE = "pass phrase"

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

# sizes of files around the blocks of 32 KB in which SFTP moves them
SIZES = [0, 1, 32767, 32768, 32769, 65535, 65536, 65537, 1048576]

BLOCK = bytes((i * 7 + i // 256) % 256 for i in range(65536))


@functools.lru_cache(maxsize=None)
def pattern(n):
    """The bytes of the pattern of the test program, (7i + i DIV 256) MOD 256, which repeats every 64 KB."""
    return (BLOCK * (n // len(BLOCK) + 1))[:n]


def fnv(b):
    h = 0x811C9DC5
    for x in b:
        h = ((h ^ x) * 16777619) & 0xFFFFFFFF
    return h


@functools.lru_cache(maxsize=None)
def summary(b):
    """Length and hash, as the test program shows data."""
    return "%d %d" % (len(b), fnv(b))


def summary_bytes(b):
    return "%s %s" % (summary(b), b[:1000].hex() if b else "-")


def h16(t):
    return "-" if t is None else t.encode("utf-16-be").hex()


def from_h16(t):
    return bytes.fromhex(t).decode("utf-16-be")


class Failed(Exception):
    pass


def check(what, got, want):
    if got != want:
        raise Failed("%s:\n  got  %r\n  want %r" % (what, got, want))


def tool(name):
    """A program of OpenSSH: from Git for Windows on Windows, else from the PATH or /usr/lib/openssh."""
    if sys.platform == "win32":
        for base in (os.environ.get("ProgramFiles", r"C:\Program Files"), r"C:\Program Files"):
            for sub in ("usr/bin", "usr/lib/ssh"):
                p = Path(base) / "Git" / sub / (name + ".exe")
                if p.exists():
                    return str(p)
    p = shutil.which(name)
    if p:
        return p
    for d in ("/usr/lib/openssh", "/usr/libexec/openssh", "/usr/libexec"):
        if Path(d, name).exists():
            return str(Path(d, name))
    raise Failed("%s of OpenSSH is missing" % name)


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

    def start(self, auth=None, sftp="asyncssh", keys=None, root=None, **options):
        """A new server; returns its port. auth has password, kbdint (bools),
        prompt (of kbdint, default "Password: ") and keys (the public data
        of the keys of users); sftp is asyncssh
        or openssh, with the directory root (default self.root)."""
        auth = auth or {"password": True}
        accepted = set(auth.get("keys", ()))

        class Auth(asyncssh.SSHServer):
            def begin_auth(self, username):
                return True

            def password_auth_supported(self):
                return auth.get("password", False)

            def validate_password(self, username, password):
                return username == USER and password == PASSWORD

            def kbdint_auth_supported(self):
                return auth.get("kbdint", False)

            def get_kbdint_challenge(self, username, lang, submethods):
                return "", "", "", [(auth.get("prompt", "Password: "), False)]

            def validate_kbdint_response(self, username, responses):
                return username == USER and list(responses) == [PASSWORD]

            def public_key_auth_supported(self):
                return bool(accepted)

            def validate_public_key(self, username, key):
                return username == USER and key.public_data in accepted

            def session_requested(self):
                # only without a process_factory, for the sftp-server of OpenSSH
                return OpenSshSftp()

        root = str(root or self.root)

        class Sftp(asyncssh.SFTPServer):
            """The SFTP server of asyncssh, with files that are slow to read
            (slow.bin), that break the connection (cut.bin) and that may not
            be touched (forbidden*)."""

            def __init__(self, chan):
                super().__init__(chan, chroot=root)
                self.names = {}

            def forbid(self, path):
                if b"forbidden" in path:
                    raise asyncssh.SFTPPermissionDenied("forbidden")

            def open(self, path, pflags, attrs):
                self.forbid(path)
                f = super().open(path, pflags, attrs)
                self.names[id(f)] = path
                return f

            def stat(self, path):
                self.forbid(path)
                return super().stat(path)

            def lstat(self, path):
                self.forbid(path)
                return super().lstat(path)

            def remove(self, path):
                self.forbid(path)
                return super().remove(path)

            async def read(self, f, offset, size):
                name = self.names.get(id(f), b"")
                if name.endswith(b"slow.bin") and offset > 0:
                    await asyncio.sleep(3)
                if name.endswith(b"cut.bin") and offset >= 200000:
                    self.connection.abort()
                    await asyncio.sleep(1)
                return super().read(f, offset, size)

        class OpenSshSftp(asyncssh.SSHServerSession):
            """The subsystem sftp served by the sftp-server of OpenSSH in
            the directory root; asyncssh routes a subsystem sftp only to its
            own server, so this is a session of its lower layer."""

            def connection_made(self, chan):
                self.chan = chan
                self.proc = None
                self.pending = []
                self.eof = False

            def subsystem_requested(self, subsystem):
                return subsystem == "sftp"

            def session_started(self):
                asyncio.ensure_future(self.run())

            async def run(self):
                self.proc = await asyncio.create_subprocess_exec(
                    tool("sftp-server"), "-d", root.replace("\\", "/"), stdin=asyncio.subprocess.PIPE,
                    stdout=asyncio.subprocess.PIPE)
                for data in self.pending:
                    self.proc.stdin.write(data)
                self.pending = None
                if self.eof:
                    self.proc.stdin.close()
                while True:
                    data = await self.proc.stdout.read(65536)
                    if not data:
                        break
                    self.chan.write(data)
                self.chan.exit(await self.proc.wait())

            def data_received(self, data, datatype):
                if self.pending is not None:
                    self.pending.append(data)
                else:
                    self.proc.stdin.write(data)

            def eof_received(self):
                self.eof = True
                if self.pending is None:
                    self.proc.stdin.close()
                return False

            def connection_lost(self, exc):
                if self.proc is not None and self.proc.returncode is None:
                    self.proc.kill()

        async def process(p):
            w = (p.command or "").split()
            cmd, arg = (w[0] if w else ""), (w[1] if len(w) > 1 else "0")
            if cmd == "echo":
                p.stdout.write((" ".join(w[1:]) + "\n").encode())
                p.exit(0)
            elif cmd == "cat":
                while True:
                    data = await p.stdin.read(65536)
                    if not data:
                        break
                    p.stdout.write(data)
                    await p.stdout.drain()
                p.exit(0)
            elif cmd == "exit":
                p.exit(int(arg))
            elif cmd in ("out", "err", "both"):
                data = pattern(int(arg))
                if len(w) > 2:
                    await asyncio.sleep(float(w[2]))
                for i in range(0, len(data), 32768):
                    if cmd != "err":
                        p.stdout.write(data[i:i + 32768])
                    if cmd != "out":
                        p.stderr.write(data[i:i + 32768])
                    await p.stdout.drain()
                    await p.stderr.drain()
                p.exit(0)
            elif cmd == "sleep":
                await asyncio.sleep(float(arg))
                p.exit(0)
            elif cmd == "kill":
                p.exit_with_signal(arg)
            elif cmd == "cut":
                p.stdout.write(pattern(int(arg)))
                await p.stdout.drain()
                await asyncio.sleep(0.2)
                p.channel.get_connection().abort()
            elif cmd == "close":
                p.channel.close()
            else:
                p.stderr.write(b"unknown command\n")
                p.exit(127)

        async def start():
            return await asyncssh.create_server(
                Auth, "127.0.0.1", 0, server_host_keys=keys or self.keys,
                process_factory=process if sftp == "asyncssh" else None,
                sftp_factory=Sftp if sftp == "asyncssh" else None, encoding=None, **options)

        s = self.call(start())
        self.servers.append(s)
        return s.sockets[0].getsockname()[1]

    def known_hosts(self, port, path, keys=None):
        lines = []
        for k in keys or self.keys:
            alg, blob = k.export_public_key("openssh").decode().split()[:2]
            lines.append("[127.0.0.1]:%d %s %s" % (port, alg, blob))
        path.write_text("\n".join(lines) + "\n", encoding="ascii")

    def close(self):
        async def stop():
            for s in self.servers:
                s.close()
            for s in self.servers:
                try:
                    await asyncio.wait_for(s.wait_closed(), 5)
                except asyncio.TimeoutError:
                    pass
        self.call(stop())
        self.loop.call_soon_threadsafe(self.loop.stop)
        self.thread.join(10)


class Program:
    """The test program, which answers each request with a line."""

    def __init__(self, env):
        self.env = env
        self.p = subprocess.Popen([env.args.java, "-cp", env.cp, "m3.Main"], cwd=env.work, stdin=subprocess.PIPE,
                                  stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="ascii")
        self.timer = threading.Timer(env.args.timeout, self.p.kill)
        self.timer.start()

    def ask(self, line):
        self.p.stdin.write(line + "\n")
        self.p.stdin.flush()
        answer = self.p.stdout.readline()
        if not answer:
            raise Failed("the program ended at %r: %s" % (line, self.p.stderr.read()[-2000:]))
        return answer.rstrip("\n")

    def ok(self, line, want=None):
        got = self.ask(line)
        if not got.startswith("ok") or want is not None and got != "ok" + ("" if want == "" else " " + want):
            raise Failed("%s:\n  got  %s\n  want ok %s" % (line[:200], got[:400], want if want is not None else "..."))
        return got[3:]

    def error(self, line, atom):
        got = self.ask(line)
        if not got.startswith("error " + atom + " ") and got != "error " + atom:
            raise Failed("%s:\n  got  %s\n  want error %s" % (line[:200], got[:400], atom))
        return got

    def connect(self, port, user=USER, password=PASSWORD, keyfile=None, keytext=None, passphrase=None, check="k",
                known=None, fingerprint=None, timeout=30, keepalive=0):
        return "connect %d %s %s %s %s %s %s %s %s %.1f %.1f" % (
            port, h16(user), h16(password), h16(keyfile and str(keyfile)), h16(keytext and str(keytext)),
            h16(passphrase), check, h16(known and str(known)), h16(fingerprint), timeout, keepalive)

    def close(self):
        self.p.stdin.close()
        rest = self.p.stdout.read()
        code = self.p.wait()
        self.timer.cancel()
        err = self.p.stderr.read()
        if code or rest or err.strip():
            raise Failed("the program: exit %d, %r, %s" % (code, rest[:200], err[-2000:]))


class Env:
    def __init__(self, args, work):
        self.args = args
        self.work = work
        self.server = Server(work)
        self.classes = work / "classes"
        self.cp = str(self.classes) + os.pathsep + args.runtime
        self.compiled = False

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

    def program(self):
        if not self.compiled:
            cmd = [self.args.java, "-Xss64m", "-cp", self.args.jar, "m3j.jvm.Compile", str(TESTS / "src" / "Main.m3"),
                   "-d", str(self.classes)]
            r = subprocess.run(cmd, cwd=self.work, capture_output=True, text=True, stdin=subprocess.DEVNULL)
            if r.returncode or r.stdout.strip() or r.stderr.strip():
                raise Failed("compile:\n" + r.stdout + r.stderr)
            self.compiled = True
        return Program(self)

    def known(self, port, name, keys=None):
        path = self.work / ("%s_%d" % (name, port))
        self.server.known_hosts(port, path, keys)
        return path


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


ECHO = b"probe\n".hex()


def case_algos(env):
    m = env.program()
    ed = env.server.keys[0].get_fingerprint("sha256")
    servers = [{"kex_algs": [k]} for k in KEX]
    # a server offers the algorithms of its keys, those of RSA as far as signature_algs allows
    servers += [{"keys": [env.server.keys[min(i, 4)]], "signature_algs": [h]} for i, h in enumerate(HOST_KEYS)]
    servers += [{"encryption_algs": [c]} for c in CIPHERS]
    servers += [{"mac_algs": [a], "encryption_algs": ["aes128-ctr"]} for a in MACS]
    for options in servers:
        port = env.server.start(**options)
        try:
            m.ok(m.connect(port, known=env.known(port, "known")))
            m.ok("echo", ECHO)
            m.ok("close")
        except Failed as e:
            raise Failed("server with %s: %s" % (options, e))
    # the defaults, with ML-KEM offered first: the key of Ed25519
    port = env.server.start()
    m.ok(m.connect(port, known=env.known(port, "known")), ed)
    m.ok("hostkey", ed)
    m.ok("echo", ECHO)
    m.ok("close")
    for options in LEFT_OUT:
        port = env.server.start(**options)
        got = m.error(m.connect(port, known=env.known(port, "known")), "SSH.Failure")
        check("server with %s" % options, got, "error SSH.Failure the server has no algorithm in common with the client")
    m.close()


def ssh_keygen(*args):
    r = subprocess.run([tool("ssh-keygen"), *args], capture_output=True, text=True, stdin=subprocess.DEVNULL)
    if r.returncode:
        raise Failed("ssh-keygen %s: %s" % (" ".join(args), r.stderr))
    return r.stdout


def case_hostkey(env):
    m = env.program()
    port = env.server.start()
    ed = env.server.keys[0]
    fp = ed.get_fingerprint("sha256")
    # the fingerprint as ssh-keygen -l shows it
    pub = env.work / "host_ed25519.pub"
    pub.write_bytes(ed.export_public_key("openssh"))
    check("ssh-keygen -l", ssh_keygen("-l", "-f", str(pub)).split()[1], fp)
    plain = env.known(port, "plain")
    m.ok(m.connect(port, known=plain), fp)
    m.ok("close")
    # hashed by ssh-keygen -H
    hashed = env.known(port, "hashed")
    ssh_keygen("-H", "-f", str(hashed))
    if "[127.0.0.1]" in hashed.read_text():
        raise Failed("ssh-keygen -H did not hash the names")
    m.ok(m.connect(port, known=hashed), fp)
    m.ok("close")
    # only a key of ECDSA known: the client asks for that type
    ecdsa = env.known(port, "ecdsa", [env.server.keys[1]])
    m.ok(m.connect(port, known=ecdsa), env.server.keys[1].get_fingerprint("sha256"))
    m.ok("close")
    # another key of Ed25519
    other = asyncssh.generate_private_key("ssh-ed25519")
    changed = env.known(port, "changed", [other])
    check("changed", m.error(m.connect(port, known=changed), "SSH.HostKeyChanged"),
          "error SSH.HostKeyChanged the key of the host is not the known one: " + fp)
    # revoked
    revoked = env.known(port, "revoked")
    with open(revoked, "a", encoding="ascii") as f:
        f.write("@revoked [127.0.0.1]:%d %s" % (port, ed.export_public_key("openssh").decode()))
    check("revoked", m.error(m.connect(port, known=revoked), "SSH.HostKeyRevoked"),
          "error SSH.HostKeyRevoked the key of the host is revoked: " + fp)
    # not known: an empty file, no file
    empty = env.work / "empty_known"
    empty.write_text("")
    check("empty", m.error(m.connect(port, known=empty), "SSH.HostKeyUnknown"),
          "error SSH.HostKeyUnknown the key of the host is not known: " + fp)
    m.error(m.connect(port, known=env.work / "none" / "known"), "SSH.HostKeyUnknown")
    # AcceptNew: a new file in a new directory, then the same key again
    new = env.work / "new" / "dir" / "known_hosts"
    m.ok(m.connect(port, known=new, check="n"), fp)
    m.ok("close")
    want = "[127.0.0.1]:%d %s\n" % (port, " ".join(ed.export_public_key("openssh").decode().split()[:2]))
    check("known hosts after AcceptNew", new.read_text(), want)
    m.ok(m.connect(port, known=new, check="n"), fp)
    m.ok("close")
    check("known hosts after AcceptNew twice", new.read_text(), want)
    # a line is added after one without its end of line
    nonl = env.work / "nonl_known"
    nonl.write_text("# no end of line")
    m.ok(m.connect(port, known=nonl, check="n"), fp)
    m.ok("close")
    check("known hosts with a line added", nonl.read_text(), "# no end of line\n" + want)
    # AcceptNew does not accept another key
    m.error(m.connect(port, known=changed, check="n"), "SSH.HostKeyChanged")
    # fingerprints
    m.ok(m.connect(port, check="f", fingerprint=fp), fp)
    m.ok("close")
    m.error(m.connect(port, check="f", fingerprint=other.get_fingerprint("sha256")), "SSH.HostKeyChanged")
    m.error(m.connect(port, check="f"), "SSH.HostKeyUnknown")
    m.close()


def make_key(env, name, kind, fmt, passphrase):
    """A key of a user made by ssh-keygen; its file and the public data."""
    path = env.work / "keys" / name
    path.parent.mkdir(exist_ok=True)
    args = ["-q", "-t", kind, "-f", str(path), "-N", passphrase or "", "-C", name]
    if kind == "ecdsa":
        args += ["-b", "384"]
    if kind == "rsa":
        args += ["-b", "3072"]
    if fmt != "openssh":
        args += ["-m", fmt]
    ssh_keygen(*args)
    return path, asyncssh.read_public_key(str(path) + ".pub").public_data


def case_auth(env):
    m = env.program()
    keys = []
    for kind in ("ed25519", "ecdsa", "rsa"):
        for fmt in ("openssh", "PEM", "PKCS8"):
            if kind == "ed25519" and fmt != "openssh":
                continue
            for passphrase in (None, PASSPHRASE):
                keys.append(("%s_%s_%s" % (kind, fmt, "enc" if passphrase else "plain"), kind, fmt, passphrase))
    made = {name: make_key(env, name, kind, fmt, passphrase) for name, kind, fmt, passphrase in keys}
    accepted = [pub for _, pub in made.values()]
    port = env.server.start(auth={"keys": accepted})
    known = env.known(port, "known")
    for name, kind, fmt, passphrase in keys:
        path = made[name][0]
        try:
            m.ok(m.connect(port, password=None, keyfile=path, passphrase=passphrase, known=known))
            m.ok("echo", ECHO)
            m.ok("close")
            m.ok(m.connect(port, password=None, keytext=path, passphrase=passphrase, known=known))
            m.ok("close")
            if passphrase:
                check(name + " without a passphrase",
                      m.error(m.connect(port, password=None, keyfile=path, known=known), "SSH.BadKey"),
                      "error SSH.BadKey the key needs a passphrase")
                check(name + " with a wrong passphrase",
                      m.error(m.connect(port, password=None, keyfile=path, passphrase="wrong", known=known),
                              "SSH.BadKey"),
                      "error SSH.BadKey the passphrase does not fit the key")
        except Failed as e:
            raise Failed("key %s: %s" % (name, e))
    # a key that the server does not accept, a file that is no key, no file
    stranger, _ = make_key(env, "stranger", "ed25519", "openssh", None)
    m.error(m.connect(port, password=None, keyfile=stranger, known=known), "SSH.AuthFailed")
    nokey = env.work / "keys" / "nokey"
    nokey.write_text("no key\n")
    m.error(m.connect(port, password=None, keyfile=nokey, known=known), "SSH.BadKey")
    m.error(m.connect(port, password=None, keyfile=env.work / "keys" / "missing", known=known), "SSH.BadKey")
    # passwords: right, wrong, a wrong user; keyboard-interactive alone
    port = env.server.start(auth={"password": True})
    known = env.known(port, "known")
    m.ok(m.connect(port, known=known))
    m.ok("close")
    m.error(m.connect(port, password="wrong", known=known), "SSH.AuthFailed")
    m.error(m.connect(port, user="other", known=known), "SSH.AuthFailed")
    m.error(m.connect(port, password=None, known=known), "SSH.AuthFailed")
    port = env.server.start(auth={"kbdint": True})
    known = env.known(port, "known")
    m.ok(m.connect(port, known=known))
    m.ok("echo", ECHO)
    m.ok("close")
    m.error(m.connect(port, password="wrong", known=known), "SSH.AuthFailed")
    # a prompt without "password:", which JSch leaves to the bridge
    port = env.server.start(auth={"kbdint": True, "prompt": "Code: "})
    known = env.known(port, "known")
    m.ok(m.connect(port, known=known))
    m.ok("echo", ECHO)
    m.ok("close")
    m.error(m.connect(port, password="wrong", known=known), "SSH.AuthFailed")
    # a key of a user and a password: the key that the server does not accept, then the password
    port = env.server.start(auth={"password": True, "keys": accepted})
    known = env.known(port, "known")
    m.ok(m.connect(port, keyfile=stranger, known=known))
    m.ok("close")
    # nobody listens
    m.error(m.connect(free_port(), known=known), "SSH.Refused")
    m.close()


def free_port():
    import socket
    s = socket.socket()
    s.bind(("127.0.0.1", 0))
    port = s.getsockname()[1]
    s.close()
    return port


def run(m, command, maxout=16777216, timeout=0.0, data="-"):
    return "run %d %.1f %s %s" % (maxout, timeout, data, h16(command))


def case_exec(env):
    m = env.program()
    port = env.server.start()
    known = env.known(port, "known")
    m.ok(m.connect(port, known=known))
    none = summary_bytes(b"")
    for status in (0, 1, 2, 127, 255):
        m.ok(run(m, "exit %d" % status), "%d %s %s" % (status, none, none))
    m.ok(run(m, "nosuch"), "127 %s %s" % (none, summary_bytes(b"unknown command\n")))
    m.ok(run(m, "kill TERM"), "-1 %s %s" % (none, none))
    m.ok(run(m, "close"), "-1 %s %s" % (none, none))
    m.ok("proc seq 16777216 - " + h16("kill KILL"), "-1 KILL 0 %d 0 %d" % (fnv(b""), fnv(b"")))
    big = pattern(10485760)
    m.ok(run(m, "cat", data="p10485760"), "0 %s %s" % (summary_bytes(big), summary_bytes(b"")))
    m.ok("proc par 16777216 p10485760 " + h16("cat"), "0 - %s %s" % (summary(big), summary(b"")))
    m.ok("proc seq 16777216 h616263 " + h16("cat"), "0 - %s %s" % (summary(b"abc"), summary(b"")))
    both = pattern(100000)
    m.ok(run(m, "both 100000"), "0 %s %s" % (summary_bytes(both), summary_bytes(both)))
    m.ok("proc par 16777216 - " + h16("both 3000000"),
         "0 - %s %s" % (summary(pattern(3000000)), summary(pattern(3000000))))
    # an error output that is never read does not hold up the session
    # and once it is closed, it is dropped instead of filling the buffer
    m.ok("proc outonly 1000000 - " + h16("err 3000000 1"), "0 - %s %s" % (summary(b""), summary(b"")))
    # limits
    m.error(run(m, "out 5000", maxout=1000), "SSH.TooLarge")
    m.error(run(m, "err 5000", maxout=1000), "SSH.TooLarge")
    m.ok(run(m, "out 1000", maxout=1000), "0 %s %s" % (summary_bytes(pattern(1000)), summary_bytes(b"")))
    m.error("proc noread 100000 - " + h16("out 1000000"), "SSH.TooLarge")
    m.ok("echo", ECHO)
    # time: a timeout, alerts in run, in a read of the output, in wait
    t0 = time.time()
    m.error(run(m, "sleep 5", timeout=1.0), "SSH.Timeout")
    if time.time() - t0 > 4:
        raise Failed("the timeout of run took %.1f s" % (time.time() - t0))
    m.ok("alertrun 0.5 " + h16("sleep 5"), "alerted")
    m.ok("alertread 0.5 " + h16("sleep 5"), "alerted")
    m.ok("alertwait 0.5 " + h16("sleep 5"), "alerted")
    if time.time() - t0 > 8:
        raise Failed("the alerts took %.1f s" % (time.time() - t0))
    m.ok("echo", ECHO)
    m.ok("many 20", "20")
    # a connection that breaks: in run, in a read of the output
    m.error(run(m, "cut 300000"), "SSH.Disconnected")
    m.error(run(m, "exit 0"), "SSH.Disconnected")
    m.ok(m.connect(port, known=known))
    m.error("proc seq 16777216 - " + h16("cut 300000"), "SSH.Disconnected")
    m.ok(m.connect(port, known=known))
    m.error("proc nowait 16777216 - " + h16("cut 300000"), "SSH.Disconnected")
    m.ok(m.connect(port, known=known))
    m.ok("proc nowait 16777216 - " + h16("out 300000"), summary(pattern(300000)))
    m.ok(m.connect(port, known=known))
    m.ok("echo", ECHO)
    m.ok("close")
    m.close()


def sftp_common(m, root, umlaut):
    """The operations that both servers have; root is their directory."""
    for n in SIZES:
        (root / ("f%d" % n)).write_bytes(pattern(n))
        m.ok("sget %s 0" % h16("f%d" % n), summary(pattern(n)))
        m.ok("sput %s 644 0 p%d" % (h16("u%d" % n), n))
        check("put of %d bytes" % n, (root / ("u%d" % n)).read_bytes() == pattern(n), True)
    m.ok("sget %s 100000" % h16("f1048576"), summary(pattern(1048576)[100000:]))
    m.ok("sget %s 2000000" % h16("f1048576"), summary(b""))
    m.ok("sput %s 644 0 p1000" % h16("app"))
    m.ok("sput %s 644 1 h414243" % h16("app"))
    check("append", (root / "app").read_bytes(), pattern(1000) + b"ABC")
    m.ok("sput %s 644 0 h5a" % h16("app"))
    check("overwrite", (root / "app").read_bytes(), b"Z")
    # attributes
    st = os.stat(root / "f65537")
    got = m.ok("sstat " + h16("f65537")).split()
    check("stat", (got[0], got[1], got[6]), ("0", "65537", str(int(st.st_mtime))))
    got = m.ok("sstat " + h16(".")).split()
    check("stat of a directory", got[0], "1")
    m.ok("stimes %s 1000000000 1700000000" % h16("f1"))
    st = os.stat(root / "f1")
    check("setTimes", (int(st.st_atime), int(st.st_mtime)), (1000000000, 1700000000))
    got = m.ok("sstat " + h16("f1")).split()
    check("stat after setTimes", (got[5], got[6]), ("1000000000", "1700000000"))
    # directories and names
    m.ok("smkdir %s 755" % h16("d"))
    check("mkdir", (root / "d").is_dir(), True)
    m.error("smkdir %s 755" % h16("d"), "SFTP.Exists")
    m.ok("sput %s 644 0 h4142" % h16("d/" + umlaut))
    names = sorted(from_h16(e.split(":")[0]) for e in m.ok("slist " + h16("d")).split()[1:])
    check("list", names, [umlaut])
    check("name on the server", sorted(os.listdir(root / "d")), [umlaut])
    m.ok("sget %s 0" % h16("d/" + umlaut), summary(b"AB"))
    m.ok("srename %s %s" % (h16("d/" + umlaut), h16("d/x")))
    check("rename", sorted(os.listdir(root / "d")), ["x"])
    m.error("srmdir " + h16("d"), "SSH.Failure")
    m.ok("sremove " + h16("d/x"))
    m.ok("srmdir " + h16("d"))
    check("rmdir", (root / "d").exists(), False)
    # patterns are not expanded: a.txt is there, *.txt is not (a name
    # that Windows does not allow at all)
    for line in ("sstat " + h16("*.txt"), "sremove " + h16("?.txt")):
        got = m.ask(line)
        if not (got.startswith("error SFTP.NoSuchFile ")
                or sys.platform == "win32" and got.startswith("error SSH.Failure ")):
            raise Failed("%s: %s" % (line, got))
    check("a.txt after *.txt", (root / "a.txt").exists(), True)
    # errors
    m.error("sget %s 0" % h16("nosuch"), "SFTP.NoSuchFile")
    m.error("sstat " + h16("nosuch"), "SFTP.NoSuchFile")
    m.error("sput %s 644 0 h41" % h16("nodir/x"), "SFTP.NoSuchFile")
    m.error("slist " + h16("a.txt"), "SSH.Failure")
    m.error("sget %s 0" % h16("."), "SSH.Failure")
    # one stream at a time
    m.ok("sbusy " + h16("f1048576"), "SSH.Busy")


def case_sftp(env):
    m = env.program()
    port = env.server.start()
    known = env.known(port, "known")
    root = env.server.root
    umlaut = "M\u00fcller \u00e4\u20ac.txt"
    m.ok(m.connect(port, known=known))
    m.ok("sopen")
    sftp_common(m, root, umlaut)
    m.ok("srealpath " + h16("d/../a.txt"), h16("/a.txt"))
    m.error("sstat " + h16("forbidden"), "SFTP.PermissionDenied")
    m.error("sget %s 0" % h16("forbidden.txt"), "SFTP.PermissionDenied")
    (root / "ro").write_bytes(b"x")
    m.ok("schmod %s 444" % h16("ro"))
    check("chmod", os.access(root / "ro", os.W_OK), False)
    m.ok("schmod %s 644" % h16("ro"))
    check("chmod", os.access(root / "ro", os.W_OK), True)
    try:
        os.symlink("a.txt", root / "probe-link")
        links = True
        os.remove(root / "probe-link")
    except OSError:
        links = False
    if links:
        m.ok("ssymlink %s %s" % (h16("a.txt"), h16("link")))
        check("symlink", os.readlink(root / "link"), "a.txt")
        m.ok("sreadlink " + h16("link"), h16("a.txt"))
        got = m.ok("slstat " + h16("link")).split()
        check("lstat of a link", got[0], "2")
        got = m.ok("sstat " + h16("link")).split()
        check("stat of a link", got[0], "0")
        m.error("ssymlink %s %s" % (h16("b.txt"), h16("link")), "SFTP.Exists")
        m.ok("sremove " + h16("link"))
    # an alert and a timeout end only the wait
    (root / "slow.bin").write_bytes(pattern(1000000))
    m.ok("salert %s 0.5" % h16("slow.bin"), "alerted")
    m.ok("sstat " + h16("."))
    m.ok("sclose")
    m.ok(m.connect(port, known=known, timeout=1))
    m.ok("sopen")
    m.error("sget %s 0" % h16("slow.bin"), "SSH.Timeout")
    m.ok("pause 4")
    m.ok("sstat " + h16("."))
    m.ok("sclose")
    # two sessions of SFTP on one session of SSH
    m.ok(m.connect(port, known=known))
    m.ok("sopen")
    m.ok("sopen")
    m.ok("sget %s 0" % h16("f65536"), summary(pattern(65536)))
    # a connection that breaks in a read
    (root / "cut.bin").write_bytes(pattern(1000000))
    m.error("sget %s 0" % h16("cut.bin"), "SSH.Disconnected")
    m.error("sstat " + h16("."), "SSH.Disconnected")
    # sftp-server of OpenSSH, in a directory of its own
    root = env.work / "openssh"
    root.mkdir()
    for name in ("a.txt", "b.txt"):
        (root / name).write_bytes(b"x")
    port = env.server.start(sftp="openssh", root=root)
    known = env.known(port, "known")
    m.ok(m.connect(port, known=known))
    m.ok("sopen")
    sftp_common(m, root, umlaut)
    m.ok("sclose")
    m.ok("close")
    m.close()


CASES = {"jsch": case_jsch, "algos": case_algos, "hostkey": case_hostkey, "auth": case_auth, "exec": case_exec,
         "sftp": case_sftp}


def main():
    jdk = os.environ.get("JDK25")
    ap = argparse.ArgumentParser()
    ap.add_argument("cases", nargs="*")
    ap.add_argument("--java", default=str(Path(jdk) / "bin" / "java") if jdk else "java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
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
            t = time.time()
            try:
                CASES[name](env)
                if env.server.errors:
                    raise Failed("server: %s" % "; ".join(env.server.errors))
                print("PASS      %-8s %.0fs" % (name, time.time() - t), flush=True)
            except (Failed, subprocess.TimeoutExpired, OSError, asyncssh.Error) as e:
                failed += 1
                env.server.errors.clear()
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
