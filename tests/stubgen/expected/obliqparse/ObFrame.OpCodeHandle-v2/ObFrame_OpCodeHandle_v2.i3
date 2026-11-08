INTERFACE ObFrame_OpCodeHandle_v2;

IMPORT Fingerprint, Rd, Wr, StubLib, ObLib, ObFrame, Thread, NetObj;
TYPE Surrogate_ObFrame_OpCodeHandle = ObFrame.OpCodeHandle OBJECT
      OVERRIDES
        getOpCodes := Surrogate_getOpCodes;
      END;

PROCEDURE Surrogate_getOpCodes(
    self: ObFrame.OpCodeHandle;
    ts: Fingerprint.T): REF ObLib.OpCodes RAISES {NetObj.Error,
    Thread.Alerted};

PROCEDURE Stub_getOpCodes(
    self: ObFrame.OpCodeHandle;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

END ObFrame_OpCodeHandle_v2.
