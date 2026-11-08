(* Pathnames: every procedure of Pathname on pathnames of POSIX and of
   Windows (roots, drives, servers, separators of both kinds, extensions,
   illegal characters) and on arcs that are not valid. The package
   check/pathname-posix has the same program on cm3's module for POSIX. *)

MODULE Main;

IMPORT IO, Fmt, TextSeq;
IMPORT Pathname AS P;
FROM Pathname IMPORT Invalid, Parent, Current;

CONST Names = ARRAY OF TEXT {
  "", "/", "//", "a", "a/", "/a", "a//b", "a/b/c.m3", "/usr/lib/x.so",
  ".", "..", "a/./b/../c", "a.b.c", ".profile", "a.", "x/.y",
  "C:", "C:\\", "C:\\Windows\\a.txt", "C:a", "c:/x/y", "\\", "\\x",
  "\\\\server\\share\\f.g", "\\\\server", "a\\b", "a\\b\\", "a:b", "a*b",
  "a?b", "x/y\\z.t", "dir\\sub/f.ext.bak", "~/f", "\177x"};

PROCEDURE B (b: BOOLEAN): TEXT =
  BEGIN
    IF b THEN RETURN "T" ELSE RETURN "F" END;
  END B;

PROCEDURE Q (t: TEXT): TEXT =
  BEGIN
    IF t = NIL THEN RETURN "NIL" END;
    RETURN "<" & t & ">";
  END Q;

PROCEDURE Arcs (a: TextSeq.T): TEXT =
  VAR s := "";
  BEGIN
    FOR i := 0 TO a.size () - 1 DO s := s & Q (a.get (i)) END;
    RETURN s;
  END Arcs;

PROCEDURE Show (pn: TEXT) =
  VAR a: TextSeq.T;
  BEGIN
    IO.Put (Q (pn) & " valid " & B (P.Valid (pn)) & " absolute " & B (P.Absolute (pn)) & "\n");
    IO.Put ("  prefix " & Q (P.Prefix (pn)) & " last " & Q (P.Last (pn))
            & " base " & Q (P.Base (pn)) & "\n");
    IO.Put ("  lastbase " & Q (P.LastBase (pn)) & " lastext " & Q (P.LastExt (pn))
            & " replaceext " & Q (P.ReplaceExt (pn, "new")) & "\n");
    TRY
      a := P.Decompose (pn);
      IO.Put ("  arcs " & Arcs (a));
      IO.Put (" compose " & Q (P.Compose (a)) & "\n");
    EXCEPT Invalid => IO.Put (" Invalid\n");
    END;
  END Show;

PROCEDURE Compose (READONLY arcs: ARRAY OF TEXT) =
  VAR a := NEW (TextSeq.T).init ();
  BEGIN
    FOR i := 0 TO LAST (arcs) DO a.addhi (arcs[i]) END;
    IO.Put ("compose " & Arcs (a) & ": ");
    TRY
      IO.Put (Q (P.Compose (a)) & "\n");
    EXCEPT Invalid => IO.Put ("Invalid\n");
    END;
  END Compose;

PROCEDURE Join (pn, base, ext: TEXT) =
  BEGIN
    IO.Put ("join " & Q (pn) & " " & Q (base) & " " & Q (ext) & ": "
            & Q (P.Join (pn, base, ext)) & "\n");
  END Join;

BEGIN
  FOR i := 0 TO LAST (Names) DO Show (Names[i]) END;
  Compose (ARRAY OF TEXT {});
  Compose (ARRAY OF TEXT {NIL});
  Compose (ARRAY OF TEXT {NIL, "a", "b"});
  Compose (ARRAY OF TEXT {"/", "a"});
  Compose (ARRAY OF TEXT {"/", "a", ""});
  Compose (ARRAY OF TEXT {"C:\\", "x", "y.z"});
  Compose (ARRAY OF TEXT {"\\\\s\\t\\", "x"});
  Compose (ARRAY OF TEXT {"x", "a"});
  Compose (ARRAY OF TEXT {NIL, "a/b"});
  Compose (ARRAY OF TEXT {NIL, "a\\b"});
  Compose (ARRAY OF TEXT {NIL, "a:b"});
  Compose (ARRAY OF TEXT {NIL, NIL});
  Join (NIL, "base", NIL);
  Join (NIL, "base", "ext");
  Join ("dir", "base", "ext");
  Join ("a/b", "c.d", "e");
  Join ("a\\b", "c", NIL);
  Join ("/", "b", NIL);
  Join ("C:\\", "b", NIL);
  Join ("", "b", NIL);
  IO.Put ("parent " & Q (Parent) & " current " & Q (Current) & "\n");
  IO.Put (Fmt.Int (NUMBER (Names)) & " pathnames\n");
END Main.
