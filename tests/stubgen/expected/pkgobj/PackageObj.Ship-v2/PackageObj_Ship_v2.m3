MODULE PackageObj_Ship_v2 EXPORTS PackageObj, PackageObj_Ship_v2;

IMPORT Rd, PackageObj, Wr, StubLib, AtomList, Thread, PkgErr, TextList,
       NetObj;
CONST Protocol: StubLib.StubProtocol = 2;

TYPE
      Methods = {pullFile, commit, prepare};
      ReturnCodes = {OK, PackageObj_SourceOutOfDate, PkgErr_E};

  PROCEDURE Surrogate_prepare(
      self: PackageObj.Ship;
      source_arg: PackageObj.Source;
      siblings_arg: PackageObj.Siblings;
      monitor_arg: PackageObj.Monitor) RAISES {PackageObj.SourceOutOfDate,
      NetObj.Error, PkgErr.E, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.prepare));
          StubLib.OutRef(c, source_arg);
          StubLib.OutRef(c, siblings_arg);
          StubLib.OutRef(c, monitor_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.PackageObj_SourceOutOfDate) =>
            VAR arg: TextList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(TextList.T));
              reuse := TRUE;
              RAISE PackageObj.SourceOutOfDate(arg);

            END;
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
    END Surrogate_prepare;

  PROCEDURE Surrogate_commit(
      self: PackageObj.Ship;
      version_arg: PackageObj.Version) RAISES {NetObj.Error, PkgErr.E,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.commit));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutInteger(c, version_arg.t);
            StubLib.OutInteger(c, version_arg.vn);
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
    END Surrogate_commit;

  PROCEDURE Surrogate_pullFile(
      self: PackageObj.Ship;
      path_arg: TextList.T;
      date_arg: LONGREAL;
      length_arg: CARDINAL): Rd.T RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: Rd.T;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.pullFile));
          StubLib.OutRef(c, path_arg);
          StubLib.OutLongreal(c, date_arg);
          StubLib.OutInteger(c, length_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(Rd.T));
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
    END Surrogate_pullFile;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, PackageObj.Ship);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.prepare) => Stub_prepare(t, c, rep);
      | ORD(Methods.commit) => Stub_commit(t, c, rep);
      | ORD(Methods.pullFile) => Stub_pullFile(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    | PkgErr.E(arg) =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.PkgErr_E));
        StubLib.OutRef(c, arg);

    | PackageObj.SourceOutOfDate(arg) =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.PackageObj_SourceOutOfDate));
        StubLib.OutRef(c, arg);
    END;
  END Invoke;

PROCEDURE Stub_prepare(
    self: PackageObj.Ship;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PackageObj.SourceOutOfDate, PkgErr.E}=
  VAR source_arg: PackageObj.Source;
      siblings_arg: PackageObj.Siblings;
      monitor_arg: PackageObj.Monitor;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    source_arg := StubLib.InRef(c, rep, TYPECODE(PackageObj.Source));
    siblings_arg := StubLib.InRef(c, rep, TYPECODE(PackageObj.Siblings));
    monitor_arg := StubLib.InRef(c, rep, TYPECODE(PackageObj.Monitor));
    self.prepare(source_arg, siblings_arg, monitor_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_prepare;

PROCEDURE Stub_commit(
    self: PackageObj.Ship;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR version_arg: PackageObj.Version;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      version_arg.t := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      version_arg.vn := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      END;
      self.commit(version_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_commit;

PROCEDURE Stub_pullFile(
    self: PackageObj.Ship;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR path_arg: TextList.T;
      date_arg: LONGREAL;
      length_arg: CARDINAL;
      res: Rd.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    path_arg := StubLib.InRef(c, rep, TYPECODE(TextList.T));
    date_arg := StubLib.InLongreal(c, rep);
    length_arg := StubLib.InInteger(c, rep, 0, 9223372036854775807);
    res := self.pullFile(path_arg, date_arg, length_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_pullFile;

BEGIN
  StubLib.Register(TYPECODE(PackageObj.Ship), 2, TYPECODE(Surrogate_PackageObj_Ship), Invoke);
END PackageObj_Ship_v2.
