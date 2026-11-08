INTERFACE Bank_Account_v1;

IMPORT Rd, Wr, StubLib, Bank, Thread, NetObj;
TYPE Surrogate_Bank_Account = Bank.Account OBJECT
      OVERRIDES
        deposit := Surrogate_deposit;
        withdraw := Surrogate_withdraw;
        get_balance := Surrogate_get_balance;
      END;

PROCEDURE Surrogate_deposit(self: Bank.Account; amount: REAL)
     RAISES {Bank.BadAmount, Thread.Alerted, NetObj.Error};

PROCEDURE Surrogate_withdraw(self: Bank.Account; amount: REAL)
     RAISES {Bank.BadAmount, Bank.InsufficientFunds, Thread.Alerted,
    NetObj.Error};

PROCEDURE Surrogate_get_balance(self: Bank.Account): REAL
     RAISES {Thread.Alerted, NetObj.Error};

PROCEDURE Stub_deposit(
    self: Bank.Account;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, Bank.BadAmount};

PROCEDURE Stub_withdraw(
    self: Bank.Account;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted, Bank.BadAmount, Bank.InsufficientFunds};

PROCEDURE Stub_get_balance(
    self: Bank.Account;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

END Bank_Account_v1.
