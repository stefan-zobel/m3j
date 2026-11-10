(* The package encoding through m3build. *)

MODULE Main;

IMPORT Base64, Hex, IO, RFC3339, URI, Utf8;

VAR u: URI.T;

BEGIN
  <*ASSERT URI.Parse ("http://example.com/a?b#c", u)*>
  IO.Put (u.host & " " & Hex.Encode (Utf8.Encode (W"\x00e9")) & " " & Base64.Encode ("linked") & " "
            & RFC3339.Format (0.0D0) & "\n");
END Main.
