MODULE Test_T_v1 EXPORTS Test, Test_T_v1;

IMPORT Test, Rd, Wr, StubLib, Thread, NetObj;
CONST Protocol: StubLib.StubProtocol = 1;

TYPE
      Methods = {quit, writer, reader, other, callback, fail, texts, chars,
        arr, sum, list, rec, echo, add};
      ReturnCodes = {OK, Test_Oops};

  PROCEDURE Surrogate_add(self: Test.T; a_arg: INTEGER; b_arg: INTEGER)
      : INTEGER RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: INTEGER;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.add));
          StubLib.OutInteger(c, a_arg);
          StubLib.OutInteger(c, b_arg);
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
    END Surrogate_add;

  PROCEDURE Surrogate_echo(self: Test.T; t_arg: TEXT): TEXT
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
          StubLib.OutInt32(c, ORD(Methods.echo));
          StubLib.OutRef(c, t_arg);
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
    END Surrogate_echo;

  PROCEDURE Surrogate_rec(
      self: Test.T;
      READONLY r_arg: Test.Rec;
      VAR out_arg: Test.Rec) RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

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
            dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            StubLib.OutInteger(c, out_arg.i);
            StubLib.OutReal(c, out_arg.r);
            StubLib.OutLongreal(c, out_arg.l);
            StubLib.OutInteger(c, ORD(out_arg.b));
            StubLib.OutInteger(c, ORD(out_arg.c));
            StubLib.OutInteger(c, ORD(out_arg.w));
            StubLib.OutInteger(c, out_arg.s);
            StubLib.OutInteger(c, ORD(out_arg.e));
            FOR i0 := FIRST([0..40]) TO LAST([0..40]) DO
              StubLib.OutBoolean(c, i0 IN out_arg.set);
              END;
            END;
            rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            out_arg.i := StubLib.InInteger(c, rep);
            out_arg.r := StubLib.InReal(c, rep);
            out_arg.l := StubLib.InLongreal(c, rep);
            out_arg.b := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
            out_arg.c := VAL(StubLib.InInteger(c, rep, 0,255), CHAR);
            out_arg.w := VAL(StubLib.InInteger(c, rep, 0,65535), WIDECHAR);
            out_arg.s := StubLib.InInteger(c, rep, -3, 1000);
            out_arg.e := VAL(StubLib.InInteger(c, rep, 0,2), Test.Color);
            out_arg.set:=SET OF [0..40]{};
            FOR i0 := FIRST([0..40]) TO LAST([0..40]) DO
              IF StubLib.InBoolean(c) THEN
                out_arg.set := out_arg.set + SET OF [0..40]{i0};
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
    END Surrogate_rec;

  PROCEDURE Surrogate_list(self: Test.T; n_arg: INTEGER): Test.List
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

  PROCEDURE Surrogate_sum(self: Test.T; l_arg: Test.List): INTEGER
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

  PROCEDURE Surrogate_arr(self: Test.T; VAR a_arg: ARRAY  OF INTEGER)
       RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.arr));
          StubLib.OutInteger(c, NUMBER(a_arg));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            FOR n1 := 0 TO LAST(a_arg) DO
              StubLib.OutInteger(c, a_arg[n1]);
              END;
            END;
            rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            FOR n1 := 0 TO LAST(a_arg) DO
              a_arg[n1] := StubLib.InInteger(c, rep);
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
    END Surrogate_arr;

  PROCEDURE Surrogate_chars(
      self: Test.T;
      READONLY a_arg: ARRAY  OF CHAR;
      VAR w_arg: ARRAY  OF WIDECHAR): TEXT RAISES {NetObj.Error,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: TEXT;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.chars));
          StubLib.OutInteger(c, NUMBER(a_arg));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            FOR n1 := 0 TO LAST(a_arg) DO
              StubLib.OutInteger(c, ORD(a_arg[n1]));
              END;
            END;
            StubLib.OutInteger(c, NUMBER(w_arg));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            FOR n1 := 0 TO LAST(w_arg) DO
              StubLib.OutInteger(c, ORD(w_arg[n1]));
              END;
            END;
            rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            FOR n1 := 0 TO LAST(w_arg) DO
              w_arg[n1] := VAL(StubLib.InInteger(c, rep, 0,65535), WIDECHAR);
              END;
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
    END Surrogate_chars;

  PROCEDURE Surrogate_texts(self: Test.T; READONLY a_arg: ARRAY  OF TEXT)
      : TEXT RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: TEXT;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.texts));
          StubLib.OutInteger(c, NUMBER(a_arg));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            FOR n1 := 0 TO LAST(a_arg) DO
              StubLib.OutRef(c, a_arg[n1]);
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
    END Surrogate_texts;

  PROCEDURE Surrogate_fail(self: Test.T; code_arg: INTEGER)
       RAISES {NetObj.Error, Thread.Alerted, Test.Oops} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.fail));
          StubLib.OutInteger(c, code_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.Test_Oops) =>
            VAR arg: TEXT;
            BEGIN
              arg := StubLib.InRef(c, rep, -1);
              reuse := TRUE;
              RAISE Test.Oops(arg);

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
    END Surrogate_fail;

  PROCEDURE Surrogate_callback(
      self: Test.T;
      cb_arg: Test.T;
      n_arg: INTEGER): INTEGER RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: INTEGER;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.callback));
          StubLib.OutRef(c, cb_arg);
          StubLib.OutInteger(c, n_arg);
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
    END Surrogate_callback;

  PROCEDURE Surrogate_other(self: Test.T): Test.U RAISES {NetObj.Error,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: Test.U;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.other));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(Test.U));
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
    END Surrogate_other;

  PROCEDURE Surrogate_reader(self: Test.T; rd_arg: Rd.T): TEXT
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
          StubLib.OutInt32(c, ORD(Methods.reader));
          StubLib.OutRef(c, rd_arg);
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
    END Surrogate_reader;

  PROCEDURE Surrogate_writer(self: Test.T; wr_arg: Wr.T; t_arg: TEXT)
       RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.writer));
          StubLib.OutRef(c, wr_arg);
          StubLib.OutRef(c, t_arg);
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
    END Surrogate_writer;

  PROCEDURE Surrogate_quit(self: Test.T) RAISES {NetObj.Error,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.quit));
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
    END Surrogate_quit;

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
      | ORD(Methods.add) => Stub_add(t, c, rep);
      | ORD(Methods.echo) => Stub_echo(t, c, rep);
      | ORD(Methods.rec) => Stub_rec(t, c, rep);
      | ORD(Methods.list) => Stub_list(t, c, rep);
      | ORD(Methods.sum) => Stub_sum(t, c, rep);
      | ORD(Methods.arr) => Stub_arr(t, c, rep);
      | ORD(Methods.chars) => Stub_chars(t, c, rep);
      | ORD(Methods.texts) => Stub_texts(t, c, rep);
      | ORD(Methods.fail) => Stub_fail(t, c, rep);
      | ORD(Methods.callback) => Stub_callback(t, c, rep);
      | ORD(Methods.other) => Stub_other(t, c, rep);
      | ORD(Methods.reader) => Stub_reader(t, c, rep);
      | ORD(Methods.writer) => Stub_writer(t, c, rep);
      | ORD(Methods.quit) => Stub_quit(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    | Test.Oops(arg) =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.Test_Oops));
        StubLib.OutRef(c, arg);
    END;
  END Invoke;

PROCEDURE Stub_add(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR a_arg: INTEGER;
      b_arg: INTEGER;
      res: INTEGER;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    a_arg := StubLib.InInteger(c, rep);
    b_arg := StubLib.InInteger(c, rep);
    res := self.add(a_arg, b_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutInteger(c, res);

  END Stub_add;

PROCEDURE Stub_echo(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR t_arg: TEXT;
      res: TEXT;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    t_arg := StubLib.InRef(c, rep, -1);
    res := self.echo(t_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_echo;

PROCEDURE Stub_rec(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR r_arg: Test.Rec;
      out_arg: Test.Rec;
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
      dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      out_arg.i := StubLib.InInteger(c, rep);
      out_arg.r := StubLib.InReal(c, rep);
      out_arg.l := StubLib.InLongreal(c, rep);
      out_arg.b := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
      out_arg.c := VAL(StubLib.InInteger(c, rep, 0,255), CHAR);
      out_arg.w := VAL(StubLib.InInteger(c, rep, 0,65535), WIDECHAR);
      out_arg.s := StubLib.InInteger(c, rep, -3, 1000);
      out_arg.e := VAL(StubLib.InInteger(c, rep, 0,2), Test.Color);
      out_arg.set:=SET OF [0..40]{};
      FOR i0 := FIRST([0..40]) TO LAST([0..40]) DO
        IF StubLib.InBoolean(c) THEN
          out_arg.set := out_arg.set + SET OF [0..40]{i0};
          END
        END;
      END;
      self.rec(r_arg, out_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutInteger(c, out_arg.i);
    StubLib.OutReal(c, out_arg.r);
    StubLib.OutLongreal(c, out_arg.l);
    StubLib.OutInteger(c, ORD(out_arg.b));
    StubLib.OutInteger(c, ORD(out_arg.c));
    StubLib.OutInteger(c, ORD(out_arg.w));
    StubLib.OutInteger(c, out_arg.s);
    StubLib.OutInteger(c, ORD(out_arg.e));
    FOR i0 := FIRST([0..40]) TO LAST([0..40]) DO
      StubLib.OutBoolean(c, i0 IN out_arg.set);
      END;

  END Stub_rec;

PROCEDURE Stub_list(
    self: Test.T;
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
    self: Test.T;
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

PROCEDURE Stub_arr(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR a_arg: REF ARRAY  OF INTEGER;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    WITH n1 = StubLib.InInteger(c, rep) DO
      a_arg := NEW(REF ARRAY  OF INTEGER, n1);
    END;
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      FOR n1 := 0 TO LAST(a_arg^) DO
        a_arg[n1] := StubLib.InInteger(c, rep);
        END;
      END;
      self.arr(a_arg^);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    FOR n1 := 0 TO LAST(a_arg^) DO
      StubLib.OutInteger(c, a_arg[n1]);
      END;

  END Stub_arr;

PROCEDURE Stub_chars(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR a_arg: REF ARRAY  OF CHAR;
      w_arg: REF ARRAY  OF WIDECHAR;
      res: TEXT;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    WITH n1 = StubLib.InInteger(c, rep) DO
      a_arg := NEW(REF ARRAY  OF CHAR, n1);
    END;
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      FOR n1 := 0 TO LAST(a_arg^) DO
        a_arg[n1] := VAL(StubLib.InInteger(c, rep, 0,255), CHAR);
        END;
      END;
      WITH n1 = StubLib.InInteger(c, rep) DO
      w_arg := NEW(REF ARRAY  OF WIDECHAR, n1);
    END;
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      FOR n1 := 0 TO LAST(w_arg^) DO
        w_arg[n1] := VAL(StubLib.InInteger(c, rep, 0,65535), WIDECHAR);
        END;
      END;
      res := self.chars(a_arg^, w_arg^);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    FOR n1 := 0 TO LAST(w_arg^) DO
      StubLib.OutInteger(c, ORD(w_arg[n1]));
      END;
    StubLib.OutRef(c, res);

  END Stub_chars;

PROCEDURE Stub_texts(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR a_arg: REF ARRAY  OF TEXT;
      res: TEXT;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    WITH n1 = StubLib.InInteger(c, rep) DO
      a_arg := NEW(REF ARRAY  OF TEXT, n1);
    END;
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      FOR n1 := 0 TO LAST(a_arg^) DO
        a_arg[n1] := StubLib.InRef(c, rep, -1);
        END;
      END;
      res := self.texts(a_arg^);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_texts;

PROCEDURE Stub_fail(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, Test.Oops}=
  VAR code_arg: INTEGER;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    code_arg := StubLib.InInteger(c, rep);
    self.fail(code_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_fail;

PROCEDURE Stub_callback(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR cb_arg: Test.T;
      n_arg: INTEGER;
      res: INTEGER;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    cb_arg := StubLib.InRef(c, rep, TYPECODE(Test.T));
    n_arg := StubLib.InInteger(c, rep);
    res := self.callback(cb_arg, n_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutInteger(c, res);

  END Stub_callback;

PROCEDURE Stub_other(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR res: Test.U;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    res := self.other();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_other;

PROCEDURE Stub_reader(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR rd_arg: Rd.T;
      res: TEXT;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    rd_arg := StubLib.InRef(c, rep, TYPECODE(Rd.T));
    res := self.reader(rd_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_reader;

PROCEDURE Stub_writer(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR wr_arg: Wr.T;
      t_arg: TEXT;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    wr_arg := StubLib.InRef(c, rep, TYPECODE(Wr.T));
    t_arg := StubLib.InRef(c, rep, -1);
    self.writer(wr_arg, t_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_writer;

PROCEDURE Stub_quit(
    self: Test.T;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  BEGIN
    self.quit();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_quit;

BEGIN
  StubLib.Register(TYPECODE(Test.T), 1, TYPECODE(Surrogate_Test_T), Invoke);
END Test_T_v1.
