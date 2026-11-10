(* The library ssh through m3build: a port where nobody listens. *)

MODULE Main;

IMPORT Atom, IO, SFTP, SSH, Thread;

<*FATAL Thread.Alerted*>

BEGIN
  TRY
    SFTP.Open (SSH.Connect ("127.0.0.1", SSH.Options {port := 1, user := "nobody", password := "none",
                                                     timeout := 10.0D0})).close ();
    IO.Put ("connected\n");
  EXCEPT
  | SSH.Error (e) => IO.Put (Atom.ToText (e.head) & "\n");
  END;
END Main.
