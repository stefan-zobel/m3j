INTERFACE ObValue_RemVar_v2;

IMPORT Rd, Wr, StubLib, ObValue, Thread, NetObj;
TYPE Surrogate_ObValue_RemVar = ObValue.RemVar OBJECT
      OVERRIDES
        Get := Surrogate_Get;
        Set := Surrogate_Set;
      END;

PROCEDURE Surrogate_Get(self: ObValue.RemVar): ObValue.Val
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_Set(self: ObValue.RemVar; val: ObValue.Val)
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Stub_Get(
    self: ObValue.RemVar;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_Set(
    self: ObValue.RemVar;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

END ObValue_RemVar_v2.
