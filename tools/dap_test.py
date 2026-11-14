#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
"""Tests of the debugger of m3j: the description of each unit that the
compiler writes into its class for a debugger, and the debug adapter
(m3j.debug.dap.Main) in sessions of the Debug Adapter Protocol.

  python3 tools/dap_test.py [CASE]... [--java JAVA] [--jar JAR] [--work DIR]
                            [--keep] [--record] [--timeout SECONDS] [-v]

The program tests/dap/pkgs/debug is compiled with -Dm3j.maxMethod=300 (so
that its big procedures are split in chunks) into a jar, in its source
directory with the names of the files alone (so that the debugger finds
them through the source roots). The cases:

  info     m3j.debug.Dump prints the descriptions of the units of the
           program (Main, Shapes, IntStack) as Modula-3; the output must
           be tests/dap/expected/info.txt
  check    tests/dap/InfoCheck.java checks the descriptions against the
           classes of the program and of the jars that m3j compiles from
           Modula-3 next to JAR (m3build, m3bundle, stubgen, the language
           server, those of them that build.sh made): every method with
           lines is described, every named slot is a described variable,
           every variable in a frame is a field of the frame, every TRY
           has lines
  SESSION  each tests/dap/sessions/SESSION.json: the adapter (java -cp
           JAR m3j.debug.dap.Main, in the work directory) gets the
           requests initialize and launch (with the jar of the program,
           the work directory as cwd, the source directory as source
           root and the attributes of "launch" of the session), then the
           steps of the session, then disconnect if the program has not
           ended and no step has; the record must be tests/dap/expected/SESSION.json

--record writes the expected files (check them by hand). Without cases,
all run.

A session is an object with "steps", a list of

  {"send": REQUEST}           sends the request {"command": ...,
                              "arguments": ...} and waits for its
                              response; with "until": EVENT also for
                              that event (it may come first)
  {"raw": TEXT}               sends TEXT as the content of a message
  {"wait": EVENT}             waits for the event
  {"restart": true}           ends the adapter (its exit code is
                              recorded) and starts another one, which
                              gets the request initialize

and optionally "launch" (attributes added to those of launch, null
removes one), "start": false (no launch: the steps launch or attach),
"launches": false (the launch fails, so no event initialized comes),
"init": true (record the response to initialize too), "refuse": N (the
client refuses the first N requests runInTerminal of the adapter),
"debuggee": {"suspend": "y" or "n", "args": [...]} (the program that
{"$port": true} starts, for attach). In a request, {"$line": [FILE,
TEXT, DELTA]} stands for the number of the line (from 1) of the first
occurrence of TEXT in the file FILE of the program (or with <m3j>/, of
m3j), plus DELTA (0 if left out), {"$src": FILE} for the path of the
file, {"$program": true} for the jar of the program, {"$pkg": NAME} for
a fresh copy of the package tests/dap/pkgs/NAME in the work directory
(one for each session) and {"$port": true} for the port of the agent of
JDWP of the program that the test starts for the session (as a server,
with the options of "debuggee"; it prints the port).

The client runs the command of a request runInTerminal itself, with
its output captured, and answers with its process id. The record has,
for each step, the response first and then the events and requests of
the adapter in the order they came, but output; the messages without
their seq, the paths of the work directory as <work>, of the program as
<src>, of m3j as <m3j>, of the JDK as <jdk>, the address of JDWP as
<address>, the class path of a command as a list. At the end it has the
output of the program by category, the exit code of the adapter, and
the output and the exit code of each program that runInTerminal
started and of the program for attach.

JAVA defaults to $JDK25/bin/java, else java from the PATH.
"""

import argparse
import json
import os
import queue
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TESTS = ROOT / "tests" / "dap"
PKG = TESTS / "pkgs" / "debug" / "src"
SESSIONS = TESTS / "sessions"
EXPECTED = TESTS / "expected"
JARS = ["m3build.jar", "m3bundle.jar", "stubgen.jar", "m3_lang_server.jar"]
UNITS = ["Main", "Shapes", "IntStack"]
# the warnings that the program gives on purpose
EXPECTED_WARNINGS = [
    "warning: Open array passed by value.",
    "warning: potentially unhandled exception: Main.Oops",
    "m3j: warning: no module of interface RT0 is compiled",
]


class Failed(Exception):
    pass


def run(cmd, **kw):
    r = subprocess.run(cmd, capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=600, **kw)
    if r.returncode:
        raise Failed("%s: exit %d\n%s%s" % (Path(cmd[0]).name, r.returncode, r.stdout, r.stderr))
    return r


class Env:
    def __init__(self, args, work):
        self.args = args
        self.work = work
        self.java = args.java
        self.program = work / "debug.jar"
        self.tools = work / "tools"

    def compile(self):
        r = run([self.java, "-Dm3j.maxMethod=300", "-cp", self.args.jar, "m3j.jvm.Compile",
                 *[u + ".m3" for u in UNITS], "-d", str(self.program)], cwd=PKG)
        for line in (r.stdout + r.stderr).splitlines():
            if "warning" in line and "warnings encountered" not in line \
                    and not any(w in line for w in EXPECTED_WARNINGS):
                raise Failed("compile:\n" + r.stdout + r.stderr)
        javac = str(Path(self.java).with_name("javac"))
        run([javac, "--release", "25", "-cp", self.args.jar, "-d", str(self.tools), str(TESTS / "InfoCheck.java")])


def compare_text(got, name, args):
    expected = EXPECTED / name
    if args.record:
        expected.parent.mkdir(parents=True, exist_ok=True)
        expected.write_text(got, newline="\n")
        return "recorded"
    want = expected.read_text()
    if got != want:
        g, w = got.splitlines(), want.splitlines()
        i = next((k for k in range(min(len(g), len(w))) if g[k] != w[k]), min(len(g), len(w)))
        raise Failed("differs from %s at line %d:\n  got  %s\n  want %s"
                     % (expected.name, i + 1, g[i] if i < len(g) else "(end)", w[i] if i < len(w) else "(end)"))
    return None


def info(env):
    units = [a for u in UNITS for a in ("-unit", u)]
    r = run([env.java, "-cp", env.args.jar, "m3j.debug.Dump", *units, str(env.program)])
    got = r.stdout.replace("\r\n", "\n")
    return compare_text(got, "info.txt", env.args) or "%d units" % got.count(": module ")


def check(env):
    jars = [str(p) for p in (Path(env.args.jar).with_name(n) for n in JARS) if p.exists()]
    if not jars:
        raise Failed("no jar of build.sh next to " + env.args.jar)
    r = run([env.java, "-cp", env.args.jar + os.pathsep + str(env.tools), "InfoCheck", str(env.program), *jars])
    return "%s (%s)" % (r.stdout.strip(), ", ".join(Path(j).name for j in jars))


# ---- sessions of the adapter

def source(f):
    """The file f of the program, or with <m3j>/ of m3j."""
    return ROOT / f[len("<m3j>/"):] if f.startswith("<m3j>/") else PKG / f


def line_of(f, text, delta=0):
    src = source(f).read_text(encoding="latin-1").replace("\r\n", "\n")
    at = src.index(text)
    return src.count("\n", 0, at) + 1 + delta


def expand(x, env, ctx=None):
    if isinstance(x, dict):
        if set(x) == {"$line"}:
            return line_of(*x["$line"])
        if set(x) == {"$src"}:
            return str(source(x["$src"]))
        if set(x) == {"$program"}:
            return str(env.program)
        if set(x) == {"$pkg"}:
            return str(ctx.package(x["$pkg"]))
        if set(x) == {"$port"}:
            return ctx.port()
        return {k: expand(v, env, ctx) for k, v in x.items()}
    if isinstance(x, list):
        return [expand(v, env, ctx) for v in x]
    return x


class Program:
    """A program that the test runs: its output, read as it comes."""

    def __init__(self, cmd, **kw):
        self.p = subprocess.Popen(cmd, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                  stderr=subprocess.PIPE, **kw)
        self.out, self.err = [], []
        self.lines = queue.Queue()  # the lines of standard output, as they come
        self.readers = [threading.Thread(target=self.read, args=(self.p.stdout, self.out, True), daemon=True),
                        threading.Thread(target=self.read, args=(self.p.stderr, self.err, False), daemon=True)]
        for r in self.readers:
            r.start()

    def read(self, f, to, lines):
        for line in f:
            s = line.decode("latin-1").replace("\r\n", "\n")
            to.append(s)
            if lines:
                self.lines.put(s)
        if lines:
            self.lines.put(None)

    def finish(self, timeout, norm):
        try:
            code = self.p.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            self.p.kill()
            code = "TIMEOUT"
        for r in self.readers:
            r.join(5)
        return {"stdout": norm("".join(self.out)), "stderr": norm("".join(self.err)), "exit": code}


class Context:
    """What the placeholders of a session stand for: the packages copied
    for it, the program started for attach."""

    def __init__(self, env, session):
        self.env = env
        self.session = session
        self.packages = {}
        self.debuggee = None
        self.terminals = []
        self.refuse = session.get("refuse", 0)

    def package(self, name):
        if name not in self.packages:
            to = self.env.work / "pkgs" / name
            if to.exists():
                shutil.rmtree(to)
            shutil.copytree(TESTS / "pkgs" / name, to)
            self.packages[name] = to
        return self.packages[name]

    def port(self):
        """The port that the program for attach listens at next (the agent
        listens at another one after a debugger leaves)."""
        if self.debuggee is None:
            d = self.session.get("debuggee", {})
            jar = Path(self.env.args.jar)
            runtime = jar.with_name("m3j-runtime.jar")
            cp = os.pathsep.join([str(runtime if runtime.exists() else jar), str(self.env.program)])
            self.debuggee = Program([self.env.java, "-agentlib:jdwp=transport=dt_socket,server=y,suspend=%s,"
                                     "address=127.0.0.1:0" % d.get("suspend", "y"), "-cp", cp, "m3.Main",
                                     *d.get("args", [])], cwd=self.env.work)
        while True:
            try:
                line = self.debuggee.lines.get(timeout=self.env.args.timeout)
            except queue.Empty:
                line = None
            if line is None:
                raise Failed("the program for attach gives no port")
            m = re.search(r"Listening for transport dt_socket at address: (?:.*:)?(\d+)", line)
            if m:
                return int(m.group(1))

    def run_in_terminal(self, a):
        """Runs the command of the arguments a of runInTerminal: the body
        of the response, or the message of a refusal."""
        if self.refuse > 0:
            self.refuse -= 1
            return None, "refused by the test"
        env = dict(os.environ)
        for k, v in (a.get("env") or {}).items():
            if v is None:
                env.pop(k, None)
            else:
                env[k] = v
        p = Program(a["args"], cwd=a.get("cwd"), env=env)
        self.terminals.append(p)
        return {"processId": p.p.pid}, None


def tidy(m):
    """The request m of the adapter, with the class path of a command as a list."""
    m = json.loads(json.dumps(m))
    args = m.get("arguments", {}).get("args")
    if isinstance(args, list) and "-cp" in args[:-1]:
        i = args.index("-cp")
        args[i + 1] = args[i + 1].split(os.pathsep)
    return m


class Normalizer:
    """Paths of the work directory, the program and m3j as <work>, <src>,
    <m3j>, with slashes; no seq."""

    def __init__(self, work, java):
        jdk = Path(java).resolve().parent.parent
        self.roots = [(str(PKG), "<src>"), (str(Path(work).resolve()), "<work>"), (str(work), "<work>"),
                      (str(ROOT), "<m3j>"), (str(jdk), "<jdk>")]

    def text(self, s):
        out = re.sub(r"(address=)[^,\s\"']+", r"\1<address>", s)
        out = re.sub(r"(at address: )\S+", r"\1<address>", out)
        for r, name in self.roots:
            for form in (r, r.replace("\\", "/")):
                out = re.sub(re.escape(form) + r"[^\s\"']*",
                             lambda m: name + m.group(0)[len(form):].replace("\\", "/"),
                             out, flags=re.IGNORECASE)
        return out

    def __call__(self, x):
        if isinstance(x, dict):
            return {k: self(v) for k, v in x.items() if k != "seq"}
        if isinstance(x, list):
            return [self(v) for v in x]
        if isinstance(x, str):
            return self.text(x)
        return x


class Adapter:
    def __init__(self, env):
        self.p = subprocess.Popen([env.java, "-cp", env.args.jar, "m3j.debug.dap.Main"], cwd=env.work,
                                  stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.q = queue.Queue()
        self.err = []
        self.seq = 0
        threading.Thread(target=self.read, daemon=True).start()
        threading.Thread(target=self.read_err, daemon=True).start()

    def read(self):
        f = self.p.stdout
        try:
            while True:
                hdr = {}
                while True:
                    line = f.readline()
                    if not line:
                        self.q.put(None)
                        return
                    line = line.strip()
                    if not line:
                        break
                    k, _, v = line.decode("latin-1").partition(":")
                    hdr[k.strip().lower()] = v.strip()
                body = f.read(int(hdr["content-length"]))
                try:
                    self.q.put(json.loads(body.decode("utf-8")))
                except ValueError:
                    self.q.put({"NOT-JSON": body.decode("latin-1")})
        except OSError:
            self.q.put(None)

    def read_err(self):
        for line in self.p.stderr:
            self.err.append(line.decode("latin-1").rstrip())

    def send(self, req):
        self.seq += 1
        msg = {"seq": self.seq, "type": "request"}
        msg.update(req)
        self.send_bytes(json.dumps(msg).encode("utf-8"))
        return self.seq

    def respond(self, req, body, error):
        self.seq += 1
        msg = {"seq": self.seq, "type": "response", "request_seq": req.get("seq"), "command": req.get("command"),
               "success": error is None}
        if error is not None:
            msg["message"] = error
        if body is not None:
            msg["body"] = body
        self.send_bytes(json.dumps(msg).encode("utf-8"))

    def send_bytes(self, b):
        try:
            self.p.stdin.write(b"Content-Length: %d\r\n\r\n" % len(b) + b)
            self.p.stdin.flush()
        except OSError:
            pass

    def get(self, timeout):
        try:
            return self.q.get(timeout=timeout)
        except queue.Empty:
            return "TIMEOUT"


def run_session(name, env):
    session = json.loads((SESSIONS / (name + ".json")).read_text(encoding="utf-8"))
    norm = Normalizer(env.work, env.java)
    timeout = env.args.timeout
    ctx = Context(env, session)
    cur = {"ad": Adapter(env)}
    record = []
    output = {}
    err = []
    state = {"terminated": False, "eof": False, "disconnected": False}

    def step(req, until):
        """Sends req (if any), records its response and the events up to until."""
        ad = cur["ad"]
        seq = ad.send(req) if req else None
        if req and req.get("command") == "disconnect":
            state["disconnected"] = True
        response, events = None, []
        end = time.time() + timeout
        while (seq is not None and response is None) or (until and until not in [e.get("event") for e in events]):
            m = ad.get(max(0.1, end - time.time()))
            if m == "TIMEOUT" or m is None:
                events.append({"event": "TIMEOUT" if m else "END OF OUTPUT"})
                state["eof"] = m is None
                break
            if m.get("type") == "response" and m.get("request_seq") == seq:
                response = m
            elif m.get("type") == "event" and m.get("event") == "output":
                b = m.get("body", {})
                output[b.get("category")] = output.get(b.get("category"), "") + b.get("output", "")
            elif m.get("type") == "request":
                events.append(tidy(m))
                if m.get("command") == "runInTerminal":
                    body, error = ctx.run_in_terminal(m.get("arguments", {}))
                else:
                    body, error = None, "unknown request"
                ad.respond(m, body, error)
            else:
                if m.get("event") == "terminated":
                    state["terminated"] = True
                events.append(m)
        record.extend(norm(x) for x in ([response] if response else []) + events)

    def initialize():
        init = {"command": "initialize", "arguments": {"clientID": "dap_test", "adapterID": "m3j",
                                                       "linesStartAt1": True, "columnsStartAt1": True,
                                                       "pathFormat": "path", "supportsRunInTerminalRequest": True}}
        if session.get("init"):
            step(init, None)
        else:
            cur["ad"].send(init)
            m = cur["ad"].get(timeout)
            if not (isinstance(m, dict) and m.get("success")):
                record.append(["initialize", norm(m) if isinstance(m, dict) else m])

    def finish():
        """Ends the adapter: its exit code."""
        ad = cur["ad"]
        try:
            ad.p.stdin.close()
        except OSError:
            pass
        try:
            code = ad.p.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            ad.p.kill()
            code = "TIMEOUT"
        err.extend(ad.err)
        return code

    try:
        initialize()
        if session.get("start", True):
            launch = {"jar": str(env.program), "cwd": str(env.work), "sourceRoots": [str(PKG)]}
            launch.update(expand(session.get("launch", {}), env, ctx))
            launch = {k: v for k, v in launch.items() if v is not None}
            step({"command": "launch", "arguments": launch},
                 "initialized" if session.get("launches", True) else None)
        for s in session["steps"]:
            if state["eof"]:
                break
            if "send" in s:
                step(expand(s["send"], env, ctx), s.get("until"))
            elif "raw" in s:
                cur["ad"].send_bytes(s["raw"].encode("utf-8"))
            elif "wait" in s:
                step(None, s["wait"])
            elif "restart" in s:
                record.append({"exit": finish()})
                cur["ad"] = Adapter(env)
                state.update(terminated=False, eof=False, disconnected=False)
                initialize()
        ad = cur["ad"]
        if not state["eof"]:
            if not state["terminated"] and not state["disconnected"]:
                step({"command": "disconnect", "arguments": {}}, None)
            # what comes after the last step
            while True:
                m = ad.get(2)
                if m in ("TIMEOUT", None):
                    break
                if m.get("event") == "output":
                    b = m.get("body", {})
                    output[b.get("category")] = output.get(b.get("category"), "") + b.get("output", "")
                else:
                    record.append(norm(m))
        code = finish()
        record.append({"output": {k: norm(v.replace("\r\n", "\n")) for k, v in sorted(output.items())}})
        record.append({"exit": code})
        for p in ctx.terminals:
            record.append({"terminal": p.finish(timeout, norm)})
        if ctx.debuggee:
            record.append({"debuggee": ctx.debuggee.finish(timeout, norm)})
    finally:
        if cur["ad"].p.poll() is None:
            cur["ad"].p.kill()
        for p in ctx.terminals + ([ctx.debuggee] if ctx.debuggee else []):
            if p.p.poll() is None:
                p.p.kill()
    return record, err


def first_diff(want, got):
    for i in range(max(len(want), len(got))):
        a = want[i] if i < len(want) else "<end>"
        b = got[i] if i < len(got) else "<end>"
        if a != b:
            return "message %d: expected %s, got %s" % (i + 1, json.dumps(a)[:300], json.dumps(b)[:300])
    return ""


def session_case(name):
    def case(env):
        record, err = run_session(name, env)
        exp = EXPECTED / (name + ".json")
        if env.args.record:
            exp.write_text(json.dumps(record, indent=1) + "\n", encoding="utf-8", newline="\n")
            return "recorded"
        if not exp.exists():
            raise Failed("no %s" % exp.name)
        want = json.loads(exp.read_text(encoding="utf-8"))
        if want != record:
            if env.args.verbose:
                print(json.dumps(record, indent=1))
                print("\n".join(err))
            raise Failed(first_diff(want, record))
        return "%d messages" % len(record)
    return case


CASES = {"info": info, "check": check}
for _p in sorted(SESSIONS.glob("*.json")):
    CASES[_p.stem] = session_case(_p.stem)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cases", nargs="*")
    ap.add_argument("--java", default=str(Path(os.environ["JDK25"]) / "bin" / "java") if os.environ.get("JDK25")
                    else shutil.which("java") or "java")
    ap.add_argument("--jar", default=str(ROOT / "m3j.jar"))
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--record", action="store_true")
    ap.add_argument("--timeout", type=float, default=60)
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()
    for c in args.cases:
        if c not in CASES:
            ap.error("unknown case %s (known: %s)" % (c, ", ".join(CASES)))
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="dap_test"))
    work = work / "a space"
    work.mkdir(parents=True, exist_ok=True)
    env = Env(args, work)
    failed = []
    try:
        env.compile()
        for name in args.cases or CASES:
            t0 = time.time()
            try:
                note = CASES[name](env)
                print("ok    %-12s %.1f s  %s" % (name, time.time() - t0, note))
            except Exception as e:
                failed.append(name)
                print("FAIL  %-12s %s" % (name, str(e)[:3000]))
    except Failed as e:
        failed.append("compile")
        print("FAIL  compile %s" % str(e)[:3000])
    finally:
        if not args.keep and not args.work:
            shutil.rmtree(work.parent, ignore_errors=True)
    print("%d failed" % len(failed) if failed else "all passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
