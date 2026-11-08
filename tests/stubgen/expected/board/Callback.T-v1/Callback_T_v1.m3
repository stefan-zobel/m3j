MODULE Callback_T_v1 EXPORTS Callback, Callback_T_v1;

IMPORT Item, Rd, Wr, StubLib, Callback, Thread, NetObj;
CONST Protocol: StubLib.StubProtocol = 1;

TYPE
      Methods = {itemsDeleted, itemsModified, itemsCreated};
      ReturnCodes = {OK};

  PROCEDURE Surrogate_itemsCreated(self: Callback.T; it_arg: Item.TArray)
       RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.itemsCreated));
          StubLib.OutRef(c, it_arg);
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
    END Surrogate_itemsCreated;

  PROCEDURE Surrogate_itemsModified(
      self: Callback.T;
      it_arg: Item.TArray;
      additive_arg: BOOLEAN) RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.itemsModified));
          StubLib.OutRef(c, it_arg);
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
    END Surrogate_itemsModified;

  PROCEDURE Surrogate_itemsDeleted(self: Callback.T; id_arg: Item.IDArray)
       RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.itemsDeleted));
          StubLib.OutRef(c, id_arg);
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
    END Surrogate_itemsDeleted;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, Callback.T);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.itemsCreated) => Stub_itemsCreated(t, c, rep);
      | ORD(Methods.itemsModified) => Stub_itemsModified(t, c, rep);
      | ORD(Methods.itemsDeleted) => Stub_itemsDeleted(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    END;
  END Invoke;

PROCEDURE Stub_itemsCreated(
    self: Callback.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR it_arg: Item.TArray;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    it_arg := StubLib.InRef(c, rep, TYPECODE(Item.TArray));
    self.itemsCreated(it_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_itemsCreated;

PROCEDURE Stub_itemsModified(
    self: Callback.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR it_arg: Item.TArray;
      additive_arg: BOOLEAN;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    it_arg := StubLib.InRef(c, rep, TYPECODE(Item.TArray));
    additive_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    self.itemsModified(it_arg, additive_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_itemsModified;

PROCEDURE Stub_itemsDeleted(
    self: Callback.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR id_arg: Item.IDArray;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    id_arg := StubLib.InRef(c, rep, TYPECODE(Item.IDArray));
    self.itemsDeleted(id_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_itemsDeleted;

BEGIN
  StubLib.Register(TYPECODE(Callback.T), 1, TYPECODE(Surrogate_Callback_T), Invoke);
END Callback_T_v1.
