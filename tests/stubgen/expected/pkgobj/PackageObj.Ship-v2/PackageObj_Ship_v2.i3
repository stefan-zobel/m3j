INTERFACE PackageObj_Ship_v2;

IMPORT Rd, PackageObj, Wr, StubLib, Thread, PkgErr, TextList, NetObj;
TYPE Surrogate_PackageObj_Ship = PackageObj.Ship OBJECT
      OVERRIDES
        prepare := Surrogate_prepare;
        commit := Surrogate_commit;
        pullFile := Surrogate_pullFile;
      END;

PROCEDURE Surrogate_prepare(
    self: PackageObj.Ship;
    source: PackageObj.Source;
    siblings: PackageObj.Siblings;
    monitor: PackageObj.Monitor) RAISES {PackageObj.SourceOutOfDate,
    NetObj.Error, PkgErr.E, Thread.Alerted};

PROCEDURE Surrogate_commit(
    self: PackageObj.Ship;
    version: PackageObj.Version) RAISES {NetObj.Error, PkgErr.E,
    Thread.Alerted};

PROCEDURE Surrogate_pullFile(
    self: PackageObj.Ship;
    path: TextList.T;
    date: LONGREAL;
    length: CARDINAL): Rd.T RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Stub_prepare(
    self: PackageObj.Ship;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PackageObj.SourceOutOfDate, PkgErr.E};

PROCEDURE Stub_commit(
    self: PackageObj.Ship;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_pullFile(
    self: PackageObj.Ship;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

END PackageObj_Ship_v2.
