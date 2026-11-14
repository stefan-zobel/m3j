// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

'use strict';
// The extension for Visual Studio Code: the language Modula-3 (in
// package.json, with its grammar), the language server of m3j
// (java -jar m3_lang_server.jar) for errors and warnings, hovers,
// declarations, type definitions, the outline and the completion of
// identifiers, and the debugger of m3j (java -cp m3j.jar
// m3j.debug.dap.Main), with the designators under the mouse as the
// expressions of its hovers.

const vscode = require('vscode');
const fs = require('fs');
const path = require('path');
const { Connection } = require('./lsp');

const LANGUAGE = 'modula3';
const MAX_RESTARTS = 3;
// the files whose saving makes the server read the imports of their package again
const PACKAGE_FILES = new Set(['m3makefile', 'm3overrides']);

let conn = null;          // the connection to the server, if it runs
let ready = null;         // a promise that the server is initialized
let initialized = false;  // the server is initialized
let output = null;        // the output channel of the server
let diagnostics = null;
let restarts = 0;
let stopping = false;
const opened = new Set(); // the URIs of the documents the server has opened

// The java program and the directory of m3j.
function installation() {
  const cfg = vscode.workspace.getConfiguration('m3j');
  const home = cfg.get('home') || process.env.M3J_HOME || '';
  let java = cfg.get('java');
  if (!java) {
    java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
  }
  return { java, home };
}

// The java program and the jar of the server.
function serverCommand() {
  const { java, home } = installation();
  return { java, jar: home ? path.join(home, 'm3_lang_server.jar') : '' };
}

function toPosition(p) {
  return new vscode.Position(p.line, p.character);
}

function toRange(r) {
  return new vscode.Range(toPosition(r.start), toPosition(r.end));
}

function fromPosition(p) {
  return { line: p.line, character: p.character };
}

function fromRange(r) {
  return { start: fromPosition(r.start), end: fromPosition(r.end) };
}

function isModula3(doc) {
  return doc.languageId === LANGUAGE && (doc.uri.scheme === 'file' || doc.uri.scheme === 'untitled');
}

function didOpen(doc) {
  // before initialize is done, the document is opened with the others then
  if (!conn || !initialized || !isModula3(doc)) return;
  const uri = doc.uri.toString();
  if (opened.has(uri)) return;
  opened.add(uri);
  conn.notify('textDocument/didOpen', {
    textDocument: { uri, languageId: LANGUAGE, version: doc.version, text: doc.getText() }
  });
}

function didChange(event) {
  const uri = event.document.uri.toString();
  if (!conn || !opened.has(uri) || event.contentChanges.length === 0) return;
  conn.notify('textDocument/didChange', {
    textDocument: { uri, version: event.document.version },
    contentChanges: event.contentChanges.map((c) => ({
      range: fromRange(c.range), rangeLength: c.rangeLength, text: c.text
    }))
  });
}

function didSave(doc) {
  if (!conn) return;
  const uri = doc.uri.toString();
  if (opened.has(uri) || (doc.uri.scheme === 'file' && PACKAGE_FILES.has(path.basename(doc.uri.fsPath)))) {
    conn.notify('textDocument/didSave', { textDocument: { uri } });
  }
}

function didClose(doc) {
  const uri = doc.uri.toString();
  if (!opened.has(uri)) return;
  opened.delete(uri);
  diagnostics.delete(doc.uri);
  if (conn) conn.notify('textDocument/didClose', { textDocument: { uri } });
}

function publishDiagnostics(params) {
  const severities = [null, vscode.DiagnosticSeverity.Error, vscode.DiagnosticSeverity.Warning,
    vscode.DiagnosticSeverity.Information, vscode.DiagnosticSeverity.Hint];
  const list = (params.diagnostics || []).map((d) => {
    const diag = new vscode.Diagnostic(toRange(d.range), d.message,
      severities[d.severity] === undefined || severities[d.severity] === null
        ? vscode.DiagnosticSeverity.Error : severities[d.severity]);
    if (d.source) diag.source = d.source;
    return diag;
  });
  diagnostics.set(vscode.Uri.parse(params.uri), list);
}

function start() {
  const { java, jar } = serverCommand();
  if (!jar || !fs.existsSync(jar)) {
    vscode.window.showErrorMessage(
      'Modula-3: m3_lang_server.jar was not found; set m3j.home (or M3J_HOME) to the directory of m3j.');
    return;
  }
  output.appendLine('starting ' + java + ' -jar ' + jar);
  const c = new Connection(java, ['-jar', jar], {});
  conn = c;
  c.onStderr = (text) => output.append(text);
  c.onExit = (code) => {
    if (conn !== c) return;
    conn = null;
    ready = null;
    initialized = false;
    opened.clear();
    diagnostics.clear();
    if (stopping) return;
    if (restarts < MAX_RESTARTS) {
      restarts++;
      output.appendLine('the language server ended (exit code ' + code + '); starting it again');
      start();
    } else {
      vscode.window.showErrorMessage('Modula-3: the language server ended ' + (MAX_RESTARTS + 1) +
        ' times; see the output "Modula-3 (m3j)".');
    }
  };
  c.onNotification('textDocument/publishDiagnostics', publishDiagnostics);
  c.onNotification('window/logMessage', (p) => output.appendLine(p.message));
  c.onNotification('window/showMessage', (p) => vscode.window.showInformationMessage(p.message));
  const folder = vscode.workspace.workspaceFolders && vscode.workspace.workspaceFolders[0];
  ready = c.request('initialize', {
    processId: process.pid,
    rootUri: folder ? folder.uri.toString() : null,
    rootPath: folder ? folder.uri.fsPath : null,
    capabilities: {}
  }, 60000).then(() => {
    c.notify('initialized', {});
    initialized = true;
    vscode.workspace.textDocuments.forEach(didOpen);
  }, (err) => {
    output.appendLine('initialize: ' + err.message);
  });
}

// A request about a document, once the server is ready: its result, or
// null.
async function request(method, doc, params) {
  if (!conn || !ready) return null;
  await ready;
  if (!conn) return null;
  didOpen(doc);
  try {
    return await conn.request(method, Object.assign({ textDocument: { uri: doc.uri.toString() } }, params));
  } catch (err) {
    output.appendLine(method + ': ' + err.message);
    return null;
  }
}

// A request about a position in a document.
function ask(method, doc, position) {
  return request(method, doc, { position: fromPosition(position) });
}

// The symbols of the outline, from those of the protocol (whose kinds
// count from 1, those of Visual Studio Code from 0).
function toSymbols(result) {
  if (!Array.isArray(result)) return null;
  return result.filter((s) => s && s.name && s.range && s.selectionRange).map((s) => {
    const kind = s.kind >= 1 && s.kind <= 26 ? s.kind - 1 : vscode.SymbolKind.Variable;
    const sym = new vscode.DocumentSymbol(s.name, s.detail || '', kind,
      toRange(s.range), toRange(s.selectionRange));
    sym.children = toSymbols(s.children) || [];
    return sym;
  });
}

// The items of a completion, from those of the protocol (whose kinds
// count from 1, those of Visual Studio Code from 0).
function toItems(result) {
  const list = Array.isArray(result) ? result : result && Array.isArray(result.items) ? result.items : null;
  if (!list) return null;
  return list.filter((i) => i && i.label).map((i) => {
    const kind = i.kind >= 1 && i.kind <= 25 ? i.kind - 1 : vscode.CompletionItemKind.Text;
    const item = new vscode.CompletionItem(i.label, kind);
    if (i.detail) item.detail = i.detail;
    if (i.sortText) item.sortText = i.sortText;
    return item;
  });
}

function toHover(result) {
  if (!result || !result.contents) return null;
  const items = Array.isArray(result.contents) ? result.contents : [result.contents];
  const contents = items.map((item) => {
    if (typeof item === 'string') return new vscode.MarkdownString(item);
    if (item.kind === 'plaintext') return new vscode.MarkdownString().appendText(item.value);
    if (item.language) return new vscode.MarkdownString().appendCodeblock(item.value, item.language);
    return new vscode.MarkdownString(item.value);
  });
  return new vscode.Hover(contents, result.range ? toRange(result.range) : undefined);
}

function toLocations(result) {
  if (!result) return null;
  const list = Array.isArray(result) ? result : [result];
  return list.filter((l) => l && l.uri).map((l) =>
    new vscode.Location(vscode.Uri.parse(l.uri), toRange(l.range)));
}

// ---- the debugger

// The debug adapter of m3j, in the folder of the session (relative paths
// of its configuration are relative to it).
const debugAdapters = {
  createDebugAdapterDescriptor(session) {
    const { java, home } = installation();
    const jar = home ? path.join(home, 'm3j.jar') : '';
    if (!jar || !fs.existsSync(jar)) {
      throw new Error('Modula-3: m3j.jar was not found; set m3j.home (or M3J_HOME) to the directory of m3j.');
    }
    const options = {};
    if (session && session.workspaceFolder) options.cwd = session.workspaceFolder.uri.fsPath;
    return new vscode.DebugAdapterExecutable(java, ['-cp', jar, 'm3j.debug.dap.Main'], options);
  }
};

// The package of a file: the directory above it with src/m3makefile, or
// null.
function packageOf(file) {
  let dir = path.dirname(file);
  for (;;) {
    if (fs.existsSync(path.join(dir, 'src', 'm3makefile'))) return dir;
    const up = path.dirname(dir);
    if (up === dir) return null;
    dir = up;
  }
}

// The package of the file in the editor, or null.
function activePackage() {
  const editor = vscode.window.activeTextEditor;
  const doc = editor && editor.document;
  return doc && doc.languageId === LANGUAGE && doc.uri.scheme === 'file' ? packageOf(doc.uri.fsPath) : null;
}

// Without a launch configuration (F5 with no launch.json), and for one
// that names neither a package nor a jar: the program of the package of
// the file in the editor.
const debugConfigurations = {
  resolveDebugConfiguration(folder, config) {
    const empty = !config.type && !config.request && !config.name;
    if (empty || (config.request === 'launch' && !config.package && !config.jar)) {
      const pkg = activePackage();
      if (!pkg) {
        vscode.window.showErrorMessage('Modula-3: open a file of a package (a directory with src/m3makefile) ' +
          'to debug its program, or name the package in the launch configuration.');
        return undefined;
      }
      if (empty) return { type: 'm3j', request: 'launch', name: 'Debug ' + path.basename(pkg), package: pkg };
      config.package = pkg;
    }
    return config;
  }
};

// The words that are no variables: the reserved words and the predeclared
// identifiers.
const RESERVED = new Set(('AND ANY ARRAY AS BEGIN BITS BRANDED BY CASE CONST DIV DO ELSE ELSIF END EVAL ' +
  'EXCEPT EXCEPTION EXIT EXPORTS FINALLY FOR FROM GENERIC IF IMPORT IN INTERFACE LOCK LOOP METHODS MOD ' +
  'MODULE NOT OBJECT OF OR OVERRIDES PROCEDURE RAISE RAISES READONLY RECORD REF REPEAT RETURN REVEAL ROOT ' +
  'SET THEN TO TRY TYPE TYPECASE UNSAFE UNTIL UNTRACED VALUE VAR WHILE WITH ' +
  'ABS ADDRESS ADR ADRSIZE BITSIZE BOOLEAN BYTESIZE CARDINAL CEILING CHAR DEC DISPOSE EXTENDED FALSE ' +
  'FIRST FLOAT FLOOR INC INTEGER ISTYPE LAST LONGINT LONGREAL LOOPHOLE MAX MIN MUTEX NARROW NEW NIL NULL ' +
  'NUMBER ORD REAL REFANY ROUND SUBARRAY TEXT TRUE TRUNC TYPECODE VAL WIDECHAR').split(' '));

// The tokens of a line: names, numbers, literals, comments (to the end
// of the line if they go on) and single characters, with their columns.
function tokens(line) {
  const out = [];
  let i = 0;
  while (i < line.length) {
    const c = line[i];
    if (/\s/.test(c)) {
      i++;
      continue;
    }
    let j = i + 1;
    let kind = 'char';
    if (/[A-Za-z]/.test(c)) {
      while (j < line.length && /[A-Za-z0-9_]/.test(line[j])) j++;
      kind = 'name';
    } else if (/[0-9]/.test(c)) {
      while (j < line.length && /[A-Za-z0-9_]/.test(line[j])) j++;
      kind = 'number';
    } else if (c === '"' || c === "'") {
      while (j < line.length && line[j] !== c) j += line[j] === '\\' ? 2 : 1;
      j = Math.min(j + 1, line.length);
      kind = 'literal';
    } else if (c === '(' && line[j] === '*') {
      let depth = 1;
      j++;
      while (j < line.length && depth > 0) {
        if (line.startsWith('(*', j)) {
          depth++;
          j += 2;
        } else if (line.startsWith('*)', j)) {
          depth--;
          j += 2;
        } else {
          j++;
        }
      }
      kind = 'comment';
    }
    out.push({ kind, text: line.slice(i, j), start: i, end: j });
    i = j;
  }
  return out;
}

// The first token of the operand that ends with the token k (a name,
// with ^ and [...] after it), or -1.
function operandStart(toks, k) {
  while (k >= 0 && (toks[k].text === '^' || toks[k].text === ']')) {
    if (toks[k].text === '^') {
      k--;
      continue;
    }
    let depth = 0;
    for (; k >= 0; k--) {
      if (toks[k].text === ']') depth++;
      else if (toks[k].text === '[' && --depth === 0) break;
    }
    k--;
  }
  return k >= 0 && toks[k].kind === 'name' && !RESERVED.has(toks[k].text) ? k : -1;
}

// The designator that ends with the name at column ch of the line
// (a.b[i].c for c, i for i): {start, end, text}, or null.
function designatorAt(line, ch) {
  const toks = tokens(line);
  const w = toks.findIndex((t) => t.kind === 'name' && t.start <= ch && ch < t.end);
  if (w < 0 || RESERVED.has(toks[w].text)) return null;
  let s = w;
  while (s >= 2 && toks[s - 1].text === '.') {
    const k = operandStart(toks, s - 2);
    if (k < 0) break;
    s = k;
  }
  const start = toks[s].start;
  const end = toks[w].end;
  return { start, end, text: line.slice(start, end) };
}

const evaluatableExpressions = {
  provideEvaluatableExpression(doc, position) {
    const d = designatorAt(doc.lineAt(position.line).text, position.character);
    if (!d) return undefined;
    return new vscode.EvaluatableExpression(
      new vscode.Range(new vscode.Position(position.line, d.start), new vscode.Position(position.line, d.end)),
      d.text);
  }
};

function activate(context) {
  output = vscode.window.createOutputChannel('Modula-3 (m3j)');
  diagnostics = vscode.languages.createDiagnosticCollection(LANGUAGE);
  context.subscriptions.push(output, diagnostics);
  const selector = { language: LANGUAGE };
  context.subscriptions.push(
    vscode.workspace.onDidOpenTextDocument(didOpen),
    vscode.workspace.onDidChangeTextDocument(didChange),
    vscode.workspace.onDidSaveTextDocument(didSave),
    vscode.workspace.onDidCloseTextDocument(didClose),
    vscode.languages.registerHoverProvider(selector, {
      provideHover: async (doc, position) => toHover(await ask('textDocument/hover', doc, position))
    }),
    vscode.languages.registerDefinitionProvider(selector, {
      provideDefinition: async (doc, position) =>
        toLocations(await ask('textDocument/declaration', doc, position))
    }),
    vscode.languages.registerDeclarationProvider(selector, {
      provideDeclaration: async (doc, position) =>
        toLocations(await ask('textDocument/declaration', doc, position))
    }),
    vscode.languages.registerTypeDefinitionProvider(selector, {
      provideTypeDefinition: async (doc, position) =>
        toLocations(await ask('textDocument/typeDefinition', doc, position))
    }),
    vscode.languages.registerDocumentSymbolProvider(selector, {
      provideDocumentSymbols: async (doc) =>
        toSymbols(await request('textDocument/documentSymbol', doc, {}))
    }),
    vscode.languages.registerCompletionItemProvider(selector, {
      provideCompletionItems: async (doc, position) =>
        toItems(await ask('textDocument/completion', doc, position))
    }, '.'),
    vscode.languages.registerEvaluatableExpressionProvider(selector, evaluatableExpressions),
    vscode.debug.registerDebugAdapterDescriptorFactory('m3j', debugAdapters),
    vscode.debug.registerDebugConfigurationProvider('m3j', debugConfigurations));
  if (vscode.workspace.getConfiguration('m3j').get('server', true)) start();
}

function deactivate() {
  stopping = true;
  if (!conn) return undefined;
  const c = conn;
  conn = null;
  return c.stop(5000);
}

module.exports = { activate, deactivate, designatorAt };
