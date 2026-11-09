(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Reading and writing archives in the ZIP format, with the classes of the
   Java platform:

|    r := Zip.Open ("a.zip");
|    FOR i := 0 TO r.count () - 1 DO
|      IF Zip.SafeName (r.entry (i).name) THEN
|        text := Rd.GetText (r.open (i, maxBytes := 1000000), 1000000);
|      END;
|    END;
|    r.close ();
|
|    w := Zip.ToWr (FileWr.Open ("b.zip"));
|    Wr.PutText (w.add ("hello.txt"), "Hello\n");
|    w.finish (); *)

INTERFACE Zip;

IMPORT Atom, AtomList, OSError, Rd, Thread, Time, Wr;

EXCEPTION Error (AtomList.T);

VAR Corrupt, Unsupported, Duplicate: Atom.T;

(* An "Error" carries one of these atoms and an atom with a description.

   "Corrupt": the file is not an archive or breaks the rules of the format.
   It is the same atom as "Deflate.Corrupt".

   "Unsupported": the archive uses something that the platform cannot read,
   such as encryption or an unknown method of compression, or a name is too
   long or otherwise cannot be written.

   "Duplicate": two entries have the same name.

   A reader of an entry raises "Rd.Failure" with "Corrupt", also if the CRC or
   the length of the data is wrong, which is only noticed at its end, or with
   "Deflate.TooLarge". *)

TYPE
  Entry = RECORD
    name: TEXT;
    size, compressedSize: INTEGER;
    crc: INTEGER;
    modified: Time.T;
    directory: BOOLEAN;
  END;

(* "size" and "compressedSize" are in bytes and "crc" is the CRC-32 of the
   data, as the archive states them. "modified" is 0.0 if the archive has no
   time. "directory" tells that the name ends with a slash. *)

  Reader <: ReaderPublic;
  ReaderPublic = OBJECT
  METHODS
    count (): CARDINAL;
    entry (i: CARDINAL): Entry;
    find (name: TEXT; VAR i: CARDINAL): BOOLEAN;
    open (i: CARDINAL; maxBytes := 0): Rd.T RAISES {Error};
    close ();
  END;

(* The entries are numbered from 0, in the order of the directory of the
   archive. A number outside of "count" is a checked runtime error.

   "r.find(name, i)" sets "i" to the number of the entry called "name" and
   returns "TRUE", or returns "FALSE" if there is none.

   "r.open(i, maxBytes)" returns a reader of the data of the entry. "maxBytes",
   if positive, limits its output, in the way of "Deflate.ReadOptions"; set it
   for archives from elsewhere. Several readers may be open at the same time.

   "r.close()" frees the archive; its readers then fail.

   The names are those in the archive, and a name such as "../x" is valid
   there: a program that makes files from them must check them, see
   "SafeName". *)

  Writer <: WriterPublic;
  WriterPublic = OBJECT
  METHODS
    add (name: TEXT; modified := 0.0D0; level := -1): Wr.T
      RAISES {Error, Wr.Failure, Thread.Alerted};
    finish () RAISES {Wr.Failure, Thread.Alerted};
  END;

(* "w.add(name, modified, level)" begins an entry and returns the writer of
   its data, which is valid until the next "add" or "finish", or until it is
   closed. "modified" 0.0 means the present time, and "level" is that of
   "Deflate.WriteOptions". A name that ends with a slash makes a directory.
   A name has at most 65000 bytes in UTF-8. "Error" with "Duplicate" or
   "Unsupported" leaves the writer usable.

   "w.finish()" ends the last entry and writes the directory of the archive.
   It does not flush or close the target, which the program does.

   After a failure of the target or an alert all further calls raise
   "Wr.Failure". A "Writer" and the writers of its entries belong to one
   thread. *)

PROCEDURE Open (path: TEXT): Reader RAISES {OSError.E, Error};
(* The archive in the file "path", which must not change while it is open. An
   archive in which two entries have the same name is refused with "Duplicate",
   because different programs read different data from it. *)

PROCEDURE ToWr (wr: Wr.T): Writer;
(* A new archive that goes to "wr". *)

PROCEDURE SafeName (name: TEXT): BOOLEAN;
(* Can "name" be used as a relative path below a directory? It must not be
   empty or absolute, have a colon as its second character, as a drive has,
   or contain a backslash, NUL or a step "..". *)

END Zip.
