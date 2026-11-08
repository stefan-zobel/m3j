MODULE EchoRW_T_v2 EXPORTS EchoRW, EchoRW_T_v2;

IMPORT EchoRW, Rd, Wr, StubLib, Thread, NetObj;
CONST Protocol: StubLib.StubProtocol = 2;

TYPE
      Methods = {echo};
      ReturnCodes = {OK};

  PROCEDURE Surrogate_echo(self: EchoRW.T; rd_arg: Rd.T; wr_arg: Wr.T)
       RAISES {} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.echo));
          StubLib.OutRef(c, rd_arg);
          StubLib.OutRef(c, wr_arg);
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
    END Surrogate_echo;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, EchoRW.T);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.echo) => Stub_echo(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    END;
  END Invoke;

PROCEDURE Stub_echo(
    self: EchoRW.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR rd_arg: Rd.T;
      wr_arg: Wr.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    rd_arg := StubLib.InRef(c, rep, TYPECODE(Rd.T));
    wr_arg := StubLib.InRef(c, rep, TYPECODE(Wr.T));
    self.echo(rd_arg, wr_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_echo;

BEGIN
  StubLib.Register(TYPECODE(EchoRW.T), 2, TYPECODE(Surrogate_EchoRW_T), Invoke);
END EchoRW_T_v2.
