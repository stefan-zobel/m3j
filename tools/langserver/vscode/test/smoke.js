// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

'use strict';
// A test of the client of the extension against the language server of
// m3j, with node, in a copy of the packages of tests/lsp/pkgs (the
// library shapes built):
//
//   node smoke.js WORK
//
// with the environment variables M3J_JAVA (the java program) and
// M3J_LSP_JAR (m3_lang_server.jar). First lsp.js alone, then
// extension.js with the API of Visual Studio Code replaced by
// vscode-mock.js. Exit code 0 if all went well.

const fs = require('fs');
const path = require('path');
const Module = require('module');

const work = path.resolve(process.argv[2] || '.');
const java = process.env.M3J_JAVA || 'java';
const jar = process.env.M3J_LSP_JAR;
const main = path.join(work, 'app', 'src', 'Main.m3');
const text = fs.readFileSync(main, 'latin1').replace(/\r\n/g, '\n');

// the API of Visual Studio Code, for extension.js
const vscode = require('./vscode-mock');
const load = Module._load;
Module._load = function (request, parent, isMain) {
  if (request === 'vscode') return vscode;
  return load.call(this, request, parent, isMain);
};
const { Connection } = require('../lsp');

let failed = 0;

// the same file (on Windows in any case)
function sameFile(a, b) {
  return process.platform === 'win32' ? a.toLowerCase() === b.toLowerCase() : a === b;
}
function check(what, ok, detail) {
  console.log((ok ? 'ok    ' : 'FAIL  ') + what + (ok || detail === undefined ? '' : ': ' + detail));
  if (!ok) failed++;
}

// the position of the first occurrence of t in the text, plus off characters
function position(t, off) {
  const at = text.indexOf(t) + (off || 0);
  const before = text.slice(0, at);
  return { line: before.split('\n').length - 1, character: at - (before.lastIndexOf('\n') + 1) };
}

function waitFor(pred, ms) {
  return new Promise((resolve) => {
    const end = Date.now() + ms;
    const poll = () => {
      const v = pred();
      if (v || Date.now() > end) resolve(v);
      else setTimeout(poll, 100);
    };
    poll();
  });
}

async function testConnection() {
  const uri = vscode.Uri.file(main).toString();
  const conn = new Connection(java, ['-jar', jar], { cwd: work });
  const stderr = [];
  conn.onStderr = (t) => stderr.push(t);
  const published = [];
  conn.onNotification('textDocument/publishDiagnostics', (p) => published.push(p));
  const init = await conn.request('initialize', { processId: process.pid, rootUri: null, capabilities: {} }, 60000);
  check('lsp.js: initialize', init && init.capabilities && init.capabilities.hoverProvider === true);
  conn.notify('initialized', {});
  conn.notify('textDocument/didOpen', { textDocument: { uri, languageId: 'modula3', version: 1, text } });
  let d = await waitFor(() => published.find((p) => p.uri === uri), 60000);
  check('lsp.js: diagnostics of the file', d && d.diagnostics.length === 0, JSON.stringify(d));
  const hover = await conn.request('textDocument/hover',
    { textDocument: { uri }, position: position('total := Util') });
  check('lsp.js: hover', hover && hover.contents[0] === 'total : INTEGER', JSON.stringify(hover));
  const decl = await conn.request('textDocument/declaration',
    { textDocument: { uri }, position: position('Util.Add', 6) });
  check('lsp.js: declaration in another file', decl && /\/app\/src\/Util\.i3$/.test(decl.uri) &&
    decl.range.start.line === 7, JSON.stringify(decl));
  published.length = 0;
  const p = position('total := Util');
  conn.notify('textDocument/didChange', {
    textDocument: { uri, version: 2 },
    contentChanges: [{ range: { start: p, end: { line: p.line, character: p.character + 5 } }, text: 'totl' }]
  });
  d = await waitFor(() => published.find((x) => x.uri === uri), 60000);
  check('lsp.js: diagnostics after a change', d && d.diagnostics.length === 1 &&
    /'totl' not declared/.test(d.diagnostics[0].message), JSON.stringify(d));
  let error = null;
  try {
    await conn.request('no/such/method', {});
  } catch (e) {
    error = e;
  }
  check('lsp.js: an error reply', error && error.code === -32601, String(error));
  const code = await conn.stop(10000);
  check('lsp.js: shutdown and exit', code === 0, 'exit code ' + code + ' ' + stderr.join(''));
}

async function testExtension() {
  vscode.mock.setSettings({ home: path.dirname(jar), java });
  const extension = require('../extension');
  const context = { subscriptions: [] };
  extension.activate(context);
  check('extension.js: providers', ['hover', 'definition', 'declaration', 'typeDefinition', 'documentSymbol',
    'completion']
    .every((k) => vscode.mock.providers[k]), Object.keys(vscode.mock.providers).join(' '));
  const doc = {
    uri: vscode.Uri.file(main), languageId: 'modula3', version: 1, getText: () => text
  };
  vscode.mock.events.open.forEach((f) => f(doc));
  const coll = vscode.mock.collections[0];
  // the server opens the document once it is initialized
  const pos = position('total := Util');
  const hover = await vscode.mock.providers.hover.provideHover(doc, new vscode.Position(pos.line, pos.character));
  check('extension.js: hover', hover && hover.contents[0].value === 'total : INTEGER',
    JSON.stringify(hover));
  const diags = await waitFor(() => coll.map.get(doc.uri.toString()), 60000);
  check('extension.js: diagnostics', Array.isArray(diags) && diags.length === 0, JSON.stringify(diags));
  const add = position('Util.Add', 6);
  const locs = await vscode.mock.providers.definition.provideDefinition(doc, new vscode.Position(add.line, add.character));
  check('extension.js: definition', locs && locs.length === 1 &&
    sameFile(locs[0].uri.fsPath, path.join(work, 'app', 'src', 'Util.i3')) && locs[0].range.start.line === 7,
    JSON.stringify(locs));
  const p = position('circle.area');
  const types = await vscode.mock.providers.typeDefinition.provideTypeDefinition(doc, new vscode.Position(p.line, p.character));
  check('extension.js: type definition', types && types.length === 1 &&
    sameFile(types[0].uri.fsPath, path.join(work, 'shapes', 'src', 'Shapes.i3')), JSON.stringify(types));
  const syms = await vscode.mock.providers.documentSymbol.provideDocumentSymbols(doc);
  const show = syms && syms.find((s) => s.name === 'Show');
  check('extension.js: outline', syms && syms.map((s) => s.name).join(' ') === 'Greeting p total table circle Show' &&
    show.kind === vscode.SymbolKind.Function && show.detail === '(w: Wr.T; n: INTEGER)' &&
    show.selectionRange.start.line === position('Show (w').line &&
    syms[0].kind === vscode.SymbolKind.Constant && syms[1].kind === vscode.SymbolKind.Variable,
    JSON.stringify(syms));
  const util = position('Util.Add', 5);
  const items = await vscode.mock.providers.completion.provideCompletionItems(doc,
    new vscode.Position(util.line, util.character));
  const addItem = items && items.find((i) => i.label === 'Add');
  check('extension.js: completion after Util.', addItem && addItem.kind === vscode.CompletionItemKind.Function &&
    items.every((i) => i.label !== 'INTEGER'), JSON.stringify(items));
  const word = position('Show (Stdio');
  const words = await vscode.mock.providers.completion.provideCompletionItems(doc,
    new vscode.Position(word.line, word.character));
  check('extension.js: completion of identifiers', words &&
    ['Show', 'total', 'Util', 'INTEGER', 'BEGIN'].every((l) => words.some((i) => i.label === l)),
    JSON.stringify(words));
  const sets = coll.sets;
  const t = position('total := Util');
  doc.version = 2;
  vscode.mock.events.change.forEach((f) => f({
    document: doc,
    contentChanges: [{
      range: new vscode.Range(new vscode.Position(t.line, t.character), new vscode.Position(t.line, t.character + 5)),
      rangeLength: 5, text: 'totl'
    }]
  }));
  const changed = await waitFor(() => coll.sets > sets && coll.map.get(doc.uri.toString()), 60000);
  check('extension.js: diagnostics after a change', changed && changed.length === 1 &&
    changed[0].severity === vscode.DiagnosticSeverity.Error && changed[0].range.start.line === t.line,
    JSON.stringify(changed));
  vscode.mock.events.close.forEach((f) => f(doc));
  check('extension.js: diagnostics removed on close', !coll.map.has(doc.uri.toString()));
  const code = await extension.deactivate();
  check('extension.js: the server ended', code === 0, 'exit code ' + code);
  check('extension.js: no error messages', vscode.mock.shown.length === 0, vscode.mock.shown.join('; '));
}

(async () => {
  if (!jar) {
    console.log('FAIL  M3J_LSP_JAR is not set');
    process.exit(1);
  }
  try {
    await testConnection();
    await testExtension();
  } catch (e) {
    check('no exception', false, e && e.stack || String(e));
  }
  console.log(failed === 0 ? 'all passed' : failed + ' failed');
  process.exit(failed === 0 ? 0 : 1);
})();
