MODULE Endpoint_T_v2 EXPORTS Endpoint, Endpoint_T_v2;

IMPORT Fingerprint, Endpoint, Rd, Wr, StubLib, Event, AtomList, Thread,
       NetObj;
CONST Protocol: StubLib.StubProtocol = 2;

TYPE
      Methods = {disconnect, connect, space};
      ReturnCodes = {OK, Event_Error};

  PROCEDURE Surrogate_space(self: Endpoint.T): Fingerprint.T
       RAISES {Thread.Alerted, NetObj.Error} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: Fingerprint.T;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.space));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            FOR i0 := FIRST([0..7]) TO LAST([0..7]) DO
              res.byte[i0] := StubLib.InInteger(c, rep, 0, 255);
              END;
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
    END Surrogate_space;

  PROCEDURE Surrogate_connect(
      self: Endpoint.T;
      id_arg: Fingerprint.T;
      rd_arg: Rd.T;
      wr_arg: Wr.T) RAISES {Thread.Alerted, NetObj.Error, Event.Error} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.connect));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            FOR i0 := FIRST([0..7]) TO LAST([0..7]) DO
              StubLib.OutInteger(c, id_arg.byte[i0]);
              END;
            END;
            StubLib.OutRef(c, rd_arg);
          StubLib.OutRef(c, wr_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.Event_Error) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE Event.Error(arg);

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
    END Surrogate_connect;

  PROCEDURE Surrogate_disconnect(self: Endpoint.T; id_arg: Fingerprint.T)
       RAISES {Thread.Alerted, NetObj.Error, Event.Error} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.disconnect));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            FOR i0 := FIRST([0..7]) TO LAST([0..7]) DO
              StubLib.OutInteger(c, id_arg.byte[i0]);
              END;
            END;
            rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.Event_Error) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE Event.Error(arg);

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
    END Surrogate_disconnect;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, Endpoint.T);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.space) => Stub_space(t, c, rep);
      | ORD(Methods.connect) => Stub_connect(t, c, rep);
      | ORD(Methods.disconnect) => Stub_disconnect(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    | Event.Error(arg) =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.Event_Error));
        StubLib.OutRef(c, arg);
    END;
  END Invoke;

PROCEDURE Stub_space(
    self: Endpoint.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR res: Fingerprint.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    res := self.space();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    FOR i0 := FIRST([0..7]) TO LAST([0..7]) DO
      StubLib.OutInteger(c, res.byte[i0]);
      END;

  END Stub_space;

PROCEDURE Stub_connect(
    self: Endpoint.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, Event.Error}=
  VAR id_arg: Fingerprint.T;
      rd_arg: Rd.T;
      wr_arg: Wr.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      FOR i0 := FIRST([0..7]) TO LAST([0..7]) DO
        id_arg.byte[i0] := StubLib.InInteger(c, rep, 0, 255);
        END;
      END;
      rd_arg := StubLib.InRef(c, rep, TYPECODE(Rd.T));
    wr_arg := StubLib.InRef(c, rep, TYPECODE(Wr.T));
    self.connect(id_arg, rd_arg, wr_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_connect;

PROCEDURE Stub_disconnect(
    self: Endpoint.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, Event.Error}=
  VAR id_arg: Fingerprint.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      FOR i0 := FIRST([0..7]) TO LAST([0..7]) DO
        id_arg.byte[i0] := StubLib.InInteger(c, rep, 0, 255);
        END;
      END;
      self.disconnect(id_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_disconnect;

BEGIN
  StubLib.Register(TYPECODE(Endpoint.T), 2, TYPECODE(Surrogate_Endpoint_T), Invoke);
END Endpoint_T_v2.
