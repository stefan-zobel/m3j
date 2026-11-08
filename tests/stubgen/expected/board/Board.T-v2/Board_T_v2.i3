INTERFACE Board_T_v2;

IMPORT Item, Board, Rd, Wr, StubLib, Callback, Thread, ClientInfo, RectR,
       NetObj;
TYPE Surrogate_Board_T = Board.T OBJECT
      OVERRIDES
        register := Surrogate_register;
        setScope := Surrogate_setScope;
        createItems := Surrogate_createItems;
        modifyItems := Surrogate_modifyItems;
        deleteItems := Surrogate_deleteItems;
        unregister := Surrogate_unregister;
      END;

PROCEDURE Surrogate_register(self: Board.T; cb: Callback.T): ClientInfo.T
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_setScope(
    self: Board.T;
    ci: ClientInfo.T;
    scope: RectR.T) RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_createItems(
    self: Board.T;
    ci: ClientInfo.T;
    its: Item.TArray): Item.IDArray RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_modifyItems(
    self: Board.T;
    ci: ClientInfo.T;
    its: Item.TArray;
    additive: BOOLEAN) RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_deleteItems(
    self: Board.T;
    ci: ClientInfo.T;
    ids: Item.IDArray) RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_unregister(self: Board.T; ci: ClientInfo.T)
     RAISES {NetObj.Error, Thread.Alerted};

PROCEDURE Stub_register(
    self: Board.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_setScope(
    self: Board.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_createItems(
    self: Board.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_modifyItems(
    self: Board.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_deleteItems(
    self: Board.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

PROCEDURE Stub_unregister(
    self: Board.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

END Board_T_v2.
