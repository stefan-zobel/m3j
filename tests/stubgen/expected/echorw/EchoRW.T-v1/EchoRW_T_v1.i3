INTERFACE EchoRW_T_v1;

IMPORT EchoRW, Rd, Wr, StubLib, Thread, NetObj;
TYPE Surrogate_EchoRW_T = EchoRW.T OBJECT
      OVERRIDES
        echo := Surrogate_echo;
      END;

PROCEDURE Surrogate_echo(self: EchoRW.T; rd: Rd.T; wr: Wr.T) RAISES {};

PROCEDURE Stub_echo(self: EchoRW.T; c: StubLib.Conn; rep: StubLib.DataRep)
     RAISES {NetObj.Error, Rd.Failure, Wr.Failure, Thread.Alerted};

END EchoRW_T_v1.
