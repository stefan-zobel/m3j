(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Zip;

IMPORT Atom, AtomList, Deflate, FileRd, M3jZip, OSError, Rd, RdClass, Text, Thread, Wr, WrClass;

CONST
  BufferSize = 32768;

REVEAL
  Reader = ReaderPublic BRANDED "Zip.Reader" OBJECT
    a: REFANY;
    n: CARDINAL;
  OVERRIDES
    count := Count;
    entry := GetEntry;
    find := Find;
    open := OpenEntry;
    close := CloseReader;
  END;

  Writer = WriterPublic BRANDED "Zip.Writer" OBJECT
    x: REFANY;
    wr: Wr.T;
    out: REF ARRAY OF CHAR;
    current: EntryWr := NIL;
    broken: AtomList.T := NIL;
  OVERRIDES
    add := Add;
    finish := Finish;
  END;

TYPE
  EntryRd = Rd.T OBJECT
    x: REFANY;
    size: INTEGER;
    maxBytes: INTEGER;
    total := 0;
    failure: AtomList.T := NIL;
  OVERRIDES
    seek := EntryRdSeek;
    length := EntryRdLength;
    close := EntryRdClose;
  END;

  EntryWr = Wr.T OBJECT
    w: Writer;
    ended := FALSE;
  OVERRIDES
    seek := EntryWrSeek;
    flush := EntryWrFlush;
    close := EntryWrClose;
  END;

VAR
  Finished, EntryEnded: AtomList.T;

PROCEDURE Problem (atom: Atom.T; text: TEXT): AtomList.T =
  BEGIN
    RETURN AtomList.List2 (atom, Atom.FromText (text));
  END Problem;

(* The failure with a number of M3jZip. The texts are fixed, because an atom
   made from the text of an archive would stay for good. *)
PROCEDURE OfCode (code: INTEGER): AtomList.T =
  BEGIN
    CASE code OF
    | M3jZip.Unsupported => RETURN Problem (Unsupported, "something that the platform cannot handle");
    | M3jZip.Duplicate => RETURN Problem (Duplicate, "two entries with the same name");
    | M3jZip.Closed => RETURN Problem (Corrupt, "the archive is closed");
    ELSE RETURN Problem (Corrupt, "not a valid archive");
    END;
  END OfCode;

(* reading *)

PROCEDURE Open (path: TEXT): Reader RAISES {OSError.E, Error} =
  <*FATAL Rd.Failure, Thread.Alerted*>
  VAR code := 0;  a: REFANY;
  BEGIN
    (* the operating system's errors, as every open of a file raises them *)
    Rd.Close (FileRd.Open (path));
    a := M3jZip.OpenArchive (path, code);
    IF a = NIL THEN RAISE Error (OfCode (code)) END;
    RETURN NEW (Reader, a := a, n := M3jZip.ArchiveCount (a));
  END Open;

PROCEDURE Count (r: Reader): CARDINAL =
  BEGIN
    RETURN r.n;
  END Count;

PROCEDURE GetEntry (r: Reader; i: CARDINAL): Entry =
  VAR e: Entry;  time: INTEGER;
  BEGIN
    <*ASSERT i < r.n*>
    e.name := M3jZip.EntryName (r.a, i);
    e.size := M3jZip.EntrySize (r.a, i);
    e.compressedSize := M3jZip.EntryCompressed (r.a, i);
    e.crc := M3jZip.EntryCrc (r.a, i);
    time := M3jZip.EntryTime (r.a, i);
    IF time = -1 THEN e.modified := 0.0D0 ELSE e.modified := FLOAT (time, LONGREAL) / 1000.0D0 END;
    e.directory := M3jZip.EntryDirectory (r.a, i);
    RETURN e;
  END GetEntry;

PROCEDURE Find (r: Reader; name: TEXT; VAR i: CARDINAL): BOOLEAN =
  VAR k := M3jZip.ArchiveFind (r.a, name);
  BEGIN
    IF k < 0 THEN RETURN FALSE END;
    i := k;
    RETURN TRUE;
  END Find;

PROCEDURE OpenEntry (r: Reader; i: CARDINAL; maxBytes := 0): Rd.T RAISES {Error} =
  VAR code := 0;  x: REFANY;
  BEGIN
    <*ASSERT i < r.n*>
    x := M3jZip.EntryOpen (r.a, i, code);
    IF x = NIL THEN RAISE Error (OfCode (code)) END;
    RETURN NEW (EntryRd, x := x, size := M3jZip.EntrySize (r.a, i), maxBytes := maxBytes,
                buff := NEW (REF ARRAY OF CHAR, BufferSize), st := 0, lo := 0, hi := 0, cur := 0,
                intermittent := TRUE, seekable := FALSE, closed := FALSE);
  END OpenEntry;

PROCEDURE CloseReader (r: Reader) =
  BEGIN
    M3jZip.CloseArchive (r.a);
  END CloseReader;

PROCEDURE EntryRdSeek (rd: EntryRd; <*UNUSED*> pos: CARDINAL; <*UNUSED*> dontBlock: BOOLEAN)
  : RdClass.SeekResult RAISES {Rd.Failure} =
  VAR n, room: INTEGER;
  BEGIN
    IF rd.failure # NIL THEN RAISE Rd.Failure (rd.failure) END;
    room := NUMBER (rd.buff^);
    IF rd.maxBytes > 0 THEN room := MIN (room, rd.maxBytes - rd.total + 1) END;
    n := M3jZip.EntryRead (rd.x, SUBARRAY (rd.buff^, 0, room));
    IF n = 0 THEN RETURN RdClass.SeekResult.Eof END;
    IF n < 0 THEN
      rd.failure := OfCode (-n);
      RAISE Rd.Failure (rd.failure);
    END;
    IF rd.maxBytes > 0 AND rd.total + n > rd.maxBytes THEN
      (* what fits goes out, and the next read reports the rest *)
      DEC (n);
      rd.failure := Problem (Deflate.TooLarge, "more output than the limit");
      IF n = 0 THEN RAISE Rd.Failure (rd.failure) END;
    END;
    INC (rd.total, n);
    rd.st := 0;
    rd.lo := rd.cur;
    rd.hi := rd.cur + n;
    RETURN RdClass.SeekResult.Ready;
  END EntryRdSeek;

PROCEDURE EntryRdLength (rd: EntryRd): INTEGER =
  BEGIN
    RETURN rd.size;
  END EntryRdLength;

PROCEDURE EntryRdClose (rd: EntryRd) =
  BEGIN
    M3jZip.EntryClose (rd.x);
  END EntryRdClose;

(* writing *)

PROCEDURE ToWr (wr: Wr.T): Writer =
  BEGIN
    RETURN NEW (Writer, x := M3jZip.NewWriter (), wr := wr, out := NEW (REF ARRAY OF CHAR, BufferSize));
  END ToWr;

(* Sends what the archive has written. *)
PROCEDURE Drain (w: Writer) RAISES {Wr.Failure, Thread.Alerted} =
  VAR n: INTEGER;
  BEGIN
    TRY
      LOOP
        n := M3jZip.WriterTake (w.x, w.out^);
        IF n = 0 THEN EXIT END;
        Wr.PutString (w.wr, SUBARRAY (w.out^, 0, n));
      END;
    EXCEPT
    | Wr.Failure (e) => w.broken := e; RAISE Wr.Failure (e);
    | Thread.Alerted => w.broken := Problem (Corrupt, "the archive was given up"); RAISE Thread.Alerted;
    END;
  END Drain;

PROCEDURE CheckCode (code: INTEGER) RAISES {Wr.Failure} =
  BEGIN
    IF code < 0 THEN RAISE Wr.Failure (OfCode (-code)) END;
  END CheckCode;

(* Passes on the buffer of "e", which is locked. *)
PROCEDURE Push (e: EntryWr) RAISES {Wr.Failure, Thread.Alerted} =
  VAR n := e.cur - e.lo;
  BEGIN
    IF e.ended THEN RAISE Wr.Failure (EntryEnded) END;
    IF e.w.broken # NIL THEN RAISE Wr.Failure (e.w.broken) END;
    IF n > 0 THEN CheckCode (M3jZip.WriterWrite (e.w.x, SUBARRAY (e.buff^, 0, n))) END;
    e.lo := e.cur;
    e.hi := e.lo + NUMBER (e.buff^);
    Drain (e.w);
  END Push;

(* Ends the entry of "e", which is locked. *)
PROCEDURE EndLocked (e: EntryWr) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    e.ended := TRUE;
    e.lo := e.cur;
    e.hi := e.cur;
    e.w.current := NIL;
    CheckCode (M3jZip.WriterCloseEntry (e.w.x));
    Drain (e.w);
  END EndLocked;

PROCEDURE EndCurrent (w: Writer) RAISES {Wr.Failure, Thread.Alerted} =
  VAR e := w.current;
  BEGIN
    IF e = NIL THEN RETURN END;
    WrClass.Lock (e);
    TRY
      Push (e);
      EndLocked (e);
    FINALLY
      WrClass.Unlock (e);
    END;
  END EndCurrent;

PROCEDURE Add (w: Writer; name: TEXT; modified := 0.0D0; level := -1): Wr.T
  RAISES {Error, Wr.Failure, Thread.Alerted} =
  VAR code: INTEGER;
  BEGIN
    <*ASSERT -1 <= level AND level <= 9*>
    IF w.broken # NIL THEN RAISE Wr.Failure (w.broken) END;
    EndCurrent (w);
    code := M3jZip.WriterAdd (w.x, name, ROUND (modified * 1000.0D0), level);
    IF code = -M3jZip.Closed THEN
      w.broken := Problem (Unsupported, "the platform cannot go on with the archive");
      RAISE Wr.Failure (w.broken);
    ELSIF code < 0 THEN
      RAISE Error (OfCode (-code));
    END;
    Drain (w);
    w.current := NEW (EntryWr, w := w, buff := NEW (REF ARRAY OF CHAR, BufferSize), st := 0,
                      lo := 0, cur := 0, hi := BufferSize, buffered := TRUE, seekable := FALSE,
                      closed := FALSE);
    RETURN w.current;
  END Add;

PROCEDURE Finish (w: Writer) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    IF w.broken = Finished THEN RETURN END;
    IF w.broken # NIL THEN RAISE Wr.Failure (w.broken) END;
    EndCurrent (w);
    CheckCode (M3jZip.WriterFinish (w.x));
    Drain (w);
    w.broken := Finished;
  END Finish;

PROCEDURE EntryWrSeek (e: EntryWr; <*UNUSED*> n: CARDINAL) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    Push (e);
  END EntryWrSeek;

PROCEDURE EntryWrFlush (e: EntryWr) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    IF e.ended THEN RETURN END;
    Push (e);
    Wr.Flush (e.w.wr);
  END EntryWrFlush;

PROCEDURE EntryWrClose (e: EntryWr) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    IF NOT e.ended THEN EndLocked (e) END;
  END EntryWrClose;

PROCEDURE SafeName (name: TEXT): BOOLEAN =
  VAR n := Text.Length (name);  start := 0;  c: WIDECHAR;
  BEGIN
    IF n = 0 OR Text.GetWideChar (name, 0) = W'/' THEN RETURN FALSE END;
    IF n >= 2 AND Text.GetWideChar (name, 1) = W':' THEN RETURN FALSE END;
    FOR i := 0 TO n DO
      IF i = n THEN c := W'/' ELSE c := Text.GetWideChar (name, i) END;
      IF c = W'\\' OR c = W'\000000' THEN RETURN FALSE END;
      IF c = W'/' THEN
        IF i - start = 2 AND Text.GetWideChar (name, start) = W'.'
             AND Text.GetWideChar (name, start + 1) = W'.' THEN
          RETURN FALSE;
        END;
        start := i + 1;
      END;
    END;
    RETURN TRUE;
  END SafeName;

BEGIN
  Corrupt := Deflate.Corrupt;
  Unsupported := Atom.FromText ("Zip.Unsupported");
  Duplicate := Atom.FromText ("Zip.Duplicate");
  Finished := Problem (Atom.FromText ("Zip.Finished"), "the archive has ended");
  EntryEnded := Problem (Atom.FromText ("Zip.EntryEnded"), "the entry has ended");
END Zip.
