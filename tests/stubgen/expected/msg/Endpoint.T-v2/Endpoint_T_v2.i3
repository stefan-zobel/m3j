INTERFACE Endpoint_T_v2;

IMPORT Fingerprint, Endpoint, Rd, Wr, StubLib, Event, Thread, NetObj;
TYPE Surrogate_Endpoint_T = Endpoint.T OBJECT
      OVERRIDES
        space := Surrogate_space;
        connect := Surrogate_connect;
        disconnect := Surrogate_disconnect;
      END;

PROCEDURE Surrogate_space(self: Endpoint.T): Fingerprint.T
     RAISES {Thread.Alerted, NetObj.Error};

PROCEDURE Surrogate_connect(
    self: Endpoint.T;
    id: Fingerprint.T;
    rd: Rd.T;
    wr: Wr.T) RAISES {Thread.Alerted, NetObj.Error, Event.Error};

PROCEDURE Surrogate_disconnect(self: Endpoint.T; id: Fingerprint.T)
     RAISES {Thread.Alerted, NetObj.Error, Event.Error};

PROCEDURE Stub_space(
    self: Endpoint.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_connect(
    self: Endpoint.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, Event.Error};

PROCEDURE Stub_disconnect(
    self: Endpoint.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, Event.Error};

END Endpoint_T_v2.
