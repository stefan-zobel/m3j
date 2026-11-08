(* Copyright (C) 2023, Peter McKinna *)
(* All rights reserved.              *)
(* Licensed under the MIT license.   *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j: the id of a request is an Id, a number or a string
   (or null, if the request could not be read); an error reply has a
   message. *)

INTERFACE Msg;

IMPORT RefSeq;

TYPE
  Id = RECORD
    num  : INTEGER := 0;
    text : TEXT    := NIL;       (* a string id, as in the request *)
    null : BOOLEAN := FALSE;     (* no id *)
  END;

PROCEDURE ReadMessages();
PROCEDURE BuildInitReply(id : Id) : TEXT;
PROCEDURE BuildShutdownReply(id : Id) : TEXT;
PROCEDURE BuildErrorReply(id : Id; error : INTEGER; message : TEXT) : TEXT;
PROCEDURE BuildChangeReply(module,uri : TEXT; seq : RefSeq.T) : TEXT;
PROCEDURE BuildFoldingRangeReply(id : Id) : TEXT;
PROCEDURE BuildHoverReply(id : Id; hover : TEXT;
                          line, col, len : INTEGER) : TEXT;
PROCEDURE BuildDeclarationReply(id : Id; uri : TEXT;
                                line, col, len : INTEGER) : TEXT;
PROCEDURE BuildTypeDefinitionReply(id : Id; uri : TEXT;
                                   line, col, len : INTEGER) : TEXT;
(* server initiates *)
PROCEDURE BuildRegistration(id,method : TEXT) : TEXT;
PROCEDURE BuildUnregistration(id,method : TEXT) : TEXT;

(* capabilities from our init message *)
PROCEDURE GetIncrCap() : BOOLEAN;

END Msg.
