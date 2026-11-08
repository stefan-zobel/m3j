(* Copyright (C) 2023, Peter McKinna *)
(* All rights reserved.              *)
(* Licensed under the MIT license.   *)
(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j: Encode. *)

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

  PROCEDURE UnEncode(s : TEXT) : TEXT;
  (* the text that the contents "s" of a JSON string stand for: its
     escapes decoded, "\uXXXX" as the bytes of UTF-8 *)

  PROCEDURE Encode(s : TEXT) : TEXT;
  (* the contents of a JSON string that stands for "s": quotes,
     backslashes and control characters escaped *)

  (* output a msg to stderr which displays on output window of client *)
  PROCEDURE Msg(s : TEXT);

END Utils.

