(* Copyright (C) 2023, Peter McKinna *)
(* All rights reserved.              *)
(* Licensed under the MIT license.   *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j: no UnEncode. *)

INTERFACE Utils;

IMPORT TextSeq;

TYPE
  Severity = {Info, Error, Warning};

  NotifyRec = RECORD
    msg : TEXT;
    severity : Severity;
    startLine,startCol,endLine,endCol : CARDINAL;
  END;

  NotifyRef = REF NotifyRec;

  Range = RECORD
    line,col : CARDINAL;
  END;

  Incr <: IncrPublic;
  IncrPublic = OBJECT
  METHODS
    delete(VAR st : TextSeq.T; sr,er : Range);
    insert(VAR st : TextSeq.T; incr : TEXT; sr : Range);
    replace(VAR st : TextSeq.T; incr : TEXT; sr,er : Range);
  END;

  (* output a msg to stderr which displays on output window of client *)
  PROCEDURE Msg(s : TEXT);

END Utils.

