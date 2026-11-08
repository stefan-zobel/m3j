// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

'use strict';
// The parts of the API of Visual Studio Code that extension.js uses, for
// testing it with node: the events and providers that it registers are
// kept, so that a test can fire and call them.

const path = require('path');

const events = { open: [], change: [], save: [], close: [] };
const providers = {};
const shown = [];
const collections = [];
let settings = {};

function disposable() {
  return { dispose() {} };
}

class Uri {
  constructor(text) {
    this.text = text;
    const m = /^([a-z]+):(.*)$/.exec(text);
    this.scheme = m ? m[1] : 'file';
    let p = decodeURIComponent((m ? m[2] : text).replace(/^\/\//, ''));
    if (/^\/[A-Za-z]:/.test(p)) p = p.slice(1);
    this.fsPath = path.normalize(p);
  }

  toString() {
    return this.text;
  }

  static parse(text) {
    return new Uri(text);
  }

  // as Visual Studio Code writes it on Windows: file:///c%3A/...
  static file(p) {
    let s = path.resolve(p).replace(/\\/g, '/');
    if (/^[A-Za-z]:/.test(s)) {
      s = '/' + s[0].toLowerCase() + '%3A' + encodeURI(s.slice(2));
    } else {
      s = encodeURI(s);
    }
    return new Uri('file://' + s);
  }
}

class Position {
  constructor(line, character) {
    this.line = line;
    this.character = character;
  }
}

class Range {
  constructor(start, end) {
    this.start = start;
    this.end = end;
  }
}

class Location {
  constructor(uri, range) {
    this.uri = uri;
    this.range = range;
  }
}

class MarkdownString {
  constructor(value) {
    this.value = value || '';
  }

  appendText(t) {
    this.value += t;
    return this;
  }

  appendCodeblock(t, lang) {
    this.value += '\n```' + (lang || '') + '\n' + t + '\n```\n';
    return this;
  }
}

class Hover {
  constructor(contents, range) {
    this.contents = contents;
    this.range = range;
  }
}

const DiagnosticSeverity = { Error: 0, Warning: 1, Information: 2, Hint: 3 };

class Diagnostic {
  constructor(range, message, severity) {
    this.range = range;
    this.message = message;
    this.severity = severity;
  }
}

function register(kind) {
  return (selector, provider) => {
    providers[kind] = provider;
    return disposable();
  };
}

module.exports = {
  Uri, Position, Range, Location, MarkdownString, Hover, Diagnostic, DiagnosticSeverity,
  workspace: {
    workspaceFolders: [],
    textDocuments: [],
    getConfiguration: () => ({ get: (key, dflt) => (key in settings ? settings[key] : dflt) }),
    onDidOpenTextDocument: (f) => { events.open.push(f); return disposable(); },
    onDidChangeTextDocument: (f) => { events.change.push(f); return disposable(); },
    onDidSaveTextDocument: (f) => { events.save.push(f); return disposable(); },
    onDidCloseTextDocument: (f) => { events.close.push(f); return disposable(); }
  },
  window: {
    createOutputChannel: () => ({
      lines: [],
      append(t) { this.lines.push(t); },
      appendLine(t) { this.lines.push(t + '\n'); },
      dispose() {}
    }),
    showErrorMessage: (m) => { shown.push(m); },
    showInformationMessage: (m) => { shown.push(m); }
  },
  languages: {
    createDiagnosticCollection: () => {
      const c = {
        map: new Map(),
        sets: 0,
        set(uri, list) { this.map.set(uri.toString(), list); this.sets++; },
        delete(uri) { this.map.delete(uri.toString()); },
        clear() { this.map.clear(); },
        dispose() {}
      };
      collections.push(c);
      return c;
    },
    registerHoverProvider: register('hover'),
    registerDefinitionProvider: register('definition'),
    registerDeclarationProvider: register('declaration'),
    registerTypeDefinitionProvider: register('typeDefinition')
  },
  // for the test
  mock: { events, providers, shown, collections, setSettings: (s) => { settings = s; } }
};
