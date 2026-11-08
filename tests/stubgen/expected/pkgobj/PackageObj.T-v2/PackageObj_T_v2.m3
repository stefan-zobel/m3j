MODULE PackageObj_T_v2 EXPORTS PackageObj, PackageObj_T_v2;

IMPORT Rd, PackageObj, Wr, StubLib, AtomList, Thread, PkgErr, TextList,
       NetPath, NetObj;
CONST Protocol: StubLib.StubProtocol = 2;

TYPE
      Methods = {status, checkDir, removeBackup, vcommit, newShip,
        newSource, version};
      ReturnCodes = {OK, PkgErr_E};

  PROCEDURE Surrogate_version(self: PackageObj.T; package_arg: NetPath.PN)
      : PackageObj.Version RAISES {NetObj.Error, PkgErr.E, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: PackageObj.Version;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.version));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, package_arg.dir);
            StubLib.OutRef(c, package_arg.arc);
            END;
            rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res.t := StubLib.InInteger(c, rep, -2147483647, 2147483647);
            res.vn := StubLib.InInteger(c, rep, -2147483647, 2147483647);
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
    END Surrogate_version;

  PROCEDURE Surrogate_newSource(
      self: PackageObj.T;
      auth_arg: TEXT;
      package_arg: NetPath.PN;
      VAR OUTversion_arg: PackageObj.Version): PackageObj.Source
       RAISES {NetObj.Error, PkgErr.E, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: PackageObj.Source;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.newSource));
          StubLib.OutRef(c, auth_arg);
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, package_arg.dir);
            StubLib.OutRef(c, package_arg.arc);
            END;
            dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutInteger(c, OUTversion_arg.t);
            StubLib.OutInteger(c, OUTversion_arg.vn);
            END;
            rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            OUTversion_arg.t := StubLib.InInteger(c, rep, -2147483647, 2147483647);
            OUTversion_arg.vn := StubLib.InInteger(c, rep, -2147483647, 2147483647);
            res := StubLib.InRef(c, rep, TYPECODE(PackageObj.Source));
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
    END Surrogate_newSource;

  PROCEDURE Surrogate_newShip(
      self: PackageObj.T;
      auth_arg: TEXT;
      package_arg: NetPath.PN;
      options_arg: PackageObj.ShipOptions): PackageObj.Ship
       RAISES {NetObj.Error, PkgErr.E, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: PackageObj.Ship;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.newShip));
          StubLib.OutRef(c, auth_arg);
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, package_arg.dir);
            StubLib.OutRef(c, package_arg.arc);
            END;
            dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutInteger(c, ORD(options_arg.keepBackup));
            StubLib.OutInteger(c, ORD(options_arg.purgeLinks));
            StubLib.OutInteger(c, ORD(options_arg.forceDateMatch));
            END;
            rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(PackageObj.Ship));
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
    END Surrogate_newShip;

  PROCEDURE Surrogate_vcommit(
      self: PackageObj.T;
      auth_arg: TEXT;
      package_arg: NetPath.PN;
      version_arg: PackageObj.Version;
      prevVersion_arg: PackageObj.Version): BOOLEAN RAISES {NetObj.Error,
      PkgErr.E, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: BOOLEAN;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.vcommit));
          StubLib.OutRef(c, auth_arg);
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
            dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutInteger(c, prevVersion_arg.t);
            StubLib.OutInteger(c, prevVersion_arg.vn);
            END;
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
    END Surrogate_vcommit;

  PROCEDURE Surrogate_removeBackup(
      self: PackageObj.T;
      auth_arg: TEXT;
      package_arg: NetPath.PN) RAISES {NetObj.Error, PkgErr.E,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.removeBackup));
          StubLib.OutRef(c, auth_arg);
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, package_arg.dir);
            StubLib.OutRef(c, package_arg.arc);
            END;
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
    END Surrogate_removeBackup;

  PROCEDURE Surrogate_checkDir(
      self: PackageObj.T;
      dir_arg: TextList.T;
      child_arg: TEXT) RAISES {NetObj.Error, PkgErr.E, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.checkDir));
          StubLib.OutRef(c, dir_arg);
          StubLib.OutRef(c, child_arg);
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
    END Surrogate_checkDir;

  PROCEDURE Surrogate_status(self: PackageObj.T): TEXT
       RAISES {NetObj.Error, Thread.Alerted} =

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
  VAR t := NARROW(obj, PackageObj.T);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.version) => Stub_version(t, c, rep);
      | ORD(Methods.newSource) => Stub_newSource(t, c, rep);
      | ORD(Methods.newShip) => Stub_newShip(t, c, rep);
      | ORD(Methods.vcommit) => Stub_vcommit(t, c, rep);
      | ORD(Methods.removeBackup) => Stub_removeBackup(t, c, rep);
      | ORD(Methods.checkDir) => Stub_checkDir(t, c, rep);
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

PROCEDURE Stub_version(
    self: PackageObj.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR package_arg: NetPath.PN;
      res: PackageObj.Version;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      package_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      package_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      res := self.version(package_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutInteger(c, res.t);
    StubLib.OutInteger(c, res.vn);

  END Stub_version;

PROCEDURE Stub_newSource(
    self: PackageObj.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR auth_arg: TEXT;
      package_arg: NetPath.PN;
      OUTversion_arg: PackageObj.Version;
      res: PackageObj.Source;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    auth_arg := StubLib.InRef(c, rep, -1);
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      package_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      package_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      OUTversion_arg.t := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      OUTversion_arg.vn := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      END;
      res := self.newSource(auth_arg, package_arg, OUTversion_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutInteger(c, OUTversion_arg.t);
    StubLib.OutInteger(c, OUTversion_arg.vn);
    StubLib.OutRef(c, res);

  END Stub_newSource;

PROCEDURE Stub_newShip(
    self: PackageObj.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR auth_arg: TEXT;
      package_arg: NetPath.PN;
      options_arg: PackageObj.ShipOptions;
      res: PackageObj.Ship;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    auth_arg := StubLib.InRef(c, rep, -1);
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      package_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      package_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      options_arg.keepBackup := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
      options_arg.purgeLinks := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
      options_arg.forceDateMatch := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
      END;
      res := self.newShip(auth_arg, package_arg, options_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_newShip;

PROCEDURE Stub_vcommit(
    self: PackageObj.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR auth_arg: TEXT;
      package_arg: NetPath.PN;
      version_arg: PackageObj.Version;
      prevVersion_arg: PackageObj.Version;
      res: BOOLEAN;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    auth_arg := StubLib.InRef(c, rep, -1);
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
      dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      prevVersion_arg.t := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      prevVersion_arg.vn := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      END;
      res := self.vcommit(auth_arg, package_arg, version_arg, prevVersion_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutInteger(c, ORD(res));

  END Stub_vcommit;

PROCEDURE Stub_removeBackup(
    self: PackageObj.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR auth_arg: TEXT;
      package_arg: NetPath.PN;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    auth_arg := StubLib.InRef(c, rep, -1);
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      package_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      package_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      self.removeBackup(auth_arg, package_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_removeBackup;

PROCEDURE Stub_checkDir(
    self: PackageObj.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR dir_arg: TextList.T;
      child_arg: TEXT;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    dir_arg := StubLib.InRef(c, rep, TYPECODE(TextList.T));
    child_arg := StubLib.InRef(c, rep, -1);
    self.checkDir(dir_arg, child_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_checkDir;

PROCEDURE Stub_status(
    self: PackageObj.T;
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
  StubLib.Register(TYPECODE(PackageObj.T), 2, TYPECODE(Surrogate_PackageObj_T), Invoke);
END PackageObj_T_v2.
