INTERFACE LongCall_T_v2;

IMPORT LongCall, Rd, Wr, StubLib, Thread, NetObj;
TYPE Surrogate_LongCall_T = LongCall.T OBJECT
      OVERRIDES
        wait := Surrogate_wait;
      END;

PROCEDURE Surrogate_wait(self: LongCall.T) RAISES {NetObj.Error,
    Thread.Alerted};

PROCEDURE Stub_wait(
    self: LongCall.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

END LongCall_T_v2.
