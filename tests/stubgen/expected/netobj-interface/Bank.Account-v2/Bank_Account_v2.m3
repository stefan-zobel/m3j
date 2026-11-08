MODULE Bank_Account_v2 EXPORTS Bank, Bank_Account_v2;

IMPORT Rd, Wr, StubLib, Bank, Thread, NetObj;
CONST Protocol: StubLib.StubProtocol = 2;

TYPE
      Methods = {get_balance, withdraw, deposit};
      ReturnCodes = {OK, Bank_BadAmount, Bank_InsufficientFunds};

  PROCEDURE Surrogate_deposit(self: Bank.Account; amount_arg: REAL)
       RAISES {Bank.BadAmount, Thread.Alerted, NetObj.Error} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.deposit));
          StubLib.OutReal(c, amount_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.Bank_BadAmount) =>
          reuse := TRUE;
          RAISE Bank.BadAmount;
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
    END Surrogate_deposit;

  PROCEDURE Surrogate_withdraw(self: Bank.Account; amount_arg: REAL)
       RAISES {Bank.BadAmount, Bank.InsufficientFunds, Thread.Alerted,
      NetObj.Error} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.withdraw));
          StubLib.OutReal(c, amount_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.Bank_BadAmount) =>
          reuse := TRUE;
          RAISE Bank.BadAmount;
          | ORD(ReturnCodes.Bank_InsufficientFunds) =>
          reuse := TRUE;
          RAISE Bank.InsufficientFunds;
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
    END Surrogate_withdraw;

  PROCEDURE Surrogate_get_balance(self: Bank.Account): REAL
       RAISES {Thread.Alerted, NetObj.Error} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: REAL;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.get_balance));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InReal(c, rep);
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
    END Surrogate_get_balance;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, Bank.Account);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.deposit) => Stub_deposit(t, c, rep);
      | ORD(Methods.withdraw) => Stub_withdraw(t, c, rep);
      | ORD(Methods.get_balance) => Stub_get_balance(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    | Bank.InsufficientFunds =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.Bank_InsufficientFunds));

    | Bank.BadAmount =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.Bank_BadAmount));
    END;
  END Invoke;

PROCEDURE Stub_deposit(
    self: Bank.Account;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, Bank.BadAmount}=
  VAR amount_arg: REAL;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    amount_arg := StubLib.InReal(c, rep);
    self.deposit(amount_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_deposit;

PROCEDURE Stub_withdraw(
    self: Bank.Account;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, Bank.BadAmount, Bank.InsufficientFunds}=
  VAR amount_arg: REAL;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    amount_arg := StubLib.InReal(c, rep);
    self.withdraw(amount_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_withdraw;

PROCEDURE Stub_get_balance(
    self: Bank.Account;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR res: REAL;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    res := self.get_balance();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutReal(c, res);

  END Stub_get_balance;

BEGIN
  StubLib.Register(TYPECODE(Bank.Account), 2, TYPECODE(Surrogate_Bank_Account), Invoke);
END Bank_Account_v2.
