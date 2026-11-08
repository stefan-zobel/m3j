(* Changes for m3j: Copyright (c) 2026 the m3j authors, distributed
   under the MIT license in the file LICENSE. *)

(* Changed for m3j: the log is written only if the environment variable
   M3J_LSP_LOG names a file, to that file (instead of always to
   /tmp/debuglsp.txt, which does not exist on Windows); if the file
   cannot be written, there is no log. *)

MODULE Debug;

IMPORT Env, IO, Text, Wr;

VAR
  deb : Wr.T;
  debug : BOOLEAN := FALSE;

PROCEDURE Write(txt : TEXT) =
  BEGIN
    IF debug THEN
      IO.Put(txt,deb);
      Wr.Flush(deb);
    END;
  END Write;

PROCEDURE Close() =
  BEGIN
    IF debug THEN
      Wr.Close(deb);
    END;
  END Close;

PROCEDURE IsDebug() : BOOLEAN =
  BEGIN
    RETURN debug;
  END IsDebug;

(* this is how to debug to stderr
Wr.PutText(Stdio.stderr,"testtest\n");
Wr.Flush(Stdio.stderr);
*)

VAR file := Env.Get("M3J_LSP_LOG");

BEGIN
  IF file # NIL AND NOT Text.Empty(file) THEN
    deb := IO.OpenWrite(file);
    debug := deb # NIL;
  END;
END Debug.
