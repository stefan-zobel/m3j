INTERFACE Test_Waiter_v1;

IMPORT Test, Rd, Wr, StubLib, Thread, NetObj;
TYPE Surrogate_Test_Waiter = Test.Waiter OBJECT
      OVERRIDES
        wait := Surrogate_wait;
      END;

PROCEDURE Surrogate_wait(self: Test.Waiter) RAISES {NetObj.Error,
    Thread.Alerted};

PROCEDURE Stub_wait(
    self: Test.Waiter;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

END Test_Waiter_v1.
