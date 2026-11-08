MODULE RemoteView_T_v1 EXPORTS RemoteView, RemoteView_T_v1;

IMPORT RemoteView, Rd, Wr, StubLib, Thread, NetObj;
CONST Protocol: StubLib.StubProtocol = 1;

TYPE
      Methods = {event, endrun, startrun};
      ReturnCodes = {OK, RemoteView_Error};

  PROCEDURE Surrogate_startrun(self: RemoteView.T) RAISES {NetObj.Error,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.startrun));
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
    END Surrogate_startrun;

  PROCEDURE Surrogate_endrun(self: RemoteView.T) RAISES {NetObj.Error,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.endrun));
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
    END Surrogate_endrun;

  PROCEDURE Surrogate_event(
      self: RemoteView.T;
      tfactor_arg: REAL;
      nm_arg: TEXT;
      args_arg: TEXT) RAISES {RemoteView.Error, NetObj.Error,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.event));
          StubLib.OutReal(c, tfactor_arg);
          StubLib.OutRef(c, nm_arg);
          StubLib.OutRef(c, args_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.RemoteView_Error) =>
            VAR arg: TEXT;
            BEGIN
              arg := StubLib.InRef(c, rep, -1);
              reuse := TRUE;
              RAISE RemoteView.Error(arg);

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
    END Surrogate_event;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, RemoteView.T);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.startrun) => Stub_startrun(t, c, rep);
      | ORD(Methods.endrun) => Stub_endrun(t, c, rep);
      | ORD(Methods.event) => Stub_event(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    | RemoteView.Error(arg) =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.RemoteView_Error));
        StubLib.OutRef(c, arg);
    END;
  END Invoke;

PROCEDURE Stub_startrun(
    self: RemoteView.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  BEGIN
    self.startrun();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_startrun;

PROCEDURE Stub_endrun(
    self: RemoteView.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  BEGIN
    self.endrun();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_endrun;

PROCEDURE Stub_event(
    self: RemoteView.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, RemoteView.Error}=
  VAR tfactor_arg: REAL;
      nm_arg: TEXT;
      args_arg: TEXT;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    tfactor_arg := StubLib.InReal(c, rep);
    nm_arg := StubLib.InRef(c, rep, -1);
    args_arg := StubLib.InRef(c, rep, -1);
    self.event(tfactor_arg, nm_arg, args_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_event;

BEGIN
  StubLib.Register(TYPECODE(RemoteView.T), 1, TYPECODE(Surrogate_RemoteView_T), Invoke);
END RemoteView_T_v1.
