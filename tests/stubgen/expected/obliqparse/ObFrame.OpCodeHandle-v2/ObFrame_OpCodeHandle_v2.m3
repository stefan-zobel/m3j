MODULE ObFrame_OpCodeHandle_v2 EXPORTS ObFrame, ObFrame_OpCodeHandle_v2;

IMPORT Fingerprint, Rd, Wr, StubLib, ObLib, ObFrame, Thread, NetObj;
CONST Protocol: StubLib.StubProtocol = 2;

TYPE
      Methods = {getOpCodes};
      ReturnCodes = {OK};

  PROCEDURE Surrogate_getOpCodes(
      self: ObFrame.OpCodeHandle;
      ts_arg: Fingerprint.T): REF ObLib.OpCodes RAISES {NetObj.Error,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: REF ObLib.OpCodes;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.getOpCodes));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            FOR i0 := FIRST([0..7]) TO LAST([0..7]) DO
              StubLib.OutInteger(c, ts_arg.byte[i0]);
              END;
            END;
            rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, -1);
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
    END Surrogate_getOpCodes;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, ObFrame.OpCodeHandle);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.getOpCodes) => Stub_getOpCodes(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    END;
  END Invoke;

PROCEDURE Stub_getOpCodes(
    self: ObFrame.OpCodeHandle;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR ts_arg: Fingerprint.T;
      res: REF ObLib.OpCodes;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      FOR i0 := FIRST([0..7]) TO LAST([0..7]) DO
        ts_arg.byte[i0] := StubLib.InInteger(c, rep, 0, 255);
        END;
      END;
      res := self.getOpCodes(ts_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_getOpCodes;

BEGIN
  StubLib.Register(TYPECODE(ObFrame.OpCodeHandle), 2, TYPECODE(Surrogate_ObFrame_OpCodeHandle), Invoke);
END ObFrame_OpCodeHandle_v2.
