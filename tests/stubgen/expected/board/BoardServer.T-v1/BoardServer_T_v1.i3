INTERFACE BoardServer_T_v1;

IMPORT Board, Rd, Wr, StubLib, BoardServer, Thread, NetObj;
TYPE Surrogate_BoardServer_T = BoardServer.T OBJECT
      OVERRIDES
        create := Surrogate_create;
        open := Surrogate_open;
        save := Surrogate_save;
        close := Surrogate_close;
        remove := Surrogate_remove;
      END;

PROCEDURE Surrogate_create(self: BoardServer.T; boardName: TEXT): Board.T
     RAISES {BoardServer.Failed, NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_open(self: BoardServer.T; boardName: TEXT): Board.T
     RAISES {BoardServer.Failed, NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_save(self: BoardServer.T; boardName: TEXT)
     RAISES {BoardServer.Failed, NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_close(self: BoardServer.T; boardName: TEXT)
     RAISES {BoardServer.Failed, NetObj.Error, Thread.Alerted};

PROCEDURE Surrogate_remove(self: BoardServer.T; boardName: TEXT)
     RAISES {BoardServer.Failed, NetObj.Error, Thread.Alerted};

PROCEDURE Stub_create(
    self: BoardServer.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, BoardServer.Failed};

PROCEDURE Stub_open(
    self: BoardServer.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, BoardServer.Failed};

PROCEDURE Stub_save(
    self: BoardServer.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, BoardServer.Failed};

PROCEDURE Stub_close(
    self: BoardServer.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, BoardServer.Failed};

PROCEDURE Stub_remove(
    self: BoardServer.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, BoardServer.Failed};

END BoardServer_T_v1.
