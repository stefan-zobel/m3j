INTERFACE ObValue_NonRemObjHook_v1;

IMPORT Rd, Wr, StubLib, ObValue, Thread, NetObj;
TYPE Surrogate_ObValue_NonRemObjHook = ObValue.NonRemObjHook OBJECT
      OVERRIDES
        init := Surrogate_init;
        Get := Surrogate_Get;
      END;

PROCEDURE Surrogate_init(
    self: ObValue.NonRemObjHook;
    remObj: ObValue.ValObj): ObValue.NonRemObjHook RAISES {NetObj.Error,
    Thread.Alerted};

PROCEDURE Surrogate_Get(self: ObValue.NonRemObjHook): ObValue.ValObj
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Stub_init(
    self: ObValue.NonRemObjHook;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_Get(
    self: ObValue.NonRemObjHook;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

END ObValue_NonRemObjHook_v1.
