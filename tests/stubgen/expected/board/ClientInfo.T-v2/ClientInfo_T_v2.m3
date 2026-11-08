MODULE ClientInfo_T_v2 EXPORTS ClientInfo, ClientInfo_T_v2;

IMPORT Rd, Wr, StubLib, Callback, Thread, ClientInfo, RectR, NetObj;
CONST Protocol: StubLib.StubProtocol = 2;

TYPE
      Methods = {getCallback, setScope, getScope};
      ReturnCodes = {OK};

  PROCEDURE Surrogate_getScope(self: ClientInfo.T): RectR.T
       RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: RectR.T;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.getScope));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res.west := StubLib.InReal(c, rep);
            res.east := StubLib.InReal(c, rep);
            res.north := StubLib.InReal(c, rep);
            res.south := StubLib.InReal(c, rep);
            reuse := TRUE;
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
    END Surrogate_getScope;

  PROCEDURE Surrogate_setScope(self: ClientInfo.T; scope_arg: RectR.T)
       RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.setScope));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutReal(c, scope_arg.west);
            StubLib.OutReal(c, scope_arg.east);
            StubLib.OutReal(c, scope_arg.north);
            StubLib.OutReal(c, scope_arg.south);
            END;
            rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
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
    END Surrogate_setScope;

  PROCEDURE Surrogate_getCallback(self: ClientInfo.T): Callback.T
       RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: Callback.T;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.getCallback));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(Callback.T));
            reuse := TRUE;
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
    END Surrogate_getCallback;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, ClientInfo.T);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.getScope) => Stub_getScope(t, c, rep);
      | ORD(Methods.setScope) => Stub_setScope(t, c, rep);
      | ORD(Methods.getCallback) => Stub_getCallback(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    END;
  END Invoke;

PROCEDURE Stub_getScope(
    self: ClientInfo.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR res: RectR.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    res := self.getScope();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutReal(c, res.west);
    StubLib.OutReal(c, res.east);
    StubLib.OutReal(c, res.north);
    StubLib.OutReal(c, res.south);

  END Stub_getScope;

PROCEDURE Stub_setScope(
    self: ClientInfo.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR scope_arg: RectR.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      scope_arg.west := StubLib.InReal(c, rep);
      scope_arg.east := StubLib.InReal(c, rep);
      scope_arg.north := StubLib.InReal(c, rep);
      scope_arg.south := StubLib.InReal(c, rep);
      END;
      self.setScope(scope_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_setScope;

PROCEDURE Stub_getCallback(
    self: ClientInfo.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR res: Callback.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    res := self.getCallback();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_getCallback;

BEGIN
  StubLib.Register(TYPECODE(ClientInfo.T), 2, TYPECODE(Surrogate_ClientInfo_T), Invoke);
END ClientInfo_T_v2.
