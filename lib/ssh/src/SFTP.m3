(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE SFTP;

IMPORT Atom, M3jSsh, Rd, RdClass, SSH, SSHRep, Thread, Time, Wr, WrClass;

CONST
  BufferSize = 32768;
  MaxTime = 4294967295.0D0;

REVEAL
  T = Public BRANDED "SFTP.T" OBJECT
    f: REFANY;
  OVERRIDES
    open := OpenFile;
    create := Create;
    stat := Stat;
    lstat := Lstat;
    list := List;
    mkdir := Mkdir;
    rmdir := Rmdir;
    remove := Remove;
    rename := Rename;
    symlink := Symlink;
    readlink := Readlink;
    realpath := Realpath;
    chmod := Chmod;
    setTimes := SetTimes;
    close := Close;
  END;

TYPE
  FileRd = Rd.T OBJECT
    x: REFANY;
  OVERRIDES
    seek := RdSeek;
    length := RdLength;
    close := RdClose;
  END;

  FileWr = Wr.T OBJECT
    x: REFANY;
  OVERRIDES
    seek := WrSeek;
    flush := WrFlush;
    close := WrClose;
  END;

PROCEDURE Check (code: INTEGER; err: TEXT) RAISES {SSH.Error, Thread.Alerted} =
  BEGIN
    IF code < 0 THEN SSHRep.Raise (-code, err) END;
  END Check;

PROCEDURE Need (path: TEXT) RAISES {SSH.Error} =
  BEGIN
    IF path = NIL THEN RAISE SSH.Error (SSHRep.Problem (M3jSsh.NoSuchFile, "no path")) END;
  END Need;

PROCEDURE Open (s: SSH.T): T RAISES {SSH.Error, Thread.Alerted} =
  VAR code := 0;  err: TEXT := NIL;  f: REFANY;
  BEGIN
    f := M3jSsh.SftpOpen (s.h, s.timeout, code, err);
    IF f = NIL THEN SSHRep.Raise (code, err) END;
    RETURN NEW (T, f := f);
  END Open;

PROCEDURE Close (t: T) =
  BEGIN
    M3jSsh.SftpClose (t.f);
  END Close;

(* attributes *)

PROCEDURE Attr (kind, size, mode, uid, gid, atime, mtime: INTEGER): Attributes =
  BEGIN
    RETURN Attributes {kind := VAL (kind, Kind), size := size, mode := mode, uid := uid, gid := gid,
                       atime := FLOAT (atime, LONGREAL), mtime := FLOAT (mtime, LONGREAL)};
  END Attr;

PROCEDURE StatOf (t: T; path: TEXT; follow: BOOLEAN; VAR a: Attributes) RAISES {SSH.Error, Thread.Alerted} =
  VAR kind, size, mode, uid, gid, atime, mtime, code := 0;  err: TEXT := NIL;
  BEGIN
    Need (path);
    code := M3jSsh.SftpStat (t.f, path, follow, kind, size, mode, uid, gid, atime, mtime, err);
    Check (code, err);
    a := Attr (kind, size, mode, uid, gid, atime, mtime);
  END StatOf;

PROCEDURE Stat (t: T; path: TEXT; VAR a: Attributes) RAISES {SSH.Error, Thread.Alerted} =
  BEGIN
    StatOf (t, path, TRUE, a);
  END Stat;

PROCEDURE Lstat (t: T; path: TEXT; VAR a: Attributes) RAISES {SSH.Error, Thread.Alerted} =
  BEGIN
    StatOf (t, path, FALSE, a);
  END Lstat;

PROCEDURE List (t: T; dir: TEXT): Entries RAISES {SSH.Error, Thread.Alerted} =
  VAR
    n: INTEGER;  err: TEXT := NIL;  list: REFANY := NIL;  r: Entries;
    kind, size, mode, uid, gid, atime, mtime := 0;
  BEGIN
    Need (dir);
    n := M3jSsh.SftpList (t.f, dir, list, err);
    Check (n, err);
    r := NEW (Entries, n);
    FOR i := 0 TO n - 1 DO
      r[i].name := M3jSsh.SftpEntry (list, i, kind, size, mode, uid, gid, atime, mtime);
      r[i].attr := Attr (kind, size, mode, uid, gid, atime, mtime);
    END;
    RETURN r;
  END List;

(* operations *)

PROCEDURE Op (t: T; op: INTEGER; a, b: TEXT; n, m: INTEGER): TEXT RAISES {SSH.Error, Thread.Alerted} =
  VAR code: INTEGER;  result, err: TEXT := NIL;
  BEGIN
    Need (a);
    code := M3jSsh.SftpOp (t.f, op, a, b, n, m, result, err);
    Check (code, err);
    RETURN result;
  END Op;

PROCEDURE Mkdir (t: T; path: TEXT; mode: Mode) RAISES {SSH.Error, Thread.Alerted} =
  BEGIN
    EVAL Op (t, M3jSsh.Mkdir, path, NIL, mode, 0);
  END Mkdir;

PROCEDURE Rmdir (t: T; path: TEXT) RAISES {SSH.Error, Thread.Alerted} =
  BEGIN
    EVAL Op (t, M3jSsh.Rmdir, path, NIL, 0, 0);
  END Rmdir;

PROCEDURE Remove (t: T; path: TEXT) RAISES {SSH.Error, Thread.Alerted} =
  BEGIN
    EVAL Op (t, M3jSsh.Remove, path, NIL, 0, 0);
  END Remove;

PROCEDURE Rename (t: T; from, to: TEXT) RAISES {SSH.Error, Thread.Alerted} =
  BEGIN
    Need (to);
    EVAL Op (t, M3jSsh.Rename, from, to, 0, 0);
  END Rename;

PROCEDURE Symlink (t: T; target, link: TEXT) RAISES {SSH.Error, Thread.Alerted} =
  BEGIN
    Need (link);
    EVAL Op (t, M3jSsh.Symlink, target, link, 0, 0);
  END Symlink;

PROCEDURE Readlink (t: T; path: TEXT): TEXT RAISES {SSH.Error, Thread.Alerted} =
  BEGIN
    RETURN Op (t, M3jSsh.Readlink, path, NIL, 0, 0);
  END Readlink;

PROCEDURE Realpath (t: T; path: TEXT): TEXT RAISES {SSH.Error, Thread.Alerted} =
  BEGIN
    RETURN Op (t, M3jSsh.Realpath, path, NIL, 0, 0);
  END Realpath;

PROCEDURE Chmod (t: T; path: TEXT; mode: Mode) RAISES {SSH.Error, Thread.Alerted} =
  BEGIN
    EVAL Op (t, M3jSsh.Chmod, path, NIL, mode, 0);
  END Chmod;

PROCEDURE Seconds (time: Time.T): INTEGER RAISES {SSH.Error} =
  BEGIN
    IF NOT (time >= 0.0D0 AND time <= MaxTime) THEN
      RAISE SSH.Error (SSHRep.Problem (M3jSsh.Failure, "a time outside of SFTP"));
    END;
    RETURN TRUNC (time);
  END Seconds;

PROCEDURE SetTimes (t: T; path: TEXT; atime, mtime: Time.T) RAISES {SSH.Error, Thread.Alerted} =
  BEGIN
    EVAL Op (t, M3jSsh.SetTimes, path, NIL, Seconds (atime), Seconds (mtime));
  END SetTimes;

(* reading *)

PROCEDURE OpenFile (t: T; path: TEXT; offset: CARDINAL): Rd.T RAISES {SSH.Error, Thread.Alerted} =
  VAR code := 0;  err: TEXT := NIL;  x: REFANY;
  BEGIN
    Need (path);
    x := M3jSsh.SftpGet (t.f, path, offset, code, err);
    IF x = NIL THEN SSHRep.Raise (code, err) END;
    RETURN NEW (FileRd, x := x, buff := NEW (REF ARRAY OF CHAR, BufferSize), st := 0, lo := 0, hi := 0,
                cur := 0, intermittent := TRUE, seekable := FALSE, closed := FALSE);
  END OpenFile;

PROCEDURE RdSeek (rd: FileRd; <*UNUSED*> pos: CARDINAL; dontBlock: BOOLEAN): RdClass.SeekResult
  RAISES {Rd.Failure, Thread.Alerted} =
  VAR n: INTEGER;  err: TEXT := NIL;
  BEGIN
    IF dontBlock THEN RETURN RdClass.SeekResult.WouldBlock END;
    n := M3jSsh.StreamRead (rd.x, rd.buff^, err);
    IF n > 0 THEN
      rd.st := 0;
      rd.lo := rd.cur;
      rd.hi := rd.cur + n;
      RETURN RdClass.SeekResult.Ready;
    ELSIF n = 0 THEN
      RETURN RdClass.SeekResult.Eof;
    ELSIF n = -M3jSsh.Alerted THEN
      IF Thread.TestAlert () THEN RAISE Thread.Alerted END;
      RAISE Rd.Failure (SSHRep.Problem (M3jSsh.Failure, "the wait was given up"));
    ELSE
      RAISE Rd.Failure (SSHRep.Problem (-n, err));
    END;
  END RdSeek;

PROCEDURE RdLength (<*UNUSED*> rd: FileRd): INTEGER =
  BEGIN
    RETURN -1;
  END RdLength;

PROCEDURE RdClose (rd: FileRd) =
  VAR err: TEXT := NIL;
  BEGIN
    EVAL M3jSsh.StreamClose (rd.x, err);
  END RdClose;

(* writing *)

PROCEDURE Create (t: T; path: TEXT; mode: Mode; append: BOOLEAN): Wr.T RAISES {SSH.Error, Thread.Alerted} =
  VAR code := 0;  err: TEXT := NIL;  x: REFANY;
  BEGIN
    Need (path);
    x := M3jSsh.SftpPut (t.f, path, mode, append, code, err);
    IF x = NIL THEN SSHRep.Raise (code, err) END;
    RETURN NEW (FileWr, x := x, buff := NEW (REF ARRAY OF CHAR, BufferSize), st := 0, lo := 0, cur := 0,
                hi := BufferSize, buffered := TRUE, seekable := FALSE, closed := FALSE);
  END Create;

(* Hands the buffer to the session of SFTP. After an alert or a timeout the
   bytes still go out, so they are taken from the buffer either way. *)
PROCEDURE Push (w: FileWr) RAISES {Wr.Failure, Thread.Alerted} =
  VAR n := w.cur - w.lo;  r := 0;  err: TEXT := NIL;
  BEGIN
    IF n > 0 THEN r := M3jSsh.StreamWrite (w.x, SUBARRAY (w.buff^, 0, n), err) END;
    w.lo := w.cur;
    w.hi := w.lo + NUMBER (w.buff^);
    IF r = -M3jSsh.Alerted THEN
      IF Thread.TestAlert () THEN RAISE Thread.Alerted END;
      RAISE Wr.Failure (SSHRep.Problem (M3jSsh.Failure, "the wait was given up"));
    ELSIF r < 0 THEN
      RAISE Wr.Failure (SSHRep.Problem (-r, err));
    END;
  END Push;

PROCEDURE WrSeek (w: FileWr; <*UNUSED*> n: CARDINAL) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    Push (w);
  END WrSeek;

PROCEDURE WrFlush (w: FileWr) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    Push (w);
  END WrFlush;

PROCEDURE WrClose (w: FileWr) RAISES {Wr.Failure, Thread.Alerted} =
  VAR r: INTEGER;  err: TEXT := NIL;
  BEGIN
    r := M3jSsh.StreamClose (w.x, err);
    IF r = -M3jSsh.Alerted THEN
      IF Thread.TestAlert () THEN RAISE Thread.Alerted END;
      RAISE Wr.Failure (SSHRep.Problem (M3jSsh.Failure, "the wait was given up"));
    ELSIF r < 0 THEN
      RAISE Wr.Failure (SSHRep.Problem (-r, err));
    END;
  END WrClose;

BEGIN
  NoSuchFile := Atom.FromText ("SFTP.NoSuchFile");
  PermissionDenied := Atom.FromText ("SFTP.PermissionDenied");
  Exists := Atom.FromText ("SFTP.Exists");
END SFTP.
