(* The package tls through m3build: a context from a file that is missing. *)

MODULE Main;

IMPORT Atom, IO, IP, TLS;

BEGIN
  TRY
    EVAL TLS.NewContext ("missing.p12", "password");
  EXCEPT
  | IP.Error (l) => IO.Put (Atom.ToText (l.head) & "\n");
  END;
END Main.
