// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

'use strict';
// A client of the Language Server Protocol: a server process, the framing
// of its messages (a header with Content-Length, then the content in
// UTF-8), requests and their replies, and notifications. It does not
// depend on Visual Studio Code, so that it can be tested with node.

const { spawn } = require('child_process');

class Connection {
  // Starts the server: command with args (options as for spawn).
  constructor(command, args, options) {
    this.nextId = 1;
    this.pending = new Map();      // id -> {resolve, reject, timer}
    this.handlers = new Map();     // method -> function (params)
    this.buffer = Buffer.alloc(0);
    this.exited = false;
    this.onExit = null;            // function (code, signal)
    this.onStderr = null;          // function (text)
    this.proc = spawn(command, args, Object.assign({ stdio: ['pipe', 'pipe', 'pipe'] }, options));
    this.proc.stdout.on('data', (chunk) => this.receive(chunk));
    this.proc.stderr.on('data', (chunk) => {
      if (this.onStderr) this.onStderr(chunk.toString('utf8'));
    });
    this.proc.stdin.on('error', () => {});
    this.proc.on('error', (err) => this.ended(null, null, err));
    this.proc.on('exit', (code, signal) => this.ended(code, signal, null));
  }

  ended(code, signal, err) {
    if (this.exited) return;
    this.exited = true;
    const reason = err ? err.message : 'the server ended (exit code ' + code + ')';
    for (const [, p] of this.pending) {
      clearTimeout(p.timer);
      p.reject(new Error(reason));
    }
    this.pending.clear();
    if (this.onStderr && err) this.onStderr(reason + '\n');
    if (this.onExit) this.onExit(code, signal);
  }

  receive(chunk) {
    this.buffer = Buffer.concat([this.buffer, chunk]);
    for (;;) {
      const end = this.buffer.indexOf('\r\n\r\n');
      if (end < 0) return;
      let length = -1;
      for (const line of this.buffer.slice(0, end).toString('latin1').split('\r\n')) {
        const colon = line.indexOf(':');
        if (colon > 0 && line.slice(0, colon).trim().toLowerCase() === 'content-length') {
          length = parseInt(line.slice(colon + 1).trim(), 10);
        }
      }
      if (length < 0) {
        // not a header of the protocol: skipped
        this.buffer = this.buffer.slice(end + 4);
        continue;
      }
      if (this.buffer.length < end + 4 + length) return;
      const body = this.buffer.slice(end + 4, end + 4 + length).toString('utf8');
      this.buffer = this.buffer.slice(end + 4 + length);
      let msg;
      try {
        msg = JSON.parse(body);
      } catch (e) {
        if (this.onStderr) this.onStderr('a message of the server that is not JSON: ' + body + '\n');
        continue;
      }
      this.dispatch(msg);
    }
  }

  dispatch(msg) {
    if (msg.method === undefined && msg.id !== undefined) {
      // a reply
      const p = this.pending.get(msg.id);
      if (!p) return;
      this.pending.delete(msg.id);
      clearTimeout(p.timer);
      if (msg.error) {
        const e = new Error(msg.error.message || 'error ' + msg.error.code);
        e.code = msg.error.code;
        p.reject(e);
      } else {
        p.resolve(msg.result === undefined ? null : msg.result);
      }
      return;
    }
    const handler = this.handlers.get(msg.method);
    if (handler) {
      try {
        handler(msg.params);
      } catch (e) {
        if (this.onStderr) this.onStderr(String(e && e.stack || e) + '\n');
      }
    }
    if (msg.id !== undefined) {
      // a request of the server: not offered by this client
      this.write({ jsonrpc: '2.0', id: msg.id, error: { code: -32601, message: 'not offered: ' + msg.method } });
    }
  }

  write(msg) {
    if (this.exited) return;
    const body = Buffer.from(JSON.stringify(msg), 'utf8');
    this.proc.stdin.write('Content-Length: ' + body.length + '\r\n\r\n');
    this.proc.stdin.write(body);
  }

  // Sends a request: a promise of its result (rejected after timeoutMs).
  request(method, params, timeoutMs) {
    if (this.exited) return Promise.reject(new Error('the server has ended'));
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(method + ': no reply'));
      }, timeoutMs || 30000);
      this.pending.set(id, { resolve, reject, timer });
      this.write({ jsonrpc: '2.0', id, method, params });
    });
  }

  notify(method, params) {
    this.write({ jsonrpc: '2.0', method, params });
  }

  // The handler of the notifications (or requests) of the server with this method.
  onNotification(method, handler) {
    this.handlers.set(method, handler);
  }

  // Ends the server as the protocol says: shutdown, then exit; a promise
  // of its exit code.
  stop(timeoutMs) {
    return new Promise((resolve) => {
      if (this.exited) {
        resolve(this.proc.exitCode);
        return;
      }
      const timer = setTimeout(() => {
        this.proc.kill();
      }, timeoutMs || 10000);
      this.proc.on('exit', (code) => {
        clearTimeout(timer);
        resolve(code);
      });
      this.request('shutdown', null, timeoutMs).then(
        () => this.notify('exit'),
        () => this.proc.kill());
    });
  }
}

module.exports = { Connection };
