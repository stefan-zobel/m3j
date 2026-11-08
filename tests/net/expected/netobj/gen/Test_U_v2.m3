MODULE Test_U_v2 EXPORTS Test, Test_U_v2;

IMPORT Test, Rd, Wr, StubLib, Thread, NetObj;
CONST Protocol: StubLib.StubProtocol = 2;

TYPE
      Methods = {same, rec, sum, list};
      ReturnCodes = {OK};

  PROCEDURE Surrogate_list(self: Test.U; n_arg: INTEGER): Test.List
       RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: Test.List;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.list));
          StubLib.OutInteger(c, n_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(Test.List));
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
    END Surrogate_list;

  PROCEDURE Surrogate_sum(self: Test.U; l_arg: Test.List): INTEGER
       RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: INTEGER;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.sum));
          StubLib.OutRef(c, l_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InInteger(c, rep);
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
    END Surrogate_sum;

  PROCEDURE Surrogate_rec(self: Test.U; READONLY r_arg: Test.Rec): Test.Rec
       RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: Test.Rec;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.rec));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutInteger(c, r_arg.i);
            StubLib.OutReal(c, r_arg.r);
            StubLib.OutLongreal(c, r_arg.l);
            StubLib.OutInteger(c, ORD(r_arg.b));
            StubLib.OutInteger(c, ORD(r_arg.c));
            StubLib.OutInteger(c, ORD(r_arg.w));
            StubLib.OutInteger(c, r_arg.s);
            StubLib.OutInteger(c, ORD(r_arg.e));
            FOR i0 := FIRST([0..40]) TO LAST([0..40]) DO
              StubLib.OutBoolean(c, i0 IN r_arg.set);
              END;
            END;
            rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res.i := StubLib.InInteger(c, rep);
            res.r := StubLib.InReal(c, rep);
            res.l := StubLib.InLongreal(c, rep);
            res.b := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
            res.c := VAL(StubLib.InInteger(c, rep, 0,255), CHAR);
            res.w := VAL(StubLib.InInteger(c, rep, 0,65535), WIDECHAR);
            res.s := StubLib.InInteger(c, rep, -3, 1000);
            res.e := VAL(StubLib.InInteger(c, rep, 0,2), Test.Color);
            res.set:=SET OF [0..40]{};
            FOR i0 := FIRST([0..40]) TO LAST([0..40]) DO
              IF StubLib.InBoolean(c) THEN
                res.set := res.set + SET OF [0..40]{i0};
                END
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
    END Surrogate_rec;

  PROCEDURE Surrogate_same(self: Test.U; t_arg: Test.T): BOOLEAN
       RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: BOOLEAN;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.same));
          StubLib.OutRef(c, t_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
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
    END Surrogate_same;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, Test.U);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.list) => Stub_list(t, c, rep);
      | ORD(Methods.sum) => Stub_sum(t, c, rep);
      | ORD(Methods.rec) => Stub_rec(t, c, rep);
      | ORD(Methods.same) => Stub_same(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    END;
  END Invoke;

PROCEDURE Stub_list(
    self: Test.U;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR n_arg: INTEGER;
      res: Test.List;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    n_arg := StubLib.InInteger(c, rep);
    res := self.list(n_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_list;

PROCEDURE Stub_sum(
    self: Test.U;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR l_arg: Test.List;
      res: INTEGER;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    l_arg := StubLib.InRef(c, rep, TYPECODE(Test.List));
    res := self.sum(l_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutInteger(c, res);

  END Stub_sum;

PROCEDURE Stub_rec(
    self: Test.U;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR r_arg: Test.Rec;
      res: Test.Rec;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      r_arg.i := StubLib.InInteger(c, rep);
      r_arg.r := StubLib.InReal(c, rep);
      r_arg.l := StubLib.InLongreal(c, rep);
      r_arg.b := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
      r_arg.c := VAL(StubLib.InInteger(c, rep, 0,255), CHAR);
      r_arg.w := VAL(StubLib.InInteger(c, rep, 0,65535), WIDECHAR);
      r_arg.s := StubLib.InInteger(c, rep, -3, 1000);
      r_arg.e := VAL(StubLib.InInteger(c, rep, 0,2), Test.Color);
      r_arg.set:=SET OF [0..40]{};
      FOR i0 := FIRST([0..40]) TO LAST([0..40]) DO
        IF StubLib.InBoolean(c) THEN
          r_arg.set := r_arg.set + SET OF [0..40]{i0};
          END
        END;
      END;
      res := self.rec(r_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutInteger(c, res.i);
    StubLib.OutReal(c, res.r);
    StubLib.OutLongreal(c, res.l);
    StubLib.OutInteger(c, ORD(res.b));
    StubLib.OutInteger(c, ORD(res.c));
    StubLib.OutInteger(c, ORD(res.w));
    StubLib.OutInteger(c, res.s);
    StubLib.OutInteger(c, ORD(res.e));
    FOR i0 := FIRST([0..40]) TO LAST([0..40]) DO
      StubLib.OutBoolean(c, i0 IN res.set);
      END;

  END Stub_rec;

PROCEDURE Stub_same(
    self: Test.U;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR t_arg: Test.T;
      res: BOOLEAN;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    t_arg := StubLib.InRef(c, rep, TYPECODE(Test.T));
    res := self.same(t_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutInteger(c, ORD(res));

  END Stub_same;

BEGIN
  StubLib.Register(TYPECODE(Test.U), 2, TYPECODE(Surrogate_Test_U), Invoke);
END Test_U_v2.
