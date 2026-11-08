INTERFACE RemoteView_T_v1;

IMPORT RemoteView, Rd, Wr, StubLib, Thread, NetObj;
TYPE Surrogate_RemoteView_T = RemoteView.T OBJECT
      OVERRIDES
        startrun := Surrogate_startrun;
        endrun := Surrogate_endrun;
        event := Surrogate_event;
      END;

PROCEDURE Surrogate_startrun(self: RemoteView.T) RAISES {NetObj.Error,
    Thread.Alerted};

PROCEDURE Surrogate_endrun(self: RemoteView.T) RAISES {NetObj.Error,
    Thread.Alerted};

PROCEDURE Surrogate_event(
    self: RemoteView.T;
    tfactor: REAL;
    nm: TEXT;
    args: TEXT) RAISES {RemoteView.Error, NetObj.Error, Thread.Alerted};

PROCEDURE Stub_startrun(
    self: RemoteView.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_endrun(
    self: RemoteView.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_event(
    self: RemoteView.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, RemoteView.Error};

END RemoteView_T_v1.
