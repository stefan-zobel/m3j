(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Writes JSON (RFC 8259) one piece after another, so a large document needs
   no more memory than what is open.

|    w := JsonWriter.ToWr (wr, JsonWriter.Options {indent := "  "});
|    w.object ();
|    w.array ("books");
|    w.object ();
|    w.int (id, "id");
|    w.text (title, "title");
|    w.end ();
|    w.end ();
|    w.end ();
|    w.finish ();

   The writer checks what it is given, so that the result is a document that
   parsers read back as it was written: the characters, the numbers and the
   order of the calls. The output is UTF-8.

   The first error ends the writer, as does a "Wr.Failure" or "Thread.Alerted"
   of "wr"; every later call raises "Closed". What was written up to then is
   the beginning of a valid document.

   A "T" belongs to one thread. A text must not be NIL; a key of NIL means
   that there is none. A character above 16_FFFF is a surrogate pair in one
   TEXT. *)

INTERFACE JsonWriter;

IMPORT OSError, Thread, Wr;

TYPE
  Problem = {Content, Structure, Closed};
  (* "Content": a character or a number that JSON does not have.
     "Structure": a call in the wrong place, a key that the object has
     already, or more nesting than "maxDepth". *)

  Failure = REF RECORD
    problem: Problem;
    message: TEXT;              (* for people *)
  END;

EXCEPTION Error (Failure);

TYPE
  Options = RECORD
    indent: TEXT := NIL;
    (* NIL writes no white space. Otherwise spaces and tabs, for each level;
       lines end with a newline on every system, and so does the document. *)
    replaceInvalid := FALSE;
    (* U+FFFD for a surrogate without its partner, and null for a "real" that
       is not finite, instead of "Content". Keys that differ only in such
       characters are then not found to be the same. *)
    uniqueKeys := TRUE;
    (* A key that the object has already is an error, since parsers keep only
       one of them. This holds the keys of the open objects in memory, which
       FALSE avoids. *)
    maxDepth: CARDINAL := 1000;
    (* The most nesting of objects and arrays, which is what "JsonReader"
       reads by default. 0 means no limit. *)
  END;

  T <: Public;
  Public = OBJECT
  METHODS
    (* In an object each value needs a key: the argument "key" of the value,
       or a call of "key" before it. Elsewhere a value has no key. *)

    object (key: TEXT := NIL) RAISES {Error, Wr.Failure, Thread.Alerted};

    array (key: TEXT := NIL) RAISES {Error, Wr.Failure, Thread.Alerted};

    end () RAISES {Error, Wr.Failure, Thread.Alerted};
    (* Ends the innermost object or array. *)

    key (k: TEXT) RAISES {Error, Wr.Failure, Thread.Alerted};
    (* The key of the value that follows, for a procedure that writes a value
       without knowing where it goes. *)

    text (t: TEXT; key: TEXT := NIL)
      RAISES {Error, Wr.Failure, Thread.Alerted};

    int (i: INTEGER; key: TEXT := NIL)
      RAISES {Error, Wr.Failure, Thread.Alerted};

    real (r: LONGREAL; key: TEXT := NIL)
      RAISES {Error, Wr.Failure, Thread.Alerted};
    (* The shortest text that reads back as "r", with a fraction or an
       exponent, so that a reader takes it for a floating-point number.
       JSON has no infinities and no NaN. *)

    bool (b: BOOLEAN; key: TEXT := NIL)
      RAISES {Error, Wr.Failure, Thread.Alerted};

    null (key: TEXT := NIL) RAISES {Error, Wr.Failure, Thread.Alerted};

    number (t: TEXT; key: TEXT := NIL)
      RAISES {Error, Wr.Failure, Thread.Alerted};
    (* A number given as a text, of any size and precision. It is "Content"
       if "t" is not a number of JSON. *)

    depth (): CARDINAL;
    (* The number of objects and arrays that are open. *)

    flush () RAISES {Wr.Failure, Thread.Alerted};
    (* Hands over all that is written and flushes "wr", also after an error. *)

    finish () RAISES {Error, Wr.Failure, Thread.Alerted};
    (* The document is complete: "Structure" if it has no value or something
       is open. Flushes "wr", and ends the writer. *)

    close () RAISES {Wr.Failure, Thread.Alerted};
    (* Ends the writer, without writing what is pending. It closes what
       "Open" has opened, not the writer given to "ToWr". It is meant for a
       FINALLY, and can be called at any time. *)
  END;

CONST
  Defaults = Options {};

PROCEDURE ToWr (wr: Wr.T; READONLY o := Defaults): T;
(* A writer of bytes to "wr". It is a checked runtime error if "o.indent" has
   other characters than spaces and tabs. *)

PROCEDURE Open (path: TEXT; READONLY o := Defaults): T RAISES {OSError.E};
(* A writer of the file "path", which "close" closes. *)

PROCEDURE Format (f: Failure): TEXT;
(* The message of the failure. *)

END JsonWriter.
