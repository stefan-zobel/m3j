(* The package zip through m3build. *)

MODULE Main;

IMPORT Deflate, IO, Rd, TextRd, TextWr, Wr, Zip;

<*FATAL ANY*>

VAR
  out := TextWr.New ();
  wr := Deflate.NewWr (out);
  rd: Rd.T;

BEGIN
  Wr.PutText (wr, "linked\n");
  Wr.Close (wr);
  rd := Deflate.NewRd (TextRd.New (TextWr.ToText (out)));
  <*ASSERT Zip.SafeName ("a/b.txt")*>
  IO.Put (Rd.GetText (rd, 100));
END Main.
