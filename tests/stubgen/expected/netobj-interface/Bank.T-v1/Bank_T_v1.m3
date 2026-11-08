MODULE Bank_T_v1 EXPORTS Bank, Bank_T_v1;

IMPORT Rd, Wr, StubLib, Bank, Thread, NetObj;
CONST Protocol: StubLib.StubProtocol = 1;

TYPE
      Methods = {findAccount};
      ReturnCodes = {OK};

  PROCEDURE Surrogate_findAccount(self: Bank.T; acct_arg: Bank.AcctNum)
      : Bank.Account RAISES {Thread.Alerted, NetObj.Error} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: Bank.Account;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.findAccount));
          StubLib.OutInteger(c, acct_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(Bank.Account));
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
    END Surrogate_findAccount;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, Bank.T);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.findAccount) => Stub_findAccount(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    END;
  END Invoke;

PROCEDURE Stub_findAccount(
    self: Bank.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR acct_arg: Bank.AcctNum;
      res: Bank.Account;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    acct_arg := StubLib.InInteger(c, rep, 1, 100);
    res := self.findAccount(acct_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_findAccount;

BEGIN
  StubLib.Register(TYPECODE(Bank.T), 1, TYPECODE(Surrogate_Bank_T), Invoke);
END Bank_T_v1.
