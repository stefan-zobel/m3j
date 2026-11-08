(* The packages json and libbuf: JSON texts parsed from a text, an array
   of characters, a reader and a file; the nodes found by paths, their
   kinds, names and values; iteration; the formatted and the raw text;
   adding, updating and deleting nodes; copies; texts with escapes;
   numbers; errors. Buf reads a file and a text, Wx builds a text and
   writes it. The program writes and deletes a file "json.tmp" in the
   current directory. *)

MODULE Main;

IMPORT Buf, FileWr, Fmt, FS, IO, Json, OSError, Rd, Text, TextRd, TextWr,
       Thread, Wr, Wx;

<*FATAL Wr.Failure, Thread.Alerted, OSError.E, Rd.Failure*>

CONST File = "json.tmp";

CONST Sample = "{\"name\": \"sam\", \"obj\": {\"elt1\": \"hydrogen\", "
  & "\"elt2\": \"oxygen\", \"number\": 23}, \"arr1\": [\"red\", "
  & "\"yellow\", \"blue\", \"green\", 9, 8], \"real\": 12.3e2, "
  & "\"neg\": -42, \"flag\": true, \"off\": false, \"none\": null, "
  & "\"empty\": {}, \"nothing\": []}";

PROCEDURE Show (node: Json.T; path: TEXT) =
  VAR n: Json.T;
  BEGIN
    n := node.find (path);
    IF n = NIL THEN
      IO.Put ("find " & path & ": NIL\n");
    ELSE
      IO.Put ("find " & path & ": name " & n.name () & ", kind "
                & Json.NK[n.kind ()] & ", value " & n.value () & ", size "
                & Fmt.Int (n.size ()) & "\n");
    END;
  END Show;

PROCEDURE Walk (node: Json.T; indent: TEXT) =
  VAR iter := node.iterate ();  name: TEXT;  child: Json.T;
  BEGIN
    WHILE iter.next (name, child) DO
      IO.Put (indent & name & " " & Json.NK[child.kind ()] & " "
                & child.value () & "\n");
      (* iterate fails on an empty object or array (it has no table) *)
      IF (child.kind () = Json.NodeKind.nkObject
            OR child.kind () = Json.NodeKind.nkArray) AND child.size () > 0 THEN
        Walk (child, indent & "  ");
      END;
    END;
  END Walk;

PROCEDURE Parse (title, text: TEXT): Json.T =
  VAR node: Json.T;
  BEGIN
    TRY
      node := Json.ParseBuf (text);
      IO.Put (title & ": parsed, " & Json.NK[node.kind ()] & ", size "
                & Fmt.Int (node.size ()) & "\n");
      RETURN node;
    EXCEPT
      Json.E => IO.Put (title & ": Json.E\n"); RETURN NIL;
    END;
  END Parse;

PROCEDURE Getters (node: Json.T) =
  BEGIN
    TRY
      IO.Put ("getInt /obj/number: " & Fmt.Int (node.find ("/obj/number").getInt ()) & "\n");
      IO.Put ("getInt /neg: " & Fmt.Int (node.find ("/neg").getInt ()) & "\n");
      IO.Put ("getFloat /real: " & Fmt.LongReal (node.find ("/real").getFloat ()) & "\n");
      IO.Put ("getBool /flag: " & Fmt.Bool (node.find ("/flag").getBool ()) & "\n");
      IO.Put ("getBool /off: " & Fmt.Bool (node.find ("/off").getBool ()) & "\n");
    EXCEPT
      Json.E => IO.Put ("a getter: Json.E\n");
    END;
    TRY
      IO.Put ("getInt /name: " & Fmt.Int (node.find ("/name").getInt ()) & "\n");
    EXCEPT
      Json.E => IO.Put ("getInt /name: Json.E\n");
    END;
    TRY
      IO.Put ("getBool /none: " & Fmt.Bool (node.find ("/none").getBool ()) & "\n");
    EXCEPT
      Json.E => IO.Put ("getBool /none: Json.E\n");
    END;
  END Getters;

PROCEDURE Edits (root: Json.T) =
  VAR obj, arr, c: Json.T;
  BEGIN
    TRY
      obj := root.find ("/obj");
      EVAL obj.addText ("elt3", "carbon");
      EVAL obj.addInt ("count", 7);
      EVAL obj.addFloat ("ratio", 0.5D0);
      EVAL obj.addBool ("ok", FALSE);
      EVAL obj.addNull ("nil");
      EVAL obj.addText ("elt1", "helium");   (* replaces *)
      arr := root.find ("/arr1");
      EVAL arr.addText ("6", "black");
      EVAL arr.addInt ("7", 100);
      c := root.addObj ("child");
      EVAL c.addText ("inner", "value");
      EVAL c.addArr ("list");
      EVAL root.find ("/child/list").addInt ("0", 1);
      EVAL root.find ("/child/list").addInt ("1", 2);
    EXCEPT
      Json.E => IO.Put ("an add: Json.E\n");
    END;
    TRY
      EVAL root.find ("/name").addText ("x", "y");
      IO.Put ("add to a text: no exception\n");
    EXCEPT
      Json.E => IO.Put ("add to a text: Json.E\n");
    END;
    root.updateText ("/obj/elt2", "nitrogen");
    root.updateInt ("/obj/number", 24);
    root.updateBool ("/flag", FALSE);
    root.updateFloat ("/real", 2.5D0);
    Show (root, "/obj/elt1");
    Show (root, "/obj/elt2");
    Show (root, "/obj/number");
    Show (root, "/arr1/6");
    Show (root, "/child/list/1");
    c := root.delete ("/obj/elt3");
    IO.Put ("delete /obj/elt3: " & Fmt.Bool (c # NIL) & "\n");
    c := root.delete ("missing");
    IO.Put ("delete missing: " & Fmt.Bool (c # NIL) & "\n");
    Show (root, "/obj/elt3");
    IO.Put ("after the edits:\n" & root.format () & "\n");
  END Edits;

PROCEDURE Copies (root: Json.T) =
  VAR c := root.copy ();
  BEGIN
    IO.Put ("copy equal: " & Fmt.Bool (Text.Equal (c.format (), root.format ())) & "\n");
    c.updateText ("/name", "max");
    Show (root, "/name");
    Show (c, "/name");
    c.find ("/obj").clear ();
    Show (c, "/obj");
    Show (root, "/obj");
    IO.Put ("root of /obj/count is the root: "
              & Fmt.Bool (root.find ("/obj/count").root () = root) & "\n");
  END Copies;

PROCEDURE Escapes () =
  VAR node: Json.T;
  BEGIN
    node := Parse ("escapes", "{\"a\": \"line\\nbreak\", \"b\": \"tab\\there\", "
      & "\"c\": \"quote\\\" and backslash\\\\\", \"d\": \"slash\\/\", "
      & "\"e\": \"\\u0041\\u00e9\"}");
    IF node # NIL THEN
      Show (node, "/a");
      Show (node, "/b");
      Show (node, "/c");
      Show (node, "/d");
      Show (node, "/e");
      IO.Put ("raw: " & node.rawText () & "\n");
    END;
  END Escapes;

PROCEDURE Numbers () =
  VAR node: Json.T;
  BEGIN
    node := Parse ("numbers", "[0, -0, 1, -1, 123456789012, 1.5, -2.25e-3, "
      & "6E10, 1e+2, 0.1]");
    IF node # NIL THEN Walk (node, "  "); END;
  END Numbers;

PROCEDURE Errors () =
  BEGIN
    EVAL Parse ("empty text", "");
    EVAL Parse ("unclosed object", "{\"a\": 1");
    EVAL Parse ("unclosed array", "[1, 2");
    EVAL Parse ("missing colon", "{\"a\" 1}");
    EVAL Parse ("bad keyword", "{\"a\": nul}");
    EVAL Parse ("unclosed text", "{\"a\": \"abc}");
    EVAL Parse ("trailing comma", "[1, 2,]");
    EVAL Parse ("scalar", "42");
    EVAL Parse ("nested", "{\"a\": [{\"b\": [[], {}]}]}");
  END Errors;

PROCEDURE Sources () =
  VAR
    chars := NEW (REF ARRAY OF CHAR, Text.Length (Sample) + 1);
    node: Json.T;
    wr: Wr.T;
  BEGIN
    (* ParseArr needs the character '\000' at the end, as ParseBuf
       puts it *)
    Text.SetChars (chars^, Sample);
    chars[LAST (chars^)] := '\000';
    TRY
      node := Json.ParseArr (chars);
      IO.Put ("ParseArr: size " & Fmt.Int (node.size ()) & "\n");
      node := Json.ParseStream (TextRd.New (Sample));
      IO.Put ("ParseStream: size " & Fmt.Int (node.size ()) & "\n");
      wr := FileWr.Open (File);
      Wr.PutText (wr, node.format ());
      Wr.Close (wr);
      node := Json.ParseFile (File);
      IO.Put ("ParseFile: size " & Fmt.Int (node.size ()) & "\n");
    EXCEPT
      Json.E => IO.Put ("a source: Json.E\n");
    END;
  END Sources;

PROCEDURE Buffers () =
  VAR b: Buf.T;  w := Wx.New ();  wr := TextWr.New ();
  BEGIN
    b := Buf.FromFile (File, NIL, pad := 3);
    IO.Put ("Buf.FromFile: " & Fmt.Int (NUMBER (b^)) & " characters, the first "
              & Text.FromChars (SUBARRAY (b^, 0, 10)) & ", the padding "
              & Fmt.Int (ORD (b[LAST (b^)])) & "\n");
    b := Buf.FromText ("abc");
    IO.Put ("Buf.FromText: " & Text.FromChars (b^) & "\n");
    FOR i := 1 TO 3000 DO
      Wx.PutChar (w, VAL (ORD ('a') + i MOD 26, CHAR));
    END;
    Wx.PutText (w, " end ");
    Wx.PutInt (w, -12345);
    Wx.PutStr (w, ARRAY OF CHAR {'x', 'y'});
    IO.Put ("Wx length: " & Fmt.Int (Wx.GetLength (w)) & "\n");
    WITH t = Wx.ToText (w) DO
      IO.Put ("Wx.ToText: " & Fmt.Int (Text.Length (t)) & " characters, ends with "
                & Text.Sub (t, Text.Length (t) - 15) & "\n");
    END;
    IO.Put ("Wx length after ToText: " & Fmt.Int (Wx.GetLength (w)) & "\n");
    Wx.PutText (w, "to a writer");
    Wx.ToWr (w, wr);
    IO.Put ("Wx.ToWr: " & TextWr.ToText (wr) & "\n");
    FS.DeleteFile (File);
  END Buffers;

VAR root: Json.T;

BEGIN
  root := Parse ("sample", Sample);
  IO.Put ("format:\n" & root.format () & "\n");
  IO.Put ("raw: " & root.rawText () & "\n");
  Walk (root, "  ");
  Show (root, "/name");
  Show (root, "/obj/elt2");
  Show (root, "/arr1/2");
  Show (root, "/arr1/4");
  Show (root, "/arr1/9");
  Show (root, "/missing");
  Show (root, "/obj/missing/deeper");
  Show (root, "/empty");
  Show (root, "/nothing");
  Show (root, "/none");
  Getters (root);
  Edits (root);
  Copies (root);
  Escapes ();
  Numbers ();
  Errors ();
  Sources ();
  Buffers ();
END Main.
