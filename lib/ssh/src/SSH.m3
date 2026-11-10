(* Copyright (c) 2026 the m3j authors.
   SPDX-License-Identifier: MIT *)

MODULE SSH EXPORTS SSH, SSHRep;

IMPORT Atom, AtomList, M3jSsh, Rd, RdClass, SFTP, Thread, Wr, WrClass;

CONST
  BufferSize = 32768;

REVEAL
  T = Private BRANDED "SSH.T" OBJECT
  OVERRIDES
    run := Run;
    start := Start;
    hostKey := HostKey;
    close := Close;
  END;

  Process = ProcessPublic BRANDED "SSH.Process" OBJECT
    s: T;
    p: REFANY;
    sig: TEXT := NIL;
  OVERRIDES
    wait := Wait;
    signal := Signal;
    close := ProcessClose;
  END;

TYPE
  OutRd = Rd.T OBJECT
    p: REFANY;
    which: INTEGER;
  OVERRIDES
    seek := OutSeek;
    length := OutLength;
    close := OutClose;
  END;

  InWr = Wr.T OBJECT
    p: REFANY;
  OVERRIDES
    seek := InSeek;
    flush := InFlush;
    close := InClose;
  END;

PROCEDURE Problem (code: INTEGER; err: TEXT): AtomList.T =
  VAR a: Atom.T;
  BEGIN
    CASE code OF
    | M3jSsh.Timeout => a := Timeout;
    | M3jSsh.Refused => a := Refused;
    | M3jSsh.Disconnected => a := Disconnected;
    | M3jSsh.AuthFailed => a := AuthFailed;
    | M3jSsh.HostKeyUnknown => a := HostKeyUnknown;
    | M3jSsh.HostKeyChanged => a := HostKeyChanged;
    | M3jSsh.HostKeyRevoked => a := HostKeyRevoked;
    | M3jSsh.BadKey => a := BadKey;
    | M3jSsh.Busy => a := Busy;
    | M3jSsh.TooLarge => a := TooLarge;
    | M3jSsh.NoSuchFile => a := SFTP.NoSuchFile;
    | M3jSsh.PermissionDenied => a := SFTP.PermissionDenied;
    | M3jSsh.Exists => a := SFTP.Exists;
    ELSE a := Failure;
    END;
    IF err = NIL THEN err := "the operation failed" END;
    RETURN AtomList.List2 (a, Atom.FromText (err));
  END Problem;

PROCEDURE Raise (code: INTEGER; err: TEXT) RAISES {Error, Thread.Alerted} =
  BEGIN
    IF code = M3jSsh.Alerted THEN
      IF Thread.TestAlert () THEN RAISE Thread.Alerted END;
      err := "the wait was given up";
    END;
    RAISE Error (Problem (code, err));
  END Raise;

PROCEDURE Connect (host: TEXT; READONLY o := Defaults): T RAISES {Error, Thread.Alerted} =
  VAR code := 0;  err: TEXT := NIL;  h: REFANY;
  BEGIN
    IF host = NIL THEN RAISE Error (Problem (M3jSsh.Refused, "no host")) END;
    IF o.keyFile # NIL AND o.key # NIL THEN
      RAISE Error (Problem (M3jSsh.BadKey, "both a file of a key and a key"));
    END;
    IF o.hostCheck = HostCheck.Fingerprint AND o.fingerprint = NIL THEN
      RAISE Error (Problem (M3jSsh.HostKeyUnknown, "no fingerprint"));
    END;
    h := M3jSsh.Connect (host, o.port, o.user, o.password, o.keyFile, o.key, o.passphrase,
                         ORD (o.hostCheck), o.knownHosts, o.fingerprint, o.timeout, o.keepAlive, code,
                         err);
    IF h = NIL THEN Raise (code, err) END;
    RETURN NEW (T, h := h, timeout := o.timeout);
  END Connect;

PROCEDURE Run (s: T; command: TEXT; VAR out, err: TEXT; input: TEXT; maxOutput: CARDINAL;
               timeout: LONGREAL): INTEGER RAISES {Error, Thread.Alerted} =
  VAR status, code := 0;  sig, o, e, text: TEXT := NIL;
  BEGIN
    IF command = NIL THEN RAISE Error (Problem (M3jSsh.Failure, "no command")) END;
    IF input = NIL THEN input := "" END;
    code := M3jSsh.Run (s.h, command, input, maxOutput, timeout, s.timeout, status, sig, o, e, text);
    IF code < 0 THEN Raise (-code, text) END;
    out := o;
    err := e;
    RETURN status;
  END Run;

PROCEDURE Start (s: T; command: TEXT; maxBuffer: CARDINAL): Process RAISES {Error, Thread.Alerted} =
  VAR code := 0;  err: TEXT := NIL;  p: REFANY;  pr: Process;
  BEGIN
    IF command = NIL THEN RAISE Error (Problem (M3jSsh.Failure, "no command")) END;
    p := M3jSsh.Start (s.h, command, maxBuffer, s.timeout, code, err);
    IF p = NIL THEN Raise (code, err) END;
    pr := NEW (Process, s := s, p := p);
    pr.stdin := NEW (InWr, p := p, buff := NEW (REF ARRAY OF CHAR, BufferSize), st := 0, lo := 0, cur := 0,
                     hi := BufferSize, buffered := TRUE, seekable := FALSE, closed := FALSE);
    pr.stdout := NewOut (p, 0);
    pr.stderr := NewOut (p, 1);
    RETURN pr;
  END Start;

PROCEDURE NewOut (p: REFANY; which: INTEGER): OutRd =
  BEGIN
    RETURN NEW (OutRd, p := p, which := which, buff := NEW (REF ARRAY OF CHAR, BufferSize), st := 0,
                lo := 0, hi := 0, cur := 0, intermittent := TRUE, seekable := FALSE, closed := FALSE);
  END NewOut;

PROCEDURE HostKey (s: T): TEXT =
  BEGIN
    RETURN M3jSsh.HostKey (s.h);
  END HostKey;

PROCEDURE Close (s: T) =
  BEGIN
    M3jSsh.Disconnect (s.h);
  END Close;

PROCEDURE Wait (p: Process): INTEGER RAISES {Error, Thread.Alerted} =
  VAR status, code := 0;  sig, err: TEXT := NIL;
  BEGIN
    code := M3jSsh.ProcWait (p.s.h, p.p, status, sig, err);
    IF code < 0 THEN Raise (-code, err) END;
    p.sig := sig;
    RETURN status;
  END Wait;

PROCEDURE Signal (p: Process): TEXT =
  BEGIN
    RETURN p.sig;
  END Signal;

PROCEDURE ProcessClose (p: Process) =
  BEGIN
    M3jSsh.ProcClose (p.p);
  END ProcessClose;

PROCEDURE OutSeek (rd: OutRd; <*UNUSED*> pos: CARDINAL; dontBlock: BOOLEAN): RdClass.SeekResult
  RAISES {Rd.Failure, Thread.Alerted} =
  VAR n: INTEGER;  err: TEXT := NIL;  wait := -1.0D0;
  BEGIN
    IF dontBlock THEN wait := 0.0D0 END;
    n := M3jSsh.ProcRead (rd.p, rd.which, rd.buff^, wait, err);
    IF n > 0 THEN
      rd.st := 0;
      rd.lo := rd.cur;
      rd.hi := rd.cur + n;
      RETURN RdClass.SeekResult.Ready;
    ELSIF n = 0 THEN
      RETURN RdClass.SeekResult.Eof;
    ELSIF n = -M3jSsh.Alerted THEN
      IF Thread.TestAlert () THEN RAISE Thread.Alerted END;
      RAISE Rd.Failure (Problem (M3jSsh.Failure, "the wait was given up"));
    ELSIF n = -M3jSsh.Timeout AND dontBlock THEN
      RETURN RdClass.SeekResult.WouldBlock;
    ELSE
      RAISE Rd.Failure (Problem (-n, err));
    END;
  END OutSeek;

PROCEDURE OutLength (<*UNUSED*> rd: OutRd): INTEGER =
  BEGIN
    RETURN -1;
  END OutLength;

PROCEDURE OutClose (rd: OutRd) =
  BEGIN
    M3jSsh.ProcDiscard (rd.p, rd.which);
  END OutClose;

PROCEDURE Push (w: InWr) RAISES {Wr.Failure, Thread.Alerted} =
  VAR n := w.cur - w.lo;  r: INTEGER;  err: TEXT := NIL;
  BEGIN
    IF n > 0 THEN
      r := M3jSsh.ProcWrite (w.p, SUBARRAY (w.buff^, 0, n), err);
      IF r = -M3jSsh.Alerted THEN
        IF Thread.TestAlert () THEN RAISE Thread.Alerted END;
        RAISE Wr.Failure (Problem (M3jSsh.Failure, "the write was given up"));
      ELSIF r < 0 THEN
        RAISE Wr.Failure (Problem (-r, err));
      END;
    END;
    w.lo := w.cur;
    w.hi := w.lo + NUMBER (w.buff^);
  END Push;

PROCEDURE InSeek (w: InWr; <*UNUSED*> n: CARDINAL) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    Push (w);
  END InSeek;

PROCEDURE InFlush (w: InWr) RAISES {Wr.Failure, Thread.Alerted} =
  BEGIN
    Push (w);
  END InFlush;

PROCEDURE InClose (w: InWr) =
  BEGIN
    M3jSsh.ProcCloseInput (w.p);
  END InClose;

BEGIN
  Refused := Atom.FromText ("SSH.Refused");
  Timeout := Atom.FromText ("SSH.Timeout");
  Disconnected := Atom.FromText ("SSH.Disconnected");
  AuthFailed := Atom.FromText ("SSH.AuthFailed");
  HostKeyUnknown := Atom.FromText ("SSH.HostKeyUnknown");
  HostKeyChanged := Atom.FromText ("SSH.HostKeyChanged");
  HostKeyRevoked := Atom.FromText ("SSH.HostKeyRevoked");
  BadKey := Atom.FromText ("SSH.BadKey");
  Busy := Atom.FromText ("SSH.Busy");
  TooLarge := Atom.FromText ("SSH.TooLarge");
  Failure := Atom.FromText ("SSH.Failure");
END SSH.
