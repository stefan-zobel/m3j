INTERFACE Test_T_v2;

IMPORT Test, Rd, Wr, StubLib, Thread, NetObj;
TYPE Surrogate_Test_T = Test.T OBJECT
      OVERRIDES
        null := Surrogate_null;
      END;

PROCEDURE Surrogate_null(self: Test.T) RAISES {NetObj.Error,
    Thread.Alerted};

PROCEDURE Stub_null(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

END Test_T_v2.
