(* The package http through m3build: its three interfaces. *)

MODULE Main;

IMPORT HTTP, HTTPClient, HTTPServer, IO;

<*FATAL ANY*>

BEGIN
  IO.Put (HTTP.Escape ("a b") & "\n");
  EVAL HTTPServer.New ();
  HTTPClient.Close (HTTPClient.New ());
  IO.Put ("linked\n");
END Main.
