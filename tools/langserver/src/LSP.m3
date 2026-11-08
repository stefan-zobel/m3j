(* Copyright (C) 2023, Peter McKinna *)
(* All rights reserved. *)
(* Licensed under the MIT license. *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j:
   - Files are named by file URIs of any system (Uri, AstCompile); a
     file is checked if it is in a package (a directory src above it
     with an m3makefile), not only if it is in the directory of the
     workspace (rootPath, which may be null).
   - For each package, m3build of m3j (m3build.jar next to the jar of
     m3j) writes the .M3IMPTAB with -tfile (and -override if the package
     has an m3overrides) when the first file of the package is checked, and again when its m3makefile or m3overrides
     is saved, instead of cm3 once for the workspace. Its output goes to
     the standard error output, not to that of the protocol.
   - A mutex keeps the thread that compiles and the requests from using
     m3tk at the same time; hover, declaration and type definition
     compile the file first if it is not the one that m3tk compiled
     last, or if it changed since.
   - textDocument/definition is answered as textDocument/declaration.
   - A request whose fields are missing or of the wrong kind gets an
     error reply (InvalidParams), a request without a method one with
     InvalidRequest, a request that the server knows but does not handle
     a null result; an id may be a number or a string. A runtime error
     while a message is handled is reported (InternalError for a
     request) and the server goes on.
   - A change without a range replaces the whole text; the changes of a
     message are applied in their order; a change of a file that is not
     open is ignored.
   - The check of the JSON of each reply and the log of each message are
     only made when there is a log (Debug).
   - The collector of cm3 is not started (RTCollectorSRC).
   - The reply to initialize (msg.json) offers only what the server
     does (no formatting, renaming, semantic tokens or signature help)
     and definitions, and names the server. *)

MODULE LSP;

(*
  This is a Modula-3 language server.

  It waits for a message on stdin, parses it into a Json.T,
  extracts the id and method and checks if it handles the message.
  The handler may send a response on stdout.
  The protocol has two types of messages, those with and id which should
  be responded to and those without, which  are notification messages only.

  The server calls m3build -tfile for each package to generate the
  .M3IMPTAB file in the build directory. This is used by m3tk, our
  semantic analyser to find and parse all the imports in the current
  message (uri).
*)


IMPORT Fmt, Wr, Stdio, Thread, Text, TextSeq, TextUtils, Process, File;
IMPORT FS, OSError, Pathname, RuntimeError;
IMPORT Json, Msg, AstCompile, Feature, Utils, Debug, Uri, M3jCompile;
IMPORT IO, Buf, RefSeq, TextRefTbl;

CONST
  MsgHeader    = "Content-Length: $len \r\n\r\n";
  CompileDelay = 2.0D0;

  (* error codes *)
  ParseError     = -32700;
  InvalidRequest = -32600;
  MethodNotFound = -32601;
  InvalidParams  = -32602;
  InternalError  = -32603;
(*
  ServerNotInitialized = -32002;
  UnknownErrorCode = -32001;
  lspReservedErrorRangeStart = -32899;
  RequestFailed = -32803;
  ServerCancelled = -32802;
  ContentModified = -32801;
  RequestCancelled = -32800;
  lspReservedErrorRangeEnd = -32800;
*)

TYPE
  MsgType = {Initialize, Shutdown, Hover, FoldingRange, CodeLens,
             Declaration, TypeDefinition,
             (* notifications *)
             Initialized, Exit, Open, Close, Save, Change,
             (* not part of protocol *)
             Error};

  MsgClass = {Request, Notification};

  VS = OBJECT
         id       : Msg.Id;
         type     : MsgType;
         method   : TEXT;
         msgClass : MsgClass   := MsgClass.Notification;
         json     : Json.T;
         errorCode: INTEGER    := 0;
         errorText: TEXT       := "";
       END;

  CompilerClosure = Thread.Closure OBJECT
                      count     : CARDINAL;
                      text, uri : TEXT;
                      cnt1, cnt2: CARDINAL   := 0;
                      finish    : BOOLEAN    := FALSE;
                      mu        : MUTEX;
                      textSeqTbl: TextRefTbl.T := NIL;
                    METHODS
                      update (uri, txt: TEXT) := Update;
                      exit   ()               := Exit;
                    OVERRIDES
                      apply := Compile;
                    END;

VAR
  compilerClosure: CompilerClosure;
  compThread     : Thread.T;
  wc                               := AstCompile.wc;
  msgMu          : MUTEX;
  useCompThread                    := TRUE;
  m3tkMu         : MUTEX;        (* m3tk, wc, compiled and packages *)
  compiled       : TEXT := NIL;  (* the URI that m3tk compiled last *)
  packages       : TextRefTbl.T; (* the packages whose .M3IMPTAB is made *)
  shutdown       := FALSE;       (* a shutdown request came *)


(* Debug proc for testing via command line *)
PROCEDURE CheckFile (uri: TEXT) =
  VAR
    text                    : TEXT;
    buf                     : Buf.T;
    line, col, startCol, len: INTEGER;
    hover                   : TEXT;
  BEGIN
    IF NOT Pathname.Absolute(uri) THEN
      TRY
        uri := FS.GetAbsolutePathname(uri);
      EXCEPT
      | OSError.E => IO.Put("no file " & uri & "\n"); RETURN;
      END;
    END;
    TRY
      buf := Buf.FromFile(uri, NIL);
    EXCEPT
    | OSError.E => IO.Put("cannot read " & uri & "\n"); RETURN;
    END;
    text := Text.FromChars(buf^);
    uri := Uri.FromPath(uri);
    IO.Put("uri:" & uri & "\n");

    CheckText(uri, text);

    line := 30;
    col := 8;
    LOCK m3tkMu DO
      IF Compiled(uri) THEN
        hover := Feature.GetSymbolType(
                   wc.getContext(), Uri.ToPath(uri), line, col, startCol, len);
      END;
    END;
  END CheckFile;

(* run m3build to create the .M3IMPTAB file for use by m3tk *)
PROCEDURE RunM3Build (dir: TEXT): INTEGER =
  VAR
    handle  : Process.T;
    exitCode: Process.ExitCode;
    params  : ARRAY [0 .. 4] OF TEXT;
    n       : CARDINAL := 4;
    stdin, stdout, stderr: File.T;
  BEGIN
    Debug.Write("RunM3Build in " & dir & "\n");
    params[0] := "-jar";
    params[1] := Pathname.Join(M3jCompile.Home(), "m3build.jar");
    params[2] := "-tfile";
    params[3] := "-silent";
    (* the overrides of the package, if it has them *)
    TRY
      EVAL FS.Status(Pathname.Join(Pathname.Join(dir, "src"), "m3overrides"));
      params[4] := "-override";
      n := 5;
    EXCEPT
    | OSError.E =>
    END;
    (* its output goes to our standard error output: the standard
       output is that of the protocol *)
    Process.GetStandardFileHandles(stdin, stdout, stderr);
    TRY
      handle := Process.Create(M3jCompile.Java(), SUBARRAY(params, 0, n),
                               NIL, dir, NIL, stderr, stderr);
      exitCode := Process.Wait(handle);
    EXCEPT
    | OSError.E =>
        Utils.Msg("m3build could not be run in " & dir & "\n");
        RETURN -1;
    END;
    IF exitCode # 0 THEN
      Utils.Msg("m3build -tfile in " & dir & ": exit code "
                  & Fmt.Int(exitCode) & "\n");
    END;
    Debug.Write("RunM3Build exit code " & Fmt.Int(exitCode) & "\n");
    RETURN exitCode;
  END RunM3Build;

(* with m3tkMu: TRUE if the file of uri is in a package that m3tk can
   compile, after its .M3IMPTAB is made (again if again) *)
PROCEDURE Package (uri: TEXT; again := FALSE): BOOLEAN =
  VAR
    base, lib: TEXT;
    ref      : REFANY;
  BEGIN
    base := wc.package(uri);
    IF base = NIL THEN RETURN FALSE; END;
    (* a package without an m3makefile, such as those of the library of
       m3j, is not built *)
    TRY
      EVAL FS.Status(Pathname.Join(Pathname.Join(base, "src"), "m3makefile"));
    EXCEPT
    | OSError.E => RETURN FALSE;
    END;
    lib := Pathname.Join(M3jCompile.Home(), "lib");
    IF Text.Length(base) > Text.Length(lib)
         AND Text.Equal(Text.Sub(base, 0, Text.Length(lib)), lib) THEN
      RETURN FALSE;
    END;
    IF again OR NOT packages.get(base, ref) THEN
      EVAL packages.put(base, NIL);
      EVAL RunM3Build(base);
      compiled := NIL;
    END;
    RETURN TRUE;
  END Package;

(* with m3tkMu: TRUE if the AST of m3tk is that of the text of uri,
   which is compiled if it is not; FALSE if the file is not open or not
   in a package *)
PROCEDURE Compiled (uri: TEXT): BOOLEAN =
  VAR
    ref    : REFANY;
    text   : TEXT;
    pending: BOOLEAN;
  BEGIN
    (* a change of the file that the thread has not compiled yet *)
    LOCK compilerClosure.mu DO
      pending := compilerClosure.cnt1 > compilerClosure.cnt2
                   AND Text.Equal(compilerClosure.uri, uri);
    END;
    IF NOT pending AND compiled # NIL AND Text.Equal(compiled, uri) THEN
      RETURN wc.getContext() # NIL;
    END;
    IF NOT compilerClosure.textSeqTbl.get(uri, ref) THEN RETURN FALSE; END;
    IF NOT Package(uri) THEN RETURN FALSE; END;
    text := TextUtils.TextSeqToText(NARROW(ref, TextSeq.T), sep := "\n");
    compiled := NIL;
    EVAL wc.compile(uri, text);
    compiled := uri;
    RETURN wc.getContext() # NIL;
  END Compiled;

PROCEDURE CheckText (uri, text: TEXT) =
  VAR
    errors: RefSeq.T;
    msg   : TEXT     := NIL;
  BEGIN
    (* FIXME compile could raise an exception eg dir not found, invalid
       extension etc and we should avoid sending a reply *)
    LOCK m3tkMu DO
      IF Package(uri) THEN
        compiled := NIL;
        errors := wc.compile(uri, text);
        compiled := uri;
        (* even if no errors still send reply *)
        msg := Msg.BuildChangeReply(wc.fileName(), uri, errors);
      END;
    END;
    IF msg # NIL THEN SendResult(msg); END;
  END CheckText;

PROCEDURE Compile (cl: CompilerClosure): REFANY =
  VAR
    doCompile: BOOLEAN;
    uri, text: TEXT;
  BEGIN
    LOOP
      Thread.Pause(CompileDelay);
      LOCK cl.mu DO
        IF cl.finish THEN EXIT; END;
        IF cl.cnt1 > cl.cnt2 THEN
          cl.cnt2 := cl.cnt1;
          uri := cl.uri;
          text := cl.text;
          doCompile := TRUE;
        ELSE
          doCompile := FALSE;
        END;
      END;
      IF doCompile THEN
        TRY
          CheckText(uri, text);
        EXCEPT
        | RuntimeError.E (e) =>
            Utils.Msg("runtime error while checking " & uri & ": "
                        & RuntimeError.Tag(e) & "\n");
        END;
      END;
    END;
    RETURN NIL;
  END Compile;

PROCEDURE Update (cl: CompilerClosure; uri, txt: TEXT) =
  BEGIN
    LOCK cl.mu DO INC(cl.cnt1); cl.uri := uri; cl.text := txt; END;
  END Update;

PROCEDURE Exit (cl: CompilerClosure) =
  BEGIN
    LOCK cl.mu DO
      IF cl.finish THEN RETURN; END;
      cl.finish := TRUE;
    END;
    EVAL Thread.Join(compThread);
  END Exit;

(* the fields of a message, FALSE if there is none of the kind *)
PROCEDURE GetText (node: Json.T; path: TEXT; VAR t: TEXT): BOOLEAN =
  BEGIN
    IF node = NIL THEN RETURN FALSE; END;
    node := node.find(path);
    IF node = NIL OR node.kind() # Json.NodeKind.nkText THEN
      RETURN FALSE;
    END;
    t := Utils.UnEncode(node.value());
    RETURN TRUE;
  END GetText;

PROCEDURE GetInt (node: Json.T; path: TEXT; VAR i: INTEGER): BOOLEAN =
  BEGIN
    IF node = NIL THEN RETURN FALSE; END;
    node := node.find(path);
    IF node = NIL OR node.kind() # Json.NodeKind.nkInt THEN
      RETURN FALSE;
    END;
    TRY
      i := node.getInt();
    EXCEPT
    | Json.E => RETURN FALSE;
    END;
    RETURN TRUE;
  END GetInt;

PROCEDURE GetPosition (vs: VS; VAR uri: TEXT; VAR line, col: INTEGER):
  BOOLEAN =
  BEGIN
    IF GetText(vs.json, "/params/textDocument/uri", uri)
         AND GetInt(vs.json, "/params/position/line", line)
         AND GetInt(vs.json, "/params/position/character", col)
         AND line >= 0 AND col >= 0 THEN
      RETURN TRUE;
    END;
    vs.errorCode := InvalidParams;
    vs.errorText := "textDocument/uri and position are needed";
    RETURN FALSE;
  END GetPosition;

PROCEDURE Classify (vs: VS): MsgType =
  VAR
    node: Json.T;
    ret : MsgType;
  BEGIN
    node := vs.json.find("id");
    IF node = NIL THEN
      vs.id.null := TRUE;
      vs.msgClass := MsgClass.Notification;
    ELSE
      vs.msgClass := MsgClass.Request;
      CASE node.kind() OF
      | Json.NodeKind.nkInt =>
          TRY
            vs.id.num := node.getInt();
          EXCEPT
          | Json.E =>
              vs.id.null := TRUE;
              vs.errorCode := InvalidRequest;
              vs.errorText := "the id is not an integer";
              RETURN MsgType.Error;
          END;
      | Json.NodeKind.nkText => vs.id.text := node.value();
      ELSE
        vs.id.null := TRUE;
        vs.errorCode := InvalidRequest;
        vs.errorText := "the id is not a number or a string";
        RETURN MsgType.Error;
      END;
    END;

    node := vs.json.find("method");
    IF node = NIL OR node.kind() # Json.NodeKind.nkText THEN
      vs.errorCode := InvalidRequest;
      vs.errorText := "no method";
      RETURN MsgType.Error;
    END;
    vs.method := Utils.UnEncode(node.value());

    (* Messages *)

    IF Text.Equal(vs.method, "initialize") THEN
      ret := MsgType.Initialize;
    ELSIF Text.Equal(vs.method, "shutdown") THEN
      ret := MsgType.Shutdown;
    ELSIF Text.Equal(vs.method, "textDocument/hover") THEN
      ret := MsgType.Hover;
    ELSIF Text.Equal(vs.method, "textDocument/foldingRange") THEN
      ret := MsgType.FoldingRange;
    ELSIF Text.Equal(vs.method, "textDocument/codeLens") THEN
      ret := MsgType.CodeLens;
    ELSIF Text.Equal(vs.method, "textDocument/declaration")
            OR Text.Equal(vs.method, "textDocument/definition") THEN
      ret := MsgType.Declaration;
    ELSIF Text.Equal(vs.method, "textDocument/typeDefinition") THEN
      ret := MsgType.TypeDefinition;

      (* Notifications *)

    ELSIF Text.Equal(vs.method, "initialized") THEN
      ret := MsgType.Initialized;
    ELSIF Text.Equal(vs.method, "exit") THEN
      ret := MsgType.Exit;
    ELSIF Text.Equal(vs.method, "textDocument/didOpen") THEN
      ret := MsgType.Open;
    ELSIF Text.Equal(vs.method, "textDocument/didClose") THEN
      ret := MsgType.Close;
    ELSIF Text.Equal(vs.method, "textDocument/didChange") THEN
      ret := MsgType.Change;
    ELSIF Text.Equal(vs.method, "textDocument/didSave") THEN
      ret := MsgType.Save;
    ELSE
      vs.errorCode := MethodNotFound;
      vs.errorText := "unknown method " & vs.method;
      ret := MsgType.Error;
    END;

    RETURN ret;
  END Classify;

PROCEDURE CheckResult (msg: TEXT) =
  BEGIN
    (* debug check if result is correct json.*)
    Debug.Write("Checking json reply\n");
    TRY
      EVAL Json.ParseBuf(msg);
    EXCEPT
    | Json.E => Debug.Write("Reply is not JSON: " & msg & "\n");
    END;
  END CheckResult;

PROCEDURE SendResult (msg: TEXT) =
  VAR header, len: TEXT;
  BEGIN
    IF Debug.IsDebug() THEN CheckResult(msg); END;
    LOCK msgMu DO
      len := Fmt.Int(Text.Length(msg));
      header := MsgHeader;
      header := TextUtils.Substitute(header, "$len", len, 1);
      msg := header & msg;

      (* send to client *)
      TRY
        Wr.PutText(Stdio.stdout, msg);
        Wr.Flush(Stdio.stdout);
      EXCEPT
      | Wr.Failure, Thread.Alerted => (* the client is gone *)
      END;
    END;
    Debug.Write("Response sent " & msg & "\n");
  END SendResult;

PROCEDURE InitResponse (vs: VS) =
  VAR msg: TEXT;
  BEGIN
    (* the packages are found from the files, not from rootPath or
       rootUri *)
    msg := Msg.BuildInitReply(vs.id);
    SendResult(msg);

    (*
    example of dynamic registration and unregistration
    not used
    msg := Msg.BuildRegistration("AAAA1111BBBB","textDocument/diagnostic");
    Debug.Write("Reg msg " & msg & "\n");
    msg := Msg.BuildUnregistration("AAAA1111BBBB","textDocument/diagnostic");
    Debug.Write("UnReg msg " & msg & "\n");
    SendResult(msg);
    *)
  END InitResponse;

PROCEDURE ShutdownResponse (vs: VS) =
  VAR msg: TEXT;
  BEGIN
    shutdown := TRUE;
    compilerClosure.exit();
    msg := Msg.BuildShutdownReply(vs.id);
    SendResult(msg);
  END ShutdownResponse;

(* not offered by the server: a null result *)
PROCEDURE NullResponse (vs: VS) =
  BEGIN
    Debug.Write("Null response to " & vs.method & "\n");
    SendResult(Msg.BuildFoldingRangeReply(vs.id));
  END NullResponse;

PROCEDURE DeclarationResponse (vs: VS) =
  VAR
    line, col, defLine, defCol, defLen: INTEGER;
    uri, msg, targetUri               : TEXT;
  BEGIN
    (* get the input message details *)
    IF NOT GetPosition(vs, uri, line, col) THEN
      ErrorResponse(vs);
      RETURN;
    END;

    Debug.Write("Declare uri-" & uri & " line " & Fmt.Int(line) & " col "
                  & Fmt.Int(col) & "\n");

    (* call m3tk to get declaration uri *)
    targetUri := NIL;
    LOCK m3tkMu DO
      IF Compiled(uri) THEN
        targetUri :=
          Feature.GetDeclaration(wc.getContext(), Uri.ToPath(uri), line,
                                 col, defLine, defCol, defLen);
      END;
    END;
    msg :=
      Msg.BuildDeclarationReply(vs.id, targetUri, defLine, defCol, defLen);

    SendResult(msg);

  END DeclarationResponse;

PROCEDURE TypeDefinitionResponse (vs: VS) =
  VAR
    line, col, defLine, defCol, defLen: INTEGER;
    uri, msg, targetUri               : TEXT;
  BEGIN
    (* get the input message details *)
    IF NOT GetPosition(vs, uri, line, col) THEN
      ErrorResponse(vs);
      RETURN;
    END;

    Debug.Write("TypeDefinition uri-" & uri & " line " & Fmt.Int(line)
                  & " col " & Fmt.Int(col) & "\n");

    (* call m3tk to get declaration uri *)
    targetUri := NIL;
    LOCK m3tkMu DO
      IF Compiled(uri) THEN
        targetUri :=
          Feature.GetTypeDefinition(wc.getContext(), Uri.ToPath(uri), line,
                                    col, defLine, defCol, defLen);
      END;
    END;
    msg := Msg.BuildTypeDefinitionReply(
             vs.id, targetUri, defLine, defCol, defLen);

    SendResult(msg);

  END TypeDefinitionResponse;

PROCEDURE HoverResponse (vs: VS) =
  VAR
    uri, msg, hover         : TEXT;
    line, col, startCol, len: INTEGER;
  BEGIN
    (* get the input message details *)
    IF NOT GetPosition(vs, uri, line, col) THEN
      ErrorResponse(vs);
      RETURN;
    END;

    Debug.Write("Hover uri-" & uri & " line " & Fmt.Int(line) & " col "
                  & Fmt.Int(col) & "\n");

    (* call m3tk to get symbol and type *)
    hover := NIL;
    LOCK m3tkMu DO
      IF Compiled(uri) THEN
        hover := Feature.GetSymbolType(
                   wc.getContext(), Uri.ToPath(uri), line, col, startCol, len);
      END;
    END;

    msg := Msg.BuildHoverReply(vs.id, hover, line, startCol, len);

    SendResult(msg);

  END HoverResponse;

PROCEDURE DidOpen (vs: VS) =
  VAR
    uri, text: TEXT;
    textSeq  : TextSeq.T;
  BEGIN
    (* get the input message details *)
    IF NOT GetText(vs.json, "/params/textDocument/text", text)
         OR NOT GetText(vs.json, "/params/textDocument/uri", uri) THEN
      RETURN;
    END;

    Debug.Write("DidOpen Uri: " & uri & "\n");

    textSeq := TextUtils.Split(text, "\n");

    (* save each open unit as a textSeq located via its uri *)
    EVAL compilerClosure.textSeqTbl.put(uri, textSeq);

    (* Initial compile *)
    CheckText(uri, text);
  END DidOpen;

PROCEDURE DidClose (vs: VS) =
  VAR
    uri    : TEXT;
    textSeq: REFANY;
  BEGIN
    (* get the input message details *)
    IF NOT GetText(vs.json, "/params/textDocument/uri", uri) THEN
      RETURN;
    END;
    Debug.Write("DidClose uri-" & uri & "\n");

    EVAL compilerClosure.textSeqTbl.delete(uri, textSeq);
    (* not doing anything if closed - not sending response *)
  END DidClose;

PROCEDURE DidSave (vs: VS) =
  VAR
    uri, name: TEXT;
  BEGIN
    IF NOT GetText(vs.json, "/params/textDocument/uri", uri) THEN
      RETURN;
    END;
    Debug.Write("DidSave uri-" & uri & "\n");
    (* the imports of a package are made again when its m3makefile or
       m3overrides is saved *)
    name := Pathname.Last(Uri.ToPath(uri));
    IF Text.Equal(name, "m3makefile") OR Text.Equal(name, "m3overrides") THEN
      LOCK m3tkMu DO EVAL Package(uri, again := TRUE); END;
    END;
  END DidSave;

PROCEDURE DidChangeResponse (vs: VS) =
  VAR
    uri, change, comp: TEXT;
    node, n          : Json.T;
    start, end       : Utils.Range;
    incrCap          : BOOLEAN;
    ts               : REFANY;
    textSeq          : TextSeq.T;
    l1, c1, l2, c2   : INTEGER;
  BEGIN
    (* For incremental we also get range - start end and
       (deprc)rangeLength(int) as well as text.  So start with orig
       document get a TEXT and apply the changes to the doc.
       A text length of zero implies delete get the start pos delete to end.
       If text length > 0 and ranges identical then inserttext at start pos.
       If text length > 0 and ranges different then replace text.
       There can be multiple changes in one message.
       Change the textDocumentSync from 1 to 2 to get incremental
       in msg.json init reply *)

    (* get the input message details *)
    IF NOT GetText(vs.json, "/params/textDocument/uri", uri) THEN
      RETURN;
    END;

    incrCap := Msg.GetIncrCap();

    (* Get the textSeq (Master document) for this uri *)
    IF NOT compilerClosure.textSeqTbl.get(uri, ts) THEN RETURN; END;
    textSeq := NARROW(ts, TextSeq.T);
    node := vs.json.find("/params/contentChanges");
    IF node = NIL OR node.kind() # Json.NodeKind.nkArray THEN RETURN; END;
    (* the changes in their order *)
    FOR i := 0 TO node.size() - 1 DO
      n := node.find(Fmt.Int(i));
      IF GetText(n, "/text", change) THEN
        (* ranges only if incremental is true *)
        IF incrCap AND GetInt(n, "/range/start/line", l1)
             AND GetInt(n, "/range/start/character", c1)
             AND GetInt(n, "/range/end/line", l2)
             AND GetInt(n, "/range/end/character", c2)
             AND l1 >= 0 AND c1 >= 0 AND l2 >= 0 AND c2 >= 0 THEN
          start.line := l1;
          start.col := c1;
          end.line := l2;
          end.col := c2;
          EVAL UpdateDoc(textSeq, change, start, end);

          Debug.Write("didChange changed>>" & change & "<<\n");
          Debug.Write("start range line" & Fmt.Int(start.line) & "\n");
          Debug.Write("start range col" & Fmt.Int(start.col) & "\n");
          Debug.Write("end range line" & Fmt.Int(end.line) & "\n");
          Debug.Write("end range col" & Fmt.Int(end.col) & "\n");
        ELSE
          (* the whole text *)
          Debug.Write("didChange whole text\n");
          textSeq := TextUtils.Split(change, "\n");
        END;
      END;
    END;
    comp := TextUtils.TextSeqToText(textSeq, sep := "\n");

    EVAL compilerClosure.textSeqTbl.put(uri, textSeq);

    IF useCompThread THEN
      compilerClosure.update(uri, comp);
    ELSE
      (* compile on every message *)
      CheckText(uri, comp);
    END;
  END DidChangeResponse;

PROCEDURE UpdateDoc
  (VAR textSeq: TextSeq.T; change: TEXT; start, end: Utils.Range; ): TEXT =
  VAR
    del : BOOLEAN;
    incr: Utils.Incr;
    res : TEXT;
  BEGIN
    (* updates the master doc represented by the textSeq with change. *)
    del := Text.Length(change) = 0;
    incr := NEW(Utils.Incr);
    IF del THEN
      Debug.Write("didChange doing delete\n");
      incr.delete(textSeq, start, end);
    ELSIF start = end THEN
      Debug.Write("didChange doing insert\n");
      incr.insert(textSeq, change, start);
    ELSE
      Debug.Write("didChange doing replace\n");
      incr.replace(textSeq, change, start, end);
    END;
    res := TextUtils.TextSeqToText(textSeq, sep := "\n");
    RETURN res;
  END UpdateDoc;

PROCEDURE ErrorResponse (vs: VS) =
  VAR msg: TEXT;
  BEGIN
    msg := Msg.BuildErrorReply(vs.id, vs.errorCode, vs.errorText);
    SendResult(msg);
  END ErrorResponse;

PROCEDURE HandleMsg (msg: TEXT): BOOLEAN =
  VAR
    json: Json.T;
    type: MsgType;
    exit: BOOLEAN := FALSE;
    vs  : VS;
  BEGIN
    vs := NEW(VS);
    TRY
      json := Json.ParseBuf(msg);
      vs.json := json;
      type := Classify(vs);
    EXCEPT
    | Json.E =>
       type := MsgType.Error;
       (* the id is not known: a reply with a null id *)
       vs.msgClass := MsgClass.Request;
       vs.id.null := TRUE;
       vs.errorCode := ParseError;
       vs.errorText := "the message is not JSON";
       Debug.Write("Json ERROR\n");
    END;

    IF Debug.IsDebug() THEN
      Debug.Write("New Message:\nMessage Type: " & Fmt.Int(ORD(vs.msgClass))
                    & "\nMessage Id: " & Fmt.Int(vs.id.num) & "\n");
      IF json # NIL THEN
        Debug.Write("Message Contents:" & json.format() & "\n");
      END;
    END;

    TRY
      CASE type OF
      | MsgType.Initialize => InitResponse(vs);
      | MsgType.Initialized =>     (* notification - no response *)
      | MsgType.Open => DidOpen(vs);
      | MsgType.Close => DidClose(vs);
      | MsgType.Save => DidSave(vs);
      | MsgType.Change => DidChangeResponse(vs);
      | MsgType.FoldingRange, MsgType.CodeLens =>
          IF vs.msgClass = MsgClass.Request THEN NullResponse(vs); END;
      | MsgType.Declaration => DeclarationResponse(vs);
      | MsgType.TypeDefinition => TypeDefinitionResponse(vs);
      | MsgType.Hover => HoverResponse(vs);
      | MsgType.Shutdown => ShutdownResponse(vs);
      | MsgType.Error =>
          (* not sending errors for notifications *)
          IF vs.msgClass = MsgClass.Request THEN ErrorResponse(vs); END;
      | MsgType.Exit => exit := TRUE;
      END;
    EXCEPT
    | RuntimeError.E (e) =>
        Utils.Msg("runtime error in " & vs.method & ": "
                    & RuntimeError.Tag(e) & "\n");
        LOCK m3tkMu DO compiled := NIL; END;
        IF vs.msgClass = MsgClass.Request THEN
          vs.errorCode := InternalError;
          vs.errorText := RuntimeError.Tag(e);
          ErrorResponse(vs);
        END;
    END;

    RETURN exit;
  END HandleMsg;

PROCEDURE ExitCode (): INTEGER =
  BEGIN
    IF shutdown THEN RETURN 0; ELSE RETURN 1; END;
  END ExitCode;

PROCEDURE Stop () =
  BEGIN
    IF compilerClosure # NIL THEN compilerClosure.exit(); END;
  END Stop;

BEGIN
  msgMu := NEW(MUTEX);
  m3tkMu := NEW(MUTEX);
  packages := NEW(TextRefTbl.Default).init(10);
  IF useCompThread THEN
    (* fork the thread for compiling the uri *)
    compilerClosure := NEW(CompilerClosure, mu := NEW(MUTEX),
                           textSeqTbl := NEW(TextRefTbl.Default).init(20));
    compThread := Thread.Fork(compilerClosure);
  END;
END LSP.
