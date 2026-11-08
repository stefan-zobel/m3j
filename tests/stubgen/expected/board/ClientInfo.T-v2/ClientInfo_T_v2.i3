INTERFACE ClientInfo_T_v2;

IMPORT Rd, Wr, StubLib, Callback, Thread, ClientInfo, RectR, NetObj;
TYPE Surrogate_ClientInfo_T = ClientInfo.T OBJECT
      OVERRIDES
        getScope := Surrogate_getScope;
        setScope := Surrogate_setScope;
        getCallback := Surrogate_getCallback;
      END;

PROCEDURE Surrogate_getScope(self: ClientInfo.T): RectR.T
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_setScope(self: ClientInfo.T; scope: RectR.T)
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_getCallback(self: ClientInfo.T): Callback.T
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Stub_getScope(
    self: ClientInfo.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_setScope(
    self: ClientInfo.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_getCallback(
    self: ClientInfo.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

END ClientInfo_T_v2.
