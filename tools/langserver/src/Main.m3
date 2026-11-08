(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j: the header of a message is read up to its empty line,
   with any number of fields, of which Content-Length (in any case) gives
   the length of the content; at the end of the input, or at a header
   that cannot be read, the server ends, as it does after the exit
   notification, with the exit code that the protocol gives (0 after a
   shutdown request, else 1). *)

MODULE Main;

IMPORT Text,Stdio,Rd,Scan,Params,Process,Thread,TextUtils,FloatMode,Lex;
IMPORT LSP,Utils;

CONST
  Field = "content-length:";

VAR
  parm : TEXT;

(* the content of the next message, or NIL at the end of the input *)
PROCEDURE ReadMsg() : TEXT =
  VAR
    count : INTEGER := -1;
    line,msg : TEXT;
  BEGIN
    TRY
      LOOP
        line := Rd.GetLine(Stdio.stdin);
        IF Text.Length(line) > 0
             AND Text.GetChar(line, Text.Length(line) - 1) = '\r' THEN
          line := Text.Sub(line, 0, Text.Length(line) - 1);
        END;
        IF Text.Empty(line) THEN
          (* the end of the header *)
          IF count >= 0 THEN EXIT; END;
        ELSIF Text.Length(line) > Text.Length(Field)
                AND Text.Equal(TextUtils.Lower(Text.Sub(line, 0, Text.Length(Field))),
                               Field) THEN
          count := Scan.Int(Text.Sub(line, Text.Length(Field)));
        END;
      END;
      msg := Rd.GetText(Stdio.stdin, count);
      IF Text.Length(msg) < count THEN RETURN NIL; END;
    EXCEPT
    | Rd.EndOfFile, Rd.Failure, Thread.Alerted, FloatMode.Trap, Lex.Error =>
        RETURN NIL;
    END;
    RETURN msg;
  END ReadMsg;

PROCEDURE Run() =
  VAR
    msg : TEXT;
    res : BOOLEAN;
  BEGIN
    LOOP
      msg := ReadMsg();
      IF msg = NIL THEN EXIT; END;
      res := LSP.HandleMsg(msg);
      IF res THEN EXIT; END;
    END;
  END Run;

BEGIN
  Utils.Msg("Modula3 language server started\n");
  IF Params.Count <= 1 THEN
    Run();
    LSP.Stop();
    Process.Exit(LSP.ExitCode());
  ELSE
    (* command line checking *)
    parm := Params.Get(1);
    LSP.CheckFile(parm);
    LSP.Stop();
  END;
END Main.
