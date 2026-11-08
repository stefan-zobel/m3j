MODULE ObValue_RemEngine_v1 EXPORTS ObValue, ObValue_RemEngine_v1;

IMPORT Rd, Wr, StubLib, ObValue, Thread, NetObj;
CONST Protocol: StubLib.StubProtocol = 1;

TYPE
      Methods = {Eval, Who};
      ReturnCodes = {OK, ObValue_Error, ObValue_Exception,
        ObValue_ServerError};

  PROCEDURE Surrogate_Who(self: ObValue.RemEngine): TEXT
       RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: TEXT;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.Who));
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
    END Surrogate_Who;

  PROCEDURE Surrogate_Eval(
      self: ObValue.RemEngine;
      proc_arg: ObValue.Val;
      mySelf_arg: ObValue.ValObj): ObValue.Val RAISES {ObValue.Error,
      ObValue.Exception, ObValue.ServerError, NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: ObValue.Val;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.Eval));
          StubLib.OutRef(c, proc_arg);
          StubLib.OutRef(c, mySelf_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(ObValue.Val));
            reuse := TRUE;
          | ORD(ReturnCodes.ObValue_Error) =>
            VAR arg: ObValue.ErrorPacket;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(ObValue.ErrorPacket));
              reuse := TRUE;
              RAISE ObValue.Error(arg);

            END;
          | ORD(ReturnCodes.ObValue_Exception) =>
            VAR arg: ObValue.ExceptionPacket;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(ObValue.ExceptionPacket));
              reuse := TRUE;
              RAISE ObValue.Exception(arg);

            END;
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
    END Surrogate_Eval;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, ObValue.RemEngine);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.Who) => Stub_Who(t, c, rep);
      | ORD(Methods.Eval) => Stub_Eval(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    | ObValue.ServerError(arg) =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.ObValue_ServerError));
        StubLib.OutRef(c, arg);

    | ObValue.Exception(arg) =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.ObValue_Exception));
        StubLib.OutRef(c, arg);

    | ObValue.Error(arg) =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.ObValue_Error));
        StubLib.OutRef(c, arg);
    END;
  END Invoke;

PROCEDURE Stub_Who(
    self: ObValue.RemEngine;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR res: TEXT;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    res := self.Who();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_Who;

PROCEDURE Stub_Eval(
    self: ObValue.RemEngine;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, ObValue.Error, ObValue.Exception,
    ObValue.ServerError}=
  VAR proc_arg: ObValue.Val;
      mySelf_arg: ObValue.ValObj;
      res: ObValue.Val;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    proc_arg := StubLib.InRef(c, rep, TYPECODE(ObValue.Val));
    mySelf_arg := StubLib.InRef(c, rep, TYPECODE(ObValue.ValObj));
    res := self.Eval(proc_arg, mySelf_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_Eval;

BEGIN
  StubLib.Register(TYPECODE(ObValue.RemEngine), 1, TYPECODE(Surrogate_ObValue_RemEngine), Invoke);
END ObValue_RemEngine_v1.
