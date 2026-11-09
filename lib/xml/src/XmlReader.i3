(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* A pull parser for XML: the program asks for one token after another, so
   a large document needs no more memory than its largest token.

|    r := XmlReader.FromRd (rd);
|    r.root ("catalog");
|    WHILE r.child () DO
|      IF r.is ("book") THEN
|        id := r.toInt (r.need ("id"));
|        WHILE r.child () DO
|          IF r.is ("title") THEN title := r.content () ELSE r.skip () END;
|        END;
|      ELSE
|        r.skip ();
|      END;
|    END;

   The parser is that of the Java platform, with namespaces. Documents come
   from untrusted places, so a document type declaration is skipped unread:
   nothing outside the document is ever fetched, and a reference to an
   entity other than the five predefined ones is an error.

   A "T" belongs to one thread.

   Characters are Unicode. A character above 16_FF needs "Text.GetWideChar",
   as "Text.GetChar" truncates it, and one above 16_FFFF is two "WIDECHAR"s,
   a surrogate pair. *)

INTERFACE XmlReader;

IMPORT OSError, Rd, Thread;

TYPE
  Kind = {None, Start, End, Text, Comment, PI, EOF};
  (* "None" is the state before the first token, and after an error. An
     empty element "<a/>" is a "Start" and an "End". "Text" holds all the
     character data up to the next markup that is reported: CDATA sections
     and character references are part of it, and so are the pieces around
     a comment or processing instruction that is not reported. *)

  Problem = {Malformed, Encoding, Limit, Unexpected, BadValue, Closed};
  (* "Malformed", "Encoding" and "Limit" are errors of the document, and
     end the reader: every later call of a moving method raises "Closed".
     So does "close". "Unexpected" and "BadValue" come from the methods
     that check what the program expects, and the reader can go on. *)

  Failure = REF RECORD
    problem: Problem;
    line, column: INTEGER;
    message: TEXT;              (* for people; in the language of the system
                                   if it comes from the parser *)
  END;

EXCEPTION Error (Failure);

TYPE
  Options = RECORD
    comments := FALSE;          (* report Kind.Comment *)
    instructions := FALSE;      (* report Kind.PI *)
    whitespace := TRUE;         (* report text that is only white space *)
    rejectDoctype := FALSE;     (* a document type declaration is an error *)
    (* The limits; 0 means none. "maxTokenChars" holds for one token,
       including text that was joined; on an attribute value it is exact
       only to about 8 KB. *)
    maxDepth := 256;
    maxAttributes := 1024;
    maxNameChars := 1024;
    maxTokenChars := 64 * 1024 * 1024;
  END;

  Attribute = RECORD local, uri, prefix, value: TEXT END;

  T <: Public;
  Public = OBJECT
  METHODS
    next (): Kind RAISES {Error, Rd.Failure, Thread.Alerted};
    (* Moves to the next token and returns its kind, "EOF" at and after the
       end of the document. An exception of the reader of the input ends
       this reader as an error of the document does. *)

    kind (): Kind;

    depth (): CARDINAL;
    (* The number of elements that are open at this token, where an
       element is open at its own "Start" and "End": 1 for those of the
       root element and for the text directly in it. *)

    close ();
    (* Ends the reader. It closes what "Open" has opened, not the reader
       given to "FromRd". *)

    (* The names of the element of a "Start" or "End", otherwise "". A name
       without a namespace or prefix has "" for those. *)
    local (): TEXT;
    uri (): TEXT;
    prefix (): TEXT;
    qname (): TEXT;             (* "prefix:local", or "local" *)

    is (local: TEXT; uri: TEXT := NIL): BOOLEAN;
    (* Whether this is a "Start" or "End" of that name. With "uri = NIL" the
       namespace does not matter. *)

    (* The attributes of a "Start"; declarations of namespaces are not
       among them. *)
    attr (local: TEXT; uri: TEXT := ""): TEXT;
    (* The value of the attribute, or NIL if there is none. By default the
       attribute has no namespace, as is the case for one without a prefix.
       With "uri = NIL" the namespace does not matter. *)

    need (local: TEXT; uri: TEXT := ""): TEXT RAISES {Error};
    (* Like "attr", but an attribute that is missing is "Unexpected". *)

    attrCount (): CARDINAL;
    attrAt (i: CARDINAL): Attribute;
    (* A checked runtime error unless "i < attrCount ()". *)

    lookup (prefix: TEXT): TEXT;
    (* What "prefix" ("" for the default) stands for at a "Start" or "End",
       or NIL. *)

    value (): TEXT;
    (* The text of a "Text" or "Comment", the data of a "PI", otherwise "". *)

    target (): TEXT;
    (* The target of a "PI", otherwise "". *)

    (* To go through a document by its structure. *)

    root (local: TEXT := NIL; uri: TEXT := NIL)
      RAISES {Error, Rd.Failure, Thread.Alerted};
    (* Moves to the "Start" of the root element, and checks its name as
       "expect" does if "local" is not NIL. "Unexpected" if the root element
       has been reached before. *)

    child (): BOOLEAN RAISES {Error, Rd.Failure, Thread.Alerted};
    (* Moves forward over text, comments and instructions: TRUE at the next
       "Start", FALSE at the next "End" or at the end of the document.
       In a loop over the children of an element, the body must consume
       the child: with "content", "skip", or a loop of its own that ends
       at the "End" of the child. *)

    content (): TEXT RAISES {Error, Rd.Failure, Thread.Alerted};
    (* At a "Start": moves to the matching "End" and returns the text of the
       element, "" if there is none. "Unexpected" if the element has child
       elements, or if this is not a "Start". *)

    skip () RAISES {Error, Rd.Failure, Thread.Alerted};
    (* At a "Start": moves to the matching "End". "Unexpected" if this is not
       a "Start". *)

    expect (local: TEXT; uri: TEXT := NIL) RAISES {Error};
    (* "Unexpected" unless this is a "Start" for which "is" holds. *)

    (* Conversions; a value that does not fit, or NIL, is "BadValue" with
       the position of this token. White space around the value is ignored. *)
    toInt (t: TEXT): INTEGER RAISES {Error};
    toBool (t: TEXT): BOOLEAN RAISES {Error};   (* true, false, 1 or 0 *)
    toReal (t: TEXT): LONGREAL RAISES {Error};

    (* Where the parser is: after the current token, from 1 and in
       characters. *)
    line (): INTEGER;
    column (): INTEGER;
    offset (): INTEGER;

    (* Once the first token is read. NIL if the document does not declare a
       version, or for the encoding if the document is a text; otherwise
       the encoding in use, by the name of the Java platform. *)
    version (): TEXT;
    encoding (): TEXT;
  END;

CONST Defaults = Options {};

PROCEDURE FromRd (rd: Rd.T; READONLY o := Defaults): T;
(* A reader of the bytes of "rd", in the encoding that the document has by
   the rules of XML. It reads ahead, so the position of "rd" is unspecified
   afterwards. A byte order mark or an encoding declaration selects the
   encoding; the default is UTF-8; the mark wins over the declaration. *)

PROCEDURE FromText (t: TEXT; READONLY o := Defaults): T;
(* A reader of the characters of "t". An encoding declaration in it has no
   effect. *)

PROCEDURE Open (path: TEXT; READONLY o := Defaults): T RAISES {OSError.E};
(* A reader of the file "path", which "close" closes. *)

PROCEDURE Format (f: Failure): TEXT;
(* The failure as "line:column: message". *)

END XmlReader.
