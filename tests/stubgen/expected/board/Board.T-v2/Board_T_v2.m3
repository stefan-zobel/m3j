MODULE Board_T_v2 EXPORTS Board, Board_T_v2;

IMPORT Item, Board, Rd, Wr, StubLib, Callback, Thread, ClientInfo, RectR,
       NetObj;
CONST Protocol: StubLib.StubProtocol = 2;

TYPE
      Methods = {unregister, deleteItems, modifyItems, createItems,
        setScope, register};
      ReturnCodes = {OK};

  PROCEDURE Surrogate_register(self: Board.T; cb_arg: Callback.T)
      : ClientInfo.T RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: ClientInfo.T;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.register));
          StubLib.OutRef(c, cb_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(ClientInfo.T));
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
    END Surrogate_register;

  PROCEDURE Surrogate_setScope(
      self: Board.T;
      ci_arg: ClientInfo.T;
      scope_arg: RectR.T) RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.setScope));
          StubLib.OutRef(c, ci_arg);
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

  PROCEDURE Surrogate_createItems(
      self: Board.T;
      ci_arg: ClientInfo.T;
      its_arg: Item.TArray): Item.IDArray RAISES {NetObj.Error,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: Item.IDArray;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.createItems));
          StubLib.OutRef(c, ci_arg);
          StubLib.OutRef(c, its_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(Item.IDArray));
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
    END Surrogate_createItems;

  PROCEDURE Surrogate_modifyItems(
      self: Board.T;
      ci_arg: ClientInfo.T;
      its_arg: Item.TArray;
      additive_arg: BOOLEAN) RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.modifyItems));
          StubLib.OutRef(c, ci_arg);
          StubLib.OutRef(c, its_arg);
          StubLib.OutInteger(c, ORD(additive_arg));
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
    END Surrogate_modifyItems;

  PROCEDURE Surrogate_deleteItems(
      self: Board.T;
      ci_arg: ClientInfo.T;
      ids_arg: Item.IDArray) RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.deleteItems));
          StubLib.OutRef(c, ci_arg);
          StubLib.OutRef(c, ids_arg);
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
    END Surrogate_deleteItems;

  PROCEDURE Surrogate_unregister(self: Board.T; ci_arg: ClientInfo.T)
       RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.unregister));
          StubLib.OutRef(c, ci_arg);
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
    END Surrogate_unregister;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, Board.T);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.register) => Stub_register(t, c, rep);
      | ORD(Methods.setScope) => Stub_setScope(t, c, rep);
      | ORD(Methods.createItems) => Stub_createItems(t, c, rep);
      | ORD(Methods.modifyItems) => Stub_modifyItems(t, c, rep);
      | ORD(Methods.deleteItems) => Stub_deleteItems(t, c, rep);
      | ORD(Methods.unregister) => Stub_unregister(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    END;
  END Invoke;

PROCEDURE Stub_register(
    self: Board.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR cb_arg: Callback.T;
      res: ClientInfo.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    cb_arg := StubLib.InRef(c, rep, TYPECODE(Callback.T));
    res := self.register(cb_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_register;

PROCEDURE Stub_setScope(
    self: Board.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR ci_arg: ClientInfo.T;
      scope_arg: RectR.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    ci_arg := StubLib.InRef(c, rep, TYPECODE(ClientInfo.T));
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      scope_arg.west := StubLib.InReal(c, rep);
      scope_arg.east := StubLib.InReal(c, rep);
      scope_arg.north := StubLib.InReal(c, rep);
      scope_arg.south := StubLib.InReal(c, rep);
      END;
      self.setScope(ci_arg, scope_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_setScope;

PROCEDURE Stub_createItems(
    self: Board.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR ci_arg: ClientInfo.T;
      its_arg: Item.TArray;
      res: Item.IDArray;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    ci_arg := StubLib.InRef(c, rep, TYPECODE(ClientInfo.T));
    its_arg := StubLib.InRef(c, rep, TYPECODE(Item.TArray));
    res := self.createItems(ci_arg, its_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_createItems;

PROCEDURE Stub_modifyItems(
    self: Board.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR ci_arg: ClientInfo.T;
      its_arg: Item.TArray;
      additive_arg: BOOLEAN;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    ci_arg := StubLib.InRef(c, rep, TYPECODE(ClientInfo.T));
    its_arg := StubLib.InRef(c, rep, TYPECODE(Item.TArray));
    additive_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    self.modifyItems(ci_arg, its_arg, additive_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_modifyItems;

PROCEDURE Stub_deleteItems(
    self: Board.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR ci_arg: ClientInfo.T;
      ids_arg: Item.IDArray;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    ci_arg := StubLib.InRef(c, rep, TYPECODE(ClientInfo.T));
    ids_arg := StubLib.InRef(c, rep, TYPECODE(Item.IDArray));
    self.deleteItems(ci_arg, ids_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_deleteItems;

PROCEDURE Stub_unregister(
    self: Board.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR ci_arg: ClientInfo.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    ci_arg := StubLib.InRef(c, rep, TYPECODE(ClientInfo.T));
    self.unregister(ci_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_unregister;

BEGIN
  StubLib.Register(TYPECODE(Board.T), 2, TYPECODE(Surrogate_Board_T), Invoke);
END Board_T_v2.
