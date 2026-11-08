INTERFACE Bank_T_v2;

IMPORT Rd, Wr, StubLib, Bank, Thread, NetObj;
TYPE Surrogate_Bank_T = Bank.T OBJECT
      OVERRIDES
        findAccount := Surrogate_findAccount;
      END;

PROCEDURE Surrogate_findAccount(self: Bank.T; acct: Bank.AcctNum)
    : Bank.Account RAISES {Thread.Alerted, NetObj.Error};

PROCEDURE Stub_findAccount(
    self: Bank.T;
    c: StubLib.Conn;
    rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure, Wr.Failure,
    Thread.Alerted};

END Bank_T_v2.
