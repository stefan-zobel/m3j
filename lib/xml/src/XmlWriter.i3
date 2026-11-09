(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Writes XML one piece after another, so a large document needs no more
   memory than the open elements.

|    w := XmlWriter.ToWr (wr, XmlWriter.Options {indent := "  "});
|    w.start ("catalog", uri := "urn:books");
|    w.start ("book");
|    w.attr ("id", Fmt.Int (id));
|    w.element ("title", title);
|    w.end ();
|    w.end ();
|    w.finish ();

   The writer checks what it is given, so that the result is a well-formed
   document that parsers read back as it was written: names, characters and
   the order of the calls. The output is UTF-8.

   The first error ends the writer, as does a "Wr.Failure" or "Thread.Alerted"
   of "wr"; every later call raises "Closed". What was written up to then is
   the beginning of a well-formed document.

   A "T" belongs to one thread. Arguments other than "uri" and "prefix" must
   not be NIL. A character above 16_FFFF is a surrogate pair in one TEXT. *)

INTERFACE XmlWriter;

IMPORT OSError, Thread, Wr;

TYPE
  Problem = {Name, Content, Structure, Namespace, Closed};
  (* "Name": not a name, or a target of an instruction that is reserved.
     "Content": a character that XML does not have, or text that the markup
     around it cannot hold ("--" in a comment, "?>" in an instruction).
     "Structure": a call in the wrong place.
     "Namespace": a prefix or binding that namespaces do not allow. *)

  Failure = REF RECORD
    problem: Problem;
    message: TEXT;              (* for people *)
  END;

EXCEPTION Error (Failure);

TYPE
  Options = RECORD
    declaration := TRUE;        (* begin with <?xml version="1.0" ...?> *)
    indent: TEXT := NIL;
    (* NIL writes no white space of its own. Otherwise spaces and tabs, for
       each level, and lines end with a newline on every system. Nothing
       is added within an element once it has text, or within xml:space
       "preserve"; but if text follows a child, the white space before the
       child has been written. *)
    selfClose := TRUE;          (* <a/> rather than <a></a> *)
    replaceInvalid := FALSE;    (* U+FFFD for a character that XML does not
                                   have, instead of "Content" *)
  END;

  T <: Public;
  Public = OBJECT
  METHODS
    start (local: TEXT; uri, prefix: TEXT := NIL)
      RAISES {Error, Wr.Failure, Thread.Alerted};
    (* Opens an element. Its start tag waits until something follows, so
       "attr" and "declare" come after this call, in any order.

       With "uri = NIL" the name is written as it is, and belongs to the
       default namespace in scope; so children take the namespace of their
       parent. "uri = """ means no namespace, with xmlns="" if that is
       needed. Otherwise "prefix", or one already bound to "uri" in scope,
       or else the default namespace on this element.
       With "uri = NIL", "prefix" must be bound already. *)

    attr (local, value: TEXT; uri: TEXT := ""; prefix: TEXT := NIL)
      RAISES {Error, Wr.Failure, Thread.Alerted};
    (* Adds an attribute to the element just opened. An attribute with a
       namespace needs a prefix, as the default namespace does not apply to
       attributes: "prefix", or one bound to "uri" in scope, or else a new
       one, "ns1", "ns2" and so on. *)

    declare (prefix, uri: TEXT)
      RAISES {Error, Wr.Failure, Thread.Alerted};
    (* Binds "prefix" ("" for the default namespace) to "uri" on the element
       just opened, for example at the root for the sake of its children. *)

    end () RAISES {Error, Wr.Failure, Thread.Alerted};

    element (local, text: TEXT; uri, prefix: TEXT := NIL)
      RAISES {Error, Wr.Failure, Thread.Alerted};
    (* "start", "text" and "end". *)

    text (t: TEXT) RAISES {Error, Wr.Failure, Thread.Alerted};

    cdata (t: TEXT) RAISES {Error, Wr.Failure, Thread.Alerted};
    (* Text as a CDATA section, in several if it holds "]]>" or a carriage
       return, so that it is read back as it is. *)

    comment (t: TEXT) RAISES {Error, Wr.Failure, Thread.Alerted};

    pi (target: TEXT; data: TEXT := "")
      RAISES {Error, Wr.Failure, Thread.Alerted};

    depth (): CARDINAL;
    (* The number of elements that are open. *)

    flush () RAISES {Wr.Failure, Thread.Alerted};
    (* Hands over all that is written and flushes "wr", also after an error,
       but not the start tag that is still waiting. *)

    finish () RAISES {Error, Wr.Failure, Thread.Alerted};
    (* The document is complete: "Structure" if it has no root or an
       element is open. Flushes "wr", and ends the writer. *)

    close () RAISES {Wr.Failure, Thread.Alerted};
    (* Ends the writer, without writing what is pending. It closes what
       "Open" has opened, not the writer given to "ToWr". It is meant for a
       FINALLY, and can be called at any time. *)
  END;

CONST
  XmlNS = "http://www.w3.org/XML/1998/namespace";
  (* The namespace of the prefix "xml", which is always bound. *)

  Defaults = Options {};

PROCEDURE ToWr (wr: Wr.T; READONLY o := Defaults): T;
(* A writer of bytes to "wr". A "TextWr" gets the bytes in UTF-8 as a TEXT,
   for "XmlReader.FromRd (TextRd.New (t))". It is a checked runtime error if
   "o.indent" has other characters than spaces and tabs. *)

PROCEDURE Open (path: TEXT; READONLY o := Defaults): T RAISES {OSError.E};
(* A writer of the file "path", which "close" closes. *)

PROCEDURE ValidName (t: TEXT): BOOLEAN;
(* Whether the writer takes "t" as a local name or prefix. *)

(* For values, in the forms of XML Schema, which "XmlReader" reads. An integer
   is "Fmt.Int". *)
PROCEDURE Bool (b: BOOLEAN): TEXT;
(* "true" or "false". *)

PROCEDURE Real (r: LONGREAL): TEXT;
(* The shortest text that reads back as "r"; "INF", "-INF" or "NaN". *)

PROCEDURE Format (f: Failure): TEXT;
(* The message of the failure. *)

END XmlWriter.
