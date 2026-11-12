# The language server of m3j

`m3_lang_server.jar` is CM3's language server for Modula-3
(`m3-tools/langserver` of CM3 5.12.0, by Peter McKinna, MIT license),
compiled by m3j. It speaks the Language Server Protocol over its standard
input and output, so any editor with a client of the protocol can use it:

- errors and warnings while a file is edited (`textDocument/publishDiagnostics`,
  about two seconds after the last change);
- the name and type of the identifier under the cursor (`textDocument/hover`);
- the declaration of an identifier and the definition of its type
  (`textDocument/declaration`, `textDocument/definition`,
  `textDocument/typeDefinition`), in the same file, in the interfaces of
  the package, in the library of m3j (`lib`) and in the packages that the
  package imports, also from a file of the library itself;
- the outline of a file (`textDocument/documentSymbol`): its constants,
  types, variables, exceptions, revelations and procedures, with the
  fields and methods of records and objects, the values of enumerations
  and the nested procedures (`src/Outline.m3`). m3tk keeps where a
  declaration begins but not where it ends, so the end is found in the
  text (the `;` or the `END` of the procedure); after a syntax error, the
  outline has what m3tk parsed before it;
- the completion of identifiers (`textDocument/completion`, also after a
  `.`, `src/Complete.m3`): after `I.` the declarations of the interface
  that the unit imports as `I` (with `IMPORT X AS I` too, and of a
  generic instance such as `TextSeq`); else the declarations of the unit
  and of the interfaces that a module exports, the interfaces it imports
  and the names of its `FROM` imports, the predeclared identifiers and
  the reserved words, each group sorted after the one before. While a
  file has a syntax error, the declarations that m3tk drops after it are
  those of the last text of the file without one. Not yet: the fields
  and methods after a variable (`v.`), and the local variables and
  parameters of procedures.

It checks a file with m3tk, CM3's toolkit for Modula-3 (in `tests/stubgen`),
not with the compiler of m3j: a message is that of m3tk. A file is checked
if it is in the library of m3j (`lib`), whose packages m3tk searches
without m3build and where the server writes nothing, or in a package,
below a directory `src` with an `m3makefile`. For each package, the
server runs m3build (`m3build.jar` next to it) with
`-tfile` in the directory of the package, which writes the imports of the
package to `JVM/.M3IMPTAB`, where m3tk finds them; again when the
`m3makefile` or the `m3overrides` of the package is saved. A package that
the package imports through its `m3overrides` must be built first (its
`.M3EXPORTS`).

`build.sh` builds the server with m3build from its `m3makefile`
(`src/m3makefile`, `M3J_LSP=no` skips it) and puts it in the root of m3j:

    java -jar m3_lang_server.jar          the server (stdin and stdout)
    java -jar m3_lang_server.jar FILE     checks FILE and prints its diagnostics

`m3j.jar` and `m3build.jar` must be in the same directory as the jar. With
the environment variable `M3J_LSP_LOG=FILE` the server writes a log of
the messages to FILE.

Positions are counted in lines and in bytes of a line, while the protocol
counts the units of UTF-16: they are the same for a line that has only
ASCII characters, as Modula-3 sources usually have (other characters can
only be in comments, texts and characters).

The sources (`src`) are those of CM3, with changes for m3j and for the use
of the server on Windows, each noted at the top of its unit: file URIs of
any system (`Uri`), m3build instead of cm3, a package for each file
instead of the directory of the workspace, a mutex around m3tk, the
escapes of JSON, replies to wrong requests instead of a crash, and
several errors of the original fixed. `tools/lsp_test.py` tests it
(`tests/lsp`).

## Visual Studio Code

`vscode` is an extension for Visual Studio Code (no other packages
needed): the language Modula-3 (`.i3`, `.m3`, `.ig`, `.mg`) with its
syntax highlighting, and a client of the server. See `vscode/README.md`.

## Other editors

The server is started as `java -jar /path/to/m3j/m3_lang_server.jar`
(java of JDK 25 or later) for the files of Modula-3; the root of a
project can be found by its `m3makefile`. Replace `/path/to/m3j` by the
directory of m3j.

Neovim (0.11 or later), in `init.lua`:

```lua
vim.filetype.add({ extension = { i3 = 'modula3', m3 = 'modula3', ig = 'modula3', mg = 'modula3' } })
vim.lsp.config('m3j', {
  cmd = { 'java', '-jar', '/path/to/m3j/m3_lang_server.jar' },
  filetypes = { 'modula3' },
  root_markers = { 'm3makefile' },
})
vim.lsp.enable('m3j')
```

Emacs (29 or later) with Eglot, in `init.el` (with a mode of its own for
Modula-3 if there is none):

```elisp
(define-derived-mode modula3-mode prog-mode "Modula-3"
  (setq-local comment-start "(* ")
  (setq-local comment-end " *)"))
(add-to-list 'auto-mode-alist '("\\.[im][3g]\\'" . modula3-mode))
(with-eval-after-load 'eglot
  (add-to-list 'eglot-server-programs
               '(modula3-mode . ("java" "-jar" "/path/to/m3j/m3_lang_server.jar"))))
(add-hook 'modula3-mode-hook #'eglot-ensure)
```

Helix, in `languages.toml`:

```toml
[language-server.m3j]
command = "java"
args = ["-jar", "/path/to/m3j/m3_lang_server.jar"]

[[language]]
name = "modula3"
scope = "source.modula3"
file-types = ["i3", "m3", "ig", "mg"]
roots = ["m3makefile"]
block-comment-tokens = { start = "(*", end = "*)" }
language-servers = ["m3j"]
```

Kate, in Settings, LSP Client, User Server Settings (Kate chooses a server
by the highlighting mode of a document: the regular expression must match
the mode that Kate gives to the files of Modula-3):

```json
{
  "servers": {
    "modula3": {
      "command": ["java", "-jar", "/path/to/m3j/m3_lang_server.jar"],
      "rootIndicationFileNames": ["m3makefile"],
      "highlightingModeRegex": "^Modula"
    }
  }
}
```
