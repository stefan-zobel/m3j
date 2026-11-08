INTERFACE Test_T_v1;

IMPORT Test, Rd, Wr, StubLib, Thread, NetObj;
TYPE Surrogate_Test_T = Test.T OBJECT
      OVERRIDES
        null := Surrogate_null;
        ten := Surrogate_ten;
        objarg := Surrogate_objarg;
        rd := Surrogate_rd;
        wr := Surrogate_wr;
        newObj := Surrogate_newObj;
        newReturn := Surrogate_newReturn;
        pickle := Surrogate_pickle;
      END;

PROCEDURE Surrogate_null(self: Test.T) RAISES {};

PROCEDURE Surrogate_ten(
    self: Test.T;
    a0: INTEGER;
    a1: INTEGER;
    a2: INTEGER;
    a3: INTEGER;
    a4: INTEGER;
    a5: INTEGER;
    a6: INTEGER;
    a7: INTEGER;
    a8: INTEGER;
    a9: INTEGER) RAISES {};

PROCEDURE Surrogate_objarg(self: Test.T; arg: NetObj.T) RAISES {};

PROCEDURE Surrogate_rd(self: Test.T): Rd.T RAISES {};

PROCEDURE Surrogate_wr(self: Test.T): Wr.T RAISES {};

PROCEDURE Surrogate_newObj(self: Test.T): NetObj.T RAISES {};

PROCEDURE Surrogate_newReturn(
    self: Test.T;
    type: Test.ObjType;
    surr: NetObj.T): Test.Return RAISES {};

PROCEDURE Surrogate_pickle(self: Test.T; r: REFANY) RAISES {};

PROCEDURE Stub_null(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_ten(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_objarg(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_rd(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_wr(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_newObj(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_newReturn(
    self: Test.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_pickle(self: Test.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

END Test_T_v1.
