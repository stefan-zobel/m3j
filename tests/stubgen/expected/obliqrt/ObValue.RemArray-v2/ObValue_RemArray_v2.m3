MODULE ObValue_RemArray_v2 EXPORTS ObValue, ObValue_RemArray_v2;

IMPORT Rd, Wr, StubLib, ObValue, Thread, NetObj;
CONST Protocol: StubLib.StubProtocol = 2;

TYPE
      Methods = {Obtain, Upd, Sub, Set, Get, Size};
      ReturnCodes = {OK, ObValue_ServerError};

  PROCEDURE Surrogate_Size(self: ObValue.RemArray): INTEGER
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
          StubLib.OutInt32(c, ORD(Methods.Size));
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
    END Surrogate_Size;

  PROCEDURE Surrogate_Get(self: ObValue.RemArray; i_arg: INTEGER)
      : ObValue.Val RAISES {ObValue.ServerError, NetObj.Error,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: ObValue.Val;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.Get));
          StubLib.OutInteger(c, i_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(ObValue.Val));
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
    END Surrogate_Get;

  PROCEDURE Surrogate_Set(
      self: ObValue.RemArray;
      i_arg: INTEGER;
      val_arg: ObValue.Val) RAISES {ObValue.ServerError, NetObj.Error,
      Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.Set));
          StubLib.OutInteger(c, i_arg);
          StubLib.OutRef(c, val_arg);
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
    END Surrogate_Set;

  PROCEDURE Surrogate_Sub(
      self: ObValue.RemArray;
      start_arg: INTEGER;
      size_arg: INTEGER): ObValue.ValArray RAISES {ObValue.ServerError,
      NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: ObValue.ValArray;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.Sub));
          StubLib.OutInteger(c, start_arg);
          StubLib.OutInteger(c, size_arg);
          rep := StubLib.AwaitResult(c);
          CASE StubLib.InInt32(c, rep) OF
          | ORD(ReturnCodes.OK) =>
            res := StubLib.InRef(c, rep, TYPECODE(ObValue.ValArray));
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
    END Surrogate_Sub;

  PROCEDURE Surrogate_Upd(
      self: ObValue.RemArray;
      start_arg: INTEGER;
      size_arg: INTEGER;
      READONLY other_arg: REF ObValue.Vals) RAISES {ObValue.ServerError,
      NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.Upd));
          StubLib.OutInteger(c, start_arg);
          StubLib.OutInteger(c, size_arg);
          StubLib.OutRef(c, other_arg);
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
    END Surrogate_Upd;

  PROCEDURE Surrogate_Obtain(self: ObValue.RemArray): REF ObValue.Vals
       RAISES {NetObj.Error, Thread.Alerted} =

    VAR reuse := FALSE;
        rep: StubLib.DataRep;
        c: StubLib.Conn;
        dataPresent: BOOLEAN; <* NOWARN *>
        res: REF ObValue.Vals;

    BEGIN
      TRY
        c := StubLib.StartCall(self, Protocol);
        TRY
          StubLib.OutInt32(c, ORD(Methods.Obtain));
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
    END Surrogate_Obtain;

PROCEDURE Invoke(
    c: StubLib.Conn;
    obj: NetObj.T;
    rep: StubLib.DataRep;
    stubProt: StubLib.StubProtocol)
    RAISES {NetObj.Error, Rd.Failure,
            Wr.Failure, Thread.Alerted} =
  VAR t := NARROW(obj, ObValue.RemArray);
  BEGIN
    IF stubProt # Protocol THEN StubLib.RaiseUnmarshalFailure() END;
    TRY
      CASE StubLib.InInt32(c, rep) OF
      | ORD(Methods.Size) => Stub_Size(t, c, rep);
      | ORD(Methods.Get) => Stub_Get(t, c, rep);
      | ORD(Methods.Set) => Stub_Set(t, c, rep);
      | ORD(Methods.Sub) => Stub_Sub(t, c, rep);
      | ORD(Methods.Upd) => Stub_Upd(t, c, rep);
      | ORD(Methods.Obtain) => Stub_Obtain(t, c, rep);
      ELSE
        StubLib.RaiseUnmarshalFailure();
      END;
    EXCEPT
    | ObValue.ServerError(arg) =>
        StubLib.StartResult(c);
        StubLib.OutInt32(c, ORD(ReturnCodes.ObValue_ServerError));
        StubLib.OutRef(c, arg);
    END;
  END Invoke;

PROCEDURE Stub_Size(
    self: ObValue.RemArray;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR res: INTEGER;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    res := self.Size();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutInteger(c, res);

  END Stub_Size;

PROCEDURE Stub_Get(
    self: ObValue.RemArray;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, ObValue.ServerError}=
  VAR i_arg: INTEGER;
      res: ObValue.Val;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    i_arg := StubLib.InInteger(c, rep);
    res := self.Get(i_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_Get;

PROCEDURE Stub_Set(
    self: ObValue.RemArray;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, ObValue.ServerError}=
  VAR i_arg: INTEGER;
      val_arg: ObValue.Val;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    i_arg := StubLib.InInteger(c, rep);
    val_arg := StubLib.InRef(c, rep, TYPECODE(ObValue.Val));
    self.Set(i_arg, val_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_Set;

PROCEDURE Stub_Sub(
    self: ObValue.RemArray;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, ObValue.ServerError}=
  VAR start_arg: INTEGER;
      size_arg: INTEGER;
      res: ObValue.ValArray;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    start_arg := StubLib.InInteger(c, rep);
    size_arg := StubLib.InInteger(c, rep);
    res := self.Sub(start_arg, size_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_Sub;

PROCEDURE Stub_Upd(
    self: ObValue.RemArray;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted, ObValue.ServerError}=
  VAR start_arg: INTEGER;
      size_arg: INTEGER;
      other_arg: REF ObValue.Vals;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    start_arg := StubLib.InInteger(c, rep);
    size_arg := StubLib.InInteger(c, rep);
    other_arg := StubLib.InRef(c, rep, -1);
    self.Upd(start_arg, size_arg, other_arg);
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));

  END Stub_Upd;

PROCEDURE Stub_Obtain(
    self: ObValue.RemArray;
    c: StubLib.Conn;
    <* NOWARN *> rep: StubLib.DataRep) RAISES {NetObj.Error, Rd.Failure,
    Wr.Failure, Thread.Alerted}=
  VAR res: REF ObValue.Vals;
      dataPresent: BOOLEAN <* NOWARN *>;

  BEGIN
    res := self.Obtain();
    StubLib.StartResult(c);
    StubLib.OutInt32(c, ORD(ReturnCodes.OK));
    StubLib.OutRef(c, res);

  END Stub_Obtain;

BEGIN
  StubLib.Register(TYPECODE(ObValue.RemArray), 2, TYPECODE(Surrogate_ObValue_RemArray), Invoke);
END ObValue_RemArray_v2.
