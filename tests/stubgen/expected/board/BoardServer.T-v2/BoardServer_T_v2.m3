MODULE BoardServer_T_v2 EXPORTS BoardServer, BoardServer_T_v2;

IMPORT Board, Rd, Wr, StubLib, BoardServer, Thread, NetObj;
CONST Protocol: StubLib.StubProtocol = 2;

TYPE
      Methods = {remove, close, save, open, create};
      ReturnCodes = {OK, BoardServer_Failed};

  PROCEDURE Surrogate_create(self: BoardServer.T; boardName_arg: TEXT)
      : Board.T RAISES {BoardServer.Failed, NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: Board.T;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.create));
          StubLib.OutRef(c, boardName_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(Board.T));
            reuse := TRUE;
          | ORD(ReturnCodes.BoardServer_Failed) =>
            VAR arg: TEXT;
            BEGIN
              arg := StubLib.InRef(c, rep, -1);
              reuse := TRUE;
              RAISE BoardServer.Failed(arg);

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
    END Surrogate_create;

  PROCEDURE Surrogate_open(self: BoardServer.T; boardName_arg: TEXT)
      : Board.T RAISES {BoardServer.Failed, NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: Board.T;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.open));
          StubLib.OutRef(c, boardName_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(Board.T));
            reuse := TRUE;
          | ORD(ReturnCodes.BoardServer_Failed) =>
            VAR arg: TEXT;
            BEGIN
              arg := StubLib.InRef(c, rep, -1);
              reuse := TRUE;
              RAISE BoardServer.Failed(arg);

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
    END Surrogate_open;

  PROCEDURE Surrogate_save(self: BoardServer.T; boardName_arg: TEXT)
       RAISES {BoardServer.Failed, NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.save));
          StubLib.OutRef(c, boardName_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.BoardServer_Failed) =>
            VAR arg: TEXT;
            BEGIN
              arg := StubLib.InRef(c, rep, -1);
              reuse := TRUE;
              RAISE BoardServer.Failed(arg);

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
    END Surrogate_save;

  PROCEDURE Surrogate_close(self: BoardServer.T; boardName_arg: TEXT)
       RAISES {BoardServer.Failed, NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.close));
          StubLib.OutRef(c, boardName_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.BoardServer_Failed) =>
            VAR arg: TEXT;
            BEGIN
              arg := StubLib.InRef(c, rep, -1);
              reuse := TRUE;
              RAISE BoardServer.Failed(arg);

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
    END Surrogate_close;

  PROCEDURE Surrogate_remove(self: BoardServer.T; boardName_arg: TEXT)
       RAISES {BoardServer.Failed, NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.remove));
          StubLib.OutRef(c, boardName_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.BoardServer_Failed) =>
            VAR arg: TEXT;
            BEGIN
              arg := StubLib.InRef(c, rep, -1);
              reuse := TRUE;
              RAISE BoardServer.Failed(arg);

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

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, BoardServer.T);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.create) => Stub_create(t, c, rep);
      | ORD(Methods.open) => Stub_open(t, c, rep);
      | ORD(Methods.save) => Stub_save(t, c, rep);
      | ORD(Methods.close) => Stub_close(t, c, rep);
      | ORD(Methods.remove) => Stub_remove(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    | BoardServer.Failed(arg) =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.BoardServer_Failed));
        StubLib.OutRef(c, arg);
    END;
  END Invoke;

PROCEDURE Stub_create(
    self: BoardServer.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, BoardServer.Failed}=
  VAR boardName_arg: TEXT;
      res: Board.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    boardName_arg := StubLib.InRef(c, rep, -1);
    res := self.create(boardName_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_create;

PROCEDURE Stub_open(
    self: BoardServer.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, BoardServer.Failed}=
  VAR boardName_arg: TEXT;
      res: Board.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    boardName_arg := StubLib.InRef(c, rep, -1);
    res := self.open(boardName_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_open;

PROCEDURE Stub_save(
    self: BoardServer.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, BoardServer.Failed}=
  VAR boardName_arg: TEXT;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    boardName_arg := StubLib.InRef(c, rep, -1);
    self.save(boardName_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_save;

PROCEDURE Stub_close(
    self: BoardServer.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, BoardServer.Failed}=
  VAR boardName_arg: TEXT;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    boardName_arg := StubLib.InRef(c, rep, -1);
    self.close(boardName_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_close;

PROCEDURE Stub_remove(
    self: BoardServer.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, BoardServer.Failed}=
  VAR boardName_arg: TEXT;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    boardName_arg := StubLib.InRef(c, rep, -1);
    self.remove(boardName_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_remove;

BEGIN
  StubLib.Register(TYPECODE(BoardServer.T), 2, TYPECODE(Surrogate_BoardServer_T), Invoke);
END BoardServer_T_v2.
