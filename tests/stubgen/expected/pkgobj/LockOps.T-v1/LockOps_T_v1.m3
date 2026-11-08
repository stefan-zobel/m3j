MODULE LockOps_T_v1 EXPORTS LockOps, LockOps_T_v1;

IMPORT LockMethods, Fingerprint, Rd, PackageObj, Wr, StubLib, AtomList,
       PkgErr, Thread, TextList, NetPath, LockOps, NetObj;
CONST Protocol: StubLib.StubProtocol = 1;

TYPE
      Methods = {removeForeign, enumerateDirs, checkDir, removeDir,
        createDir, createForeign, createCheck, setFingerprint, setEntry,
        getEntry, enumerate, commit, assign, unlock, lock, remove, create};
      ReturnCodes = {OK, PkgErr_E, LockOps_CommitFailed,
        LockOps_LockConflict, LockOps_SynchVersions};

  PROCEDURE Surrogate_create(
      self: LockOps.T;
      auth_arg: TEXT;
      pn_arg: NetPath.PN;
      initialKey_arg: TEXT;
      version_arg: PackageObj.Version;
      remoteCheck_arg: BOOLEAN) RAISES {NetObj.Error, PkgErr.E,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.create));
          StubLib.OutRef(c, auth_arg);
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, pn_arg.dir);
            StubLib.OutRef(c, pn_arg.arc);
            END;
            StubLib.OutRef(c, initialKey_arg);
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutInteger(c, version_arg.t);
            StubLib.OutInteger(c, version_arg.vn);
            END;
            StubLib.OutInteger(c, ORD(remoteCheck_arg));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
    END Surrogate_create;

  PROCEDURE Surrogate_remove(
      self: LockOps.T;
      auth_arg: TEXT;
      package_arg: NetPath.PN;
      key_arg: TEXT;
      reship_arg: BOOLEAN) RAISES {NetObj.Error, PkgErr.E, Thread.Alerted,
      LockOps.CommitFailed, LockOps.LockConflict} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.remove));
          StubLib.OutRef(c, auth_arg);
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, package_arg.dir);
            StubLib.OutRef(c, package_arg.arc);
            END;
            StubLib.OutRef(c, key_arg);
          StubLib.OutInteger(c, ORD(reship_arg));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          | ORD(ReturnCodes.LockOps_CommitFailed) =>
            VAR arg: LockOps.CommitFailures;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(LockOps.CommitFailures));
              reuse := TRUE;
              RAISE LockOps.CommitFailed(arg);

            END;
          | ORD(ReturnCodes.LockOps_LockConflict) =>
            VAR arg: LockOps.Owner;
            BEGIN
              arg.key := StubLib.InRef(c, rep, -1);
              arg.site := StubLib.InRef(c, rep, -1);
              reuse := TRUE;
              RAISE LockOps.LockConflict(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
    END Surrogate_remove;

  PROCEDURE Surrogate_lock(
      self: LockOps.T;
      auth_arg: TEXT;
      pn_arg: NetPath.PN;
      version_arg: PackageObj.Version;
      key_arg: TEXT;
      keySite_arg: TEXT): PackageObj.Version RAISES {NetObj.Error,
      PkgErr.E, LockOps.LockConflict, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: PackageObj.Version;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.lock));
          StubLib.OutRef(c, auth_arg);
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, pn_arg.dir);
            StubLib.OutRef(c, pn_arg.arc);
            END;
            dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutInteger(c, version_arg.t);
            StubLib.OutInteger(c, version_arg.vn);
            END;
            StubLib.OutRef(c, key_arg);
          StubLib.OutRef(c, keySite_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res.t := StubLib.InInteger(c, rep, -2147483647, 2147483647);
            res.vn := StubLib.InInteger(c, rep, -2147483647, 2147483647);
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          | ORD(ReturnCodes.LockOps_LockConflict) =>
            VAR arg: LockOps.Owner;
            BEGIN
              arg.key := StubLib.InRef(c, rep, -1);
              arg.site := StubLib.InRef(c, rep, -1);
              reuse := TRUE;
              RAISE LockOps.LockConflict(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
      RETURN res;
    END Surrogate_lock;

  PROCEDURE Surrogate_unlock(
      self: LockOps.T;
      auth_arg: TEXT;
      pn_arg: NetPath.PN;
      version_arg: PackageObj.Version;
      key_arg: TEXT;
      keySite_arg: TEXT;
      breakRights_arg: LockOps.BreakRights;
      forceVersion_arg: BOOLEAN) RAISES {NetObj.Error, PkgErr.E,
      LockOps.CommitFailed, LockOps.LockConflict, LockOps.SynchVersions,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.unlock));
          StubLib.OutRef(c, auth_arg);
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, pn_arg.dir);
            StubLib.OutRef(c, pn_arg.arc);
            END;
            dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutInteger(c, version_arg.t);
            StubLib.OutInteger(c, version_arg.vn);
            END;
            StubLib.OutRef(c, key_arg);
          StubLib.OutRef(c, keySite_arg);
          StubLib.OutInteger(c, ORD(breakRights_arg));
          StubLib.OutInteger(c, ORD(forceVersion_arg));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          | ORD(ReturnCodes.LockOps_CommitFailed) =>
            VAR arg: LockOps.CommitFailures;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(LockOps.CommitFailures));
              reuse := TRUE;
              RAISE LockOps.CommitFailed(arg);

            END;
          | ORD(ReturnCodes.LockOps_LockConflict) =>
            VAR arg: LockOps.Owner;
            BEGIN
              arg.key := StubLib.InRef(c, rep, -1);
              arg.site := StubLib.InRef(c, rep, -1);
              reuse := TRUE;
              RAISE LockOps.LockConflict(arg);

            END;
          | ORD(ReturnCodes.LockOps_SynchVersions) =>
            VAR arg: PackageObj.Version;
            BEGIN
              arg.t := StubLib.InInteger(c, rep, -2147483647, 2147483647);
              arg.vn := StubLib.InInteger(c, rep, -2147483647, 2147483647);
              reuse := TRUE;
              RAISE LockOps.SynchVersions(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
    END Surrogate_unlock;

  PROCEDURE Surrogate_assign(
      self: LockOps.T;
      auth_arg: TEXT;
      pn_arg: NetPath.PN;
      key_arg: TEXT;
      keySite_arg: TEXT): PackageObj.Version RAISES {NetObj.Error,
      PkgErr.E, LockOps.LockConflict, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: PackageObj.Version;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.assign));
          StubLib.OutRef(c, auth_arg);
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, pn_arg.dir);
            StubLib.OutRef(c, pn_arg.arc);
            END;
            StubLib.OutRef(c, key_arg);
          StubLib.OutRef(c, keySite_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res.t := StubLib.InInteger(c, rep, -2147483647, 2147483647);
            res.vn := StubLib.InInteger(c, rep, -2147483647, 2147483647);
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          | ORD(ReturnCodes.LockOps_LockConflict) =>
            VAR arg: LockOps.Owner;
            BEGIN
              arg.key := StubLib.InRef(c, rep, -1);
              arg.site := StubLib.InRef(c, rep, -1);
              reuse := TRUE;
              RAISE LockOps.LockConflict(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
      RETURN res;
    END Surrogate_assign;

  PROCEDURE Surrogate_commit(
      self: LockOps.T;
      auth_arg: TEXT;
      pn_arg: NetPath.PN;
      version_arg: PackageObj.Version;
      VAR ships_arg: LockOps.ShipArray;
      reship_arg: BOOLEAN): LockOps.CommitFailures RAISES {NetObj.Error,
      LockOps.CommitFailed, PkgErr.E, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: LockOps.CommitFailures;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.commit));
          StubLib.OutRef(c, auth_arg);
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, pn_arg.dir);
            StubLib.OutRef(c, pn_arg.arc);
            END;
            dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutInteger(c, version_arg.t);
            StubLib.OutInteger(c, version_arg.vn);
            END;
            StubLib.OutInteger(c, NUMBER(ships_arg));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            FOR n1 := 0 TO LAST(ships_arg) DO
              StubLib.OutRef(c, ships_arg[n1]);
              END;
            END;
            StubLib.OutInteger(c, ORD(reship_arg));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            FOR n1 := 0 TO LAST(ships_arg) DO
              ships_arg[n1] := StubLib.InRef(c, rep, TYPECODE(PackageObj.Ship));
              END;
            res := StubLib.InRef(c, rep, TYPECODE(LockOps.CommitFailures));
            reuse := TRUE;
          | ORD(ReturnCodes.LockOps_CommitFailed) =>
            VAR arg: LockOps.CommitFailures;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(LockOps.CommitFailures));
              reuse := TRUE;
              RAISE LockOps.CommitFailed(arg);

            END;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
      RETURN res;
    END Surrogate_commit;

  PROCEDURE Surrogate_enumerate(
      self: LockOps.T;
      dir_arg: TextList.T;
      site_arg: TEXT;
      locksOnly_arg: BOOLEAN;
      localOnly_arg: BOOLEAN;
      pendingOnly_arg: BOOLEAN): LockOps.EnumList RAISES {NetObj.Error,
      PkgErr.E, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: LockOps.EnumList;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.enumerate));
          StubLib.OutRef(c, dir_arg);
          StubLib.OutRef(c, site_arg);
          StubLib.OutInteger(c, ORD(locksOnly_arg));
          StubLib.OutInteger(c, ORD(localOnly_arg));
          StubLib.OutInteger(c, ORD(pendingOnly_arg));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(LockOps.EnumList));
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
      RETURN res;
    END Surrogate_enumerate;

  PROCEDURE Surrogate_getEntry(
      self: LockOps.T;
      pn_arg: NetPath.PN;
      goRemote_arg: BOOLEAN): LockOps.RefEntry RAISES {NetObj.Error,
      PkgErr.E, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: LockOps.RefEntry;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.getEntry));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, pn_arg.dir);
            StubLib.OutRef(c, pn_arg.arc);
            END;
            StubLib.OutInteger(c, ORD(goRemote_arg));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(LockOps.RefEntry));
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
      RETURN res;
    END Surrogate_getEntry;

  PROCEDURE Surrogate_setEntry(
      self: LockOps.T;
      auth_arg: TEXT;
      pn_arg: NetPath.PN;
      entry_arg: LockOps.RefEntry) RAISES {NetObj.Error, PkgErr.E,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.setEntry));
          StubLib.OutRef(c, auth_arg);
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, pn_arg.dir);
            StubLib.OutRef(c, pn_arg.arc);
            END;
            StubLib.OutRef(c, entry_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
    END Surrogate_setEntry;

  PROCEDURE Surrogate_setFingerprint(
      self: LockOps.T;
      auth_arg: TEXT;
      pn_arg: NetPath.PN;
      version_arg: PackageObj.Version;
      fp_arg: Fingerprint.T) RAISES {NetObj.Error, PkgErr.E,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.setFingerprint));
          StubLib.OutRef(c, auth_arg);
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, pn_arg.dir);
            StubLib.OutRef(c, pn_arg.arc);
            END;
            dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutInteger(c, version_arg.t);
            StubLib.OutInteger(c, version_arg.vn);
            END;
            dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            FOR i0 := FIRST([0..7]) TO LAST([0..7]) DO
              StubLib.OutInteger(c, fp_arg.byte[i0]);
              END;
            END;
            rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
    END Surrogate_setFingerprint;

  PROCEDURE Surrogate_createCheck(self: LockOps.T; pn_arg: NetPath.PN)
       RAISES {NetObj.Error, PkgErr.E, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.createCheck));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, pn_arg.dir);
            StubLib.OutRef(c, pn_arg.arc);
            END;
            rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
    END Surrogate_createCheck;

  PROCEDURE Surrogate_createForeign(
      self: LockOps.T;
      auth_arg: TEXT;
      pn_arg: NetPath.PN;
      owningSite_arg: TEXT;
      instance_arg: PackageObj.Int32) RAISES {NetObj.Error, PkgErr.E,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.createForeign));
          StubLib.OutRef(c, auth_arg);
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, pn_arg.dir);
            StubLib.OutRef(c, pn_arg.arc);
            END;
            StubLib.OutRef(c, owningSite_arg);
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutInteger(c, instance_arg);
            END;
            rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
    END Surrogate_createForeign;

  PROCEDURE Surrogate_createDir(
      self: LockOps.T;
      auth_arg: TEXT;
      dir_arg: TextList.T) RAISES {NetObj.Error, PkgErr.E, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.createDir));
          StubLib.OutRef(c, auth_arg);
          StubLib.OutRef(c, dir_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
    END Surrogate_createDir;

  PROCEDURE Surrogate_removeDir(
      self: LockOps.T;
      auth_arg: TEXT;
      dir_arg: TextList.T) RAISES {NetObj.Error, PkgErr.E, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.removeDir));
          StubLib.OutRef(c, auth_arg);
          StubLib.OutRef(c, dir_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
    END Surrogate_removeDir;

  PROCEDURE Surrogate_checkDir(self: LockOps.T; dir_arg: TextList.T)
       RAISES {NetObj.Error, PkgErr.E, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.checkDir));
          StubLib.OutRef(c, dir_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
    END Surrogate_checkDir;

  PROCEDURE Surrogate_enumerateDirs(self: LockOps.T; site_arg: TEXT)
      : LockOps.DirList RAISES {NetObj.Error, PkgErr.E, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: LockOps.DirList;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.enumerateDirs));
          StubLib.OutRef(c, site_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(LockOps.DirList));
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
      RETURN res;
    END Surrogate_enumerateDirs;

  PROCEDURE Surrogate_removeForeign(
      self: LockOps.T;
      auth_arg: TEXT;
      pn_arg: NetPath.PN) RAISES {NetObj.Error, PkgErr.E,
      LockOps.CommitFailed, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.removeForeign));
          StubLib.OutRef(c, auth_arg);
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutRef(c, pn_arg.dir);
            StubLib.OutRef(c, pn_arg.arc);
            END;
            rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.PkgErr_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE PkgErr.E(arg);

            END;
          | ORD(ReturnCodes.LockOps_CommitFailed) =>
            VAR arg: LockOps.CommitFailures;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(LockOps.CommitFailures));
              reuse := TRUE;
              RAISE LockOps.CommitFailed(arg);

            END;
          ELSE
            StubLib.RaiseUnmarshalFailure();
          END
        FINALLY
          StubLib.EndCall(c, reuse);
        END;

      EXCEPT
      | Rd.Failure(ec) => StubLib.RaiseCommFailure(ec);
      | Wr.Failure(ec) => StubLib.RaiseCommFailure(ec);
      END;
    END Surrogate_removeForeign;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, LockOps.T);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.create) => Stub_create(t, c, rep);
      | ORD(Methods.remove) => Stub_remove(t, c, rep);
      | ORD(Methods.lock) => Stub_lock(t, c, rep);
      | ORD(Methods.unlock) => Stub_unlock(t, c, rep);
      | ORD(Methods.assign) => Stub_assign(t, c, rep);
      | ORD(Methods.commit) => Stub_commit(t, c, rep);
      | ORD(Methods.enumerate) => Stub_enumerate(t, c, rep);
      | ORD(Methods.getEntry) => Stub_getEntry(t, c, rep);
      | ORD(Methods.setEntry) => Stub_setEntry(t, c, rep);
      | ORD(Methods.setFingerprint) => Stub_setFingerprint(t, c, rep);
      | ORD(Methods.createCheck) => Stub_createCheck(t, c, rep);
      | ORD(Methods.createForeign) => Stub_createForeign(t, c, rep);
      | ORD(Methods.createDir) => Stub_createDir(t, c, rep);
      | ORD(Methods.removeDir) => Stub_removeDir(t, c, rep);
      | ORD(Methods.checkDir) => Stub_checkDir(t, c, rep);
      | ORD(Methods.enumerateDirs) => Stub_enumerateDirs(t, c, rep);
      | ORD(Methods.removeForeign) => Stub_removeForeign(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    | LockOps.SynchVersions(arg) =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.LockOps_SynchVersions));
        StubLib.OutInteger(c, arg.t);
StubLib.OutInteger(c, arg.vn);

    | LockOps.LockConflict(arg) =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.LockOps_LockConflict));
        StubLib.OutRef(c, arg.key);
StubLib.OutRef(c, arg.site);

    | LockOps.CommitFailed(arg) =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.LockOps_CommitFailed));
        StubLib.OutRef(c, arg);

    | PkgErr.E(arg) =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.PkgErr_E));
        StubLib.OutRef(c, arg);
    END;
  END Invoke;

PROCEDURE Stub_create(
    self: LockOps.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR auth_arg: TEXT;
      pn_arg: NetPath.PN;
      initialKey_arg: TEXT;
      version_arg: PackageObj.Version;
      remoteCheck_arg: BOOLEAN;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    auth_arg := StubLib.InRef(c, rep, -1);
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      pn_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      pn_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      initialKey_arg := StubLib.InRef(c, rep, -1);
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      version_arg.t := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      version_arg.vn := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      END;
      remoteCheck_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    self.create(auth_arg, pn_arg, initialKey_arg, version_arg, remoteCheck_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_create;

PROCEDURE Stub_remove(
    self: LockOps.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E, LockOps.CommitFailed,
    LockOps.LockConflict}=
  VAR auth_arg: TEXT;
      package_arg: NetPath.PN;
      key_arg: TEXT;
      reship_arg: BOOLEAN;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    auth_arg := StubLib.InRef(c, rep, -1);
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      package_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      package_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      key_arg := StubLib.InRef(c, rep, -1);
    reship_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    self.remove(auth_arg, package_arg, key_arg, reship_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_remove;

PROCEDURE Stub_lock(
    self: LockOps.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E, LockOps.LockConflict}=
  VAR auth_arg: TEXT;
      pn_arg: NetPath.PN;
      version_arg: PackageObj.Version;
      key_arg: TEXT;
      keySite_arg: TEXT;
      res: PackageObj.Version;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    auth_arg := StubLib.InRef(c, rep, -1);
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      pn_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      pn_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      version_arg.t := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      version_arg.vn := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      END;
      key_arg := StubLib.InRef(c, rep, -1);
    keySite_arg := StubLib.InRef(c, rep, -1);
    res := self.lock(auth_arg, pn_arg, version_arg, key_arg, keySite_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutInteger(c, res.t);
    StubLib.OutInteger(c, res.vn);

  END Stub_lock;

PROCEDURE Stub_unlock(
    self: LockOps.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E, LockOps.CommitFailed,
    LockOps.LockConflict, LockOps.SynchVersions}=
  VAR auth_arg: TEXT;
      pn_arg: NetPath.PN;
      version_arg: PackageObj.Version;
      key_arg: TEXT;
      keySite_arg: TEXT;
      breakRights_arg: LockOps.BreakRights;
      forceVersion_arg: BOOLEAN;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    auth_arg := StubLib.InRef(c, rep, -1);
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      pn_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      pn_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      version_arg.t := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      version_arg.vn := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      END;
      key_arg := StubLib.InRef(c, rep, -1);
    keySite_arg := StubLib.InRef(c, rep, -1);
    breakRights_arg := VAL(StubLib.InInteger(c, rep, 0,2), LockOps.BreakRights);
    forceVersion_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    self.unlock(auth_arg, pn_arg, version_arg, key_arg, keySite_arg, breakRights_arg, forceVersion_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_unlock;

PROCEDURE Stub_assign(
    self: LockOps.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E, LockOps.LockConflict}=
  VAR auth_arg: TEXT;
      pn_arg: NetPath.PN;
      key_arg: TEXT;
      keySite_arg: TEXT;
      res: PackageObj.Version;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    auth_arg := StubLib.InRef(c, rep, -1);
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      pn_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      pn_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      key_arg := StubLib.InRef(c, rep, -1);
    keySite_arg := StubLib.InRef(c, rep, -1);
    res := self.assign(auth_arg, pn_arg, key_arg, keySite_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutInteger(c, res.t);
    StubLib.OutInteger(c, res.vn);

  END Stub_assign;

PROCEDURE Stub_commit(
    self: LockOps.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, LockOps.CommitFailed, PkgErr.E}=
  VAR auth_arg: TEXT;
      pn_arg: NetPath.PN;
      version_arg: PackageObj.Version;
      ships_arg: REF LockOps.ShipArray;
      reship_arg: BOOLEAN;
      res: LockOps.CommitFailures;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    auth_arg := StubLib.InRef(c, rep, -1);
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      pn_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      pn_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      version_arg.t := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      version_arg.vn := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      END;
      WITH n1 = StubLib.InInteger(c, rep) DO
      ships_arg := NEW(REF LockOps.ShipArray, n1);
    END;
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      FOR n1 := 0 TO LAST(ships_arg^) DO
        ships_arg[n1] := StubLib.InRef(c, rep, TYPECODE(PackageObj.Ship));
        END;
      END;
      reship_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    res := self.commit(auth_arg, pn_arg, version_arg, ships_arg^, reship_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    FOR n1 := 0 TO LAST(ships_arg^) DO
      StubLib.OutRef(c, ships_arg[n1]);
      END;
    StubLib.OutRef(c, res);

  END Stub_commit;

PROCEDURE Stub_enumerate(
    self: LockOps.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR dir_arg: TextList.T;
      site_arg: TEXT;
      locksOnly_arg: BOOLEAN;
      localOnly_arg: BOOLEAN;
      pendingOnly_arg: BOOLEAN;
      res: LockOps.EnumList;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    dir_arg := StubLib.InRef(c, rep, TYPECODE(TextList.T));
    site_arg := StubLib.InRef(c, rep, -1);
    locksOnly_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    localOnly_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    pendingOnly_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    res := self.enumerate(dir_arg, site_arg, locksOnly_arg, localOnly_arg, pendingOnly_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_enumerate;

PROCEDURE Stub_getEntry(
    self: LockOps.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR pn_arg: NetPath.PN;
      goRemote_arg: BOOLEAN;
      res: LockOps.RefEntry;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      pn_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      pn_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      goRemote_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    res := self.getEntry(pn_arg, goRemote_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_getEntry;

PROCEDURE Stub_setEntry(
    self: LockOps.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR auth_arg: TEXT;
      pn_arg: NetPath.PN;
      entry_arg: LockOps.RefEntry;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    auth_arg := StubLib.InRef(c, rep, -1);
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      pn_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      pn_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      entry_arg := StubLib.InRef(c, rep, TYPECODE(LockOps.RefEntry));
    self.setEntry(auth_arg, pn_arg, entry_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_setEntry;

PROCEDURE Stub_setFingerprint(
    self: LockOps.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR auth_arg: TEXT;
      pn_arg: NetPath.PN;
      version_arg: PackageObj.Version;
      fp_arg: Fingerprint.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    auth_arg := StubLib.InRef(c, rep, -1);
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      pn_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      pn_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      version_arg.t := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      version_arg.vn := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      END;
      dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      FOR i0 := FIRST([0..7]) TO LAST([0..7]) DO
        fp_arg.byte[i0] := StubLib.InInteger(c, rep, 0, 255);
        END;
      END;
      self.setFingerprint(auth_arg, pn_arg, version_arg, fp_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_setFingerprint;

PROCEDURE Stub_createCheck(
    self: LockOps.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR pn_arg: NetPath.PN;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      pn_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      pn_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      self.createCheck(pn_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_createCheck;

PROCEDURE Stub_createForeign(
    self: LockOps.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR auth_arg: TEXT;
      pn_arg: NetPath.PN;
      owningSite_arg: TEXT;
      instance_arg: PackageObj.Int32;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    auth_arg := StubLib.InRef(c, rep, -1);
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      pn_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      pn_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      owningSite_arg := StubLib.InRef(c, rep, -1);
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      instance_arg := StubLib.InInteger(c, rep, -2147483647, 2147483647);
      END;
      self.createForeign(auth_arg, pn_arg, owningSite_arg, instance_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_createForeign;

PROCEDURE Stub_createDir(
    self: LockOps.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR auth_arg: TEXT;
      dir_arg: TextList.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    auth_arg := StubLib.InRef(c, rep, -1);
    dir_arg := StubLib.InRef(c, rep, TYPECODE(TextList.T));
    self.createDir(auth_arg, dir_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_createDir;

PROCEDURE Stub_removeDir(
    self: LockOps.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR auth_arg: TEXT;
      dir_arg: TextList.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    auth_arg := StubLib.InRef(c, rep, -1);
    dir_arg := StubLib.InRef(c, rep, TYPECODE(TextList.T));
    self.removeDir(auth_arg, dir_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_removeDir;

PROCEDURE Stub_checkDir(
    self: LockOps.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR dir_arg: TextList.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    dir_arg := StubLib.InRef(c, rep, TYPECODE(TextList.T));
    self.checkDir(dir_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_checkDir;

PROCEDURE Stub_enumerateDirs(
    self: LockOps.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E}=
  VAR site_arg: TEXT;
      res: LockOps.DirList;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    site_arg := StubLib.InRef(c, rep, -1);
    res := self.enumerateDirs(site_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_enumerateDirs;

PROCEDURE Stub_removeForeign(
    self: LockOps.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, PkgErr.E, LockOps.CommitFailed}=
  VAR auth_arg: TEXT;
      pn_arg: NetPath.PN;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    auth_arg := StubLib.InRef(c, rep, -1);
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      pn_arg.dir := StubLib.InRef(c, rep, TYPECODE(TextList.T));
      pn_arg.arc := StubLib.InRef(c, rep, -1);
      END;
      self.removeForeign(auth_arg, pn_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_removeForeign;

BEGIN
  StubLib.Register(TYPECODE(LockOps.T), 1, TYPECODE(Surrogate_LockOps_T), Invoke);
END LockOps_T_v1.
