#!/usr/bin/env python3
# Copyright (c) 2026 the m3j authors.
# SPDX-License-Identifier: MIT
r"""Tests of the language server (m3_lang_server.jar, tools/langserver):
sessions of the Language Server Protocol against replies checked by hand.

Usage:
  python3 tools/lsp_test.py [SESSION]... [--java JAVA] [--jar JAR]
                            [--node NODE] [--work DIR] [--keep]
                            [--timeout SECONDS] [-v]

The packages of tests/lsp/pkgs are copied into a new directory for each
session (the work directory, WORK/SESSION/a space: its name has a space,
as the pathnames of Windows often have), and the library shapes there is
built with m3build, as the package app imports it through its
m3overrides. Each session
tests/lsp/sessions/SESSION.json starts a server in the work directory
(java -jar JAR, by default m3_lang_server.jar in the root of m3j), sends
the requests initialize and initialized, then its steps, then the request
shutdown and the notification exit, and records what the server sends
and its exit code. The record, with the work directory written as
<work> and the root of m3j as <m3j>, must be
tests/lsp/expected/SESSION.json. Without arguments all sessions run.

A session is an object with the list "steps"; a step is one of

  {"send": MESSAGE}   sends the message (its "jsonrpc" is added); for a
                      request (with an id), the step waits for its reply;
                      for didOpen and didChange, for the diagnostics of the
                      file (unless "wait": false is in the step); with
                      "slash": true, each slash is written as the escape
                      \/ of JSON; with "labels": [LABEL...], only the
                      items of the completion it gets back that have
                      these labels are recorded, in this order (for a
                      list that depends on what lib has)
  {"raw": TEXT}       sends TEXT as the content of a message, and waits
                      for a reply (an error for a message that is not JSON)
  {"quiet": SECONDS}  waits that long and records what comes (nothing,
                      for a file that the server does not check)
  {"eof": true}       closes the input of the server and waits for its end
                      (the session then ends there)

and "init": false leaves out initialize, "end": false shutdown and exit;
"crlf": [FILE...] gives these files of the copy CR LF line ends before the
library is built and the server starts.
The messages of the steps with the same "group" are recorded sorted, for
what may come in any order (the diagnostics of the thread that compiles
and the reply to a request).
In a message, {"$uri": FILE} stands for the URI of FILE (relative to the
work directory) as Visual Studio Code writes it on Windows
("file:///c%3A/...", a drive letter in lower case and its colon escaped),
{"$text": FILE} for the contents of FILE (with LF line ends), {"$crlf":
FILE} for them with CR LF line ends, and {"$pos": [FILE, TEXT, N,
OFFSET]} for the position (line and character, from 0) of the OFFSET-th
character (0 if left out) of the N-th occurrence (1 if left out) of TEXT
in FILE (the file as in the package, not as changed by the session).

The test also runs tools/langserver/vscode/test/smoke.js with node (the
client of the extension for Visual Studio Code against the server), if
node is there (--node, else node from the PATH): SKIP if not.

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
import urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LSP = ROOT / "tests" / "lsp"
SESSIONS = LSP / "sessions"
EXPECTED = LSP / "expected"


def file_uri(path):
    """The URI of path as Visual Studio Code writes it."""
    p = str(path).replace("\\", "/")
    if re.match(r"[A-Za-z]:", p):
        p = "/" + p[0].lower() + "%3A" + urllib.parse.quote(p[2:], safe="/")
    else:
        p = urllib.parse.quote(p, safe="/")
    return "file://" + p


def source(work, f):
    """The file f of a session: below the work directory, or with <m3j>/ below m3j."""
    return ROOT / f[len("<m3j>/"):] if f.startswith("<m3j>/") else work / f


def position(work, spec):
    f, text = spec[0], spec[1]
    n = spec[2] if len(spec) > 2 else 1
    off = spec[3] if len(spec) > 3 else 0
    src = source(work, f).read_text(encoding="latin-1").replace("\r\n", "\n")
    at = -1
    for _ in range(n):
        at = src.index(text, at + 1)
    at += off
    line = src.count("\n", 0, at)
    col = at - (src.rfind("\n", 0, at) + 1)
    return {"line": line, "character": col}


def expand(x, work):
    if isinstance(x, dict):
        if set(x) == {"$uri"}:
            return file_uri(source(work, x["$uri"]))
        if set(x) == {"$text"}:
            return source(work, x["$text"]).read_text(encoding="latin-1").replace("\r\n", "\n")
        if set(x) == {"$crlf"}:
            return source(work, x["$crlf"]).read_text(encoding="latin-1").replace("\r\n", "\n").replace(
                "\n", "\r\n")
        if set(x) == {"$pos"}:
            return position(work, x["$pos"])
        return {k: expand(v, work) for k, v in x.items()}
    if isinstance(x, list):
        return [expand(v, work) for v in x]
    return x


class Normalizer:
    """Pathnames and file URIs of the work directory and of m3j as <work>
    and <m3j>, with slashes."""

    def __init__(self, work):
        self.roots = [(str(Path(work).resolve()), "<work>"), (str(ROOT), "<m3j>")]
        w = str(work)
        if w != str(Path(work).resolve()):
            self.roots.insert(0, (w, "<work>"))

    def path(self, s):
        t = s.replace("\\", "/")
        for r, name in self.roots:
            r = r.replace("\\", "/")
            if t.lower().startswith(r.lower()):
                return name + t[len(r):]
        return None

    def text(self, s):
        if s.startswith("file:"):
            p = urllib.parse.unquote(re.sub(r"^file://", "", s))
            if re.match(r"/[A-Za-z]:", p):
                p = p[1:]
            n = self.path(p)
            return "file:" + n if n else s
        out = s
        for r, name in self.roots:
            for form in (r, r.replace("\\", "/")):
                out = re.sub(re.escape(form) + r"[^\s\"']*",
                             lambda m: name + m.group(0)[len(form):].replace("\\", "/"),
                             out, flags=re.IGNORECASE)
        return out

    def __call__(self, x):
        if isinstance(x, dict):
            return {k: self(v) for k, v in x.items()}
        if isinstance(x, list):
            return [self(v) for v in x]
        if isinstance(x, str):
            return self.text(x)
        return x


class Server:
    def __init__(self, args, work):
        cmd = [args.java, "-jar", args.jar]
        self.p = subprocess.Popen(cmd, cwd=work, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                  stderr=subprocess.PIPE)
        self.q = queue.Queue()
        self.err = []
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
                n = int(hdr["content-length"])
                body = f.read(n)
                try:
                    self.q.put(json.loads(body.decode("utf-8")))
                except ValueError:
                    self.q.put({"NOT-JSON": body.decode("latin-1")})
        except OSError:
            self.q.put(None)

    def read_err(self):
        for line in self.p.stderr:
            self.err.append(line.decode("latin-1").rstrip())

    def send_bytes(self, b):
        try:
            self.p.stdin.write(b"Content-Length: %d\r\n\r\n" % len(b) + b)
            self.p.stdin.flush()
        except OSError:
            pass   # the server has ended: what it sent is recorded

    def close(self):
        try:
            self.p.stdin.close()
        except OSError:
            pass

    def send(self, msg, slash=False):
        msg = dict(msg)
        msg.setdefault("jsonrpc", "2.0")
        text = json.dumps(msg)
        if slash:
            # the escape of JSON for a slash (only strings have slashes)
            text = text.replace("/", "\\/")
        self.send_bytes(text.encode("utf-8"))

    def get(self, timeout):
        try:
            return self.q.get(timeout=timeout)
        except queue.Empty:
            return "TIMEOUT"


def only(reply, labels):
    """Keeps of the items of the completion reply those with the labels, in their order."""
    if isinstance(reply, dict) and isinstance(reply.get("result"), list):
        by_label = {i.get("label"): i for i in reply["result"]}
        reply["result"] = [by_label[l] for l in labels if l in by_label]


def run_session(name, args, work):
    session = json.loads((SESSIONS / (name + ".json")).read_text(encoding="utf-8"))
    norm = Normalizer(work)
    srv = Server(args, work)
    record = []
    ended = False

    def receive_until(pred, timeout):
        """Records the messages up to the one that pred accepts."""
        end = time.time() + timeout
        while True:
            m = srv.get(max(0.1, end - time.time()))
            if m == "TIMEOUT":
                record.append("TIMEOUT")
                return False
            if m is None:
                record.append("END OF OUTPUT")
                return False
            record.append(norm(m))
            if pred(m):
                return True

    def request(msg, timeout):
        srv.send(msg)
        return receive_until(lambda m: isinstance(m, dict) and m.get("id") == msg["id"]
                             and "method" not in m, timeout)

    def finish():
        try:
            code = srv.p.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            srv.p.kill()
            code = "TIMEOUT"
        # what came after the last step
        while True:
            m = srv.get(1)
            if m in ("TIMEOUT", None):
                break
            record.append(norm(m))
        record.append({"exit": code})

    try:
        if session.get("init", True):
            init = {"id": 0, "method": "initialize",
                    "params": {"processId": None, "rootUri": file_uri(work), "rootPath": str(work),
                               "capabilities": {}}}
            srv.send(init)
            m = srv.get(args.timeout)
            if not (isinstance(m, dict) and m.get("id") == 0):
                record.append(["initialize", norm(m) if isinstance(m, dict) else m])
            srv.send({"method": "initialized", "params": {}})
        groups = {}    # a group -> the index in record of its first message
        for step in session["steps"]:
            start = len(record)
            if "send" in step:
                msg = expand(step["send"], work)
                if "id" in msg:
                    request(msg, args.timeout)
                    if "labels" in step:
                        only(record[-1], step["labels"])
                else:
                    srv.send(msg, slash=step.get("slash", False))
                    method = msg.get("method", "")
                    if method in ("textDocument/didOpen", "textDocument/didChange") \
                            and step.get("wait", True):
                        uri = msg["params"]["textDocument"]["uri"]
                        receive_until(lambda m: isinstance(m, dict)
                                      and m.get("method") == "textDocument/publishDiagnostics"
                                      and m["params"]["uri"] == uri, args.timeout)
            elif "raw" in step:
                srv.send_bytes(step["raw"].encode("utf-8"))
                receive_until(lambda m: isinstance(m, dict) and "id" in m and "method" not in m,
                              args.timeout)
            elif "quiet" in step:
                end = time.time() + step["quiet"]
                while time.time() < end:
                    m = srv.get(max(0.1, end - time.time()))
                    if m == "TIMEOUT":
                        break
                    if m is None:
                        record.append("END OF OUTPUT")
                        break
                    record.append(norm(m))
            elif step.get("eof"):
                srv.close()
                finish()
                ended = True
            if step.get("group"):
                first = groups.setdefault(step["group"], start)
                record[first:] = sorted(record[first:], key=lambda m: json.dumps(m, sort_keys=True))
            if ended:
                break
        if not ended:
            if session.get("end", True):
                request({"id": 9999, "method": "shutdown"}, args.timeout)
                srv.send({"method": "exit"})
            srv.close()
            finish()
    finally:
        if srv.p.poll() is None:
            srv.p.kill()
    return record, srv.err


def lib_files():
    return {str(p.relative_to(ROOT)) for p in (ROOT / "lib").rglob("*") if p.is_file()}


def first_diff(want, got):
    for i in range(max(len(want), len(got))):
        a = want[i] if i < len(want) else "<end>"
        b = got[i] if i < len(got) else "<end>"
        if a != b:
            return "message %d: expected %s, got %s" % (i + 1, json.dumps(a)[:160], json.dumps(b)[:160])
    return ""


def m3build(args, pkg, *opts):
    c = subprocess.run([args.java, "-jar", str(ROOT / "m3build.jar")] + list(opts), cwd=pkg,
                       capture_output=True, timeout=args.timeout)
    return c.returncode, (c.stdout + c.stderr).decode("latin-1")


def smoke(args, work):
    """The client of the extension with node against the server."""
    script = ROOT / "tools" / "langserver" / "vscode" / "test" / "smoke.js"
    node = args.node or shutil.which("node")
    if not node or not script.exists():
        return "SKIP", "no node" if not node else "no " + str(script)
    env = dict(os.environ, M3J_JAVA=args.java, M3J_LSP_JAR=args.jar)
    c = subprocess.run([node, str(script), str(work)], capture_output=True, timeout=args.timeout * 2, env=env)
    out = (c.stdout + c.stderr).decode("latin-1").strip()
    if c.returncode != 0:
        failed = [l for l in out.splitlines() if l.startswith("FAIL")]
        return "FAIL", (failed or out.splitlines() or ["exit %d" % c.returncode])[0]
    return "PASS", ""


def main():
    ap = argparse.ArgumentParser(description="tests of the language server")
    ap.add_argument("sessions", nargs="*")
    jdk = os.environ.get("JDK25")
    ap.add_argument("--java", default=str(Path(jdk) / "bin" / "java") if jdk else "java")
    ap.add_argument("--jar", default=str(ROOT / "m3_lang_server.jar"))
    ap.add_argument("--node")
    ap.add_argument("--work")
    ap.add_argument("--keep", action="store_true")
    ap.add_argument("--timeout", type=float, default=120)
    ap.add_argument("--record", action="store_true", help=argparse.SUPPRESS)
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()

    names = [n for n in args.sessions if n != "smoke"] if args.sessions \
        else sorted(p.stem for p in SESSIONS.glob("*.json"))
    work = Path(args.work) if args.work else Path(tempfile.mkdtemp(prefix="m3j-lsp-"))
    work.mkdir(parents=True, exist_ok=True)
    # the physical path, as the working directory of the JVM has it (on
    # macOS the temporary directory is under /var, a link to /private/var)
    work = work.resolve()
    counts = {}
    t0 = time.time()

    def report(status, what, detail=""):
        counts[status] = counts.get(status, 0) + 1
        if status not in ("PASS", "RECORDED") or args.verbose:
            print("%-12s %s  %s" % (status, what, detail))

    before = lib_files()
    try:
        for name in names:
            # a new copy of the packages for each session
            w = work / name / "a space"
            if w.exists():
                shutil.rmtree(w)
            shutil.copytree(LSP / "pkgs", w)
            session = json.loads((SESSIONS / (name + ".json")).read_text(encoding="utf-8"))
            for f in session.get("crlf", []):
                data = (w / f).read_bytes().replace(b"\r\n", b"\n")
                (w / f).write_bytes(data.replace(b"\n", b"\r\n"))
            code, out = m3build(args, w / "shapes")
            if code != 0:
                report("FAIL", name, "m3build of shapes: " + out.strip().splitlines()[-1])
                continue
            record, err = run_session(name, args, w)
            exp = EXPECTED / (name + ".json")
            if args.record:
                exp.write_text(json.dumps(record, indent=1) + "\n", encoding="utf-8")
                report("RECORDED", name)
            elif not exp.exists():
                report("NO-EXPECTED", name)
            else:
                want = json.loads(exp.read_text(encoding="utf-8"))
                if want == record:
                    report("PASS", name)
                else:
                    report("FAIL", name, first_diff(want, record))
                    if args.verbose:
                        print(json.dumps(record, indent=1))
                        print("\n".join(err))
        if not args.sessions or "smoke" in args.sessions:
            w = work / "smoke" / "a space"
            if w.exists():
                shutil.rmtree(w)
            shutil.copytree(LSP / "pkgs", w)
            m3build(args, w / "shapes")
            status, detail = smoke(args, w)
            report(status, "smoke (node)", detail)
        new = lib_files() - before
        report("FAIL" if new else "PASS", "nothing written to lib", ", ".join(sorted(new)))
        print(", ".join("%s %d" % (k, counts[k]) for k in sorted(counts)) + "  (%.1f s)" % (time.time() - t0))
        return 0 if set(counts) <= {"PASS", "RECORDED", "SKIP"} else 1
    finally:
        if not args.keep and not args.work:
            shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
