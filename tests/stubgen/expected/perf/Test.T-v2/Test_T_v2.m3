MODULE Test_T_v2 EXPORTS Test, Test_T_v2;

IMPORT Test, Rd, Wr, StubLib, Thread, NetObj;
CONST Protocol: StubLib.StubProtocol = 2;

TYPE
      Methods = {pickle, newReturn, newObj, wr, rd, objarg, ten, null};
      ReturnCodes = {OK};

  PROCEDURE Surrogate_null(self: Test.T) RAISES {} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.null));
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
    END Surrogate_null;

  PROCEDURE Surrogate_ten(
      self: Test.T;
      a0_arg: INTEGER;
      a1_arg: INTEGER;
      a2_arg: INTEGER;
      a3_arg: INTEGER;
      a4_arg: INTEGER;
      a5_arg: INTEGER;
      a6_arg: INTEGER;
      a7_arg: INTEGER;
      a8_arg: INTEGER;
      a9_arg: INTEGER) RAISES {} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.ten));
          StubLib.OutInteger(c, a0_arg);
          StubLib.OutInteger(c, a1_arg);
          StubLib.OutInteger(c, a2_arg);
          StubLib.OutInteger(c, a3_arg);
          StubLib.OutInteger(c, a4_arg);
          StubLib.OutInteger(c, a5_arg);
          StubLib.OutInteger(c, a6_arg);
          StubLib.OutInteger(c, a7_arg);
          StubLib.OutInteger(c, a8_arg);
          StubLib.OutInteger(c, a9_arg);
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
    END Surrogate_ten;

  PROCEDURE Surrogate_objarg(self: Test.T; arg_arg: NetObj.T) RAISES {} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.objarg));
          StubLib.OutRef(c, arg_arg);
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
    END Surrogate_objarg;

  PROCEDURE Surrogate_rd(self: Test.T): Rd.T RAISES {} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: Rd.T;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.rd));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(Rd.T));
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
    END Surrogate_rd;

  PROCEDURE Surrogate_wr(self: Test.T): Wr.T RAISES {} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: Wr.T;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.wr));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(Wr.T));
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
    END Surrogate_wr;

  PROCEDURE Surrogate_newObj(self: Test.T): NetObj.T RAISES {} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: NetObj.T;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.newObj));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(NetObj.T));
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
    END Surrogate_newObj;

  PROCEDURE Surrogate_newReturn(
      self: Test.T;
      type_arg: Test.ObjType;
      surr_arg: NetObj.T): Test.Return RAISES {} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: Test.Return;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.newReturn));
          StubLib.OutInteger(c, ORD(type_arg));
          StubLib.OutRef(c, surr_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(Test.Return));
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
    END Surrogate_newReturn;

  PROCEDURE Surrogate_pickle(self: Test.T; r_arg: REFANY) RAISES {} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.pickle));
          StubLib.OutRef(c, r_arg);
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
    END Surrogate_pickle;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, Test.T);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.null) => Stub_null(t, c, rep);
      | ORD(Methods.ten) => Stub_ten(t, c, rep);
      | ORD(Methods.objarg) => Stub_objarg(t, c, rep);
      | ORD(Methods.rd) => Stub_rd(t, c, rep);
      | ORD(Methods.wr) => Stub_wr(t, c, rep);
      | ORD(Methods.newObj) => Stub_newObj(t, c, rep);
      | ORD(Methods.newReturn) => Stub_newReturn(t, c, rep);
      | ORD(Methods.pickle) => Stub_pickle(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    END;
  END Invoke;

PROCEDURE Stub_null(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  BEGIN
    self.null();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_null;

PROCEDURE Stub_ten(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR a0_arg: INTEGER;
      a1_arg: INTEGER;
      a2_arg: INTEGER;
      a3_arg: INTEGER;
      a4_arg: INTEGER;
      a5_arg: INTEGER;
      a6_arg: INTEGER;
      a7_arg: INTEGER;
      a8_arg: INTEGER;
      a9_arg: INTEGER;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    a0_arg := StubLib.InInteger(c, rep);
    a1_arg := StubLib.InInteger(c, rep);
    a2_arg := StubLib.InInteger(c, rep);
    a3_arg := StubLib.InInteger(c, rep);
    a4_arg := StubLib.InInteger(c, rep);
    a5_arg := StubLib.InInteger(c, rep);
    a6_arg := StubLib.InInteger(c, rep);
    a7_arg := StubLib.InInteger(c, rep);
    a8_arg := StubLib.InInteger(c, rep);
    a9_arg := StubLib.InInteger(c, rep);
    self.ten(a0_arg, a1_arg, a2_arg, a3_arg, a4_arg, a5_arg, a6_arg, a7_arg, a8_arg, a9_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_ten;

PROCEDURE Stub_objarg(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR arg_arg: NetObj.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    arg_arg := StubLib.InRef(c, rep, TYPECODE(NetObj.T));
    self.objarg(arg_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_objarg;

PROCEDURE Stub_rd(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR res: Rd.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    res := self.rd();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_rd;

PROCEDURE Stub_wr(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR res: Wr.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    res := self.wr();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_wr;

PROCEDURE Stub_newObj(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR res: NetObj.T;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    res := self.newObj();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_newObj;

PROCEDURE Stub_newReturn(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR type_arg: Test.ObjType;
      surr_arg: NetObj.T;
      res: Test.Return;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    type_arg := VAL(StubLib.InInteger(c, rep, 0,2), Test.ObjType);
    surr_arg := StubLib.InRef(c, rep, TYPECODE(NetObj.T));
    res := self.newReturn(type_arg, surr_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_newReturn;

PROCEDURE Stub_pickle(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR r_arg: REFANY;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    r_arg := StubLib.InRef(c, rep, -1);
    self.pickle(r_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_pickle;

BEGIN
  StubLib.Register(TYPECODE(Test.T), 2, TYPECODE(Surrogate_Test_T), Invoke);
END Test_T_v2.
