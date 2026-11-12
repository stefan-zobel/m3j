(* Copyright (C) 2023, Peter McKinna *)
(* All rights reserved.              *)
(* Licensed under the MIT license.   *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j: the messages are written with JsonWriter, not made
   from the templates of msg.json and its bundle; "jsonrpc" is the
   string "2.0", as JSON-RPC wants it; the id of a reply is that of its
   request, a number or a string (Id); an error is an object with a code
   and a message; the texts of messages and hovers are written as the
   characters that their bytes of UTF-8 stand for, instead of having
   their quotes changed to apostrophes; the file of a declaration is a
   file URI of any system (Uri), and a declaration that was not found is
   null; the outline of a file (BuildDocumentSymbolReply); the
   completion of identifiers (BuildCompletionReply), after a "." too. *)

MODULE Msg;

IMPORT JsonWriter,RefSeq,Utils,Text,TextUtils,TextWr,Thread,Utf8,Wr;
IMPORT M3Error;
IMPORT Uri, Outline, Complete;

<*FATAL JsonWriter.Error, Wr.Failure, Thread.Alerted*>

CONST
  (* a surrogate without its partner, which no text of the server has,
     would not end the server *)
  Options = JsonWriter.Options {replaceInvalid := TRUE};

  (* the changes of a document come as ranges (incremental) *)
  TextDocumentSync = 2;

TYPE
  Writer = RECORD
    wr : TextWr.T;
    w  : JsonWriter.T;
  END;

(* the beginning of a message *)
PROCEDURE Start(VAR m : Writer) =
  BEGIN
    m.wr := TextWr.New();
    m.w := JsonWriter.ToWr(m.wr, Options);
    m.w.object();
    m.w.text("2.0", "jsonrpc");
  END Start;

(* the beginning of a reply *)
PROCEDURE StartReply(VAR m : Writer; READONLY id : Id) =
  BEGIN
    Start(m);
    IF id.null THEN
      m.w.null("id");
    ELSIF id.text # NIL THEN
      m.w.text(id.text, "id");
    ELSE
      m.w.int(id.num, "id");
    END;
  END StartReply;

(* the end of a message, and its bytes *)
PROCEDURE Finish(VAR m : Writer) : TEXT =
  BEGIN
    m.w.end();
    m.w.finish();
    RETURN TextWr.ToText(m.wr);
  END Finish;

(* the characters of a text of the server, which holds the bytes of
   UTF-8, as the documents and the messages it reads do; bytes that are
   not UTF-8 stand for themselves *)
PROCEDURE Chars(t : TEXT) : TEXT =
  VAR r : TEXT;
  BEGIN
    IF Utf8.Decode(t, r) THEN RETURN r; END;
    RETURN t;
  END Chars;

PROCEDURE Range(w : JsonWriter.T; line, col, len : INTEGER) =
  BEGIN
    w.object("range");
    w.object("start");
    w.int(line, "line");
    w.int(col, "character");
    w.end();
    w.object("end");
    w.int(line, "line");
    w.int(col + len, "character");
    w.end();
    w.end();
  END Range;

PROCEDURE BuildInitReply(id : Id) : TEXT =
  VAR m : Writer;
  BEGIN
    StartReply(m, id);
    m.w.object("result");
    (* only what the server does *)
    m.w.object("capabilities");
    m.w.int(TextDocumentSync, "textDocumentSync");
    m.w.object("completionProvider");
    m.w.array("triggerCharacters");
    m.w.text(".");
    m.w.end();
    m.w.end();
    m.w.bool(TRUE, "hoverProvider");
    m.w.bool(TRUE, "definitionProvider");
    m.w.bool(TRUE, "declarationProvider");
    m.w.bool(TRUE, "typeDefinitionProvider");
    m.w.bool(FALSE, "implementationProvider");
    m.w.bool(TRUE, "documentSymbolProvider");
    m.w.bool(FALSE, "workspaceSymbolProvider");
    m.w.bool(FALSE, "foldingRangeProvider");
    m.w.bool(FALSE, "codeActionProvider");
    m.w.bool(FALSE, "codeLensProvider");
    m.w.bool(FALSE, "documentFormattingProvider");
    m.w.bool(FALSE, "documentRangeFormattingProvider");
    m.w.bool(FALSE, "renameProvider");
    m.w.end();
    m.w.object("serverInfo");
    m.w.text("m3_lang_server", "name");
    m.w.end();
    m.w.end();
    RETURN Finish(m);
  END BuildInitReply;

PROCEDURE GetIncrCap() : BOOLEAN =
  BEGIN
    RETURN TextDocumentSync = 2;
  END GetIncrCap;

PROCEDURE BuildRegistration(id,method : TEXT) : TEXT =
  VAR m : Writer;
  BEGIN
    (* method example "textDocument/diagnostic" *)
    Start(m);
    m.w.text("client/registerCapability", "method");
    m.w.object("params");
    m.w.array("registrations");
    m.w.object();
    m.w.text(id, "id");
    m.w.text(method, "method");
    m.w.object("registerOptions");
    m.w.end();
    m.w.end();
    m.w.end();
    m.w.end();
    RETURN Finish(m);
  END BuildRegistration;

PROCEDURE BuildUnregistration(id,method : TEXT) : TEXT =
  VAR m : Writer;
  BEGIN
    Start(m);
    m.w.text("client/unregisterCapability", "method");
    m.w.object("params");
    (* the name that the protocol gives it *)
    m.w.array("unregisterations");
    m.w.object();
    m.w.text(id, "id");
    m.w.text(method, "method");
    m.w.end();
    m.w.end();
    m.w.end();
    RETURN Finish(m);
  END BuildUnregistration;

(* a reply with a null result *)
PROCEDURE NullReply(READONLY id : Id) : TEXT =
  VAR m : Writer;
  BEGIN
    StartReply(m, id);
    m.w.null("result");
    RETURN Finish(m);
  END NullReply;

PROCEDURE BuildShutdownReply(id : Id) : TEXT =
  BEGIN
    RETURN NullReply(id);
  END BuildShutdownReply;

PROCEDURE BuildChangeReply(module,uri : TEXT; seq : RefSeq.T) : TEXT =
  VAR
    m : Writer;
    errRef : M3Error.RefErr;
    line, col : INTEGER;
    severity : Utils.Severity;
  BEGIN
    Start(m);
    m.w.text("textDocument/publishDiagnostics", "method");
    m.w.object("params");
    m.w.text(Chars(uri), "uri");
(*fixme: add version *)
    m.w.array("diagnostics");

    (* for each error/warning *)
    WHILE seq.size() > 0 DO
      errRef := seq.remhi();

      IF Text.Equal(module,errRef.name) THEN
        col := errRef.col;
        line := errRef.line - 1;
        (* ignore messages without pos info - probably bug in m3tk *)
        IF line >= 0 THEN
          IF TextUtils.Pos(errRef.msg,"warning:") = -1 THEN
            severity := Utils.Severity.Error;
          ELSE
            severity := Utils.Severity.Warning;
          END;
          m.w.object();
          m.w.int(ORD(severity), "severity");
          Range(m.w, line, col, errRef.len);
          m.w.text(Chars(errRef.msg), "message");
          m.w.text("m3tk", "source");
          m.w.end();
        END;
      END;
    END;

    m.w.end();
    m.w.end();
    RETURN Finish(m);
  END BuildChangeReply;

PROCEDURE BuildFoldingRangeReply(id : Id) : TEXT =
  BEGIN
(* fixme: what does folding range do? *)
    RETURN NullReply(id);
  END BuildFoldingRangeReply;

PROCEDURE BuildHoverReply(id : Id; hover : TEXT;
                          line, col, len : INTEGER) : TEXT =
  VAR m : Writer;
  BEGIN
    IF hover = NIL THEN RETURN NullReply(id); END;
    StartReply(m, id);
    m.w.object("result");
    m.w.array("contents");
    m.w.text(Chars(hover));
    m.w.end();
    Range(m.w, line, col, len);
    m.w.end();
    RETURN Finish(m);
  END BuildHoverReply;

PROCEDURE BuildDeclarationReply(id : Id; uri : TEXT;
                                line, col, len : INTEGER) : TEXT =
  VAR m : Writer;
  BEGIN
    (* uri is the pathname of the file *)
    IF uri = NIL OR line < 0 THEN RETURN NullReply(id); END;
    StartReply(m, id);
    m.w.object("result");
    m.w.text(Chars(Uri.FromPath(uri)), "uri");
    Range(m.w, line, col, len);
    m.w.end();
    RETURN Finish(m);
  END BuildDeclarationReply;

PROCEDURE BuildTypeDefinitionReply(id : Id; uri : TEXT;
                                line, col, len : INTEGER) : TEXT =
  BEGIN
    RETURN BuildDeclarationReply(id,uri,line,col,len);
  END BuildTypeDefinitionReply;

PROCEDURE Span(w : JsonWriter.T; name : TEXT; READONLY s : Outline.Span) =
  BEGIN
    w.object(name);
    w.object("start");
    w.int(s.start.line, "line");
    w.int(s.start.col, "character");
    w.end();
    w.object("end");
    w.int(s.end.line, "line");
    w.int(s.end.col, "character");
    w.end();
    w.end();
  END Span;

PROCEDURE Symbols(w : JsonWriter.T; syms : Outline.Symbols) =
  BEGIN
    FOR i := 0 TO LAST(syms^) DO
      WITH sym = syms[i] DO
        w.object();
        w.text(Chars(sym.name), "name");
        IF sym.detail # NIL THEN w.text(Chars(sym.detail), "detail"); END;
        w.int(sym.kind, "kind");
        Span(w, "range", sym.range);
        Span(w, "selectionRange", sym.selection);
        IF sym.children # NIL THEN
          w.array("children");
          Symbols(w, sym.children);
          w.end();
        END;
        w.end();
      END;
    END;
  END Symbols;

PROCEDURE BuildDocumentSymbolReply(id : Id; syms : Outline.Symbols) : TEXT =
  VAR m : Writer;
  BEGIN
    IF syms = NIL THEN RETURN NullReply(id); END;
    StartReply(m, id);
    m.w.array("result");
    Symbols(m.w, syms);
    m.w.end();
    RETURN Finish(m);
  END BuildDocumentSymbolReply;

PROCEDURE BuildCompletionReply(id : Id; items : Complete.Items) : TEXT =
  VAR m : Writer;
  BEGIN
    IF items = NIL THEN RETURN NullReply(id); END;
    StartReply(m, id);
    m.w.array("result");
    FOR i := 0 TO LAST(items^) DO
      WITH item = items[i] DO
        m.w.object();
        m.w.text(Chars(item.label), "label");
        m.w.int(item.kind, "kind");
        IF item.detail # NIL THEN m.w.text(Chars(item.detail), "detail"); END;
        m.w.text(item.sort, "sortText");
        m.w.end();
      END;
    END;
    m.w.end();
    RETURN Finish(m);
  END BuildCompletionReply;

PROCEDURE BuildErrorReply(id : Id; error : INTEGER; message : TEXT) : TEXT =
  VAR m : Writer;
  BEGIN
    StartReply(m, id);
    m.w.object("error");
    m.w.int(error, "code");
    m.w.text(Chars(message), "message");
    m.w.end();
    RETURN Finish(m);
  END BuildErrorReply;

BEGIN
END Msg.
