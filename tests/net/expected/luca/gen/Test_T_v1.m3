MODULE Test_T_v1 EXPORTS Test, Test_T_v1;

IMPORT Test, Rd, OSError, Wr, StubLib, AtomList, Thread, NetObj;
CONST Protocol: StubLib.StubProtocol = 1;

TYPE
      Methods = {getRdEmbedded, getRd};
      ReturnCodes = {OK, OSError_E};

  PROCEDURE Surrogate_getRd(self: Test.T; path_arg: TEXT): Rd.T
       RAISES {NetObj.Error, OSError.E} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: Rd.T;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.getRd));
          StubLib.OutRef(c, path_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(Rd.T));
            reuse := TRUE;
          | ORD(ReturnCodes.OSError_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE OSError.E(arg);

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
    END Surrogate_getRd;

  PROCEDURE Surrogate_getRdEmbedded(self: Test.T; path_arg: TEXT)
      : Test.RdPkl RAISES {NetObj.Error, OSError.E} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: Test.RdPkl;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.getRdEmbedded));
          StubLib.OutRef(c, path_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(Test.RdPkl));
            reuse := TRUE;
          | ORD(ReturnCodes.OSError_E) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE OSError.E(arg);

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
    END Surrogate_getRdEmbedded;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, Test.T);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.getRd) => Stub_getRd(t, c, rep);
      | ORD(Methods.getRdEmbedded) => Stub_getRdEmbedded(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    | OSError.E(arg) =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.OSError_E));
        StubLib.OutRef(c, arg);
    END;
  END Invoke;

PROCEDURE Stub_getRd(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, OSError.E}=
  VAR path_arg: TEXT;
      res: Rd.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    path_arg := StubLib.InRef(c, rep, -1);
    res := self.getRd(path_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_getRd;

PROCEDURE Stub_getRdEmbedded(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, OSError.E}=
  VAR path_arg: TEXT;
      res: Test.RdPkl;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    path_arg := StubLib.InRef(c, rep, -1);
    res := self.getRdEmbedded(path_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_getRdEmbedded;

BEGIN
  StubLib.Register(TYPECODE(Test.T), 1, TYPECODE(Surrogate_Test_T), Invoke);
END Test_T_v1.
