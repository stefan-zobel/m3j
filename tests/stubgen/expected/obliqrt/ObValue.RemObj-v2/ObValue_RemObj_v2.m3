MODULE ObValue_RemObj_v2 EXPORTS ObValue, ObValue_RemObj_v2;

IMPORT Rd, SharedObj, Wr, StubLib, AtomList, ObValue, Thread, NetObj;
CONST Protocol: StubLib.StubProtocol = 2;

TYPE
      Methods = {Obtain, Has, Redirect, Update, Invoke, Select, Who};
      ReturnCodes = {OK, ObValue_Error, ObValue_Exception,
        ObValue_ServerError, SharedObj_Error};

  PROCEDURE Surrogate_Who(
      self: ObValue.RemObj;
      VAR protected_arg: BOOLEAN;
      VAR serialized_arg: BOOLEAN): TEXT RAISES {NetObj.Error,
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
          StubLib.OutInt32(c, ORD(Methods.Who));
          StubLib.OutInteger(c, ORD(protected_arg));
          StubLib.OutInteger(c, ORD(serialized_arg));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            protected_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
            serialized_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
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

  PROCEDURE Surrogate_Select(
      self: ObValue.RemObj;
      label_arg: TEXT;
      internal_arg: BOOLEAN;
      VAR hint_arg: INTEGER): ObValue.Val RAISES {ObValue.Error,
      ObValue.Exception, ObValue.ServerError, SharedObj.Error,
      NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: ObValue.Val;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.Select));
          StubLib.OutRef(c, label_arg);
          StubLib.OutInteger(c, ORD(internal_arg));
          StubLib.OutInteger(c, hint_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            hint_arg := StubLib.InInteger(c, rep);
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
          | ORD(ReturnCodes.SharedObj_Error) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE SharedObj.Error(arg);

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
    END Surrogate_Select;

  PROCEDURE Surrogate_Invoke(
      self: ObValue.RemObj;
      label_arg: TEXT;
      argNo_arg: INTEGER;
      READONLY args_arg: ObValue.Vals;
      internal_arg: BOOLEAN;
      VAR hint_arg: INTEGER): ObValue.Val RAISES {ObValue.Error,
      ObValue.Exception, ObValue.ServerError, SharedObj.Error,
      NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: ObValue.Val;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.Invoke));
          StubLib.OutRef(c, label_arg);
          StubLib.OutInteger(c, argNo_arg);
          StubLib.OutInteger(c, NUMBER(args_arg));
          dataPresent := TRUE;
          StubLib.OutBoolean(c, dataPresent);
          IF dataPresent THEN
            FOR n1 := 0 TO LAST(args_arg) DO
              StubLib.OutRef(c, args_arg[n1]);
              END;
            END;
            StubLib.OutInteger(c, ORD(internal_arg));
          StubLib.OutInteger(c, hint_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            hint_arg := StubLib.InInteger(c, rep);
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
          | ORD(ReturnCodes.SharedObj_Error) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE SharedObj.Error(arg);

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
    END Surrogate_Invoke;

  PROCEDURE Surrogate_Update(
      self: ObValue.RemObj;
      label_arg: TEXT;
      val_arg: ObValue.Val;
      internal_arg: BOOLEAN;
      VAR hint_arg: INTEGER) RAISES {ObValue.ServerError, SharedObj.Error,
      NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.Update));
          StubLib.OutRef(c, label_arg);
          StubLib.OutRef(c, val_arg);
          StubLib.OutInteger(c, ORD(internal_arg));
          StubLib.OutInteger(c, hint_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            hint_arg := StubLib.InInteger(c, rep);
            reuse := TRUE;
          | ORD(ReturnCodes.ObValue_ServerError) =>
            VAR arg: TEXT;
            BEGIN
              arg := StubLib.InRef(c, rep, -1);
              reuse := TRUE;
              RAISE ObValue.ServerError(arg);

            END;
          | ORD(ReturnCodes.SharedObj_Error) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE SharedObj.Error(arg);

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
    END Surrogate_Update;

  PROCEDURE Surrogate_Redirect(
      self: ObValue.RemObj;
      val_arg: ObValue.Val;
      internal_arg: BOOLEAN) RAISES {ObValue.ServerError, SharedObj.Error,
      NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.Redirect));
          StubLib.OutRef(c, val_arg);
          StubLib.OutInteger(c, ORD(internal_arg));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            reuse := TRUE;
          | ORD(ReturnCodes.ObValue_ServerError) =>
            VAR arg: TEXT;
            BEGIN
              arg := StubLib.InRef(c, rep, -1);
              reuse := TRUE;
              RAISE ObValue.ServerError(arg);

            END;
          | ORD(ReturnCodes.SharedObj_Error) =>
            VAR arg: AtomList.T;
            BEGIN
              arg := StubLib.InRef(c, rep, TYPECODE(AtomList.T));
              reuse := TRUE;
              RAISE SharedObj.Error(arg);

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
    END Surrogate_Redirect;

  PROCEDURE Surrogate_Has(
      self: ObValue.RemObj;
      label_arg: TEXT;
      VAR hint_arg: INTEGER): BOOLEAN RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: BOOLEAN;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.Has));
          StubLib.OutRef(c, label_arg);
          StubLib.OutInteger(c, hint_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            hint_arg := StubLib.InInteger(c, rep);
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
    END Surrogate_Has;

  PROCEDURE Surrogate_Obtain(self: ObValue.RemObj; internal_arg: BOOLEAN)
      : REF ObValue.ObjFields RAISES {ObValue.ServerError, NetObj.Error,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: REF ObValue.ObjFields;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.Obtain));
          StubLib.OutInteger(c, ORD(internal_arg));
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, -1);
            reuse := TRUE;
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
    END Surrogate_Obtain;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, ObValue.RemObj);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.Who) => Stub_Who(t, c, rep);
      | ORD(Methods.Select) => Stub_Select(t, c, rep);
      | ORD(Methods.Invoke) => Stub_Invoke(t, c, rep);
      | ORD(Methods.Update) => Stub_Update(t, c, rep);
      | ORD(Methods.Redirect) => Stub_Redirect(t, c, rep);
      | ORD(Methods.Has) => Stub_Has(t, c, rep);
      | ORD(Methods.Obtain) => Stub_Obtain(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    | SharedObj.Error(arg) =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.SharedObj_Error));
        StubLib.OutRef(c, arg);

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
    self: ObValue.RemObj;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR protected_arg: BOOLEAN;
      serialized_arg: BOOLEAN;
      res: TEXT;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    protected_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    serialized_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    res := self.Who(protected_arg, serialized_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutInteger(c, ORD(protected_arg));
    StubLib.OutInteger(c, ORD(serialized_arg));
    StubLib.OutRef(c, res);

  END Stub_Who;

PROCEDURE Stub_Select(
    self: ObValue.RemObj;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, ObValue.Error, ObValue.Exception,
    ObValue.ServerError, SharedObj.Error}=
  VAR label_arg: TEXT;
      internal_arg: BOOLEAN;
      hint_arg: INTEGER;
      res: ObValue.Val;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    label_arg := StubLib.InRef(c, rep, -1);
    internal_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    hint_arg := StubLib.InInteger(c, rep);
    res := self.Select(label_arg, internal_arg, hint_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutInteger(c, hint_arg);
    StubLib.OutRef(c, res);

  END Stub_Select;

PROCEDURE Stub_Invoke(
    self: ObValue.RemObj;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, ObValue.Error, ObValue.Exception,
    ObValue.ServerError, SharedObj.Error}=
  VAR label_arg: TEXT;
      argNo_arg: INTEGER;
      args_arg: REF ObValue.Vals;
      internal_arg: BOOLEAN;
      hint_arg: INTEGER;
      res: ObValue.Val;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    label_arg := StubLib.InRef(c, rep, -1);
    argNo_arg := StubLib.InInteger(c, rep);
    WITH n1 = StubLib.InInteger(c, rep) DO
      args_arg := NEW(REF ObValue.Vals, n1);
    END;
    dataPresent := StubLib.InBoolean(c);
    IF dataPresent THEN
      FOR n1 := 0 TO LAST(args_arg^) DO
        args_arg[n1] := StubLib.InRef(c, rep, TYPECODE(ObValue.Val));
        END;
      END;
      internal_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    hint_arg := StubLib.InInteger(c, rep);
    res := self.Invoke(label_arg, argNo_arg, args_arg^, internal_arg, hint_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutInteger(c, hint_arg);
    StubLib.OutRef(c, res);

  END Stub_Invoke;

PROCEDURE Stub_Update(
    self: ObValue.RemObj;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, ObValue.ServerError, SharedObj.Error}=
  VAR label_arg: TEXT;
      val_arg: ObValue.Val;
      internal_arg: BOOLEAN;
      hint_arg: INTEGER;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    label_arg := StubLib.InRef(c, rep, -1);
    val_arg := StubLib.InRef(c, rep, TYPECODE(ObValue.Val));
    internal_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    hint_arg := StubLib.InInteger(c, rep);
    self.Update(label_arg, val_arg, internal_arg, hint_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutInteger(c, hint_arg);

  END Stub_Update;

PROCEDURE Stub_Redirect(
    self: ObValue.RemObj;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, ObValue.ServerError, SharedObj.Error}=
  VAR val_arg: ObValue.Val;
      internal_arg: BOOLEAN;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    val_arg := StubLib.InRef(c, rep, TYPECODE(ObValue.Val));
    internal_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    self.Redirect(val_arg, internal_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_Redirect;

PROCEDURE Stub_Has(
    self: ObValue.RemObj;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR label_arg: TEXT;
      hint_arg: INTEGER;
      res: BOOLEAN;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    label_arg := StubLib.InRef(c, rep, -1);
    hint_arg := StubLib.InInteger(c, rep);
    res := self.Has(label_arg, hint_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutInteger(c, hint_arg);
    StubLib.OutInteger(c, ORD(res));

  END Stub_Has;

PROCEDURE Stub_Obtain(
    self: ObValue.RemObj;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, ObValue.ServerError}=
  VAR internal_arg: BOOLEAN;
      res: REF ObValue.ObjFields;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    internal_arg := VAL(StubLib.InInteger(c, rep, 0,1), BOOLEAN);
    res := self.Obtain(internal_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_Obtain;

BEGIN
  StubLib.Register(TYPECODE(ObValue.RemObj), 2, TYPECODE(Surrogate_ObValue_RemObj), Invoke);
END ObValue_RemObj_v2.
