// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

'use strict';
// The extension for Visual Studio Code: the language Modula-3 (in
// package.json, with its grammar), and the language server of m3j
// (java -jar m3_lang_server.jar) for errors and warnings, hovers,
// declarations, type definitions, the outline and the completion of
// identifiers.

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

// The java program and the jar of the server.
function serverCommand() {
  const cfg = vscode.workspace.getConfiguration('m3j');
  const home = cfg.get('home') || process.env.M3J_HOME || '';
  let java = cfg.get('java');
  if (!java) {
    java = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'java') : 'java';
  }
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
    }, '.'));
  if (vscode.workspace.getConfiguration('m3j').get('server', true)) start();
}

function deactivate() {
  stopping = true;
  if (!conn) return undefined;
  const c = conn;
  conn = null;
  return c.stop(5000);
}

module.exports = { activate, deactivate };
