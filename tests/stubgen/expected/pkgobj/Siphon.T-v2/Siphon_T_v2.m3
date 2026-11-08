MODULE Siphon_T_v2 EXPORTS Siphon, Siphon_T_v2;

IMPORT Siphon, Rd, PackageObj, Wr, StubLib, AtomList, Thread, PkgErr,
       TextList, NetPath, LockOps, NetObj;
CONST Protocol: StubLib.StubProtocol = 2;

TYPE
      Methods = {status, lockserver, synch, dequeue, enqueue, ship};
      ReturnCodes = {OK, PkgErr_E};

  PROCEDURE Surrogate_ship(
      self: Siphon.T;
      package_arg: NetPath.PN;
      source_arg: PackageObj.Source;
      version_arg: PackageObj.Version;
      caller_arg: TEXT;
      manager_arg: TEXT;
      fwdSites_arg: TextList.T) RAISES {PkgErr.E, NetObj.Error,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.ship));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, package_arg.dir);
            StubLib.OutRef(c, package_arg.arc);
            END;
            StubLib.OutRef(c, source_arg);
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutInteger(c, version_arg.t);
            StubLib.OutInteger(c, version_arg.vn);
            END;
            StubLib.OutRef(c, caller_arg);
          StubLib.OutRef(c, manager_arg);
          StubLib.OutRef(c, fwdSites_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
    END Surrogate_ship;

  PROCEDURE Surrogate_enqueue(
      self: Siphon.T;
      package_arg: NetPath.PN;
      version_arg: PackageObj.Version;
      manager_arg: TEXT;
      forSite_arg: TEXT;
      urgent_arg: BOOLEAN) RAISES {PkgErr.E, NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.enqueue));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, package_arg.dir);
            StubLib.OutRef(c, package_arg.arc);
            END;
            dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutInteger(c, version_arg.t);
            StubLib.OutInteger(c, version_arg.vn);
            END;
            StubLib.OutRef(c, manager_arg);
          StubLib.OutRef(c, forSite_arg);
          StubLib.OutInteger(c, ORD(urgent_arg));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
    END Surrogate_enqueue;

  PROCEDURE Surrogate_dequeue(
      self: Siphon.T;
      package_arg: NetPath.PN;
      forSite_arg: TEXT;
      interruptSend_arg: BOOLEAN): BOOLEAN RAISES {PkgErr.E, NetObj.Error,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: BOOLEAN;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.dequeue));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, package_arg.dir);
            StubLib.OutRef(c, package_arg.arc);
            END;
            StubLib.OutRef(c, forSite_arg);
          StubLib.OutInteger(c, ORD(interruptSend_arg));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
      RETURN res;
    END Surrogate_dequeue;

  PROCEDURE Surrogate_synch(
      self: Siphon.T;
      kind_arg: Siphon.SynchKind;
      package_arg: NetPath.PN): TEXT RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: TEXT;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.synch));
          StubLib.OutInteger(c, ORD(kind_arg));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, package_arg.dir);
            StubLib.OutRef(c, package_arg.arc);
            END;
            rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, -1);
            reuse := TRUE;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
      RETURN res;
    END Surrogate_synch;

  PROCEDURE Surrogate_lockserver(self: Siphon.T): LockOps.T
       RAISES {PkgErr.E, NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: LockOps.T;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.lockserver));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(LockOps.T));
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
      RETURN res;
    END Surrogate_lockserver;

  PROCEDURE Surrogate_status(self: Siphon.T): TEXT RAISES {NetObj.Error,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: TEXT;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.status));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, -1);
            reuse := TRUE;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
      RETURN res;
    END Surrogate_status;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, Siphon.T);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.ship) => Stub_ship(t, c, rep);
      | ORD(Methods.enqueue) => Stub_enqueue(t, c, rep);
      | ORD(Methods.dequeue) => Stub_dequeue(t, c, rep);
      | ORD(Methods.synch) => Stub_synch(t, c, rep);
      | ORD(Methods.lockserver) => Stub_lockserver(t, c, rep);
      | ORD(Methods.status) => Stub_status(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    | PkgErr.E(arg) =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.PkgErr_E));
        StubLib.OutRef(c, arg);
    END;
  END Invoke;

PROCEDURE Stub_ship(
    self: Siphon.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR package_arg: NetPath.PN;
      source_arg: PackageObj.Source;
      version_arg: PackageObj.Version;
      caller_arg: TEXT;
      manager_arg: TEXT;
      fwdSites_arg: TextList.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      package_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      package_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      source_arg := StubLib.InRef(c, rep, TYPECODE(PackageObj.Source));
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      version_arg.t := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      version_arg.vn := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      END;
      caller_arg := StubLib.InRef(c, rep, -1);
    manager_arg := StubLib.InRef(c, rep, -1);
    fwdSites_arg := StubLib.InRef(c, rep, TYPECODE(TextList.T));
    self.ship(package_arg, source_arg, version_arg, caller_arg, manager_arg, fwdSites_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_ship;

PROCEDURE Stub_enqueue(
    self: Siphon.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR package_arg: NetPath.PN;
      version_arg: PackageObj.Version;
      manager_arg: TEXT;
      forSite_arg: TEXT;
      urgent_arg: BOOLEAN;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      package_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      package_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      version_arg.t := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      version_arg.vn := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      END;
      manager_arg := StubLib.InRef(c, rep, -1);
    forSite_arg := StubLib.InRef(c, rep, -1);
    urgent_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    self.enqueue(package_arg, version_arg, manager_arg, forSite_arg, urgent_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_enqueue;

PROCEDURE Stub_dequeue(
    self: Siphon.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR package_arg: NetPath.PN;
      forSite_arg: TEXT;
      interruptSend_arg: BOOLEAN;
      res: BOOLEAN;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      package_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      package_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      forSite_arg := StubLib.InRef(c, rep, -1);
    interruptSend_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    res := self.dequeue(package_arg, forSite_arg, interruptSend_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutInteger(c, ORD(res));

  END Stub_dequeue;

PROCEDURE Stub_synch(
    self: Siphon.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR kind_arg: Siphon.SynchKind;
      package_arg: NetPath.PN;
      res: TEXT;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    kind_arg := VAL(StubLib.InInteger(c, rep, 0,2), Siphon.SynchKind);
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      package_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      package_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      res := self.synch(kind_arg, package_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_synch;

PROCEDURE Stub_lockserver(
    self: Siphon.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR res: LockOps.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    res := self.lockserver();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_lockserver;

PROCEDURE Stub_status(
    self: Siphon.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR res: TEXT;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    res := self.status();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_status;

BEGIN
  StubLib.Register(TYPECODE(Siphon.T), 2, TYPECODE(Surrogate_Siphon_T), Invoke);
END Siphon_T_v2.
