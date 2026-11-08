INTERFACE Siphon_T_v2;

IMPORT Siphon, Rd, PackageObj, Wr, StubLib, Thread, PkgErr, TextList,
       NetPath, LockOps, NetObj;
TYPE Surrogate_Siphon_T = Siphon.T OBJECT
      OVERRIDES
        ship := Surrogate_ship;
        enqueue := Surrogate_enqueue;
        dequeue := Surrogate_dequeue;
        synch := Surrogate_synch;
        lockserver := Surrogate_lockserver;
        status := Surrogate_status;
      END;

PROCEDURE Surrogate_ship(
    self: Siphon.T;
    package: NetPath.PN;
    source: PackageObj.Source;
    version: PackageObj.Version;
    caller: TEXT;
    manager: TEXT;
    fwdSites: TextList.T) RAISES {PkgErr.E, NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_enqueue(
    self: Siphon.T;
    package: NetPath.PN;
    version: PackageObj.Version;
    manager: TEXT;
    forSite: TEXT;
    urgent: BOOLEAN) RAISES {PkgErr.E, NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_dequeue(
    self: Siphon.T;
    package: NetPath.PN;
    forSite: TEXT;
    interruptSend: BOOLEAN): BOOLEAN RAISES {PkgErr.E, NetObj.Error,
    Thread.Alerted};

PROCEDURE Surrogate_synch(
    self: Siphon.T;
    kind: Siphon.SynchKind;
    package: NetPath.PN): TEXT RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_lockserver(self: Siphon.T): LockOps.T RAISES {PkgErr.E,
    NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_status(self: Siphon.T): TEXT RAISES {NetObj.Error,
    Thread.Alerted};

PROCEDURE Stub_ship(self: Siphon.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted,
    PkgErr.E};

PROCEDURE Stub_enqueue(
    self: Siphon.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_dequeue(
    self: Siphon.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_synch(self: Siphon.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

PROCEDURE Stub_lockserver(
    self: Siphon.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_status(
    self: Siphon.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

END Siphon_T_v2.
