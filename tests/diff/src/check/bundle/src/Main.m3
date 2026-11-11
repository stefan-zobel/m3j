(* Bundle and Rsrc: the elements of a bundle that m3bundle made of three
   files (a text with the characters that m3bundle escapes, one of 5000
   characters, which m3bundle splits into blocks, and one with every byte
   value), and the resources of a path of a directory ("rsrc") and the
   bundle, in both orders, with Rsrc.Get and Rsrc.Open, an environment
   variable in a path, and a resource that is nowhere. *)

MODULE Main;

IMPORT Bundle, Env, Fmt, IO, Rd, Rsrc, Text, TestBundle, Thread;

<*FATAL Rd.Failure, Thread.Alerted, Rsrc.NotFound*>

PROCEDURE Line (t: TEXT) =
  BEGIN
    IO.Put (t & "\n");
  END Line;

PROCEDURE Q (t: TEXT): TEXT =
  (* t in brackets, with its characters below ' ' and above '~' as \ooo. *)
  VAR r := "[";  c: CHAR;
  BEGIN
    IF t = NIL THEN RETURN "NIL" END;
    FOR i := 0 TO Text.Length (t) - 1 DO
      c := Text.GetChar (t, i);
      IF c < ' ' OR c > '~' THEN
        r := r & "\\" & Fmt.Pad (Fmt.Int (ORD (c), 8), 3, '0');
      ELSE
        r := r & Text.FromChar (c);
      END;
    END;
    RETURN r & "]";
  END Q;

PROCEDURE Sum (t: TEXT): TEXT =
  (* The length of t and a checksum of its characters. *)
  VAR s := 0;
  BEGIN
    IF t = NIL THEN RETURN "NIL" END;
    FOR i := 0 TO Text.Length (t) - 1 DO
      s := (s * 31 + ORD (Text.GetChar (t, i))) MOD 1000000007;
    END;
    RETURN Fmt.Int (Text.Length (t)) & " chars, sum " & Fmt.Int (s);
  END Sum;

PROCEDURE Get (name: TEXT; path: Rsrc.Path): TEXT =
  BEGIN
    TRY
      RETURN Q (Rsrc.Get (name, path));
    EXCEPT
      Rsrc.NotFound => RETURN "NotFound";
    END;
  END Get;

PROCEDURE Open (name: TEXT; path: Rsrc.Path): TEXT =
  VAR rd: Rd.T;  t: TEXT;
  BEGIN
    TRY
      rd := Rsrc.Open (name, path);
      t := Rd.GetText (rd, LAST (CARDINAL));
      Rd.Close (rd);
      RETURN Q (t);
    EXCEPT
      Rsrc.NotFound => RETURN "NotFound";
    END;
  END Open;

VAR
  b := TestBundle.Get ();
  names := Bundle.EltNames (b);
  dirFirst := Rsrc.BuildPath ("rsrc", b);
  bundleFirst := Rsrc.BuildPath (b, "rsrc");
  r: TEXT;

BEGIN
  r := "";
  FOR i := 0 TO LAST (names^) DO r := r & " " & Q (names[i]) END;
  Line ("EltNames " & Fmt.Int (NUMBER (names^)) & ":" & r);
  Line ("hello.txt " & Q (Bundle.Get (b, "hello.txt")));
  Line ("big " & Sum (Bundle.Get (b, "big")) & ", again " & Sum (Bundle.Get (b, "big")));
  Line ("bytes.dat " & Sum (Bundle.Get (b, "bytes.dat")));
  Line ("nothing " & Q (Bundle.Get (b, "nothing")) & ", big.txt "
          & Q (Bundle.Get (b, "big.txt")));
  Line ("Get dir first: " & Get ("hello.txt", dirFirst) & " " & Get ("dironly.txt", dirFirst)
          & " " & Get ("bytes.dat", dirFirst));
  Line ("Get bundle first: " & Get ("hello.txt", bundleFirst) & " "
          & Get ("dironly.txt", bundleFirst) & " " & Get ("nothing", bundleFirst));
  Line ("Open: " & Open ("hello.txt", dirFirst) & " " & Open ("hello.txt", bundleFirst) & " "
          & Open ("nothing", dirFirst));
  Line ("Open big: " & Sum (Rsrc.Get ("big", bundleFirst)));
  Line ("only the bundle: " & Get ("hello.txt", Rsrc.BuildPath (b)) & " "
          & Get ("dironly.txt", Rsrc.BuildPath (b)));
  Line ("no path: " & Get ("hello.txt", NIL));
  Line ("$UNSET_VARIABLE_OF_BUNDLE_TEST: " & Get ("hello.txt", Rsrc.BuildPath ("$UNSET_VARIABLE_OF_BUNDLE_TEST", b))
          & " environment not empty " & Fmt.Bool (Env.Count > 0));
END Main.
