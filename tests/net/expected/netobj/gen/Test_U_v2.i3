INTERFACE Test_U_v2;

IMPORT Test, Rd, Wr, StubLib, Thread, NetObj;
TYPE Surrogate_Test_U = Test.U OBJECT
      OVERRIDES
        list := Surrogate_list;
        sum := Surrogate_sum;
        rec := Surrogate_rec;
        same := Surrogate_same;
      END;

PROCEDURE Surrogate_list(self: Test.U; n: INTEGER): Test.List
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_sum(self: Test.U; l: Test.List): INTEGER
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_rec(self: Test.U; READONLY r: Test.Rec): Test.Rec
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_same(self: Test.U; t: Test.T): BOOLEAN
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Stub_list(self: Test.U; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_sum(self: Test.U; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_rec(self: Test.U; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_same(self: Test.U; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

END Test_U_v2.
