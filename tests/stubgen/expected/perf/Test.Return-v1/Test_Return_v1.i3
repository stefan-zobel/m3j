INTERFACE Test_Return_v1;

IMPORT Test, Rd, Wr, StubLib, Thread, NetObj;
TYPE Surrogate_Test_Return = Test.Return OBJECT
      OVERRIDES
        doIt := Surrogate_doIt;
      END;

PROCEDURE Surrogate_doIt(self: Test.Return): NetObj.T RAISES {};

PROCEDURE Stub_doIt(
    self: Test.Return;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

END Test_Return_v1.
