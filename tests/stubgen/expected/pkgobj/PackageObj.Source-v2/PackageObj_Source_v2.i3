INTERFACE PackageObj_Source_v2;

IMPORT Rd, PackageObj, Wr, StubLib, Thread, PkgErr, TextList, NetObj,
       RefList;
TYPE Surrogate_PackageObj_Source = PackageObj.Source OBJECT
      OVERRIDES
        enum := Surrogate_enum;
        pullFile := Surrogate_pullFile;
        links := Surrogate_links;
      END;

PROCEDURE Surrogate_enum(self: PackageObj.Source): PackageObj.Enum
     RAISES {NetObj.Error, PkgErr.E, Thread.Alerted};

PROCEDURE Surrogate_pullFile(self: PackageObj.Source; path: TextList.T)
    : Rd.T RAISES {NetObj.Error, PkgErr.E, Thread.Alerted};

PROCEDURE Surrogate_links(self: PackageObj.Source): PackageObj.ExportLinks
     RAISES {NetObj.Error, PkgErr.E, Thread.Alerted};

PROCEDURE Stub_enum(
    self: PackageObj.Source;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_pullFile(
    self: PackageObj.Source;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_links(
    self: PackageObj.Source;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

END PackageObj_Source_v2.
