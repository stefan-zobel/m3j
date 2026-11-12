# Modula-3 for Visual Studio Code

An extension for Visual Studio Code with the language Modula-3 and the
language server of m3j (`m3_lang_server.jar`):

- syntax highlighting of interfaces and modules (`.i3`, `.m3`, `.ig`,
  `.mg`): keywords, the standard types, constants and procedures, nested
  comments, pragmas, texts and characters with their escapes, numbers
  with a base (`16_FF`);
- comments and brackets for the editor (Toggle Block Comment gives
  `(* *)`), and indentation after `BEGIN`, `THEN`, `DO` and the others;
- errors and warnings while a file is edited, in the editor and in
  Problems;
- Hover: the name and type of an identifier;
- Go to Definition (F12) and Go to Declaration: the declaration of an
  identifier; Go to Type Definition: the definition of its type;
- the outline of a file (Outline, breadcrumbs, Go to Symbol in Editor):
  its constants, types, variables, exceptions, revelations and
  procedures, with the fields and methods of records and objects and the
  nested procedures;
- completion (while typing, after a `.` and with Ctrl+Space): after
  `I.` the declarations of the imported interface `I`, else the
  identifiers of the file and of the interfaces it exports and imports,
  the predeclared identifiers and the reserved words (not yet the fields
  and methods after a variable, nor local variables and parameters).

It needs no other packages: `extension.js` talks to the server with
`lsp.js`, a client of the Language Server Protocol of its own.

## Installation

Copy this directory (`tools/langserver/vscode`) to the extensions of
Visual Studio Code, with the name that it would have there:

    Windows:        %USERPROFILE%\.vscode\extensions\m3j.modula3-0.1.0
    Linux, macOS:   ~/.vscode/extensions/m3j.modula3-0.1.0

and restart Visual Studio Code. Then set, in the settings (or with the
environment variables named there):

| Setting | |
|---|---|
| `m3j.home` | the directory of m3j, with `m3_lang_server.jar`, `m3j.jar` and `m3build.jar` (empty: `M3J_HOME`) |
| `m3j.java` | the java program, of JDK 25 or later (empty: `JAVA_HOME/bin/java`, else `java` from the PATH) |
| `m3j.server` | `false`: no language server, only the highlighting |

The output "Modula-3 (m3j)" shows what the server writes (the messages
of m3build, errors). If the server ends, it is started again, at most
three times.

## How the server sees the files

A file is checked if it is in the library of m3j (`lib`) or in a package
(below a directory `src` with an `m3makefile`); the server finds the imports of the package with m3build
when it sees the first file of the package, and again when the
`m3makefile` or the `m3overrides` of the package is saved. A library that
the package imports through its `m3overrides` must be built (m3build in
its directory) for its declarations to be found. See
`tools/langserver/README.md`.

## Test

`tools/lsp_test.py` runs `test/smoke.js` with node: `lsp.js` and
`extension.js` (with the API of Visual Studio Code replaced by
`test/vscode-mock.js`) against the server.
