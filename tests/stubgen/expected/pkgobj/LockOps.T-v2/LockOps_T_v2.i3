INTERFACE LockOps_T_v2;

IMPORT Fingerprint, LockMethods, Rd, PackageObj, Wr, StubLib, Thread,
       PkgErr, TextList, NetPath, LockOps, NetObj;
TYPE Surrogate_LockOps_T = LockOps.T OBJECT
      OVERRIDES
        create := Surrogate_create;
        remove := Surrogate_remove;
        lock := Surrogate_lock;
        unlock := Surrogate_unlock;
        assign := Surrogate_assign;
        commit := Surrogate_commit;
        enumerate := Surrogate_enumerate;
        getEntry := Surrogate_getEntry;
        setEntry := Surrogate_setEntry;
        setFingerprint := Surrogate_setFingerprint;
        createCheck := Surrogate_createCheck;
        createForeign := Surrogate_createForeign;
        createDir := Surrogate_createDir;
        removeDir := Surrogate_removeDir;
        checkDir := Surrogate_checkDir;
        enumerateDirs := Surrogate_enumerateDirs;
        removeForeign := Surrogate_removeForeign;
      END;

PROCEDURE Surrogate_create(
    self: LockOps.T;
    auth: TEXT;
    pn: NetPath.PN;
    initialKey: TEXT;
    version: PackageObj.Version;
    remoteCheck: BOOLEAN) RAISES {NetObj.Error, PkgErr.E, Thread.Alerted};

PROCEDURE Surrogate_remove(
    self: LockOps.T;
    auth: TEXT;
    package: NetPath.PN;
    key: TEXT;
    reship: BOOLEAN) RAISES {NetObj.Error, PkgErr.E, Thread.Alerted,
    LockOps.CommitFailed, LockOps.LockConflict};

PROCEDURE Surrogate_lock(
    self: LockOps.T;
    auth: TEXT;
    pn: NetPath.PN;
    version: PackageObj.Version;
    key: TEXT;
    keySite: TEXT): PackageObj.Version RAISES {NetObj.Error, PkgErr.E,
    LockOps.LockConflict, Thread.Alerted};

PROCEDURE Surrogate_unlock(
    self: LockOps.T;
    auth: TEXT;
    pn: NetPath.PN;
    version: PackageObj.Version;
    key: TEXT;
    keySite: TEXT;
    breakRights: LockOps.BreakRights;
    forceVersion: BOOLEAN) RAISES {NetObj.Error, PkgErr.E,
    LockOps.CommitFailed, LockOps.LockConflict, LockOps.SynchVersions,
    Thread.Alerted};

PROCEDURE Surrogate_assign(
    self: LockOps.T;
    auth: TEXT;
    pn: NetPath.PN;
    key: TEXT;
    keySite: TEXT): PackageObj.Version RAISES {NetObj.Error, PkgErr.E,
    LockOps.LockConflict, Thread.Alerted};

PROCEDURE Surrogate_commit(
    self: LockOps.T;
    auth: TEXT;
    pn: NetPath.PN;
    version: PackageObj.Version;
    VAR ships: LockOps.ShipArray;
    reship: BOOLEAN): LockOps.CommitFailures RAISES {NetObj.Error,
    LockOps.CommitFailed, PkgErr.E, Thread.Alerted};

PROCEDURE Surrogate_enumerate(
    self: LockOps.T;
    dir: TextList.T;
    site: TEXT;
    locksOnly: BOOLEAN;
    localOnly: BOOLEAN;
    pendingOnly: BOOLEAN): LockOps.EnumList RAISES {NetObj.Error, PkgErr.E,
    Thread.Alerted};

PROCEDURE Surrogate_getEntry(
    self: LockOps.T;
    pn: NetPath.PN;
    goRemote: BOOLEAN): LockOps.RefEntry RAISES {NetObj.Error, PkgErr.E,
    Thread.Alerted};

PROCEDURE Surrogate_setEntry(
    self: LockOps.T;
    auth: TEXT;
    pn: NetPath.PN;
    entry: LockOps.RefEntry) RAISES {NetObj.Error, PkgErr.E,
    Thread.Alerted};

PROCEDURE Surrogate_setFingerprint(
    self: LockOps.T;
    auth: TEXT;
    pn: NetPath.PN;
    version: PackageObj.Version;
    fp: Fingerprint.T) RAISES {NetObj.Error, PkgErr.E, Thread.Alerted};

PROCEDURE Surrogate_createCheck(self: LockOps.T; pn: NetPath.PN)
     RAISES {NetObj.Error, PkgErr.E, Thread.Alerted};

PROCEDURE Surrogate_createForeign(
    self: LockOps.T;
    auth: TEXT;
    pn: NetPath.PN;
    owningSite: TEXT;
    instance: PackageObj.Int32) RAISES {NetObj.Error, PkgErr.E,
    Thread.Alerted};

PROCEDURE Surrogate_createDir(self: LockOps.T; auth: TEXT; dir: TextList.T)
     RAISES {NetObj.Error, PkgErr.E, Thread.Alerted};

PROCEDURE Surrogate_removeDir(self: LockOps.T; auth: TEXT; dir: TextList.T)
     RAISES {NetObj.Error, PkgErr.E, Thread.Alerted};

PROCEDURE Surrogate_checkDir(self: LockOps.T; dir: TextList.T)
     RAISES {NetObj.Error, PkgErr.E, Thread.Alerted};

PROCEDURE Surrogate_enumerateDirs(self: LockOps.T; site: TEXT)
    : LockOps.DirList RAISES {NetObj.Error, PkgErr.E, Thread.Alerted};

PROCEDURE Surrogate_removeForeign(
    self: LockOps.T;
    auth: TEXT;
    pn: NetPath.PN) RAISES {NetObj.Error, PkgErr.E, LockOps.CommitFailed,
    Thread.Alerted};

PROCEDURE Stub_create(
    self: LockOps.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_remove(
    self: LockOps.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E, LockOps.CommitFailed, LockOps.LockConflict};

PROCEDURE Stub_lock(self: LockOps.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted,
    PkgErr.E, LockOps.LockConflict};

PROCEDURE Stub_unlock(
    self: LockOps.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E, LockOps.CommitFailed, LockOps.LockConflict,
    LockOps.SynchVersions};

PROCEDURE Stub_assign(
    self: LockOps.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E, LockOps.LockConflict};

PROCEDURE Stub_commit(
    self: LockOps.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, LockOps.CommitFailed, PkgErr.E};

PROCEDURE Stub_enumerate(
    self: LockOps.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_getEntry(
    self: LockOps.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_setEntry(
    self: LockOps.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_setFingerprint(
    self: LockOps.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_createCheck(
    self: LockOps.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_createForeign(
    self: LockOps.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_createDir(
    self: LockOps.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_removeDir(
    self: LockOps.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_checkDir(
    self: LockOps.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_enumerateDirs(
    self: LockOps.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E};

PROCEDURE Stub_removeForeign(
    self: LockOps.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, PkgErr.E, LockOps.CommitFailed};

END LockOps_T_v2.
