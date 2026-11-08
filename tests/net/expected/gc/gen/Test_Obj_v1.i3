INTERFACE Test_Obj_v1;

IMPORT Test, Rd, Wr, StubLib, Thread, NetObj;
TYPE Surrogate_Test_Obj = Test.Obj OBJECT
      OVERRIDES
        id := Surrogate_id;
      END;

PROCEDURE Surrogate_id(self: Test.Obj): INTEGER RAISES {NetObj.Error,
    Thread.Alerted};

PROCEDURE Stub_id(self: Test.Obj; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

END Test_Obj_v1.
