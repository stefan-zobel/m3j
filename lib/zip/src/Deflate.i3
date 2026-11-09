(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

(* Readers and writers that decompress and compress streams in the formats
   of the Deflate family, with the compressor of the Java platform:

|    wr := Deflate.NewWr (FileWr.Open ("a.gz"), o := Deflate.WriteOptions {closeTarget := TRUE});
|    Wr.PutText (wr, "Hello\n");
|    Wr.Close (wr);
|
|    rd := Deflate.NewRd (FileRd.Open ("a.gz"), o := Deflate.ReadOptions {closeSource := TRUE});
|    text := Rd.GetText (rd, LAST (CARDINAL));
|    Rd.Close (rd);

   A reader or writer of this interface is a stream of bytes, one "CHAR" for
   each byte. *)

INTERFACE Deflate;

IMPORT Atom, Rd, Thread, Wr;

TYPE Format = {Gzip, Zlib, Raw};

(* "Gzip" is the format of RFC 1952, "Zlib" that of RFC 1950, and "Raw" the
   plain data of RFC 1951, without a header or a checksum. *)

VAR Corrupt, Truncated, TooLarge: Atom.T;

(* A reader raises "Rd.Failure" with one of these atoms and an atom with a
   description. "Corrupt": the data breaks the rules of the format or does not
   match its checksum. "Truncated": the source ends before the data does.
   "TooLarge": the output exceeds "maxBytes". After one of them every further
   read raises the same failure. A failure of the source or an alert passes
   through unchanged. *)

TYPE
  ReadOptions = RECORD
    maxBytes := 0;
    concatenated := TRUE;
    closeSource := FALSE;
  END;

(* "maxBytes", if positive, limits the bytes of output: what a small input can
   expand to is limited only by this. Set it for data from elsewhere.

   "concatenated" lets one "Gzip" member follow another, as the "gzip" program
   does, and the output is that of all of them; zero bytes may stand between
   and after them. Anything else after a member is "Corrupt". Without this
   option the reader ends after the first member. *)

  WriteOptions = RECORD
    level := -1;
    closeTarget := FALSE;
  END;

(* "level" goes from 0, no compression, to 9, the smallest output; -1 is the
   default of the compressor. Any other value is a checked runtime error. *)

CONST
  ReadDefaults = ReadOptions {};
  WriteDefaults = WriteOptions {};

PROCEDURE NewRd (src: Rd.T; format := Format.Gzip; READONLY o := ReadDefaults): Rd.T;
(* A reader of the decompressed data from "src". It is not seekable and it reads
   ahead, so the position of "src" is unspecified afterwards. It has the data
   that is there as soon as it can be decompressed, and it does not wait for
   more than it needs.

   An empty source is an empty "Gzip" stream; for the other formats it is
   "Truncated". The memory of the decompressor is given back at the end of the
   data, after a failure and by "Rd.Close", which closes "src" only if
   "closeSource" is set. *)

PROCEDURE NewWr (dst: Wr.T; format := Format.Gzip; READONLY o := WriteDefaults): Wr.T;
(* A writer that compresses to "dst". It is not seekable. A "Gzip" header has
   no time and no name, so equal input gives equal output.

   "Wr.Flush" passes on everything so far, ending a block of the compressed
   data, and flushes "dst"; many flushes make the output larger.

   "Wr.Close" ends the stream, flushes "dst" and gives back the memory of the
   compressor; it closes "dst" only if "closeTarget" is set. After a failure
   of "dst" or an alert all further writes raise "Wr.Failure", as the stream
   has a hole. *)

PROCEDURE Finish (wr: Wr.T) RAISES {Wr.Failure, Thread.Alerted};
(* Ends the stream of "wr", which must come from "NewWr", without flushing
   "dst" and without closing anything. Writing to "wr" afterwards raises
   "Wr.Failure". Does nothing if the stream has ended. *)

PROCEDURE Discard (wr: Wr.T);
(* Gives up the stream of "wr", which must come from "NewWr", and gives back
   the memory of the compressor. Nothing more goes to "dst", and writing to
   "wr" raises "Wr.Failure"; it can still be closed. *)

END Deflate.
