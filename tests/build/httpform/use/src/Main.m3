(* Cookies and multipart bodies of the package http through m3build. *)

MODULE Main;

IMPORT HTTPCookie, IO, Multipart, Rd, TextRd, TextWr, Wr;

<*FATAL ANY*>

VAR
  wr := TextWr.New ();
  w := Multipart.NewWriter (wr);
  r: Multipart.Reader;
  p: Multipart.Part;

BEGIN
  IO.Put (HTTPCookie.Format (HTTPCookie.T {name := "id", value := "1", path := "/", httpOnly := TRUE})
            & "\n");
  Wr.PutText (w.part ("title"), "linked");
  w.finish ();
  r := Multipart.NewReader (TextRd.New (TextWr.ToText (wr)), Multipart.Boundary (w.contentType ()));
  EVAL r.next (p);
  IO.Put (p.name & " " & Rd.GetText (p.body, 100) & "\n");
END Main.
