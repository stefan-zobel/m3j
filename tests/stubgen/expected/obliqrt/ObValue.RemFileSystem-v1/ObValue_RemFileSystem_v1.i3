INTERFACE ObValue_RemFileSystem_v1;

IMPORT Rd, Wr, StubLib, ObValue, Thread, NetObj;
TYPE Surrogate_ObValue_RemFileSystem = ObValue.RemFileSystem OBJECT
      OVERRIDES
        OpenRead := Surrogate_OpenRead;
        OpenWrite := Surrogate_OpenWrite;
        OpenAppend := Surrogate_OpenAppend;
      END;

PROCEDURE Surrogate_OpenRead(self: ObValue.RemFileSystem; fileName: TEXT)
    : Rd.T RAISES {NetObj.Error, ObValue.ServerError, Thread.Alerted};

PROCEDURE Surrogate_OpenWrite(self: ObValue.RemFileSystem; fileName: TEXT)
    : Wr.T RAISES {NetObj.Error, ObValue.ServerError, Thread.Alerted};

PROCEDURE Surrogate_OpenAppend(self: ObValue.RemFileSystem; fileName: TEXT)
    : Wr.T RAISES {NetObj.Error, ObValue.ServerError, Thread.Alerted};

PROCEDURE Stub_OpenRead(
    self: ObValue.RemFileSystem;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, ObValue.ServerError};

PROCEDURE Stub_OpenWrite(
    self: ObValue.RemFileSystem;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, ObValue.ServerError};

PROCEDURE Stub_OpenAppend(
    self: ObValue.RemFileSystem;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, ObValue.ServerError};

END ObValue_RemFileSystem_v1.
