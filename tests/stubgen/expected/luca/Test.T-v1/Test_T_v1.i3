INTERFACE Test_T_v1;

IMPORT Test, Rd, OSError, Wr, StubLib, Thread, NetObj;
TYPE Surrogate_Test_T = Test.T OBJECT
      OVERRIDES
        getRd := Surrogate_getRd;
        getRdEmbedded := Surrogate_getRdEmbedded;
      END;

PROCEDURE Surrogate_getRd(self: Test.T; path: TEXT): Rd.T
     RAISES {NetObj.Error, OSError.E};

PROCEDURE Surrogate_getRdEmbedded(self: Test.T; path: TEXT): Test.RdPkl
     RAISES {NetObj.Error, OSError.E};

PROCEDURE Stub_getRd(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted,
    OSError.E};

PROCEDURE Stub_getRdEmbedded(
    self: Test.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, OSError.E};

END Test_T_v1.
