(* Copyright (C) 2023, Peter McKinna *)
(* All rights reserved.              *)
(* Licensed under the MIT license.   *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j: "jsonrpc" is the string "2.0", as JSON-RPC wants it;
   the id of a reply is that of its request, a number or a string (Id);
   an error is an object with a code and a message; the texts of
   messages and hovers are escaped for JSON (Utils.Encode) instead of
   having their quotes changed to apostrophes; the file of a declaration
   is a file URI of any system (Uri), and a declaration that was not
   found is null. *)

MODULE Msg;

IMPORT Json,RefSeq,Utils,Text,TextUtils,Rsrc,MsgBundle,Rd,Thread;
IMPORT M3Error;
IMPORT Debug, Uri;

CONST
  Head      = "{\"jsonrpc\": \"2.0\" ,";
  ReqMsg    = Head & "\"id\": 0,\"method\": \"\",\"params\": null}";
  SuccMsg   = Head & "\"id\": 0,\"result\" : null}";
  ErrorMsg  = Head & "\"id\": 0,\"error\": {\"code\": 0, \"message\": \"\"}}";
  NotifyMsg = Head & "\"method\": \"\",\"params\": null}";

TYPE
  (* these are read from msg.json, a bundled, required file *)
  Messages = RECORD
    initreply,registration,unregistration,
    diag,notify,params,hover,location : Json.T;
  END;

  Replies = RECORD
    reqMsg,succMsg,errorMsg,notifyMsg : Json.T;
  END;

VAR
  msgs : Messages;
  replies : Replies;

PROCEDURE Init() =
  BEGIN
    TRY
      replies.notifyMsg := Json.ParseBuf(NotifyMsg);
      replies.reqMsg := Json.ParseBuf(ReqMsg);
      replies.succMsg := Json.ParseBuf(SuccMsg);
      replies.errorMsg := Json.ParseBuf(ErrorMsg);
    EXCEPT
    | Json.E => Debug.Write("Error in messages\n");
    END;
  END Init;

PROCEDURE ReadMessages() =
  VAR node : Json.T;
  BEGIN
    TRY
      WITH path = Rsrc.BuildPath(MsgBundle.Get()),
           rd = Rsrc.Open("msg.json", path) DO
        node := Json.ParseStream(rd);
        Rd.Close(rd);
      END;
    EXCEPT
    | Json.E => Debug.Write("error in msg.json\n");
    | Rsrc.NotFound => Debug.Write("msg.json resource not found\n")
    | Rd.Failure,Thread.Alerted => Debug.Write("msg.json stream error\n")
    END;

    msgs.initreply := node.find("initreply");
    <*ASSERT msgs.initreply # NIL *>
    msgs.registration := node.find("registration");
    <*ASSERT msgs.registration # NIL *>
    msgs.unregistration := node.find("unregistration");
    <*ASSERT msgs.unregistration # NIL *>
    msgs.diag := node.find("diagnostic");
    <*ASSERT msgs.diag # NIL *>
(* need new name for params must be lots of diff types *)
    msgs.params := node.find("params");
    <*ASSERT msgs.params # NIL *>
    msgs.notify := node.find("notify");
    <*ASSERT msgs.notify # NIL *>
    msgs.hover := node.find("hover");
    <*ASSERT msgs.hover # NIL *>
    msgs.location := node.find("location");
    <*ASSERT msgs.location # NIL *>
  END ReadMessages;

PROCEDURE SetId(msg : Json.T; id : Id) =
  <*FATAL Json.E*>
  BEGIN
    (* adding with the same name replaces *)
    IF id.null THEN
      EVAL msg.addNull("id");
    ELSIF id.text # NIL THEN
      EVAL msg.addText("id", id.text);
    ELSE
      EVAL msg.addInt("id", id.num);
    END;
  END SetId;

PROCEDURE BuildInitReply(id : Id) : TEXT =
  VAR
    res,initMsg : Json.T;
    msg : TEXT;
  BEGIN
    initMsg := replies.succMsg.copy();
    res := msgs.initreply.copy();
    (* eg of updating a capability *)
    res.updateBool("capabilities/documentSymbolProvider",FALSE);
    SetId(initMsg, id);
    initMsg.updateArrObj("result",res);

    msg := initMsg.format();
    RETURN msg;
  END BuildInitReply;

PROCEDURE GetIncrCap() : BOOLEAN =
  VAR
    node : Json.T;
    capInt : INTEGER;
  BEGIN
    node := msgs.initreply.copy();
    node := node.find("capabilities/textDocumentSync");
    capInt := node.getInt();
    IF capInt = 2 THEN RETURN TRUE; ELSE RETURN FALSE; END;
  END GetIncrCap;

PROCEDURE BuildRegistration(id,method : TEXT) : TEXT =
  VAR
    res : Json.T;
    msg : TEXT;
  BEGIN
    (* method example "textDocument/diagnostic" *)
    res := msgs.registration.copy();
    res.updateText("params/registrations/0/id",id);
    res.updateText("params/registrations/0/method",method);
    msg := res.format();
    RETURN msg;
  END BuildRegistration;

PROCEDURE BuildUnregistration(id,method : TEXT) : TEXT =
  VAR
    res : Json.T;
    msg : TEXT;
  BEGIN
    res := msgs.unregistration.copy();
    res.updateText("params/unregisterations/0/id",id);
    res.updateText("params/unregisterations/0/method",method);
    msg := res.format();
    RETURN msg;
  END BuildUnregistration;

PROCEDURE BuildShutdownReply(id : Id) : TEXT =
  VAR
    shutdownMsg : Json.T;
    msg : TEXT;
  BEGIN
    shutdownMsg := replies.succMsg.copy();
    SetId(shutdownMsg, id);
    (* adding with same name overwrites *)
    EVAL shutdownMsg.addNull("result"); <*NOWARN*>

    msg := shutdownMsg.format();
    RETURN msg;
  END BuildShutdownReply;

PROCEDURE Escape(s : TEXT) : TEXT =
  BEGIN
    RETURN Utils.Encode(s);
  END Escape;

PROCEDURE BuildChangeReply(module,uri : TEXT; seq : RefSeq.T) : TEXT =
  VAR
    ds,d,p,notifyMsg : Json.T;
    errRef : M3Error.RefErr;
    line, col : INTEGER;
    msg,err : TEXT;
  BEGIN
    notifyMsg := replies.notifyMsg.copy();
    (* copy the orig message and operate on that *)
    p := msgs.params.copy();
    p.updateText("uri", Escape(uri));
(*fixme: add version *)
    ds := p.find("diagnostics");
    d := msgs.diag.copy();

    (* for each error/warning *)
    WHILE seq.size() > 0 DO
      errRef := seq.remhi();

      IF Text.Equal(module,errRef.name) THEN
        col := errRef.col;
        line := errRef.line - 1;
        (* ignore messages without pos info - probably bug in m3tk *)
        IF line >= 0 THEN 

          err := Escape(errRef.msg);
          d.updateText("message",err);
          IF TextUtils.Pos(errRef.msg,"warning:") = -1 THEN
            d.updateInt("severity",ORD(Utils.Severity.Error));
          ELSE
            d.updateInt("severity",ORD(Utils.Severity.Warning));
          END;
          d.updateText("source","m3tk");
          d.updateInt("range/start/line", line);
          d.updateInt("range/start/character", col);
          d.updateInt("range/end/line", line);
          d.updateInt("range/end/character", col + errRef.len);
          EVAL ds.addObj("",d); <*NOWARN*>
        END;
      END;
    END;

    notifyMsg.updateText("method", "textDocument/publishDiagnostics");
    notifyMsg.updateArrObj("params",p);
    (* send formatted but could send rawtext *)
    msg := notifyMsg.format();
    RETURN msg;
  END BuildChangeReply;

PROCEDURE BuildFoldingRangeReply(id : Id) : TEXT =
  VAR
    frMsg : Json.T;
    msg : TEXT;
  BEGIN
    frMsg := replies.succMsg.copy();
    SetId(frMsg, id);
(* fixme: what does folding range do? *)
    EVAL frMsg.addNull("result"); <*NOWARN*>

    msg := frMsg.format();
    RETURN msg;
  END BuildFoldingRangeReply;

PROCEDURE BuildHoverReply(id : Id; hover : TEXT;
                          line, col, len : INTEGER) : TEXT =
  VAR
    hoverMsg,p : Json.T;
    msg : TEXT;
  BEGIN
    hoverMsg := replies.succMsg.copy();
    SetId(hoverMsg, id);
    p := msgs.hover.copy();
    p.updateInt("range/start/line", line);
    p.updateInt("range/start/character", col);
    p.updateInt("range/end/line", line);
    p.updateInt("range/end/character", col + len);

    IF hover # NIL THEN
      hover := Escape(hover);
      p.updateText("contents/0",hover);
      EVAL hoverMsg.addObj("result",p); <*NOWARN*>
    END;
    msg := hoverMsg.format();
    RETURN msg;
  END BuildHoverReply;

PROCEDURE BuildDeclarationReply(id : Id; uri : TEXT;
                                line, col, len : INTEGER) : TEXT =
  VAR
    declMsg,p : Json.T;
    msg : TEXT;
  BEGIN
    declMsg := replies.succMsg.copy();
    SetId(declMsg, id);

    p := msgs.location.copy();
    p.updateInt("range/start/line", line);
    p.updateInt("range/start/character", col);
    p.updateInt("range/end/line", line);
    p.updateInt("range/end/character", col + len);

    (* uri is the pathname of the file *)
    IF uri # NIL AND line >= 0 THEN
      uri := Escape(Uri.FromPath(uri));
      p.updateText("uri",uri);
      EVAL declMsg.addObj("result",p); <*NOWARN*>
    END;
    msg := declMsg.format();
    RETURN msg;
  END BuildDeclarationReply;

PROCEDURE BuildTypeDefinitionReply(id : Id; uri : TEXT;
                                line, col, len : INTEGER) : TEXT =
  BEGIN
    RETURN BuildDeclarationReply(id,uri,line,col,len);
  END BuildTypeDefinitionReply;

PROCEDURE BuildErrorReply(id : Id; error : INTEGER; message : TEXT) : TEXT =
  VAR
    errMsg : Json.T;
    msg : TEXT;
  BEGIN
    errMsg := replies.errorMsg.copy();
    SetId(errMsg, id);
    errMsg.updateInt("error/code",error);
    errMsg.updateText("error/message",Escape(message));

    msg := errMsg.format();
    RETURN msg;
  END BuildErrorReply;

BEGIN
  Init();
  ReadMessages();
END Msg.
