MODULE ObValue_RemFileSystem_v2 EXPORTS ObValue, ObValue_RemFileSystem_v2;

IMPORT Rd, Wr, StubLib, ObValue, Thread, NetObj;
CONST Protocol: StubLib.StubProtocol = 2;

TYPE
      Methods = {OpenAppend, OpenWrite, OpenRead};
      ReturnCodes = {OK, ObValue_ServerError};

  PROCEDURE Surrogate_OpenRead(
      self: ObValue.RemFileSystem;
      fileName_arg: TEXT): Rd.T RAISES {NetObj.Error, ObValue.ServerError,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: Rd.T;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.OpenRead));
          StubLib.OutRef(c, fileName_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(Rd.T));
            reuse := TRUE;
          | ORD(ReturnCodes.ObValue_ServerError) =>
            VAR arg: TEXT;
            BEGIN
              arg := StubLib.InRef(c, rep, -1);
              reuse := TRUE;
              RAISE ObValue.ServerError(arg);

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
    END Surrogate_OpenRead;

  PROCEDURE Surrogate_OpenWrite(
      self: ObValue.RemFileSystem;
      fileName_arg: TEXT): Wr.T RAISES {NetObj.Error, ObValue.ServerError,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: Wr.T;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.OpenWrite));
          StubLib.OutRef(c, fileName_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(Wr.T));
            reuse := TRUE;
          | ORD(ReturnCodes.ObValue_ServerError) =>
            VAR arg: TEXT;
            BEGIN
              arg := StubLib.InRef(c, rep, -1);
              reuse := TRUE;
              RAISE ObValue.ServerError(arg);

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
    END Surrogate_OpenWrite;

  PROCEDURE Surrogate_OpenAppend(
      self: ObValue.RemFileSystem;
      fileName_arg: TEXT): Wr.T RAISES {NetObj.Error, ObValue.ServerError,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: Wr.T;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.OpenAppend));
          StubLib.OutRef(c, fileName_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(Wr.T));
            reuse := TRUE;
          | ORD(ReturnCodes.ObValue_ServerError) =>
            VAR arg: TEXT;
            BEGIN
              arg := StubLib.InRef(c, rep, -1);
              reuse := TRUE;
              RAISE ObValue.ServerError(arg);

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
    END Surrogate_OpenAppend;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, ObValue.RemFileSystem);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.OpenRead) => Stub_OpenRead(t, c, rep);
      | ORD(Methods.OpenWrite) => Stub_OpenWrite(t, c, rep);
      | ORD(Methods.OpenAppend) => Stub_OpenAppend(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    | ObValue.ServerError(arg) =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.ObValue_ServerError));
        StubLib.OutRef(c, arg);
    END;
  END Invoke;

PROCEDURE Stub_OpenRead(
    self: ObValue.RemFileSystem;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, ObValue.ServerError}=
  VAR fileName_arg: TEXT;
      res: Rd.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    fileName_arg := StubLib.InRef(c, rep, -1);
    res := self.OpenRead(fileName_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_OpenRead;

PROCEDURE Stub_OpenWrite(
    self: ObValue.RemFileSystem;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, ObValue.ServerError}=
  VAR fileName_arg: TEXT;
      res: Wr.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    fileName_arg := StubLib.InRef(c, rep, -1);
    res := self.OpenWrite(fileName_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_OpenWrite;

PROCEDURE Stub_OpenAppend(
    self: ObValue.RemFileSystem;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, ObValue.ServerError}=
  VAR fileName_arg: TEXT;
      res: Wr.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    fileName_arg := StubLib.InRef(c, rep, -1);
    res := self.OpenAppend(fileName_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_OpenAppend;

BEGIN
  StubLib.Register(TYPECODE(ObValue.RemFileSystem), 2, TYPECODE(Surrogate_ObValue_RemFileSystem), Invoke);
END ObValue_RemFileSystem_v2.
