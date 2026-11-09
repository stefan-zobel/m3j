(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE Deflate;

IMPORT Atom, AtomList, M3jZip, Rd, RdClass, Thread, Word, Wr, WrClass;

CONST
  BufferSize = 32768;

  FlagHeaderCrc = 2;
  FlagExtra = 4;
  FlagName = 8;
  FlagComment = 16;
  FlagsReserved = 16_E0;

  GzipHeader = ARRAY [0 .. 9] OF CHAR
    {'\037', '\213', '\010', '\000', '\000', '\000', '\000', '\000', '\000', '\377'};

TYPE
  (* Where the reader is. A state of the header or trailer of "Gzip" takes one
     byte at a time, so that any byte may be the last of a read from the source. *)
  State = {Start, Magic, Method, Flags, Fixed, ExtraLength, Extra, Name, Comment,
           HeaderCrc, Body, Trailer, Between, End};

  Reader = Rd.T OBJECT
    src: Rd.T;
    z: REFANY;
    format: Format;
    o: ReadOptions;
    state: State;
    in: REF ARRAY OF CHAR;
    inPos, inEnd := 0;     (* in[inPos .. inEnd-1] is not yet used *)
    total := 0;            (* bytes of output *)
    failure: AtomList.T := NIL;
    flags := 0;
    count := 0;            (* of the bytes of this part *)
    value := 0;            (* what this part has so far *)
    headerCrc := 0;
    trailer: ARRAY [0 .. 7] OF CHAR;
  OVERRIDES
    seek := RdSeek;
    length := RdLength;
    close := RdClose;
  END;

  Writer = Wr.T OBJECT
    dst: Wr.T;
    z: REFANY;
    format: Format;
    closeTarget: BOOLEAN;
    out: REF ARRAY OF CHAR;
    headed := FALSE;       (* the header of "Gzip" has gone out *)
    ended := FALSE;
    broken: AtomList.T := NIL;
  OVERRIDES
    seek := WrSeek;
    flush := WrFlush;
    close := WrClose;
  END;

PROCEDURE Problem (atom: Atom.T; text: TEXT): AtomList.T =
  BEGIN
    RETURN AtomList.List2 (atom, Atom.FromText (text));
  END Problem;

(* reading *)

PROCEDURE NewRd (src: Rd.T; format := Format.Gzip; READONLY o := ReadDefaults): Rd.T =
  VAR r := NEW (Reader, src := src, format := format, o := o,
                z := M3jZip.NewInflate (format # Format.Zlib),
                in := NEW (REF ARRAY OF CHAR, BufferSize),
                buff := NEW (REF ARRAY OF CHAR, BufferSize), st := 0, lo := 0, hi := 0, cur := 0,
                intermittent := TRUE, seekable := FALSE, closed := FALSE);
  BEGIN
    IF format = Format.Gzip THEN r.state := State.Start ELSE r.state := State.Body END;
    RETURN r;
  END NewRd;

PROCEDURE FreeInflater (r: Reader) =
  BEGIN
    IF r.z # NIL THEN
      M3jZip.InflateEnd (r.z);
      r.z := NIL;
    END;
  END FreeInflater;

(* Makes the failure final. *)
PROCEDURE Fail (r: Reader; atom: Atom.T; text: TEXT) RAISES {Rd.Failure} =
  BEGIN
    r.failure := Problem (atom, text);
    FreeInflater (r);
    RAISE Rd.Failure (r.failure);
  END Fail;

PROCEDURE Done (r: Reader) =
  BEGIN
    r.state := State.End;
    FreeInflater (r);
  END Done;

(* Makes bytes of the source available in "in". "GetSub" would wait for a full
   buffer, so it is asked only for what is there. *)
PROCEDURE Fill (r: Reader; dontBlock: BOOLEAN): RdClass.SeekResult
  RAISES {Rd.Failure, Thread.Alerted} =
  VAR n: CARDINAL;
  BEGIN
    IF r.inPos < r.inEnd THEN RETURN RdClass.SeekResult.Ready END;
    IF dontBlock AND Rd.CharsReady (r.src) = 0 THEN RETURN RdClass.SeekResult.WouldBlock END;
    IF Rd.EOF (r.src) THEN RETURN RdClass.SeekResult.Eof END;
    n := Rd.GetSub (r.src, SUBARRAY (r.in^, 0, MIN (NUMBER (r.in^), MAX (1, Rd.CharsReady (r.src)))));
    IF n = 0 THEN RETURN RdClass.SeekResult.Eof END;
    r.inPos := 0;
    r.inEnd := n;
    RETURN RdClass.SeekResult.Ready;
  END Fill;

(* Goes to the first optional part of the header after "from" that is there. *)
PROCEDURE NextPart (r: Reader; from: State) =
  BEGIN
    r.count := 0;
    r.value := 0;
    IF from < State.ExtraLength AND Word.And (r.flags, FlagExtra) # 0 THEN
      r.state := State.ExtraLength;
    ELSIF from < State.Name AND Word.And (r.flags, FlagName) # 0 THEN
      r.state := State.Name;
    ELSIF from < State.Comment AND Word.And (r.flags, FlagComment) # 0 THEN
      r.state := State.Comment;
    ELSIF from < State.HeaderCrc AND Word.And (r.flags, FlagHeaderCrc) # 0 THEN
      r.state := State.HeaderCrc;
      r.headerCrc := Word.And (M3jZip.CrcValue (r.z), 16_FFFF);
    ELSE
      M3jZip.CrcReset (r.z);
      r.state := State.Body;
    END;
  END NextPart;

PROCEDURE Little (READONLY a: ARRAY OF CHAR): INTEGER =
  VAR v := 0;
  BEGIN
    FOR i := LAST (a) TO 0 BY -1 DO v := v * 256 + ORD (a[i]) END;
    RETURN v;
  END Little;

(* Takes the next byte of a header, or of what lies between members. *)
PROCEDURE Step (r: Reader; ch: CHAR) RAISES {Rd.Failure} =
  BEGIN
    IF r.state < State.HeaderCrc THEN M3jZip.CrcByte (r.z, ORD (ch)) END;
    CASE r.state OF
    | State.Start =>
        IF ch # '\037' THEN Fail (r, Corrupt, "not in the gzip format") END;
        r.state := State.Magic;
    | State.Magic =>
        IF ch # '\213' THEN Fail (r, Corrupt, "not in the gzip format") END;
        r.state := State.Method;
    | State.Method =>
        IF ch # '\010' THEN Fail (r, Corrupt, "an unknown method of compression") END;
        r.state := State.Flags;
    | State.Flags =>
        r.flags := ORD (ch);
        IF Word.And (r.flags, FlagsReserved) # 0 THEN
          Fail (r, Corrupt, "reserved flags in a gzip header");
        END;
        r.state := State.Fixed;
        r.count := 0;
    | State.Fixed =>
        INC (r.count);
        IF r.count = 6 THEN NextPart (r, State.Fixed) END;
    | State.ExtraLength =>
        r.value := r.value + ORD (ch) * Word.Shift (1, 8 * r.count);
        INC (r.count);
        IF r.count = 2 THEN
          r.state := State.Extra;
          r.count := r.value;
          IF r.count = 0 THEN NextPart (r, State.Extra) END;
        END;
    | State.Extra =>
        DEC (r.count);
        IF r.count = 0 THEN NextPart (r, State.Extra) END;
    | State.Name =>
        IF ch = '\000' THEN NextPart (r, State.Name) END;
    | State.Comment =>
        IF ch = '\000' THEN NextPart (r, State.Comment) END;
    | State.HeaderCrc =>
        r.value := r.value + ORD (ch) * Word.Shift (1, 8 * r.count);
        INC (r.count);
        IF r.count = 2 THEN
          IF r.value # r.headerCrc THEN Fail (r, Corrupt, "wrong checksum of a gzip header") END;
          NextPart (r, State.HeaderCrc);
        END;
    | State.Trailer =>
        r.trailer[r.count] := ch;
        INC (r.count);
        IF r.count = 8 THEN
          IF Little (SUBARRAY (r.trailer, 0, 4)) # M3jZip.CrcValue (r.z) THEN
            Fail (r, Corrupt, "wrong checksum of the data");
          END;
          IF Little (SUBARRAY (r.trailer, 4, 4)) # M3jZip.InflateTotal (r.z) THEN
            Fail (r, Corrupt, "wrong length of the data");
          END;
          IF r.o.concatenated THEN
            M3jZip.InflateReset (r.z);
            r.state := State.Between;
          ELSE
            Done (r);
          END;
        END;
    | State.Between =>
        IF ch = '\037' THEN
          M3jZip.CrcByte (r.z, ORD (ch));
          r.state := State.Magic;
        ELSIF ch # '\000' THEN
          Fail (r, Corrupt, "something other than gzip after the data");
        END;
    ELSE
      <*ASSERT FALSE*>
    END;
  END Step;

(* The source has ended in a state other than "Body". *)
PROCEDURE AtEof (r: Reader) RAISES {Rd.Failure} =
  BEGIN
    IF r.state = State.Start OR r.state = State.Between THEN
      Done (r);
    ELSE
      Fail (r, Truncated, "the data is cut off");
    END;
  END AtEof;

(* Produces output in the buffer of "r". Eof means that the compressed data of
   this stream is done, and the caller goes on with the state it finds. *)
PROCEDURE Decompress (r: Reader; dontBlock: BOOLEAN): RdClass.SeekResult
  RAISES {Rd.Failure, Thread.Alerted} =
  VAR n, room: INTEGER;
  BEGIN
    LOOP
      IF M3jZip.InflateFinished (r.z) THEN
        r.inPos := r.inEnd - M3jZip.InflateRemaining (r.z);
        IF r.format = Format.Gzip THEN
          r.state := State.Trailer;
          r.count := 0;
        ELSE
          Done (r);
        END;
        RETURN RdClass.SeekResult.Eof;
      END;
      room := NUMBER (r.buff^);
      IF r.o.maxBytes > 0 THEN room := MIN (room, r.o.maxBytes - r.total + 1) END;
      n := M3jZip.Inflate (r.z, SUBARRAY (r.buff^, 0, room));
      IF n = -M3jZip.Dictionary THEN
        Fail (r, Corrupt, "the data needs a preset dictionary");
      ELSIF n < 0 THEN
        Fail (r, Corrupt, "invalid compressed data");
      END;
      IF r.o.maxBytes > 0 AND r.total + n > r.o.maxBytes THEN
        (* what fits goes out, and the next read reports the rest *)
        DEC (n);
        r.failure := Problem (TooLarge, "more output than the limit");
        FreeInflater (r);
        IF n = 0 THEN RAISE Rd.Failure (r.failure) END;
      END;
      IF n > 0 THEN
        INC (r.total, n);
        r.st := 0;
        r.lo := r.cur;
        r.hi := r.cur + n;
        RETURN RdClass.SeekResult.Ready;
      END;
      (* the last call may have reached the end without any output *)
      IF NOT M3jZip.InflateFinished (r.z) THEN
        IF NOT M3jZip.InflateNeedsInput (r.z) THEN
          Fail (r, Corrupt, "invalid compressed data");
        END;
        CASE Fill (r, dontBlock) OF
        | RdClass.SeekResult.WouldBlock => RETURN RdClass.SeekResult.WouldBlock;
        | RdClass.SeekResult.Eof => Fail (r, Truncated, "the data is cut off");
        | RdClass.SeekResult.Ready =>
            M3jZip.InflateFeed (r.z, SUBARRAY (r.in^, r.inPos, r.inEnd - r.inPos));
            r.inPos := r.inEnd;
        END;
      END;
    END;
  END Decompress;

PROCEDURE RdSeek (r: Reader; <*UNUSED*> pos: CARDINAL; dontBlock: BOOLEAN): RdClass.SeekResult
  RAISES {Rd.Failure, Thread.Alerted} =
  VAR res: RdClass.SeekResult;
  BEGIN
    IF r.failure # NIL THEN RAISE Rd.Failure (r.failure) END;
    LOOP
      IF r.state = State.End THEN
        RETURN RdClass.SeekResult.Eof;
      ELSIF r.state = State.Body THEN
        res := Decompress (r, dontBlock);
        IF res # RdClass.SeekResult.Eof THEN RETURN res END;
      ELSE
        res := Fill (r, dontBlock);
        CASE res OF
        | RdClass.SeekResult.WouldBlock => RETURN res;
        | RdClass.SeekResult.Eof => AtEof (r);
        | RdClass.SeekResult.Ready =>
            INC (r.inPos);
            Step (r, r.in[r.inPos - 1]);
        END;
      END;
    END;
  END RdSeek;

PROCEDURE RdLength (<*UNUSED*> r: Reader): INTEGER =
  BEGIN
    RETURN -1;
  END RdLength;

PROCEDURE RdClose (r: Reader) RAISES {Rd.Failure, Thread.Alerted} =
  BEGIN
    FreeInflater (r);
    IF r.o.closeSource THEN Rd.Close (r.src) END;
  END RdClose;

(* writing *)

VAR
  Finished, Unusable: AtomList.T;

PROCEDURE NewWr (dst: Wr.T; format := Format.Gzip; READONLY o := WriteDefaults): Wr.T =
  BEGIN
    <*ASSERT -1 <= o.level AND o.level <= 9*>
    RETURN NEW (Writer, dst := dst, format := format, closeTarget := o.closeTarget,
                z := M3jZip.NewDeflate (o.level, format # Format.Zlib),
                out := NEW (REF ARRAY OF CHAR, BufferSize),
                buff := NEW (REF ARRAY OF CHAR, BufferSize), st := 0, lo := 0, cur := 0,
                hi := BufferSize, buffered := TRUE, seekable := FALSE, closed := FALSE);
  END NewWr;

PROCEDURE FreeDeflater (w: Writer) =
  BEGIN
    IF w.z # NIL THEN
      M3jZip.DeflateEnd (w.z);
      w.z := NIL;
    END;
  END FreeDeflater;

(* Compresses the buffer of "w" and sends what comes out. *)
PROCEDURE Compress (w: Writer; mode: INTEGER) RAISES {Wr.Failure, Thread.Alerted} =
  VAR n := w.cur - w.lo;  got: INTEGER;  more: BOOLEAN;
  BEGIN
    IF NOT w.headed THEN
      w.headed := TRUE;
      IF w.format = Format.Gzip THEN Wr.PutString (w.dst, GzipHeader) END;
    END;
    IF n > 0 THEN M3jZip.DeflateFeed (w.z, SUBARRAY (w.buff^, 0, n)) END;
    REPEAT
      got := M3jZip.Deflate (w.z, w.out^, mode);
      IF got > 0 THEN Wr.PutString (w.dst, SUBARRAY (w.out^, 0, got)) END;
      IF mode = M3jZip.Finish THEN
        more := NOT M3jZip.DeflateFinished (w.z);
      ELSE
        more := got = NUMBER (w.out^);
      END;
    UNTIL NOT more;
    w.lo := w.cur;
    w.hi := w.lo + NUMBER (w.buff^);
  END Compress;

PROCEDURE PutLittle (w: Writer; v: INTEGER) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    FOR i := 0 TO 3 DO
      Wr.PutChar (w.dst, VAL (Word.And (Word.RightShift (v, 8 * i), 255), CHAR));
    END;
  END PutLittle;

(* Ends the stream. "w" is locked. *)
PROCEDURE End (w: Writer) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    IF w.ended OR w.closed THEN RETURN END;
    IF w.broken # NIL THEN RAISE Wr.Failure (w.broken) END;
    TRY
      Compress (w, M3jZip.Finish);
      IF w.format = Format.Gzip THEN
        PutLittle (w, M3jZip.DeflateCrc (w.z));
        PutLittle (w, M3jZip.DeflateTotal (w.z));
      END;
      w.ended := TRUE;
      w.broken := Finished;
      FreeDeflater (w);
      w.lo := w.cur;
      w.hi := w.cur;
    EXCEPT
    | Wr.Failure (e) => Break (w, e); RAISE Wr.Failure (e);
    | Thread.Alerted => Break (w, Unusable); RAISE Thread.Alerted;
    END;
  END End;

PROCEDURE Break (w: Writer; why: AtomList.T) =
  BEGIN
    w.broken := why;
    FreeDeflater (w);
    w.lo := w.cur;
    w.hi := w.cur;
  END Break;

PROCEDURE Guarded (w: Writer; mode: INTEGER) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    IF w.broken # NIL THEN RAISE Wr.Failure (w.broken) END;
    TRY
      Compress (w, mode);
    EXCEPT
    | Wr.Failure (e) => Break (w, e); RAISE Wr.Failure (e);
    | Thread.Alerted => Break (w, Unusable); RAISE Thread.Alerted;
    END;
  END Guarded;

PROCEDURE WrSeek (w: Writer; <*UNUSED*> n: CARDINAL) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    Guarded (w, M3jZip.NoFlush);
  END WrSeek;

PROCEDURE WrFlush (w: Writer) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    IF w.ended THEN RETURN END;
    Guarded (w, M3jZip.SyncFlush);
    Wr.Flush (w.dst);
  END WrFlush;

PROCEDURE WrClose (w: Writer) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    TRY
      End (w);
    FINALLY
      FreeDeflater (w);
      IF w.closeTarget THEN Wr.Close (w.dst) END;
    END;
  END WrClose;

PROCEDURE Finish (wr: Wr.T) RAISES {Wr.Failure, Thread.Alerted} =
  VAR w := NARROW (wr, Writer);
  BEGIN
    WrClass.Lock (w);
    TRY
      End (w);
    FINALLY
      WrClass.Unlock (w);
    END;
  END Finish;

PROCEDURE Discard (wr: Wr.T) =
  VAR w := NARROW (wr, Writer);
  BEGIN
    WrClass.Lock (w);
    TRY
      IF NOT w.ended THEN
        IF w.broken = NIL THEN Break (w, Unusable) END;
        w.ended := TRUE;
      END;
      FreeDeflater (w);
    FINALLY
      WrClass.Unlock (w);
    END;
  END Discard;

BEGIN
  Corrupt := Atom.FromText ("Deflate.Corrupt");
  Truncated := Atom.FromText ("Deflate.Truncated");
  TooLarge := Atom.FromText ("Deflate.TooLarge");
  Finished := Problem (Atom.FromText ("Deflate.Finished"), "the stream has ended");
  Unusable := Problem (Atom.FromText ("Deflate.Unusable"), "the stream was given up");
END Deflate.
