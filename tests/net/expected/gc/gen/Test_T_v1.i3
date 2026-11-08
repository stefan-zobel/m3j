INTERFACE Test_T_v1;

IMPORT Test, Rd, Wr, StubLib, Thread, NetObj;
TYPE Surrogate_Test_T = Test.T OBJECT
      OVERRIDES
        make := Surrogate_make;
        get := Surrogate_get;
        live := Surrogate_live;
        hold := Surrogate_hold;
        drop := Surrogate_drop;
      END;

PROCEDURE Surrogate_make(self: Test.T; id: INTEGER): Test.Obj
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_get(self: Test.T; id: INTEGER): Test.Obj
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_live(self: Test.T): INTEGER RAISES {NetObj.Error,
    Thread.Alerted};

PROCEDURE Surrogate_hold(self: Test.T; o: Test.Obj) RAISES {NetObj.Error,
    Thread.Alerted};

PROCEDURE Surrogate_drop(self: Test.T): INTEGER RAISES {NetObj.Error,
    Thread.Alerted};

PROCEDURE Stub_make(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_get(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_live(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_hold(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_drop(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

END Test_T_v1.
