(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* The compression and the archives of the Java platform, for the modules
   Deflate and Zip of m3j: the procedures are methods of the class m3j.rt.Zip
   (through m3j.rt.Externals). A result less than 0 is one of the numbers
   below, negated. *)

INTERFACE M3jZip;

CONST
  Corrupt = 1;
  Unsupported = 2;
  Duplicate = 3;
  Closed = 4;
  Dictionary = 5;

  NoFlush = 0;
  SyncFlush = 1;
  Finish = 2;

<*EXTERNAL "M3jZip__NewInflate"*>
PROCEDURE NewInflate (raw: BOOLEAN): REFANY;

<*EXTERNAL "M3jZip__InflateFeed"*>
PROCEDURE InflateFeed (z: REFANY; READONLY b: ARRAY OF CHAR);
(* The array must not change until "InflateNeedsInput" or the end. *)

<*EXTERNAL "M3jZip__Inflate"*>
PROCEDURE Inflate (z: REFANY; VAR b: ARRAY OF CHAR): INTEGER;
(* The number of bytes put into b, which may be 0, or an error. *)

<*EXTERNAL "M3jZip__InflateNeedsInput"*>
PROCEDURE InflateNeedsInput (z: REFANY): BOOLEAN;

<*EXTERNAL "M3jZip__InflateFinished"*>
PROCEDURE InflateFinished (z: REFANY): BOOLEAN;

<*EXTERNAL "M3jZip__InflateRemaining"*>
PROCEDURE InflateRemaining (z: REFANY): INTEGER;
(* How many of the bytes that were fed the stream did not use. *)

<*EXTERNAL "M3jZip__InflateTotal"*>
PROCEDURE InflateTotal (z: REFANY): INTEGER;
(* The length of the output of this stream, modulo 2^32. *)

<*EXTERNAL "M3jZip__InflateReset"*>
PROCEDURE InflateReset (z: REFANY);

<*EXTERNAL "M3jZip__InflateEnd"*>
PROCEDURE InflateEnd (z: REFANY);

<*EXTERNAL "M3jZip__CrcByte"*>
PROCEDURE CrcByte (z: REFANY; b: INTEGER);
(* Adds a byte to the CRC of an inflating stream. *)

<*EXTERNAL "M3jZip__CrcValue"*>
PROCEDURE CrcValue (z: REFANY): INTEGER;

<*EXTERNAL "M3jZip__CrcReset"*>
PROCEDURE CrcReset (z: REFANY);

<*EXTERNAL "M3jZip__NewDeflate"*>
PROCEDURE NewDeflate (level: INTEGER; raw: BOOLEAN): REFANY;

<*EXTERNAL "M3jZip__DeflateFeed"*>
PROCEDURE DeflateFeed (z: REFANY; READONLY b: ARRAY OF CHAR);

<*EXTERNAL "M3jZip__Deflate"*>
PROCEDURE Deflate (z: REFANY; VAR b: ARRAY OF CHAR; mode: INTEGER): INTEGER;
(* The number of bytes put into b. With "SyncFlush" b must be longer than
   six bytes. *)

<*EXTERNAL "M3jZip__DeflateFinished"*>
PROCEDURE DeflateFinished (z: REFANY): BOOLEAN;

<*EXTERNAL "M3jZip__DeflateCrc"*>
PROCEDURE DeflateCrc (z: REFANY): INTEGER;

<*EXTERNAL "M3jZip__DeflateTotal"*>
PROCEDURE DeflateTotal (z: REFANY): INTEGER;
(* The length of the input of this stream, modulo 2^32. *)

<*EXTERNAL "M3jZip__DeflateEnd"*>
PROCEDURE DeflateEnd (z: REFANY);

<*EXTERNAL "M3jZip__OpenArchive"*>
PROCEDURE OpenArchive (path: TEXT; VAR code: INTEGER): REFANY;
(* The archive at "path", or NIL with code set. *)

<*EXTERNAL "M3jZip__CloseArchive"*>
PROCEDURE CloseArchive (a: REFANY);

<*EXTERNAL "M3jZip__ArchiveCount"*>
PROCEDURE ArchiveCount (a: REFANY): INTEGER;

<*EXTERNAL "M3jZip__ArchiveFind"*>
PROCEDURE ArchiveFind (a: REFANY; name: TEXT): INTEGER;
(* The number of the entry called "name", or -1. *)

<*EXTERNAL "M3jZip__EntryName"*>
PROCEDURE EntryName (a: REFANY; i: INTEGER): TEXT;

<*EXTERNAL "M3jZip__EntrySize"*>
PROCEDURE EntrySize (a: REFANY; i: INTEGER): INTEGER;

<*EXTERNAL "M3jZip__EntryCompressed"*>
PROCEDURE EntryCompressed (a: REFANY; i: INTEGER): INTEGER;

<*EXTERNAL "M3jZip__EntryCrc"*>
PROCEDURE EntryCrc (a: REFANY; i: INTEGER): INTEGER;

<*EXTERNAL "M3jZip__EntryTime"*>
PROCEDURE EntryTime (a: REFANY; i: INTEGER): INTEGER;
(* Milliseconds since 1970, or -1. *)

<*EXTERNAL "M3jZip__EntryDirectory"*>
PROCEDURE EntryDirectory (a: REFANY; i: INTEGER): BOOLEAN;

<*EXTERNAL "M3jZip__EntryOpen"*>
PROCEDURE EntryOpen (a: REFANY; i: INTEGER; VAR code: INTEGER): REFANY;
(* A stream of the data of the entry, or NIL with code set. *)

<*EXTERNAL "M3jZip__EntryRead"*>
PROCEDURE EntryRead (x: REFANY; VAR b: ARRAY OF CHAR): INTEGER;
(* The number of bytes put into b, 0 at the end, or an error. The end is
   reported only if the CRC and the length are right. *)

<*EXTERNAL "M3jZip__EntryClose"*>
PROCEDURE EntryClose (x: REFANY);

<*EXTERNAL "M3jZip__NewWriter"*>
PROCEDURE NewWriter (): REFANY;

<*EXTERNAL "M3jZip__WriterAdd"*>
PROCEDURE WriterAdd (w: REFANY; name: TEXT; millis, level: INTEGER): INTEGER;
(* Begins an entry and returns 0, or an error; after "Closed" the archive
   cannot go on. "millis" 0 means now. *)

<*EXTERNAL "M3jZip__WriterWrite"*>
PROCEDURE WriterWrite (w: REFANY; READONLY b: ARRAY OF CHAR): INTEGER;

<*EXTERNAL "M3jZip__WriterCloseEntry"*>
PROCEDURE WriterCloseEntry (w: REFANY): INTEGER;

<*EXTERNAL "M3jZip__WriterFinish"*>
PROCEDURE WriterFinish (w: REFANY): INTEGER;

<*EXTERNAL "M3jZip__WriterTake"*>
PROCEDURE WriterTake (w: REFANY; VAR b: ARRAY OF CHAR): INTEGER;
(* Moves what the archive has written into b; the number of bytes. *)

END M3jZip.
