INTERFACE ObValue_RemEngine_v2;

IMPORT Rd, Wr, StubLib, ObValue, Thread, NetObj;
TYPE Surrogate_ObValue_RemEngine = ObValue.RemEngine OBJECT
      OVERRIDES
        Who := Surrogate_Who;
        Eval := Surrogate_Eval;
      END;

PROCEDURE Surrogate_Who(self: ObValue.RemEngine): TEXT
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_Eval(
    self: ObValue.RemEngine;
    proc: ObValue.Val;
    mySelf: ObValue.ValObj): ObValue.Val RAISES {ObValue.Error,
    ObValue.Exception, ObValue.ServerError, NetObj.Error, Thread.Alerted};

PROCEDURE Stub_Who(
    self: ObValue.RemEngine;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_Eval(
    self: ObValue.RemEngine;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, ObValue.Error, ObValue.Exception, ObValue.ServerError};

END ObValue_RemEngine_v2.
