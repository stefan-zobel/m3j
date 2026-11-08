INTERFACE Test_T_v1;

IMPORT Test, Rd, Wr, StubLib, Thread, NetObj;
TYPE Surrogate_Test_T = Test.T OBJECT
      OVERRIDES
        swap := Surrogate_swap;
      END;

PROCEDURE Surrogate_swap(self: Test.T; r: REFANY): REFANY
     RAISES {NetObj.Error};

PROCEDURE Stub_swap(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

END Test_T_v1.
