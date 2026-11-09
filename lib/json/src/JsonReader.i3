(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Reads JSON (RFC 8259) one piece after another, so a large document needs
   no more memory than its longest string.

|    r := JsonReader.FromRd (rd);
|    r.object ();
|    WHILE r.member () DO
|      IF r.is ("books") THEN
|        r.array ();
|        WHILE r.element () DO
|          r.object ();
|          WHILE r.member () DO
|            IF r.is ("id") THEN id := r.int ();
|            ELSIF r.is ("title") THEN title := r.text ();
|            ELSE r.skip ();
|            END;
|          END;
|        END;
|      ELSE
|        r.skip ();
|      END;
|    END;
|    r.finish ();

   "peek" tells what comes next, and every other method that moves takes one
   piece. The input must be exactly one value in UTF-8, with a byte order
   mark or without; there are no comments and no commas at the end of a list.

   A key can occur twice in an object: the program sees each one.

   A "T" belongs to one thread.

   Characters are Unicode. A character above 16_FF needs "Text.GetWideChar",
   as "Text.GetChar" truncates it, and one above 16_FFFF is two "WIDECHAR"s,
   a surrogate pair. *)

INTERFACE JsonReader;

IMPORT OSError, Rd, Thread;

TYPE
  Kind = {Object, ObjectEnd, Array, ArrayEnd, Key, Text, Number, Bool, Null, EOF};

  Problem = {Malformed, Encoding, Limit, Unexpected, BadValue, Closed};
  (* "Malformed", "Encoding" and "Limit" are errors of the document, and end
     the reader: every later call of a moving method raises "Closed". So do
     "finish", "close", and an exception of the reader of the input.
     "Unexpected" and "BadValue" come from the methods that check what the
     program expects. They take nothing from the input, so the program can
     go on, with "peek", "number" or "skip" for instance. *)

  Failure = REF RECORD
    problem: Problem;
    line, column: INTEGER;      (* from 1; the column counts bytes *)
    offset: INTEGER;            (* from 0, in bytes *)
    message: TEXT;              (* for people *)
  END;

EXCEPTION Error (Failure);

TYPE
  Options = RECORD
    replaceInvalid := FALSE;
    (* U+FFFD for what is not UTF-8 in a string, once for each maximal part
       that could have begun a character, and for a surrogate without its
       partner, instead of "Encoding". *)

    (* The limits; 0 means none. A string counts in "WIDECHAR"s. *)
    maxDepth: CARDINAL := 1000;
    maxStringChars: CARDINAL := 64 * 1024 * 1024;
    maxNumberChars: CARDINAL := 64 * 1024;
  END;

  T <: Public;
  Public = OBJECT
  METHODS
    peek (): Kind RAISES {Error, Rd.Failure, Thread.Alerted};
    (* What comes next; it takes nothing. "Key" is next in an object, and
       "EOF" after the value of the document. A piece is checked when it is
       taken, so a "Text" that "peek" reported can still be "Malformed". *)

    object () RAISES {Error, Rd.Failure, Thread.Alerted};
    array () RAISES {Error, Rd.Failure, Thread.Alerted};
    (* Takes the beginning of an object or array. *)

    member (): BOOLEAN RAISES {Error, Rd.Failure, Thread.Alerted};
    (* In an object: TRUE with the next key taken, and its value next. FALSE
       with the end of the object taken. *)

    element (): BOOLEAN RAISES {Error, Rd.Failure, Thread.Alerted};
    (* In an array: TRUE if an element is next, which the program must take
       before it calls "element" again, or that call is "Unexpected". FALSE
       with the end of the array taken. *)

    end () RAISES {Error, Rd.Failure, Thread.Alerted};
    (* Skips the rest of the innermost object or array, and takes its end. *)

    name (): TEXT;
    (* The key that "member" took, until its value is taken; otherwise "". *)

    is (key: TEXT): BOOLEAN;
    (* Whether "name () = key", without making a text. *)

    text (): TEXT RAISES {Error, Rd.Failure, Thread.Alerted};

    int (): INTEGER RAISES {Error, Rd.Failure, Thread.Alerted};
    (* A number without fraction and exponent; "BadValue" if it has one of
       them or does not fit. *)

    real (): LONGREAL RAISES {Error, Rd.Failure, Thread.Alerted};
    (* The nearest "LONGREAL" of any number; "BadValue" if that is not
       finite. A value too small for it gives zero. *)

    number (): TEXT RAISES {Error, Rd.Failure, Thread.Alerted};
    (* A number of any size and precision, as it stands in the input. *)

    bool (): BOOLEAN RAISES {Error, Rd.Failure, Thread.Alerted};

    null () RAISES {Error, Rd.Failure, Thread.Alerted};

    skip () RAISES {Error, Rd.Failure, Thread.Alerted};
    (* Takes the next value, with all that is in it, and checks it as the
       other methods do. *)

    finish () RAISES {Error, Rd.Failure, Thread.Alerted};
    (* The value of the document is taken, and nothing but white space
       follows: otherwise "Unexpected" or "Malformed". On a connection this
       waits for its end. Ends the reader. *)

    close ();
    (* Ends the reader. It closes what "Open" has opened, not the reader given
       to "FromRd". It is meant for a FINALLY, and can be called at any time. *)

    depth (): CARDINAL;
    (* The number of objects and arrays that are open. *)

    (* Where the reader is, as in "Failure". *)
    line (): INTEGER;
    column (): INTEGER;
    offset (): INTEGER;
  END;

CONST
  Defaults = Options {};

PROCEDURE FromRd (rd: Rd.T; READONLY o := Defaults): T;
(* A reader of the bytes of "rd". It asks for what is ready, so a connection
   does not have to fill a buffer. It reads ahead, so the position of "rd" is
   unspecified afterwards. *)

PROCEDURE FromText (t: TEXT; READONLY o := Defaults): T;
(* A reader of the characters of "t". Positions are those of its UTF-8 form. *)

PROCEDURE Open (path: TEXT; READONLY o := Defaults): T RAISES {OSError.E};
(* A reader of the file "path", which "close" closes. *)

PROCEDURE Format (f: Failure): TEXT;
(* The failure as "line:column: message". *)

END JsonReader.
