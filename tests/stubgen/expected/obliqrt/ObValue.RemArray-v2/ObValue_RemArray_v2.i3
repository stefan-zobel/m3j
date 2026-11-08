INTERFACE ObValue_RemArray_v2;

IMPORT Rd, Wr, StubLib, ObValue, Thread, NetObj;
TYPE Surrogate_ObValue_RemArray = ObValue.RemArray OBJECT
      OVERRIDES
        Size := Surrogate_Size;
        Get := Surrogate_Get;
        Set := Surrogate_Set;
        Sub := Surrogate_Sub;
        Upd := Surrogate_Upd;
        Obtain := Surrogate_Obtain;
      END;

PROCEDURE Surrogate_Size(self: ObValue.RemArray): INTEGER
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_Get(self: ObValue.RemArray; i: INTEGER): ObValue.Val
     RAISES {ObValue.ServerError, NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_Set(
    self: ObValue.RemArray;
    i: INTEGER;
    val: ObValue.Val) RAISES {ObValue.ServerError, NetObj.Error,
    Thread.Alerted};

PROCEDURE Surrogate_Sub(
    self: ObValue.RemArray;
    start: INTEGER;
    size: INTEGER): ObValue.ValArray RAISES {ObValue.ServerError,
    NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_Upd(
    self: ObValue.RemArray;
    start: INTEGER;
    size: INTEGER;
    READONLY other: REF ObValue.Vals) RAISES {ObValue.ServerError,
    NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_Obtain(self: ObValue.RemArray): REF ObValue.Vals
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Stub_Size(
    self: ObValue.RemArray;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_Get(
    self: ObValue.RemArray;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, ObValue.ServerError};

PROCEDURE Stub_Set(
    self: ObValue.RemArray;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, ObValue.ServerError};

PROCEDURE Stub_Sub(
    self: ObValue.RemArray;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, ObValue.ServerError};

PROCEDURE Stub_Upd(
    self: ObValue.RemArray;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, ObValue.ServerError};

PROCEDURE Stub_Obtain(
    self: ObValue.RemArray;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

END ObValue_RemArray_v2.
