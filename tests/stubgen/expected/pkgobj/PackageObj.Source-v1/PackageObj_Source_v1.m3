MODULE PackageObj_Source_v1 EXPORTS PackageObj, PackageObj_Source_v1;

IMPORT Rd, PackageObj, Wr, StubLib, AtomList, Thread, PkgErr, TextList,
       NetObj, RefList;
CONST Protocol: StubLib.StubProtocol = 1;

TYPE
      Methods = {links, pullFile, enum};
      ReturnCodes = {OK, PkgErr_E};

  PROCEDURE Surrogate_enum(self: PackageObj.Source): PackageObj.Enum
       RAISES {NetObj.Error, PkgErr.E, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: PackageObj.Enum;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.enum));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res.ts := StubLib.InLongreal(c, rep);
            res.dir := StubLib.InRef(c, rep, TYPECODE(RefList.T));
            res.fill := StubLib.InRef(c, rep, -1);
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
    END Surrogate_enum;

  PROCEDURE Surrogate_pullFile(
      self: PackageObj.Source;
      path_arg: TextList.T): Rd.T RAISES {NetObj.Error, PkgErr.E,
      Thread.Alerted} =

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
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(Rd.T));
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
    END Surrogate_pullFile;

  PROCEDURE Surrogate_links(self: PackageObj.Source)
      : PackageObj.ExportLinks RAISES {NetObj.Error, PkgErr.E,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: PackageObj.ExportLinks;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.links));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(PackageObj.ExportLinks));
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
    END Surrogate_links;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, PackageObj.Source);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.enum) => Stub_enum(t, c, rep);
      | ORD(Methods.pullFile) => Stub_pullFile(t, c, rep);
      | ORD(Methods.links) => Stub_links(t, c, rep);
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

PROCEDURE Stub_enum(
    self: PackageObj.Source;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR res: PackageObj.Enum;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    res := self.enum();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutLongreal(c, res.ts);
    StubLib.OutRef(c, res.dir);
    StubLib.OutRef(c, res.fill);

  END Stub_enum;

PROCEDURE Stub_pullFile(
    self: PackageObj.Source;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR path_arg: TextList.T;
      res: Rd.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    path_arg := StubLib.InRef(c, rep, TYPECODE(TextList.T));
    res := self.pullFile(path_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_pullFile;

PROCEDURE Stub_links(
    self: PackageObj.Source;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR res: PackageObj.ExportLinks;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    res := self.links();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_links;

BEGIN
  StubLib.Register(TYPECODE(PackageObj.Source), 1, TYPECODE(Surrogate_PackageObj_Source), Invoke);
END PackageObj_Source_v1.
