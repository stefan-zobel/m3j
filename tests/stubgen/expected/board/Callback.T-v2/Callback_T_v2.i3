INTERFACE Callback_T_v2;

IMPORT Item, Rd, Wr, StubLib, Callback, Thread, NetObj;
TYPE Surrogate_Callback_T = Callback.T OBJECT
      OVERRIDES
        itemsCreated := Surrogate_itemsCreated;
        itemsModified := Surrogate_itemsModified;
        itemsDeleted := Surrogate_itemsDeleted;
      END;

PROCEDURE Surrogate_itemsCreated(self: Callback.T; it: Item.TArray)
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_itemsModified(
    self: Callback.T;
    it: Item.TArray;
    additive: BOOLEAN) RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_itemsDeleted(self: Callback.T; id: Item.IDArray)
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Stub_itemsCreated(
    self: Callback.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_itemsModified(
    self: Callback.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_itemsDeleted(
    self: Callback.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

END Callback_T_v2.
