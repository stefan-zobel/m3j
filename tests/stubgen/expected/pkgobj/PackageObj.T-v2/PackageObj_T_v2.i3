INTERFACE PackageObj_T_v2;

IMPORT Rd, PackageObj, Wr, StubLib, Thread, PkgErr, TextList, NetPath,
       NetObj;
TYPE Surrogate_PackageObj_T = PackageObj.T OBJECT
      OVERRIDES
        version := Surrogate_version;
        newSource := Surrogate_newSource;
        newShip := Surrogate_newShip;
        vcommit := Surrogate_vcommit;
        removeBackup := Surrogate_removeBackup;
        checkDir := Surrogate_checkDir;
        status := Surrogate_status;
      END;

PROCEDURE Surrogate_version(self: PackageObj.T; package: NetPath.PN)
    : PackageObj.Version RAISES {NetObj.Error, PkgErr.E, Thread.Alerted};

PROCEDURE Surrogate_newSource(
    self: PackageObj.T;
    auth: TEXT;
    package: NetPath.PN;
    VAR OUTversion: PackageObj.Version): PackageObj.Source
     RAISES {NetObj.Error, PkgErr.E, Thread.Alerted};

PROCEDURE Surrogate_newShip(
    self: PackageObj.T;
    auth: TEXT;
    package: NetPath.PN;
    options: PackageObj.ShipOptions): PackageObj.Ship RAISES {NetObj.Error,
    PkgErr.E, Thread.Alerted};

PROCEDURE Surrogate_vcommit(
    self: PackageObj.T;
    auth: TEXT;
    package: NetPath.PN;
    version: PackageObj.Version;
    prevVersion: PackageObj.Version): BOOLEAN RAISES {NetObj.Error,
    PkgErr.E, Thread.Alerted};

PROCEDURE Surrogate_removeBackup(
    self: PackageObj.T;
    auth: TEXT;
    package: NetPath.PN) RAISES {NetObj.Error, PkgErr.E, Thread.Alerted};

PROCEDURE Surrogate_checkDir(
    self: PackageObj.T;
    dir: TextList.T;
    child: TEXT) RAISES {NetObj.Error, PkgErr.E, Thread.Alerted};

PROCEDURE Surrogate_status(self: PackageObj.T): TEXT RAISES {NetObj.Error,
    Thread.Alerted};

PROCEDURE Stub_version(
    self: PackageObj.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_newSource(
    self: PackageObj.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_newShip(
    self: PackageObj.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_vcommit(
    self: PackageObj.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_removeBackup(
    self: PackageObj.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_checkDir(
    self: PackageObj.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_status(
    self: PackageObj.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

END PackageObj_T_v2.
