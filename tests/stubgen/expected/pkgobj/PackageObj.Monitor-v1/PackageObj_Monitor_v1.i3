INTERFACE PackageObj_Monitor_v1;

IMPORT Rd, PackageObj, Wr, StubLib, Thread, NetObj;
TYPE Surrogate_PackageObj_Monitor = PackageObj.Monitor OBJECT
      OVERRIDES
        report := Surrogate_report;
      END;

PROCEDURE Surrogate_report(self: PackageObj.Monitor; arg: REFANY)
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Stub_report(
    self: PackageObj.Monitor;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

END PackageObj_Monitor_v1.
